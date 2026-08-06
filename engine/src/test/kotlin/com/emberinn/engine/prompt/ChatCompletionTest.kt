package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionTest {

    private val handler = TokenHandler(TokenCounter { it.length })

    @Test
    fun `budget is context minus response`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(32000, 1000)
        assertEquals(31000, cc.tokenBudget)
    }

    @Test
    fun `add decreases budget and overflow marks`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionMessage("system", "abcde", tokens = 5))
        assertEquals(5, cc.tokenBudget)
        cc.add(CompletionMessage("system", "1234567890", tokens = 10))
        assertTrue(cc.overflowed)
    }

    @Test
    fun `squash consecutive unnamed system messages`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        cc.add(CompletionMessage("system", "A", tokens = 1))
        cc.add(CompletionMessage("system", "B", tokens = 1))
        cc.add(CompletionMessage("user", "C", tokens = 1))
        cc.squashSystemMessages()
        assertEquals(2, cc.messages.size)
        assertEquals("A\nB", cc.messages[0].content)
    }
}
