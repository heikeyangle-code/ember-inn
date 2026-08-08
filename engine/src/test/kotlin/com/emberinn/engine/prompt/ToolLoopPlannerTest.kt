package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolLoopPlannerTest {

    @Test
    fun `continues while under recurse limit`() {
        val calls = listOf(ToolCall("c1", "f", "{}"))
        assertTrue(ToolLoopPlanner.shouldContinue(calls, 0))
        assertTrue(ToolLoopPlanner.shouldContinue(calls, 4))
        assertFalse(ToolLoopPlanner.shouldContinue(calls, 5))
        assertFalse(ToolLoopPlanner.shouldContinue(calls, 6))
    }

    @Test
    fun `stops when no tool calls`() {
        assertFalse(ToolLoopPlanner.shouldContinue(emptyList(), 0))
        assertFalse(ToolLoopPlanner.shouldContinue(null, 0))
    }

    @Test
    fun `builds assistant plus tool result messages`() {
        val assistant = CompletionMessage("assistant", "thinking", toolCalls = listOf(ToolCall("c1", "f", "{}")))
        val messages = ToolLoopPlanner.buildNextMessages(
            assistant,
            listOf("c1" to "result"),
        )
        assertEquals(2, messages.size)
        assertEquals("assistant", messages[0].role)
        assertEquals("tool", messages[1].role)
        assertEquals("c1", messages[1].toolCallId)
        assertEquals("result", messages[1].content)
    }

    @Test
    fun `recursion count advances`() {
        assertEquals(1, ToolLoopPlanner.nextRecursionCount(0))
        assertEquals(5, ToolLoopPlanner.nextRecursionCount(4))
    }
}
