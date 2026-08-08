package com.emberinn.app.ui.chat

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ProviderState
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    /** 上次发送命中的世界书条目（名字/主关键词），聊天页显示命中灯。 */
    private val _worldHits = MutableStateFlow<List<String>>(emptyList())
    val worldHits: StateFlow<List<String>> = _worldHits

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
    private var streamContinueMode = false
    private var currentCharName = "Assistant"
    private var currentUserName = "User"

    val characterId: String? = chatStore.get(sessionId)?.characterId
    private val character: CharacterRecord? =
        characterId?.let { id -> charStore.list().firstOrNull { it.id == id } }

    val accentColor: Long? = character?.seedColor
    val avatarPath: String? = character?.avatarPath

    fun saveProvider(profile: ConnectionProfile) {
        chatRepository.saveProfile(profile)
        ProviderState.refresh(profile)
    }

    fun send(text: String, userName: String = "User", media: List<MediaAttachment> = emptyList()) {
        if ((text.isBlank() && media.isEmpty()) || _isStreaming.value) return
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        currentCharName = charName
        currentUserName = userName
        _notice.value = null
        _impersonated.value = null
        chatStore.append(sessionId, true, text, userName, media)
        _pendingMedia.value = emptyList()
        refreshMessages()

        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
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
        _isStreaming.value = false
        _isImpersonating.value = false
        if (!wasImpersonating && _streamingReasoning.value.isNotBlank()) {
            _lastReasoning.value = _streamingReasoning.value
        }
        if (partial.isNotBlank()) {
            when {
                wasImpersonating -> _impersonated.value = partial
                wasContinue -> {
                    val after = chatStore.messages(sessionId).toMutableList()
                    val aiIdx = after.indexOfLast { !isUser(it) }
                    if (aiIdx >= 0) {
                        val combined = textOf(after[aiIdx]) + "\n" + partial
                        after[aiIdx] = JsonObject(after[aiIdx].jsonObject + ("mes" to JsonPrimitive(combined)))
                        chatStore.replace(sessionId, after)
                        refreshMessages()
                    }
                }
                else -> {
                    chatStore.append(sessionId, false, partial, currentCharName)
                    refreshMessages()
                }
            }
        }
        _streamingText.value = ""
        _streamingReasoning.value = ""
        streamContinueMode = false
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
        if (isUser(last)) return
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

    fun deleteMessage(index: Int) {
        if (_isStreaming.value) return
        chatStore.removeAt(sessionId, index)
        refreshMessages()
    }

    /** 编辑消息（官方 updateMessage：更新文本 + 清 extra.bias；regex/isEdit 待正则 UI 接线）。 */
    fun editMessage(index: Int, newText: String) {
        if (_isStreaming.value) return
        val text = newText.trim()
        if (text.isEmpty()) return
        chatStore.updateMessage(sessionId, index, text)
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
                val extension = extensionFor(type, displayName)
                val dir = java.io.File(app.filesDir, "media").apply { mkdirs() }
                val file = java.io.File(dir, "${System.currentTimeMillis()}_${displayName.hashCode().toUInt().toString(16)}.$extension")
                file.writeBytes(bytes)
                _pendingMedia.value = _pendingMedia.value + MediaAttachment(
                    type = mediaType,
                    url = file.absolutePath,
                    title = displayName,
                )
            }
        }
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

    private fun startStream(
        history: List<JsonElement>,
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonation: Boolean = false,
        cyclePrompt: String = "",
        continueMode: Boolean = false,
        mediaInlining: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        _streamingText.value = ""
        _streamingReasoning.value = ""
        _lastReasoning.value = null
        _worldHits.value = emptyList()
        _contextUsage.value = null
        _isStreaming.value = true
        _isImpersonating.value = impersonation
        streamContinueMode = continueMode
        streamActive = true
        // 提示词总装（世界书扫描/宏/历史/token 计数）较重：丢后台线程做，UI 不卡顿，
        // 先置“生成中”，组装完再真正发起请求（官方异步语义）。
        viewModelScope.launch(Dispatchers.Default) {
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
                onError = {
                    if (streamActive) {
                        streamActive = false
                        if (_streamingText.value.isBlank()) {
                            _notice.value = "（请求失败，请检查网络或 API Key 后重试。）"
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
                onPrepared = { info ->
                    if (streamActive) {
                        _worldHits.value = info.activatedWorldInfo
                            .map { it.name.ifBlank { it.keys.firstOrNull().orEmpty() } }
                            .filter { it.isNotBlank() }
                        _contextUsage.value = Pair(info.counts.values.sum(), info.maxContextTokens)
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
                    _notice.value = "（未配置模型，请先选一个模型。）"
                }
            } else {
                // 组装期间用户点了停止：直接取消刚建好的请求
                session?.cancel()
            }
        }
    }

    private fun finalizeStream(continueMode: Boolean = false) {
        _isStreaming.value = false
        streamSession = null
        val reply = _streamingText.value
        val wasImpersonating = _isImpersonating.value
        when {
            wasImpersonating -> {
                // 官方：冒充结果进输入框，不写历史
                if (reply.isNotBlank()) _impersonated.value = reply
                _isImpersonating.value = false
            }
            continueMode && reply.isNotBlank() -> {
                // 官方 mes_continue：续写追加回最后一条 AI 消息（思考过程一并保留）
                if (_streamingReasoning.value.isNotBlank()) _lastReasoning.value = _streamingReasoning.value
                val after = chatStore.messages(sessionId).toMutableList()
                val aiIdx = after.indexOfLast { !isUser(it) }
                if (aiIdx >= 0) {
                    val combined = textOf(after[aiIdx]) + "\n" + reply
                    after[aiIdx] = JsonObject(after[aiIdx].jsonObject + ("mes" to JsonPrimitive(combined)))
                    chatStore.replace(sessionId, after)
                    refreshMessages()
                }
            }
            _streamingReasoning.value.isNotBlank() -> {
                // 思考过程保留 + 正常追加回复
                _lastReasoning.value = _streamingReasoning.value
                if (reply.isNotBlank()) {
                    chatStore.append(sessionId, false, reply, currentCharName)
                    refreshMessages()
                }
            }
            reply.isNotBlank() -> {
                chatStore.append(sessionId, false, reply, currentCharName)
                refreshMessages()
            }
        }
        _streamingText.value = ""
        _streamingReasoning.value = ""
        streamContinueMode = false
    }

    private fun refreshMessages() {
        _messages.value = chatStore.messages(sessionId)
    }

    private fun isUser(el: JsonElement): Boolean {
        val v = el.jsonObject["is_user"] ?: return false
        return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
    }

    private fun textOf(el: JsonElement): String =
        el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""
}
