package com.emberinn.engine.card

import com.emberinn.engine.expression.SpriteStorage
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON 角色卡导入，对齐官方 characters.js importFromJson：
 * V2/V3（spec）→ Risu 精灵抽取 + 私有字段清理 + sanitize + readFromV2 + create_date；
 * V1（name）与 Gradio（char_name）→ 旧字段映射 + convertToV2。
 */
object JsonImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun import(
        data: ByteArray,
        now: String = Instant.now().toString(),
        chatNow: String = V2Normalizer.humanizedDateTime(),
    ): String {
        val text = String(data, Charsets.UTF_8)
        val root = json.parseToJsonElement(text).jsonObject

        return when {
            root["spec"] != null -> importSpec(text, root, now, chatNow)
            root["name"] != null -> importV1(root, now, chatNow)
            root["char_name"] != null -> importGradio(root, now, chatNow)
            else -> ""
        }
    }

    private fun importSpec(text: String, root: JsonObject, now: String, chatNow: String = V2Normalizer.humanizedDateTime()): String {
        // 官方 importRisuSprites 会删除 additionalAssets/emotions
        val risu = SpriteStorage.extractRisuSprites(text)
        val afterRisu = risu?.data?.toString() ?: text
        val afterClean = CharacterCardCodec.cleanPrivateFields(afterRisu)
        val card = Json.parseToJsonElement(afterClean).jsonObject.toMutableMap()
        val data = (card["data"] as? JsonObject)?.toMutableMap()
        val dataName = data?.get("name")?.jsonPrimitive?.contentOrNull
        if (data != null && !dataName.isNullOrEmpty()) {
            data["name"] = JsonPrimitive(CardSanitize.sanitizeName(dataName))
            card["data"] = JsonObject(data)
        }
        val rootName = dataName?.takeIf { it.isNotEmpty() }
            ?: card["name"]?.jsonPrimitive?.contentOrNull ?: ""
        card["name"] = JsonPrimitive(CardSanitize.sanitizeName(rootName))
        val normalized = V2Normalizer.normalize(JsonObject(card).toString(), now = chatNow)
        val result = Json.parseToJsonElement(normalized).jsonObject.toMutableMap()
        result["create_date"] = JsonPrimitive(now)
        return JsonObject(result).toString()
    }

    private fun importV1(root: JsonObject, now: String, chatNow: String): String {
        val name = CardSanitize.sanitizeName(root["name"]?.jsonPrimitive?.contentOrNull ?: "")
        val creatorNotes = (root["creator_notes"]?.jsonPrimitive?.contentOrNull ?: "")
            .replace("Creator's notes go here.", "")
        val creatorComment = root["creatorcomment"]?.jsonPrimitive?.contentOrNull ?: creatorNotes
        return V2Normalizer.buildV2FromLegacy(
            name = name,
            description = root["description"]?.jsonPrimitive?.contentOrNull ?: "",
            firstMes = root["first_mes"]?.jsonPrimitive?.contentOrNull ?: "",
            mesExample = root["mes_example"]?.jsonPrimitive?.contentOrNull ?: "",
            createDate = now,
            chat = "$name - $chatNow",
            creatorComment = creatorComment,
            includeRootCreator = true,
            personality = root["personality"]?.jsonPrimitive?.contentOrNull ?: "",
            scenario = root["scenario"]?.jsonPrimitive?.contentOrNull ?: "",
            talkativeness = root["talkativeness"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5,
            creator = root["creator"]?.jsonPrimitive?.contentOrNull ?: "",
            tags = parseTags(root["tags"]),
        )
    }

    private fun importGradio(root: JsonObject, now: String, chatNow: String): String {
        val name = CardSanitize.sanitizeName(root["char_name"]?.jsonPrimitive?.contentOrNull ?: "")
        val creatorNotes = (root["creator_notes"]?.jsonPrimitive?.contentOrNull ?: "")
            .replace("Creator's notes go here.", "")
        val creatorComment = root["creatorcomment"]?.jsonPrimitive?.contentOrNull ?: creatorNotes
        val chatName = root["name"]?.jsonPrimitive?.contentOrNull ?: "undefined"
        return V2Normalizer.buildV2FromLegacy(
            name = name,
            description = root["char_persona"]?.jsonPrimitive?.contentOrNull ?: "",
            firstMes = root["char_greeting"]?.jsonPrimitive?.contentOrNull ?: "",
            mesExample = root["example_dialogue"]?.jsonPrimitive?.contentOrNull ?: "",
            createDate = now,
            chat = "$chatName - $chatNow",
            creatorComment = creatorComment,
            includeRootCreator = true,
            scenario = root["world_scenario"]?.jsonPrimitive?.contentOrNull ?: "",
            talkativeness = root["talkativeness"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5,
            creator = root["creator"]?.jsonPrimitive?.contentOrNull ?: "",
            tags = parseTags(root["tags"]),
        )
    }

    private fun parseTags(el: JsonElement?): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { it.jsonPrimitive.contentOrNull }
        is JsonPrimitive -> el.content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}
