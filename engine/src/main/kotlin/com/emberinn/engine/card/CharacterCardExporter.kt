package com.emberinn.engine.card

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON 导出：对齐官方 /api/characters/export json
 * （getCharaCardV2 → unsetPrivateFields → JSON.stringify(_, null, 4)）。
 */
object CharacterCardExporter {

    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { ignoreUnknownKeys = true; prettyPrint = true; prettyPrintIndent = "    " }

    fun exportToV2Json(jsonString: String): String {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val normalized = if (root["spec"] == null) legacyToV2(root) else V2Normalizer.normalize(jsonString)
        val cleaned = CharacterCardCodec.cleanPrivateFields(normalized)
        val element = json.parseToJsonElement(cleaned)
        return pretty.encodeToString(JsonElement.serializer(), element)
    }

    /** 旧版无 spec 卡 → charaFormatData 主字段映射（深度提示等边缘字段后续补）。 */
    private fun legacyToV2(root: JsonObject): String {
        val name = root["name"]?.jsonPrimitive?.contentOrNull() ?: ""
        val tags = root["tags"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) it.mapNotNull { e -> e.jsonPrimitive.contentOrNull() }
            else it.jsonPrimitive.contentOrNull()?.split(',')?.map { t -> t.trim() }?.filter { t -> t.isNotEmpty() } ?: emptyList()
        } ?: emptyList()
        val now = V2Normalizer.humanizedDateTime()
        return V2Normalizer.buildV2FromLegacy(
            name = name,
            description = root["description"]?.jsonPrimitive?.contentOrNull() ?: "",
            firstMes = root["first_mes"]?.jsonPrimitive?.contentOrNull() ?: "",
            createDate = root["create_date"]?.jsonPrimitive?.contentOrNull() ?: now,
            chat = root["chat"]?.jsonPrimitive?.contentOrNull() ?: "$name - $now",
            creatorComment = root["creatorcomment"]?.jsonPrimitive?.contentOrNull() ?: "",
            personality = root["personality"]?.jsonPrimitive?.contentOrNull() ?: "",
            scenario = root["scenario"]?.jsonPrimitive?.contentOrNull() ?: "",
            talkativeness = root["talkativeness"]?.jsonPrimitive?.let { it.contentOrNull()?.toDoubleOrNull() } ?: 0.5,
            creator = root["creator"]?.jsonPrimitive?.contentOrNull() ?: "",
            tags = tags,
            systemPrompt = root["system_prompt"]?.jsonPrimitive?.contentOrNull() ?: "",
            postHistoryInstructions = root["post_history_instructions"]?.jsonPrimitive?.contentOrNull() ?: "",
            characterVersion = root["character_version"]?.jsonPrimitive?.contentOrNull() ?: "",
            alternateGreetings = root["alternate_greetings"]?.let {
                if (it is kotlinx.serialization.json.JsonArray) it.mapNotNull { e -> e.jsonPrimitive.contentOrNull() }
                else listOfNotNull(it.jsonPrimitive.contentOrNull())
            } ?: emptyList(),
            fav = root["fav"]?.jsonPrimitive?.let { it.contentOrNull() == "true" } ?: false,
        )
    }
}
