package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiTokenCounterTest {

    @Test
    fun `counts tokens consistently`() {
        val counter = OpenAiTokenCounter()
        assertTrue(counter.count("Hello, world!") > 0)
        assertTrue(counter.count("你好，世界") > 0)
        assertEquals(counter.count("稳定输入"), counter.count("稳定输入"))
    }
}
