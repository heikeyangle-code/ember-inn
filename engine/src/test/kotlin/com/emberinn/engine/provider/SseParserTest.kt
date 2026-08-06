package com.emberinn.engine.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun `parses deltas and done`() {
        val raw = """
            data: {"choices":[{"delta":{"content":"你"}}]}

            data: {"choices":[{"delta":{"content":"好"}}]}

            data: [DONE]

        """.trimIndent()
        val chunks = SseParser.parse(raw)
        assertEquals(listOf("你", "好", ""), chunks.map { it.content })
        assertTrue(chunks.last().done)
    }

    @Test
    fun `ignores non data lines`() {
        val chunks = SseParser.parse(": keep-alive\ndata: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n")
        assertEquals(listOf("x"), chunks.map { it.content })
    }
}
