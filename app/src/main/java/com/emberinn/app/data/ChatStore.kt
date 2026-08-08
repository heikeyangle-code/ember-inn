package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.worldinfo.ChatJsonl
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SessionRecord(
    val id: String,
    val characterId: String?,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

/** 聊天会话存储：sessions 目录（*.json）+ chats 目录（*.jsonl）（对齐官方 jsonl：每行一条消息 JSON）。 */
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
            ?.sortedWith(compareByDescending<SessionRecord> { it.pinned }.thenByDescending { it.updatedAt })
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

    /** 编辑消息：更新文本并清空 extra.bias（对齐官方 updateMessage 的 AI_OUTPUT 分支；regex/isEdit 待正则 UI 接线）。 */
    fun updateMessage(sessionId: String, index: Int, content: String) {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return
        val el = list[index].jsonObject
        val oldExtra = el["extra"] as? JsonObject
        val newExtra = JsonObject((oldExtra?.toMap() ?: emptyMap()) + ("bias" to JsonNull))
        list[index] = JsonObject(el + ("mes" to JsonPrimitive(content)) + ("extra" to newExtra))
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** 删除指定下标的一条消息（重新生成/删除消息用；对齐官方删除消息后落盘 jsonl）。 */
    /** 整体替换某会话消息（重新生成/继续/清空会话用）。 */
    fun replace(sessionId: String, elements: List<JsonElement>) {
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(elements))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun removeAt(sessionId: String, index: Int) {
        val list = messages(sessionId).toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
            get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
        }
    }

    /** 会话列表预览：最后一条消息文本（无消息返回 null）。 */
    fun lastMessage(sessionId: String): String? {
        val list = messages(sessionId)
        if (list.isEmpty()) return null
        val mes = list.last().jsonObject["mes"]?.jsonPrimitive?.contentOrNull
        return mes?.takeIf { it.isNotBlank() }
    }

    /** 导出聊天：原始 JSONL 文本（对齐官方聊天文件格式）。 */
    fun exportJsonl(sessionId: String): String? {
        val file = File(chatsDir, "$sessionId.jsonl")
        return if (file.exists()) file.readText() else null
    }

    /** 删除单个会话（会话元数据 + 聊天 jsonl）。 */
    fun delete(sessionId: String) {
        File(sessionsDir, "$sessionId.json").delete()
        File(chatsDir, "$sessionId.jsonl").delete()
    }

    fun deleteByCharacter(characterId: String?) {
        list().filter { it.characterId == characterId }.forEach { s ->
            File(sessionsDir, "${s.id}.json").delete()
            File(chatsDir, "${s.id}.jsonl").delete()
        }
    }
}
