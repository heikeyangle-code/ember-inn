package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 官方 sse-stream.js parseStreamData 的单个产出。 */
data class SseParsedChunk(
    val data: JsonElement,
    val chunk: String,
    val reasoning: Boolean = false,
    val reasoningPresent: Boolean = false,
)

/** 对齐官方 sse-stream.js parseStreamData：把一块 JSON 拆成逐字符增量。 */
object SseChunkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonString: String): List<SseParsedChunk> {
        val root = json.parseToJsonElement(jsonString)
        return parse(root)
    }

    fun parse(root: JsonElement): List<SseParsedChunk> {
        val out = mutableListOf<SseParsedChunk>()
        val obj = root as? JsonObject ?: return out
        val delta = obj["delta"] as? JsonObject

        // Cohere（官方要求 type 为 tool-plan-delta / content-delta）
        val cohereText = delta?.get("message")?.jsonObject?.get("content")?.jsonObject?.get("text")
        val cohereType = obj["type"]?.jsonPrimitive?.content
        if (
            cohereText is JsonPrimitive && cohereText.isString &&
            (cohereType == "tool-plan-delta" || cohereType == "content-delta")
        ) {
            for (ch in cohereText.content) {
                val data = setPath(root, listOf("delta", "message", "content", "text"), JsonPrimitive(ch.toString()))
                out += SseParsedChunk(data, ch.toString())
            }
            return out
        }

        // Claude text / thinking
        val claudeText = delta?.get("text")
        if (claudeText is JsonPrimitive && claudeText.isString && claudeText.content.isNotEmpty()) {
            for (ch in claudeText.content) {
                val data = setPath(root, listOf("delta", "text"), JsonPrimitive(ch.toString()))
                out += SseParsedChunk(data, ch.toString())
            }
            return out
        }
        val claudeThinking = delta?.get("thinking")
        if (claudeThinking is JsonPrimitive && claudeThinking.isString && claudeThinking.content.isNotEmpty()) {
            for (ch in claudeThinking.content) {
                val data = setPath(root, listOf("delta", "thinking"), JsonPrimitive(ch.toString()))
                out += SseParsedChunk(data, ch.toString(), reasoning = true, reasoningPresent = true)
            }
            return out
        }

        // Gemini candidates
        val candidates = obj["candidates"] as? JsonArray
        if (candidates != null) {
            if (candidates.isEmpty()) return out
            val first = candidates.first().jsonObject
            val isNotPrimary = (first["index"] as? JsonPrimitive)?.content?.toIntOrNull()?.let { it > 0 } ?: false
            val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray
            val hasTool = parts?.any { it.jsonObject["functionCall"] != null } == true
            val hasInline = parts?.any { it.jsonObject["inlineData"] != null } == true
            if (isNotPrimary) return out
            if (hasTool || hasInline) {
                out += SseParsedChunk(root, "")
                return out
            }
            val content = first["content"] as? JsonObject
            val partList = content?.get("parts") as? JsonArray
            if (content != null && partList != null) {
                for ((j, partEl) in partList.withIndex()) {
                    val partObj = partEl as? JsonObject ?: continue
                    val text = partObj["text"] as? JsonPrimitive
                    if (text != null && text.isString) {
                        for ((k, ch) in text.content.withIndex()) {
                            val more = partList.size > 1
                            val notLastPart = j != partList.size - 1
                            val lastSymbol = k == text.content.length - 1
                            val addNewline = more && notLastPart && lastSymbol
                            val str = ch.toString() + if (addNewline) "\n\n" else ""
                            val partClone = partEl.jsonObject.toMutableMap()
                            partClone["text"] = JsonPrimitive(str)
                            val newParts = JsonArray(listOf(JsonObject(partClone)))
                            val contentClone = content.toMutableMap()
                            contentClone["parts"] = newParts
                            val candidateClone = first.toMutableMap()
                            candidateClone["content"] = JsonObject(contentClone)
                            val newCandidates = JsonArray(listOf(JsonObject(candidateClone)))
                            val data = setPath(root, listOf("candidates"), newCandidates)
                            val reasoning = partEl.jsonObject["thought"] != null
                            out += SseParsedChunk(data, str, reasoning, reasoningPresent = true)
                        }
                    }
                }
            }
            return out
        }

        // token / content
        val token = obj["token"] as? JsonPrimitive
        if (token != null && token.isString && token.content.isNotEmpty()) {
            for (ch in token.content) {
                val data = setPath(root, listOf("token"), JsonPrimitive(ch.toString()))
                out += SseParsedChunk(data, ch.toString())
            }
            return out
        }
        val content = obj["content"] as? JsonPrimitive
        val objectType = obj["object"]?.jsonPrimitive?.content
        if (content != null && content.isString && content.content.isNotEmpty() && objectType != "chat.completion.chunk") {
            if ((obj["index"] as? JsonPrimitive)?.content?.toIntOrNull()?.let { it > 0 } == true) throw IllegalStateException("Not a primary swipe")
            for (ch in content.content) {
                val data = setPath(root, listOf("content"), JsonPrimitive(ch.toString()))
                out += SseParsedChunk(data, ch.toString())
            }
            return out
        }

        // choices
        val choices = obj["choices"]?.jsonArray
        if (choices != null) {
            if (choices.isEmpty()) throw IllegalStateException("Not a primary swipe")
            val choice = choices.first().jsonObject
            if ((choice["index"] as? JsonPrimitive)?.content?.toIntOrNull()?.let { it > 0 } == true) throw IllegalStateException("Not a primary swipe")
            val choiceText = choice["text"] as? JsonPrimitive
            if (choiceText != null && choiceText.isString && choiceText.content.isNotEmpty()) {
                for (ch in choiceText.content) {
                    val data = setPath(root, listOf("choices", "0", "text"), JsonPrimitive(ch.toString()))
                    out += SseParsedChunk(data, ch.toString())
                }
                return out
            }
            val choiceThinking = choice["thinking"] as? JsonPrimitive
            if (choiceThinking != null && choiceThinking.isString && choiceThinking.content.isNotEmpty()) {
                for (ch in choiceThinking.content) {
                    val data = setPath(root, listOf("choices", "0", "thinking"), JsonPrimitive(ch.toString()))
                    out += SseParsedChunk(data, ch.toString(), reasoning = true, reasoningPresent = true)
                }
                return out
            }
            val deltaRaw = choice["delta"]
            if (deltaRaw is JsonNull) {
                // JS typeof null === 'object' → 官方会进入 delta 分支并在读 delta.text 时抛 TypeError
                throw IllegalStateException("Cannot read properties of null (reading 'text')")
            }
            val deltaObj = deltaRaw as? JsonObject
            if (deltaObj != null) {
                val dt = deltaObj["text"] as? JsonPrimitive
                if (dt != null && dt.isString && dt.content.isNotEmpty()) {
                    for (ch in dt.content) {
                        val data = setPath(root, listOf("choices", "0", "delta", "text"), JsonPrimitive(ch.toString()))
                        out += SseParsedChunk(data, ch.toString())
                    }
                    return out
                }
                val dr = deltaObj["reasoning_content"] as? JsonPrimitive
                if (dr != null && dr.isString && dr.content.isNotEmpty()) {
                    for ((j, ch) in dr.content.withIndex()) {
                        val data = setPath(root, listOf("choices", "0", "delta", "reasoning_content"), JsonPrimitive(ch.toString()))
                        out += SseParsedChunk(data, ch.toString(), reasoning = true, reasoningPresent = true)
                    }
                    return out
                }
                val dreason = deltaObj["reasoning"] as? JsonPrimitive
                if (dreason != null && dreason.isString && dreason.content.isNotEmpty()) {
                    for (ch in dreason.content) {
                        val data = setPath(root, listOf("choices", "0", "delta", "reasoning"), JsonPrimitive(ch.toString()))
                        out += SseParsedChunk(data, ch.toString(), reasoning = true, reasoningPresent = true)
                    }
                    return out
                }
                val dc = deltaObj["content"] as? JsonPrimitive
                if (dc != null && dc.isString && dc.content.isNotEmpty()) {
                    for (ch in dc.content) {
                        val data = setPath(root, listOf("choices", "0", "delta", "content"), JsonPrimitive(ch.toString()))
                        out += SseParsedChunk(data, ch.toString())
                    }
                    return out
                }
                val dcArray = deltaObj["content"] as? JsonArray
                if (dcArray != null && dcArray.isNotEmpty()) {
                    val thinking = (dcArray.first() as? JsonObject)?.get("thinking") as? JsonArray
                    if (thinking != null && thinking.isNotEmpty()) {
                        val text = (thinking.first() as? JsonObject)?.get("text") as? JsonPrimitive
                        if (text != null && text.isString && text.content.isNotEmpty()) {
                            for (ch in text.content) {
                                val data = setPath(root, listOf("choices", "0", "delta", "content", "0", "thinking", "0", "text"), JsonPrimitive(ch.toString()))
                                out += SseParsedChunk(data, ch.toString(), reasoning = true, reasoningPresent = true)
                            }
                            return out
                        }
                    }
                }
            }
            val message = choice["message"] as? JsonObject
            val mc = message?.get("content") as? JsonPrimitive
            if (mc != null && mc.isString && mc.content.isNotEmpty()) {
                for (ch in mc.content) {
                    val data = setPath(root, listOf("choices", "0", "message", "content"), JsonPrimitive(ch.toString()))
                    out += SseParsedChunk(data, ch.toString())
                }
                return out
            }
        }

        // 对齐官方 parseStreamData：没有命中任何格式分支就抛 Unknown event data format
        // （上游平滑流会 catch 并跳过该事件，等价于不产出文本）
        throw IllegalStateException("Unknown event data format")
    }

    private fun setPath(root: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value
        val key = path.first()
        val current = when (root) {
            is JsonObject -> root[key] ?: JsonNull
            is JsonArray -> key.toIntOrNull()?.let { root.getOrNull(it) } ?: JsonNull
            else -> JsonNull
        }
        val child = setPath(current, path.drop(1), value)
        return when (root) {
            is JsonObject -> {
                val map = root.toMutableMap()
                map[key] = child
                JsonObject(map)
            }
            is JsonArray -> {
                val idx = key.toIntOrNull()
                if (idx != null && idx in root.indices) {
                    val list = root.toMutableList()
                    list[idx] = child
                    JsonArray(list)
                } else root
            }
            else -> child
        }
    }
}
