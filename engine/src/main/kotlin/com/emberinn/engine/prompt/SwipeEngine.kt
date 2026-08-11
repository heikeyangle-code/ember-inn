package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 官方 script.js 滑动切回复/自动过滤纯逻辑：
 * isSwipingAllowed / isMessageSwipeable / getOverswipeBehavior / ensureSwipes /
 * generatedTextFiltered（power-user.js）/ extractMultiSwipes。
 */
data class SwipeMessage(
    val mes: String = "",
    val isUser: Boolean = false,
    val isSystem: Boolean = false,
    val swipes: List<String>? = null,
    val swipeId: Int? = null,
    val swipeInfo: List<JsonObject>? = null,
    val isSmallSys: Boolean = false,
    val swipeable: Boolean? = null,
    val overswipeBehavior: String? = null,
)

data class EnsureSwipesResult(
    val updated: Boolean,
    val message: SwipeMessage,
)

data class OverswipeBehavior(
    val none: String = "none",
    val pristineGreeting: String = "pristine_greeting",
    val regenerate: String = "regenerate",
    val loop: String = "loop",
)

object SwipeEngine {

    fun isSwipingAllowed(
        chatLength: Int,
        swipesEnabled: Boolean,
        swipesHidden: Boolean,
        isGenerating: Boolean,
        midSwipe: Boolean,
    ): Boolean =
        chatLength != 0 &&
            swipesEnabled && !swipesHidden &&
            !isGenerating &&
            !midSwipe

    fun isMessageSwipeable(
        messageId: Int,
        chatLength: Int,
        thisEditMesId: Int?,
        editing: Boolean,
        message: SwipeMessage,
    ): Boolean {
        val allowed = (messageId > (thisEditMesId ?: -1)) && !editing
        return allowed &&
            messageId == chatLength - 1 &&
            !message.isSmallSys &&
            message.swipeable != false &&
            !message.isUser
    }

    fun getOverswipeBehavior(
        messageId: Int,
        message: SwipeMessage,
        chatTainted: Boolean,
    ): String {
        if (!message.overswipeBehavior.isNullOrEmpty()) return message.overswipeBehavior
        if (message.swipeable == false) return OverswipeBehavior().none
        if (message.isSmallSys) return OverswipeBehavior().none
        if (messageId == 0 && !chatTainted) return OverswipeBehavior().pristineGreeting
        if (!message.isUser && !message.isSystem) return OverswipeBehavior().regenerate
        return OverswipeBehavior().loop
    }

    fun ensureSwipes(message: SwipeMessage): EnsureSwipesResult {
        var updated = false
        if (message.isUser || message.isSmallSys) {
            return EnsureSwipesResult(updated, message)
        }
        var swipes = message.swipes
        if (swipes == null) {
            swipes = listOf(message.mes)
            updated = true
        }
        var swipeId = message.swipeId
        if (swipeId == null) {
            swipeId = 0
            updated = true
        }
        var swipeInfo = message.swipeInfo
        if (swipeInfo == null) {
            swipeInfo = swipes.map { JsonObject(emptyMap()) }
            updated = true
        }
        val normalizedSwipes = swipes.mapIndexed { i, s ->
            if (s is String) s else {
                updated = true
                ""
            }
        }
        val normalizedInfo = swipeInfo.mapIndexed { i, info ->
            if (info is JsonObject) info else {
                updated = true
                JsonObject(emptyMap())
            }
        }
        return EnsureSwipesResult(
            updated = updated,
            message = message.copy(
                swipes = normalizedSwipes,
                swipeId = swipeId,
                swipeInfo = normalizedInfo,
            ),
        )
    }

    fun generatedTextFiltered(
        text: String,
        minimumLength: Int = 0,
        blacklist: List<String> = emptyList(),
        threshold: Int = 0,
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            if (minimumLength > 0 && trimmed.length < minimumLength) return true
            if (blacklist.isNotEmpty() && threshold > 0) {
                if (containsBlacklistedWords(trimmed, blacklist, threshold)) return true
            }
        }
        return false
    }

    fun extractMultiSwipes(
        data: JsonElement?,
        type: String,
        mainApi: String,
        textgenType: String?,
        cleanUpConfig: CleanUpConfig,
    ): List<String> {
        if (data == null) return emptyList()
        if (type == "continue" || type == "impersonate" || type == "quiet") return emptyList()

        val swipes = mutableListOf<String>()
        if (mainApi == "textgenerationwebui" && textgenType == "llamacpp") {
            val array = data as? JsonArray ?: return emptyList()
            val count = array.size - 1
            if (count <= 0) return emptyList()
            for (i in 1..count) {
                swipes += array.getOrNull(i)?.jsonObjectOrNull()?.get("content")?.stringOrNull() ?: ""
            }
        }

        if (mainApi == "openai" || (mainApi == "textgenerationwebui" && listOf("mancer", "vllm", "aphrodite", "tabby", "infermaticai").contains(textgenType))) {
            val choices = (data as? JsonObject)?.get("choices")?.jsonArrayOrNull() ?: return swipes
            val count = choices.size - 1
            if (count <= 0) return swipes
            for (i in 1..count) {
                val choice = choices.getOrNull(i)?.jsonObjectOrNull() ?: continue
                swipes += choice.get("message")?.jsonObjectOrNull()?.get("content")?.stringOrNull()
                    ?: choice.get("text")?.stringOrNull()
                    ?: ""
            }
        }

        return swipes.map { text ->
            CleanUpMessageEngine.clean(
                getMessage = text,
                config = cleanUpConfig,
            )
        }
    }

    private fun containsBlacklistedWords(text: String, blacklist: List<String>, threshold: Int): Boolean {
        val pattern = blacklist.joinToString("|")
        val regex = Regex("\\b($pattern)\\b", RegexOption.IGNORE_CASE)
        return regex.findAll(text).count() >= threshold
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
