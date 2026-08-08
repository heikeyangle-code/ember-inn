package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaInliner
import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
)

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
    ): String = buildOpenAiCompatibleFromChatML(model, messages.map { messageJson(it) }, params, options)

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
            if (options.hasTools) {
                put("tools", options.openAiTools())
                options.toolChoice?.let { put("tool_choice", JsonPrimitive(it)) }
            }
            options.jsonSchema?.let { schema ->
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put("json_schema", buildJsonObject {
                        put("name", JsonPrimitive(schema["name"]?.let { it.toString().trim('"') } ?: "json"))
                        put("strict", JsonPrimitive(true))
                        put("schema", schema["value"] ?: schema)
                    })
                })
            }
            if (extra != null) extra.forEach { (k, v) -> put(k, v) }
        }
        return body.toString()
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
                }
            }))
        }
    }
}
