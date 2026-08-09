package com.emberinn.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁 TTS 朗读文本处理（对齐官方 tts 扩展 index.js）。 */
class TtsTextProcessorTest {

    @Test
    fun `prepares text per official tts extension`() {
        val text = "```code``` 你好 *世界* <b>标签</b> ![img](url) 多   空格"
        val out = TtsTextProcessor.prepare(
            text = text,
            skipCodeblocks = true,
            skipTags = true,
            applyRegex = true,
            regexPattern = "/多\\s+空格/",
        )
        assertFalse(out.contains("```"))
        assertFalse(out.contains("<b>"))
        assertFalse(out.contains("*"))
        assertFalse(out.contains("![img]"))
        assertFalse(out.contains("多"))
        assertFalse(out.contains("   "))
        assertTrue(out.contains("你好"))
    }

    @Test
    fun `keeps asterisks when passAsterisks enabled`() {
        val out = TtsTextProcessor.prepare("*你好*", skipCodeblocks = false, skipTags = false, applyRegex = false, regexPattern = "", passAsterisks = true)
        assertTrue(out.contains("*"))
    }

    @Test
    fun `invalid regex falls back to original text`() {
        val out = TtsTextProcessor.prepare("你好", skipCodeblocks = false, skipTags = false, applyRegex = true, regexPattern = "[")
        assertTrue(out.contains("你好"))
    }
}
