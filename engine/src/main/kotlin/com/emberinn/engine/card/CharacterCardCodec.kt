package com.emberinn.engine.card

import com.emberinn.engine.card.png.PngChunk
import com.emberinn.engine.card.png.PngChunkCodec
import com.emberinn.engine.card.png.PngText
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * PNG 角色卡读写，逐字节对齐官方 src/character-card-parser.js：
 * - 写入：删除旧 chara/ccv3 tEXt → 插 chara（base64 JSON）→ 插 ccv3（带 spec 的 JSON）→ 均在 IEND 前
 * - 读取：优先 ccv3，其次 chara；base64 → UTF-8 JSON
 * - 导出私有字段清理对齐 src/endpoints/characters.js unsetPrivateFields
 */
object CharacterCardCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun readFromPng(image: ByteArray): String {
        val textChunks = PngChunkCodec.extract(image)
            .filter { it.type == "tEXt" }
            .map { PngText.decode(it.data) }
        val ccv3 = textChunks.firstOrNull { it.first.lowercase() == "ccv3" }
        if (ccv3 != null) return String(Base64.getDecoder().decode(ccv3.second), Charsets.UTF_8)
        val chara = textChunks.firstOrNull { it.first.lowercase() == "chara" }
        if (chara != null) return String(Base64.getDecoder().decode(chara.second), Charsets.UTF_8)
        error("No PNG metadata.")
    }

    fun writeToPng(image: ByteArray, data: String): ByteArray {
        val chunks = PngChunkCodec.extract(image).toMutableList()
        chunks.removeAll { chunk ->
            chunk.type == "tEXt" && PngText.decode(chunk.data).first.lowercase() in setOf("chara", "ccv3")
        }

        val charaPayload = Base64.getEncoder().encodeToString(data.toByteArray(Charsets.UTF_8))
        chunks.add(chunks.size - 1, PngChunk("tEXt", PngText.encode("chara", charaPayload)))

        // 官方：能解析则追加 ccv3；失败静默忽略
        runCatching {
            val v3 = json.parseToJsonElement(data).jsonObject.toMutableMap().apply {
                put("spec", JsonPrimitive("chara_card_v3"))
                put("spec_version", JsonPrimitive("3.0"))
            }
            val v3Payload = Base64.getEncoder().encodeToString(JsonObject(v3).toString().toByteArray(Charsets.UTF_8))
            chunks.add(chunks.size - 1, PngChunk("tEXt", PngText.encode("ccv3", v3Payload)))
        }

        return PngChunkCodec.encode(chunks)
    }

    /** 对齐官方 unsetPrivateFields：fav=false、data.extensions.fav=false、删除 chat。 */
    fun cleanPrivateFields(jsonString: String): String {
        val root = json.parseToJsonElement(jsonString).jsonObject.toMutableMap()
        root["fav"] = JsonPrimitive(false)
        // 官方 lodash get：null 视为缺省（JsonNull.jsonObject 会抛异常，必须先判型）
        val data = (root["data"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val extensions = (data["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        extensions["fav"] = JsonPrimitive(false)
        data["extensions"] = JsonObject(extensions)
        root["data"] = JsonObject(data)
        root.remove("chat")
        return JsonObject(root).toString()
    }
}
