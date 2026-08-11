package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** 官方 openai.js getStreamingReply 的流式状态（reasoning/images/signature/toolSignatures）。 */
data class StreamingState(
    val reasoning: String = "",
    val images: List<String> = emptyList(),
    val signature: String? = null,
    val toolSignatures: Map<String, String> = emptyMap(),
)

data class StreamingChunkResult(
    val text: String = "",
    val state: StreamingState,
)

/**
 * 官方 openai.js getStreamingReply（script 层流式 delta 解析）的引擎移植。
 * 返回纯结果，不修改传入 state。
 */
object StreamingReplyParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun process(
        data: JsonElement?,
        chatCompletionSource: String?,
        showThoughts: Boolean,
        state: StreamingState,
    ): StreamingChunkResult {
        if (data !is JsonObject) {
            return StreamingChunkResult(state = state)
        }

        var reasoning = state.reasoning
        var images = state.images.toMutableList()
        var signature = state.signature
        var toolSignatures = state.toolSignatures.toMutableMap()

        val text = when (chatCompletionSource) {
            "claude" -> {
                if (showThoughts) {
                    reasoning += data["delta"]?.jsonObject?.get("thinking")?.stringOrNull().orEmpty()
                }
                data["delta"]?.jsonObject?.get("text")?.stringOrNull() ?: ""
            }
            "makersuite", "vertexai" -> {
                val parts = data["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                val inlineData = parts.mapNotNull { it.jsonObjectOrNull()?.get("inlineData")?.jsonObjectOrNull() }
                    .filter { it.get("mimeType")?.stringOrNull() != null && it.get("data")?.stringOrNull() != null }
                    .map { "data:${it.get("mimeType")!!.stringOrNull()};base64,${it.get("data")!!.stringOrNull()}" }
                    .filter { it.startsWith("data:") }
                images.addAll(inlineData)
                if (showThoughts) {
                    reasoning += parts.firstOrNull { it.jsonObjectOrNull()?.get("thought")?.booleanOrNull() == true }
                        ?.jsonObjectOrNull()?.get("text")?.stringOrNull().orEmpty()
                }
                parts.forEach { part ->
                    val p = part.jsonObjectOrNull() ?: return@forEach
                    val sig = p.get("thoughtSignature")?.stringOrNull()
                    if (sig != null && p.get("text")?.stringOrNull() != null) {
                        signature = sig
                    }
                }
                parts.firstOrNull { it.jsonObjectOrNull()?.get("thought")?.booleanOrNull() != true }
                    ?.jsonObjectOrNull()?.get("text")?.stringOrNull() ?: ""
            }
            "cohere" -> data["delta"]?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonObjectOrNull()?.get("text")?.stringOrNull()
                ?: data["delta"]?.jsonObject?.get("message")?.jsonObject?.get("tool_plan")?.stringOrNull()
                ?: ""
            "deepseek", "xai" -> {
                if (showThoughts) {
                    reasoning += data["choices"]?.jsonArray.orEmpty()
                        .firstOrNull { it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content") != null }
                        ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content")?.stringOrNull().orEmpty()
                }
                data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()
                    ?.get("delta")?.jsonObjectOrNull()?.get("content")?.stringOrNull() ?: ""
            }
            "openrouter" -> {
                val imageUrls = data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()
                    ?.get("delta")?.jsonObjectOrNull()?.get("images")?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObjectOrNull()?.get("image_url")?.jsonObjectOrNull()?.get("url")?.stringOrNull() }
                    .filter { it.startsWith("data:") }
                images.addAll(imageUrls)
                if (showThoughts) {
                    reasoning += data["choices"]?.jsonArray.orEmpty()
                        .firstOrNull { it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning") != null }
                        ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning")?.stringOrNull()
                        ?: data["choices"]?.jsonArray.orEmpty()
                            .firstOrNull { it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content") != null }
                            ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content")?.stringOrNull()
                        ?: data["choices"]?.jsonArray.orEmpty()
                            .firstOrNull { it.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("reasoning") != null }
                            ?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("reasoning")?.stringOrNull()
                        ?: data["choices"]?.jsonArray.orEmpty()
                            .firstOrNull { it.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("reasoning_content") != null }
                            ?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("reasoning_content")?.stringOrNull()
                        .orEmpty()
                }
                val details = (
                    data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()
                        ?.get("delta")?.jsonObjectOrNull()?.get("reasoning_details")?.jsonArray.orEmpty() +
                        data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()
                            ?.get("message")?.jsonObjectOrNull()?.get("reasoning_details")?.jsonArray.orEmpty()
                    )
                details.forEach { detail ->
                    val d = detail.jsonObjectOrNull() ?: return@forEach
                    if (d.get("type")?.stringOrNull() == "reasoning.encrypted" && d.get("data")?.stringOrNull() != null) {
                        val id = d.get("id")?.stringOrNull() ?: ""
                        val isToolLikeId = id.isNotEmpty() && Regex("^(tool_|call_)").containsMatchIn(id)
                        if (id.isNotEmpty()) toolSignatures[id] = d.get("data")!!.stringOrNull()!!
                        if (!isToolLikeId) signature = d.get("data")!!.stringOrNull()
                    }
                }
                data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("text")?.stringOrNull()
                    ?: ""
            }
            "custom", "pollinations", "aimlapi", "moonshot", "cometapi", "electronhub", "nanogpt", "zai", "siliconflow", "chutes", "workers_ai" -> {
                if (showThoughts) {
                    reasoning += data["choices"]?.jsonArray.orEmpty()
                        .firstOrNull { it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content") != null }
                        ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning_content")?.stringOrNull()
                        ?: data["choices"]?.jsonArray.orEmpty()
                            .firstOrNull { it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning") != null }
                            ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("reasoning")?.stringOrNull()
                        .orEmpty()
                }
                data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("text")?.stringOrNull()
                    ?: ""
            }
            "mistralai" -> {
                if (showThoughts) {
                    reasoning += data["choices"]?.jsonArray.orEmpty()
                        .firstOrNull {
                            it.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")?.jsonArrayOrNull()
                                ?.firstOrNull()?.jsonObjectOrNull()?.get("thinking") != null
                        }
                        ?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")?.jsonArrayOrNull()
                        ?.firstOrNull()?.jsonObjectOrNull()?.get("thinking")?.jsonArrayOrNull()
                        ?.firstOrNull()?.jsonObjectOrNull()?.get("text")?.stringOrNull().orEmpty()
                }
                val content = data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("content")
                    ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("text")
                if (content is JsonArray) {
                    content.mapNotNull { it.jsonObjectOrNull()?.get("text")?.stringOrNull() }
                        .filter { it.isNotEmpty() }
                        .joinToString("")
                } else {
                    content?.stringOrNull() ?: ""
                }
            }
            else -> data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("delta")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                ?: data["choices"]?.jsonArray?.firstOrNull()?.jsonObjectOrNull()?.get("text")?.stringOrNull()
                ?: ""
        }

        return StreamingChunkResult(
            text = text,
            state = StreamingState(
                reasoning = reasoning,
                images = images,
                signature = signature,
                toolSignatures = toolSignatures,
            ),
        )
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
}

/** 官方 openai.js tryParseStreamingError 的纯分类结果。 */
data class StreamingErrorInfo(
    val hasError: Boolean = false,
    val quotaError: Boolean = false,
    val moderationError: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val detail: String? = null,
)

object StreamingErrorParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonNumberRegex = Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$")

    fun parse(decoded: String, statusText: String? = null): StreamingErrorInfo {
        val data = tryParse(decoded) as? JsonObject ?: return StreamingErrorInfo()
        if (data["quota_error"]?.booleanOrNull() == true) {
            return StreamingErrorInfo(hasError = true, quotaError = true)
        }
        val moderationMessage = data["error"]?.jsonObjectOrNull()?.get("message")?.stringOrNull()
        val moderationError = moderationMessage?.contains("requires moderation") == true
        val errorMessage = data["error"]?.jsonObjectOrNull()?.get("message")?.stringOrNull()
            ?: if (data["error"] != null) statusText else null
        val message = data["message"]?.stringOrNull()
        val detail = if (data["detail"] != null) {
            data["detail"]?.jsonObjectOrNull()?.get("error")?.jsonObjectOrNull()?.get("message")?.stringOrNull()
                ?: data["detail"]?.stringOrNull()
                ?: statusText
        } else {
            null
        }
        val hasError = data["error"] != null || data["message"] != null || data["detail"] != null || moderationError
        return StreamingErrorInfo(
            hasError = hasError,
            moderationError = moderationError,
            errorMessage = errorMessage,
            message = message,
            detail = detail,
        )
    }

    private fun tryParse(value: String): JsonElement? {
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

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
}
