package com.emberinn.engine.expression

import com.emberinn.engine.card.CardSanitize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** RisuAI 精灵（label + base64 PNG）。 */
data class RisuSprite(val label: String, val base64: String)

/** RisuAI 导入结果：实际写入的精灵 + 删除 additionalAssets/emotions 后的角色卡。 */
data class RisuExtraction(
    val written: List<RisuSprite>,
    val data: JsonObject,
)

/**
 * 对齐 sprites.js：getSpritesPath（sanitize + 子目录）+ importRisuSprites（提取/去重/删除字段）。
 * 文件系统写入由 App/存储层负责。
 */
object SpriteStorage {

    private val json = Json { ignoreUnknownKeys = true }

    fun spritesPath(charactersRoot: String, name: String, isSubfolder: Boolean): String? {
        if (isSubfolder) {
            val parts = name.split('/')
            val characterName = CardSanitize.sanitizeName(parts.getOrNull(0) ?: "")
            val subfolderName = CardSanitize.sanitizeName(parts.getOrNull(1) ?: "")
            if (characterName.isEmpty() || subfolderName.isEmpty()) return null
            return listOf(charactersRoot, characterName, subfolderName).filter { it.isNotEmpty() }.joinToString("/")
        }
        val safe = CardSanitize.sanitizeName(name)
        if (safe.isEmpty()) return null
        return listOf(charactersRoot, safe).filter { it.isNotEmpty() }.joinToString("/")
    }

    fun extractRisuSprites(
        dataJson: String,
        charactersRoot: String = "",
        existingLabels: Set<String> = emptySet(),
    ): RisuExtraction? {
        val root = json.parseToJsonElement(dataJson).jsonObject
        val data = root["data"] as? JsonObject ?: return RisuExtraction(emptyList(), root)
        val name = data["name"]?.jsonPrimitive?.content ?: return RisuExtraction(emptyList(), root)
        val risu = (data["extensions"] as? JsonObject)?.get("risuai") as? JsonObject ?: return RisuExtraction(emptyList(), root)
        val path = spritesPath(charactersRoot, name, false) ?: return RisuExtraction(emptyList(), root)

        val images = mutableListOf<RisuSprite>()
        (risu["additionalAssets"] as? JsonArray)?.forEach { el ->
            val arr = el.jsonArray
            if (arr.size >= 2) images += RisuSprite(arr[0].jsonPrimitive.content, arr[1].jsonPrimitive.content)
        }
        (risu["emotions"] as? JsonArray)?.forEach { el ->
            val arr = el.jsonArray
            if (arr.size >= 2) images += RisuSprite(arr[0].jsonPrimitive.content, arr[1].jsonPrimitive.content)
        }
        if (images.isEmpty()) return RisuExtraction(emptyList(), root)

        val written = mutableListOf<RisuSprite>()
        val seen = existingLabels.toMutableSet()
        for (sprite in images) {
            if (sprite.label in seen) continue
            seen += sprite.label
            written += sprite
        }

        // 官方 importRisuSprites 删除 additionalAssets/emotions（保留其它 risuai 字段）
        val risuMutable = risu.toMutableMap()
        risuMutable.remove("additionalAssets")
        risuMutable.remove("emotions")
        val extensions = data["extensions"]!!.jsonObject.toMutableMap()
        extensions["risuai"] = JsonObject(risuMutable)
        val dataMutable = data.toMutableMap()
        dataMutable["extensions"] = JsonObject(extensions)
        val rootMutable = root.toMutableMap()
        rootMutable["data"] = JsonObject(dataMutable)
        return RisuExtraction(written = written, data = JsonObject(rootMutable))
    }
}
