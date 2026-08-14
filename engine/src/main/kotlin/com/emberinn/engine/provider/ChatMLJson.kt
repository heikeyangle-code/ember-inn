package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaInliner
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** CompletionMessage → 官方 ChatML 消息（字符串/数组 content + name/tool_calls/tool_call_id/signature）。 */
fun CompletionMessage.toChatMLJson(mediaQuality: String = "auto"): JsonObject = buildJsonObject {
    put("role", role)
    val hasMedia = media?.isNotEmpty() == true
    put(
        "content",
        if (hasMedia) MediaInliner.inlineOpenAi(JsonPrimitive(content), media.orEmpty(), mediaQuality)
        else JsonPrimitive(content),
    )
    name?.let { put("name", it) }
    toolCallId?.let { put("tool_call_id", it) }
    toolCalls?.let { calls ->
        put("tool_calls", JsonArray(calls.map { call ->
            buildJsonObject {
                put("id", JsonPrimitive(call.id))
                put("type", JsonPrimitive(call.type))
                put("function", buildJsonObject {
                    put("name", JsonPrimitive(call.name))
                    put("arguments", JsonPrimitive(call.arguments))
                })
            }
        }))
    }
    signature?.let { put("signature", it) }
}

/** ChatML JsonObject → CompletionMessage（postProcessPrompt 后回填请求体用）。 */
fun JsonObject.toCompletionMessage(): CompletionMessage? {
    val r = str("role") ?: return null
    val content = when (val c = this["content"]) {
        is JsonPrimitive -> c.contentOrNull ?: ""
        is JsonArray -> c.mapNotNull { part -> (part as? JsonObject)?.str("text") }.joinToString("")
        else -> ""
    }
    val toolCalls = (this["tool_calls"] as? JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val fn = o["function"] as? JsonObject
        ToolCall(
            id = o.str("id") ?: "",
            name = fn?.str("name") ?: "",
            arguments = fn?.str("arguments") ?: "",
            type = o.str("type") ?: "function",
            signature = o.str("signature"),
        )
    }?.ifEmpty { null }
    return CompletionMessage(
        role = r,
        content = content,
        name = name(),
        toolCallId = str("tool_call_id"),
        toolCalls = toolCalls,
        signature = str("signature"),
    )
}

/** JsonObject 小工具（转换器共用）。 */
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

internal fun JsonObject.role(): String = str("role").orEmpty()

internal fun JsonObject.name(): String? = str("name")

internal fun JsonObject.function(): JsonObject? = this["function"] as? JsonObject

internal fun JsonObject.contentParts(): List<JsonObject> =
    (this["content"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()

internal fun JsonObject.set(key: String, value: JsonElement): JsonObject =
    JsonObject(toMutableMap().apply { put(key, value) })

internal fun JsonObject.without(vararg keys: String): JsonObject {
    val m = toMutableMap()
    keys.forEach { m.remove(it) }
    return JsonObject(m)
}
