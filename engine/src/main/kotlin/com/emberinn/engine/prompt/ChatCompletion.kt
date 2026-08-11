package com.emberinn.engine.prompt

import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.worldinfo.TokenCounter

/** 对齐官方 TokenHandler：按类型统计 token。 */
class TokenHandler(private val counter: TokenCounter) {

    val counts = mutableMapOf(
        "start_chat" to 0, "prompt" to 0, "bias" to 0, "nudge" to 0,
        "jailbreak" to 0, "impersonate" to 0, "examples" to 0, "conversation" to 0,
    )

    fun resetCounts() { counts.replaceAll { _, _ -> 0 } }

    fun countAsync(text: String, type: String): Int {
        val n = counter.count(text)
        counts[type] = (counts[type] ?: 0) + n
        return n
    }
}

/** 对齐官方 Message 核心字段。 */
data class CompletionMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val identifier: String? = null,
    val tokens: Int = 0,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val media: List<MediaAttachment>? = null,
    /** Gemini 2.5/3 思考签名（对齐官方 message.signature，Gemini 转换时注入 thoughtSignature）。 */
    val signature: String? = null,
    /** 助手思考文本（对齐官方 message.reasoning，工具推理链 fallback 用）。 */
    val reasoning: String? = null,
)

/** 对齐官方 tool_calls（id/type/function.name/arguments + 推理签名）。 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val type: String = "function",
    val signature: String? = null,
)

/** 对齐官方 MessageCollection：带 identifier 的消息集合。 */
class CompletionCollection(val identifier: String) {
    val items = mutableListOf<CompletionMessage>()

    fun add(message: CompletionMessage) { items += message }

    fun insert(message: CompletionMessage, position: Int) {
        items.add(position, message)
    }

    fun insertAtStart(message: CompletionMessage) { items.add(0, message) }

    fun insertAtEnd(message: CompletionMessage) { items += message }

    fun removeLast(): CompletionMessage? =
        if (items.isEmpty()) null else items.removeAt(items.lastIndex)

    fun getTokens(): Int = items.sumOf { it.tokens }
}

/** ChatCompletion 根集合中的一项：嵌套集合或扁平消息（squash 后）。 */
sealed class ChatEntry {
    data class Collection(val collection: CompletionCollection) : ChatEntry()
    data class Message(val message: CompletionMessage) : ChatEntry()
}

/** 官方 openai.js TokenBudgetExceededError。 */
class TokenBudgetExceededError(message: String = "") : RuntimeException(message)

/**
 * 对齐官方 openai.js ChatCompletion：
 * 根集合按 PromptManager 顺序稀疏放置 MessageCollection；
 * add/insert 超预算抛 TokenBudgetExceededError；getChat 展平并跳过空消息。
 */
class ChatCompletion(private val handler: TokenHandler) {

    val entries = mutableListOf<ChatEntry?>()
    var tokenBudget = 0
        private set
    val overriddenPrompts = mutableListOf<String>()

    fun setTokenBudget(context: Int, response: Int) {
        tokenBudget = context - response
    }

    fun setOverriddenPrompts(prompts: List<String>) {
        overriddenPrompts.clear()
        overriddenPrompts += prompts
    }

    fun canAfford(message: CompletionMessage): Boolean = message.tokens <= tokenBudget

    fun canAffordAll(messages: List<CompletionMessage>): Boolean =
        messages.sumOf { it.tokens } <= tokenBudget

    /** 对齐官方 add(collection, position)：position 非 null 且非 -1 时按位覆盖，否则追加。 */
    fun add(collection: CompletionCollection, position: Int? = null): ChatCompletion {
        checkTokenBudget(collection.getTokens())
        if (position != null && position != -1) {
            while (entries.size <= position) entries.add(null)
            entries[position] = ChatEntry.Collection(collection)
        } else {
            entries += ChatEntry.Collection(collection)
        }
        tokenBudget -= collection.getTokens()
        return this
    }

    fun has(identifier: String): Boolean = findMessageIndex(identifier) != -1

    fun findMessageIndex(identifier: String): Int =
        entries.indexOfFirst { (it as? ChatEntry.Collection)?.collection?.identifier == identifier }

    /** 对齐官方 insert：先查预算（抛错），空内容不插入。 */
    fun insert(message: CompletionMessage, identifier: String, position: String = "end") {
        checkTokenBudget(message)
        // 官方：content 或 tool_calls 任一存在即插入
        if (message.content.isEmpty() && message.toolCalls == null) return
        val index = findMessageIndex(identifier)
        if (index < 0) return
        val collection = (entries[index] as? ChatEntry.Collection)?.collection ?: return
        when (position) {
            "start" -> collection.insertAtStart(message)
            "end" -> collection.insertAtEnd(message)
            else -> collection.insert(message, position.toIntOrNull() ?: collection.items.size)
        }
        tokenBudget -= message.tokens
    }

    fun insertAtStart(message: CompletionMessage, identifier: String) =
        insert(message, identifier, "start")

    /** 批量插到集合开头（单次 O(n)，避免逐条 unshift 造成 O(n²)）。 */
    fun insertAllAtStart(messages: List<CompletionMessage>, identifier: String) {
        if (messages.isEmpty()) return
        checkTokenBudget(messages.sumOf { it.tokens })
        val index = findMessageIndex(identifier)
        if (index < 0) return
        val collection = (entries[index] as? ChatEntry.Collection)?.collection ?: return
        collection.items.addAll(0, messages)
        tokenBudget -= messages.sumOf { it.tokens }
    }

    fun insertAtEnd(message: CompletionMessage, identifier: String) =
        insert(message, identifier, "end")

    /** 对齐 removeLastFrom：弹出集合最后一条并归还预算。 */
    fun removeLastFrom(identifier: String): CompletionMessage? {
        val index = findMessageIndex(identifier)
        if (index < 0) return null
        val collection = (entries[index] as? ChatEntry.Collection)?.collection ?: return null
        val message = collection.removeLast() ?: return null
        tokenBudget += message.tokens
        return message
    }

    fun reserveBudget(message: CompletionMessage) { tokenBudget -= message.tokens }

    fun reserveBudget(tokens: Int) { tokenBudget -= tokens }

    fun freeBudget(message: CompletionMessage) { tokenBudget += message.tokens }

    fun freeBudget(tokens: Int) { tokenBudget += tokens }

    fun getTokens(): Int = entries.filterNotNull().sumOf { entry ->
        when (entry) {
            is ChatEntry.Collection -> entry.collection.getTokens()
            is ChatEntry.Message -> entry.message.tokens
        }
    }

    /** 对齐 getChat：展平、跳过空消息、tool_calls/signature 等字段暂为边界。 */
    fun getChat(): List<CompletionMessage> {
        val chat = mutableListOf<CompletionMessage>()
        for (entry in entries.filterNotNull()) {
            val messages = when (entry) {
                is ChatEntry.Collection -> entry.collection.items
                is ChatEntry.Message -> listOf(entry.message)
            }
            chat += messages.filter { it.content.isNotEmpty() || it.toolCalls != null }
        }
        return chat
    }

    /** 对齐 squashSystemMessages：展平后合并连续无名 system（排除 newMainChat/newChat/groupNudge）。 */
    fun squashSystemMessages() {
        val exclude = setOf("newMainChat", "newChat", "groupNudge")
        val flat = entries.filterNotNull().flatMap { entry ->
            when (entry) {
                is ChatEntry.Collection -> entry.collection.items
                is ChatEntry.Message -> listOf(entry.message)
            }
        }
        val out = mutableListOf<ChatEntry>()
        var last: CompletionMessage? = null
        for (m in flat) {
            if (m.role == "system" && m.content.isEmpty()) continue
            val canSquash = m.role == "system" && m.name == null && m.identifier !in exclude
            if (canSquash && last != null && last.role == "system" && last.name == null && last.identifier !in exclude) {
                val merged = last.copy(
                    content = last.content + "\n" + m.content,
                    tokens = handler.countAsync(last.content + "\n" + m.content, "prompt"),
                )
                out[out.lastIndex] = ChatEntry.Message(merged)
                last = merged
            } else {
                out.add(ChatEntry.Message(m))
                last = m
            }
        }
        entries.clear()
        entries.addAll(out)
    }

    private fun checkTokenBudget(tokens: Int) {
        if (tokens > tokenBudget) throw TokenBudgetExceededError()
    }

    private fun checkTokenBudget(message: CompletionMessage) = checkTokenBudget(message.tokens)
}
