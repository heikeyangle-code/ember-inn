package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetLibraryTest {

    @Test
    fun `instruct presets load from official bundle`() {
        val presets = PresetLibrary.instructPresets()
        assertEquals(38, presets.size)
        val alpaca = presets.first { it.preset == "Alpaca" }
        assertEquals("### Instruction:", alpaca.inputSequence)
        assertEquals("### Response:", alpaca.outputSequence)
        assertEquals(NamesBehavior.FORCE, alpaca.namesBehavior)
        assertTrue(alpaca.wrap)
    }

    @Test
    fun `context presets load with story strings`() {
        val presets = PresetLibrary.contextPresets()
        assertEquals(34, presets.size)
        val default = presets.first { it.preset == "Default" }
        assertTrue(default.storyString.contains("{{#if system}}"))
        assertEquals("***", default.chatStart)
    }

    @Test
    fun `sampler presets load for each api`() {
        assertEquals(1, PresetLibrary.samplerPresets("openai").size)
        assertEquals(6, PresetLibrary.samplerPresets("textgen").size)
        assertEquals(24, PresetLibrary.samplerPresets("novel").size)
        assertEquals(6, PresetLibrary.samplerPresets("kobold").size)
        val novel = PresetLibrary.samplerPresets("novel").first()
        assertTrue(novel.name.isNotEmpty())
        assertTrue(novel.settings["temperature"] != null)
    }

    @Test
    fun `sysprompt and reasoning presets load`() {
        assertEquals(13, PresetLibrary.systemPromptPresets().size)
        assertEquals(5, PresetLibrary.reasoningPresets().size)
    }
}
