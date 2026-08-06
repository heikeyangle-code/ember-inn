package com.emberinn.engine.prompt

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

/** 对齐官方 Message（核心字段）。 */
data class CompletionMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val identifier: String? = null,
    val tokens: Int = 0,
)

/** 官方 openai.js TokenBudgetExceededError。 */
class TokenBudgetExceededError(message: String = "") : RuntimeException(message)

/**
 * ChatCompletion 核心：tokenBudget = context - response；
 * add/insert 超预算抛 TokenBudgetExceededError（对齐官方）；squashSystemMessages 合并连续无名 system。
 */
class ChatCompletion(private val handler: TokenHandler) {

    val messages = mutableListOf<CompletionMessage>()
    var tokenBudget = 0
        private set

    fun setTokenBudget(context: Int, response: Int) {
        tokenBudget = context - response
    }

    fun reserveBudget(message: CompletionMessage) {
        tokenBudget -= message.tokens
    }

    fun canAfford(message: CompletionMessage): Boolean = message.tokens <= tokenBudget

    fun canAffordAll(messages: List<CompletionMessage>): Boolean =
        messages.sumOf { it.tokens } <= tokenBudget

    /** 插入到指定 identifier 消息之后（对齐官方 insert：空内容不插入、超预算抛错）。 */
    fun insertAfterIdentifier(identifier: String, message: CompletionMessage) {
        checkTokenBudget(message)
        if (message.content.isEmpty()) return
        val idx = messages.indexOfLast { it.identifier == identifier }
        if (idx >= 0) {
            messages.add(idx + 1, message)
        } else {
            messages.add(message)
        }
        tokenBudget -= message.tokens
    }

    fun add(message: CompletionMessage): ChatCompletion {
        checkTokenBudget(message)
        messages.add(message)
        tokenBudget -= message.tokens
        return this
    }

    fun squashSystemMessages() {
        val exclude = setOf("newMainChat", "newChat", "groupNudge")
        val out = mutableListOf<CompletionMessage>()
        var last: CompletionMessage? = null
        for (m in messages) {
            if (m.role == "system" && m.content.isEmpty()) continue
            val canSquash = m.role == "system" && m.name == null && m.identifier !in exclude
            if (canSquash && last != null && last.role == "system" && last.name == null && last.identifier !in exclude) {
                val merged = last.copy(
                    content = last.content + "\n" + m.content,
                    tokens = handler.countAsync(last.content + "\n" + m.content, "prompt"),
                )
                out[out.lastIndex] = merged
                last = merged
            } else {
                out.add(m)
                last = m
            }
        }
        messages.clear()
        messages.addAll(out)
    }

    private fun checkTokenBudget(message: CompletionMessage) {
        if (message.tokens > tokenBudget) throw TokenBudgetExceededError(message.identifier ?: "")
    }
}
