package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方预设打包完整性：数量与必需字段（由 scripts/build-presets.mjs 从
 * SillyTavern 1.18.0 default/content/presets 打包，官方更新后重新生成）。
 */
class PresetLibraryTest {

    @Test
    fun `official presets all load with required fields`() {
        val contexts = PresetLibrary.contextPresets()
        assertEquals(34, contexts.size)
        contexts.forEach { c ->
            assertFalse("context preset name blank", c.preset.isBlank())
            assertTrue("context preset story_string blank: ${c.preset}", c.storyString.isNotBlank())
        }
        assertEquals(34, contexts.map { it.preset }.distinct().size)

        val instructs = PresetLibrary.instructPresets()
        assertEquals(38, instructs.size)
        instructs.forEach { i ->
            assertFalse("instruct preset name blank", i.preset.isBlank())
        }
        assertEquals(38, instructs.map { it.preset }.distinct().size)

        assertEquals(1, PresetLibrary.samplerPresets("openai").size)
        assertEquals(6, PresetLibrary.samplerPresets("textgen").size)
        assertEquals(24, PresetLibrary.samplerPresets("novel").size)
        assertEquals(6, PresetLibrary.samplerPresets("kobold").size)
        PresetLibrary.samplerPresets("openai").forEach { s ->
            assertFalse("sampler preset name blank", s.name.isBlank())
        }

        assertEquals(13, PresetLibrary.systemPromptPresets().size)
        assertEquals(5, PresetLibrary.reasoningPresets().size)

        assertTrue("quick-replies preset missing", PresetLibrary.quickRepliesPresets().isNotEmpty())
    }
}
