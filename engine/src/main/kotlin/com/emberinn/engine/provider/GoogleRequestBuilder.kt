package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Gemini generateContent 请求体（对齐官方 google 协议）。 */
object GoogleRequestBuilder {

    fun build(
        model: String,
        messages: List<CompletionMessage>,
        maxOutputTokens: Int = 512,
        temperature: Double = 1.0,
        topP: Double = 1.0,
    ): String {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            put("contents", JsonArray(messages.filter { it.role != "system" }.map { m ->
                buildJsonObject {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", JsonArray(listOf(buildJsonObject {
                        put("text", m.content)
                    })))
                }
            }))
            if (system.isNotEmpty()) put("systemInstruction", buildJsonObject {
                put("parts", JsonArray(listOf(buildJsonObject { put("text", system) })))
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("topP", topP)
                put("maxOutputTokens", maxOutputTokens)
            })
        }
        return body.toString()
    }
}
