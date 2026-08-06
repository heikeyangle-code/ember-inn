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
    fun `add decreases budget`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionMessage("system", "abcde", tokens = 5))
        assertEquals(5, cc.tokenBudget)
    }

    @Test
    fun `add throws when budget exceeded`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionMessage("system", "abcde", tokens = 5))
        try {
            cc.add(CompletionMessage("system", "1234567890", tokens = 10))
            org.junit.Assert.fail("expected TokenBudgetExceededError")
        } catch (expected: TokenBudgetExceededError) {
            // expected
        }
        assertEquals(2, cc.messages.size)
    }

    @Test
    fun `insert skips empty content and throws on overflow`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionMessage("system", "marker", identifier = "marker", tokens = 1))
        cc.insertAfterIdentifier("marker", CompletionMessage("system", "", identifier = "empty", tokens = 0))
        assertEquals(1, cc.messages.size)
        try {
            cc.insertAfterIdentifier("marker", CompletionMessage("system", "x".repeat(20), tokens = 20))
            org.junit.Assert.fail("expected TokenBudgetExceededError")
        } catch (expected: TokenBudgetExceededError) {
            // expected
        }
        assertEquals(1, cc.messages.size)
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
