package com.emberinn.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ContextBudgetException
import com.emberinn.app.data.ProviderState
import com.emberinn.app.data.TtsReader
import com.emberinn.app.data.TtsTextProcessor
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.slash.QuickReplyExecutor
import com.emberinn.engine.slash.QuickReplySlot
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 聊天页 ViewModel：消息读写 + PromptPipeline 总装流式发送。
 * 停止 = 取消 OkHttp call 并保留已生成文本（官方 mes_stop）；未配置模型只显示提示、不写历史。
 */
class ChatViewModel(application: Application, private val sessionId: String) : AndroidViewModel(application) {

    private val chatStore = ChatStore(application)
    private val charStore = CharacterStore(application)
    private val chatRepository = ChatRepository(application)
    private val quickReplyStore = QuickReplyStore(application)

    private val _messages = MutableStateFlow(chatStore.messages(sessionId))
    val messages: StateFlow<List<JsonElement>> = _messages

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    /** 流式思考过程（官方 reasoning 独立通道，不进聊天正文）。 */
    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning

    /** 最近一次生成的完整思考过程（生成完保留，UI 折叠展示；新请求时清空）。 */
    private val _lastReasoning = MutableStateFlow<String?>(null)
    val lastReasoning: StateFlow<String?> = _lastReasoning

    /** 待发送附件（官方 extra.media；本地读成 data URL 后随消息发送）。 */
    private val _pendingMedia = MutableStateFlow<List<MediaAttachment>>(emptyList())
    val pendingMedia: StateFlow<List<MediaAttachment>> = _pendingMedia

    /** 上次发送命中的世界书条目（完整信息），聊天页状态面板（README 命中指示灯）。 */
    private val _worldHits = MutableStateFlow<List<WorldHitView>>(emptyList())
    val worldHits: StateFlow<List<WorldHitView>> = _worldHits

    /** 上次发送的上下文占用（已用 token, 上限），聊天页显示占比胶囊。 */
    private val _contextUsage = MutableStateFlow<Pair<Int, Int>?>(null)
    val contextUsage: StateFlow<Pair<Int, Int>?> = _contextUsage

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    /** 冒充生成中（官方 type=impersonate：结果进输入框，不落历史）。 */
    private val _isImpersonating = MutableStateFlow(false)
    val isImpersonating: StateFlow<Boolean> = _isImpersonating

    /** 冒充完成的草稿文本（由聊天页放进输入框后调用 consumeImpersonation）。 */
    private val _impersonated = MutableStateFlow<String?>(null)
    val impersonated: StateFlow<String?> = _impersonated

    /** 共享状态：设置页写入后自动更新，聊天页无需轮询读盘。 */
    val providerConfigured: StateFlow<Boolean> = ProviderState.configured

    /** 全局快捷回复槽位（官方 QuickReplySlot，设置页管理）。 */
    private val _quickReplies = MutableStateFlow(quickReplyStore.slots())
    val quickReplies: StateFlow<List<QuickReplySlot>> = _quickReplies

    /** 快捷回复执行输出（填入输入框；空输出不动作，如 /let 只写变量）。 */
    private val _quickReplyOutput = MutableStateFlow<String?>(null)
    val quickReplyOutput: StateFlow<String?> = _quickReplyOutput

    fun runQuickReply(label: String) {
        if (_isStreaming.value) return
        val output = QuickReplyExecutor.execute(quickReplyStore.load(), label)
        if (output.isNotBlank()) _quickReplyOutput.value = output
    }

    fun consumeQuickReplyOutput() { _quickReplyOutput.value = null }

    /** 朗读指定消息（长按菜单）；文本处理与分段对齐官方 tts 扩展。 */
    fun narrateMessage(index: Int) {
        val msgs = chatStore.messages(sessionId)
        val text = msgs.getOrNull(index)?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull ?: return
        narrateText(text)
    }

    /** 朗读最后一条 AI 消息（自动朗读）。 */
    fun narrateLastMessage() {
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull()?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull ?: return
        narrateText(last)
    }

    fun stopNarration() { TtsReader.stop() }

    private fun narrateText(text: String) {
        val voice = VoicePrefs.read(getApplication())
        if (!voice.enabled) return
        val cleaned = TtsTextProcessor.prepare(
            text = text,
            skipCodeblocks = voice.skipCodeblocks,
            skipTags = voice.skipTags,
            applyRegex = voice.applyRegex,
            regexPattern = voice.regexPattern,
        )
        val ok = TtsReader.speak(getApplication(), cleaned, voice.voice, voice.rate, voice.narrateByParagraphs)
        if (!ok && cleaned.isNotBlank()) {
            _notice.value = "（语音引擎未就绪，请到 设置→语音 检查。）"
        }
    }

    /** 聊天背景（官方 chat_metadata.custom_background，本地文件路径）。 */
    private val _chatBackground = MutableStateFlow(
        chatStore.metadata(sessionId)["custom_background"]?.jsonPrimitive?.contentOrNull,
    )
    val chatBackground: StateFlow<String?> = _chatBackground

    fun setChatBackground(uri: Uri) {
        val bytes = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return
        val ext = when (getApplication<Application>().contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(getApplication<Application>().filesDir, "media/chat-bg-$sessionId.$ext")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        val meta = chatStore.metadata(sessionId).toMutableMap()
        meta["custom_background"] = JsonPrimitive(file.absolutePath)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _chatBackground.value = file.absolutePath
    }

    fun clearChatBackground() {
        _chatBackground.value?.let { old -> runCatching { File(old).delete() } }
        val meta = chatStore.metadata(sessionId).toMutableMap()
        meta.remove("custom_background")
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _chatBackground.value = null
    }

    /** 瞬态提示（未配置模型 / 请求失败），只显示不落盘。 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** 每次进入聊天页调用：读盘刷新一次（设置页已保存过则直接由 ProviderState 同步）。 */
    fun refreshProviderConfigured() {
        ProviderState.refresh(chatRepository.profile())
    }

    private fun isProviderConfigured(): Boolean = ProviderState.isConfigured()

    @Volatile
    private var streamSession: LlmClient.StreamSession? = null
    @Volatile
    private var streamActive = false
    private var streamStartedAt: String = java.time.Instant.now().toString()
    private var streamContinueMode = false
    /** 当前流是否“滑动生成新变体”（对齐官方 Generate('swipe')：结果追加进最后一条 swipes，不新增消息）。 */
    @Volatile
    private var generatingSwipe = false
    private var currentCharName = "Assistant"
    private var currentUserName = "User"

    val characterId: String? = chatStore.get(sessionId)?.characterId
    val character: CharacterRecord? =
        characterId?.let { id -> charStore.list().firstOrNull { it.id == id } }

    val accentColor: Long? = character?.seedColor
    val avatarPath: String? = character?.avatarPath

    init {
        // 官方新聊天第一条消息 = 角色开场白 first_mes（script.js newChat 语义）；空会话才补。
        // README：AI 对话（无角色卡）带默认开场“我是余烬，想聊点什么？”
        if (chatStore.messages(sessionId).isEmpty()) {
            val charName = chatStore.get(sessionId)?.name ?: "Assistant"
            val firstMes = if (character != null) {
                firstMesOf(character.rawJson)
            } else {
                DEFAULT_AI_OPENING
            }
            if (!firstMes.isNullOrBlank()) {
                chatStore.append(sessionId, false, firstMes, charName)
                refreshMessages()
            }
        }
    }

    /** README 启动体验：AI 对话默认开场。 */
    private companion object {
        const val DEFAULT_AI_OPENING = "我是余烬，想聊点什么？"
    }

    private fun firstMesOf(rawJson: String): String? = runCatching {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonObject ?: root
        data["first_mes"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    fun saveProvider(profile: ConnectionProfile) {
        chatRepository.saveProfile(profile)
        ProviderState.refresh(profile)
    }

    fun send(text: String, userName: String = "User", media: List<MediaAttachment> = emptyList()) {
        if ((text.isBlank() && media.isEmpty()) || _isStreaming.value) return
        // 未配置模型先拦住：只显示提示，不写历史（避免悬空用户消息之后真的发给模型）。
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
        }
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        currentCharName = charName
        currentUserName = userName
        _notice.value = null
        _impersonated.value = null
        chatStore.append(sessionId, true, text, userName, media)
        _pendingMedia.value = emptyList()
        refreshMessages()
        val voice = VoicePrefs.read(getApplication())
        if (voice.enabled && voice.narrateUser) {
            narrateText(text)
        }
        startStream(
            history = chatStore.messages(sessionId),
            mediaInlining = media.isNotEmpty(),
        )
    }

    /** 停止按钮：取消请求并保留已生成的部分（官方 abortController + mes_stop 语义）。 */
    fun stop() {
        if (!_isStreaming.value) return
        streamSession?.cancel()
        streamSession = null
        streamActive = false
        val partial = _streamingText.value
        val wasImpersonating = _isImpersonating.value
        val wasContinue = streamContinueMode
        val wasSwipe = generatingSwipe
        _isStreaming.value = false
        _isImpersonating.value = false
        if (!wasImpersonating && _streamingReasoning.value.isNotBlank()) {
            _lastReasoning.value = _streamingReasoning.value
        }
        if (partial.isNotBlank()) {
            when {
                wasImpersonating -> _impersonated.value = partial
                wasSwipe -> appendGeneratedSwipe(partial)
                wasContinue -> {
                    // 对齐官方 saveReply(type='continue')：lastMessage.mes += getMessage，紧贴追加不插换行
                    val after = chatStore.messages(sessionId).toMutableList()
                    val aiIdx = after.indexOfLast { !isUser(it) }
                    if (aiIdx >= 0) {
                        val combined = textOf(after[aiIdx]) + partial
                        after[aiIdx] = JsonObject(after[aiIdx].jsonObject + ("mes" to JsonPrimitive(combined)))
                        chatStore.replace(sessionId, after)
                        refreshMessages()
                    }
                }
                else -> {
                    appendAiReply(partial)
                    refreshMessages()
                }
            }
        }
        _streamingText.value = ""
        _streamingReasoning.value = ""
        streamContinueMode = false
        generatingSwipe = false
    }

    /** 重新生成（官方 option_regenerate）：只对最后一条 AI 生效——先删掉它，再按剩余历史重新请求。 */
    fun regenerate() {
        if (_isStreaming.value) return
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return
        if (isUser(last)) return
        chatStore.removeAt(sessionId, msgs.lastIndex)
        refreshMessages()
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
        }
        startStream(history = chatStore.messages(sessionId))
    }

    /**
     * 继续生成：官方 mes_continue（默认 continue_prefill=false → nudge 路径）。
     * 传完整历史（ChatPromptFactory 会翻成“新的在前”），引擎把最后一条 AI 移进 continueNudge；
     * 流结束把续写追加回最后一条 AI（不新增消息）。
     */
    fun continueGeneration() {
        if (_isStreaming.value) return
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return
        if (isUser(last)) {
            // 不再静默失败：最后一条是用户消息时明确提示，否则点“继续”毫无反应
            _notice.value = "（最后一条是你发的消息，先让对方回复或发送后再继续。）"
            return
        }
        val lastText = textOf(last)
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
        }
        startStream(
            history = msgs,
            type = "continue",
            cyclePrompt = lastText,
            continueMode = true,
        )
    }

    /** 滑动切回复：左滑 = 上一个变体（对齐官方 swipe_left，越界 wrap 回最后一条）。 */
    fun swipeLeft(index: Int) {
        if (_isStreaming.value) return
        if (!chatStore.ensureSwipes(sessionId, index)) return
        val el = chatStore.messages(sessionId).getOrNull(index) ?: return
        val count = chatStore.swipeCount(el)
        if (count <= 0) return
        val cur = chatStore.currentSwipeId(el)
        val newId = (cur - 1 + count) % count
        chatStore.swipeTo(sessionId, index, newId)
        refreshMessages()
    }

    /** 滑动切回复：右滑 = 下一个变体；越界时最后一条 AI 生成新变体（对齐官方 overswipe REGENERATE），其余 wrap 回第一条。 */
    fun swipeRight(index: Int) {
        if (_isStreaming.value) return
        if (!chatStore.ensureSwipes(sessionId, index)) return
        val msgs = chatStore.messages(sessionId)
        val el = msgs.getOrNull(index) ?: return
        val count = chatStore.swipeCount(el)
        if (count <= 0) return
        val cur = chatStore.currentSwipeId(el)
        if (cur + 1 < count) {
            chatStore.swipeTo(sessionId, index, cur + 1)
            refreshMessages()
        } else if (index == msgs.lastIndex && !isUser(el)) {
            generateSwipe()
        } else {
            chatStore.swipeTo(sessionId, index, 0)
            refreshMessages()
        }
    }

    /**
     * 生成新变体：对齐官方 Generate('swipe')——coreChat.pop() 排除最后一条消息再组装，
     * 结果追加进最后一条 AI 的 swipes（不新增消息）。
     */
    fun generateSwipe() {
        if (_isStreaming.value) return
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return
        if (isUser(last)) return
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再滑动生成。）"
            return
        }
        chatStore.ensureSwipes(sessionId, msgs.lastIndex)
        startStream(
            history = msgs.dropLast(1),
            type = "swipe",
            swipeMode = true,
        )
    }

    /** 读取消息的 swipes 变体数（UI 计数 chip 用）。 */
    fun swipeCountOf(el: JsonElement): Int = chatStore.swipeCount(el)

    /** 当前 swipes 下标（UI 计数 chip 用）。 */
    fun currentSwipeOf(el: JsonElement): Int = chatStore.currentSwipeId(el)

    /** 删除当前消息的指定 swipes 变体（对齐官方 deleteSwipe）。 */
    fun deleteSwipe(index: Int, swipeIndex: Int) {
        if (_isStreaming.value) return
        chatStore.deleteSwipe(sessionId, index, swipeIndex)
        refreshMessages()
    }

    fun deleteMessage(index: Int) {
        if (_isStreaming.value) return
        chatStore.removeAt(sessionId, index)
        refreshMessages()
    }

    /** 编辑消息（官方 updateMessage：substituteParams 宏替换 + 清 extra.bias；regex(isEdit)/bias 提取待正则 UI 接线）。 */
    fun editMessage(index: Int, newText: String) {
        if (_isStreaming.value) return
        val text = newText.trim()
        if (text.isEmpty()) return
        val env = MacroEnv(user = currentUserName, char = currentCharName)
        val processed = MacroEngine.substitute(text, env)
        chatStore.updateMessage(sessionId, index, processed)
        refreshMessages()
    }

    /** 冒充（官方 Generate('impersonate')）：模型以 {{user}} 视角写下一句，流式草稿进输入框，不落历史。 */
    fun impersonate() {
        if (_isStreaming.value) return
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再冒充。）"
            return
        }
        startStream(
            history = chatStore.messages(sessionId),
            type = "impersonate",
            impersonation = true,
        )
    }

    fun consumeImpersonation() {
        _impersonated.value = null
    }

    /** 从系统文件选择器取附件：落盘到 media/ 目录，聊天只存路径（官方 saveBase64AsFile 语义），发送时再转 data URL。 */
    fun addPendingMedia(uri: Uri, mime: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val resolver = app.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val type = mime?.ifBlank { null }
                    ?: resolver.getType(uri)
                    ?: "application/octet-stream"
                val mediaType = when {
                    type.startsWith("image/") -> "image"
                    type.startsWith("video/") -> "video"
                    type.startsWith("audio/") -> "audio"
                    else -> return@launch
                }
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: "attachment"
                // 官方 compressImage：非 jpeg/png/webp 一律转缩略图（JPEG，最长边 2048）；>2MB 的提供商规则登记
                val safeMime = type == "image/jpeg" || type == "image/png" || type == "image/webp"
                val processedBytes = if (mediaType == "image" && !safeMime) compressToJpeg(bytes) else bytes
                val extension = if (processedBytes !== bytes) "jpg" else extensionFor(type, displayName)
                val dir = java.io.File(app.filesDir, "media").apply { mkdirs() }
                val file = java.io.File(dir, "${System.currentTimeMillis()}_${displayName.hashCode().toUInt().toString(16)}.$extension")
                file.writeBytes(processedBytes)
                _pendingMedia.value = _pendingMedia.value + MediaAttachment(
                    type = mediaType,
                    url = file.absolutePath,
                    title = displayName,
                )
            }
        }
    }

    /** 官方 createThumbnail 近似：最长边 2048 等比缩放 → JPEG 85。 */
    private fun compressToJpeg(bytes: ByteArray): ByteArray {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val maxSide = 2048
        val longest = maxOf(src.width, src.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            src
        }
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== src) scaled.recycle()
        return out.toByteArray()
    }

    private fun extensionFor(mime: String, fallbackName: String): String {
        val fromName = fallbackName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 }
        if (fromName != null) return fromName
        return when {
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/gif") -> "gif"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("image/") -> "jpg"
            mime.startsWith("video/mp4") -> "mp4"
            mime.startsWith("video/webm") -> "webm"
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("audio/mpeg") -> "mp3"
            mime.startsWith("audio/mp4") || mime.contains("m4a") -> "m4a"
            mime.startsWith("audio/ogg") -> "ogg"
            mime.startsWith("audio/wav") -> "wav"
            mime.startsWith("audio/flac") -> "flac"
            mime.startsWith("audio/") -> "m4a"
            else -> "bin"
        }
    }

    fun removePendingMedia(index: Int) {
        val list = _pendingMedia.value
        if (index in list.indices) _pendingMedia.value = list.filterIndexed { i, _ -> i != index }
    }

    fun clearPendingMedia() {
        _pendingMedia.value = emptyList()
    }

    fun clearSession() {
        if (_isStreaming.value) stop()
        chatStore.replace(sessionId, emptyList())
        refreshMessages()
    }

    fun clearNotice() {
        _notice.value = null
    }

    /** 导出聊天原始 JSONL（官方聊天文件格式，可直接进酒馆）。 */
    fun exportJsonl(): String? = chatStore.exportJsonl(sessionId)

    private fun startStream(
        history: List<JsonElement>,
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonation: Boolean = false,
        cyclePrompt: String = "",
        continueMode: Boolean = false,
        swipeMode: Boolean = false,
        mediaInlining: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        streamStartedAt = java.time.Instant.now().toString()
        _streamingText.value = ""
        _streamingReasoning.value = ""
        _lastReasoning.value = null
        _worldHits.value = emptyList()
        _contextUsage.value = null
        _isStreaming.value = true
        _isImpersonating.value = impersonation
        streamContinueMode = continueMode
        generatingSwipe = swipeMode
        streamActive = true
        // 提示词总装（世界书扫描/宏/历史/token 计数）较重：丢后台线程做，UI 不卡顿，
        // 先置“生成中”，组装完再真正发起请求（官方异步语义）。
        viewModelScope.launch(Dispatchers.Default) {
            // buildRequest 阶段异常（如接口地址 scheme 非法）在 newCall 之前抛出，不经过 onError，
            // 直接透传给协程会崩溃——这里统一兜底转 notice，绝不崩。
            try {
            val session = chatRepository.streamPrepared(
                characterRawJson = character?.rawJson,
                history = history,
                userName = currentUserName,
                charName = currentCharName,
                onDelta = { delta ->
                    if (streamActive) _streamingText.value += delta
                },
                onReasoning = { text ->
                    if (streamActive) _streamingReasoning.value += text
                },
                onDone = {
                    if (streamActive) {
                        streamActive = false
                        finalizeStream(streamContinueMode)
                        onFinished?.invoke()
                    }
                },
                onError = { e ->
                    if (streamActive) {
                        streamActive = false
                        // 中断也保留已流出的思考过程，不静默吞掉；状态必须全部复位，避免卡“生成中”
                        if (_streamingReasoning.value.isNotBlank()) {
                            _lastReasoning.value = _streamingReasoning.value
                        }
                        if (_streamingText.value.isBlank()) {
                            _isStreaming.value = false
                            _isImpersonating.value = false
                            _streamingReasoning.value = ""
                            _notice.value = if (e is ContextBudgetException) {
                                "（${e.message}）"
                            } else {
                                "（请求中断，请检查网络或 API Key 后重试。）"
                            }
                        } else {
                            finalizeStream(streamContinueMode)
                        }
                        onFinished?.invoke()
                    }
                },
                type = type,
                continuePrefill = continuePrefill,
                cyclePrompt = cyclePrompt,
                mediaInlining = mediaInlining,
                chatMetadata = chatStore.metadata(sessionId),
                onPrepared = { info ->
                    if (streamActive) {
                        _worldHits.value = info.activatedWorldInfo.mapNotNull { entry ->
                            val name = entry.name.ifBlank { entry.keys.firstOrNull().orEmpty() }
                            if (name.isBlank()) null else WorldHitView(
                                name = name,
                                key = entry.keys.firstOrNull().orEmpty(),
                                constant = entry.constant,
                                positionLabel = positionLabel(entry.position),
                                tokens = entryTokens(entry.content),
                            )
                        }
                        // 官方 ChatCompletion 初始 reserveBudget(3)（start_chat）不入 counts，补上更接近实际
                        _contextUsage.value = Pair(info.counts.values.sum() + 3, info.maxContextTokens)
                    }
                },
            )
            if (streamActive) {
                streamSession = session
                if (session == null) {
                    streamActive = false
                    _isStreaming.value = false
                    _isImpersonating.value = false
                    _streamingReasoning.value = ""
                    streamContinueMode = false
                    generatingSwipe = false
                    _notice.value = "（未配置模型，请先选一个模型。）"
                }
            } else {
                // 组装期间用户点了停止：直接取消刚建好的请求
                session?.cancel()
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (streamActive) {
                    streamActive = false
                    _isStreaming.value = false
                    _isImpersonating.value = false
                    _streamingReasoning.value = ""
                    streamContinueMode = false
                    generatingSwipe = false
                    _notice.value = e.message?.let { "（$it）" }
                        ?: "（请求失败，请检查提供商设置后重试。）"
                }
            }
        }
    }

    private fun finalizeStream(continueMode: Boolean = false) {
        _isStreaming.value = false
        streamSession = null
        val reply = _streamingText.value
        val wasImpersonating = _isImpersonating.value
        val wasSwipe = generatingSwipe
        when {
            wasImpersonating -> {
                // 官方：冒充结果进输入框，不写历史
                if (reply.isNotBlank()) {
                    _impersonated.value = reply
                } else {
                    _lastReasoning.value = _streamingReasoning.value.takeIf { it.isNotBlank() }
                    _notice.value = if (_streamingReasoning.value.isNotBlank()) {
                        "（模型只返回了思考，没有生成冒充内容。）"
                    } else {
                        "（冒充没有生成内容，请重试。）"
                    }
                }
                _isImpersonating.value = false
            }
            wasSwipe -> {
                // 对齐官方 swipe 生成：结果追加进最后一条 AI 的 swipes，不新增消息
                if (_streamingReasoning.value.isNotBlank()) _lastReasoning.value = _streamingReasoning.value
                if (reply.isNotBlank()) {
                    appendGeneratedSwipe(reply)
                } else if (_streamingReasoning.value.isBlank()) {
                    _notice.value = "（滑动生成没有新内容，已保留当前回复。）"
                }
            }
            continueMode && reply.isNotBlank() -> {
                // 对齐官方 saveReply(type='continue')：lastMessage.mes += getMessage，紧贴追加不插换行
                if (_streamingReasoning.value.isNotBlank()) _lastReasoning.value = _streamingReasoning.value
                val after = chatStore.messages(sessionId).toMutableList()
                val aiIdx = after.indexOfLast { !isUser(it) }
                if (aiIdx >= 0) {
                    val combined = textOf(after[aiIdx]) + reply
                    after[aiIdx] = JsonObject(after[aiIdx].jsonObject + ("mes" to JsonPrimitive(combined)))
                    chatStore.replace(sessionId, after)
                    refreshMessages()
                }
            }
            _streamingReasoning.value.isNotBlank() -> {
                // 思考过程保留 + 正常追加回复；空正文不再静默吞掉，给用户明确反馈
                _lastReasoning.value = _streamingReasoning.value
                if (reply.isNotBlank()) {
                    appendAiReply(reply)
                    refreshMessages()
                } else {
                    _notice.value = "（模型只返回了思考过程，没有生成正文——多半是“最大回复 tokens”太小被思考占满。去 设置→提供商→最大回复 tokens 调大（如 8192），或关闭思考模式。）"
                }
            }
            reply.isNotBlank() -> {
                appendAiReply(reply)
                refreshMessages()
                val voice = VoicePrefs.read(getApplication())
                if (voice.enabled && voice.autoGeneration) {
                    narrateLastMessage()
                }
            }
        }
        _streamingText.value = ""
        _streamingReasoning.value = ""
        streamContinueMode = false
        generatingSwipe = false
    }

    /** 滑动生成完成落盘：追加为新变体（对齐官方 swipe 生成 saveReply）。 */
    private fun appendGeneratedSwipe(reply: String) {
        val msgs = chatStore.messages(sessionId)
        val aiIdx = msgs.indexOfLast { !isUser(it) }
        if (aiIdx >= 0) {
            val profile = chatRepository.profile()
            chatStore.appendSwipe(
                sessionId = sessionId,
                index = aiIdx,
                content = reply,
                api = profile?.providerId,
                model = profile?.model,
                genStarted = streamStartedAt,
                genFinished = java.time.Instant.now().toString(),
                reasoning = _streamingReasoning.value.takeIf { it.isNotBlank() },
            )
            refreshMessages()
        }
    }

    /** AI 回复落盘：带官方字段（api/model/gen_started/gen_finished/reasoning）。 */
    private fun appendAiReply(reply: String) {
        val profile = chatRepository.profile()
        chatStore.append(
            sessionId = sessionId,
            isUser = false,
            content = reply,
            name = currentCharName,
            api = profile?.providerId,
            model = profile?.model,
            genStarted = streamStartedAt,
            genFinished = java.time.Instant.now().toString(),
            reasoning = _streamingReasoning.value.takeIf { it.isNotBlank() },
        )
    }

    private fun refreshMessages() {
        _messages.value = chatStore.messages(sessionId)
    }

    /** 世界书命中面板行（README 状态面板：名字/关键词/常驻/位置/token）。 */
    data class WorldHitView(
        val name: String,
        val key: String,
        val constant: Boolean,
        val positionLabel: String,
        val tokens: Int,
    )

    private fun positionLabel(position: Int): String = when (position) {
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_AFTER -> "角色后"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_AT_DEPTH -> "深度"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_AN_TOP -> "作者注释上"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_AN_BOTTOM -> "作者注释下"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_EM_TOP -> "EM 上"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_EM_BOTTOM -> "EM 下"
        com.emberinn.engine.worldinfo.WorldInfoConstants.POSITION_OUTLET -> "出口"
        else -> "角色前"
    }

    private fun entryTokens(content: String): Int = runCatching {
        com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(chatRepository.profile()?.model.orEmpty()).count(content)
    }.getOrDefault(content.length / 4)

    private fun isUser(el: JsonElement): Boolean {
        val v = el.jsonObject["is_user"] ?: return false
        return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
    }

    private fun textOf(el: JsonElement): String =
        el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""
}
