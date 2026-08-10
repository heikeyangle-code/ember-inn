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

    /** 官方 extension_settings.disabledExtensions 的 regex 开关（默认启用）。 */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun saveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .apply()

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    /** 官方 regex 扩展 character_allowed_regex：允许该卡正则的角色头像名列表（空 = 全部禁用该卡正则）。 */
    fun characterAllowedRegex(context: Context): List<String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("character_allowed_regex", "[]") ?: "[]"
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList())
    }

    fun saveCharacterAllowed(context: Context, avatars: List<String>) {
        val arr = JsonArray(avatars.map { JsonPrimitive(it) })
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("character_allowed_regex", arr.toString())
            .apply()

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    // ---- 预设正则（官方 preset 扩展：预设文件的 regex_scripts 扩展字段；App 用命名预设集模拟）----

    fun presetSets(context: Context): Map<String, List<RegexPipelineScript>> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("preset_sets", "{}") ?: "{}"
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) ->
                (v as? JsonArray)?.mapNotNull { el -> runCatching { decodeScript(el.jsonObject) }.getOrNull() } ?: emptyList()
            }
        }.getOrDefault(emptyMap())
    }

    fun savePresetSets(context: Context, sets: Map<String, List<RegexPipelineScript>>) {
        val obj = JsonObject(sets.mapValues { (_, v) -> JsonArray(v.map { encodeScript(it) }) })
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("preset_sets", obj.toString())
            .apply()

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    fun activePresetSet(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("active_preset_set", "") ?: ""

    fun saveActivePresetSet(context: Context, name: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("active_preset_set", name)
            .apply()

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    /** 官方 preset_allowed_regex[apiId]：允许生效的预设集名列表（App 固定 api=openai）。 */
    fun presetAllowed(context: Context, api: String): List<String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("preset_allowed_regex", "{}") ?: "{}"
        return runCatching {
            json.parseToJsonElement(raw).jsonObject[api]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun savePresetAllowed(context: Context, api: String, names: List<String>) {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("preset_allowed_regex", "{}") ?: "{}"
        val map = runCatching { json.parseToJsonElement(raw).jsonObject.toMutableMap() }.getOrDefault(mutableMapOf())
        map[api] = JsonArray(names.map { JsonPrimitive(it) })
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("preset_allowed_regex", JsonObject(map).toString())
            .apply()

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    private fun decodeScript(o: JsonObject): RegexPipelineScript = RegexPipelineScript(
        scriptName = o["scriptName"]?.jsonPrimitive?.contentOrNull ?: "",
        findRegex = o["findRegex"]?.jsonPrimitive?.contentOrNull ?: "",
        replaceString = o["replaceString"]?.jsonPrimitive?.contentOrNull ?: "",
        trimStrings = (o["trimStrings"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        disabled = o["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
        substituteRegex = o["substituteRegex"]?.jsonPrimitive?.intOrNull ?: 0,
        placement = (o["placement"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(1, 2, 5, 6),
        markdownOnly = o["markdownOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
        promptOnly = o["promptOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
        runOnEdit = o["runOnEdit"]?.jsonPrimitive?.booleanOrNull ?: true,
        minDepth = o["minDepth"]?.jsonPrimitive?.intOrNull,
        maxDepth = o["maxDepth"]?.jsonPrimitive?.intOrNull,
    )

    private fun encodeScript(s: RegexPipelineScript): JsonObject = buildJsonObject {
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

        com.emberinn.app.data.DisplayCacheVersion.bump()
    }
}
