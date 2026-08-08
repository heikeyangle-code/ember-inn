package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** SSE 解析：OpenAI Chat Completions / Anthropic Messages / Gemini generateContent 三种格式。 */
object SseParser {

    private val json = Json { ignoreUnknownKeys = true }

    data class Chunk(
        val content: String,
        val done: Boolean,
    )

    private data class SseEvent(
        val event: String,
        val data: String,
    )

    /** 把一段 SSE 文本切成 (event, data) 事件列表。 */
    private fun events(raw: String): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        var event = ""
        val data = StringBuilder()
        var inEvent = false

        fun flush() {
            if (inEvent && data.isNotEmpty()) {
                out += SseEvent(event, data.toString())
            }
            event = ""
            data.setLength(0)
            inEvent = false
        }

        for (line in raw.lineSequence()) {
            when {
                line.isEmpty() -> flush()
                line.startsWith("event:") -> {
                    inEvent = true
                    event = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    inEvent = true
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        flush()
        return out
    }

    /**
     * 解析一段 SSE 文本（可能多行），返回每个事件的内容增量。
     * protocol: openai / anthropic / google。
     */
    fun parse(raw: String, protocol: String = "openai"): List<Chunk> = when (protocol) {
        "anthropic" -> events(raw).mapNotNull { ev ->
            when {
                ev.event == "message_stop" || ev.data == "[DONE]" -> Chunk(content = "", done = true)
                ev.event == "content_block_delta" -> runCatching {
                    val root = json.parseToJsonElement(ev.data).jsonObject
                    Chunk(
                        content = root["delta"]?.jsonObject?.get("text")?.asText().orEmpty(),
                        done = false,
                    )
                }.getOrNull()
                else -> runCatching {
                    val root = json.parseToJsonElement(ev.data).jsonObject
                    when (root["type"]?.jsonPrimitive?.content) {
                        "message_stop" -> Chunk(content = "", done = true)
                        "content_block_delta" -> Chunk(
                            content = root["delta"]?.jsonObject?.get("text")?.asText().orEmpty(),
                            done = false,
                        )
                        else -> null
                    }
                }.getOrNull()
            }
        }
        "google" -> events(raw).mapNotNull { ev ->
            runCatching {
                val root = json.parseToJsonElement(ev.data).jsonObject
                val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.asText() }
                    ?.joinToString("").orEmpty()
                Chunk(content = text, done = false)
            }.getOrNull()
        }
        "cohere" -> events(raw).mapNotNull { ev ->
            when {
                ev.data == "[DONE]" -> Chunk(content = "", done = true)
                else -> runCatching {
                    val root = json.parseToJsonElement(ev.data).jsonObject
                    when (root["type"]?.jsonPrimitive?.content) {
                        "message-end", "stream-end" -> Chunk(content = "", done = true)
                        else -> {
                            val message = root["delta"]?.jsonObject?.get("message")?.jsonObject
                            Chunk(
                                content = message?.get("content")?.jsonObject?.get("text")?.asText()
                                    ?: message?.get("tool_plan")?.asText().orEmpty(),
                                done = false,
                            )
                        }
                    }
                }.getOrNull()
            }
        }
        else -> events(raw).mapNotNull { ev ->
            when {
                ev.data == "[DONE]" -> Chunk(content = "", done = true)
                else -> runCatching {
                    val root = json.parseToJsonElement(ev.data).jsonObject
                    Chunk(
                        content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject?.get("content")?.asText().orEmpty(),
                        done = false,
                    )
                }.getOrNull()
            }
        }
    }

    private fun JsonElement?.asText(): String? = when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> null
    }
}
