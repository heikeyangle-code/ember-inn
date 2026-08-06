package com.emberinn.engine.worldinfo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 聊天记录 jsonl：每行一条消息 JSON（对齐官方 chats.js）。 */
object ChatJsonl {

    private val json = Json { ignoreUnknownKeys = true }

    fun export(messages: List<JsonElement>): String =
        messages.joinToString("\n") { it.toString() }

    fun import(text: String): List<JsonElement> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it) }
            .toList()
}

/** 聊天元数据（对齐官方 chat_metadata 核心字段：背景/书签）。 */
@Serializable
data class ChatMetadata(
    val background: String = "",
    @SerialName("custom_background")
    val customBackground: String = "",
    @SerialName("bookmark_message_ids")
    val bookmarkMessageIds: List<Int> = emptyList(),
)
