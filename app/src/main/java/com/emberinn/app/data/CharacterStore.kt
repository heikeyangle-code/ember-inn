package com.emberinn.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/** 角色卡文件存储：characters 目录（*.json）+ avatars 目录（*.png）（内部存储，无权限要求）。 */
class CharacterStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val charactersDir: File get() = File(context.filesDir, "characters").apply { mkdirs() }
    private val avatarsDir: File get() = File(context.filesDir, "avatars").apply { mkdirs() }

    /** 内存缓存：角色列表只读一次，save/delete/导入时失效（避免每次访问全量读盘）。 */
    private var cache: List<CharacterRecord>? = null

    fun list(): List<CharacterRecord> {
        cache?.let { return it }
        val loaded = charactersDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<CharacterRecord>(file.readText()) }.getOrNull()
            }
            ?.sortedWith(compareByDescending<CharacterRecord> { it.pinned }.thenByDescending { it.importedAt })
            ?: emptyList()
        cache = loaded
        return loaded
    }

    fun get(id: String): CharacterRecord? = list().firstOrNull { it.id == id }

    fun save(record: CharacterRecord) {
        File(charactersDir, "${record.id}.json").writeText(json.encodeToString(CharacterRecord.serializer(), record))
        cache = null
    }

    fun delete(id: String) {
        File(charactersDir, "$id.json").delete()
        File(avatarsDir, "$id.png").delete()
        cache = null
    }

    fun saveAvatar(id: String, bytes: ByteArray): String {
        val file = File(avatarsDir, "$id.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
