package com.emberinn.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 分节明细里的一条消息（官方 itemizedPrompts[].rawPrompt 的 messages 数组项；identifier 为提示项 key）。 */
@Serializable
data class ItemizationSection(
    val identifier: String,
    val role: String,
    val content: String,
    val tokens: Int,
)

/** 官方 itemized-prompts.js 每条消息的明细：rawPrompt + TokenHandler 分桶 + 分节消息 + 上下文元信息。 */
@Serializable
data class ItemizationEntry(
    val messageIndex: Int,
    val rawPrompt: String,
    val totalTokens: Int,
    val counts: Map<String, Int> = emptyMap(),
    val sections: List<ItemizationSection> = emptyList(),
    val providerName: String = "",
    val model: String = "",
    val presetName: String = "",
    val tokenizer: String = "",
    val maxContext: Int = 0,
    val maxTokens: Int = 0,
    /** 官方 itemized-prompts instruction：非 openai 源启用 sysprompt 时的系统提示（宏已替换）。 */
    val instruction: String = "",
)

/**
 * 官方 itemized-prompts.js promptStorage（localforage）的 App 等价：
 * 按会话持久化每条生成消息的总装明细到 filesDir/itemizations/<session>.json。
 * 消息删除时同官方 deleteItemizedPromptForMessage：删该条并把后续 mesId 下移。
 */
object ItemizationStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(dir: File, sessionId: String): File {
        val name = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "itemizations/$name.json")
    }

    fun load(dir: File, sessionId: String): List<ItemizationEntry> {
        val f = file(dir, sessionId)
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ItemizationEntry.serializer()), f.readText())
        }.getOrDefault(emptyList())
    }

    fun save(dir: File, sessionId: String, entries: List<ItemizationEntry>) {
        val f = file(dir, sessionId)
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(ListSerializer(ItemizationEntry.serializer()), entries))
    }

    fun put(dir: File, sessionId: String, entry: ItemizationEntry) {
        val list = load(dir, sessionId).filterNot { it.messageIndex == entry.messageIndex } + entry
        save(dir, sessionId, list.sortedBy { it.messageIndex })
    }

    /** 官方 deleteItemizedPromptForMessage：删除该消息明细并把之后的下移。 */
    fun deleteMessage(dir: File, sessionId: String, index: Int) {
        val list = load(dir, sessionId)
        if (list.isEmpty()) return
        val shifted = list
            .filterNot { it.messageIndex == index }
            .map { if (it.messageIndex > index) it.copy(messageIndex = it.messageIndex - 1) else it }
        save(dir, sessionId, shifted)
    }
}
