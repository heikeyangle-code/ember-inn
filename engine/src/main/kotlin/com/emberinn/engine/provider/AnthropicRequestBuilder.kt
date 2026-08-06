package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Anthropic Messages API 请求体（对齐官方 anthropic 协议）。 */
object AnthropicRequestBuilder {

    fun build(
        model: String,
        messages: List<CompletionMessage>,
        maxTokens: Int = 512,
        temperature: Double = 1.0,
        stream: Boolean = false,
    ): String {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", stream)
            if (system.isNotEmpty()) put("system", system)
            put("messages", JsonArray(messages.filter { it.role != "system" }.map { m ->
                buildJsonObject {
                    put("role", if (m.role == "assistant") "assistant" else "user")
                    put("content", JsonArray(listOf(buildJsonObject {
                        put("type", "text")
                        put("text", m.content)
                    })))
                }
            }))
        }
        return body.toString()
    }
}
