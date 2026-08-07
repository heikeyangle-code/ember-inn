package com.emberinn.engine.card

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
        CardFormat.JSON -> JsonImporter.import(data)
        CardFormat.CHARX -> CharXImporter.cardJson(data)
        CardFormat.YAML -> YamlImporter.import(data)
        CardFormat.BYAF -> ByafImporter.import(data)
    }
}

object CharXImporter {

    private val imageExts = setOf("png", "jpg", "jpeg", "webp", "gif", "apng", "avif", "bmp", "jfif")
    private val spriteTypes = setOf("emotion", "expression")
    private val backgroundTypes = setOf("background")
    private val uriPrefixes = listOf("embeded://", "embedded://", "__asset:")
    private val json = Json { ignoreUnknownKeys = true }

    /** CharX = ZIP，card.json 即 V3 data；对齐官方 importFromCharX：名字 sanitize + readFromV2 + 清理私有字段 + create_date=ISO。 */
    fun cardJson(zipBytes: ByteArray, now: String = Instant.now().toString()): String {
        val raw = findCardJson(zipBytes)
        val card = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
        val data = card["data"]?.jsonObject?.toMutableMap()
        val dataName = data?.get("name")?.jsonPrimitive?.contentOrNull
        if (data != null && !dataName.isNullOrEmpty()) {
            data["name"] = JsonPrimitive(CardSanitize.sanitizeName(dataName))
            card["data"] = JsonObject(data)
        }
        val rootName = dataName?.takeIf { it.isNotEmpty() }
            ?: card["name"]?.jsonPrimitive?.contentOrNull ?: ""
        card["name"] = JsonPrimitive(CardSanitize.sanitizeName(rootName))

        val normalized = V2Normalizer.normalize(JsonObject(card).toString())
        val cleaned = CharacterCardCodec.cleanPrivateFields(normalized)
        val root = Json.parseToJsonElement(cleaned).jsonObject.toMutableMap()
        root["create_date"] = JsonPrimitive(now)
        return JsonObject(root).toString()
    }

    private fun findCardJson(zipBytes: ByteArray): String {
        val start = findZipStart(zipBytes)
        java.util.zip.ZipInputStream(start.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("card.json") && !entry.name.startsWith("__MACOSX")) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        error("CharX: card.json not found")
    }

    private fun findZipStart(data: ByteArray): ByteArray {
        val sig = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        for (i in 0..data.size - sig.size) {
            var match = true
            for (j in sig.indices) {
                if (data[i + j] != sig[j]) { match = false; break }
            }
            if (match) return data.copyOfRange(i, data.size)
        }
        return data
    }

    /** CharX 资源（对齐 charx.js CharXAsset / storage mapping）。 */
    data class CharXAsset(
        val type: String,
        val name: String,
        val ext: String,
        val zipPath: String,
        val order: Int,
        val storageCategory: String? = null,
        val baseName: String? = null,
        val data: ByteArray? = null,
    )

    data class CharXAssets(
        val icon: CharXAsset?,
        val assets: List<CharXAsset>,
    )

    fun extractAssets(zipBytes: ByteArray): CharXAssets {
        val files = readZip(zipBytes)
        val card = json.parseToJsonElement(findCardJson(zipBytes)).jsonObject
        val assetsEl = card["data"]?.jsonObject?.get("assets")?.jsonArray ?: return CharXAssets(null, emptyList())

        val collected = mutableListOf<CharXAsset>()
        assetsEl.forEachIndexed { index, el ->
            if (el !is JsonObject) return@forEachIndexed
            val uri = el["uri"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEachIndexed
            val zipPath = embeddedZipPath(uri) ?: return@forEachIndexed
            val ext = deriveExt(el["ext"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }, zipPath)
            val type = el["type"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }?.lowercase() ?: ""
            val name = el["name"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
            collected.add(CharXAsset(type = type, name = name, ext = ext, zipPath = zipPath, order = index))
        }

        val icon = pickIcon(collected, files)
        val mapped = mapForStorage(collected, files)
        return CharXAssets(icon = icon, assets = mapped)
    }

    private fun embeddedZipPath(uri: String): String? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        for (prefix in uriPrefixes) {
            if (lower.startsWith(prefix)) {
                return normalizeZipPath(trimmed.substring(prefix.length))
            }
        }
        return null
    }

    private fun normalizeExt(ext: String?): String {
        if (ext == null) return ""
        return ext.trim().lowercase().removePrefix(".")
    }

    private fun deriveExt(metaExt: String?, zipPath: String): String {
        val fromMeta = normalizeExt(metaExt)
        val dot = zipPath.lastIndexOf('.')
        val slash = zipPath.lastIndexOf('/')
        val fromPath = if (dot > slash && dot >= 0) zipPath.substring(dot + 1).lowercase().trim() else ""
        return fromMeta.ifEmpty { fromPath }
    }

    private fun pickIcon(collected: List<CharXAsset>, files: Map<String, ByteArray>): CharXAsset? {
        val icons = collected.filter { it.type == "icon" && it.ext in imageExts && it.zipPath.isNotEmpty() }
        if (icons.isEmpty()) return null
        val main = icons.firstOrNull { it.name.lowercase() == "main" } ?: icons.first()
        return main.copy(data = files[main.zipPath])
    }

    private fun mapForStorage(collected: List<CharXAsset>, files: Map<String, ByteArray>): List<CharXAsset> {
        return collected.mapNotNull { asset ->
            if (asset.zipPath.isEmpty()) return@mapNotNull null
            val ext = asset.ext.lowercase()
            if (ext !in imageExts) return@mapNotNull null
            if (asset.type == "icon" || asset.type == "user_icon") return@mapNotNull null

            val storageCategory = when {
                asset.type in spriteTypes -> "sprite"
                asset.type in backgroundTypes -> "background"
                else -> "misc"
            }
            val useHyphens = storageCategory == "sprite"
            val nameWithoutExt = stripTrailingImageExtension(asset.name, ext)
            val baseName = assetBaseName(nameWithoutExt, "${storageCategory}-${asset.order}", useHyphens)
            asset.copy(
                storageCategory = storageCategory,
                baseName = baseName,
                data = files[asset.zipPath],
            )
        }
    }

    private fun stripTrailingImageExtension(name: String, expectedExt: String): String {
        if (name.isEmpty() || expectedExt.isEmpty()) return name
        val lower = name.lowercase()
        if (lower.endsWith(".$expectedExt")) return name.dropLast(expectedExt.length + 1)
        for (ext in imageExts) {
            if (lower.endsWith(".$ext")) return name.dropLast(ext.length + 1)
        }
        return name
    }

    private fun assetBaseName(rawName: String, fallback: String, useHyphens: Boolean): String {
        val cleaned = rawName.trim()
        if (cleaned.isEmpty()) return fallback.lowercase()
        val separator = if (useHyphens) '-' else '_'
        var base = cleaned.lowercase().replace(Regex("[^a-z0-9]+"), separator.toString())
        base = base.trimStart(separator).trimEnd(separator)
        if (base.isEmpty()) return fallback.lowercase()
        val sanitized = CardSanitize.sanitizeName(base)
        return (sanitized.ifEmpty { fallback }).lowercase()
    }

    /** 对齐官方 util.js normalizeZipEntryPath。 */
    private fun normalizeZipPath(entryName: String): String? {
        if (entryName.isEmpty()) return null
        var normalized = entryName.replace('\\', '/').trim()
        if (normalized.isEmpty()) return null
        while (normalized.startsWith("./")) normalized = normalized.removePrefix("./")
        val parts = ArrayDeque<String>()
        for (part in normalized.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        normalized = parts.joinToString("/")
        if (normalized.isEmpty() || normalized == "." || normalized.startsWith("..")) return null
        if (normalized.startsWith("/")) normalized = normalized.drop(1)
        return normalized
    }

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(findZipStart(zipBytes).inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalized = normalizeZipPath(entry.name)
                    if (normalized != null) files[normalized] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return files
    }
}

