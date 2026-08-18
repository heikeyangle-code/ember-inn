package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.expression.ExpressionEngine
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

    companion object {
        /** 精灵目录列表缓存（对齐官方 spriteCache 全局缓存语义）：聊天列表滚动时每条 AI 消息
         *  组合都会调 sprites(name)，此前每次都列目录（磁盘 IO）是滑动卡顿源；写操作（save/delete/import）
         *  全部经本类，变更时 invalidate 即时失效。LRU 上限 24 个角色。 */
        private const val CACHE_MAX = 24
        private val cache = LinkedHashMap<String, List<ExpressionEngine.SpriteEntry>>()
        private var cacheVersion = 0
        private var cachedVersion = -1

        @Synchronized
        fun invalidate() {
            cacheVersion++
            cache.clear()
        }

        @Synchronized
        private fun cached(name: String, version: Int, loader: () -> List<ExpressionEngine.SpriteEntry>): List<ExpressionEngine.SpriteEntry> {
            if (cachedVersion != version) {
                cache.clear()
                cachedVersion = version
            }
            cache.remove(name)?.let { hit ->
                cache[name] = hit // LRU touch
                return hit
            }
            val loaded = loader()
            cache[name] = loaded
            if (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
            return loaded
        }
    }

    fun sprites(characterName: String): List<ExpressionEngine.SpriteEntry> =
        cached(characterName, cacheVersion) {
            val dir = folder(characterName) ?: return@cached emptyList()
            (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && (it.extension.equals("png", true) || it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("webp", true)) }
                .map { ExpressionEngine.SpriteEntry(ExpressionEngine.labelFromFilename(it.name), it.absolutePath) }
                .sortedBy { it.label }
        }

    fun saveSprite(characterName: String, fileName: String, bytes: ByteArray) {
        val dir = folder(characterName) ?: return
        dir.mkdirs()
        File(dir, sanitize(fileName)).writeBytes(bytes)
        invalidate()
    }

    fun deleteSprite(characterName: String, path: String) {
        File(path).delete()
        invalidate()
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
        invalidate()
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
