package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryPopulatorTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `stops when budget exhausted`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        val messages = listOf(
            PromptMessage("user", "一二三四五六七八九十"),   // 10
            PromptMessage("assistant", "一二三四五六七八九十"), // 10
        )
        val added = ChatHistoryPopulator.populate(messages, cc, handler, "新对话", env)
        // 新对话预留后预算剩 97，两条消息 20，能全加
        assertEquals(2, added.size)
        assertEquals(77, cc.tokenBudget)
    }

    @Test
    fun `new chat prompt macro substituted`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        val added = ChatHistoryPopulator.populate(emptyList(), cc, handler, "[Start a new Chat] {{char}}", env)
        assertEquals(0, added.size)
        // 预留消息后预算扣减 = 宏替换后文本长度
        assertEquals(100 - "[Start a new Chat] 柳春娘".length, cc.tokenBudget)
    }
}
