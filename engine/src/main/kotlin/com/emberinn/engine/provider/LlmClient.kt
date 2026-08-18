package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.LogprobsEngine
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 连接档案（提供商 + Key + 模型 + 采样参数 + 区域/账户扩展）。 */
@Serializable
data class ConnectionProfile(
    val id: String = "",
    val name: String = "",
    val providerId: String,
    val apiKey: String = "",
    val baseUrlOverride: String = "",
    val model: String = "",
    val sampler: SamplerParams = SamplerParams(),
    /** 官方 oai_settings.vertexai_region 默认 us-central1。 */
    val region: String = "us-central1",
    val accountId: String = "",
    val apiVersionOverride: String = "",
    /** 上下文上限（tokens），对齐官方 openai_max_context 默认 max_4k=4095；App 配置后作为占比胶囊分母。 */
    val contextWindow: Int = 4095,
    /** 官方 oai_settings.reverse_proxy：非空时替换厂商 API base URL。 */
    val reverseProxy: String = "",
    /** 官方 oai_settings.proxy_password：reverse_proxy 时的 API Key（server 端语义）。 */
    val proxyPassword: String = "",
    /** 官方 oai_settings.custom_url（custom 源 API 地址）。 */
    val customUrl: String = "",
    /** 官方 oai_settings.custom_include_body（YAML 字符串，合并进请求体）。 */
    val customIncludeBody: String = "",
    /** 官方 oai_settings.custom_exclude_body（YAML 字符串，从请求体剔除字段）。 */
    val customExcludeBody: String = "",
    /** 官方 oai_settings.custom_include_headers（YAML 字符串，合并进请求头）。 */
    val customIncludeHeaders: String = "",
    /** 官方 oai_settings.custom_prompt_post_processing（NONE/MERGE/DISABLED；App 未接消费点，登记）。 */
    val customPromptPostProcessing: String = "",
    /** 官方 oai_settings.bypass_status_check（测试连接跳过状态检查）。 */
    val bypassStatusCheck: Boolean = false,
    /** 官方 oai_settings.show_external_models（模型列表显示外部模型）。 */
    val showExternalModels: Boolean = false,
    /** 官方 oai_settings.group_models（按提供商分组模型）。 */
    val groupModels: Boolean = false,
    /** 官方 oai_settings.sort_models：alphabetically/reverse/group-alphabetically/... */
    val sortModels: String = "alphabetically",
    /** 官方 oai_settings.azure_deployment_name。 */
    val azureDeploymentName: String = "",
    /** 官方 oai_settings.azure_openai_model。 */
    val azureOpenaiModel: String = "",
    /** 官方 oai_settings.vertexai_auth_mode（默认 express）。 */
    val vertexaiAuthMode: String = "express",
    /** 官方 oai_settings.vertexai_express_project_id。 */
    val vertexaiExpressProjectId: String = "",
    /** 官方 Vertex AI Full 模式服务账号 JSON（secrets.vertexai_service_account_json 的 App 侧存储）。 */
    val vertexaiServiceAccountJson: String = "",
    /** 官方 oai_settings.nanogpt_provider。 */
    val nanogptProvider: String = "",
    /** 官方 oai_settings.nanogpt_payg_override。 */
    val nanogptPaygOverride: Boolean = false,
)

/** 提供商连接存储（JSON 文件，多档案：profiles.json，旧单档案 connection.json 自动迁移）。 */
class ProviderStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(dir, "profiles.json")
    private val legacyFile = File(dir, "connection.json")

    @Serializable
    data class ProfileList(
        val activeId: String = "",
        val profiles: List<ConnectionProfile> = emptyList(),
    )

    fun load(): ConnectionProfile? {
        val list = loadList()
        return list.profiles.firstOrNull { it.id == list.activeId }
            ?: list.profiles.firstOrNull()
    }

    fun profiles(): List<ConnectionProfile> = loadList().profiles

    fun save(profile: ConnectionProfile) = save(profile, active = true)

    fun save(profile: ConnectionProfile, active: Boolean) {
        dir.mkdirs()
        val list = loadList()
        val normalized = if (profile.id.isBlank()) {
            profile.copy(id = "p-" + System.nanoTime().toString(36))
        } else profile
        val others = list.profiles.filterNot { it.id == normalized.id }
        val updated = others + normalized
        val activeId = if (active) normalized.id else (list.activeId.ifBlank { updated.firstOrNull()?.id ?: "" })
        file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = activeId, profiles = updated)))
    }

    fun setActive(id: String) {
        val list = loadList()
        if (list.profiles.any { it.id == id }) {
            file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = id, profiles = list.profiles)))
        }
    }

    fun delete(id: String) {
        val list = loadList()
        val updated = list.profiles.filterNot { it.id == id }
        file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = if (list.activeId == id) updated.firstOrNull()?.id ?: "" else list.activeId, profiles = updated)))
    }

    private fun loadList(): ProfileList {
        if (!file.exists() && legacyFile.exists()) {
            runCatching {
                val legacy = json.decodeFromString<ConnectionProfile>(legacyFile.readText())
                val withId = if (legacy.id.isBlank()) legacy.copy(id = legacy.providerId) else legacy
                file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = withId.id, profiles = listOf(withId))))
            }
        }
        return if (file.exists()) runCatching {
            json.decodeFromString(ProfileList.serializer(), file.readText())
        }.getOrNull() ?: ProfileList() else ProfileList()
    }
}

/** LLM 客户端：OpenAI 兼容 / Anthropic / Gemini 三个协议，非流式 + SSE 流式。 */
class LlmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun chatML(messages: List<CompletionMessage>): List<JsonObject> = messages.map { it.toChatMLJson() }

    /** 非流式对话：返回纯文本回复（按协议解析官方响应体）。 */
    fun chatCompletions(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        options: ProviderRequestOptions = ProviderRequestOptions(),
    ): String {
        val request = buildRequest(provider, profile, messages, stream = false, options = options)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val text = response.body?.string() ?: return ""
            return parseChatResponse(provider.protocol, text)
        }
    }

    /** 拉取模型列表（对齐官方 status/models 逻辑；解析失败返回空，由 UI 兜底 default_models）。 */
    fun models(
        provider: ProviderSpec,
        profile: ConnectionProfile,
    ): List<ModelSortEngine.ModelMeta> {
        val url = modelsUrl(provider, profile) ?: return emptyList()
        val builder = Request.Builder().url(url).get()
        applyAuth(builder, provider, profile, anthropicVersion = true)
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val text = response.body?.string() ?: return emptyList()
            return parseModels(provider, text)
        }
    }

    /**
     * 官方 /api/backends/text-completions/props（koboldcpp/llamacpp）：
     * GET {base}/props → { chat_template, default_generation_settings.n_ctx, ... }。
     */
    fun modelProps(provider: ProviderSpec, profile: ConnectionProfile): JsonObject? {
        val base = profile.baseUrlOverride.ifBlank { provider.baseUrl }.trimEnd('/')
        if (base.isBlank()) return null
        val builder = Request.Builder().url("$base/props").get()
        applyAuth(builder, provider, profile, anthropicVersion = true)
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return runCatching {
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
            }
        }.getOrNull()
    }

    /** SSE 流式：按协议解析增量；流结束（含服务端直接关闭）保证回调 onDone。 */
    fun streamChatCompletions(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        options: ProviderRequestOptions = ProviderRequestOptions(),
        onToolCalls: ((JsonElement) -> Unit)? = null,
    ) {
        val request = buildRequest(provider, profile, messages, stream = true, options = options)
        http.newCall(request).execute().use { response ->
            executeStream(response, provider.protocol, onDelta, onDone, onToolCalls = onToolCalls)
        }
    }

    /** 可取消流式会话（App 停止按钮用）：后台线程执行，返回 cancel()。 */
    class StreamSession internal constructor(private val call: okhttp3.Call) {
        fun cancel() { call.cancel() }
    }

    fun streamChatCompletionsAsync(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        options: ProviderRequestOptions = ProviderRequestOptions(),
        onReasoning: ((String) -> Unit)? = null,
        onToolCalls: ((JsonElement) -> Unit)? = null,
        onLogprobs: ((List<LogprobsEngine.TokenLogprobs>) -> Unit)? = null,
    ): StreamSession {
        val request = buildRequest(provider, profile, messages, stream = true, options = options)
        val call = http.newCall(request)
        Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                    }
                    executeStream(response, provider.protocol, onDelta, onDone, onReasoning, onToolCalls, onLogprobs)
                }
            } catch (e: Exception) {
                if (!call.isCanceled()) onError?.invoke(e)
            }
        }.start()
        return StreamSession(call)
    }

    private fun executeStream(
        response: okhttp3.Response,
        protocol: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onReasoning: ((String) -> Unit)? = null,
        onToolCalls: ((JsonElement) -> Unit)? = null,
        onLogprobs: ((List<LogprobsEngine.TokenLogprobs>) -> Unit)? = null,
    ) {
        val source = response.body?.source() ?: return
        val sb = StringBuilder()
        var finished = false
        // onDone 只发一次：部分 OpenAI 兼容网关会发两个 [DONE]，textgen 会 stream_end+[DONE] 混发，
        // Anthropic 的 message_stop 也可能拆成多块——重复触发会让上层把群聊下一位成员推进两次（消息重复落盘）
        var doneEmitted = false
        fun emitDone() {
            if (!doneEmitted) {
                doneEmitted = true
                onDone()
            }
        }
        val toolAccumulator = ToolCallAccumulator()
        var lastToolSnapshot = ""
        while (true) {
            val line = source.readUtf8Line() ?: break
            sb.append(line).append('\n')
            if (line.isEmpty()) {
                val raw = sb.toString()
                sb.setLength(0)
                val dataText = extractSseData(raw)
                if (dataText == null) continue
                if (dataText == "[DONE]") {
                    finished = true
                    emitDone()
                    continue
                }
                // Anthropic 结束事件（event: message_stop 或 data type=message_stop）
                if (protocol == "anthropic" && raw.contains("message_stop")) {
                    finished = true
                    emitDone()
                    continue
                }
                // NovelAI / Kobold SSE：data: {"token":"...",...}，无结束事件，流关闭收尾
                // （官方 generateKoboldWithStreaming：JSON.parse(value.data)，data.token 追加）
                if (protocol == "novel" || protocol == "kobold") {
                    try {
                        val obj = json.parseToJsonElement(dataText).jsonObject
                        (obj["token"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let {
                            if (it.isNotEmpty()) onDelta(it)
                        }
                    } catch (e: Exception) {
                        // 对齐官方 SmoothEventSourceStream：坏事件跳过不中断
                    }
                    continue
                }
                // Text Completion SSE：data: {"event":"text_stream","text":...} / stream_end
                if (protocol == "textgenerationwebui") {
                    try {
                        val obj = json.parseToJsonElement(dataText).jsonObject
                        when (obj["event"]?.jsonPrimitive?.contentOrNull) {
                            "text_stream" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let {
                                if (it.isNotEmpty()) onDelta(it)
                            }
                            "stream_end" -> {
                                finished = true
                                emitDone()
                            }
                        }
                    } catch (e: Exception) {
                        // 对齐官方 SmoothEventSourceStream：坏事件跳过不中断
                    }
                    continue
                }
                try {
                    // 官方 StreamingProcessor：每个 SSE 块解析 logprobs（OpenAI chat 流式 choices[0].logprobs）
                    if (onLogprobs != null && protocol == "openai") {
                        runCatching {
                            val obj = json.parseToJsonElement(dataText).jsonObject
                            LogprobsEngine.parseChatCompletionLogprobs(obj, "openai", false)
                                ?.takeIf { it.isNotEmpty() }
                                ?.let(onLogprobs)
                        }
                    }
                    if (onToolCalls != null) {
                        val parsed = json.parseToJsonElement(dataText)
                        toolAccumulator.parse(parsed)
                        val snapshot = toolAccumulator.snapshot().toString()
                        if (snapshot != lastToolSnapshot) {
                            lastToolSnapshot = snapshot
                            onToolCalls(toolAccumulator.snapshot())
                        }
                    }
                    // 官方对拍解析器：逐字符增量；推理文本走独立通道（UI 显示思考过程，不进聊天正文）
                    for (chunk in SseChunkParser.parse(dataText)) {
                        if (chunk.reasoning) {
                            if (chunk.chunk.isNotEmpty()) onReasoning?.invoke(chunk.chunk)
                        } else if (chunk.chunk.isNotEmpty()) {
                            onDelta(chunk.chunk)
                        }
                    }
                } catch (e: Exception) {
                    // 对齐官方 SmoothEventSourceStream：整个事件处理 try/catch，
                    // JSON 解析失败/未知格式/其它异常都只跳过该事件，绝不中断整条流
                }
            }
        }
        if (!finished) emitDone()
    }

    /** 从 SSE 事件文本里取 data: 负载（多行按官方 EventSource 语义用 \n 连接）。 */
    private fun extractSseData(raw: String): String? {
        val lines = raw.lineSequence()
            .mapNotNull { line -> if (line.startsWith("data:")) line.removePrefix("data:").trimStart() else null }
            .toList()
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    /** 官方 openai.js proxySupportedSources（CLAUDE/OPENAI/MISTRALAI/MAKERSUITE/VERTEXAI/DEEPSEEK/XAI/ZAI/MOONSHOT）。 */
    private val proxySupportedIds = setOf("openai", "anthropic", "google", "vertexai", "mistral", "deepseek", "xai", "zai", "moonshot")

    private fun buildRequest(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        stream: Boolean,
        options: ProviderRequestOptions = ProviderRequestOptions(),
    ): Request {
        // 官方：reverse_proxy 非空且源在支持列表时，API 地址用代理、API Key 用 proxy_password
        val resolvedBase = requireHttpScheme(resolveBase(provider, profile))
        val proxyActive = profile.reverseProxy.isNotBlank() && provider.id in proxySupportedIds
        val base = if (proxyActive) requireHttpScheme(profile.reverseProxy) else resolvedBase
        val authProfile = if (proxyActive) profile.copy(apiKey = profile.proxyPassword) else profile
        val builder = Request.Builder()
        // 官方 createGenerationParameters：isO1（openai/azure_openai + o1-2024-12-17/o1）强制非流式
        val effectiveStream = stream &&
            !(provider.id in setOf("openai", "azure") && profile.model in setOf("o1-2024-12-17", "o1"))
        when (provider.protocol) {
            "anthropic" -> {
                val url = base.trimEnd('/') + "/messages"
                val effort = profile.sampler.reasoningEffort
                val isAdaptive = Regex("^claude-(opus-4-7)").containsMatchIn(profile.model) ||
                    (profile.sampler.enableAdaptiveThinking && Regex("^claude-(opus-4-6|sonnet-4-6)").containsMatchIn(profile.model))
                val reasoningBudget = ProviderConverters.calculateClaudeBudgetTokens(
                    profile.sampler.maxTokens, effort, effectiveStream, isAdaptive,
                )
                val request = AnthropicRequestBuilder.build(
                    model = profile.model,
                    messages = messages,
                    maxTokens = profile.sampler.maxTokens,
                    temperature = profile.sampler.temperature,
                    stream = effectiveStream,
                    topP = profile.sampler.topP,
                    reasoningEffort = effort,
                    includeReasoning = profile.sampler.showThoughts || profile.sampler.includeReasoning,
                    reasoningBudget = reasoningBudget,
                    enableAdaptiveThinking = profile.sampler.enableAdaptiveThinking,
                    useSystemPrompt = profile.sampler.useSysprompt,
                    tools = options.tools.map { AnthropicTool(it.name, it.description, it.parameters) },
                    toolChoice = options.toolChoice,
                    stop = options.stopSequences,
                    jsonSchema = options.jsonSchema,
                    enableWebSearch = options.enableWebSearch,
                    mediaQuality = profile.sampler.inlineImageQuality,
                    enableSystemPromptCache = profile.sampler.enableSystemPromptCache,
                    cachingAtDepth = profile.sampler.cachingAtDepth,
                    cacheTTL = profile.sampler.cacheTTL,
                )
                builder.url(url).post(request.body.toRequestBody("application/json".toMediaType()))
                builder.header("x-api-key", authProfile.apiKey)
                builder.header("anthropic-version", "2023-06-01")
                if (request.betaHeaders.isNotEmpty()) {
                    builder.header("anthropic-beta", request.betaHeaders.joinToString(","))
                }
            }
            "google" -> {
                val apiVersion = provider.apiVersion.ifBlank { "v1beta" }
                val model = URLEncoder.encode(profile.model, "UTF-8")
                val params = mutableListOf<String>()
                if (provider.authType == "google-key" && authProfile.apiKey.isNotEmpty()) {
                    params += "key=" + URLEncoder.encode(authProfile.apiKey, "UTF-8")
                }
                if (effectiveStream) params += "alt=sse"
                val url = base.trimEnd('/') + "/" + apiVersion + "/models/" + model + ":generateContent" +
                    (if (params.isNotEmpty()) "?" + params.joinToString("&") else "")
                val effort = profile.sampler.reasoningEffort
                val reasoningBudget = ProviderConverters.calculateGoogleBudgetTokens(
                    profile.sampler.maxTokens, effort, profile.model,
                )
                val body = GoogleRequestBuilder.build(
                    model = profile.model,
                    messages = messages,
                    maxOutputTokens = profile.sampler.maxTokens,
                    temperature = profile.sampler.temperature,
                    topP = profile.sampler.topP,
                    reasoningEffort = effort,
                    includeReasoning = profile.sampler.showThoughts || profile.sampler.includeReasoning,
                    mediaQuality = profile.sampler.inlineImageQuality,
                    reasoningBudget = reasoningBudget,
                    tools = options.tools.map { GeminiFunctionTool(it.name, it.description, it.parameters) },
                    toolChoice = options.toolChoice?.let { JsonPrimitive(it) },
                    enableWebSearch = options.enableWebSearch,
                    requestImages = options.requestImages,
                    aspectRatio = options.aspectRatio,
                    imageSize = options.imageSize,
                    safetySettings = options.safetySettings,
                    stop = options.stopSequences,
                    responseMimeType = if (options.jsonSchema != null) "application/json" else null,
                    responseSchema = options.jsonSchema?.let { schema ->
                        schema["value"] as? JsonObject ?: schema
                    },
                    topK = profile.sampler.topK.takeIf { it > 0 },
                    useSystemPrompt = profile.sampler.useSysprompt,
                )
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
            }
            "vertexai" -> {
                // 官方 src/endpoints/google.js getVertexAIAuth + getGoogleApiConfig：
                // proxy → Bearer proxy_password；full → 服务账号 JWT→access_token + 项目 URL；
                // express → x-goog-api-key +（可选）项目 URL。
                val region = profile.region.ifBlank { "us-central1" }
                val model = URLEncoder.encode(profile.model, "UTF-8")
                val endpoint = if (effectiveStream) "streamGenerateContent" else "generateContent"
                val reverseProxy = profile.reverseProxy.trim()
                val fullMode = profile.vertexaiAuthMode.ifBlank { "express" } == "full"
                var accessToken: String? = null
                var projectId: String? = profile.vertexaiExpressProjectId.ifBlank { null }
                if (fullMode && reverseProxy.isEmpty()) {
                    val saText = profile.vertexaiServiceAccountJson
                    if (saText.isBlank()) error("Vertex AI Full mode requires Service Account JSON")
                    val sa = VertexAuth.serviceAccount(saText) ?: error("Invalid Service Account JSON")
                    projectId = VertexAuth.projectId(sa)
                    accessToken = VertexAuth.accessToken(sa)
                }
                val req = VertexAuth.requestUrlAndHeaders(
                    authMode = if (fullMode) "full" else "express",
                    region = region,
                    model = model,
                    projectId = projectId,
                    reverseProxy = reverseProxy,
                    proxyPassword = profile.proxyPassword,
                    apiKey = profile.apiKey,
                    endpoint = endpoint,
                    accessToken = accessToken,
                )
                val url = req.url + (if (effectiveStream) "?alt=sse" else "")
                req.headers.forEach { (k, v) -> builder.header(k, v) }
                val effort = profile.sampler.reasoningEffort
                val reasoningBudget = ProviderConverters.calculateGoogleBudgetTokens(
                    profile.sampler.maxTokens, effort, profile.model,
                )
                val body = GoogleRequestBuilder.build(
                    model = profile.model,
                    messages = messages,
                    maxOutputTokens = profile.sampler.maxTokens,
                    temperature = profile.sampler.temperature,
                    topP = profile.sampler.topP,
                    reasoningEffort = effort,
                    includeReasoning = profile.sampler.showThoughts || profile.sampler.includeReasoning,
                    mediaQuality = profile.sampler.inlineImageQuality,
                    reasoningBudget = reasoningBudget,
                    tools = options.tools.map { GeminiFunctionTool(it.name, it.description, it.parameters) },
                    toolChoice = options.toolChoice?.let { JsonPrimitive(it) },
                    enableWebSearch = options.enableWebSearch,
                    requestImages = options.requestImages,
                    aspectRatio = options.aspectRatio,
                    imageSize = options.imageSize,
                    safetySettings = options.safetySettings,
                    stop = options.stopSequences,
                    responseMimeType = if (options.jsonSchema != null) "application/json" else null,
                    responseSchema = options.jsonSchema?.let { schema ->
                        schema["value"] as? JsonObject ?: schema
                    },
                    topK = profile.sampler.topK.takeIf { it > 0 },
                    useSystemPrompt = profile.sampler.useSysprompt,
                )
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
            }
            "textgenerationwebui" -> {
                val settings = options.textGenSettings ?: TextgenSettingsDefaults.forProfile(provider, profile)
                val body = TextgenRequestBodyEngine.build(
                    TextgenRequestBodyEngine.BuildInput(
                        settings = settings,
                        cfgValues = options.textGenCfgValues,
                        finalPrompt = options.textGenPrompt ?: "",
                        maxTokens = profile.sampler.maxTokens.takeIf { it > 0 },
                        type = if (effectiveStream) "normal" else "quiet",
                        stoppingStrings = options.stopSequences,
                        maxContext = profile.contextWindow.takeIf { it > 0 } ?: 4096,
                        dynatempTypes = "ooba vllm mancer aphrodite tabby",
                    )
                )
                val url = base.trimEnd('/') + (if (effectiveStream) "/api/v1/stream" else "/api/v1/generate")
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            "novel" -> {
                // 官方 src/endpoints/novelai.js：kayra/erato 走 text.novelai.net，其余 api.novelai.net
                val apiBase = if (profile.model.contains("kayra") || profile.model.contains("erato")) {
                    "https://text.novelai.net"
                } else {
                    base.trimEnd('/')
                }
                val url = apiBase.trimEnd('/') + (if (effectiveStream) "/ai/generate-stream" else "/ai/generate")
                val input = NovelRequestBodyEngine.fromSettingsJson(
                    model = profile.model,
                    finalPrompt = options.textGenPrompt ?: "",
                    maxLength = profile.sampler.maxTokens,
                    isImpersonate = options.textGenIsImpersonate,
                    isContinue = options.textGenIsContinue,
                    stoppingStrings = options.stopSequences,
                    requestTokenProbabilities = profile.sampler.requestTokenProbabilities,
                    settings = options.novelSettings,
                    defaults = NovelGenerationInput(
                        model = profile.model,
                        temperature = profile.sampler.temperature,
                        topP = profile.sampler.topP,
                        topK = profile.sampler.topK,
                        minP = profile.sampler.minP,
                        repetitionPenalty = profile.sampler.repetitionPenalty,
                    ),
                )
                val body = NovelRequestBodyEngine.build(input)
                builder.url(url).post(body.toString().toRequestBody("application/json".toMediaType()))
                builder.header("Authorization", "Bearer ${profile.apiKey}")
            }
            "kobold" -> {
                // 官方 src/endpoints/backends/kobold.js /generate：
                // localhost→127.0.0.1、非 gui_settings 全字段 + stop_sequence 条件、
                // 流式 URL=/extra/generate/stream、非流式=/v1/generate（请求体 KoboldRequestBodyEngine 差分 12 例）。
                val apiServer = base.trimEnd('/')
                val input = KoboldRequestBodyEngine.fromSettingsJson(
                    apiServer = apiServer,
                    prompt = options.textGenPrompt ?: "",
                    maxContextLength = profile.contextWindow.takeIf { it > 0 } ?: 4096,
                    maxLength = profile.sampler.maxTokens.takeIf { it > 0 } ?: 0,
                    streaming = effectiveStream,
                    settings = options.koboldSettings,
                )
                val result = KoboldRequestBodyEngine.build(input)
                builder.url(result.url).post(result.body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            "mistral" -> {
                val url = base.trimEnd('/') + "/chat/completions"
                val body = MistralRequestBuilder.build(profile.model, chatML(messages), profile.sampler, options)
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            "xai" -> {
                val url = base.trimEnd('/') + "/chat/completions"
                val body = XaiRequestBuilder.build(profile.model, chatML(messages), profile.sampler, options)
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            "ai21" -> {
                val url = base.trimEnd('/') + "/chat/completions"
                val body = Ai21RequestBuilder.build(profile.model, chatML(messages), profile.sampler, options)
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            "cohere" -> {
                val url = base.trimEnd('/') + "/chat"
                val body = CohereRequestBuilder.build(profile.model, chatML(messages), profile.sampler, options)
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
            }
            else -> {
                if (provider.id == "vertexai") {
                    error("Vertex AI 需要服务账号与项目配置，请使用 Gemini (AI Studio) 或自定义地址。")
                }
                val isTextCompletion = profile.model in TEXT_COMPLETION_MODELS && provider.id != "openrouter"
                val url = when (provider.id) {
                    "azure" -> {
                        val apiVersion = profile.apiVersionOverride.ifBlank { provider.apiVersion }.ifBlank { "2024-12-01" }
                        base.trimEnd('/') + "/openai/deployments/" +
                            URLEncoder.encode(profile.model, "UTF-8") +
                            "/chat/completions?api-version=" + apiVersion
                    }
                    "workers-ai" -> {
                        val account = profile.accountId.trim()
                        if (account.isEmpty()) {
                            error("Cloudflare Workers AI 需要账户 ID。")
                        }
                        base.trimEnd('/') + "/" + account + "/ai/v1/chat/completions"
                    }
                    else -> base.trimEnd('/') + (if (isTextCompletion) "/completions" else "/chat/completions")
                }
                val body = if (isTextCompletion) {
                    val prompt = ProviderConverters.convertTextCompletionPrompt(JsonArray(chatML(messages)))
                    TextCompletionRequestBuilder.build(profile.model, prompt, profile.sampler.copy(stream = effectiveStream))
                } else if (provider.id == "openrouter") {
                    val chatMl = chatML(messages).toMutableList()
                    ProviderConverters.addOpenRouterSignatures(chatMl, profile.model)
                    ProviderConverters.embedOpenRouterMedia(chatMl, audio = true, video = true)
                    val isClaude = Regex("^anthropic/claude").containsMatchIn(profile.model)
                    if (isClaude) {
                        if (profile.sampler.enableSystemPromptCache) {
                            ProviderConverters.cachingSystemPromptForOpenRouter(chatMl, profile.sampler.cacheTTL)
                        }
                        if (profile.sampler.cachingAtDepth != -1) {
                            ProviderConverters.cachingAtDepthForOpenRouterClaude(
                                chatMl, profile.sampler.cachingAtDepth, profile.sampler.cacheTTL,
                            )
                        }
                    }
                    val effort = profile.sampler.reasoningEffort
                    val extra = OpenRouterParams.extra(
                        middleout = profile.sampler.middleout,
                        enableWebSearch = options.enableWebSearch,
                        includeReasoning = profile.sampler.showThoughts || profile.sampler.includeReasoning,
                        reasoningEffort = effort,
                    )
                    ChatRequestBuilder.buildOpenAiCompatibleFromChatML(
                        model = profile.model,
                        messages = chatMl,
                        params = profile.sampler.copy(stream = effectiveStream),
                        options = options,
                        extra = extra,
                        source = provider.id,
                    )
                } else if (provider.id == "deepseek") {
                    // 官方 sendDeepSeekRequest：postProcessPrompt(SEMI_TOOLS) + addAssistantPrefix + addReasoningContentToToolCalls
                    val chatMl = chatML(messages).toMutableList()
                    val schema = options.jsonSchema
                    if (schema != null) {
                        chatMl += buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive("JSON schema for the response:\n" + Json {
                                    prettyPrint = true
                                    prettyPrintIndent = "    "
                                }.encodeToString(JsonElement.serializer(), schema["value"] ?: schema)))
                        }
                    }
                    val tools = options.openAiTools().let { arr ->
                        JsonArray(arr.map { el ->
                            val tool = el.jsonObject
                            val fn = tool["function"]?.jsonObject
                            val required = fn?.get("parameters")?.jsonObject?.get("required")
                            if (required is JsonArray && required.isEmpty()) {
                                val params = fn!!.get("parameters")!!.jsonObject.toMutableMap().apply { remove("required") }
                                val newFn = fn.toMutableMap().apply { put("parameters", JsonObject(params)) }
                                tool.toMutableMap().apply { put("function", JsonObject(newFn)) }.let { JsonObject(it) }
                            } else tool
                        })
                    }
                    val processed = ProviderConverters.addAssistantPrefix(
                        ProviderConverters.postProcessPrompt(chatMl, "semi_tools", PromptNames()),
                        if (options.hasTools) options.openAiTools().map { it.jsonObject } else emptyList(),
                        "prefix",
                    ).toMutableList()
                    ProviderConverters.addReasoningContentToToolCalls(processed)
                    val effort = profile.sampler.reasoningEffort
                    val extra = buildJsonObject {
                        if (options.hasTools) {
                            put("tools", tools)
                            options.toolChoice?.let { put("tool_choice", JsonPrimitive(it)) }
                        }
                        if (schema != null) {
                            put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
                        }
                        if (profile.sampler.includeReasoning && effort.isNotEmpty()) {
                            put("reasoning_effort", effort)
                        }
                    }
                    ChatRequestBuilder.buildOpenAiCompatibleFromChatML(
                        model = profile.model,
                        messages = processed,
                        params = profile.sampler.copy(stream = effectiveStream),
                        options = options.copy(tools = emptyList(), jsonSchema = null),
                        extra = extra,
                        source = provider.id,
                    )
                } else if (provider.id == "moonshot") {
                    // 官方后端 moonshot 分支：json_schema → setJsonObjectFormat（注入 schema 用户消息 + json_object）；
                    // 否则 addAssistantPrefix(partial)
                    val chatMl = chatML(messages).toMutableList()
                    val schema = options.jsonSchema
                    if (schema != null) {
                        chatMl += buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put(
                                "content",
                                JsonPrimitive(
                                    "JSON schema for the response:\n" +
                                        Json { prettyPrint = true; prettyPrintIndent = "    " }
                                            .encodeToString(JsonElement.serializer(), schema["value"] ?: schema),
                                ),
                            )
                        }
                    } else {
                        ProviderConverters.addAssistantPrefix(chatMl, emptyList(), "partial")
                    }
                    ChatRequestBuilder.buildOpenAiCompatibleFromChatML(
                        model = profile.model,
                        messages = chatMl,
                        params = profile.sampler.copy(stream = effectiveStream),
                        options = options,
                        source = provider.id,
                    )
                } else if (provider.id == "perplexity") {
                    // 官方后端 perplexity 分支：postProcessPrompt(STRICT) + reasoning_effort
                    val chatMl = ProviderConverters.postProcessPrompt(chatML(messages), "strict", PromptNames())
                    ChatRequestBuilder.buildOpenAiCompatibleFromChatML(
                        model = profile.model,
                        messages = chatMl,
                        params = profile.sampler.copy(stream = effectiveStream),
                        options = options,
                        source = provider.id,
                    )
                } else {
                    ChatRequestBuilder.buildOpenAiCompatible(
                        model = profile.model,
                        messages = messages,
                        params = profile.sampler.copy(stream = effectiveStream),
                        options = options,
                        source = provider.id,
                    )
                }
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, authProfile, anthropicVersion = false)
                if (provider.id == "zai") {
                    builder.header("Accept-Language", "en-US,en")
                }
            }
        }
        // OpenRouter 的 HTTP-Referer / X-Title 由 providers.json extra_headers 提供（项目身份）
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        // 官方 custom_include_headers：YAML 字符串合并进请求头（chat-completions.js custom 分支）
        if (provider.id == "custom" && options.customIncludeHeaders.isNotBlank()) {
            YamlMerge.headers(options.customIncludeHeaders).forEach { (k, v) -> builder.header(k, v) }
        }
        return builder.build()
    }

    private fun resolveBase(provider: ProviderSpec, profile: ConnectionProfile): String {
        if (provider.id == "custom" && profile.customUrl.isNotBlank()) return profile.customUrl.trimEnd('/')
        if (profile.baseUrlOverride.isNotBlank()) return profile.baseUrlOverride.trimEnd('/')
        if (profile.region.isNotBlank()) {
            provider.regionBases[profile.region]?.let { return it.trimEnd('/') }
        }
        return provider.baseUrl
    }

    /** 接口地址必须以 http(s):// 开头；否则 OkHttp 抛 IllegalArgumentException 会崩溃（配置错位时把 Key 当地址）。 */
    private fun requireHttpScheme(base: String): String {
        if (base.isNotBlank() && !base.startsWith("http://") && !base.startsWith("https://")) {
            error("接口地址无效：\"$base\" 应以 http:// 或 https:// 开头，请检查 设置→提供商→接口地址。")
        }
        return base
    }

    private fun applyAuth(
        builder: Request.Builder,
        provider: ProviderSpec,
        profile: ConnectionProfile,
        anthropicVersion: Boolean,
    ) {
        val key = profile.apiKey.trim()
        if (key.isEmpty()) return
        when (provider.authType) {
            "x-api-key" -> {
                builder.header("x-api-key", key)
                if (anthropicVersion && provider.protocol == "anthropic") {
                    builder.header("anthropic-version", "2023-06-01")
                }
            }
            "api-key" -> builder.header("api-key", key)
            // 官方 additional-headers.js getMancerHeaders / getInfermaticAIHeaders / getFeatherlessHeaders
            // google-key 走 URL 查询参数；none 无认证
            "google-key", "none" -> Unit
            else -> {
                val textgenHeaders = TextgenHeaders.forProvider(provider.id, key)
                if (textgenHeaders.isNotEmpty()) {
                    textgenHeaders.forEach { (k, v) -> builder.header(k, v) }
                } else {
                    builder.header("Authorization", "Bearer $key")
                }
            }
        }
    }

    private fun modelsUrl(provider: ProviderSpec, profile: ConnectionProfile): String? {
        val base = requireHttpScheme(resolveBase(provider, profile))
        if (base.isBlank()) return null
        return when (provider.id) {
            "azure" -> {
                val apiVersion = profile.apiVersionOverride.ifBlank { provider.apiVersion }.ifBlank { "2024-12-01" }
                base.trimEnd('/') + "/openai/models?api-version=" + apiVersion
            }
            "workers-ai" -> {
                val account = profile.accountId.trim()
                if (account.isEmpty()) return null
                base.trimEnd('/') + "/" + account + "/ai/models/search?task=Text+Generation&per_page=1000"
            }
            "google" -> {
                val apiVersion = provider.apiVersion.ifBlank { "v1beta" }
                val key = if (provider.authType == "google-key" && profile.apiKey.isNotEmpty()) {
                    "?key=" + URLEncoder.encode(profile.apiKey, "UTF-8")
                } else ""
                base.trimEnd('/') + "/" + apiVersion + "/models" + key
            }
            "vertexai" -> null
            else -> {
                val endpoint = provider.modelsEndpoint
                if (endpoint.isBlank()) return null
                val query = provider.modelsQuery.entries.joinToString("&") { (k, v) ->
                    k + "=" + URLEncoder.encode(v, "UTF-8")
                }
                base.trimEnd('/') + "/" + endpoint.trimStart('/') + (if (query.isNotEmpty()) "?" + query else "")
            }
        }
    }

    private fun parseChatResponse(protocol: String, text: String): String = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        when (protocol) {
            "anthropic" -> root["content"]?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.asText() }
                ?.joinToString("").orEmpty()
            "google", "vertexai" -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.asText() }
                ?.joinToString("").orEmpty()
            "textgenerationwebui" -> ResponseDataExtractor.extractMessageFromData(root, "textgenerationwebui")
            "novel" -> ResponseDataExtractor.extractMessageFromData(root, "novel")
            "kobold", "koboldhorde" -> root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.asText().orEmpty()
            "cohere" -> {
                val message = root["message"]?.jsonObject ?: return ""
                when (val content = message["content"]) {
                    is JsonPrimitive -> content.content
                    is JsonArray -> content.mapNotNull { part ->
                        (part as? JsonObject)?.get("text")?.asText()
                    }.joinToString("")
                    else -> message["tool_plan"]?.asText().orEmpty()
                }
            }
            else -> ResponseDataExtractor.extractMessageFromData(root, "openai")
        }
    }.getOrDefault("")

    private fun parseModels(provider: ProviderSpec, text: String): List<ModelSortEngine.ModelMeta> = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        when (provider.modelsFormat) {
            "google" -> root["models"]?.jsonArray?.mapNotNull { m ->
                val obj = m.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val methods = obj["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                if (methods != null && "generateContent" !in methods) null else ModelSortEngine.ModelMeta(id = name.removePrefix("models/"))
            }.orEmpty()
            "workers" -> root["result"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content?.let { n -> ModelSortEngine.ModelMeta(id = n) } }.orEmpty()
            "azure" -> {
                val value = root["value"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content?.let { n -> ModelSortEngine.ModelMeta(id = n) } }.orEmpty()
                if (value.isNotEmpty()) {
                    value
                } else {
                    root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.asText()?.let { n -> ModelSortEngine.ModelMeta(id = n) } }.orEmpty()
                }
            }
            else -> root["data"]?.jsonArray?.mapNotNull { modelFromJson(it as? JsonObject ?: return@mapNotNull null) }.orEmpty()
        }
    }.getOrDefault(emptyList())

    /** 官方模型对象字段投影（data[] 各源：id/name/context_length/pricing/tokens/endpoints/type/info）。 */
    private fun modelFromJson(obj: JsonObject): ModelSortEngine.ModelMeta {
        val pricing = obj["pricing"] as? JsonObject
        val info = obj["info"] as? JsonObject
        return ModelSortEngine.ModelMeta(
            id = obj["id"]?.asText().orEmpty(),
            name = obj["name"]?.asText(),
            contextLength = obj["context_length"]?.jsonPrimitive?.longOrNull,
            pricingPrompt = pricing?.get("prompt")?.jsonPrimitive?.doubleOrNull,
            pricingCompletion = pricing?.get("completion")?.jsonPrimitive?.doubleOrNull,
            pricingInput = pricing?.get("input")?.jsonPrimitive?.doubleOrNull,
            pricingOutput = pricing?.get("output")?.jsonPrimitive?.doubleOrNull,
            tokens = obj["tokens"]?.jsonPrimitive?.longOrNull,
            infoName = info?.get("name")?.jsonPrimitive?.contentOrNull,
            infoContextLength = info?.get("contextLength")?.jsonPrimitive?.longOrNull,
            infoDeveloper = info?.get("developer")?.jsonPrimitive?.contentOrNull,
            endpoints = (obj["endpoints"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull },
            type = obj["type"]?.asText(),
        )
    }

    private fun JsonElement?.asText(): String? = when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> null
    }
}
