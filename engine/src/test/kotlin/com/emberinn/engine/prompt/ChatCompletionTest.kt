package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `add collection at position keeps sparse order`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        cc.add(CompletionCollection("a"), 3)
        assertEquals(3, cc.findMessageIndex("a"))
        assertTrue(cc.has("a"))
        assertEquals(100, cc.tokenBudget)
    }

    @Test
    fun `add throws when budget exceeded`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(5, 0)
        val collection = CompletionCollection("x")
        collection.add(CompletionMessage("system", "1234567890", tokens = 10))
        try {
            cc.add(collection)
            org.junit.Assert.fail("expected TokenBudgetExceededError")
        } catch (expected: TokenBudgetExceededError) {
            // expected
        }
    }

    @Test
    fun `insert skips empty content and throws on overflow`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionCollection("marker"))
        cc.insert(CompletionMessage("system", "", identifier = "empty", tokens = 0), "marker")
        assertEquals(0, (cc.entries[0] as ChatEntry.Collection).collection.items.size)
        try {
            cc.insert(CompletionMessage("system", "x".repeat(20), tokens = 20), "marker")
            org.junit.Assert.fail("expected TokenBudgetExceededError")
        } catch (expected: TokenBudgetExceededError) {
            // expected
        }
        assertEquals(0, (cc.entries[0] as ChatEntry.Collection).collection.items.size)
    }

    @Test
    fun `insertAtStart produces chronological order when fed latest first`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        cc.add(CompletionCollection("hist"))
        cc.insertAtStart(CompletionMessage("assistant", "最新", tokens = 1), "hist")
        cc.insertAtStart(CompletionMessage("user", "更早", tokens = 1), "hist")
        val ids = (cc.entries[0] as ChatEntry.Collection).collection.items.map { it.content }
        assertEquals(listOf("更早", "最新"), ids)
    }

    @Test
    fun `getChat flattens and skips empty messages`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        cc.add(CompletionCollection("main"))
        cc.insert(CompletionMessage("system", "主体", identifier = "main", tokens = 1), "main")
        cc.add(CompletionCollection("hist"))
        cc.insert(CompletionMessage("system", "", identifier = "empty", tokens = 0), "hist")
        cc.insert(CompletionMessage("user", "你好", identifier = "chatHistory", tokens = 1), "hist")
        assertEquals(listOf("主体", "你好"), cc.getChat().map { it.content })
    }

    @Test
    fun `squash merges consecutive unnamed system and excludes newChat`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        val a = CompletionCollection("a")
        a.add(CompletionMessage("system", "A", tokens = 1))
        a.add(CompletionMessage("system", "B", tokens = 1))
        a.add(CompletionMessage("user", "C", tokens = 1))
        cc.add(a)
        val b = CompletionCollection("b")
        b.add(CompletionMessage("system", "示例", identifier = "newChat", tokens = 1))
        b.add(CompletionMessage("system", "D", tokens = 1))
        cc.add(b)

        cc.squashSystemMessages()
        val contents = cc.getChat().map { it.content }
        assertEquals(listOf("A\nB", "C", "示例", "D"), contents)
        assertTrue(cc.entries[0] is ChatEntry.Message)
    }

    @Test
    fun `reserve and free budget`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.reserveBudget(4)
        assertEquals(6, cc.tokenBudget)
        cc.freeBudget(4)
        assertEquals(10, cc.tokenBudget)
    }

    @Test
    fun `removeLastFrom restores budget`() {
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10, 0)
        cc.add(CompletionCollection("hist"))
        cc.insert(CompletionMessage("user", "m", tokens = 3), "hist")
        assertEquals(7, cc.tokenBudget)
        val removed = cc.removeLastFrom("hist")
        assertEquals("m", removed?.content)
        assertEquals(10, cc.tokenBudget)
        assertTrue(cc.has("hist"))
    }
}
