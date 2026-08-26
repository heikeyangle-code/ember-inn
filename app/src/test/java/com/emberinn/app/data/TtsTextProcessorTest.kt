package com.emberinn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁 TTS 朗读文本处理（对齐官方 tts 扩展 index.js processAndQueueTtsMessage）。 */
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

    @Test
    fun `dialoguesOnly removes asterisk-wrapped content instead of bare stars`() {
        val out = TtsTextProcessor.prepare(
            "*动作描写* 你好 \"台词\"",
            skipCodeblocks = false, skipTags = false, applyRegex = false, regexPattern = "",
            passAsterisks = false, dialoguesOnly = true,
        )
        // 官方 index.js:686：/\*[^*]*?(\*|$)/g 去整段星号内容，引号台词保留
        assertFalse(out.contains("动作"))
        assertFalse(out.contains("*"))
        assertTrue(out.contains("你好"))
        assertTrue(out.contains("\"台词\""))
    }

    @Test
    fun `quotedOnly joins quoted blocks with official separator`() {
        val out = TtsTextProcessor.prepare(
            "旁白一 \"第一句\" 旁白二 「第二句」",
            skipCodeblocks = false, skipTags = false, applyRegex = false, regexPattern = "",
            quotedOnly = true,
        )
        // 官方 index.js:702：separator ' ... '，includeQuotes=true
        assertEquals("\"第一句\" ... 「第二句」", out)
    }

    @Test
    fun `joinQuotedBlocks collects outermost pairs and passes through without quotes`() {
        // 最外层收集（内层不同对不拆分）
        assertEquals("「外 \"内\" 尾」", TtsTextProcessor.joinQuotedBlocks("「外 \"内\" 尾」"))
        assertEquals("\"a\" ... \"b\"", TtsTextProcessor.joinQuotedBlocks("\"a\" mid \"b\""))
        // 无引号返回原文；returnEmptyOnNoQuotes 返回空串
        assertEquals("no quotes", TtsTextProcessor.joinQuotedBlocks("no quotes"))
        assertEquals("", TtsTextProcessor.joinQuotedBlocks("no quotes", returnEmptyOnNoQuotes = true))
    }
}
