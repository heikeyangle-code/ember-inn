package com.emberinn.engine.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `null delta content is skipped not literal null`() {
        val raw = """
            data: {"choices":[{"delta":{"role":"assistant"}}]}

            data: {"choices":[{"delta":{"content":null}}]}

            data: {"choices":[{"delta":{"content":"你好"}}]}

            data: [DONE]

        """.trimIndent()
        val chunks = SseParser.parse(raw)
        // null / 缺省 content 的块产出空内容，绝不拼出字面 "null"
        assertFalse(chunks.any { it.content == "null" })
        assertEquals(listOf("你好"), chunks.filter { it.content.isNotEmpty() }.map { it.content })
        assertTrue(chunks.last().done)
    }

    @Test
    fun `ignores non data lines`() {
        val chunks = SseParser.parse(": keep-alive\ndata: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n")
        assertEquals(listOf("x"), chunks.map { it.content })
    }
}
