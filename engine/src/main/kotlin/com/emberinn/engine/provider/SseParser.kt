package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** OpenAI Chat Completions SSE 解析：逐块提取 delta.content，忽略非 data 行。 */
object SseParser {

    private val json = Json { ignoreUnknownKeys = true }

    data class Chunk(
        val content: String,
        val done: Boolean,
    )

    /** 解析一段 SSE 文本（可能多行），返回每个 data 事件的内容增量。 */
    fun parse(raw: String): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var currentData = StringBuilder()
        var inEvent = false
        for (line in raw.lineSequence()) {
            when {
                line == "" -> {
                    if (inEvent && currentData.isNotEmpty()) {
                        chunks += parseData(currentData.toString())
                        currentData = StringBuilder()
                    }
                    inEvent = false
                }
                line.startsWith("data:") -> {
                    inEvent = true
                    if (currentData.isNotEmpty()) currentData.append('\n')
                    currentData.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        if (inEvent && currentData.isNotEmpty()) {
            chunks += parseData(currentData.toString())
        }
        return chunks
    }

    private fun parseData(data: String): Chunk {
        if (data == "[DONE]") return Chunk(content = "", done = true)
        return runCatching {
            val root = json.parseToJsonElement(data).jsonObject
            val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
            val text = delta?.get("content")?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
            Chunk(content = text, done = false)
        }.getOrDefault(Chunk(content = "", done = false))
    }
}
