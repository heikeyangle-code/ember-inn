package com.emberinn.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.AppSlashExecutor
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ContextBudgetException
import com.emberinn.app.data.GroupRecord
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.data.GroupStore
import com.emberinn.app.data.ImageGenClient
import com.emberinn.app.data.Persona
import com.emberinn.app.data.PersonaStore
import com.emberinn.app.data.ProviderState
import com.emberinn.app.data.QuickReplyStore
import com.emberinn.app.data.ThemeState
import com.emberinn.app.data.SlashMessageActions
import com.emberinn.app.data.TranslateClient
import com.emberinn.app.data.TtsReader
import com.emberinn.app.data.TtsTextProcessor
import com.emberinn.app.data.VectorRagService
import com.emberinn.app.ui.settings.GlobalRegexPrefs
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.app.ui.settings.WorldInfoPrefs
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.group.GroupActivationEngine
import com.emberinn.engine.group.GroupCardMember
import com.emberinn.engine.group.GroupMember
import com.emberinn.engine.group.GroupMessage
import com.emberinn.engine.group.GroupCharacterCardsEngine
import com.emberinn.engine.group.AutoContinueSettings
import com.emberinn.engine.group.GroupDepthMember
import com.emberinn.engine.group.GroupDepthPromptsEngine
import com.emberinn.engine.group.GroupGenerationMode
import com.emberinn.engine.group.GroupLoopEngine
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.slash.AutoExecuteHandler
import com.emberinn.engine.slash.QuickReplySlot
import com.emberinn.engine.slash.SlashState
import com.emberinn.engine.slash.WorldInfoAutoExecute
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class ChatViewModel(application: Application, private val sessionId: String) : AndroidViewModel(application), SlashMessageActions {

    private val chatStore = ChatStore(application)
    private val charStore = CharacterStore(application)
    private val chatRepository = ChatRepository(application)
    private val quickReplyStore = QuickReplyStore(application)
    private val personaStore = PersonaStore(application)
    private val groupStore = GroupStore(application)
    private val translateClient = TranslateClient()
    private val imageGenClient = ImageGenClient()
    private val vectorRag = VectorRagService(application)
    private val slashExecutor = AppSlashExecutor(this)
    private val autoExecuteHandler = AutoExecuteHandler()

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

    /** 设置页改动快捷回复后刷新（聊天页 LaunchedEffect 调用）。 */
    fun refreshQuickReplies() {
        _quickReplies.value = quickReplyStore.slots()
    }

    fun runQuickReply(label: String) {
        if (_isStreaming.value) return
        val slot = quickReplyStore.load().slots.firstOrNull { it.label == label && it.enabled } ?: return
        runSlash(slot.mes)
    }

    /** 输入框以 / 开头：走斜杠命令（官方 ST 输入即执行；未知命令给提示，不当作普通消息发送）。 */
    fun runSlash(line: String) {
        if (_isStreaming.value) return
        _notice.value = null
        try {
            val output = slashExecutor.execute(line)
            if (output.isNotBlank()) _quickReplyOutput.value = output
        } catch (e: Exception) {
            _notice.value = "（${e.message ?: "斜杠命令执行失败"}）"
        }
    }

    fun consumeQuickReplyOutput() { _quickReplyOutput.value = null }

    /** 官方 quick-reply AutoExecuteHandler.handleWIActivation：世界书命中条目的 automationId 匹配槽位自动执行。 */
    private fun runAutoExecutions(activated: List<WorldInfoEntry>, type: String) {
        if (type == "impersonate") return
        val preset = quickReplyStore.load()
        val slots = WorldInfoAutoExecute.resolve(activated, listOf(preset))
        if (slots.isEmpty()) return
        if (!autoExecuteHandler.checkExecute()) return
        val state = SlashState()
        for (slot in slots) {
            autoExecuteHandler.withPrevent(slot) {
                runCatching {
                    val output = slashExecutor.execute(slot.mes, state)
                    if (output.isNotBlank()) _quickReplyOutput.value = output
                }
            }
        }
    }

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

    /** 图像生成（A1111）：成功则追加到待发送附件，用户可预览后发送。 */
    fun generateImage(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = imageGenClient.generate(getApplication(), prompt)
            withContext(Dispatchers.Main) {
                if (path != null) {
                    _pendingMedia.value = _pendingMedia.value + MediaAttachment(
                        type = "image",
                        url = path,
                        title = "生成的图片",
                    )
                } else {
                    _notice.value = "（图像生成失败：请检查 设置→服务→图像 的接口地址与来源。）"
                }
            }
        }
    }

    /** 翻译指定消息（P1-6 执行层；结果放 notice）。 */
    fun translateMessage(index: Int) {
        val msgs = chatStore.messages(sessionId)
        val text = msgs.getOrNull(index)?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = translateClient.translate(getApplication(), text)
            withContext(Dispatchers.Main) {
                _notice.value = if (result.isNullOrBlank()) {
                    "（翻译失败：请检查 设置→服务→翻译 的提供商/Key/接口地址。）"
                } else {
                    "译文：$result"
                }
            }
        }
    }

    // ---- 书签（官方 checkpoint 存档语义）----

    private val _bookmarks = MutableStateFlow(chatStore.bookmarkNames(sessionId))
    val bookmarks: StateFlow<List<String>> = _bookmarks

    fun defaultBookmarkName(): String = "Checkpoint #${chatStore.bookmarkNames(sessionId).size + 1}"

    fun createBookmark(name: String) {
        if (chatStore.createBookmark(sessionId, name)) {
            _bookmarks.value = chatStore.bookmarkNames(sessionId)
            refreshMessages()
        }
    }

    fun openBookmark(name: String) {
        if (chatStore.openBookmark(sessionId, name)) refreshMessages()
    }

    fun deleteBookmark(name: String) {
        chatStore.deleteBookmark(sessionId, name)
        _bookmarks.value = chatStore.bookmarkNames(sessionId)
    }

    // ---- 向量检索 / 数据银行（官方 vectors 扩展 Data Bank；本 App 存 filesDir/databank/）----

    private val _dataBank = MutableStateFlow(vectorRag.dataBankNames())
    val dataBank: StateFlow<List<String>> = _dataBank

    fun refreshDataBank() {
        _dataBank.value = vectorRag.dataBankNames()
    }

    fun addDataBankFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val resolver = app.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.isEmpty()) return@launch
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: "data-${System.currentTimeMillis()}.txt"
                vectorRag.saveDataBankFile(displayName, bytes)
            }
            withContext(Dispatchers.Main) { refreshDataBank() }
        }
    }

    fun removeDataBankFile(name: String) {
        vectorRag.deleteDataBankFile(name)
        refreshDataBank()
    }

    // ---- SlashMessageActions（消息类斜杠命令；对齐官方 slash-commands.js）----

    override fun sendAsCharacter(name: String, text: String): String {
        if (text.isBlank()) return ""
        // 官方 sendas：name 缺省用当前角色名
        chatStore.appendManualMessage(sessionId, isUser = false, content = text, name = name.ifBlank { currentCharName })
        refreshMessages()
        return ""
    }

    override fun sendAsUser(text: String): String {
        if (text.isBlank()) return ""
        chatStore.appendManualMessage(sessionId, isUser = true, content = text, name = currentUserName)
        refreshMessages()
        return ""
    }

    override fun sendSystemMessage(text: String, name: String): String {
        if (text.isBlank()) return ""
        chatStore.appendNarratorMessage(
            sessionId,
            content = text,
            name = name.ifBlank { chatStore.narratorName(sessionId) },
        )
        refreshMessages()
        return ""
    }

    override fun setNarratorName(name: String): String {
        // 官方 setNarratorName：空名重置为 System（写死默认值，不是删键）
        val resolved = name.trim().ifBlank { "System" }
        chatStore.setNarratorName(sessionId, resolved)
        _notice.value = "（旁白显示名已设置为 $resolved）"
        return ""
    }

    override fun sendComment(text: String): String {
        if (text.isBlank()) return ""
        chatStore.appendCommentMessage(sessionId, content = text)
        refreshMessages()
        return ""
    }

    override fun getSetMessageRole(at: Int, role: String): String {
        val result = chatStore.setMessageRole(sessionId, at, role)
        refreshMessages()
        return result
    }

    override fun getSetMessageName(at: Int, name: String): String {
        val result = chatStore.setMessageName(sessionId, at, name)
        refreshMessages()
        return result
    }

    override fun hideMessage(index: Int, hidden: Boolean): String {
        chatStore.setMessageHidden(sessionId, index, hidden)
        refreshMessages()
        return ""
    }

    override fun deleteMessagesByName(name: String): Int {
        val count = chatStore.deleteMessagesByName(sessionId, name)
        refreshMessages()
        return count
    }

    override fun addSwipe(text: String, switch: Boolean): String {
        val id = chatStore.addSwipeManual(sessionId, text, switch)
        refreshMessages()
        return id
    }

    override fun deleteSwipe(id: Int?): String {
        val newId = chatStore.deleteSwipeManual(sessionId, id)
        refreshMessages()
        return newId
    }

    override fun notify(text: String) {
        _notice.value = "（$text）"
    }

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

    /** 人设（官方 Persona Management：全局列表 + active，persona_description 进提示词）。 */
    private val _personas = MutableStateFlow(personaStore.list())
    val personas: StateFlow<List<Persona>> = _personas
    private val _activePersona = MutableStateFlow(personaStore.active())
    val activePersona: StateFlow<Persona?> = _activePersona

    fun setPersona(id: String) {
        personaStore.setActive(id)
        _activePersona.value = personaStore.active()
        _personas.value = personaStore.list()
    }

    fun savePersona(persona: Persona) {
        val list = personaStore.list()
        val next = if (list.any { it.id == persona.id }) {
            list.map { if (it.id == persona.id) persona else it }
        } else {
            list + persona
        }
        personaStore.save(next, activeId = persona.id)
        _personas.value = personaStore.list()
        _activePersona.value = personaStore.active()
    }

    fun deletePersona(id: String) {
        personaStore.save(personaStore.list().filterNot { it.id == id })
        _personas.value = personaStore.list()
        _activePersona.value = personaStore.active()
    }

    val characterId: String? = chatStore.get(sessionId)?.characterId
    val character: CharacterRecord?
        get() = characterId?.let { id -> charStore.list().firstOrNull { it.id == id } }

    /** 群聊：每次访问实时读（群聊设置保存后立即生效，不缓存旧模式/策略）。 */
    val group: GroupRecord?
        get() = chatStore.get(sessionId)?.groupId?.let { groupStore.get(it) }
    val groupMembers: List<CharacterRecord>
        get() = group?.members?.mapNotNull { id -> charStore.list().firstOrNull { it.id == id } } ?: emptyList()

    val accentColor: Long?
        get() = character?.seedColor
    val avatarPath: String?
        get() = character?.avatarPath

    /** 角色卡/主题编辑后返回聊天：刷新第三层主题、头像与聊天背景（character getter 实时读盘）。 */
    fun refreshTheme() {
        ThemeState.update(
            recipe = character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson) },
            seedColor = character?.seedColor,
        )
        _chatBackground.value = chatStore.metadata(sessionId)["custom_background"]?.jsonPrimitive?.contentOrNull
            ?: character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson).background }?.ifBlank { null }
    }

    /** 聊天背景：会话锁定（chat_metadata.custom_background）优先，否则角色主题配方 background。 */
    private val _chatBackground = MutableStateFlow(
        chatStore.metadata(sessionId)["custom_background"]?.jsonPrimitive?.contentOrNull
            ?: character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson).background }?.ifBlank { null },
    )
    val chatBackground: StateFlow<String?> = _chatBackground

    /** 作者注释（官方 authors-note.js 元数据键：note_prompt/note_position/note_depth/note_role）。 */
    data class AuthorsNoteDraft(val prompt: String, val position: Int, val depth: Int, val role: Int)

    fun authorsNoteDraft(): AuthorsNoteDraft {
        val meta = chatStore.metadata(sessionId)
        return AuthorsNoteDraft(
            prompt = meta["note_prompt"]?.jsonPrimitive?.contentOrNull ?: "",
            position = meta["note_position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2,
            depth = meta["note_depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
            role = meta["note_role"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
    }

    fun saveAuthorsNote(prompt: String, position: Int, depth: Int, role: Int) {
        val meta = chatStore.metadata(sessionId).toMutableMap()
        if (prompt.isBlank()) meta.remove("note_prompt") else meta["note_prompt"] = JsonPrimitive(prompt)
        meta["note_position"] = JsonPrimitive(position)
        meta["note_depth"] = JsonPrimitive(depth)
        meta["note_role"] = JsonPrimitive(role)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _notice.value = if (prompt.isBlank()) "（作者注释已清除）" else "（作者注释已保存，下次发送生效）"
    }

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

    init {
        // 第三层主题（角色配方）：当前角色进入全局主题管线；离开聊天由 ChatScreen 清空回全局
        ThemeState.update(
            recipe = character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson) },
            seedColor = character?.seedColor,
        )
        // 官方新聊天第一条消息 = 角色开场白 first_mes（script.js newChat 语义）；空会话才补。
        // README：AI 对话（无角色卡）带默认开场“我是余烬，想聊点什么？”
        val isGroupSession = chatStore.get(sessionId)?.groupId != null
        if (chatStore.messages(sessionId).isEmpty() && !isGroupSession) {
            val charName = chatStore.get(sessionId)?.name ?: "Assistant"
            val currentCharacter = character
            val firstMes = if (currentCharacter != null) {
                firstMesOf(currentCharacter.rawJson)
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

    /** @return 是否已接受（成功发送/执行斜杠）；false 时调用方不要清空输入框。 */
    fun send(
        text: String,
        userName: String = "User",
        media: List<MediaAttachment> = emptyList(),
        mediaDisplay: String? = null,
        mediaIndex: Int? = null,
    ): Boolean {
        if ((text.isBlank() && media.isEmpty()) || _isStreaming.value) return false
        // 官方 ST：输入以 / 开头即斜杠命令（消息类直接插消息，不触发生成；未知命令只提示不发送）
        if (text.trimStart().startsWith("/") && media.isEmpty()) {
            runSlash(text.trim())
            return true
        }
        // 未配置模型先拦住：只显示提示，不写历史（避免悬空用户消息之后真的发给模型）。
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return false
        }
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        currentCharName = charName
        currentUserName = userName
        _notice.value = null
        _impersonated.value = null
        chatStore.append(sessionId, true, text, userName, media, mediaDisplay = mediaDisplay, mediaIndex = mediaIndex)
        _pendingMedia.value = emptyList()
        refreshMessages()
        val voice = VoicePrefs.read(getApplication())
        if (voice.enabled && voice.narrateUser) {
            narrateText(text)
        }
        if (group != null) {
            startGroupTurn(type = "generate")
        } else {
            startStream(
                history = chatStore.messages(sessionId),
            )
        }
        return true
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
                        val profile = chatRepository.profile()
                        chatStore.appendToCurrentSwipe(
                            sessionId, aiIdx, partial,
                            api = profile?.providerId,
                            model = profile?.model,
                            reasoning = _streamingReasoning.value.takeIf { it.isNotBlank() },
                        )
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
        if (isUser(last) || isSystemMsg(last)) {
            _notice.value = "（最后一条不是可重新生成的 AI 回复。）"
            return
        }
        // 先检查配置再删回复：未配置时绝不丢最后一条 AI 回复
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再重新生成。）"
            return
        }
        chatStore.removeAt(sessionId, msgs.lastIndex)
        refreshMessages()
        if (group != null) {
            startGroupTurn(type = "regenerate")
        } else {
            startStream(history = chatStore.messages(sessionId))
        }
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
        if (isSystemMsg(last)) {
            _notice.value = "（最后一条是系统/隐藏消息，不能继续生成。）"
            return
        }
        val lastText = textOf(last)
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
        }
        if (group != null) {
            startGroupTurn(type = "continue", cyclePrompt = lastText)
        } else {
            startStream(
                history = msgs,
                type = "continue",
                cyclePrompt = lastText,
                continueMode = true,
            )
        }
    }

    /** 滑动切回复：左滑 = 上一个变体（对齐官方 swipe_left，越界 wrap 回最后一条）。 */
    fun swipeLeft(index: Int) {
        if (_isStreaming.value) return
        val el = chatStore.messages(sessionId).getOrNull(index) ?: return
        if (isSystemMsg(el)) return
        if (!chatStore.ensureSwipes(sessionId, index)) return
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
        val msgs = chatStore.messages(sessionId)
        val el = msgs.getOrNull(index) ?: return
        if (isSystemMsg(el)) return
        if (!chatStore.ensureSwipes(sessionId, index)) return
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
        if (isUser(last) || isSystemMsg(last)) {
            _notice.value = "（最后一条不是可生成变体的 AI 回复。）"
            return
        }
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

    /** 变体列表（swipe picker 用；先 ensureSwipes 保证字段齐）。 */
    fun swipeVariantsOf(index: Int): List<String> {
        if (index !in chatStore.messages(sessionId).indices) return emptyList()
        chatStore.ensureSwipes(sessionId, index)
        return chatStore.swipesOf(chatStore.messages(sessionId)[index])
    }

    /** 跳转到指定变体（swipe picker 点击；对齐官方 swipe）。 */
    fun swipeToVariant(index: Int, variant: Int) {
        if (chatStore.swipeTo(sessionId, index, variant)) refreshMessages()
    }

    /** 图库模式左右滑：更新消息 extra.media_index（对齐官方 gallery media_index）。 */
    fun setMediaIndex(messageIndex: Int, mediaIndex: Int) {
        val list = chatStore.messages(sessionId).toMutableList()
        if (messageIndex !in list.indices) return
        val el = list[messageIndex].jsonObject
        val extra = (el["extra"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        extra["media_index"] = JsonPrimitive(mediaIndex)
        list[messageIndex] = JsonObject(el + ("extra" to JsonObject(extra)))
        chatStore.replace(sessionId, list)
        refreshMessages()
    }

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

    // ---- 群聊调度（P2-9）：官方 GroupScheduler 选人 + GroupCharacterCardsEngine 合并 + 顺序生成 ----

    private data class GroupStep(
        val speaker: CharacterRecord,
        val cardJson: String,
        val type: String,
        val cyclePrompt: String = "",
        val inChatExtensions: List<PromptItem> = emptyList(),
    )

    private fun startGroupTurn(type: String, cyclePrompt: String = "") {
        val members = groupMembers
        if (members.isEmpty()) {
            _notice.value = "（群聊成员缺失，请检查群聊设置。）"
            return
        }
        val history = chatStore.messages(sessionId)
        val disabled = group?.disabledMembers.orEmpty()
        val enabled = members.filter { it.id !in disabled }
        if (enabled.isEmpty()) {
            _notice.value = "（群聊没有可用成员。）"
            return
        }
        val speakers = when (type) {
            "regenerate", "continue" -> {
                val lastName = history.lastOrNull { !isUser(it) && !isSystemMsg(it) }
                    ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                enabled.filter { it.name == lastName }.ifEmpty { listOf(enabled.last()) }
            }
            else -> {
                val strategy = group?.activationStrategy ?: "natural"
                if (strategy == "natural" || strategy == "pooled") {
                    // 官方 activateNatural / activatePooled：输入词命中成员名 + 话痨概率
                    val membersForActivation = members.map { m ->
                        GroupMember(
                            avatar = m.id,
                            name = m.name,
                            talkativeness = CharacterCardEdit.readFields(m.rawJson, m.name, m.description).talkativeness.toDouble(),
                        )
                    }
                    val lastMessage = history.lastOrNull()?.let { el ->
                        GroupMessage(
                            name = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            isUser = isUser(el),
                            isSystem = el.jsonObject["is_system"]?.jsonPrimitive?.content == "true",
                            originalAvatar = null,
                        )
                    }
                    val lastUserText = history.lastOrNull { isUser(it) }
                        ?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val activated = if (strategy == "natural") {
                        GroupActivationEngine.natural(
                            members = membersForActivation,
                            input = lastUserText,
                            lastMessage = lastMessage,
                            allowSelfResponses = false,
                            isUserInput = true,
                        )
                    } else {
                        GroupActivationEngine.pooled(
                            members = membersForActivation,
                            chat = history.map { el ->
                                GroupMessage(
                                    name = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                    isUser = isUser(el),
                                    isSystem = el.jsonObject["is_system"]?.jsonPrimitive?.content == "true",
                                    originalAvatar = null,
                                )
                            },
                            lastMessage = lastMessage,
                            isUserInput = true,
                        )
                    }
                    val byId = activated.mapNotNull { id -> members.firstOrNull { it.id == id } }
                    byId.ifEmpty { enabled }
                } else if (group?.generationMode == GroupGenerationMode.SWAP) {
                    val lastSpeaker = history.lastOrNull { !isUser(it) && !isSystemMsg(it) }
                        ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    val idx = enabled.indexOfFirst { it.name == lastSpeaker }
                    listOf(enabled[(idx + 1).coerceAtMost(enabled.lastIndex)])
                } else {
                    enabled
                }
            }
        }
        if (speakers.size > 1) {
            _notice.value = "群聊：本轮 ${speakers.size} 位成员依次回复（${speakers.joinToString(" → ") { it.name }}）"
        }
        val steps = speakers.map { speaker ->
            val cardJson = if (group?.generationMode == GroupGenerationMode.SWAP) {
                speaker.rawJson
            } else {
                buildGroupCardJson(speaker)
            }
            GroupStep(
                speaker = speaker,
                cardJson = cardJson,
                type = if (type == "regenerate" || type == "continue") type else "generate",
                cyclePrompt = cyclePrompt,
                inChatExtensions = buildGroupDepthPrompts(speaker),
            )
        }
        runGroupStep(steps, 0, history)
    }

    private fun runGroupStep(
        steps: List<GroupStep>,
        index: Int,
        history: List<JsonElement>,
        autoContinueRuns: Int = 0,
    ) {
        if (index >= steps.size) return
        val step = steps[index]
        currentCharName = step.speaker.name
        val isLastStep = index == steps.lastIndex
        startStream(
            history = history,
            type = step.type,
            cyclePrompt = step.cyclePrompt,
            continueMode = step.type == "continue",
            characterRawJsonOverride = step.cardJson,
            inChatExtensions = step.inChatExtensions,
            onFinished = {
                val msgs = chatStore.messages(sessionId)
                // 官方 generateGroupWrapper：每人生成后按 shouldAutoContinue 自动续写（power_user.auto_continue，默认关）
                val lastAi = msgs.lastOrNull { !isUser(it) && !isSystemMsg(it) }
                val lastText = lastAi?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull.orEmpty()
                val should = autoContinueRuns < 5 && GroupLoopEngine.shouldAutoContinue(
                    messageChunk = lastText.ifBlank { null },
                    isImpersonate = false,
                    settings = AutoContinueSettings(
                        enabled = GenerationPrefs.autoContinueEnabled(getApplication()),
                        targetLength = GenerationPrefs.autoContinueTargetLength(getApplication()),
                        allowChatCompletions = GenerationPrefs.allowChatCompletions(getApplication()),
                    ),
                    userInputEmpty = msgs.isEmpty() || !isUser(msgs.last()),
                    lastMessageTokens = runCatching {
                        com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(chatRepository.profile()?.model.orEmpty()).count(lastText)
                    }.getOrNull(),
                    isOpenAi = chatRepository.profile()?.let { profile ->
                        com.emberinn.engine.provider.ProviderRegistry.get(profile.providerId)?.protocol != "anthropic"
                    } ?: true,
                )
                when {
                    should -> runGroupStep(
                        listOf(step.copy(type = "continue", cyclePrompt = lastText)),
                        0,
                        msgs,
                        autoContinueRuns + 1,
                    )
                    !isLastStep -> runGroupStep(steps, index + 1, msgs)
                }
            },
        )
    }

    /** 群聊深度提示（官方 getGroupDepthPrompts → setExtensionPrompt(IN_CHAT, depth, role)）。 */
    private fun buildGroupDepthPrompts(speaker: CharacterRecord): List<PromptItem> {
        if (group?.generationMode == GroupGenerationMode.SWAP) return emptyList()
        val speakerIndex = groupMembers.indexOfFirst { it.id == speaker.id }
        val prompts = GroupDepthPromptsEngine.collect(
            groupId = group?.id ?: "",
            generationMode = group?.generationMode ?: GroupGenerationMode.APPEND,
            members = groupMembers.map { it.id },
            disabledMembers = group?.disabledMembers.orEmpty(),
            characterCards = groupMembers.map { m ->
                val f = CharacterCardEdit.readFields(m.rawJson, m.name, m.description)
                GroupDepthMember(
                    avatar = m.id,
                    name = m.name,
                    depthPrompt = f.depthPrompt,
                    depth = f.depthPromptDepth.toIntOrNull() ?: 4,
                    role = f.depthPromptRole.ifBlank { "system" },
                )
            },
            characterId = speakerIndex,
        )
        return prompts.mapIndexed { i, p ->
            PromptItem(
                identifier = "groupDepthPrompt$i",
                name = "群聊深度提示 ${i + 1}",
                content = p.text,
                role = p.role,
                injectionDepth = p.depth,
                injectionOrder = 100,
            )
        }
    }

    /** APPEND 模式：官方 getGroupCharacterCards 合并成员卡字段 → 合成卡 JSON 喂总装。 */
    private fun buildGroupCardJson(speaker: CharacterRecord): String {
        val merged = GroupCharacterCardsEngine.cards(
            groupId = group?.id ?: "",
            generationMode = GroupGenerationMode.APPEND,
            members = groupMembers.map { it.id },
            disabledMembers = group?.disabledMembers.orEmpty(),
            joinPrefix = "",
            joinSuffix = "",
            characterCards = groupMembers.map { m ->
                GroupCardMember(
                    avatar = m.id,
                    name = m.name,
                    description = CharacterCardEdit.readFields(m.rawJson, m.name, m.description).description,
                    personality = CharacterCardEdit.readFields(m.rawJson, m.name, m.description).personality,
                    scenario = CharacterCardEdit.readFields(m.rawJson, m.name, m.description).scenario,
                    mesExample = CharacterCardEdit.readFields(m.rawJson, m.name, m.description).mesExample,
                )
            },
        )
        val d = merged?.description.orEmpty()
        val p = merged?.personality.orEmpty()
        val s = merged?.scenario.orEmpty()
        val e = merged?.mesExamples.orEmpty()
        return buildJsonObject {
            put("spec", JsonPrimitive("chara_card_v2"))
            put(
                "data",
                buildJsonObject {
                    put("name", JsonPrimitive(speaker.name))
                    put("description", JsonPrimitive(d))
                    put("personality", JsonPrimitive(p))
                    put("scenario", JsonPrimitive(s))
                    put("mes_example", JsonPrimitive(e))
                },
            )
        }.toString()
    }

    /** 当前会话名（实时读盘；/renamechat 后顶栏立即刷新）。 */
    fun sessionName(): String = chatStore.sessionName(sessionId).ifBlank { currentCharName }

    /** 群聊设置：生成模式（SWAP/APPEND）+ 激活策略（natural/pooled）。 */
    fun saveGroupSettings(generationMode: Int, activationStrategy: String) {
        group?.let {
            groupStore.save(it.copy(generationMode = generationMode, activationStrategy = activationStrategy))
        }
    }

    private fun startStream(
        history: List<JsonElement>,
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonation: Boolean = false,
        cyclePrompt: String = "",
        continueMode: Boolean = false,
        swipeMode: Boolean = false,
        mediaInlining: Boolean = true,
        characterRawJsonOverride: String? = null,
        inChatExtensions: List<PromptItem> = emptyList(),
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
            // 向量 RAG（官方 vectors 扩展）：设置页开关 + 数据银行；嵌入配置不完整时本轮禁用并提示
            val rag = VectorRagService(getApplication())
            val vectorStore = rag.store()
            val vectorSettings = rag.chatSettings()
            val vectorWorldSettings = rag.worldSettings()
            val vectorDataBank = rag.dataBankFiles()
            val worldInfoSettings = WorldInfoPrefs.read(getApplication())
            val globalRegexScripts = GlobalRegexPrefs.read(getApplication())
            if (rag.enabled() && vectorStore == null) {
                _notice.value = "（向量检索已开启，但嵌入服务未配置完整（地址/Key/模型），本轮未启用向量检索。）"
            }
            val session = chatRepository.streamPrepared(
                characterRawJson = characterRawJsonOverride ?: character?.rawJson,
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
                personaDescription = _activePersona.value?.description.orEmpty(),
                personaInPrompt = _activePersona.value != null,
                vectorStore = vectorStore,
                vectorChatSettings = vectorSettings,
                vectorWorldSettings = vectorWorldSettings,
                vectorDataBank = vectorDataBank,
                vectorFileText = { path -> rag.readDataBankText(path) },
                inChatExtensions = inChatExtensions,
                worldInfoSettings = worldInfoSettings,
                globalRegexScripts = globalRegexScripts,
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
                        // 官方 WORLD_INFO_ACTIVATED → 自动执行 automationId 匹配的快捷回复
                        runAutoExecutions(info.activatedWorldInfo, type)
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
                    val profile = chatRepository.profile()
                    chatStore.appendToCurrentSwipe(
                        sessionId, aiIdx, reply,
                        api = profile?.providerId,
                        model = profile?.model,
                        reasoning = _streamingReasoning.value.takeIf { it.isNotBlank() },
                    )
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

    /** 系统消息（/hide 隐藏、/comment 注释；官方 coreChat 过滤 is_system）。 */
    private fun isSystemMsg(el: JsonElement): Boolean {
        val v = el.jsonObject["is_system"] ?: return false
        return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
    }

    /** 最后一条是否为可继续的 AI 消息（非用户、非系统；官方 coreChat 语义）。 */
    fun canContinueGeneration(): Boolean {
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return false
        return !isUser(last) && !isSystemMsg(last)
    }

    private fun textOf(el: JsonElement): String =
        el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""
}
