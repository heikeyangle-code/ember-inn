package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 特殊协议请求体构建（对齐官方 chat-completions.js 各后端，body 差分见 special-bodies-official.mjs）：
 * Mistral（sendMistralAIRequest）/ xAI（sendXAIRequest）/ AI21（sendAI21Request）/ Cohere（sendCohereRequest）。
 */

/** JS 数字序列化：整数输出为整数（1.0 → 1），否则保留小数。 */
private fun jsNum(value: Double): JsonPrimitive =
    if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
        JsonPrimitive(value.toInt())
    } else {
        JsonPrimitive(value)
    }
object MistralRequestBuilder {

    fun build(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams,
        options: ProviderRequestOptions,
        names: PromptNames = PromptNames(),
    ): String {
        val converted = ProviderConverters.convertMistral(messages, names)
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", JsonArray(converted))
            put("temperature", jsNum(params.temperature))
            put("top_p", jsNum(params.topP))
            put("frequency_penalty", jsNum(params.frequencyPenalty))
            put("presence_penalty", jsNum(params.presencePenalty))
            put("max_tokens", params.maxTokens)
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
                        put("description", JsonPrimitive(schema["description"]?.let { it.toString().trim('"') } ?: ""))
                        put("schema", schema["value"] ?: schema)
                        put("strict", JsonPrimitive(schema["strict"]?.jsonPrimitive?.content?.toBoolean() ?: true))
                    })
                })
            }
        }.toString()
    }
}

object XaiRequestBuilder {

    fun build(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams,
        options: ProviderRequestOptions,
        names: PromptNames = PromptNames(),
    ): String {
        val converted = ProviderConverters.convertXAI(messages, names)
        val effort = params.reasoningEffort
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", JsonArray(converted))
            put("temperature", jsNum(params.temperature))
            put("max_tokens", params.maxTokens)
            put("stream", params.stream)
            put("presence_penalty", jsNum(params.presencePenalty))
            put("frequency_penalty", jsNum(params.frequencyPenalty))
            put("top_p", jsNum(params.topP))
            // 官方：reasoning_effort 非空即写（auto 也写 low）
            if (effort.isNotEmpty()) {
                put("reasoning_effort", if (effort == "high") "high" else "low")
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
        }.toString()
    }
}

object Ai21RequestBuilder {

    /** 官方 JSON.stringify(value, null, 4)。 */
    private fun prettyJson(el: JsonElement): String {
        val pretty = Json { prettyPrint = true; prettyPrintIndent = "    " }
        return pretty.encodeToString(JsonElement.serializer(), el)
    }

    fun build(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams,
        options: ProviderRequestOptions,
        names: PromptNames = PromptNames(),
    ): String {
        val raw = messages.toMutableList()
        val schema = options.jsonSchema
        if (schema != null) {
            // 官方 Hack to support JSON schema
            raw += buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("JSON schema for the response:\n" + prettyJson(schema["value"] ?: schema)))
            }
        }
        val converted = ProviderConverters.convertAI21(raw, names)
        return buildJsonObject {
            put("messages", JsonArray(converted))
            put("model", JsonPrimitive(model))
            put("max_tokens", params.maxTokens)
            put("temperature", jsNum(params.temperature))
            put("top_p", jsNum(params.topP))
            put("stream", params.stream)
            if (options.hasTools) put("tools", options.openAiTools())
            if (schema != null) {
                put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
            }
        }.toString()
    }
}

object CohereRequestBuilder {

    fun build(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams,
        options: ProviderRequestOptions,
        names: PromptNames = PromptNames(),
    ): String {
        val converted = ProviderConverters.convertCohere(messages, names)
        return buildJsonObject {
            put("stream", params.stream)
            put("model", JsonPrimitive(model))
            put("messages", JsonArray(converted))
            put("temperature", jsNum(params.temperature))
            put("max_tokens", params.maxTokens)
            put("p", jsNum(params.topP))
            put("frequency_penalty", jsNum(params.frequencyPenalty))
            put("presence_penalty", jsNum(params.presencePenalty))
            put("documents", JsonArray(emptyList()))
            if (options.hasTools) {
                put("tools", JsonArray(options.tools.map { t ->
                    buildJsonObject {
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(t.name))
                            put("description", JsonPrimitive(t.description))
                            val paramsJson = t.parameters.toMutableMap().apply { remove("\$schema") }
                            put("parameters", JsonObject(paramsJson))
                        })
                    }
                }))
            } else {
                put("tools", JsonArray(emptyList()))
            }
            options.jsonSchema?.let { schema ->
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put("schema", schema["value"] ?: schema)
                })
            }
            // 官方 canDoSafetyMode：模型以 08-2024 结尾
            if (model.endsWith("08-2024")) {
                put("safety_mode", JsonPrimitive("OFF"))
            }
        }.toString()
    }
}
