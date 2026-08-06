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

/**
 * ChatCompletion 核心：tokenBudget = context - response；
 * add 先查预算、再扣减；overflow 标记；squashSystemMessages 合并连续无名 system。
 */
class ChatCompletion(private val handler: TokenHandler) {

    val messages = mutableListOf<CompletionMessage>()
    var tokenBudget = 0
        private set
    var overflowed = false
        private set

    fun setTokenBudget(context: Int, response: Int) {
        tokenBudget = context - response
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
        if (message.tokens > tokenBudget) overflowed = true
    }
}
