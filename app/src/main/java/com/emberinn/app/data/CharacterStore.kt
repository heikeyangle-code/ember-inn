package com.emberinn.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/** 角色卡文件存储：characters/*.json + avatars/*.png（内部存储，无权限要求）。 */
class CharacterStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val charactersDir: File get() = File(context.filesDir, "characters").apply { mkdirs() }
    private val avatarsDir: File get() = File(context.filesDir, "avatars").apply { mkdirs() }

    fun list(): List<CharacterRecord> =
        charactersDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<CharacterRecord>(file.readText()) }.getOrNull()
            } ?: emptyList()

    fun save(record: CharacterRecord) {
        val file = File(charactersDir, "${record.id}.json")
        file.writeText(json.encodeToString(CharacterRecord.serializer(), record))
    }

    fun saveAvatar(id: String, bytes: ByteArray): String {
        val file = File(avatarsDir, "$id.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
