package com.emberinn.engine.worldinfo

import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCounterFactoryTest {

    @Test
    fun `openai and unknown models count tokens`() {
        val gpt = TokenCounterFactory.forModel("gpt-4o")
        val fallback = TokenCounterFactory.forModel("some-unknown-model")
        assertTrue(gpt.count("hello world") > 0)
        assertTrue(fallback.count("hello world") > 0)
        assertTrue(gpt.count("The quick brown fox") >= 4)
    }
}
