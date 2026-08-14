package com.emberinn.engine.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 官方 chat-completions.js /bias 端点语义：claude 无 bias、原始 id 数组透传、后写覆盖。 */
class LogitBiasEngineTest {

    @Test
    fun `claude models produce no bias`() {
        assertTrue(
            LogitBiasEngine.compute("claude-sonnet-4-5", listOf(BiasEntry(text = "bond", value = -50.0))).isEmpty(),
        )
    }

    @Test
    fun `raw id arrays pass through`() {
        val out = LogitBiasEngine.compute(
            "gpt-4o",
            listOf(BiasEntry(text = "[123, 456]", value = -50.0), BiasEntry(text = "[456]", value = 5.0)),
        )
        // 后写覆盖先写
        assertEquals(-50.0, out["123"] ?: 0.0, 0.001)
        assertEquals(5.0, out["456"] ?: 0.0, 0.001)
    }

    @Test
    fun `tiktoken encodes text into token ids`() {
        val out = LogitBiasEngine.compute("gpt-4o", listOf(BiasEntry(text = " hello", value = -25.0)))
        assertTrue(out.isNotEmpty())
        assertTrue(out.values.all { it == -25.0 })
    }

    @Test
    fun `sentencepiece and web families return empty like unavailable tokenizer`() {
        assertTrue(LogitBiasEngine.compute("gemini-2.5-pro", listOf(BiasEntry(text = "x", value = 1.0))).isEmpty())
        assertTrue(LogitBiasEngine.compute("llama-3.3-70b", listOf(BiasEntry(text = "x", value = 1.0))).isEmpty())
    }
}
