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

/**
 * 世界书文件（lorebook JSON）：entries（按 uid 键控）+ extensions + name。
 *
 * 扩展字段透传契约（防遗漏，配合 docs/HANDOFF.md「扩展行为待接清单」）：
 * - rawEntries 原样保留整条条目 JSON，extensions 原样保留——官方没有的字段我们不丢，官方有的字段我们也不改。
 * - 以下 4 个字段官方【核心扫描/注入】不消费，本引擎同样不消费，只透传：
 *   · vectorized  —— 已实现：WorldInfoVectorActivation + VectorStore/EmbeddingProvider（对齐 vectors 扩展）
 *   · automationId—— 已实现：WorldInfoAutoExecute + AutoExecuteHandler（对齐 quick-reply AutoExecuteHandler）
 *   · displayIndex—— 已实现：WorldInfoEditorSort（对齐 sortWorldInfoEntries）
 *   · addMemo     —— 官方核心从未读取，仅创建条目/编辑器 UI 使用（无需接入行为）
 */
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
