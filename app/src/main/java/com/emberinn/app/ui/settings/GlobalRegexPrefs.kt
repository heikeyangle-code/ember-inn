package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.engine.regex.RegexPipelineScript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 全局正则脚本（对齐官方 regex 扩展 GLOBAL 分桶：extension_settings.regex）。 */
object GlobalRegexPrefs {

    private const val NAME = "ember_regex_global"
    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context): List<RegexPipelineScript> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("scripts", "[]") ?: "[]"
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                RegexPipelineScript(
                    scriptName = o["scriptName"]?.jsonPrimitive?.contentOrNull ?: "",
                    findRegex = o["findRegex"]?.jsonPrimitive?.contentOrNull ?: "",
                    replaceString = o["replaceString"]?.jsonPrimitive?.contentOrNull ?: "",
                    trimStrings = (o["trimStrings"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    disabled = o["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                    substituteRegex = o["substituteRegex"]?.jsonPrimitive?.intOrNull ?: 0,
                    placement = (o["placement"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(1, 2, 5, 6),
                    markdownOnly = o["markdownOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
                    promptOnly = o["promptOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
                    runOnEdit = o["runOnEdit"]?.jsonPrimitive?.booleanOrNull ?: true,
                    minDepth = o["minDepth"]?.jsonPrimitive?.intOrNull,
                    maxDepth = o["maxDepth"]?.jsonPrimitive?.intOrNull,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, scripts: List<RegexPipelineScript>) {
        val arr = JsonArray(scripts.map { s ->
            buildJsonObject {
                put("scriptName", JsonPrimitive(s.scriptName))
                put("findRegex", JsonPrimitive(s.findRegex))
                put("replaceString", JsonPrimitive(s.replaceString))
                put("trimStrings", JsonArray(s.trimStrings.map { JsonPrimitive(it) }))
                put("disabled", JsonPrimitive(s.disabled))
                put("substituteRegex", JsonPrimitive(s.substituteRegex))
                put("placement", JsonArray(s.placement.map { JsonPrimitive(it) }))
                put("markdownOnly", JsonPrimitive(s.markdownOnly))
                put("promptOnly", JsonPrimitive(s.promptOnly))
                put("runOnEdit", JsonPrimitive(s.runOnEdit))
                s.minDepth?.let { put("minDepth", JsonPrimitive(it)) }
                s.maxDepth?.let { put("maxDepth", JsonPrimitive(it)) }
            }
        })
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("scripts", arr.toString())
            .apply()
    }
}
