package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryStringRenderTest {

    @Test
    fun `official default context story string renders`() {
        val preset = PresetLibrary.contextPresets().first { it.preset == "Default" }
        val out = PromptAssembler.renderStoryString(
            StoryParams(
                description = "描述",
                personality = "性格",
                persona = "人设",
                scenario = "场景",
                system = "系统",
                char = "角色",
                user = "玩家",
                wiBefore = "前置",
                wiAfter = "后置",
                anchorBefore = "锚前",
                anchorAfter = "锚后",
            ),
            template = preset.storyString,
        )
        assertTrue(out.contains("描述"))
        assertTrue(out.contains("性格"))
        assertTrue(out.contains("人设"))
        assertTrue(out.contains("锚后"))
    }

    @Test
    fun `trim helper removes trailing newlines`() {
        val out = PromptAssembler.renderStoryString(
            StoryParams(description = "文本"),
            template = "{{description}}\n\n{{trim}}",
        )
        assertEquals("文本\n", out)
    }
}
