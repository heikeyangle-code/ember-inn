package com.emberinn.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/** 角色卡文件存储：characters 目录（*.json）+ avatars 目录（*.png）（内部存储，无权限要求）。 */
class CharacterStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val charactersDir: File get() = File(context.filesDir, "characters").apply { mkdirs() }
    private val avatarsDir: File get() = File(context.filesDir, "avatars").apply { mkdirs() }

    fun list(): List<CharacterRecord> =
        charactersDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<CharacterRecord>(file.readText()) }.getOrNull()
            }
            ?.sortedWith(compareByDescending<CharacterRecord> { it.pinned }.thenByDescending { it.importedAt })
            ?: emptyList()

    fun get(id: String): CharacterRecord? = list().firstOrNull { it.id == id }

    fun save(record: CharacterRecord) {
        File(charactersDir, "${record.id}.json").writeText(json.encodeToString(CharacterRecord.serializer(), record))
    }

    fun delete(id: String) {
        File(charactersDir, "$id.json").delete()
        File(avatarsDir, "$id.png").delete()
    }

    fun saveAvatar(id: String, bytes: ByteArray): String {
        val file = File(avatarsDir, "$id.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
