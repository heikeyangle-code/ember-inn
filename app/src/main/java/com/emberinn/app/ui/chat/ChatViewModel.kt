package com.emberinn.app.ui.chat

import android.app.Application
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.AppSlashExecutor
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.data.ChatPromptFactory
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ContextBudgetException
import com.emberinn.app.data.DisplayCacheVersion
import com.emberinn.app.data.DisplayPipeline
import com.emberinn.app.data.MemoryService
import com.emberinn.app.data.GroupRecord
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.data.GroupStore
import com.emberinn.app.data.ImageGenClient
import com.emberinn.app.data.Persona
import com.emberinn.app.data.PersonaStore
import com.emberinn.engine.persona.PersonaEngine
import com.emberinn.app.data.ProviderState
import com.emberinn.app.data.QuickReplyStore
import com.emberinn.app.data.ThemeState
import com.emberinn.app.data.ManualSendResult
import com.emberinn.app.data.PromptManagerPrefs
import com.emberinn.app.data.SlashMessageActions
import com.emberinn.app.data.TranslateClient
import com.emberinn.app.data.TtsReader
import com.emberinn.app.data.TtsTextProcessor
import com.emberinn.app.data.VectorRagService
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.settings.BehaviorPrefs
import com.emberinn.app.ui.settings.CaptionPrefs
import com.emberinn.app.ui.settings.GlobalRegexPrefs
import com.emberinn.app.ui.settings.MemoryPrefs
import com.emberinn.app.ui.settings.AuthorsNotePrefsStore
import com.emberinn.app.ui.settings.CharaNoteData
import com.emberinn.app.ui.settings.RenderPrefs
import com.emberinn.app.ui.settings.ServicesPrefs
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.app.ui.settings.WorldInfoPrefs
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.regex.RegexPipelineEngine
import com.emberinn.engine.regex.RegexPipelineScript
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
import com.emberinn.engine.prompt.BiasEngine
import com.emberinn.engine.prompt.AutoContinueConfig
import com.emberinn.engine.prompt.AutoContinueEngine
import com.emberinn.engine.prompt.ExtensionPromptEngine
import com.emberinn.engine.prompt.CleanUpConfig
import com.emberinn.engine.prompt.CleanUpMessageEngine
import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.CustomStoppingConfig
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.media.MediaEngine
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.ToolCall
import com.emberinn.engine.prompt.ToolLoopPlanner
import com.emberinn.engine.prompt.StoppingStringsConfig
import com.emberinn.engine.prompt.StoppingStringsEngine
import com.emberinn.app.data.ToolRegistry
import com.emberinn.engine.slash.AutoExecuteHandler
import com.emberinn.engine.slash.QuickReplySlot
import com.emberinn.engine.slash.SlashState
import com.emberinn.engine.slash.WorldInfoAutoExecute
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
    private val memoryService by lazy { MemoryService(application, chatRepository, chatStore) }
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

    /** 显示文本缓存：displayTextOf 只在消息刷新时算一次，组合期不再读盘/跑正则（性能）。
     *  设置（encode_tags/正则/允许列表）变更时 DisplayCacheVersion.bump()，这里整体失效即时生效。 */
    private val displayCache = mutableMapOf<Int, String>()
    private var displayCacheVersion = -1

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

    private val _promptPreview = MutableStateFlow<Pair<String, Int>?>(null)
    val promptPreview: StateFlow<Pair<String, Int>?> = _promptPreview

    /** dryRun 提示词预览：只总装不发送（官方 Generate dryRun），结果供 UI 展示。 */
    fun previewPrompt() {
        if (_isStreaming.value) return
        _promptPreview.value = null
        startStream(
            history = chatStore.messages(sessionId),
            type = "generate",
            previewOnly = true,
            onPreview = { _promptPreview.value = it },
        )
    }

    fun consumePromptPreview() {
        _promptPreview.value = null
    }

    /** 全部可执行斜杠命令清单（App 命令 + 引擎命令），供输入框补全弹层使用。 */
    fun slashCommandList(): List<Pair<String, String>> = slashExecutor.commandList()

    /** 输入框以 / 开头：走斜杠命令（官方 ST 输入即执行；未知命令给提示，不当作普通消息发送）。 */
    fun runSlash(line: String) {
        if (_isStreaming.value) return
        _notice.value = null
        // 异步执行：/gen /genraw 需要等待生成；同步命令行为不变
        viewModelScope.launch {
            try {
                val output = slashExecutor.executeAsync(line)
                if (output.isNotBlank()) _quickReplyOutput.value = output
            } catch (e: Exception) {
                _notice.value = "（${e.message ?: "斜杠命令执行失败"}）"
            }
        }
    }

    /** 官方 /gen：用当前聊天上下文 + 提示生成文本（不落盘），返回生成文本。 */
    override suspend fun generateText(prompt: String, length: Int?): String {
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            return "（未配置模型，请先选一个模型。）"
        }
        val history = chatStore.messages(sessionId).toMutableList()
        history += buildJsonObject {
            put("name", JsonPrimitive(currentUserName))
            put("is_user", JsonPrimitive(true))
            put("is_system", JsonPrimitive(false))
            put("send_date", JsonPrimitive(java.time.Instant.now().toString()))
            put("mes", JsonPrimitive(prompt))
            put("extra", buildJsonObject {})
        }
        return chatRepository.chat(history, maxTokensOverride = length) ?: "（生成失败）"
    }

    /** 官方 /genraw：直接用提示请求（system/prefill/length 可选），返回生成文本。 */
    override suspend fun generateRaw(
        prompt: String,
        system: String,
        prefill: String,
        length: Int?,
        instruct: Boolean,
        asRole: String,
        stop: String,
        trim: Boolean,
    ): String {
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            return "（未配置模型，请先选一个模型。）"
        }
        // 官方 genraw：stop 是 JSON 数组（一次性停用词）；instruct/as 在 App 无 instruct 模式，登记边界
        val stops = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .parseToJsonElement(stop).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        val result = chatRepository.rawGenerate(prompt, system, prefill, length, stops) ?: "（生成失败）"
        if (!trim) return result
        var out = result
        for (prefix in listOf("$currentUserName:", "$currentCharName:")) {
            while (out.startsWith(prefix)) out = out.removePrefix(prefix).trimStart()
        }
        return out
    }

    /** 官方 /summarize：无文本 → forceSummarizeChat；有文本 → generateRaw + 当前总结设置。 */
    override suspend fun summarize(text: String, source: String?, prompt: String?, quiet: Boolean): String {
        val s = memoryService.settings()
        if (text.isBlank()) {
            if (s.source != "main") return ""
            return kotlin.coroutines.suspendCoroutine { cont ->
                memoryService.forceSummarize(sessionId) { cont.resume(it) }
            }
        }
        if (source != null && source != "main") return ""
        val wordsPrompt = (prompt?.takeIf { it.isNotBlank() } ?: s.prompt)
            .replace("{{words}}", s.promptWords.toString())
        val substituted = MacroEngine.substitute(wordsPrompt, MacroEnv(user = currentUserName, char = currentCharName))
        return chatRepository.rawGenerate(text, substituted, "", s.overrideResponseLength.takeIf { it > 0 }) ?: ""
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

    /** 当前激活的预设正则集 + 允许标记（官方 preset_allowed_regex[api]，App 固定 openai）。 */
    private fun presetRegex(): Pair<List<RegexPipelineScript>, Boolean> {
        val sets = GlobalRegexPrefs.presetSets(getApplication())
        val active = GlobalRegexPrefs.activePresetSet(getApplication())
        val allowed = GlobalRegexPrefs.presetAllowed(getApplication(), "openai")
        return (sets[active].orEmpty()) to (active.isNotBlank() && active in allowed)
    }

    /** 官方 translate 扩展自动翻译模式（none/responses/inputs/both）。 */
    private fun translateAutoMode(): String = ServicesPrefs.translateAutoMode(getApplication())

    /** 官方 translateIncomingMessage：AI 回复译文写 extra.display_text（原文保留），推理写 extra.reasoning_display_text。 */
    private fun translateIncoming(index: Int, reasoning: String? = null) {
        val mode = translateAutoMode()
        if (mode != "responses" && mode != "both") return
        val msgs = chatStore.messages(sessionId)
        val el = msgs.getOrNull(index)?.jsonObject ?: return
        val text = el["mes"]?.jsonPrimitive?.contentOrNull ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val translated = translateClient.translate(getApplication(), text)
            val reasoningTranslated = if (!reasoning.isNullOrBlank()) {
                translateClient.translate(getApplication(), reasoning)
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                if (translated.isNullOrBlank() && reasoningTranslated.isNullOrBlank()) return@withContext
                chatStore.setDisplayText(
                    sessionId,
                    index,
                    displayText = translated,
                    reasoningDisplayText = reasoningTranslated,
                )
                refreshMessages()
            }
        }
    }

    /** 官方 translateOutgoingMessage：用户消息 mes 换成译文，原文存 extra.display_text。 */
    private fun translateOutgoing(index: Int) {
        val mode = translateAutoMode()
        if (mode != "inputs" && mode != "both") return
        val msgs = chatStore.messages(sessionId)
        val el = msgs.getOrNull(index)?.jsonObject ?: return
        val text = el["mes"]?.jsonPrimitive?.contentOrNull ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val translated = translateClient.translate(getApplication(), text) ?: return@launch
            withContext(Dispatchers.Main) {
                chatStore.setDisplayText(sessionId, index, displayText = translated, replaceMes = true)
                refreshMessages()
            }
        }
    }

    /**
     * 显示文本管线，对齐官方 script.js messageFormatting：
     * 显示位点正则（isMarkdown=true，仅 markdownOnly 脚本生效，depth=可用消息数-位置-1）
     * → fixMarkdown(forDisplay=true) → encode_tags（可选）。
     * 只影响显示，不改落盘文本。
     */
    fun displayTextOf(index: Int): String {
        if (displayCacheVersion != DisplayCacheVersion.version) {
            displayCache.clear()
            displayCacheVersion = DisplayCacheVersion.version
        }
        displayCache[index]?.let { return it }
        val el = _messages.value.getOrNull(index)?.jsonObject ?: return ""
        val extra = el["extra"] as? JsonObject
        val base = extra?.get("display_text")?.jsonPrimitive?.contentOrNull
            ?: el["mes"]?.jsonPrimitive?.contentOrNull ?: return ""
        // 官方 messageFormatting：系统消息不走显示位点正则、不做 encode_tags；fixMarkdown 仍然执行
        val isSystem = isSystemMsg(el)
        var out = base
        if (!isSystem && GlobalRegexPrefs.enabled(getApplication())) {
            val (presetScripts, presetAllowed) = presetRegex()
            val scripts = ChatPromptFactory().resolveRegexScripts(
                characterRawJson = character?.rawJson,
                globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
                scopedAllowed = character?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
                presetScripts = presetScripts,
                presetAllowed = presetAllowed,
            )
            val isUser = isUser(el)
            val isNarrator = extra?.get("type")?.jsonPrimitive?.contentOrNull == "narrator"
            val placement = when {
                isUser -> ChatPromptFactory.REGEX_USER_INPUT
                isNarrator -> ChatPromptFactory.REGEX_SLASH_COMMAND
                else -> ChatPromptFactory.REGEX_AI_OUTPUT
            }
            // 官方 depth：usableMessages.length - indexOf - 1（usable 不含系统消息）
            val usable = _messages.value.filterNot { isSystemMsg(it) }
            val pos = usable.indexOfFirst { it == el }
            val depth = if (pos >= 0) usable.size - pos - 1 else null
            out = RegexPipelineEngine.apply(
                raw = base,
                placement = placement,
                scripts = scripts,
                isMarkdown = true,
                depth = depth,
                characterOverride = el["name"]?.jsonPrimitive?.contentOrNull ?: currentCharName,
            )
        }
        // 官方 auto_fix_generated_markdown（默认开）：仅开时 fixMarkdown(forDisplay=true)
        if (AppearancePrefs.fixMarkdown(getApplication())) out = DisplayPipeline.fixMarkdown(out)
        if (!isSystem && AppearancePrefs.encodeTags(getApplication())) out = DisplayPipeline.encodeTags(out)
        displayCache[index] = out
        return out
    }

    /** README 消息操作：token 统计（官方 option_toggle_logprobs；用当前模型 tokenizer 计数）。 */
    fun messageTokenCount(index: Int): Pair<String, Int>? {
        val el = chatStore.messages(sessionId).getOrNull(index)?.jsonObject ?: return null
        val text = el["mes"]?.jsonPrimitive?.contentOrNull ?: return null
        val model = chatRepository.profile()?.model.orEmpty()
        val count = TokenCounterFactory.forModel(model).count(text)
        return text to count
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

    /** 官方 vectors 扩展 Data Bank 支持 URL 上传：下载文本内容入库（本 App 存 filesDir/databank/）。 */
    fun addDataBankUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(trimmed).header("User-Agent", "EmberInn/0.1").build()
                val bytes = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.bytes() ?: error("空响应")
                }
                if (bytes.isEmpty()) return@runCatching
                val name = trimmed.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "data-${System.currentTimeMillis()}.txt" }
                vectorRag.saveDataBankFile(name, bytes)
            }.onFailure { e ->
                _notice.value = "（数据银行下载失败：${e.message ?: "未知错误"}）"
            }
            withContext(Dispatchers.Main) { refreshDataBank() }
        }
    }

    fun removeDataBankFile(name: String) {
        vectorRag.deleteDataBankFile(name)
        refreshDataBank()
    }

    // ---- SlashMessageActions（消息类斜杠命令；对齐官方 slash-commands.js）----

    override fun sendAsCharacter(name: String, text: String, at: Int?, avatar: String?, compact: Boolean): ManualSendResult {
        val mesText = text.trim()
        if (mesText.isBlank()) return ManualSendResult("", "{}")
        val resolvedName = name.ifBlank { currentCharName }
        // 官方 sendas：SLASH_COMMAND 正则（characterOverride=目标角色名）
        var raw = mesText
        if (GlobalRegexPrefs.enabled(getApplication())) {
            raw = RegexPipelineEngine.apply(
                raw = raw,
                placement = ChatPromptFactory.REGEX_SLASH_COMMAND,
                scripts = resolveCurrentRegexScripts(),
                characterOverride = resolvedName,
            )
        }
        // 官方：只设置 bias 的消息是系统消息（is_system=true、mes 为空），不进上下文
        val bias = BiasEngine.extractMessageBias(raw)
        val isSystem = bias.isNotBlank() && BiasEngine.removeMacros(raw).isEmpty()
        val substituted = MacroEngine.substitute(raw, MacroEnv(user = currentUserName, char = resolvedName))
        val message = chatStore.appendManualMessage(
            sessionId = sessionId,
            isUser = false,
            content = substituted,
            name = resolvedName,
            at = at,
            isSystem = isSystem,
            bias = bias.trim().takeIf { it.isNotBlank() },
            compact = compact,
            avatar = avatar,
        )
        refreshMessages()
        return ManualSendResult(
            mes = substituted,
            json = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), message),
        )
    }

    override fun sendAsUser(text: String, name: String?, at: Int?, compact: Boolean): ManualSendResult {
        val mesText = text.trim()
        if (mesText.isBlank()) return ManualSendResult("", "{}")
        // 官方 send：name 参数存在时按参数显示（可为空=不显示名）；缺省用当前用户名
        val resolvedName = if (name != null) name else currentUserName
        val bias = BiasEngine.extractMessageBias(mesText)
        val substituted = MacroEngine.substitute(mesText, MacroEnv(user = resolvedName, char = currentCharName))
        val message = chatStore.appendManualMessage(
            sessionId = sessionId,
            isUser = true,
            content = substituted,
            name = resolvedName,
            at = at,
            isSystem = false,
            bias = bias.trim().takeIf { it.isNotBlank() },
            compact = compact,
        )
        refreshMessages()
        return ManualSendResult(
            mes = substituted,
            json = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), message),
        )
    }

    /** 按消息 extra.force_avatar/original_avatar 解析头像文件（官方 sendas avatar= / force_avatar 渲染语义）。 */
    fun avatarPathOf(index: Int): String? {
        val el = chatStore.messages(sessionId).getOrNull(index)?.jsonObject ?: return null
        val key = (el["extra"] as? JsonObject)?.get("force_avatar")?.jsonPrimitive?.contentOrNull
            ?: el["original_avatar"]?.jsonPrimitive?.contentOrNull
            ?: return null
        if (key.isBlank()) return null
        if (java.io.File(key).exists()) return key
        val store = CharacterStore(getApplication())
        store.get(key)?.avatarPath?.let { return it }
        return store.list().firstOrNull { it.name == key }?.avatarPath
    }

    /** 当前会话正则脚本（全局 + 角色 + 预设），供 /sendas 等 SLASH_COMMAND 位点复用。 */
    private fun resolveCurrentRegexScripts(): List<RegexPipelineScript> {
        val (presetScripts, presetAllowed) = presetRegex()
        return ChatPromptFactory().resolveRegexScripts(
            characterRawJson = character?.rawJson,
            globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
            scopedAllowed = character?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
            presetScripts = presetScripts,
            presetAllowed = presetAllowed,
        )
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

    override fun renameChat(name: String): String {
        if (name.isBlank()) return "（/renamechat 需要提供名字）"
        chatStore.renameSession(sessionId, name.trim())
        return ""
    }

    override fun chatName(): String = sessionName()

    override fun setInput(text: String): String {
        _inputDraft.value = text
        return text
    }

    override fun setBackground(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return _chatBackground.value.orEmpty()
        if (trimmed == "clear") {
            clearChatBackground()
            return ""
        }
        // 官方 /bg 按背景文件名匹配；App 直接存 URL/路径（近似，HANDOFF 登记）
        val meta = chatStore.metadata(sessionId).toMutableMap()
        meta["custom_background"] = JsonPrimitive(trimmed)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _chatBackground.value = trimmed
        return trimmed
    }

    override fun selectPersona(name: String, mode: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "（/persona-set 需要指定人设名）"
        // 官方 setNameCallback：先按名字/avatar 找人设
        val found = personaStore.list().firstOrNull { it.name == trimmed || it.id == trimmed }
        when (mode.lowercase()) {
            "lookup" -> {
                if (found == null) return "（找不到人设：$trimmed）"
                setPersona(found.id)
                return ""
            }
            "temp" -> {
                // 官方 temp：仅设置临时用户名，不查找人设
                currentUserName = trimmed
                _notice.value = "（临时用户名已设为 $trimmed）"
                return ""
            }
            else -> {
                // 官方 all：先找人设，找不到回退临时用户名
                if (found != null) {
                    setPersona(found.id)
                    return ""
                }
                currentUserName = trimmed
                _notice.value = "（未找到人设“$trimmed”，已设为临时用户名）"
                return ""
            }
        }
    }

    override fun applyPreset(name: String): String {
        val prefs = com.emberinn.app.ui.settings.PresetPrefsStore.load(getApplication())
        val names = com.emberinn.engine.prompt.PresetLibrary.samplerPresets("openai").map { it.name } +
            com.emberinn.app.ui.settings.UserPresetStore.list(getApplication(), "sampler")
        if (name.isBlank()) return prefs.samplerPreset.ifBlank { names.firstOrNull().orEmpty() }
        // 官方 presetCommandCallback：exact + Fuse.js 7.1 模糊回退（引擎差分，见 FusePresetSearch）
        val target = com.emberinn.engine.prompt.FusePresetSearch.selectPresetName(names, name)
        if (target != null) {
            com.emberinn.app.ui.settings.PresetSettingsStore.applySampler(getApplication(), target)
            _notice.value = "（采样预设已切换：$target）"
            return target
        }
        return prefs.samplerPreset
    }

    override suspend fun triggerGeneration(await: Boolean): String {
        if (_isStreaming.value) return "（正在生成中，请稍后再试。）"
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            return "（未配置模型，请先选一个模型。）"
        }
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return "（还没有消息可触发生成。）"
        val result = if (isUser(last)) {
            // 官方 Generate('normal')：最后一条用户消息 → 正常生成回复
            if (group != null) startGroupTurn(type = "generate") else startStream(history = msgs)
            ""
        } else if (!isSystemMsg(last)) {
            // 官方发送按钮在最后一条为 AI 时走 continue（mes_continue）
            continueGeneration()
            ""
        } else {
            "（最后一条是系统消息，无法触发生成。）"
        }
        if (await && result.isEmpty()) {
            while (_isStreaming.value) delay(50)
        }
        return result
    }

    override fun injectScript(
        text: String,
        id: String,
        position: String,
        depth: Int,
        role: String,
        scan: Boolean,
        ephemeral: Boolean,
        filter: String?,
    ): String {
        // 官方 injectCallback：position/role/depth/scan 参数归一，空 value 删除条目
        val spec = ExtensionPromptEngine.parseInject(id, text, position, depth, role, scan)
        val resolvedId = spec.id
        val meta = chatStore.metadata(sessionId).toMutableMap()
        val injects = (meta["script_injects"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (text.isBlank()) {
            injects.remove(resolvedId)
        } else {
            injects[resolvedId] = buildJsonObject {
                put("value", JsonPrimitive(spec.value))
                put("position", JsonPrimitive(spec.position))
                put("depth", JsonPrimitive(spec.depth))
                put("scan", JsonPrimitive(spec.scan))
                put("role", JsonPrimitive(spec.role))
                filter?.takeIf { it.isNotBlank() }?.let { put("filter", JsonPrimitive(it)) }
            }
            if (ephemeral) ephemeralInjectIds += resolvedId
        }
        meta["script_injects"] = JsonObject(injects)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        return resolvedId
    }

    override fun impersonate(prompt: String): String {
        if (_isStreaming.value) return "（正在生成中，请稍后再试。）"
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            return "（未配置模型，请先选一个模型。）"
        }
        startStream(
            history = chatStore.messages(sessionId),
            type = "impersonate",
            impersonation = true,
            impersonationPrompt = ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT,
            quietPrompt = prompt.trim(),
        )
        return ""
    }

    private fun narrateText(text: String) {
        val voice = VoicePrefs.read(getApplication())
        if (!voice.enabled) return
        // 官方 tts/index.js:674：朗读前先 substituteParams 宏替换
        val substituted = MacroEngine.substitute(text, MacroEnv(user = currentUserName, char = currentCharName))
        val cleaned = TtsTextProcessor.prepare(
            text = substituted,
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
    private val _defaultPersona = MutableStateFlow(personaStore.default())
    val defaultPersona: StateFlow<Persona?> = _defaultPersona

    fun setPersona(id: String) {
        personaStore.setActive(id)
        _activePersona.value = personaStore.active()
        _personas.value = personaStore.list()
    }

    fun setDefaultPersona(id: String) {
        personaStore.setDefault(id)
        _defaultPersona.value = personaStore.default()
        _personas.value = personaStore.list()
    }

    /** 官方 chat_metadata.persona：人设锁定到当前聊天。 */
    fun lockedPersonaId(): String? =
        chatStore.metadata(sessionId)["persona"]?.jsonPrimitive?.contentOrNull

    fun lockPersonaToChat(id: String?) {
        val meta = chatStore.metadata(sessionId).toMutableMap()
        if (id == null) meta.remove("persona") else meta["persona"] = JsonPrimitive(id)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _notice.value = if (id == null) "（已解除人设聊天锁）" else "（人设已锁定到本聊天）"
    }

    /**
     * 官方 resolvePersonaForChat：聊天锁 > 角色/群聊连接 > 默认人设 > 当前选择。
     * 决策下沉引擎 PersonaEngine.resolve（差分锁定）。
     */
    fun effectivePersona(): Persona? {
        val list = personaStore.list()
        if (list.isEmpty()) return null
        val connectedIds = list.filter { p ->
            p.connections.any { c ->
                (c.type == "character" && c.id == characterId) ||
                    (c.type == "group" && c.id == group?.id)
            }
        }.map { it.id }
        val resolved = PersonaEngine.resolve(
            chatMetaPersona = lockedPersonaId(),
            userAvatars = list.map { it.id },
            connectedPersonas = connectedIds,
            defaultPersona = _defaultPersona.value?.id,
            allowMultiConnections = false,
            userAvatar = _activePersona.value?.id.orEmpty(),
            personaAutoLock = false,
        )
        return list.firstOrNull { it.id == resolved.chatPersona } ?: _activePersona.value
    }

    fun savePersona(persona: Persona) {
        val list = personaStore.list()
        val next = if (list.any { it.id == persona.id }) {
            list.map { if (it.id == persona.id) persona else it }
        } else {
            list + persona
        }
        personaStore.save(next, activeId = persona.id, defaultId = if (list.isEmpty()) persona.id else null)
        _personas.value = personaStore.list()
        _activePersona.value = personaStore.active()
        _defaultPersona.value = personaStore.default()
    }

    fun deletePersona(id: String) {
        personaStore.save(personaStore.list().filterNot { it.id == id })
        _personas.value = personaStore.list()
        _activePersona.value = personaStore.active()
        _defaultPersona.value = personaStore.default()
    }

    fun duplicatePersona(id: String) {
        val source = personaStore.list().firstOrNull { it.id == id } ?: return
        val copy = source.copy(
            id = "p-" + System.nanoTime().toString(36),
            name = source.name + " 副本",
            connections = emptyList(),
        )
        personaStore.save(personaStore.list() + copy, activeId = copy.id)
        _personas.value = personaStore.list()
        _activePersona.value = personaStore.active()
        _defaultPersona.value = personaStore.default()
    }

    /** 官方 syncUserNameToPersona：本聊天所有用户消息 name=当前用户名，force_avatar=人设头像。 */
    fun syncUserNameToPersona() {
        val persona = _activePersona.value
        if (persona == null) {
            _notice.value = "（请先选择人设）"
            return
        }
        val current = chatStore.messages(sessionId)
        val updated = current.map { el ->
            val obj = el.jsonObject
            val isUser = obj["is_user"]?.jsonPrimitive
                ?.let { it.booleanOrNull ?: (it.content == "true") } == true
            if (isUser) {
                JsonObject(
                    obj.toMutableMap().apply {
                        put("name", JsonPrimitive(currentUserName))
                        if (persona.avatarPath.isNotBlank()) put("force_avatar", JsonPrimitive(persona.avatarPath))
                    },
                )
            } else {
                el
            }
        }
        chatStore.replace(sessionId, updated)
        refreshMessages()
        _notice.value = "（已把本聊天用户消息名称同步为 $currentUserName）"
    }

    /** 官方 Backup Personas：导出 personas.json。 */
    fun backupPersonas(uri: android.net.Uri) {
        runCatching {
            getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(personaStore.exportJson().toByteArray())
            }
            _notice.value = "（人设已备份）"
        }.onFailure { _notice.value = "（人设备份失败：${it.message}）" }
    }

    /** 官方 Restore Personas：合并语义（已存在跳过，default_persona 存在才应用）。 */
    fun restorePersonas(uri: android.net.Uri) {
        val result = runCatching {
            val text = getApplication<android.app.Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            personaStore.importJson(text)
        }.getOrNull()
        if (result != null && result.ok) {
            _personas.value = personaStore.list()
            _activePersona.value = personaStore.active()
            _defaultPersona.value = personaStore.default()
            _notice.value = if (result.warnings.isEmpty()) {
                "（人设已恢复）"
            } else {
                "（人设已恢复，${result.warnings.size} 条跳过提示）"
            }
        } else {
            _notice.value = "（人设恢复失败：文件格式不正确）"
        }
    }

    /** 官方 extension_token_counter：当前模型 tokenizer 计数。 */
    fun tokenCount(text: String): Int {
        val model = runCatching { chatRepository.profile()?.model }.getOrNull().orEmpty()
        return com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(model).count(text)
    }

    /** 官方 extension_floating_counter：距下次插入剩余用户消息数（interval 1 恒 0）。 */
    fun nextAnInsertion(interval: Int): Int? {
        val meta = chatStore.metadata(sessionId)
        val effectiveInterval = meta["note_interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: interval
        if (effectiveInterval <= 0) return null
        val userCount = chatStore.messages(sessionId).count { el ->
            el.jsonObject["is_user"]?.jsonPrimitive
                ?.let { it.booleanOrNull ?: (it.content == "true") } == true
        }
        if (effectiveInterval == 1) return 0
        return if (userCount >= effectiveInterval) userCount % effectiveInterval else effectiveInterval - userCount
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
    /** 无头像取色时的稳定兜底：卡名哈希 → HSV 色（README：无头像卡用卡名哈希生成稳定 seed，可复现）。 */
    private fun nameHashSeed(name: String): Long {
        val h = name.hashCode()
        val hue = ((h % 360) + 360) % 360
        return Color.hsv(hue.toFloat(), 0.55f, 0.78f).value.toLong()
    }

    fun refreshTheme() {
        ThemeState.update(
            recipe = character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson) },
            seedColor = character?.let { it.seedColor ?: nameHashSeed(it.name) },
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

    /** 作者注释（官方 authors-note.js 元数据键：note_prompt/note_position/note_depth/note_role/note_interval）。 */
    data class AuthorsNoteDraft(
        val prompt: String,
        val position: Int,
        val depth: Int,
        val role: Int,
        val interval: Int,
    )

    fun authorsNoteDraft(): AuthorsNoteDraft {
        val meta = chatStore.metadata(sessionId)
        return AuthorsNoteDraft(
            prompt = meta["note_prompt"]?.jsonPrimitive?.contentOrNull ?: "",
            position = meta["note_position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
            depth = meta["note_depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
            role = meta["note_role"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            interval = meta["note_interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
        )
    }

    fun saveAuthorsNote(prompt: String, position: Int, depth: Int, role: Int, interval: Int) {
        val meta = chatStore.metadata(sessionId).toMutableMap()
        if (prompt.isBlank()) meta.remove("note_prompt") else meta["note_prompt"] = JsonPrimitive(prompt)
        meta["note_position"] = JsonPrimitive(position)
        meta["note_depth"] = JsonPrimitive(depth)
        meta["note_role"] = JsonPrimitive(role)
        meta["note_interval"] = JsonPrimitive(interval)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _notice.value = if (prompt.isBlank()) "（作者注释已清除）" else "（作者注释已保存，下次发送生效）"
    }

    /** 当前角色的角色备注（官方 extension_settings.note.chara[charName]）。 */
    fun charaNoteDraft(): CharaNoteData? =
        character?.let { AuthorsNotePrefsStore.load(getApplication<android.app.Application>()).charaNotes[it.name] }

    fun saveCharaNote(data: CharaNoteData) {
        val name = character?.name ?: return
        val prefs = AuthorsNotePrefsStore.load(getApplication<android.app.Application>())
        AuthorsNotePrefsStore.save(
            getApplication(),
            prefs.copy(charaNotes = prefs.charaNotes + (name to data)),
        )
        _notice.value = "（角色备注已保存）"
    }

    fun deleteCharaNote() {
        val name = character?.name ?: return
        val prefs = AuthorsNotePrefsStore.load(getApplication<android.app.Application>())
        AuthorsNotePrefsStore.save(getApplication(), prefs.copy(charaNotes = prefs.charaNotes - name))
        _notice.value = "（角色备注已清除）"
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

    /** /setinput 输入框草稿：ChatScreen 消费后清空。 */
    private val _inputDraft = MutableStateFlow<String?>(null)
    val inputDraft: StateFlow<String?> = _inputDraft

    fun clearInputDraft() {
        _inputDraft.value = null
    }

    /** 官方 caption 扩展 refine_mode：生成后待用户确认/编辑的草稿。 */
    data class CaptionDraft(val text: String, val image: MediaAttachment)

    private val _captionDraft = MutableStateFlow<CaptionDraft?>(null)
    val captionDraft: StateFlow<CaptionDraft?> = _captionDraft

    /** 官方 caption prompt_ask：生成前请求自定义提示词。 */
    private val _captionPromptRequest = MutableStateFlow(false)
    val captionPromptRequest: StateFlow<Boolean> = _captionPromptRequest

    /** 每次进入聊天页调用：读盘刷新一次（设置页已保存过则直接由 ProviderState 同步）。 */
    fun refreshProviderConfigured() {
        ProviderState.refresh(chatRepository.profile())
    }

    private fun isProviderConfigured(): Boolean = ProviderState.isConfigured()

    @Volatile
    private var streamSession: LlmClient.StreamSession? = null
    @Volatile
    private var streamActive = false
    private var singleAutoContinueRuns = 0
    /** 工具循环递归计数（官方 ToolManager.RECURSE_LIMIT=5）。 */
    private var toolLoopRuns = 0
    private var pendingToolCalls: kotlinx.serialization.json.JsonElement? = null
    /** 当前流的生成参数：工具循环递归时按官方 Generate('normal') 重启 startStream。 */
    private var currentStreamParams: StreamParams? = null
    private var streamStartedAt: String = java.time.Instant.now().toString()
    /** 官方 time_to_first_token：首个流式 delta 到达时刻（毫秒）。 */
    private var firstDeltaAt: Long? = null
    private var streamContinueMode = false
    /** 当前流是否“滑动生成新变体”（对齐官方 Generate('swipe')：结果追加进最后一条 swipes，不新增消息）。 */
    @Volatile
    private var generatingSwipe = false
    /** 本轮落盘时使用的正则脚本集合（对齐官方 saveReply 的 getRegexScripts({allowedOnly:true})）。 */
    @Volatile
    private var saveRegexScripts: List<RegexPipelineScript> = emptyList()
    /** 本轮群聊批次 ID（官方 group_generation_id：整批共享，regenerate 定位用）。 */
    @Volatile
    private var pendingGroupGenId: Long? = null
    /** /inject ephemeral=true 的注入 ID：生成结束后从元数据删除（官方 GENERATION_ENDED/STOPPED）。 */
    private val ephemeralInjectIds = mutableSetOf<String>()
    private var currentCharName = "Assistant"
    private var currentUserName = "User"
    val userName: String get() = currentUserName

    init {
        // 第三层主题（角色配方）：当前角色进入全局主题管线；离开聊天由 ChatScreen 清空回全局
        ThemeState.update(
            recipe = character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson) },
            seedColor = character?.let { it.seedColor ?: nameHashSeed(it.name) },
        )
        // 官方新聊天第一条消息 = 角色开场白 first_mes（script.js newChat 语义）；空会话才补。
        // README：AI 对话（无角色卡）带默认开场“我是余烬，想聊点什么？”
        val isGroupSession = chatStore.get(sessionId)?.groupId != null
        // 官方 preset-manager.js autoSelectPreset（CHAT_CHANGED）：角色名精确等于采样预设名 → 自动选中并应用
        val autoCharName = character?.name
        if (autoCharName != null && !isGroupSession) {
            val samplerNames = com.emberinn.engine.prompt.PresetLibrary.samplerPresets("openai").map { it.name } +
                com.emberinn.app.ui.settings.UserPresetStore.list(getApplication(), "sampler")
            val presetPrefs = com.emberinn.app.ui.settings.PresetPrefsStore.load(getApplication())
            val decided = com.emberinn.engine.prompt.PresetApplyEngine.autoSelectPresetDecision(
                autoCharName, samplerNames, presetPrefs.samplerPreset,
            )
            if (decided != null && decided != presetPrefs.samplerPreset) {
                com.emberinn.app.ui.settings.PresetSettingsStore.applySampler(getApplication(), decided)
            }
        }
        if (chatStore.messages(sessionId).isEmpty() && !isGroupSession) {
            val charName = chatStore.get(sessionId)?.name ?: "Assistant"
            val currentCharacter = character
            val firstMes = if (currentCharacter != null) {
                firstMesOf(currentCharacter.rawJson)
            } else {
                DEFAULT_AI_OPENING
            }
            val alternates = currentCharacter?.let { alternatesOf(it.rawJson) } ?: emptyList()
            if (!firstMes.isNullOrBlank() || alternates.isNotEmpty()) {
                // 官方 getFirstMessage：first_mes + alternate_greetings 全部存前过 AI_OUTPUT 正则，
                // 作为第一条 AI 消息的 swipes（官方：swipes = [firstMes, ...alternateGreetings]）
                val (presetScripts, presetAllowed) = presetRegex()
                val scripts = ChatPromptFactory().resolveRegexScripts(
                    characterRawJson = currentCharacter?.rawJson,
                    globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
                    scopedAllowed = currentCharacter?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
                    presetScripts = presetScripts,
                    presetAllowed = presetAllowed,
                )
                val regexOn = GlobalRegexPrefs.enabled(getApplication())
                val greetings = (listOfNotNull(firstMes) + alternates)
                    .map { if (regexOn) RegexPipelineEngine.apply(it, ChatPromptFactory.REGEX_AI_OUTPUT, scripts) else it }
                val content = greetings.firstOrNull { it.isNotBlank() } ?: greetings.firstOrNull().orEmpty()
                chatStore.append(
                    sessionId,
                    isUser = false,
                    content = content,
                    name = charName,
                    greetingSwipes = greetings,
                )
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

    private fun alternatesOf(rawJson: String): List<String> = runCatching {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonObject ?: root
        (data["alternate_greetings"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }.getOrDefault(emptyList())

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
        if (_isStreaming.value) return false
        // 官方 sendTextareaMessage / Generate：
        // 1) power_user.continue_on_send：空输入 + 最后一条 AI + 无附件 + 非群聊 → continue
        // 2) oai_settings.send_if_empty：仅 OpenAI 系 + 空输入 + 最后一条 AI → 用配置文本 normal 发送
        var effectiveText = text
        if (effectiveText.isBlank() && media.isEmpty()) {
            val last = chatStore.messages(sessionId).lastOrNull()
            val lastIsAi = last != null && !isUser(last) && !isSystemMsg(last)
            if (lastIsAi && group == null && GenerationPrefs.continueOnSend(getApplication())) {
                if (!isProviderConfigured()) {
                    refreshProviderConfigured()
                    _notice.value = "（未配置模型，请先选一个模型再发送。）"
                    return false
                }
                currentCharName = chatStore.get(sessionId)?.name ?: "Assistant"
                currentUserName = userName
                _notice.value = null
                _impersonated.value = null
                startStream(
                    history = chatStore.messages(sessionId),
                    type = "continue",
                    continueMode = true,
                )
                return true
            }
            val profileProtocol = chatRepository.profile()?.let {
                com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
            }
            val isOpenAiLike = profileProtocol != null && profileProtocol != "anthropic" && profileProtocol != "google"
            if (lastIsAi && isOpenAiLike) {
                effectiveText = GenerationPrefs.sendIfEmpty(getApplication())
                if (effectiveText.isBlank()) return false
            } else {
                return false
            }
        }
        if (effectiveText.isBlank() && media.isEmpty()) return false
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
        // 官方 sendMessageAsUser：USER_INPUT 正则存前应用一次；总装 isPrompt=true 只跑 promptOnly 脚本
        val (presetScripts, presetAllowed) = presetRegex()
        saveRegexScripts = ChatPromptFactory().resolveRegexScripts(
            characterRawJson = character?.rawJson,
            globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
            scopedAllowed = character?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
            presetScripts = presetScripts,
            presetAllowed = presetAllowed,
        )
        // 官方 Generate：getBiasStrings(textareaText, type) 先提取 {{bias}}，sendMessageAsUser 再存 extra.bias
        val messageBias = BiasEngine.extractMessageBias(effectiveText)
        val regexedText = if (GlobalRegexPrefs.enabled(getApplication())) {
            RegexPipelineEngine.apply(effectiveText, ChatPromptFactory.REGEX_USER_INPUT, saveRegexScripts)
        } else {
            effectiveText
        }
        // 官方 sendMessageAsUser：regex USER_INPUT → substituteParams →（有 bias 时）removeMacros
        val substitutedText = MacroEngine.substitute(regexedText, MacroEnv(user = userName, char = charName))
        val storedText = if (messageBias.isNotBlank()) BiasEngine.removeMacros(substitutedText) else substitutedText
        chatStore.append(sessionId, true, storedText, userName, media, mediaDisplay = mediaDisplay, mediaIndex = mediaIndex, bias = messageBias)
        // 官方 sendMessageAsUser：message_token_count_enabled 时用户消息也写 extra.token_count
        if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
            val model = chatRepository.profile()?.model.orEmpty()
            val count = runCatching {
                com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(model).count(storedText)
            }.getOrDefault(storedText.length / 4)
            val idx = chatStore.messages(sessionId).lastIndex
            if (idx >= 0) chatStore.setExtraValue(sessionId, idx, "token_count", count.toString())
        }
        _pendingMedia.value = emptyList()
        refreshMessagesAppendOnly()
        // 官方 translate 扩展：auto_mode=inputs/both 时用户消息自动翻译（mes 换译文、原文进 extra.display_text）
        translateOutgoing(chatStore.messages(sessionId).lastIndex)
        val voice = VoicePrefs.read(getApplication())
        if (voice.enabled && voice.narrateUser) {
            narrateText(effectiveText)
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
        pendingToolCalls = null
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
        if (chatStore.swipeTo(sessionId, index, variant)) {
            refreshMessages()
            // 官方 memory MESSAGE_SWIPED → onChatEvent
            memoryService.maybeAutoSummarize(sessionId)
        }
    }

    /** 官方 chats.js：点击图片在 LIST ↔ GALLERY 间切换显示模式（持久化 extra.media_display）。 */
    fun setMediaDisplay(messageIndex: Int) {
        val list = chatStore.messages(sessionId).toMutableList()
        if (messageIndex !in list.indices) return
        val el = list[messageIndex].jsonObject
        val extra = (el["extra"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val current = extra["media_display"]?.jsonPrimitive?.contentOrNull
        if (current == "gallery") {
            extra.remove("media_display")
        } else {
            extra["media_display"] = JsonPrimitive("gallery")
        }
        list[messageIndex] = JsonObject(el + ("extra" to JsonObject(extra)))
        chatStore.replace(sessionId, list)
        refreshMessages()
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
        // 官方 memory MESSAGE_DELETED → onChatEvent
        memoryService.maybeAutoSummarize(sessionId)
    }

    /** 编辑消息（官方 updateMessage：getRegexedString(isEdit) → extractMessageBias → substituteParams → 清/写 extra.bias）。 */
    fun editMessage(index: Int, newText: String) {
        if (_isStreaming.value) return
        val text = newText.trim()
        if (text.isEmpty()) return
        val el = chatStore.messages(sessionId).getOrNull(index) ?: return
        val obj = el.jsonObject
        val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
        val isNarrator = (obj["extra"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "narrator"
        // 官方 updateMessage：用户消息 USER_INPUT、旁白 SLASH_COMMAND、其余 AI_OUTPUT
        val placement = when {
            isUser -> ChatPromptFactory.REGEX_USER_INPUT
            isNarrator -> ChatPromptFactory.REGEX_SLASH_COMMAND
            else -> ChatPromptFactory.REGEX_AI_OUTPUT
        }
        val (presetScripts, presetAllowed) = presetRegex()
        val scripts = ChatPromptFactory().resolveRegexScripts(
            characterRawJson = character?.rawJson,
            globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
            scopedAllowed = character?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
            presetScripts = presetScripts,
            presetAllowed = presetAllowed,
        )
        // 官方 updateMessage：getRegexedString(text, regexPlacement, { characterOverride, isEdit: true })；
        // 旁白不传 characterOverride（官方 narrator 分支）；disabledExtensions.regex 关闭时不应用
        val regexed = if (GlobalRegexPrefs.enabled(getApplication())) {
            RegexPipelineEngine.apply(
                raw = text,
                placement = placement,
                scripts = scripts,
                isEdit = true,
                characterOverride = if (isNarrator) null else obj["name"]?.jsonPrimitive?.contentOrNull ?: currentCharName,
            )
        } else {
            text
        }
        val env = MacroEnv(user = currentUserName, char = currentCharName)
        // 官方 updateMessage：extractMessageBias 在 substituteParams 之前；bias 存入 extra.bias
        val (cleaned, bias) = extractEditBias(regexed)
        val processed = MacroEngine.substitute(cleaned, env)
        chatStore.updateMessage(sessionId, index, processed, bias = bias)
        refreshMessages()
        // 官方 translateMessageEdit：auto_mode=none 时清 display_text；否则按消息类型重译
        val autoMode = translateAutoMode()
        if (autoMode == "none") {
            chatStore.setDisplayText(sessionId, index, displayText = null, reasoningDisplayText = null)
            refreshMessages()
        } else {
            val edited = chatStore.messages(sessionId).getOrNull(index)?.jsonObject ?: return
            if (isUser(edited)) translateOutgoing(index) else translateIncoming(index)
        }
        // 官方 memory MESSAGE_UPDATED → onChatEvent
        memoryService.maybeAutoSummarize(sessionId)
    }

    /** 官方 extractMessageBias + removeMacros（引擎 1:1）。 */
    private fun extractEditBias(message: String): Pair<String, String> {
        val bias = BiasEngine.extractMessageBias(message)
        val cleaned = if (bias.isNotBlank()) BiasEngine.removeMacros(message) else message
        return cleaned to bias
    }

    override suspend fun continueChat(prompt: String): String {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            continueGeneration()
            return ""
        }
        // 官方 continueChatCallback：prompt 走 quiet_prompt+quietToLoud；守卫与 UI 按钮一致
        if (_isStreaming.value) return ""
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return ""
        if (isUser(last)) {
            _notice.value = "（最后一条是你发的消息，先让对方回复或发送后再继续。）"
            return ""
        }
        if (isSystemMsg(last)) {
            _notice.value = "（最后一条是系统/隐藏消息，不能继续生成。）"
            return ""
        }
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return ""
        }
        startStream(
            history = msgs,
            type = "continue",
            continueMode = true,
            quietPrompt = trimmed,
        )
        return ""
    }

    override suspend fun regenerateChat(): String {
        regenerate()
        return ""
    }

    override suspend fun swipeChat(direction: String): String {
        val idx = chatStore.messages(sessionId).indexOfLast { !isUser(it) && !isSystemMsg(it) }
        if (idx < 0) return ""
        if (direction.lowercase() == "left") swipeLeft(idx) else swipeRight(idx)
        return ""
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
                // 对齐官方 constants.js getFromMime：前缀分类，未知类型拒绝
                val mediaType = MediaEngine.typeFromMime(type) ?: return@launch
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

    /** 从 URL 导入附件（官方 Message.addImage/addVideo/addAudio 的 URL 来源）：下载 → 落盘 → 本地附件链。 */
    fun addMediaFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = runCatching {
                    val client = OkHttpClient.Builder()
                        .followRedirects(true)
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(trimmed).header("User-Agent", "EmberInn/0.1").build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.bytes() ?: error("空响应")
                    }
                }.getOrElse { e -> throw IllegalArgumentException(e.message ?: "下载失败") }
                val (type, displayName) = guessMediaFromUrl(trimmed, bytes)
                // 对齐官方 constants.js getFromMime：前缀分类，未知类型拒绝
                val mediaType = MediaEngine.typeFromMime(type) ?: return@runCatching
                // 与本地附件同一条处理链：非 jpeg/png/webp 图片压缩
                val safeMime = type == "image/jpeg" || type == "image/png" || type == "image/webp"
                val processedBytes = if (mediaType == "image" && !safeMime) compressToJpeg(bytes) else bytes
                val extension = if (processedBytes !== bytes) "jpg" else extensionFor(type, displayName)
                val app = getApplication<Application>()
                val dir = java.io.File(app.filesDir, "media").apply { mkdirs() }
                val file = java.io.File(dir, "${System.currentTimeMillis()}_${displayName.hashCode().toUInt().toString(16)}.$extension")
                file.writeBytes(processedBytes)
                _pendingMedia.value = _pendingMedia.value + MediaAttachment(
                    type = mediaType,
                    url = file.absolutePath,
                    title = displayName,
                )
            }.onFailure { e ->
                _notice.value = "（附件下载失败：${e.message ?: "未知错误"}）"
            }
        }
    }

    /**
     * 官方 caption 扩展：入口。prompt_ask=true 先弹提示词输入，refine_mode=true 生成后弹确认编辑，
     * 最终 sendCaptionedMessage 语义追加带 captioned 媒体的用户消息并触发回复。
     */
    fun startCaptionFlow() {
        if (_isStreaming.value) {
            _notice.value = "（正在生成中，请稍后再试。）"
            return
        }
        val pending = _pendingMedia.value
        val image = pending.firstOrNull { it.type == "image" }
        if (image == null) {
            _notice.value = "（请先添加一张图片，再点“生成描述并发送”。）"
            return
        }
        val s = CaptionPrefs.load(getApplication())
        if (s.promptAsk) {
            _captionPromptRequest.value = true
        } else {
            captionImageAndDraft(null)
        }
    }

    fun submitCaptionPrompt(prompt: String) {
        _captionPromptRequest.value = false
        captionImageAndDraft(prompt)
    }

    fun cancelCaptionFlow() {
        _captionPromptRequest.value = false
        _captionDraft.value = null
    }

    fun confirmCaptionSend(editedText: String? = null) {
        val draft = _captionDraft.value ?: return
        _captionDraft.value = null
        sendCaptionedMessage(editedText?.takeIf { it.isNotBlank() } ?: draft.text, draft.image)
    }

    private fun captionImageAndDraft(promptOverride: String?) {
        val pending = _pendingMedia.value
        val image = pending.firstOrNull { it.type == "image" } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val s = CaptionPrefs.load(getApplication())
                val file = java.io.File(image.url)
                if (!file.exists()) error("图片文件不存在")
                val mime = mimeForCaption(image.url)
                val dataUrl = "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(file.readBytes())
                val rawPrompt = promptOverride?.takeIf { it.isNotBlank() } ?: s.prompt
                val prompt = MacroEngine.substitute(rawPrompt, MacroEnv(user = currentUserName, char = currentCharName))
                val caption = chatRepository.captionImage(dataUrl, prompt) ?: error("描述生成失败")
                if (caption.isBlank()) error("描述生成失败")
                val rawTemplate = if (s.template.contains("{{caption}}", ignoreCase = true)) {
                    s.template
                } else {
                    s.template + " {{caption}}"
                }
                val substituted = MacroEngine.substitute(rawTemplate, MacroEnv(user = currentUserName, char = currentCharName))
                val wrapped = substituted.replace("{{caption}}", caption.trim())
                if (s.refineMode) {
                    _captionDraft.value = CaptionDraft(wrapped, image)
                } else {
                    sendCaptionedMessage(wrapped, image)
                }
            }.onFailure { e ->
                _notice.value = "（图片描述失败：${e.message ?: "未知错误"}）"
            }
        }
    }

    private fun sendCaptionedMessage(wrapped: String, image: MediaAttachment) {
        val s = CaptionPrefs.load(getApplication())
        chatStore.append(
            sessionId = sessionId,
            isUser = true,
            content = wrapped,
            name = currentUserName,
            media = listOf(image.copy(title = wrapped)),
            mediaDisplay = "gallery",
            mediaIndex = 0,
            captioned = true,
            inlineImage = s.showInChat,
        )
        _pendingMedia.value = _pendingMedia.value.filterNot { it.url == image.url }
        refreshMessages()
        startStream(history = chatStore.messages(sessionId))
    }

    private fun mimeForCaption(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    /** 从 URL 后缀 + 魔数推断媒体类型与文件名（官方按 MIME/URL 来源分类）。 */
    private fun guessMediaFromUrl(url: String, bytes: ByteArray): Pair<String, String> {
        val path = url.substringBefore('?').substringAfterLast('/')
        val lower = path.lowercase()
        val name = path.ifBlank { "attachment" }
        val byExt = when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".ogg") -> "audio/ogg"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".flac") -> "audio/flac"
            else -> null
        }
        val mime = byExt ?: when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "image/gif"
            bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() -> "image/webp"
            bytes.size >= 12 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "video/mp4"
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "video/webm"
            bytes.size >= 4 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == 0x33.toByte() -> "audio/mpeg"
            bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() -> "audio/wav"
            else -> "application/octet-stream"
        }
        return mime to name
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

    /** 官方 memory forceSummarizeChat：立即总结当前聊天并落盘，结果经 notice 反馈。 */
    fun forceMemorySummary() {
        if (_isStreaming.value) {
            _notice.value = "（正在生成中，请稍后再试。）"
            return
        }
        memoryService.forceSummarize(sessionId) { summary ->
            refreshMessages()
            _notice.value = if (summary.isNotBlank()) {
                "（记忆已更新）${summary.take(80)}"
            } else {
                "（记忆总结失败：请检查模型配置，或聊天内容不足以总结。）"
            }
        }
    }

    /** 官方 chat_metadata.world_info：本会话指定的外置世界（空=跟随角色关联/全局）。 */
    fun externalWorlds(): List<com.emberinn.app.data.WorldStore.WorldFile> =
        com.emberinn.app.data.WorldStore(getApplication()).list()

    fun chatWorld(): String? =
        chatStore.metadata(sessionId)["world_info"]?.jsonPrimitive?.contentOrNull

    fun setChatWorld(name: String) {
        val meta = chatStore.metadata(sessionId).toMutableMap()
        if (name.isBlank()) meta.remove("world_info") else meta["world_info"] = JsonPrimitive(name)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
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

    /** 工具循环重启 startStream 所需的当前流参数（官方 Generate 递归用同一组参数 + depth+1）。 */
    private data class StreamParams(
        val impersonation: Boolean,
        val swipeMode: Boolean,
        val cyclePrompt: String,
        val impersonationPrompt: String,
        val mediaInlining: Boolean,
        val characterRawJsonOverride: String?,
        val inChatExtensions: List<PromptItem>,
        val scopedRegexAvatar: String?,
        val groupGenId: Long?,
    )

    private fun startGroupTurn(type: String, cyclePrompt: String = "") {
        val members = groupMembers
        if (members.isEmpty()) {
            _notice.value = "（群聊成员缺失，请检查群聊设置。）"
            return
        }
        // 官方 generateGroupWrapper：group_generation_id = Date.now()，本轮所有成员消息共享
        val groupGenId = System.currentTimeMillis()
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
        runGroupStep(steps, 0, history, groupGenId = groupGenId)
    }

    private fun runGroupStep(
        steps: List<GroupStep>,
        index: Int,
        history: List<JsonElement>,
        autoContinueRuns: Int = 0,
        groupGenId: Long? = null,
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
            scopedRegexAvatar = step.speaker.id,
            groupGenId = groupGenId,
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
                        groupGenId,
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
        impersonationPrompt: String = ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT,
        quietPrompt: String = "",
        previewOnly: Boolean = false,
        onPreview: ((Pair<String, Int>) -> Unit)? = null,
        mediaInlining: Boolean = true,
        characterRawJsonOverride: String? = null,
        inChatExtensions: List<PromptItem> = emptyList(),
        scopedRegexAvatar: String? = null,
        groupGenId: Long? = null,
        onFinished: (() -> Unit)? = null,
    ) {
        currentStreamParams = StreamParams(
            impersonation = impersonation,
            swipeMode = swipeMode,
            cyclePrompt = cyclePrompt,
            impersonationPrompt = impersonationPrompt,
            mediaInlining = mediaInlining,
            characterRawJsonOverride = characterRawJsonOverride,
            inChatExtensions = inChatExtensions,
            scopedRegexAvatar = scopedRegexAvatar,
            groupGenId = groupGenId,
        )
        if (!streamActive) {
            toolLoopRuns = 0
            pendingToolCalls = null
        }
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
        pendingGroupGenId = groupGenId
        streamActive = true
        firstDeltaAt = null
        singleAutoContinueRuns = 0
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
            val memorySettings = MemoryPrefs.load(getApplication())
            val behavior = BehaviorPrefs.load(getApplication())
            // 官方外置世界书：角色关联 + 聊天指定 + 全局选择，合并成扫描条目池
            val worldStore = com.emberinn.app.data.WorldStore(getApplication())
            val linkedWorld = (characterRawJsonOverride ?: character?.rawJson)
                ?.let { com.emberinn.app.data.CharacterCardEdit.readWorldLink(it) }
            val chatMetaWorld = chatStore.metadata(sessionId)["world_info"]?.jsonPrimitive?.contentOrNull
            val globalSelect = WorldInfoPrefs.globalSelect(getApplication())
            // 官方 getPersonaLore：人设关联世界书（persona_description_lorebook）——
            // 已激活在聊天/全局世界书时跳过，否则并入扫描
            val personaLoreWorld = effectivePersona()?.lorebook
                ?.takeIf { it.isNotBlank() && it !in listOfNotNull(linkedWorld, chatMetaWorld) && it !in globalSelect }
            val externalWorlds = (listOfNotNull(linkedWorld, chatMetaWorld) + globalSelect + listOfNotNull(personaLoreWorld))
                .distinct()
                .associateWith { worldStore.entries(it) }
            val globalRegexScripts = GlobalRegexPrefs.read(getApplication())
            // 官方 regex getScriptsByType(SCOPED)：allowedOnly 时角色头像必须在 character_allowed_regex 中
            val regexAllowedAvatars = GlobalRegexPrefs.characterAllowedRegex(getApplication())
            val regexScopedAllowed = (scopedRegexAvatar ?: character?.id)?.let { "$it.png" in regexAllowedAvatars } ?: false
            val regexEnabled = GlobalRegexPrefs.enabled(getApplication())
            val reasoningToPrompts = GenerationPrefs.reasoningToPrompts(getApplication())
            // 官方 /inject：chat_metadata.script_injects → 本轮扩展提示 + scan 扫描文本
            val scriptInjections = (chatStore.metadata(sessionId)["script_injects"] as? JsonObject)
                ?.mapNotNull { (id, el) ->
                    val o = el.jsonObject
                    val value = o["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    // 官方 /inject filter：闭包在生成时判定，结果 true 才注入；解析失败/空=始终注入
                    val filterRaw = o["filter"]?.jsonPrimitive?.contentOrNull
                    val filterPassed = filterRaw.isNullOrBlank() || runCatching {
                        val out = slashExecutor.execute(filterRaw, SlashState())
                        out.trim().lowercase() in setOf("true", "1", "yes", "y", "on")
                    }.getOrDefault(true)
                    if (!filterPassed) return@mapNotNull null
                    val positionRaw = o["position"]?.jsonPrimitive?.contentOrNull
                    val roleRaw = o["role"]?.jsonPrimitive?.contentOrNull
                    ExtensionPromptEngine.ScriptInject(
                        id = id,
                        value = value,
                        position = positionRaw?.toIntOrNull()
                            ?: when (positionRaw?.lowercase()) {
                                "before" -> ExtensionPromptEngine.POSITION_BEFORE_PROMPT
                                "chat" -> ExtensionPromptEngine.POSITION_IN_CHAT
                                "none" -> ExtensionPromptEngine.POSITION_NONE
                                else -> ExtensionPromptEngine.POSITION_IN_PROMPT
                            },
                        depth = o["depth"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: ExtensionPromptEngine.DEFAULT_DEPTH,
                        role = roleRaw?.toIntOrNull()
                            ?: ExtensionPromptEngine.roleByName(roleRaw),
                        scan = o["scan"]?.jsonPrimitive?.content == "true",
                    )
                } ?: emptyList()
            // 官方 saveReply：AI_OUTPUT 正则存前应用，使用与本轮生成相同的脚本集合（群聊按发言人判定）
            val (presetScripts, presetAllowed) = presetRegex()
            saveRegexScripts = ChatPromptFactory().resolveRegexScripts(
                characterRawJson = characterRawJsonOverride ?: character?.rawJson,
                globalRegexScripts = globalRegexScripts,
                scopedAllowed = regexScopedAllowed,
                presetScripts = presetScripts,
                presetAllowed = presetAllowed,
            )
            if (rag.enabled() && vectorStore == null) {
                _notice.value = "（向量检索已开启，但嵌入服务未配置完整（地址/Key/模型），本轮未启用向量检索。）"
            }
            val session = chatRepository.streamPrepared(
                characterRawJson = characterRawJsonOverride ?: character?.rawJson,
                history = history,
                userName = currentUserName,
                charName = currentCharName,
                previewOnly = previewOnly,
                onPreview = onPreview,
                onDelta = { delta ->
                    if (streamActive) {
                        if (firstDeltaAt == null) firstDeltaAt = System.currentTimeMillis()
                        _streamingText.value += delta
                    }
                },
                onReasoning = { text ->
                    if (streamActive) _streamingReasoning.value += text
                },
                onToolCalls = { calls ->
                    if (streamActive) pendingToolCalls = calls
                },
                stopGroupMemberNames = groupMembers.map { it.name },
                onDone = { handleStreamDone(streamContinueMode, onFinished) },
                onError = { e ->
                    if (streamActive) {
                        streamActive = false
                        pendingToolCalls = null
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
                textareaText = _inputDraft.value.orEmpty(),
                continuePrefill = continuePrefill,
                cyclePrompt = cyclePrompt,
                impersonationPrompt = impersonationPrompt,
                quietPrompt = quietPrompt,
                mediaInlining = mediaInlining,
                chatMetadata = chatStore.metadata(sessionId),
                personaDescription = effectivePersona()?.description.orEmpty(),
                anSettings = AuthorsNotePrefsStore.load(getApplication<android.app.Application>()).let {
                    com.emberinn.engine.prompt.AuthorsNoteSettings(
                        default = it.defaultPrompt,
                        defaultPosition = it.defaultPosition,
                        defaultDepth = it.defaultDepth,
                        defaultInterval = it.defaultInterval,
                        defaultRole = it.defaultRole,
                        allowWIScan = it.allowWIScan,
                    )
                },
                charaNote = character?.let { AuthorsNotePrefsStore.charaNote(getApplication(), it.name) },
                // 官方 persona_description_positions：0=IN_PROMPT（story string 注入）、
                // 2/3=TOP/BOTTOM_AN（合并进作者注释）、4=AT_DEPTH（IN_CHAT+深度+角色）、9=NONE 不注入
                personaInPrompt = effectivePersona()?.position == 0 && effectivePersona() != null,
                personaPosition = effectivePersona()?.position ?: 0,
                personaDepth = effectivePersona()?.depth ?: 2,
                personaRole = effectivePersona()?.role ?: 0,
                vectorStore = vectorStore,
                vectorChatSettings = vectorSettings,
                vectorWorldSettings = vectorWorldSettings,
                vectorDataBank = vectorDataBank,
                vectorFileText = { path -> rag.readDataBankText(path) },
                inChatExtensions = inChatExtensions,
                userPrompts = PromptManagerPrefs.prompts(getApplication()),
                userOrder = PromptManagerPrefs.order(getApplication(), character?.id),
                worldInfoSettings = worldInfoSettings,
                globalRegexScripts = globalRegexScripts,
                regexScopedAllowed = regexScopedAllowed,
                regexPresetScripts = presetScripts,
                regexPresetAllowed = presetAllowed,
                isContinue = continueMode,
                regexEnabled = regexEnabled,
                reasoningToPrompts = reasoningToPrompts,
                reasoningTemplate = com.emberinn.app.ui.settings.PresetSettingsStore.load(getApplication()).reasoning.template,
                scriptInjections = scriptInjections,
                useCharacterDepthPrompt = inChatExtensions.isEmpty(),
                memorySummary = memoryService.latestMemory(history),
                memoryTemplate = memorySettings.template,
                memoryPosition = memorySettings.position,
                memoryRole = memorySettings.role,
                memoryDepth = memorySettings.depth,
                memoryScan = memorySettings.scan,
                collapseNewlines = RenderPrefs.collapseNewlines(getApplication()),
                exampleSeparator = RenderPrefs.exampleSeparator(getApplication()),
                userPromptBias = behavior.userPromptBias,
                pinExamples = behavior.pinExamples,
                stripExamples = behavior.stripExamples,
                namesAsStopStrings = behavior.namesAsStopStrings,
                externalWorlds = externalWorlds,
                linkedWorld = linkedWorld,
                chatMetadataWorld = chatMetaWorld,
                globalWorlds = globalSelect + listOfNotNull(personaLoreWorld),
                worldInsertStrategy = WorldInfoPrefs.insertionStrategy(getApplication()),
                wiIncludeNames = WorldInfoPrefs.includeNames(getApplication()),
                onPrepared = { info ->
                    if (streamActive) {
                        // 命中面板/上下文胶囊只影响 UI：丢后台算，不挡请求发出（发送内容不变）
                        viewModelScope.launch(Dispatchers.Default) {
                            _worldHits.value = info.activatedWorldInfo.mapNotNull { entry ->
                                val name = entry.name.ifBlank { entry.keys.firstOrNull().orEmpty() }
                                if (name.isBlank()) null else WorldHitView(
                                    name = name,
                                    key = entry.keys.firstOrNull().orEmpty(),
                                    constant = entry.constant,
                                    vectorized = entry.vectorized,
                                    useProbability = entry.useProbability && entry.probability < 100,
                                    positionLabel = positionLabel(entry.position),
                                    tokens = entryTokens(entry.content),
                                )
                            }
                            // 官方 ChatCompletion 初始 reserveBudget(3)（start_chat）不入 counts，补上更接近实际
                            _contextUsage.value = Pair(info.counts.values.sum() + 3, info.maxContextTokens)
                        }
                        // 官方 WORLD_INFO_ACTIVATED → 自动执行 automationId 匹配的快捷回复
                        runAutoExecutions(info.activatedWorldInfo, type)
                    }
                },
            )
            if (previewOnly) return@launch
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

    /**
     * 流结束统一收尾：先看是否要续工具循环（官方 Generate 工具调用递归），
     * 否则落盘 + 自动续写（官方 triggerAutoContinue；群聊走 runGroupStep）。
     */
    private fun handleStreamDone(continueMode: Boolean, onFinished: (() -> Unit)?) {
        if (!streamActive) {
            onFinished?.invoke()
            return
        }
        if (maybeContinueToolLoop(continueMode, onFinished)) return
        streamActive = false
        finalizeStream(continueMode)
        // 官方 triggerAutoContinue：单聊且满足条件时自动 continue（群聊走 runGroupStep）
        val wasImpersonating = currentStreamParams?.impersonation == true
        val wasSwipe = currentStreamParams?.swipeMode == true
        val lastAi = chatStore.messages(sessionId).lastOrNull { !isUser(it) && !isSystemMsg(it) }
        val lastText = lastAi?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull.orEmpty()
        val cleanProfile = chatRepository.profile()
        val shouldAutoContinue = group == null &&
            !wasImpersonating &&
            !wasSwipe &&
            singleAutoContinueRuns < 5 &&
            AutoContinueEngine.shouldAutoContinue(
                messageChunk = lastText,
                isImpersonate = false,
                config = AutoContinueConfig(
                    enabled = GenerationPrefs.autoContinueEnabled(getApplication()),
                    targetLength = GenerationPrefs.autoContinueTargetLength(getApplication()),
                    allowChatCompletions = GenerationPrefs.allowChatCompletions(getApplication()),
                    mainApi = cleanProfile?.let {
                        com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
                    } ?: "openai",
                    textareaText = _inputDraft.value.orEmpty(),
                    lastMessageText = lastText,
                ),
                tokenCount = { text ->
                    runCatching {
                        com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(cleanProfile?.model.orEmpty()).count(text)
                    }.getOrDefault(0)
                },
            )
        if (shouldAutoContinue) {
            singleAutoContinueRuns++
            continueGeneration()
        }
        onFinished?.invoke()
    }

    /**
     * 官方 Generate 工具调用递归（script.js:5345-5375 语义）：
     * 空回复 + 新发送用户消息 → 删最后用户消息（shouldDeleteMessage）；
     * 非空回复 → 先落盘 assistant 消息（finalizeIntermediaryMessage）；
     * 执行工具 → saveFunctionToolInvocations（extra.tool_invocations 系统消息）→
     * depth+1 重新 Generate('normal')（startStream 重新总装，工具历史进提示词）。
     */
    private fun maybeContinueToolLoop(continueMode: Boolean, onFinished: (() -> Unit)?): Boolean {
        val snapshot = pendingToolCalls ?: return false
        pendingToolCalls = null
        val executed = ToolRegistry.executeToolCalls(snapshot)
        val toolCalls = executed.map { ToolCall(it.id, it.name, it.arguments) }
        val params = currentStreamParams ?: return false
        val reply = _streamingText.value
        val reasoning = _streamingReasoning.value
        val msgs = chatStore.messages(sessionId)
        // 官方 script.js 工具循环决策下沉引擎（ToolLoopPlanner.decide 差分锁定）：
        // shouldDeleteMessage（空回复+无思考+空用户消息）、shouldStopGeneration（无调用结果/stealth）、
        // shouldRecurse（有 tool_calls && 未 stop），nextDepth 只在递归时 +1。
        val decision = ToolLoopPlanner.decide(
            dryRun = false,
            type = "normal",
            depth = toolLoopRuns,
            recurseLimit = ToolLoopPlanner.DEFAULT_RECURSE_LIMIT,
            toolCallingSupported = true,
            isStreaming = true,
            isStreamFinished = true,
            isStreamWithToolCalls = executed.isNotEmpty(),
            hasToolCalls = executed.isNotEmpty(),
            lastMessageExists = msgs.isNotEmpty(),
            lastMessageMes = msgs.lastOrNull()?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull ?: "",
            hasReasoning = reasoning.isNotBlank(),
            streamingResult = reply,
            invocationCount = executed.size,
            stealthCalls = false, // App 无 stealth 概念（工具执行异常计入 executed 空）
        )
        // 官方流式分支：shouldDeleteMessage → 删空用户消息；否则有回复先落盘中间 assistant 消息
        if (decision.shouldDeleteMessage) {
            chatStore.removeAt(sessionId, msgs.lastIndex)
        } else if (reply.isNotBlank()) {
            appendAiReply(reply)
        }
        refreshMessages()
        if (!decision.shouldRecurse) return false
        val profile = chatRepository.profile()
        chatStore.appendToolInvocations(
            sessionId = sessionId,
            invocations = executed,
            api = profile?.providerId,
            model = profile?.model,
            reasoning = reasoning.takeIf { it.isNotBlank() },
        )
        refreshMessages()
        toolLoopRuns = decision.nextDepth
        _streamingText.value = ""
        _streamingReasoning.value = ""
        // 官方递归 Generate('normal')：重新总装（工具调用历史经 extra.tool_invocations 进提示词）
        startStream(
            history = chatStore.messages(sessionId),
            type = "generate",
            continuePrefill = false,
            impersonation = false,
            cyclePrompt = params.cyclePrompt,
            continueMode = false,
            swipeMode = false,
            impersonationPrompt = params.impersonationPrompt,
            mediaInlining = params.mediaInlining,
            characterRawJsonOverride = params.characterRawJsonOverride,
            inChatExtensions = params.inChatExtensions,
            scopedRegexAvatar = params.scopedRegexAvatar,
            groupGenId = params.groupGenId,
            onFinished = onFinished,
        )
        return true
    }

    private fun finalizeStream(continueMode: Boolean = false) {
        _isStreaming.value = false
        streamSession = null
        // 官方 saveReply：getRegexedString(getMessage, isImpersonate ? USER_INPUT : AI_OUTPUT)，
        // 冒充不落盘（进输入框，发送时再过 USER_INPUT）；continue/swipe/普通回复都先过 AI_OUTPUT
        val wasImpersonating = _isImpersonating.value
        val wasSwipe = generatingSwipe
        val rawReply = if (GlobalRegexPrefs.enabled(getApplication())) {
            RegexPipelineEngine.apply(_streamingText.value, ChatPromptFactory.REGEX_AI_OUTPUT, saveRegexScripts)
        } else {
            _streamingText.value
        }
        // 官方 saveReply：cleanUpMessage（停用词/名字/endoftext/Instruct/群消息/trim 全链）
        val behavior = BehaviorPrefs.load(getApplication())
        val profileForClean = chatRepository.profile()
        val apiForClean = profileForClean?.let {
            com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
        } ?: "openai"
        val stoppingStrings = StoppingStringsEngine.getStoppingStrings(
            api = apiForClean,
            config = StoppingStringsConfig(
                isImpersonate = wasImpersonating,
                isContinue = continueMode,
                name1 = currentUserName,
                name2 = currentCharName,
                chatLastIsUser = chatStore.messages(sessionId).lastOrNull()?.let { isUser(it) } == true,
                groupMemberNames = groupMembers.map { it.name },
                selectedGroup = group != null,
                namesAsStopStrings = behavior.namesAsStopStrings,
                env = MacroEnv(user = currentUserName, char = currentCharName),
            ),
        )
        val reply = CleanUpMessageEngine.clean(
            getMessage = rawReply,
            config = CleanUpConfig(
                isImpersonate = wasImpersonating,
                isContinue = continueMode,
                stoppingStrings = stoppingStrings,
                name1 = currentUserName,
                name2 = currentCharName,
                hasReasoningPrefix = _streamingReasoning.value.isNotBlank(),
                groupMemberNames = groupMembers.map { it.name },
                groupTrimmingEnabled = group != null,
                collapseNewlines = RenderPrefs.collapseNewlines(getApplication()),
                trimSpaces = behavior.trimSpaces,
                trimSentences = behavior.trimSentences,
                userPromptBias = behavior.userPromptBias.takeIf { it.isNotBlank() },
                includeUserPromptBias = behavior.showUserPromptBias,
            ),
        )
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
                } else {
                    _notice.value = "（模型只返回了思考过程，没有生成正文——多半是“最大回复 tokens”太小被思考占满。去 设置→提供商→最大回复 tokens 调大（如 8192），或关闭思考模式。）"
                }
            }
            reply.isNotBlank() -> {
                appendAiReply(reply)
                // 官方 power_user.auto_swipe：过滤命中（最短长度/黑名单阈值）→ 自动生成新变体
                if (behavior.autoSwipe && com.emberinn.engine.prompt.SwipeEngine.generatedTextFiltered(
                        text = reply,
                        minimumLength = behavior.autoSwipeMinimumLength,
                        blacklist = behavior.autoSwipeBlacklist.toList(),
                        threshold = behavior.autoSwipeBlacklistThreshold,
                    )
                ) {
                    val aiIdx = chatStore.messages(sessionId).lastIndex
                    if (aiIdx >= 0) swipeRight(aiIdx)
                }
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
        // 官方 translate 扩展：auto_mode=responses/both 时 AI 回复自动翻译（译文进 extra.display_text）
        if (!wasImpersonating) {
            val lastAi = chatStore.messages(sessionId).indexOfLast { !isUser(it) }
            if (lastAi >= 0) translateIncoming(lastAi, _streamingReasoning.value.takeIf { it.isNotBlank() })
        }
        // 官方 memory 扩展 onChatEvent（CHARACTER_MESSAGE_RENDERED 后）：满足条件时自动总结
        if (!wasImpersonating) memoryService.maybeAutoSummarize(sessionId)
        // 官方 /inject ephemeral：GENERATION_ENDED/STOPPED 后删除注入
        clearEphemeralInjects()
    }

    private fun clearEphemeralInjects() {
        if (ephemeralInjectIds.isEmpty()) return
        val meta = chatStore.metadata(sessionId).toMutableMap()
        val injects = (meta["script_injects"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        ephemeralInjectIds.forEach { injects.remove(it) }
        ephemeralInjectIds.clear()
        meta["script_injects"] = JsonObject(injects)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
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
            refreshMessagesAppendOnly()
        }
    }

    /** AI 回复落盘：带官方字段（api/model/gen_started/gen_finished/reasoning/time_to_first_token）。 */
    private fun appendAiReply(reply: String) {
        val profile = chatRepository.profile()
        val startedAt = runCatching { java.time.Instant.parse(streamStartedAt).toEpochMilli() }.getOrNull()
        val ttf = firstDeltaAt?.let { first -> startedAt?.let { (first - it).coerceAtLeast(0) } }
        chatStore.append(
            sessionId = sessionId,
            isUser = false,
            content = reply,
            name = currentCharName,
            api = profile?.providerId,
            model = profile?.model,
            timeToFirstToken = ttf,
            genStarted = streamStartedAt,
            genFinished = java.time.Instant.now().toString(),
            reasoning = _streamingReasoning.value.takeIf { it.isNotBlank() },
            // 官方群聊 AI 消息带 gen_id（group_generation_id，整批共享）；单聊不带
            groupGenId = pendingGroupGenId,
        )
        // 官方 expressions：回复落盘后把选中的精灵路径写进 extra.sprite（渲染优先读它）
        runCatching {
            val prefs = com.emberinn.app.ui.settings.ExpressionPrefs.load(getApplication())
            if (prefs.enabled) {
                val store = com.emberinn.app.data.ExpressionStore(getApplication())
                val expression = com.emberinn.engine.expression.ExpressionEngine.sampleClassifyText(reply)
                val groups = com.emberinn.engine.expression.ExpressionEngine.groupSprites(
                    store.sprites(currentCharName),
                    prefs.customLabels,
                )
                val chosen = com.emberinn.engine.expression.ExpressionEngine.chooseSprite(
                    folderName = currentCharName,
                    expression = expression ?: "",
                    spriteCache = mapOf(currentCharName to groups),
                    settings = com.emberinn.engine.expression.ExpressionEngine.ExpressionSettings(
                        fallbackExpression = prefs.fallbackExpression.ifBlank { null },
                        allowMultiple = prefs.allowMultiple,
                        rerollIfSame = prefs.rerollIfSame,
                        customLabels = prefs.customLabels,
                    ),
                )?.imageSrc
                if (chosen != null) {
                    val idx = chatStore.messages(sessionId).lastIndex
                    if (idx >= 0) chatStore.setExtraValue(sessionId, idx, "sprite", chosen)
                }
            }
        }
        // 官方 power_user.message_token_count_enabled：extra.token_count 落盘
        if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
            val model = profile?.model.orEmpty()
            val count = runCatching {
                com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(model)
                    .count((_streamingReasoning.value + reply))
            }.getOrDefault(reply.length / 4)
            val idx = chatStore.messages(sessionId).lastIndex
            if (idx >= 0) chatStore.setExtraValue(sessionId, idx, "token_count", count.toString())
        }
    }

    private fun refreshMessages() {
        _messages.value = chatStore.messages(sessionId)
        displayCache.clear()
    }

    /** 仅“末尾追加”后的轻量刷新：旧消息索引不变，显示缓存仍有效，不全表失效。 */
    private fun refreshMessagesAppendOnly() {
        _messages.value = chatStore.messages(sessionId)
    }

    /** 世界书命中面板行（README 状态面板：名字/关键词/常驻/位置/token）。 */
    data class WorldHitView(
        val name: String,
        val key: String,
        val constant: Boolean,
        val vectorized: Boolean,
        val useProbability: Boolean,
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
