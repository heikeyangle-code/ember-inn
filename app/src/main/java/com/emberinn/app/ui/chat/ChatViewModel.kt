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
import com.emberinn.app.data.CfgPrefs
import com.emberinn.app.data.ChatPromptFactory
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ContextBudgetException
import com.emberinn.app.data.DisplayCacheVersion
import com.emberinn.app.data.ItemizationEntry
import com.emberinn.app.data.ItemizationStore
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
import com.emberinn.app.data.DisplayPipeline
import com.emberinn.app.ui.settings.RenderPrefs
import com.emberinn.app.ui.settings.ServicesPrefs
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.app.ui.settings.WorldInfoPrefs
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.prompt.LogprobsEngine
import com.emberinn.engine.prompt.MessageFormattingEngine
import com.emberinn.engine.prompt.MessageFormattingSettings
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
import com.emberinn.engine.prompt.CfgPromptEngine
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
import kotlinx.serialization.json.JsonArray
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

    init {
        // 卡解析预热：后台提前解析当前角色卡，首次发送直接命中 LRU 缓存
        viewModelScope.launch(Dispatchers.Default) {
            character?.rawJson?.let { chatRepository.warmCard(it) }
        }
    }

    /** 显示文本缓存：displayTextOf 只在消息刷新时算一次，组合期不再读盘/跑正则（性能）。
     *  设置（encode_tags/正则/允许列表）变更时 DisplayCacheVersion.bump()，这里整体失效即时生效。
     *  缓存条目绑定消息身份（send_date）：结构变更（swipe/翻译/删除）后同 index 指向不同消息时自动失效，
     *  防止显示错条内容（索引漂移）。 */
    private data class DisplayCacheEntry(val sendDate: String?, val text: String)
    private val displayCache = mutableMapOf<Int, DisplayCacheEntry>()
    private var displayCacheVersion = -1

    /** 显示管线上下文缓存（全局设置层）：behavior/fixMarkdown/encodeTags/推理模板/正则开关与脚本/宏环境。
     *  旧实现在 displayTextOf 未命中缓存时逐条消息重新加载——PresetSettingsStore.load 是主线程
     *  磁盘 JSON 读+反序列化，BehaviorPrefs/GlobalRegexPrefs 每条一读，usableIndices 每条全表扫描：
     *  这是滚动时"每出现一条新消息卡一下"的元凶。现在只在 DisplayCacheVersion / 会话身份 / 人设变化时刷新。 */
    private var dctxValid = false
    private var dctxVersion = -1
    private var dctxSessionId: String? = null
    private var dctxBehavior: com.emberinn.app.ui.settings.BehaviorSettings? = null
    private var dctxFixMarkdown = false
    private var dctxEncodeTags = false
    private var dctxReasoningPrefix = ""
    private var dctxReasoningSuffix = ""
    private var dctxRegexEnabled = false
    private var dctxScripts: List<com.emberinn.engine.regex.RegexPipelineScript>? = null
    private var dctxEnv: com.emberinn.engine.macros.MacroEnv? = null
    private var dctxUserName = ""
    private var dctxCharName = ""

    private fun ensureDisplayCtx() {
        if (dctxValid && dctxVersion == DisplayCacheVersion.version && dctxSessionId == sessionId &&
            dctxUserName == currentUserName && dctxCharName == currentCharName
        ) {
            return
        }
        val app = getApplication<Application>()
        val behavior = com.emberinn.app.ui.settings.BehaviorPrefs.load(app)
        val template = com.emberinn.app.ui.settings.PresetSettingsStore.load(app).reasoning.template
        dctxBehavior = behavior
        dctxFixMarkdown = com.emberinn.app.ui.settings.AppearancePrefs.fixMarkdown(app)
        dctxEncodeTags = com.emberinn.app.ui.settings.AppearancePrefs.encodeTags(app)
        dctxReasoningPrefix = template.prefix
        dctxReasoningSuffix = template.suffix
        dctxRegexEnabled = com.emberinn.app.ui.settings.GlobalRegexPrefs.enabled(app)
        dctxScripts = if (dctxRegexEnabled) resolveDisplayRegexScripts() else emptyList()
        dctxEnv = displayEnv()
        dctxUserName = currentUserName
        dctxCharName = currentCharName
        dctxVersion = DisplayCacheVersion.version
        dctxSessionId = sessionId
        dctxValid = true
    }

    /** usable 消息下标（官方 depth 用的非系统消息序列）：随消息表实例缓存，替代逐消息全表扫描。 */
    private var usableCacheRef: List<JsonElement>? = null
    private var usableIndicesCache: List<Int> = emptyList()
    private fun usableIndicesFor(msgs: List<JsonElement>): List<Int> {
        if (usableCacheRef !== msgs) {
            usableCacheRef = msgs
            usableIndicesCache = msgs.indices.filter { !isSystemMsg(msgs[it]) }
        }
        return usableIndicesCache
    }

    /** 显示管线修订号：消息列表每次替换 / DisplayCacheVersion 失效时 +1。
     *  ChatScreen 的 derived remember 以此为 key，缓存失效后历史行立即重算（修复改设置后显示旧文本）。 */
    private val _displayRevision = MutableStateFlow(0)
    val displayRevision: StateFlow<Int> = _displayRevision
    /** 最近一次真实发送（非预览）的总装明细，落盘时写进 ItemizationStore（官方 itemizedPrompts）。 */
    private var pendingItemization: ItemizationEntry? = null

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    /** 流式思考过程（官方 reasoning 独立通道，不进聊天正文）。 */
    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning
    /** 官方 oai_settings.show_thoughts（默认 true）：显示/保存思考过程；会话菜单可快速开关。 */
    private val _showThoughts = MutableStateFlow(chatRepository.profile()?.sampler?.showThoughts != false)
    val showThoughts: StateFlow<Boolean> = _showThoughts

    // 流式节流缓冲（对齐官方 Stopwatch(1000/streaming_fps)≈33ms 单层节流）：SSE 每 token 只追加进
    // StringBuilder，每 ~33ms 才写一次 StateFlow。官方每 tick 全量重算 messageFormatting 也只受
    // streaming_fps 上限约束；此前 100ms+UI 120ms 双层叠加 ~220ms 一帧导致流式视觉卡顿。
    private val streamingTextBuffer = StringBuilder()
    private val streamingReasoningBuffer = StringBuilder()
    private var streamingFlusher: kotlinx.coroutines.Job? = null
    /** flusher 启动与缓冲弹出共用一把锁：避免双 flusher 并发读-改-写 StateFlow 丢 delta。 */
    private val flushLock = Any()

    private fun appendStreamingText(delta: String) {
        synchronized(streamingTextBuffer) { streamingTextBuffer.append(delta) }
        ensureStreamingFlusher()
    }

    private fun appendStreamingReasoning(text: String) {
        synchronized(streamingReasoningBuffer) { streamingReasoningBuffer.append(text) }
        ensureStreamingFlusher()
    }

    private fun ensureStreamingFlusher() {
        // check-then-act 全程持锁：SSE 回调线程并发触发时只会启动一个 flusher
        synchronized(flushLock) {
            if (streamingFlusher?.isActive == true) return
            streamingFlusher = viewModelScope.launch {
                while (true) {
                    delay(33)
                    flushStreamingBuffers()
                    if (!streamActive && streamingTextBuffer.isEmpty() && streamingReasoningBuffer.isEmpty()) break
                }
            }
        }
    }

    private fun flushStreamingBuffers() {
        synchronized(flushLock) {
            var text: String? = null
            var reasoning: String? = null
            synchronized(streamingTextBuffer) {
                if (streamingTextBuffer.isNotEmpty()) {
                    text = streamingTextBuffer.toString()
                    streamingTextBuffer.clear()
                }
            }
            synchronized(streamingReasoningBuffer) {
                if (streamingReasoningBuffer.isNotEmpty()) {
                    reasoning = streamingReasoningBuffer.toString()
                    streamingReasoningBuffer.clear()
                }
            }
            // 读-改-写在锁内：与 stop()/handleStreamDone 的 flush 互斥，停止瞬间不丢末尾 token
            text?.let {
                _streamingText.value = _streamingText.value + it
                // 官方 onProgressStreaming(L3616)：冒充流式每 tick 先 cleanUpMessage(isImpersonate) + 定界符补齐
                // 再写 send_textarea——输入框里是清洗后的文本，不是裸流（否则名字残留/未闭合引号先闪现再消失）
                if (_isImpersonating.value) {
                    _impersonationDraft.value = cleanImpersonationText(_streamingText.value, isFinal = false)
                }
            }
            reasoning?.let { _streamingReasoning.value = _streamingReasoning.value + it }
        }
    }

    /** 冒充流式输入框显示（官方 script.js:3600-3618 语义：clean(isImpersonate) + balance 后进 textarea）。 */
    private val _impersonationDraft = MutableStateFlow("")
    val impersonationDraft: StateFlow<String> = _impersonationDraft

    /** 官方冒充显示管线：cleanUpMessage(isImpersonate=true)（去用户名前缀/collapseNewlines/trimSpaces）
     *  + charsToBalance（* " ``` ~~~ 奇数补齐，isFinal 不补）。 */
    private fun cleanImpersonationText(raw: String, isFinal: Boolean): String {
        val behavior = BehaviorPrefs.load(getApplication())
        val cleaned = CleanUpMessageEngine.clean(
            getMessage = raw,
            config = CleanUpConfig(
                isImpersonate = true,
                name1 = currentUserName,
                name2 = currentCharName,
                collapseNewlines = RenderPrefs.collapseNewlines(getApplication()),
                trimSpaces = behavior.trimSpaces,
            ),
        )
        return DisplayPipeline.balanceStreamingDelimiters(cleaned, isFinal = isFinal)
    }

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
    private val _logprobs = MutableStateFlow<List<LogprobsEngine.TokenLogprobs>?>(null)
    val contextUsage: StateFlow<Pair<Int, Int>?> = _contextUsage
    val logprobs: StateFlow<List<LogprobsEngine.TokenLogprobs>?> = _logprobs

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    /** 生成纪元：每次进入 startStream（发送/继续/重生成/变体/冒充/群聊轮次）+1。
     *  官方 generate() 开头 scrollLock = false——所有生成类型开始时恢复自动贴底跟随，UI 以此对齐。 */
    private val _generationEpoch = MutableStateFlow(0)
    val generationEpoch: StateFlow<Int> = _generationEpoch

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

    private val _promptPreview = MutableStateFlow<com.emberinn.app.data.PromptPreview?>(null)
    val promptPreview: StateFlow<com.emberinn.app.data.PromptPreview?> = _promptPreview

    /** dryRun 提示词预览：只总装不发送（官方 Generate dryRun），结果供 UI 展示。 */
    fun previewPrompt() {
        if (_isStreaming.value) return
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，无法预览提示词。）"
            return
        }
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

    /** 官方 /genraw：直接用提示请求（system/prefill/length 可选），返回生成文本。
     *  官方 generateRawCallback + createRawPrompt：instruct=off 跳过 instruct 格式化、
     *  as=char → quietToLoud（输出序列用角色名）；text completion 走 instruct 整段格式化，
     *  chat completion（openai 族）官方 instruct/as 本就不参与（isInstruct 排除 openai）。 */
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
        // 官方 genraw：stop 是 JSON 数组（一次性停用词）
        val stops = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .parseToJsonElement(stop).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        // 官方 createRawPrompt：text completion（textgen/novel/kobold）走文本化
        // （novel 被 isInstruct 排除，但仍走名字前缀 + adjustNovelInstructionPrompt 拼接）。
        // 官方字符串 prompt 先 .trim() 再转消息；length<=0 视为未设置。
        val protocol = chatRepository.profile()?.let {
            com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
        }
        val effectiveLength = length?.takeIf { it > 0 }
        val textPrompt = if (protocol in setOf("textgenerationwebui", "novel", "kobold")) {
            val presetState = com.emberinn.app.ui.settings.PresetSettingsStore.load(getApplication())
            val raw = com.emberinn.engine.prompt.InstructMode.createRawPrompt(
                prompt = listOf(com.emberinn.engine.prompt.PromptMessage(role = "user", content = prompt.trim())),
                api = protocol ?: "",
                instructOverride = !instruct,
                quietToLoud = asRole == "char",
                systemPrompt = system,
                prefill = prefill,
                instruct = presetState.instruct,
                context = presetState.context,
                env = MacroEnv(user = currentUserName, char = currentCharName),
            )
            (raw as? com.emberinn.engine.prompt.InstructMode.RawPrompt.Text)?.text
        } else {
            null
        }
        val result = chatRepository.rawGenerate(prompt, system, prefill, effectiveLength, stops, textPrompt) ?: "（生成失败）"
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

    /** 会话菜单快速开关 show_thoughts（官方 oai_settings.show_thoughts，默认 true）。 */
    fun setShowThoughtsQuick(enabled: Boolean) {
        _showThoughts.value = enabled
        if (!enabled) {
            synchronized(streamingReasoningBuffer) { streamingReasoningBuffer.clear() }
            _streamingReasoning.value = ""
            _lastReasoning.value = null
        }
        val p = chatRepository.profile() ?: return
        chatRepository.saveProfile(p.copy(sampler = p.sampler.copy(showThoughts = enabled)), active = true)
        ProviderState.refresh(chatRepository.profile())
    }

    /** 图像生成（A1111）：成功则追加到待发送附件，用户可预览后发送。 */
    fun generateImage(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val path = imageGenClient.generate(
                ctx,
                prompt,
                negativePrompt = ServicesPrefs.imageCharaNegativePrompt(ctx, character?.id),
                extraPrompt = ServicesPrefs.imageCharaPrompt(ctx, character?.id),
            )
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

    /** 官方 sd_message_gen：用消息文本生成图片并挂到该消息 extra.media（非待发送附件）。 */
    fun generateImageForMessage(index: Int) {
        val list = chatStore.messages(sessionId)
        val text = list.getOrNull(index)?.jsonObject?.get("mes")?.jsonPrimitive?.contentOrNull ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val path = imageGenClient.generate(
                ctx,
                text,
                negativePrompt = ServicesPrefs.imageCharaNegativePrompt(ctx, character?.id),
                extraPrompt = ServicesPrefs.imageCharaPrompt(ctx, character?.id),
            )
            withContext(Dispatchers.Main) {
                if (path != null) {
                    chatStore.addMediaToMessage(sessionId, index, "image", path, "生成的图片")
                    refreshMessages()
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
        val targetLang = ServicesPrefs.translateTargetLanguage(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            val translated = translateClient.translate(getApplication(), text)
            // 官方 translateIncomingMessageReasoning：reasoning 译文写 extra.reasoning_display_text。
            // 流式完成落盘后调用（见 appendAiReply/appendGeneratedSwipe 后的 translateIncoming(lastAi, rawReasoning)）。
            val reasoningTranslated = if (!reasoning.isNullOrBlank()) {
                translateClient.translateReasoning(getApplication(), reasoning, targetLang)
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
    private fun displayEnv(): MacroEnv =
        ChatPromptFactory().displayMacroEnv(currentUserName, currentCharName, character?.rawJson, chatRepository.localVariableStore())

    /** 推理显示文本（官方 messageFormatting isReasoning=true：REASONING 位点正则 → fixMarkdown → encode_tags）。
     *  官方 messageId=-1 无 depth 过滤；ch_name 为空 → characterOverride 空。 */
    private val reasoningDisplayCache = mutableMapOf<String, String>()
    private var reasoningDisplayCacheVersion = -1
    fun displayReasoningText(raw: String): String {
        if (raw.isBlank()) return raw
        if (reasoningDisplayCacheVersion != DisplayCacheVersion.version) {
            reasoningDisplayCache.clear()
            reasoningDisplayCacheVersion = DisplayCacheVersion.version
        }
        reasoningDisplayCache[raw]?.let { return it }
        ensureDisplayCtx()
        val env = dctxEnv!!
        val regexEnabled = dctxRegexEnabled
        val scripts = dctxScripts!!
        // 官方 messageFormatting isReasoning=true：messageId=-1、ch_name=''（bias/名字剥离跳过）
        val result = MessageFormattingEngine.format(
            mes = raw,
            chName = "",
            isSystem = false,
            isUser = false,
            isNarrator = false,
            messageId = -1,
            isReasoning = true,
            settings = MessageFormattingSettings(
                userPromptBias = "",
                showUserPromptBias = true,
                autoFixMarkdown = dctxFixMarkdown,
                encodeTags = dctxEncodeTags,
                reasoningPrefix = dctxReasoningPrefix,
                reasoningSuffix = dctxReasoningSuffix,
                allowName2Display = true,
            ),
            depth = null,
            macroSubstitute = { MacroEngine.substitute(it, env) },
            regexApply = if (regexEnabled) { text, placement, depth ->
                RegexPipelineEngine.apply(
                    raw = text,
                    placement = placement,
                    scripts = scripts,
                    isMarkdown = true,
                    depth = depth,
                    characterOverride = "",
                    substitute = { MacroEngine.substitute(it, env) },
                )
            } else { text, _, _ -> text },
        )
        reasoningDisplayCache[raw] = result.text
        return result.text
    }

    fun displayTextOf(index: Int): String {
        syncDisplayVersion()
        val msgs = _messages.value
        val el = msgs.getOrNull(index)?.jsonObject ?: return ""
        val sendDate = el["send_date"]?.jsonPrimitive?.contentOrNull
        // 身份校验：同 index 但消息已换（结构变更漂移）→ 视为未命中重算
        displayCache[index]?.takeIf { it.sendDate == sendDate }?.let { return it.text }
        val extra = el["extra"] as? JsonObject
        val base = extra?.get("display_text")?.jsonPrimitive?.contentOrNull
            ?: el["mes"]?.jsonPrimitive?.contentOrNull ?: return ""
        // 官方 messageFormatting 纯文本子集全部交给引擎（MessageFormattingEngine，差分锁死）：
        // 首条宏替换 → Note/systemUserName 归一 → bias 剥离 → 显示正则 → fixMarkdown → encode_tags
        // → reasoning 前后缀转义 → allow_name2_display 名字前缀剥离
        // 全局设置/宏环境/正则脚本全部走 ensureDisplayCtx 缓存（组合期零 prefs/磁盘 IO）
        ensureDisplayCtx()
        val behavior = dctxBehavior!!
        val env = dctxEnv!!
        val regexEnabled = dctxRegexEnabled
        val scripts = dctxScripts!!
        val rawIsSystem = isSystemMsg(el)
        val isUser = isUser(el)
        val chName = el["name"]?.jsonPrimitive?.contentOrNull
        val isNarrator = extra?.get("type")?.jsonPrimitive?.contentOrNull == "narrator"
        // 官方 depth：usableMessages.length - indexOf - 1（usable 不含系统消息）；
        // 按原始下标定位，避免结构相等消息（内容重复）误匹配
        val usableIndices = usableIndicesFor(msgs)
        val pos = usableIndices.indexOf(index)
        val depth = if (pos >= 0) usableIndices.size - pos - 1 else null
        val result = MessageFormattingEngine.format(
            mes = base,
            chName = chName,
            isSystem = rawIsSystem,
            isUser = isUser,
            isNarrator = isNarrator,
            messageId = index,
            settings = MessageFormattingSettings(
                userPromptBias = behavior.userPromptBias,
                showUserPromptBias = behavior.showUserPromptBias,
                autoFixMarkdown = dctxFixMarkdown,
                encodeTags = dctxEncodeTags,
                reasoningPrefix = dctxReasoningPrefix,
                reasoningSuffix = dctxReasoningSuffix,
                allowName2Display = behavior.allowName2Display,
            ),
            depth = depth,
            macroSubstitute = { MacroEngine.substitute(it, env) },
            regexApply = if (regexEnabled) { text, placement, d ->
                RegexPipelineEngine.apply(
                    raw = text,
                    placement = placement,
                    scripts = scripts,
                    isMarkdown = true,
                    depth = d,
                    characterOverride = chName ?: currentCharName,
                    substitute = { MacroEngine.substitute(it, env) },
                )
            } else { text, _, _ -> text },
        )
        // 官方 messageFormatting：messageId==0 时把宏替换结果写回 chat.mes（chatMessage.mes === 原文
        // 且 extra.display_text !== 原文 才写；只改 mes，不动 swipes/extra）
        result.firstMessageSubstituted?.let { substituted ->
            val rawMes = el["mes"]?.jsonPrimitive?.contentOrNull
            val displayText = extra?.get("display_text")?.jsonPrimitive?.contentOrNull
            if (rawMes == base && displayText != base) {
                chatStore.updateMesText(sessionId, index, substituted)
            }
        }
        val out = result.text
        displayCache[index] = DisplayCacheEntry(sendDate, out)
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
                substitute = { MacroEngine.substitute(it, MacroEnv(user = currentUserName, char = resolvedName)) },
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
    /** 显示位点正则脚本集合（displayTextOf/displayReasoningText 共用）。 */
    private fun resolveDisplayRegexScripts(): List<RegexPipelineScript> {
        val (presetScripts, presetAllowed) = presetRegex()
        return ChatPromptFactory().resolveRegexScripts(
            characterRawJson = character?.rawJson,
            globalRegexScripts = GlobalRegexPrefs.read(getApplication()),
            scopedAllowed = character?.let { "${it.id}.png" in GlobalRegexPrefs.characterAllowedRegex(getApplication()) } ?: false,
            presetScripts = presetScripts,
            presetAllowed = presetAllowed,
        )
    }

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

    /** 官方 bookmarks.js createBranch：以 [0..at] 为快照新建分支会话并切换（名称 `<源> - Branch #N`）。 */
    fun createBranch(at: Int): SessionRecord? {
        val src = chatStore.get(sessionId) ?: return null
        val list = chatStore.messages(sessionId)
        if (list.isEmpty()) return null
        val idx = at.coerceIn(0, list.lastIndex)
        val existing = chatStore.list().map { it.name }.toSet()
        val suffix = Regex(" - Branch #\\d+$")
        val legacy = Regex("^Branch #\\d+ - ")
        val clean = legacy.replace(suffix.replace(src.name, ""), "")
        var i = 1
        var name = "$clean - Branch #$i"
        while (name in existing) {
            i++
            name = "$clean - Branch #$i"
        }
        val branch = chatStore.createBranchSession(sessionId, idx, name) ?: return null
        chatStore.addBranchName(sessionId, idx, name)
        refreshMessages()
        _notice.value = "（已创建分支：$name）"
        return branch
    }

    /** 官方 bookmarks.js convertSoloToGroupChat：确认后建群（成员=当前角色，SWAP 模式）并转换当前会话消息。 */
    fun convertToGroup(): SessionRecord? {
        val src = chatStore.get(sessionId) ?: return null
        val char = character ?: return null
        val groups = groupStore.list().map { it.name }.toSet()
        var i = 1
        var name = "Group: ${char.name}"
        while (name in groups) {
            i++
            name = "Group: ${char.name} ($i)"
        }
        val groupId = java.util.UUID.randomUUID().toString()
        groupStore.save(
            GroupRecord(
                id = groupId,
                name = name,
                members = listOf(char.id),
                generationMode = com.emberinn.engine.group.GroupGenerationMode.SWAP,
                activationStrategy = "natural",
            ),
        )
        val chatName = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        val record = chatStore.createConvertedGroupSession(sessionId, groupId, char.name, char.avatarPath, chatName)
        if (record != null) _notice.value = "（已转换为群聊：$name）"
        return record
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
        // 当前会话也在 Past Chats 列表里：改名后驱动弹层刷新
        _pastChatsRevision.value++
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
        // 官方 presetCommandCallback：getPresetManager() 按当前 API 取预设列表
        val names = com.emberinn.app.ui.settings.PresetSettingsStore.samplerPresetNames(getApplication())
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
            impersonationPrompt = impersonationPromptText(),
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
        // speak 现为 suspend（支持外部 HTTP 后端 MediaPlayer 播放）：协程发起
        viewModelScope.launch(Dispatchers.IO) {
            val ok = TtsReader.speak(getApplication(), cleaned, voice.voice, voice.rate, voice.narrateByParagraphs)
            if (!ok && cleaned.isNotBlank()) {
                _notice.value = "（语音引擎未就绪，请到 设置→语音 检查。）"
            }
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

    // ---- CFG Scale（官方 scripts/cfg-scale.js；全局/角色/会话三级）----

    /** 当前会话 CFG 快照：全局 + 角色 + 会话（chat_metadata.cfg_*）。 */
    fun cfgSnapshot(): Triple<CfgPromptEngine.CfgGlobal, CfgPromptEngine.CfgChara?, CfgPromptEngine.CfgChat> {
        val ctx = getApplication<Application>()
        val meta = chatStore.metadata(sessionId)
        fun d(key: String): Double? = meta[key]?.jsonPrimitive?.content?.toDoubleOrNull()
        fun s(key: String): String = meta[key]?.jsonPrimitive?.contentOrNull ?: ""
        fun i(key: String): Int? = meta[key]?.jsonPrimitive?.content?.toIntOrNull()
        val chat = CfgPromptEngine.CfgChat(
            guidanceScale = d("cfg_guidance_scale"),
            negativePrompt = s("cfg_negative_prompt"),
            positivePrompt = s("cfg_positive_prompt"),
            promptCombine = meta["cfg_prompt_combine"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList(),
            groupchatIndividualChars = meta["cfg_groupchat_individual_chars"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            promptInsertionDepth = i("cfg_prompt_insertion_depth") ?: 1,
            promptSeparator = meta["cfg_prompt_separator"]?.jsonPrimitive?.contentOrNull,
        )
        return Triple(CfgPrefs.global(ctx), CfgPrefs.chara(ctx, character?.id), chat)
    }

    fun saveCfgGlobal(g: CfgPromptEngine.CfgGlobal) {
        CfgPrefs.saveGlobal(getApplication(), g)
        _notice.value = "（CFG 全局设置已保存，下次发送生效）"
    }

    fun saveCfgChara(c: CfgPromptEngine.CfgChara) {
        val id = character?.id ?: return
        CfgPrefs.saveChara(getApplication(), c.copy(name = id))
        _notice.value = "（角色 CFG 已保存，下次发送生效）"
    }

    fun saveCfgChat(c: CfgPromptEngine.CfgChat) {
        val meta = chatStore.metadata(sessionId).toMutableMap()
        if (c.guidanceScale == null) meta.remove("cfg_guidance_scale") else meta["cfg_guidance_scale"] = JsonPrimitive(c.guidanceScale)
        if (c.negativePrompt.isBlank()) meta.remove("cfg_negative_prompt") else meta["cfg_negative_prompt"] = JsonPrimitive(c.negativePrompt)
        if (c.positivePrompt.isBlank()) meta.remove("cfg_positive_prompt") else meta["cfg_positive_prompt"] = JsonPrimitive(c.positivePrompt)
        if (c.promptCombine.isEmpty()) meta.remove("cfg_prompt_combine") else meta["cfg_prompt_combine"] = kotlinx.serialization.json.JsonArray(c.promptCombine.map { JsonPrimitive(it) })
        if (!c.groupchatIndividualChars) meta.remove("cfg_groupchat_individual_chars") else meta["cfg_groupchat_individual_chars"] = JsonPrimitive(true)
        if (c.promptInsertionDepth == 1) meta.remove("cfg_prompt_insertion_depth") else meta["cfg_prompt_insertion_depth"] = JsonPrimitive(c.promptInsertionDepth)
        if (c.promptSeparator.isNullOrBlank()) meta.remove("cfg_prompt_separator") else meta["cfg_prompt_separator"] = JsonPrimitive(c.promptSeparator)
        chatStore.saveMetadata(sessionId, JsonObject(meta))
        _notice.value = "（会话 CFG 已保存，下次发送生效）"
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
    /** 用户主动停止标记（官方 stopGeneration：群聊整批终止，不推进下一位发言人）。 */
    @Volatile
    private var userStopped = false

    /** 单轮流式完成闩锁：网络层双 [DONE]/message_stop 拆块等会让 onDone 触发两次，
     *  handleStreamDone 必须只收尾一次——第二次会走 "!streamActive → onFinished()" 早退分支，
     *  把群聊下一位成员推进两次，产生重复消息（每次翻倍）。startStream 开新流时复位。 */
    @Volatile private var streamDoneHandled = false
    private var currentCharName = "Assistant"
    private var currentUserName = "User"
    val userName: String get() = currentUserName

    /** 生成入口统一刷新说话人名（官方 name1/name2 全局变量在聊天加载时已就绪；
     *  冒充提示词宏/停用词/cleanUp 都依赖，不能停在 "Assistant"/"User" 默认值）。 */
    private fun refreshSpeakerNames() {
        chatStore.get(sessionId)?.name?.takeIf { it.isNotBlank() }?.let { currentCharName = it }
        effectivePersona()?.name?.takeIf { it.isNotBlank() }?.let { currentUserName = it }
    }

    init {
        // 官方在聊天加载时即填充 name1/name2（selectChat/getChara）；
        // 未发送前冒充/重生成/续写/滑动的宏与停用词不能停在默认值
        refreshSpeakerNames()
        // 第三层主题（角色配方）：当前角色进入全局主题管线；离开聊天由 ChatScreen 清空回全局
        ThemeState.update(
            recipe = character?.let { CharacterCardEdit.readThemeRecipe(it.rawJson) },
            seedColor = character?.let { it.seedColor ?: nameHashSeed(it.name) },
        )
        // 官方新聊天第一条消息 = 角色开场白 first_mes（script.js newChat 语义）；空会话才补。
        // README：AI 对话（无角色卡）带默认开场“我是余烬，想聊点什么？”
        val isGroupSession = chatStore.get(sessionId)?.groupId != null
        // 官方 preset-manager.js autoSelectPreset（CHAT_CHANGED）：当前 API 的采样预设里
        // 角色名/群聊名精确匹配 → 自动选中并应用（getPresetManager() 按 main_api 取）
        val autoPresetName = if (isGroupSession) group?.name else character?.name
        if (!autoPresetName.isNullOrBlank()) {
            val samplerNames = com.emberinn.app.ui.settings.PresetSettingsStore.samplerPresetNames(getApplication())
            val presetPrefs = com.emberinn.app.ui.settings.PresetPrefsStore.load(getApplication())
            val decided = com.emberinn.engine.prompt.PresetApplyEngine.autoSelectPresetDecision(
                autoPresetName, samplerNames, presetPrefs.samplerPreset,
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
                val greetingEnv = ChatPromptFactory().displayMacroEnv(currentUserName, charName, currentCharacter?.rawJson)
                val greetings = (listOfNotNull(firstMes) + alternates)
                    .map {
                        if (regexOn) RegexPipelineEngine.apply(
                            it,
                            ChatPromptFactory.REGEX_AI_OUTPUT,
                            scripts,
                            substitute = { MacroEngine.substitute(it, greetingEnv) },
                        ) else it
                    }
                    .toMutableList()
                // 官方 getFirstMessage：first_mes 正则后为空 → swipes.shift()，改用第一条 alternate
                if (greetings.firstOrNull()?.isBlank() == true && greetings.size > 1) {
                    greetings.removeAt(0)
                }
                val content = greetings.firstOrNull().orEmpty()
                chatStore.append(
                    sessionId,
                    isUser = false,
                    content = content,
                    name = charName,
                    greetingSwipes = greetings,
                    isGreeting = true,
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
                refreshSpeakerNames()
                singleAutoContinueRuns = 0
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
        // 官方 ST processCommands 优先于附件处理：斜杠命令即便带附件也执行（命令消费输入，不落消息）
        if (text.trimStart().startsWith("/")) {
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
        refreshSpeakerNames()
        singleAutoContinueRuns = 0
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
            RegexPipelineEngine.apply(
                effectiveText,
                ChatPromptFactory.REGEX_USER_INPUT,
                saveRegexScripts,
                substitute = { MacroEngine.substitute(it, ChatPromptFactory().displayMacroEnv(userName, charName, character?.rawJson)) },
            )
        } else {
            effectiveText
        }
        // 官方 sendMessageAsUser：regex USER_INPUT → substituteParams →（有 bias 时）removeMacros
        val substitutedText = MacroEngine.substitute(regexedText, MacroEnv(user = userName, char = charName))
        val storedText = if (messageBias.isNotBlank()) BiasEngine.removeMacros(substitutedText) else substitutedText
        chatStore.append(sessionId, true, storedText, userName, media, mediaDisplay = mediaDisplay, mediaIndex = mediaIndex, bias = messageBias)
        // 官方 sendMessageAsUser：message_token_count_enabled 时用户消息也写 extra.token_count
        // （官方在 removeMacros 之前按 substituteParams 后的文本计数）
        if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
            val model = chatRepository.profile()?.model.orEmpty()
            val count = runCatching {
                com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(model).count(substitutedText)
            }.getOrDefault(substitutedText.length / 4)
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
        flushStreamingBuffers()
        if (!_isStreaming.value) return
        // 官方 stopGeneration：整批群聊终止——handleStreamDone 不再推进下一位发言人
        userStopped = true
        streamSession?.cancel()
        streamSession = null
        streamActive = false
        pendingToolCalls = null
        pendingGroupGenId = null
        singleAutoContinueRuns = 0
        val partial = _streamingText.value
        val rawReasoning = _streamingReasoning.value
        val wasImpersonating = _isImpersonating.value
        val wasContinue = streamContinueMode
        val wasSwipe = generatingSwipe
        _isStreaming.value = false
        _isImpersonating.value = false
        if (!wasImpersonating && rawReasoning.isNotBlank()) {
            _lastReasoning.value = rawReasoning
        }
        // 官方 abort：textarea/消息保留的是最后一个 tick 已清洗的文本（onProgressStreaming 每 tick
        // 全量 cleanUpMessage），停止路径同样过 clean 管线，绝不落裸流
        val cleaned = if (partial.isBlank()) "" else cleanStreamReply(partial, wasImpersonating, wasContinue)
        if (cleaned.isNotBlank() || wasImpersonating) {
            when {
                wasImpersonating -> _impersonated.value = cleanImpersonationText(partial, isFinal = true)
                wasSwipe -> appendGeneratedSwipe(cleaned, reasoningOverride = rawReasoning.takeIf { it.isNotBlank() })
                wasContinue -> {
                    // 对齐官方 saveReply(type='continue')：lastMessage.mes += getMessage，紧贴追加不插换行
                    // 官方追加目标是 chat 的最后一条（continue 允许最后一条是用户消息）
                    val aiIdx = chatStore.messages(sessionId).lastIndex
                    if (aiIdx >= 0) {
                        val profile = chatRepository.profile()
                        chatStore.appendToCurrentSwipe(
                            sessionId, aiIdx, cleaned,
                            api = profile?.providerId,
                            model = profile?.model,
                            reasoning = rawReasoning.takeIf { it.isNotBlank() },
                        )
                        refreshMessages()
                    }
                }
                else -> {
                    appendAiReply(cleaned, reasoningOverride = rawReasoning.takeIf { it.isNotBlank() })
                    refreshMessages()
                }
            }
        }
        _streamingText.value = ""
        _streamingReasoning.value = ""
        _impersonationDraft.value = ""
        streamContinueMode = false
        generatingSwipe = false
    }

    /** ViewModel 销毁兜底：取消在途请求与 flusher，避免 OkHttp 连接/协程泄漏（生成中退出聊天页）。 */
    override fun onCleared() {
        streamSession?.cancel()
        streamSession = null
        streamActive = false
        streamingFlusher?.cancel()
        super.onCleared()
    }

    /** 重新生成（官方 option_regenerate / script.js:4340-4353）：最后一条是用户消息时"do nothing"
     *  ——不删任何消息，直接对其重新生成 AI 回复；最后一条非用户（AI/系统）才删掉再生成。 */
    fun regenerate() {
        if (_isStreaming.value) return
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return
        // 先检查配置再删回复：未配置时绝不丢最后一条 AI 回复
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再重新生成。）"
            return
        }
        refreshSpeakerNames()
        singleAutoContinueRuns = 0
        _impersonated.value = null
        // 官方 script.js:4346：lastMessage.is_user → 什么都不删，照常生成（对用户最后一句重新要一条 AI 回复）
        if (!isUser(last)) {
            chatStore.removeAt(sessionId, msgs.lastIndex)
            refreshMessages()
        }
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
    fun continueGeneration(fromAutoContinue: Boolean = false) {
        if (_isStreaming.value) return
        val msgs = chatStore.messages(sessionId)
        val last = msgs.lastOrNull() ?: return
        // 官方 option_continue / script.js:5026：最后一条是用户消息时不警告不拒绝——照样续写，
        // 仅 modifyLastPromptLine 省略角色名追加（引擎 nudge 泛化，cyclePrompt 取实际最后一条文本）
        val lastText = textOf(last)
        _notice.value = null
        if (!isProviderConfigured()) {
            refreshProviderConfigured()
            _notice.value = "（未配置模型，请先选一个模型再发送。）"
            return
        }
        // 自动续写链（triggerAutoContinue）继承计数；用户手动续写重置新一轮计数
        if (!fromAutoContinue) {
            singleAutoContinueRuns = 0
            _impersonated.value = null
        }
        refreshSpeakerNames()
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
        refreshSpeakerNames()
        singleAutoContinueRuns = 0
        _impersonated.value = null
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
        // 官方 deleteItemizedPromptForMessage：删除明细并把后续消息索引下移。
        ItemizationStore.deleteMessage(getApplication<Application>().filesDir, sessionId, index)
    }

    /** 官方 mes_edit_up/down（messageEditMove）：与相邻消息交换位置；首条不能上移、末条不能下移、生成中拒绝。 */
    fun moveMessage(index: Int, delta: Int) {
        if (_isStreaming.value) return
        val target = index + delta
        val size = chatStore.messages(sessionId).size
        if (index !in 0 until size || target !in 0 until size) return
        chatStore.swapMessages(sessionId, index, target)
        refreshMessages()
    }

    /** 官方 mes_edit_copy：深拷贝本条消息（send_date 重置）插到其后。 */
    fun duplicateMessage(index: Int) {
        if (_isStreaming.value) return
        chatStore.duplicateMessage(sessionId, index)
        refreshMessages()
    }

    /** 官方 RossAscends-mods.js humanizedDateTime：`yyyy-MM-dd@HHh mms sms msms`。 */
    private fun humanizedDateTime(now: Long = System.currentTimeMillis()): String {
        val t = java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault())
        return "%04d-%02d-%02d@%02dh%02dm%02ds%03dms".format(
            t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second, t.nano / 1_000_000,
        )
    }

    /** 官方 doNewChat（script.js:10558-10586）：新聊天文件命名 `{角色名} - {humanizedDateTime()}`，
     * 群聊走 createNewGroupChat（同样时间命名）；旧聊天保留。 */
    fun startNewChat(): com.emberinn.app.data.SessionRecord? {
        val src = chatStore.get(sessionId) ?: return null
        val displayName = (group?.name ?: character?.name ?: src.name).ifBlank { src.name }
        val record = com.emberinn.app.data.SessionRecord(
            id = java.util.UUID.randomUUID().toString(),
            characterId = src.characterId,
            name = "$displayName - ${humanizedDateTime()}",
            groupId = src.groupId,
        )
        chatStore.upsert(record)
        return record
    }

    /** 官方 option_back_to_main：当前会话是分支（metadata.main_chat）时找到父会话。 */
    fun parentSession(): com.emberinn.app.data.SessionRecord? {
        val src = chatStore.get(sessionId) ?: return null
        val mainChat = chatStore.metadata(sessionId)["main_chat"]?.jsonPrimitive?.contentOrNull
            ?: return null
        // 新分支存父会话 ID（重命名/同名不断链）；UUID 格式直接命中
        chatStore.list().firstOrNull { it.id != src.id && it.id == mainChat }?.let { return it }
        // 旧数据兼容：早期分支存的是父会话名称
        return chatStore.list().firstOrNull { it.id != src.id && it.name == mainChat && it.characterId == src.characterId }
    }

    /** 官方 past chats 条目（displayChats：文件名 / 消息数 / 预览 / 末条时间）。 */
    data class PastChatEntry(
        val record: com.emberinn.app.data.SessionRecord,
        val messageCount: Int,
        val preview: String?,
        val lastDate: Long,
        val isCurrent: Boolean,
    )

    /** 官方 displayPastChats + displayChats：同角色/群的聊天文件列表，按末条时间倒序，支持搜索过滤。 */
    fun pastChats(query: String = ""): List<PastChatEntry> {
        val src = chatStore.get(sessionId) ?: return emptyList()
        val q = query.trim()
        return chatStore.list()
            .filter { it.characterId == src.characterId && it.groupId == src.groupId }
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .map { r ->
                PastChatEntry(
                    record = r,
                    messageCount = chatStore.messages(r.id).size,
                    preview = chatStore.lastMessage(r.id)?.take(80),
                    lastDate = chatStore.lastMessageDate(r.id),
                    isCurrent = r.id == sessionId,
                )
            }
            .sortedByDescending { it.lastDate }
    }

    /** past chats 列表修订号：重命名/删除后 +1，驱动弹层列表即时刷新（数据流不再依赖当前会话 messages）。 */
    private val _pastChatsRevision = MutableStateFlow(0)
    val pastChatsRevision: StateFlow<Int> = _pastChatsRevision

    /** 官方 renameChatFile。 */
    fun renamePastChat(id: String, newName: String) {
        val safe = newName.trim()
        if (safe.isNotBlank()) {
            chatStore.renameSession(id, safe)
            _pastChatsRevision.value++
        }
    }

    /** 当前会话 id（Past Chats 删除当前聊天时判断跳转）。 */
    val currentSessionId: String get() = sessionId

    /** 官方 delChat（PastChat_cross）：删除聊天文件；返回删除后剩余的 past chats。 */
    fun deletePastChat(id: String) {
        chatStore.delete(id)
        _pastChatsRevision.value++
    }

    /** 官方 "Download chat as plain text document"。 */
    fun exportChatPlainText(id: String): String? = chatStore.exportPlainText(id)

    /** 官方 dialogue_del_mes_ok：从勾选消息起（含）全部删除 + 明细降序清理 + 记忆触发。 */
    fun truncateFrom(index: Int) {
        if (_isStreaming.value) return
        val removed = chatStore.truncateFrom(sessionId, index)
        if (removed.isEmpty()) return
        // 官方：for (let i = chat.length-1; i >= this_del_mes; i--) deleteItemizedPromptForMessage(i)
        for (i in (index + removed.size - 1) downTo index) {
            ItemizationStore.deleteMessage(getApplication<Application>().filesDir, sessionId, i)
        }
        refreshMessages()
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
        val env = MacroEnv(user = currentUserName, char = currentCharName)
        val regexed = if (GlobalRegexPrefs.enabled(getApplication())) {
            RegexPipelineEngine.apply(
                raw = text,
                placement = placement,
                scripts = scripts,
                isEdit = true,
                characterOverride = if (isNarrator) null else obj["name"]?.jsonPrimitive?.contentOrNull ?: currentCharName,
                substitute = { MacroEngine.substitute(it, env) },
            )
        } else {
            text
        }
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
        refreshSpeakerNames()
        singleAutoContinueRuns = 0
        _impersonated.value = null
        startStream(
            history = chatStore.messages(sessionId),
            type = "impersonate",
            impersonation = true,
            impersonationPrompt = impersonationPromptText(),
        )
    }

    /** 官方 oai_settings.impersonation_prompt（默认 default_impersonation_prompt）。 */
    private fun impersonationPromptText(): String =
        chatRepository.profile()?.sampler?.impersonationPrompt?.takeIf { it.isNotBlank() }
            ?: ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT

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

    /** 官方 mes_embed：给已有消息嵌入本地附件（落盘 → extra.media 追加 → 刷新）。 */
    fun addMediaToMessage(index: Int, uri: Uri, mime: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val resolver = app.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val type = mime?.ifBlank { null } ?: resolver.getType(uri) ?: "application/octet-stream"
                val mediaType = com.emberinn.engine.media.MediaEngine.typeFromMime(type) ?: return@launch
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: "attachment"
                val safeMime = type == "image/jpeg" || type == "image/png" || type == "image/webp"
                val processed = if (mediaType == "image" && !safeMime) compressToJpeg(bytes) else bytes
                val extension = if (processed !== bytes) "jpg" else extensionFor(type, displayName)
                val dir = java.io.File(app.filesDir, "media").apply { mkdirs() }
                val file = java.io.File(dir, "${System.currentTimeMillis()}_${displayName.hashCode().toUInt().toString(16)}.$extension")
                file.writeBytes(processed)
                chatStore.addMediaToMessage(sessionId, index, mediaType, file.absolutePath, displayName)
                refreshMessages()
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
                val base64 = dataUrl.substringAfter("base64,")
                val rawPrompt = promptOverride?.takeIf { it.isNotBlank() } ?: s.prompt
                val prompt = MacroEngine.substitute(rawPrompt, MacroEnv(user = currentUserName, char = currentCharName))
                val caption = chatRepository.captionImageBySource(s.source, dataUrl, base64, prompt) ?: error("描述生成失败")
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

    /**
     * 官方 caption captionExistingMessage：给已有消息第 [mediaIndex] 张图片补字幕（mes 空→写 mes；
     * 否则 media.title=字幕、append_title）。source 路由见 [com.emberinn.app.data.ChatRepository.captionImageBySource]。
     */
    fun captionExistingMessage(messageId: Int, mediaIndex: Int = 0) {
        if (_isStreaming.value) {
            _notice.value = "（正在生成中，请稍后再试。）"
            return
        }
        val msgs = chatStore.messages(sessionId)
        val el = msgs.getOrNull(messageId)?.jsonObject ?: run {
            _notice.value = "（找不到该消息。）"
            return
        }
        val extra = el["extra"] as? JsonObject
        val media = extra?.get("media") as? JsonArray
        if (media.isNullOrEmpty()) {
            _notice.value = "（该消息没有可补字幕的图片。）"
            return
        }
        val mi = mediaIndex.coerceIn(0, media.lastIndex)
        val entry = media.getOrNull(mi)?.jsonObject
        val url = entry?.get("url")?.jsonPrimitive?.contentOrNull
        val type = entry?.get("type")?.jsonPrimitive?.contentOrNull
        if (url.isNullOrBlank() || type == "audio") {
            _notice.value = "（该消息没有可补字幕的图片。）"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = java.io.File(url)
                if (!file.exists()) error("图片文件不存在")
                val s = CaptionPrefs.load(getApplication())
                val mime = mimeForCaption(url)
                val dataUrl = "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(file.readBytes())
                val base64 = dataUrl.substringAfter("base64,")
                val prompt = MacroEngine.substitute(s.prompt, MacroEnv(user = currentUserName, char = currentCharName))
                val caption = chatRepository.captionImageBySource(s.source, dataUrl, base64, prompt) ?: error("描述生成失败")
                if (caption.isBlank()) error("描述生成失败")
                val rawTemplate = if (s.template.contains("{{caption}}", ignoreCase = true)) {
                    s.template
                } else {
                    s.template + " {{caption}}"
                }
                val substituted = MacroEngine.substitute(rawTemplate, MacroEnv(user = currentUserName, char = currentCharName))
                val wrapped = substituted.replace("{{caption}}", caption.trim())
                val mes = el["mes"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val mesIfBlank = if (mes.isEmpty()) wrapped else null
                withContext(Dispatchers.Main) {
                    chatStore.captionExistingMedia(sessionId, messageId, mi, wrapped, mesIfBlank)
                    refreshMessages()
                }
            }.onFailure { e ->
                _notice.value = "（图片描述失败：${e.message ?: "未知错误"}）"
            }
        }
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
    fun exportJsonl(id: String = sessionId): String? = chatStore.exportJsonl(id)

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
                // 双保险：上一位的收尾已启动新流（自动续写/下一位）时绝不再推进——
                // 重复推进会产生两条并行流各自落盘，消息成倍重复
                if (_isStreaming.value) return@startStream
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
        onPreview: ((com.emberinn.app.data.PromptPreview) -> Unit)? = null,
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
        synchronized(streamingTextBuffer) { streamingTextBuffer.clear() }
        synchronized(streamingReasoningBuffer) { streamingReasoningBuffer.clear() }
        _lastReasoning.value = null
        _worldHits.value = emptyList()
        _contextUsage.value = null
        _logprobs.value = null
        _isStreaming.value = true
        _generationEpoch.value++
        _isImpersonating.value = impersonation
        // 官方 onStartStreaming(L3570)：冒充开始即清空输入框——旧草稿不保留，空结果时输入框也是空的
        if (impersonation) _impersonationDraft.value = ""
        streamContinueMode = continueMode
        generatingSwipe = swipeMode
        pendingGroupGenId = groupGenId
        streamActive = true
        userStopped = false
        streamDoneHandled = false
        firstDeltaAt = null
        // 注意：singleAutoContinueRuns 不在此重置——自动续写链经 continueGeneration(fromAutoContinue=true)
        // 继承计数，5 次上限才真正生效；重置只发生在用户手动入口（send/regenerate/impersonate/generateSwipe）
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
            // 官方 power_user.reasoning：add_to_prompts / max_additions 从推理预设（Advanced Formatting）读取
            val reasoningState = com.emberinn.app.ui.settings.PresetSettingsStore.load(getApplication()).reasoning
            val reasoningToPrompts = reasoningState.addToPrompts
            val reasoningMaxAdditions = reasoningState.maxAdditions
            // 官方 PromptManager 只服务 chat completion（main_api==='openai'）；textgen/novel/kobold 不注入 PM 提示
            val textCompletionProtocol = chatRepository.profile()?.let {
                com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
            } in setOf("textgenerationwebui", "novel", "kobold")
            // 官方 /inject：chat_metadata.script_injects → 本轮扩展提示 + scan 扫描文本
            val scriptInjections = (chatStore.metadata(sessionId)["script_injects"] as? JsonObject)
                ?.mapNotNull { (id, el) ->
                    val o = el.jsonObject
                    val value = o["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val filterRaw = o["filter"]?.jsonPrimitive?.contentOrNull
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
                        filter = filterRaw,
                    )
                } ?: emptyList()
            // 官方 /inject filter：闭包在生成时判定（closureToFilter：执行→isTrueBoolean(pipe)），
            // false 跳过注入与 scan；求值异常按官方 reviveFilterClosure 失败语义=始终注入。
            val scriptFilterEvaluator: (String) -> Boolean = { raw ->
                runCatching {
                    val evalState = SlashState()
                    // 官方闭包在当前作用域执行：聊天变量对 /getvar 可见
                    chatRepository.localVariableStore().names().forEach { n ->
                        chatRepository.localVariableStore().get(n)?.let { evalState.variables[n] = it }
                    }
                    ExtensionPromptEngine.isTrueBoolean(slashExecutor.execute(raw, evalState).trim())
                }.getOrDefault(true)
            }
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
            val itemizationCallback: ((ItemizationEntry) -> Unit)? =
                if (previewOnly) null else { entry -> pendingItemization = entry }
            val session = chatRepository.streamPrepared(
                characterRawJson = characterRawJsonOverride ?: character?.rawJson,
                history = history,
                userName = currentUserName,
                charName = currentCharName,
                previewOnly = previewOnly,
                onPreview = onPreview,
                onItemization = itemizationCallback,
                onDelta = { delta ->
                    if (streamActive) {
                        if (firstDeltaAt == null) firstDeltaAt = System.currentTimeMillis()
                        appendStreamingText(delta)
                    }
                },
                onReasoning = { text ->
                    // 官方 oai_settings.show_thoughts：false 时不请求/不展示推理（include_reasoning=show_thoughts）
                    if (streamActive && _showThoughts.value) {
                        appendStreamingReasoning(text)
                    }
                },
                onToolCalls = { calls ->
                    if (streamActive) pendingToolCalls = calls
                },
                onLogprobs = { chunk ->
                    if (streamActive) _logprobs.value = (_logprobs.value.orEmpty() + chunk)
                },
                stopGroupMemberNames = groupMembers.map { it.name },
                onDone = { handleStreamDone(streamContinueMode, onFinished) },
                onError = { e ->
                    flushStreamingBuffers()
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
                                // 官方 showSendMessageError：toastr 直接展示后端错误原文（HTTP 状态/body），
                                // 不再只给泛化文案——用户无法区分欠费/限流/鉴权问题
                                val msg = e.message?.trim().orEmpty()
                                if (msg.isNotBlank()) "（生成失败：${msg.take(160)}）"
                                else "（请求中断，请检查网络或 API Key 后重试。）"
                            }
                        } else {
                            finalizeStream(streamContinueMode)
                        }
                        // 官方 onErrorStreaming：上层 promise reject，群聊整批终止——不推进下一位发言人
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
                cfgCharacterId = character?.id,
                cfgSelectedGroup = group != null,
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
                // 官方 global 策略：始终读 character_id=100000 的全局顺序；text completion 不注入 PM
                userPrompts = if (textCompletionProtocol) emptyList() else PromptManagerPrefs.prompts(getApplication()),
                userOrder = if (textCompletionProtocol) emptyList() else PromptManagerPrefs.order(getApplication()),
                worldInfoSettings = worldInfoSettings,
                globalRegexScripts = globalRegexScripts,
                regexScopedAllowed = regexScopedAllowed,
                regexPresetScripts = presetScripts,
                regexPresetAllowed = presetAllowed,
                isContinue = continueMode,
                regexEnabled = regexEnabled,
                reasoningToPrompts = reasoningToPrompts,
                reasoningMaxAdditions = reasoningMaxAdditions,
                reasoningTemplate = reasoningState.template,
                scriptInjections = scriptInjections,
                scriptFilterEvaluator = scriptFilterEvaluator,
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
                        // 官方 WORLD_INFO_ACTIVATED → 自动执行 automationId 匹配的快捷回复（dryRun 不执行）
                        if (!previewOnly) runAutoExecutions(info.activatedWorldInfo, type)
                    }
                },
            )
            if (previewOnly) {
                // dryRun 不进入生成态：任何路径（含无 profile 提前返回）都要复位，否则卡“生成中”
                streamActive = false
                _isStreaming.value = false
                _isImpersonating.value = false
                _streamingReasoning.value = ""
                streamContinueMode = false
                generatingSwipe = false
                pendingGroupGenId = null
                return@launch
            }
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
        // 幂等闩锁：同一轮流式的 onDone 只处理一次（双 [DONE]/结束事件拆块会重复触发）
        if (streamDoneHandled) return
        streamDoneHandled = true
        flushStreamingBuffers()
        if (!streamActive) {
            // 用户主动停止（官方 stopGeneration：群聊整批终止，不推进下一位发言人）
            if (!userStopped) onFinished?.invoke()
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
            continueGeneration(fromAutoContinue = true)
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
        // 冒充/变体流不进工具循环（官方 impersonate/swipe 类型无工具递归）：
        // 冒充文本会被 appendAiReply 落盘成 AI 消息，随后 finalizeStream 又把同段文本
        // 填进输入框——屏幕与输入框双重显示；快照也须清掉，不残留到下一轮流误触发
        if (_isImpersonating.value || generatingSwipe) {
            pendingToolCalls = null
            return false
        }
        val snapshot = pendingToolCalls ?: return false
        pendingToolCalls = null
        // 无真实工具调用的快照（网络层空快照兜底/异常流）不进工具循环：
        // 官方无 tool_calls 时走普通 saveReply，这里提前返回让 finalizeStream 统一落盘一次
        val hasToolCalls = (snapshot as? kotlinx.serialization.json.JsonArray)
            ?.any { choice -> (choice as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true } == true
        if (!hasToolCalls) return false
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
            recurseLimit = chatRepository.profile()?.sampler?.toolCallRecurseLimit ?: ToolLoopPlanner.DEFAULT_RECURSE_LIMIT,
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
        // 本轮流文本在此已消费：立即清空，否则提前 return false 后 finalizeStream
        // 读到残留 _streamingText 会把同一段再落盘一次（单聊消息重复的直接根因）
        _streamingText.value = ""
        _streamingReasoning.value = ""
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

    /** 官方 saveReply 清洗管线：停用词/名字剥离/endoftext/trim/collapseNewlines + AI_OUTPUT 正则。
     *  正常完成与停止保留半截文本共用，停止路径不落裸流。 */
    private fun cleanStreamReply(
        raw: String,
        isImpersonate: Boolean,
        isContinue: Boolean,
        hasReasoningPrefix: Boolean = false,
    ): String {
        val behavior = BehaviorPrefs.load(getApplication())
        val profileForClean = chatRepository.profile()
        val apiForClean = profileForClean?.let {
            com.emberinn.engine.provider.ProviderRegistry.get(it.providerId)?.protocol
        } ?: "openai"
        val stoppingStrings = StoppingStringsEngine.getStoppingStrings(
            api = apiForClean,
            config = StoppingStringsConfig(
                isImpersonate = isImpersonate,
                isContinue = isContinue,
                name1 = currentUserName,
                name2 = currentCharName,
                chatLastIsUser = chatStore.messages(sessionId).lastOrNull()?.let { isUser(it) } == true,
                groupMemberNames = groupMembers.map { it.name },
                selectedGroup = group != null,
                namesAsStopStrings = behavior.namesAsStopStrings,
                env = MacroEnv(user = currentUserName, char = currentCharName),
            ),
        )
        return CleanUpMessageEngine.clean(
            getMessage = raw,
            config = CleanUpConfig(
                isImpersonate = isImpersonate,
                isContinue = isContinue,
                stoppingStrings = stoppingStrings,
                name1 = currentUserName,
                name2 = currentCharName,
                hasReasoningPrefix = hasReasoningPrefix,
                groupMemberNames = groupMembers.map { it.name },
                groupTrimmingEnabled = group != null,
                collapseNewlines = RenderPrefs.collapseNewlines(getApplication()),
                trimSpaces = behavior.trimSpaces,
                trimSentences = behavior.trimSentences,
                userPromptBias = behavior.userPromptBias.takeIf { it.isNotBlank() },
                includeUserPromptBias = behavior.showUserPromptBias,
            ),
            regexTransform = { text ->
                if (GlobalRegexPrefs.enabled(getApplication())) {
                    RegexPipelineEngine.apply(
                        text,
                        ChatPromptFactory.REGEX_AI_OUTPUT,
                        saveRegexScripts,
                        substitute = { MacroEngine.substitute(it, ChatPromptFactory().displayMacroEnv(currentUserName, currentCharName, character?.rawJson)) },
                    )
                } else {
                    text
                }
            },
        )
    }

    private fun finalizeStream(continueMode: Boolean = false) {
        flushStreamingBuffers()
        _isStreaming.value = false
        streamSession = null
        // 官方 saveReply：getRegexedString(getMessage, isImpersonate ? USER_INPUT : AI_OUTPUT)，
        // 冒充不落盘（进输入框，发送时再过 USER_INPUT）；continue/swipe/普通回复都先过 AI_OUTPUT
        val wasImpersonating = _isImpersonating.value
        val wasSwipe = generatingSwipe
        // 先捕获再复位：auto-swipe 触发的下一轮流（swipeRight→startStream）会重新置位
        // generatingSwipe/_streamingText 等状态，收尾尾巴绝不能覆盖新流状态
        val rawText = _streamingText.value
        val rawReasoning = _streamingReasoning.value
        _streamingText.value = ""
        _streamingReasoning.value = ""
        streamContinueMode = false
        generatingSwipe = false
        val behavior = BehaviorPrefs.load(getApplication())
        // 官方 saveReply：cleanUpMessage（停用词/名字/endoftext/Instruct/群消息/trim 全链）；
        // AI_OUTPUT 正则按官方位置在停用词裁剪之后注入（引擎 regexTransform）
        val reply = cleanStreamReply(rawText, wasImpersonating, continueMode, hasReasoningPrefix = rawReasoning.isNotBlank())
        when {
            wasImpersonating -> {
                // 官方：冒充结果进输入框，不写历史
                if (reply.isNotBlank()) {
                    _impersonated.value = reply
                } else {
                    _lastReasoning.value = rawReasoning.takeIf { it.isNotBlank() }
                    _notice.value = if (rawReasoning.isNotBlank()) {
                        "（模型只返回了思考，没有生成冒充内容。）"
                    } else {
                        "（冒充没有生成内容，请重试。）"
                    }
                }
                _isImpersonating.value = false
            }
            wasSwipe -> {
                // 对齐官方 swipe 生成：结果追加进最后一条 AI 的 swipes，不新增消息
                if (rawReasoning.isNotBlank()) _lastReasoning.value = rawReasoning
                if (reply.isNotBlank()) {
                    appendGeneratedSwipe(reply, reasoningOverride = rawReasoning.takeIf { it.isNotBlank() })
                } else if (rawReasoning.isBlank()) {
                    _notice.value = "（滑动生成没有新内容，已保留当前回复。）"
                }
            }
            continueMode && reply.isNotBlank() -> {
                // 对齐官方 saveReply(type='continue')：lastMessage.mes += getMessage，紧贴追加不插换行
                // 官方追加目标是 chat 最后一条（continue 允许最后一条是用户消息，脚本无 is_user 拦截）
                if (rawReasoning.isNotBlank()) _lastReasoning.value = rawReasoning
                val after = chatStore.messages(sessionId).toMutableList()
                val aiIdx = after.lastIndex
                if (aiIdx >= 0) {
                    val profile = chatRepository.profile()
                    // 官方：generation_started 时长守恒（now - (prevFinished - prevStarted)）
                    val aiEl = after[aiIdx].jsonObject
                    val prevStarted = aiEl["gen_started"]?.jsonPrimitive?.contentOrNull
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                    val prevFinished = aiEl["gen_finished"]?.jsonPrimitive?.contentOrNull
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                    val nowMs = System.currentTimeMillis()
                    val adjustedStart = if (prevStarted != null && prevFinished != null) {
                        java.time.Instant.ofEpochMilli(nowMs - (prevFinished - prevStarted)).toString()
                    } else {
                        java.time.Instant.ofEpochMilli(nowMs).toString()
                    }
                    chatStore.appendToCurrentSwipe(
                        sessionId, aiIdx, reply,
                        api = profile?.providerId,
                        model = profile?.model,
                        reasoning = rawReasoning.takeIf { it.isNotBlank() },
                        genStarted = adjustedStart,
                    )
                    // 官方 saveReply('continue')：message_token_count_enabled 时刷新合并后 token_count
                    if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
                        val msgs = chatStore.messages(sessionId)
                        val idx = msgs.lastIndex
                        if (idx >= 0) {
                            val text = msgs[idx].jsonObject["mes"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val count = runCatching {
                                com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(profile?.model.orEmpty())
                                    .count(rawReasoning + text)
                            }.getOrDefault(text.length / 4)
                            chatStore.setExtraValue(sessionId, idx, "token_count", count.toString())
                        }
                    }
                    refreshMessages()
                }
            }
            rawReasoning.isNotBlank() -> {
                // 思考过程保留 + 正常追加回复；空正文不再静默吞掉，给用户明确反馈
                _lastReasoning.value = rawReasoning
                if (reply.isNotBlank()) {
                    appendAiReply(reply, reasoningOverride = rawReasoning)
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
        // 官方 translate 扩展：auto_mode=responses/both 时 AI 回复自动翻译（译文进 extra.display_text）
        if (!wasImpersonating) {
            val lastAi = chatStore.messages(sessionId).indexOfLast { !isUser(it) }
            if (lastAi >= 0) translateIncoming(lastAi, rawReasoning.takeIf { it.isNotBlank() })
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
    private fun appendGeneratedSwipe(reply: String, reasoningOverride: String? = null) {
        val msgs = chatStore.messages(sessionId)
        val aiIdx = msgs.indexOfLast { !isUser(it) }
        val reasoning = reasoningOverride ?: _streamingReasoning.value.takeIf { it.isNotBlank() }
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
                reasoning = reasoning,
                groupGenId = if (group != null) pendingGroupGenId else null,
            )
            // 官方 swipe saveReply：message_token_count_enabled 时刷新新变体 token_count
            if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
                val count = runCatching {
                    com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(profile?.model.orEmpty())
                        .count((reasoning ?: "") + reply)
                }.getOrDefault(reply.length / 4)
                chatStore.setExtraValue(sessionId, aiIdx, "token_count", count.toString())
            }
            refreshMessagesAppendOnly()
        }
    }

    /**
     * 官方 expressions getExpressionLabel 的 LLM 分支：
     * sampleClassifyText(useLlm) → raw: generateRaw / full: generateQuietPrompt → parseLlmResponse。
     * 结果不落盘；失败/非 LLM API 回调 null（调用方走 fallback_expression）。
     */
    fun classifyExpression(text: String, onResult: (String?) -> Unit) {
        val prefs = com.emberinn.app.ui.settings.ExpressionPrefs.load(getApplication())
        // 官方 expressions.api=webllm：用本地 transformers.js 分类。Android 无 WebLLM，回退 LLM 分类 + 日志。
        val webllmFallback = com.emberinn.app.data.ExpressionStore(getApplication()).shouldFallbackToLlm()
        if (webllmFallback) {
            android.util.Log.w("ExpressionStore", "WebLLM 本地分类在 Android 不可用，回退 LLM 分类")
        }
        val useLlm = prefs.api == com.emberinn.engine.expression.ExpressionApi.LLM || webllmFallback
        if (!useLlm || text.isBlank()) {
            onResult(null)
            return
        }
        val labels = com.emberinn.engine.expression.ExpressionEngine.DEFAULT_EXPRESSIONS
        val prompt = com.emberinn.engine.expression.ExpressionEngine.llmPrompt(labels, prefs.llmPrompt)
        val sampled = com.emberinn.engine.expression.ExpressionEngine.sampleClassifyText(text, useLlm = true)
        if (sampled.isNullOrBlank()) {
            onResult(null)
            return
        }
        if (prefs.promptType == com.emberinn.engine.expression.ExpressionPromptType.RAW) {
            chatRepository.summarizeRaw(
                systemPrompt = prompt,
                userPrompt = sampled,
                responseLength = 50,
                onResult = { resp -> onResult(com.emberinn.engine.expression.ExpressionEngine.parseLlmResponse(resp.trim(), labels)) },
                onError = { onResult(null) },
            )
        } else {
            chatRepository.generateQuietSummary(
                history = chatStore.messages(sessionId),
                quietPrompt = prompt,
                responseLength = 50,
                onResult = { resp -> onResult(com.emberinn.engine.expression.ExpressionEngine.parseLlmResponse(resp.trim(), labels)) },
                onError = { onResult(null) },
            )
        }
    }

    /** AI 回复落盘：带官方字段（api/model/gen_started/gen_finished/reasoning/time_to_first_token）。 */
    private fun appendAiReply(reply: String, reasoningOverride: String? = null) {
        val profile = chatRepository.profile()
        val reasoning = reasoningOverride ?: _streamingReasoning.value.takeIf { it.isNotBlank() }
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
            reasoning = reasoning,
            // 官方群聊 AI 消息带 gen_id（group_generation_id，整批共享）；单聊不带
            groupGenId = pendingGroupGenId,
        )
        // 官方 expressions：分类在渲染期异步完成（classifyExpression），不持久化 extra.sprite
        // 官方 power_user.message_token_count_enabled：extra.token_count 落盘
        if (BehaviorPrefs.load(getApplication()).messageTokenCount) {
            val model = profile?.model.orEmpty()
            val count = runCatching {
                com.emberinn.engine.worldinfo.TokenCounterFactory.forModel(model)
                    .count((reasoning ?: "") + reply)
            }.getOrDefault(reply.length / 4)
            val idx = chatStore.messages(sessionId).lastIndex
            if (idx >= 0) chatStore.setExtraValue(sessionId, idx, "token_count", count.toString())
        }
        // 官方 itemized-prompts.js：生成落盘后保存该消息的总装明细。
        pendingItemization?.let { entry ->
            val idx = chatStore.messages(sessionId).lastIndex
            if (idx >= 0) {
                ItemizationStore.put(getApplication<Application>().filesDir, sessionId, entry.copy(messageIndex = idx))
            }
            pendingItemization = null
        }
    }

    /** 官方 findItemizedPromptSet：按消息索引取该条的总装明细。 */
    fun itemizationFor(index: Int): ItemizationEntry? =
        ItemizationStore.load(getApplication<Application>().filesDir, sessionId).firstOrNull { it.messageIndex == index }

    /** 全部明细（供“与上一条对比”）。 */
    fun itemizations(): List<ItemizationEntry> =
        ItemizationStore.load(getApplication<Application>().filesDir, sessionId)

    private fun refreshMessages() {
        _messages.value = chatStore.messages(sessionId)
        displayCache.clear()
        _displayRevision.value++
    }

    /** 仅“末尾追加”后的轻量刷新：旧消息索引不变，显示缓存仍有效，不全表失效。 */
    private fun refreshMessagesAppendOnly() {
        _messages.value = chatStore.messages(sessionId)
        _displayRevision.value++
    }

    /** 显示设置（encode_tags/正则/行为）变更时由 ChatScreen 的版本监听调用：清缓存并让全表行重算。 */
    fun onDisplaySettingsChanged() {
        syncDisplayVersion()
    }

    private fun syncDisplayVersion() {
        if (displayCacheVersion != DisplayCacheVersion.version) {
            displayCache.clear()
            displayCacheVersion = DisplayCacheVersion.version
            _displayRevision.value++
        }
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

    /** 是否可继续生成（官方 option_continue：无 is_user 拦截，最后一条是用户消息时照样续写）。 */
    fun canContinueGeneration(): Boolean {
        return chatStore.messages(sessionId).isNotEmpty()
    }

    private fun textOf(el: JsonElement): String =
        el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""
}
