package com.emberinn.engine.card

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * V3 → V2 归一，对齐官方 src/endpoints/characters.js readFromV2：
 * name/description/personality/scenario/first_mes/mes_example 直接提升；
 * talkativeness/fav 从 data.extensions 读，缺省 0.5/false；tags 提升。
 */
object V2Normalizer {

    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(charJson: String): String {
        val root = json.parseToJsonElement(charJson).jsonObject
        val data = root["data"]?.jsonObject
            ?: return charJson // 没有 data = 已是 V2，原样返回（官方 warn 后 return char）

        val extensions = data["extensions"]?.jsonObject ?: JsonObject(emptyMap())
        val talkativeness = extensions["talkativeness"]?.let { parseDouble(it.toString()) } ?: 0.5
        val fav = extensions["fav"]?.let { parseBool(it.toString()) } ?: false
        val chat = root["chat"]?.let { it.toString() } ?: defaultChatName(data["name"]?.let { it.toString().trim('"') } ?: "")

        return buildJsonObject {
            put("name", data["name"] ?: JsonPrimitive(""))
            put("description", data["description"] ?: JsonPrimitive(""))
            put("personality", data["personality"] ?: JsonPrimitive(""))
            put("scenario", data["scenario"] ?: JsonPrimitive(""))
            put("first_mes", data["first_mes"] ?: JsonPrimitive(""))
            put("mes_example", data["mes_example"] ?: JsonPrimitive(""))
            put("talkativeness", JsonPrimitive(talkativeness))
            put("fav", JsonPrimitive(fav))
            put("tags", data["tags"] ?: kotlinx.serialization.json.JsonArray(emptyList()))
            put("chat", JsonPrimitive(chat))
            root.forEach { (k, v) -> if (k !in setOf("data", "chat")) put(k, v) }
        }.toString()
    }

    /** 官方 legacy convertToV2：YAML/旧字段 → V2 卡（含 spec）。 */
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
    ): String = buildJsonObject {
        put("spec", JsonPrimitive("chara_card_v2"))
        put("spec_version", JsonPrimitive("2.0"))
        put("create_date", JsonPrimitive(createDate))
        put("chat", JsonPrimitive(chat))
        put("data", buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put("personality", JsonPrimitive(personality))
            put("scenario", JsonPrimitive(scenario))
            put("first_mes", JsonPrimitive(firstMes))
            put("mes_example", JsonPrimitive(""))
            put("creator_notes", JsonPrimitive(creatorComment))
            put("talkativeness", JsonPrimitive(talkativeness))
            put("fav", JsonPrimitive(false))
            put("creator", JsonPrimitive(creator))
            put("tags", kotlinx.serialization.json.JsonArray(tags.map { JsonPrimitive(it) }))
        })
    }.toString()

    fun humanizedDateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun defaultChatName(name: String): String =
        if (name.isBlank()) humanizedDateTime() else "$name - ${humanizedDateTime()}"

    private fun parseDouble(s: String): Double? = s.trim('"').toDoubleOrNull()
    private fun parseBool(s: String): Boolean? = when (s.trim('"').lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
