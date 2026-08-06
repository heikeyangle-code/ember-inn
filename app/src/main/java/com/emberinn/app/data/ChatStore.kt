package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.worldinfo.ChatJsonl
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SessionRecord(
    val id: String,
    val characterId: String?,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 聊天会话存储：sessions/*.json + chats/*.jsonl（对齐官方 jsonl：每行一条消息 JSON）。 */
class ChatStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val sessionsDir: File get() = File(context.filesDir, "sessions").apply { mkdirs() }
    private val chatsDir: File get() = File(context.filesDir, "chats").apply { mkdirs() }

    fun findByCharacter(characterId: String?): SessionRecord? =
        list().firstOrNull { it.characterId == characterId }

    fun get(id: String): SessionRecord? = list().firstOrNull { it.id == id }

    fun list(): List<SessionRecord> =
        sessionsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<SessionRecord>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun recent(limit: Int): List<SessionRecord> = list().take(limit)

    fun upsert(record: SessionRecord) {
        File(sessionsDir, "${record.id}.json").writeText(json.encodeToString(SessionRecord.serializer(), record))
    }

    fun messages(sessionId: String): List<JsonElement> {
        val file = File(chatsDir, "$sessionId.jsonl")
        if (!file.exists()) return emptyList()
        return ChatJsonl.import(file.readText())
    }

    /** 消息字段对齐官方 script.js：name / is_user / is_system / send_date / mes / extra。 */
    fun append(sessionId: String, isUser: Boolean, content: String, name: String) {
        val list = messages(sessionId).toMutableList()
        list += buildJsonObject {
            put("name", JsonPrimitive(name))
            put("is_user", JsonPrimitive(isUser))
            put("is_system", JsonPrimitive(false))
            put("send_date", JsonPrimitive(java.time.Instant.now().toString()))
            put("mes", JsonPrimitive(content))
            put("extra", JsonObject(emptyMap()))
        }
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteByCharacter(characterId: String?) {
        list().filter { it.characterId == characterId }.forEach { s ->
            File(sessionsDir, "${s.id}.json").delete()
            File(chatsDir, "${s.id}.jsonl").delete()
        }
    }
}
