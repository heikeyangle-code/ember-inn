package com.emberinn.engine.card

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * V3 → V2 归一，逐字段对齐官方 src/endpoints/characters.js readFromV2：
 * - data 字段提升到根（name/description/personality/scenario/first_mes/mes_example/tags）
 * - talkativeness/fav 从 data.extensions 读，缺省 0.5/false
 * - json_data 删除、data 保留、chat 缺省补「名字 - 时间」
 */
object V2Normalizer {

    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(charJson: String, now: String = humanizedDateTime()): String {
        val root = json.parseToJsonElement(charJson).jsonObject.toMutableMap()
        val data = root["data"]?.jsonObject
            ?: return charJson // 没有 data = 已是 V2（官方 warn 后原样返回）

        root.remove("json_data")

        val extensions = data["extensions"]?.jsonObject ?: JsonObject(emptyMap())
        // 官方 readFromV2：talkativeness/fav 原值透传（不转换类型）；
        // 缺失时官方 defaultValue 回填会被随后的 char[field]=v2Value(undefined) 覆盖 → 实际不写入，差分已证实
        extensions["talkativeness"]?.let { root["talkativeness"] = it }
        extensions["fav"]?.let { root["fav"] = it }

        // 官方 fieldMappings：data 有值才覆盖
        listOf("name", "description", "personality", "scenario", "first_mes", "mes_example", "tags")
            .forEach { field -> data[field]?.let { root[field] = it } }

        // 官方：char.chat = char.chat ?? `${char.name} - ${humanizedDateTime()}`
        if (root["chat"] == null) {
            val name = data["name"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
            root["chat"] = JsonPrimitive(defaultChatName(name, now))
        }

        return JsonObject(root).toString()
    }

    /** 官方 charaFormatData + convertToV2：YAML/旧字段 → V2 卡（含 depth_prompt）。 */
    fun buildV2FromLegacy(
        name: String,
        description: String,
        firstMes: String,
        createDate: String,
        chat: String,
        creatorComment: String = "",
        personality: String = "",
        scenario: String = "",
        talkativeness: Double = 0.5,
        creator: String = "",
        tags: List<String> = emptyList(),
        systemPrompt: String = "",
        postHistoryInstructions: String = "",
        characterVersion: String = "",
        alternateGreetings: List<String> = emptyList(),
        world: String = "",
        fav: Boolean = false,
        depthPromptPrompt: String = "",
        depthPromptDepth: Int = 4,
        depthPromptRole: String = "system",
    ): String = buildJsonObject {
        put("spec", JsonPrimitive("chara_card_v2"))
        put("spec_version", JsonPrimitive("2.0"))
        put("create_date", JsonPrimitive(createDate))
        put("chat", JsonPrimitive(chat))
        put("name", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        put("personality", JsonPrimitive(personality))
        put("scenario", JsonPrimitive(scenario))
        put("first_mes", JsonPrimitive(firstMes))
        put("mes_example", JsonPrimitive(""))
        put("creatorcomment", JsonPrimitive(creatorComment))
        put("avatar", JsonPrimitive("none"))
        put("talkativeness", JsonPrimitive(talkativeness))
        put("fav", JsonPrimitive(fav))
        put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
        put("data", buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put("personality", JsonPrimitive(personality))
            put("scenario", JsonPrimitive(scenario))
            put("first_mes", JsonPrimitive(firstMes))
            put("mes_example", JsonPrimitive(""))
            put("creator_notes", JsonPrimitive(creatorComment))
            put("system_prompt", JsonPrimitive(systemPrompt))
            put("post_history_instructions", JsonPrimitive(postHistoryInstructions))
            put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
            put("creator", JsonPrimitive(creator))
            put("character_version", JsonPrimitive(characterVersion))
            put("alternate_greetings", JsonArray(alternateGreetings.map { JsonPrimitive(it) }))
            put("extensions", buildJsonObject {
                put("talkativeness", JsonPrimitive(talkativeness))
                put("fav", JsonPrimitive(fav))
                put("world", JsonPrimitive(world))
                put("depth_prompt", buildJsonObject {
                    put("prompt", JsonPrimitive(depthPromptPrompt))
                    put("depth", JsonPrimitive(depthPromptDepth))
                    put("role", JsonPrimitive(depthPromptRole))
                })
            })
        })
    }.toString()

    /** 对齐官方 util.js humanizedDateTime：YYYY-MM-DD@HHhMMmSSsMSms（毫秒 3 位，其余 2 位）。 */
    fun humanizedDateTime(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
        val dt = now
        fun pad(v: Int, len: Int) = v.toString().padStart(len, '0')
        return "${pad(dt.year, 4)}-${pad(dt.monthValue, 2)}-${pad(dt.dayOfMonth, 2)}@" +
            "${pad(dt.hour, 2)}h${pad(dt.minute, 2)}m${pad(dt.second, 2)}s${pad(dt.nano / 1_000_000, 3)}ms"
    }

    fun defaultChatName(name: String, now: String = humanizedDateTime()): String =
        if (name.isBlank()) now else "$name - $now"

    private fun parseDouble(el: JsonElement): Double =
        el.jsonPrimitive.content.toDoubleOrNull() ?: 0.5

    private fun parseBool(el: JsonElement): Boolean =
        el.jsonPrimitive.let { p ->
            p.booleanOrNull ?: (p.content.lowercase() == "true")
        }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (isString) content else null
}
