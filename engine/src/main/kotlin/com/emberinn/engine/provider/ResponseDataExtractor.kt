package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 官方 script.js extractMessageFromData / extractJsonFromData 的引擎移植。
 * removeReasoningFromString 由调用方注入（官方 reasoning.js，后续单独差分）。
 */
object ResponseDataExtractor {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonNumberRegex = Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$")

    fun extractMessageFromData(data: JsonElement?, activeApi: String?): String {
        fun getResult(): String? {
            if (data is JsonPrimitive) {
                return data.content
            }
            if (data !is JsonObject && data !is JsonArray) return null
            val obj = data as? JsonObject
            return when (activeApi) {
                "kobold" -> obj?.get("results")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.stringOrNull()
                "koboldhorde" -> obj?.get("text")?.stringOrNull()
                "textgenerationwebui" -> {
                    val choices = obj?.get("choices")?.jsonArray
                    choices?.firstOrNull()?.jsonObject?.get("text")?.stringOrNull()
                        ?: choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.stringOrNull()
                        ?: obj?.get("content")?.stringOrNull()
                        ?: obj?.get("response")?.stringOrNull()
                        ?: (data as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                        ?: ""
                }
                "novel" -> obj?.get("output")?.stringOrNull()
                "openai" -> {
                    val contentTexts = obj?.get("content")?.jsonArray
                        ?.mapNotNull { it.jsonObjectOrNull()?.get("text")?.stringOrNull() }
                    if (contentTexts != null) {
                        contentTexts.joinToString("\n\n")
                    } else {
                        val choices = obj?.get("choices")?.jsonArray
                        choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.stringOrNull()
                            ?: choices?.firstOrNull()?.jsonObject?.get("text")?.stringOrNull()
                            ?: obj?.get("text")?.stringOrNull()
                            ?: obj?.get("message")?.jsonObject?.get("content")?.jsonArray
                                ?.firstOrNull()?.jsonObject?.get("text")?.stringOrNull()
                            ?: obj?.get("message")?.jsonObject?.get("tool_plan")?.stringOrNull()
                            ?: ""
                    }
                }
                else -> ""
            }
        }

        return getResult() ?: ""
    }

    fun extractJsonFromData(
        data: JsonElement?,
        mainApi: String?,
        chatCompletionSource: String?,
        returnInvalidJson: Boolean = false,
        removeReasoning: (String) -> String = { it },
    ): String {
        fun tryParse(value: String): JsonElement? {
            // kotlinx 的 parseToJsonElement 会把裸词 bad 当字符串解析，官方 JSON.parse 会抛错；
            // 先按 JSON 语法白名单校验，保证差分语义一致。
            val trimmed = value.trim()
            val looksValid = trimmed.startsWith("{") ||
                trimmed.startsWith("[") ||
                trimmed.startsWith("\"") ||
                trimmed == "true" ||
                trimmed == "false" ||
                trimmed == "null" ||
                jsonNumberRegex.matches(trimmed)
            if (!looksValid) return null
            return try {
                json.parseToJsonElement(value)
            } catch (_: Exception) {
                null
            }
        }

        if (mainApi != "openai") {
            return "{}"
        }

        val text = extractMessageFromData(data, "openai")
        val result: JsonElement? = when (chatCompletionSource) {
            "claude" -> (data as? JsonObject)?.get("content")?.jsonArray
                ?.firstOrNull { it.jsonObjectOrNull()?.get("type")?.stringOrNull() == "tool_use" }
                ?.jsonObjectOrNull()?.get("input")
            "perplexity" -> {
                val parsed = tryParse(removeReasoning(text))
                if (parsed == null && returnInvalidJson) return text
                parsed
            }
            else -> {
                val parsed = tryParse(text)
                if (parsed == null && returnInvalidJson) return text
                parsed
            }
        }

        return json.encodeToString(JsonElement.serializer(), result ?: JsonNull)
            .let { if (it == "null") "{}" else it }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
