package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 角色详情页可编辑字段快照（官方 v2 归一字段；tags 逗号拼接、depth_prompt 兼容对象/字符串、talkativeness 读 extensions）。 */
data class CharacterDetailFields(
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val mesExample: String,
    val systemPrompt: String,
    val postHistoryInstructions: String,
    val creatorNotes: String,
    val creator: String,
    val characterVersion: String,
    val tags: String,
    val depthPrompt: String,
    val depthPromptDepth: String,
    val depthPromptRole: String,
    val talkativeness: Float,
    val alternateGreetings: List<String>,
)

/** 世界书条目编辑草稿（字段对齐官方 v2DataWorldInfoEntry 常用项）。 */
data class WorldEntryDraft(
    val id: Int,
    val keys: String,
    val content: String,
    val comment: String,
    val constant: Boolean,
    val selective: Boolean,
    val enabled: Boolean,
    val insertionOrder: Int,
)

/** 该卡正则脚本（对齐官方 char-data.js RegexScriptData；缺失字段用官方默认）。 */
data class CharacterRegexScript(
    val id: String,
    val scriptName: String,
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val placement: List<Int> = listOf(1, 2, 5, 6),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = true,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val substituteRegex: Int = 0,
)


    /** 读取该卡变量（README 自定义扩展，data.extensions.emberinn_variables，字符串值）。 */
    fun readVariables(raw: String): Map<String, String> = runCatching {
        val data = dataLayer(json.parseToJsonElement(raw).jsonObject)
        val ext = data["extensions"]?.jsonObject ?: return@runCatching emptyMap()
        (ext["emberinn_variables"] as? JsonObject)?.mapNotNull { (k, v) ->
            val value = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to value
        }?.toMap() ?: emptyMap()
    }.getOrDefault(emptyMap())

    /** 保存该卡变量：JSON 对象，字符串值。 */
    fun applyVariables(raw: String, variables: Map<String, String>): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (variables.isEmpty()) {
            ext.remove("emberinn_variables")
        } else {
            ext["emberinn_variables"] = JsonObject(
                variables.filterValues { it.isNotEmpty() }.mapValues { (_, v) -> JsonPrimitive(v) },
            )
        }
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }

    /** 世界书官方位置是 data.character_book；兼容历史卡把 character_book 放在根部的写法。 */
    private fun bookOf(root: JsonObject, data: JsonObject): JsonObject? =
        data["character_book"]?.jsonObject ?: root["character_book"]?.jsonObject

    /** data 层改写并落回整卡；V2 卡同步 readFromV2 会提升到根部的字段，V1 卡整卡即 data。 */
    private fun updateData(raw: String, transform: (JsonObject) -> JsonObject): String {
        val root = json.parseToJsonElement(raw).jsonObject.toMutableMap()
        val hadData = root["data"] is JsonObject
        val newData = transform(dataLayer(JsonObject(root)))
        if (hadData) {
            root["data"] = newData
            mirrorRootFields(root, newData)
        } else {
            root.clear()
            root.putAll(newData)
        }
        return JsonObject(root).toString()
    }

    /** 对齐官方 readFromV2 的 fieldMappings：data 有值才覆盖根字段；talkativeness/fav 从 extensions 提升。 */
    private fun mirrorRootFields(root: MutableMap<String, JsonElement>, data: JsonObject) {
        listOf("name", "description", "personality", "scenario", "first_mes", "mes_example", "tags")
            .forEach { key ->
                val v = data[key]
                if (v != null) root[key] = v else root.remove(key)
            }
        val ext = data["extensions"]?.jsonObject
        val talk = ext?.get("talkativeness") ?: data["talkativeness"]
        if (talk != null) root["talkativeness"] = talk else root.remove("talkativeness")
        val fav = ext?.get("fav") ?: data["fav"]
        if (fav != null) root["fav"] = fav else root.remove("fav")
    }
}
