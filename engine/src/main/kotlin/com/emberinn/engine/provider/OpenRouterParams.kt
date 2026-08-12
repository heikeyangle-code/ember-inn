package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 官方后端 chat-completions.js 的 OpenRouter body 参数：
 * getOpenRouterTransforms（middleout on→['middle-out']/off→[]/auto→undefined）、
 * getOpenRouterPlugins（enable_web_search→[{id:'web'}]）与 reasoning.exclude/effort。
 */
object OpenRouterParams {

    fun transforms(middleout: String): JsonElement? = when (middleout) {
        "on" -> JsonArray(listOf(JsonPrimitive("middle-out")))
        "off" -> JsonArray(emptyList())
        else -> null
    }

    fun plugins(enableWebSearch: Boolean): JsonArray = if (enableWebSearch) {
        JsonArray(listOf(buildJsonObject { put("id", JsonPrimitive("web")) }))
    } else {
        JsonArray(emptyList())
    }

    fun extra(
        middleout: String,
        enableWebSearch: Boolean,
        includeReasoning: Boolean,
        reasoningEffort: String,
    ): JsonObject = buildJsonObject {
        transforms(middleout)?.let { put("transforms", it) }
        put("plugins", plugins(enableWebSearch))
        put(
            "reasoning",
            buildJsonObject {
                put("exclude", JsonPrimitive(!includeReasoning))
                if (reasoningEffort.isNotEmpty()) put("effort", JsonPrimitive(reasoningEffort))
            },
        )
    }
}
