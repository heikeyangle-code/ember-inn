package com.emberinn.engine.worldinfo

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
