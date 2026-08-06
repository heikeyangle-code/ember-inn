package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 官方支持的 5 种角色卡导入格式（docs/FORMATS.md）。 */
enum class CardFormat { PNG, JSON, CHARX, YAML, BYAF }

interface CharacterCardImporter {
    /** 返回角色卡 JSON 字符串（V2/V3 均可，后续统一归一）。 */
    fun import(data: ByteArray, format: CardFormat): String
}

object CardImporter : CharacterCardImporter {

    override fun import(data: ByteArray, format: CardFormat): String = when (format) {
        CardFormat.PNG -> CharacterCardCodec.readFromPng(data)
        CardFormat.JSON -> String(data, Charsets.UTF_8)
        CardFormat.CHARX -> CharXImporter.cardJson(data)
        CardFormat.YAML -> YamlImporter.import(data)
        CardFormat.BYAF -> ByafImporter.import(data)
    }
}

object CharXImporter {

    private val imageExts = setOf("png", "jpg", "jpeg", "webp", "gif", "apng", "avif", "bmp", "jfif")
    private val json = Json { ignoreUnknownKeys = true }

    /** CharX = ZIP，card.json 即 V3 data；对齐官方 importFromCharX：readFromV2 + 清理私有字段 + create_date=ISO。 */
    fun cardJson(zipBytes: ByteArray): String {
        val raw = findCardJson(zipBytes)
        val normalized = V2Normalizer.normalize(raw)
        val cleaned = CharacterCardCodec.cleanPrivateFields(normalized)
        val root = Json.parseToJsonElement(cleaned).jsonObject.toMutableMap()
        root["create_date"] = JsonPrimitive(java.time.Instant.now().toString())
        return JsonObject(root).toString()
    }

    private fun findCardJson(zipBytes: ByteArray): String {
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "card.json" || entry.name.endsWith("/card.json")) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        error("CharX: card.json not found")
    }

    /** CharX 资源：图标 + 辅助资源（对齐 charx.js mapCharXAssetsForStorage/pickCharXIconAsset）。 */
    data class CharXAsset(
        val type: String,
        val ext: String,
        val zipPath: String,
        val fileName: String,
        val index: Int,
        val data: ByteArray,
    )

    data class CharXAssets(
        val icon: CharXAsset?,
        val assets: List<CharXAsset>,
    )

    fun extractAssets(zipBytes: ByteArray): CharXAssets {
        val files = readZip(zipBytes)
        val card = json.parseToJsonElement(findCardJson(zipBytes)).jsonObject
        val assetsEl = card["data"]?.jsonObject?.get("assets")?.jsonArray ?: return CharXAssets(null, emptyList())
        val mapped = mutableListOf<CharXAsset>()
        assetsEl.forEachIndexed { index, el ->
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEachIndexed
            val ext = obj["ext"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEachIndexed
            val zipPath = obj["zipPath"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEachIndexed
            val fileName = obj["fileName"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                ?: zipPath.substringAfterLast('/')
            val data = files[zipPath] ?: return@forEachIndexed
            mapped.add(CharXAsset(type = type, ext = ext, zipPath = zipPath, fileName = fileName, index = index, data = data))
        }
        val icon = mapped.firstOrNull { it.type == "icon" && it.ext in imageExts }
        return CharXAssets(icon = icon, assets = mapped)
    }

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) files[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }
        return files
    }
}
