package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.expression.ExpressionEngine
import com.emberinn.engine.expression.SpriteEntry
import com.emberinn.engine.expression.SpriteStorage
import java.io.File
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表情精灵存储（对齐官方 extensions/expressions sprites 目录：expressions/{角色名} 下扩展名为 .png 的文件）。
 * 引擎只负责路径/分组/选择纯逻辑；本类负责落盘与读取。
 */
class ExpressionStore(private val context: Context) {

    private val root = File(context.filesDir, "expressions").apply { mkdirs() }

    fun sprites(characterName: String): List<SpriteEntry> {
        val dir = folder(characterName) ?: return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.extension.equals("png", true) || it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("webp", true)) }
            .map { SpriteEntry(ExpressionEngine.labelFromFilename(it.name), it.absolutePath) }
            .sortedBy { it.label }
    }

    fun saveSprite(characterName: String, fileName: String, bytes: ByteArray) {
        val dir = folder(characterName) ?: return
        dir.mkdirs()
        File(dir, sanitize(fileName)).writeBytes(bytes)
    }

    fun deleteSprite(characterName: String, path: String) {
        File(path).delete()
    }

    /** 官方 importRisuSprites：从角色卡提取 base64 精灵并落盘，返回清理后的卡 JSON。 */
    fun importRisu(characterRawJson: String): String? {
        val existing = sprites(characterNameOf(characterRawJson)).map { it.label }.toSet()
        val extraction = SpriteStorage.extractRisuSprites(characterRawJson, root.absolutePath, existing)
            ?: return null
        val name = characterNameOf(characterRawJson)
        val dir = folder(name) ?: return null
        dir.mkdirs()
        extraction.written.forEach { sprite ->
            val bytes = runCatching { java.util.Base64.getDecoder().decode(sprite.base64) }.getOrNull() ?: return@forEach
            File(dir, sanitize(sprite.label) + ".png").writeBytes(bytes)
        }
        return extraction.data.toString()
    }

    private fun folder(characterName: String): File? {
        val safe = com.emberinn.engine.card.CardSanitize.sanitizeName(characterName)
        if (safe.isEmpty()) return null
        return File(root, safe)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "sprite" }

    private fun characterNameOf(raw: String): String {
        val rootJson = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return ""
        val data = rootJson["data"]?.jsonObject ?: rootJson
        return data["name"]?.jsonPrimitive?.contentOrNull ?: ""
    }
}
