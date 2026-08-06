package com.emberinn.engine.slash

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickReplyTest {

    @Test
    fun `execute slot runs slash chain`() {
        val preset = QuickReplyPreset(
            name = "Default",
            slots = listOf(
                QuickReplySlot(mes = "/echo hello world", label = "问候", enabled = true),
                QuickReplySlot(mes = "/pass nope", label = "禁用", enabled = false),
            ),
        )
        assertEquals("hello world", QuickReplyExecutor.execute(preset, "问候"))
        assertEquals("", QuickReplyExecutor.execute(preset, "禁用"))
        assertEquals("", QuickReplyExecutor.execute(preset, "不存在"))
    }

    @Test
    fun `official bundled quick replies load`() {
        val presets = com.emberinn.engine.prompt.PresetLibrary.quickRepliesPresets()
        assertEquals(1, presets.size)
        assertEquals("Default", presets[0].name)
        assertEquals("HELP", presets[0].slots.first().label)
    }
}
