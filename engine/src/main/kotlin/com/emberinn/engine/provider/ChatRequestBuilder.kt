package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaInliner
import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 采样参数（对齐官方 OpenAI 请求体字段）。 */
@Serializable
data class SamplerParams(
    val temperature: Double = 1.0,
    val topP: Double = 1.0,
    val maxTokens: Int = 512,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val stream: Boolean = false,
    /** 官方 reasoning_effort：auto/low/medium/high/min/max，默认 auto。 */
    val reasoningEffort: String = "auto",
    /** 官方 include_reasoning（思考内容是否随响应返回），默认关。 */
    val includeReasoning: Boolean = false,
    /** 官方 enableAdaptiveThinking（Claude opus-4-6/sonnet-4-6 是否走 adaptive），默认开。 */
    val enableAdaptiveThinking: Boolean = true,
    /** 官方 claude.enableSystemPromptCache（OpenRouter/Claude 系统提示缓存），默认关。 */
    val enableSystemPromptCache: Boolean = false,
    /** 官方 cachingAtDepth（消息缓存深度，-1 = 关）。 */
    val cachingAtDepth: Int = -1,
    /** 官方 claude.extendedTTL：false→5m，true→1h。 */
    val cacheTTL: String = "5m",
    /** 官方 settings.seed（-1 = 不发送）。 */
    val seed: Int = -1,
    /** 官方 settings.n（多回复/多 swipe）。 */
    val n: Int = 1,
    /** 官方 settings.top_k（OpenAI 系/OpenRouter）。 */
    val topK: Int = 40,
    /** 官方 settings.logit_bias（仅支持源发送）。 */
    val logitBias: Map<String, Double> = emptyMap(),
    /** 官方 settings.verbosity（gpt-5 系）。 */
    val verbosity: String? = null,
)

private val OPENAI_REASONING_EFFORT_MODELS = setOf(
    "o1", "o3-mini", "o3-mini-2025-01-31", "o4-mini", "o4-mini-2025-04-16",
    "o3", "o3-2025-04-16", "gpt-5", "gpt-5-2025-08-07", "gpt-5-mini",
    "gpt-5-mini-2025-08-07", "gpt-5-nano", "gpt-5-nano-2025-08-07",
    "gpt-5.1", "gpt-5.1-2025-11-13", "gpt-5.1-chat-latest", "gpt-5.2",
    "gpt-5.2-2025-12-11", "gpt-5.2-chat-latest", "gpt-5.3-chat-latest",
    "gpt-5.4", "gpt-5.4-2026-03-05", "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17",
    "gpt-5.4-nano", "gpt-5.4-nano-2026-03-17", "gpt-5.5", "gpt-5.5-2026-04-23",
)

private val OPENAI_REASONING_EFFORT_MAP = mapOf("min" to "minimal")
private val OPENAI_FIXED_REASONING_EFFORT = mapOf("gpt-5.3-chat-latest" to "medium")
private val OPENAI_VERBOSITY_MODELS = Regex("^gpt-5")

/** 工具定义（官方 request.body.tools 的 function 结构）。 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = JsonObject(emptyMap()),
)

/**
 * 发送请求时的能力开关（工具/结构化输出/联网搜索/图像模态/安全设置）。
 * 对应官方 oai_settings 与后端 request.body 的相关字段，App 层按设置填充。
 */
@Serializable
data class ProviderRequestOptions(
    val tools: List<ToolDefinition> = emptyList(),
    val toolChoice: String? = null,
    val jsonSchema: JsonObject? = null,
    val enableWebSearch: Boolean = false,
    val requestImages: Boolean = false,
    val aspectRatio: String = "",
    val imageSize: String = "",
    val safetySettings: JsonArray = JsonArray(emptyList()),
    /** 官方 createGenerationParameters.stop / 后端 request.body.stop。 */
    val stopSequences: List<String> = emptyList(),
) {
    val hasTools: Boolean get() = tools.isNotEmpty()

    /** 官方 OpenAI 风格 tools 数组（type=function）。 */
    fun openAiTools(): JsonArray = JsonArray(tools.map { t ->
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("function", buildJsonObject {
                put("name", JsonPrimitive(t.name))
                put("description", JsonPrimitive(t.description))
                put("parameters", t.parameters)
            })
        }
    })
}

/** OpenAI 兼容 Chat Completions 请求体构建。 */
object ChatRequestBuilder {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildOpenAiCompatible(
        model: String,
        messages: List<CompletionMessage>,
        params: SamplerParams = SamplerParams(),
        options: ProviderRequestOptions = ProviderRequestOptions(),
        source: String = "openai",
    ): String = buildOpenAiCompatibleFromChatML(model, messages.map { messageJson(it) }, params, options, source = source)

    /**
     * 直接吃官方 ChatML 消息（供 OpenRouter 等先做签名/缓存/媒体处理，再序列化）。
     * extra 额外请求体字段（如 OpenRouter 的 transforms/plugins/reasoning）。
     */
    fun buildOpenAiCompatibleFromChatML(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams = SamplerParams(),
        options: ProviderRequestOptions = ProviderRequestOptions(),
        extra: JsonObject? = null,
        source: String = "openai",
    ): String {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", params.maxTokens)
            put("presence_penalty", params.presencePenalty)
            put("frequency_penalty", params.frequencyPenalty)
            put("stream", params.stream)
            put("top_k", params.topK)
            if (options.stopSequences.isNotEmpty()) {
                put("stop", JsonArray(options.stopSequences.map { JsonPrimitive(it) }))
            }
            if (params.logitBias.isNotEmpty() && source in setOf("openai", "azure_openai", "openrouter", "electronhub", "chutes", "custom")) {
                put("logit_bias", buildJsonObject {
                    params.logitBias.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            if (source in setOf("openai", "azure_openai", "openrouter", "mistralai", "custom", "cohere", "groq", "electronhub", "nanogpt", "xai", "pollinations", "aimlapi", "vertexai", "makersuite", "chutes") && params.seed >= 0) {
                put("seed", JsonPrimitive(params.seed))
            }
            if (params.n > 1 && source in setOf("openai", "azure_openai", "custom", "xai", "aimlapi", "moonshot")) {
                put("n", JsonPrimitive(params.n))
            }
            if (params.reasoningEffort.isNotBlank() && source in setOf("custom", "openai") && model in OPENAI_REASONING_EFFORT_MODELS) {
                put("reasoning_effort", JsonPrimitive(OPENAI_FIXED_REASONING_EFFORT[model] ?: OPENAI_REASONING_EFFORT_MAP[params.reasoningEffort] ?: params.reasoningEffort))
            }
            if (!params.verbosity.isNullOrBlank() && source in setOf("custom", "openai") && OPENAI_VERBOSITY_MODELS.containsMatchIn(model)) {
                put("verbosity", JsonPrimitive(params.verbosity))
            }
            if (options.hasTools) {
                put("tools", options.openAiTools())
                options.toolChoice?.let { put("tool_choice", JsonPrimitive(it)) }
            }
            options.jsonSchema?.let { schema ->
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put("json_schema", buildJsonObject {
                        put("name", JsonPrimitive(schema["name"]?.let { it.toString().trim('"') } ?: "json"))
                        put("strict", JsonPrimitive(schema["strict"]?.jsonPrimitive?.content?.toBoolean() ?: true))
                        put("schema", schema["value"] ?: schema)
                    })
                })
            }
            if (extra != null) extra.forEach { (k, v) -> put(k, v) }
        }
        return applyGpt5Params(body, model, params, source).toString()
    }

    /**
     * 对齐官方 openai.js createGenerationParameters 的 gpt-5 分支
     * （gptSources = openai / azure_openai / openrouter）：
     * max_tokens → max_completion_tokens，并删掉 gpt-5 不支持的采样参数。
     */
    private fun applyGpt5Params(
        body: JsonObject,
        model: String,
        params: SamplerParams,
        source: String,
    ): JsonObject {
        val gptSources = setOf("openai", "azure_openai", "openrouter")
        if (source !in gptSources || !Regex("gpt-5").containsMatchIn(model)) return body
        val map = body.toMutableMap()
        map["max_completion_tokens"] = map.remove("max_tokens") ?: JsonNull
        map.remove("logprobs")
        map.remove("top_logprobs")
        if (Regex("gpt-5-chat-latest").containsMatchIn(model)) {
            map.remove("tools")
            map.remove("tool_choice")
        } else if (
            Regex("gpt-5\\.(1|2|3|4)").containsMatchIn(model) &&
            !Regex("chat-latest").containsMatchIn(model) &&
            params.reasoningEffort.isBlank()
        ) {
            map.remove("frequency_penalty")
            map.remove("presence_penalty")
            map.remove("logit_bias")
            map.remove("stop")
        } else {
            map.remove("temperature")
            map.remove("top_p")
            map.remove("frequency_penalty")
            map.remove("presence_penalty")
            map.remove("logit_bias")
            map.remove("stop")
        }
        return JsonObject(map)
    }

    private fun messageJson(message: CompletionMessage): JsonObject = buildJsonObject {
        put("role", message.role)
        val hasMedia = message.media?.isNotEmpty() == true
        put(
            "content",
            if (hasMedia) MediaInliner.inlineOpenAi(JsonPrimitive(message.content), message.media.orEmpty())
            else JsonPrimitive(message.content),
        )
        message.name?.let { put("name", it) }
        message.toolCallId?.let { put("tool_call_id", it) }
        message.toolCalls?.let { calls ->
            put("tool_calls", JsonArray(calls.map { call ->
                buildJsonObject {
                    put("id", call.id)
                    put("type", call.type)
                    put("function", buildJsonObject {
                        put("name", call.name)
                        put("arguments", call.arguments)
                    })
                    // 对齐官方 serializeMessages：tool_calls 条目透传 signature
                    call.signature?.let { put("signature", it) }
                }
            }))
        }
        // 对齐官方 serializeMessages：message.signature / message.reasoning 透传（推理链/OpenRouter/Custom）
        message.signature?.let { put("signature", it) }
        message.reasoning?.let { put("reasoning", it) }
    }
}
