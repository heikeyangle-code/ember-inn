package com.emberinn.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 显示管线纯逻辑：对齐官方 power-user.js fixMarkdown(forDisplay=true) + encode_tags。 */
class DisplayPipelineTest {

    @Test
    fun `paired emphasis keeps content`() {
        assertEquals("**bold**", DisplayPipeline.fixMarkdown("**bold**"))
        assertEquals("_italic_", DisplayPipeline.fixMarkdown("_italic_"))
    }

    @Test
    fun `spaces beside emphasis markers are removed`() {
        assertEquals("*spaced*", DisplayPipeline.fixMarkdown("* spaced *"))
        assertEquals("_spaced_", DisplayPipeline.fixMarkdown("_ spaced _"))
        assertEquals("a *b* c", DisplayPipeline.fixMarkdown("a * b * c"))
    }

    @Test
    fun `odd asterisk or quote gets one appended at line end`() {
        assertEquals("abc*def*", DisplayPipeline.fixMarkdown("abc*def"))
        assertEquals("他说\"你好\"", DisplayPipeline.fixMarkdown("他说\"你好"))
        // 偶数个不改
        assertEquals("abc*def*", DisplayPipeline.fixMarkdown("abc*def*"))
    }

    @Test
    fun `encode tags escapes angle brackets`() {
        assertEquals("&lt;b&gt;hi&lt;/b&gt;", DisplayPipeline.encodeTags("<b>hi</b>"))
    }
}
