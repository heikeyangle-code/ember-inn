package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 世界书文件（lorebook JSON）：entries（按 uid 键控）+ extensions + name。 */
data class WorldInfoFile(
    val name: String,
    val extensions: JsonObject = JsonObject(emptyMap()),
    val entries: List<WorldInfoEntry> = emptyList(),
    val rawEntries: Map<String, JsonObject> = emptyMap(),
)

/**
 * 世界书文件编解码，对齐官方：
 * - 文件 = JSON.stringify({entries, extensions, name?}, null, 4)
 * - entries 为按 uid 键控的对象（前端格式）
 * - 解析用 WorldBookEntryParser 生成扫描用条目
 */
object WorldInfoFileCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { ignoreUnknownKeys = true; prettyPrint = true; prettyPrintIndent = "    " }

    fun parse(text: String): WorldInfoFile {
        val root = json.parseToJsonElement(text).jsonObject
        val name = str(root["name"]) ?: ""
        val extensions = root["extensions"]?.jsonObject ?: JsonObject(emptyMap())
        val rawEntries = linkedMapOf<String, JsonObject>()
        val entries = mutableListOf<WorldInfoEntry>()

        when (val entriesEl = root["entries"]) {
            is JsonObject -> entriesEl.forEach { (uid, v) ->
                val obj = v.jsonObject
                rawEntries[uid] = obj
                entries += WorldBookEntryParser.parse(obj, name, uid.toIntOrNull() ?: rawEntries.size)
            }
            is JsonArray -> entriesEl.forEachIndexed { idx, v ->
                val obj = v.jsonObject
                val uid = obj["uid"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                    ?: idx.toString()
                rawEntries[uid] = obj
                entries += WorldBookEntryParser.parse(obj, name, idx)
            }
            else -> {}
        }

        return WorldInfoFile(name = name, extensions = extensions, entries = entries, rawEntries = rawEntries)
    }

    fun serialize(file: WorldInfoFile): String {
        val root = buildJsonObject {
            put("entries", JsonObject(file.rawEntries))
            put("extensions", file.extensions)
            if (file.name.isNotEmpty()) put("name", JsonPrimitive(file.name))
        }
        return pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    private fun str(el: JsonElement?): String? =
        el?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
}
