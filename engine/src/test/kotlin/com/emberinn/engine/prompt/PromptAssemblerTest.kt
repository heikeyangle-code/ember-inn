package com.emberinn.engine.prompt

import com.emberinn.engine.macros.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    @Test
    fun `default story renders conditionals`() {
        val out = PromptAssembler.renderStoryString(
            StoryParams(
                system = "系统提示",
                description = "描述",
                personality = "性格",
                scenario = "场景",
                char = "柳春娘",
            ),
        )
        assertEquals("系统提示\n描述\n柳春娘's personality: 性格\nScenario: 场景\n", out)
    }

    @Test
    fun `story skips empty fields`() {
        val out = PromptAssembler.renderStoryString(StoryParams(char = "A"))
        assertEquals("", out)
    }

    @Test
    fun `formatWorldInfo uses zero placeholder`() {
        assertEquals("世界", PromptAssembler.formatWorldInfo("世界"))
        assertEquals("[世界]", PromptAssembler.formatWorldInfo("世界", "[{0}]"))
    }

    @Test
    fun `system prompts ordered and empty entries kept as official`() {
        val prompts = PromptAssembler.buildSystemPrompts(
            charDescription = "描述",
            charPersonality = "性格",
            scenario = "场景",
            worldInfoBefore = "前置",
            worldInfoAfter = "",
            char = "C",
            user = "U",
        )
        assertEquals(
            listOf(
                "worldInfoBefore", "worldInfoAfter", "charDescription", "charPersonality",
                "scenario", "impersonate", "quietPrompt", "groupNudge", "bias",
            ),
            prompts.map { it.identifier },
        )
        assertEquals("前置", prompts.first().content)
        assertEquals("性格", prompts[3].content)
    }

    @Test
    fun `openai messages keeps system flagged messages`() {
        val chat = listOf(
            ChatMessage(mes = "旁白", isUser = false, name = "旁白", isSystem = true),
            ChatMessage(mes = "问", isUser = true, name = "User"),
            ChatMessage(mes = "答", isUser = false, name = "Char"),
        )
        val out = PromptAssembler.toOpenAiMessages(chat)
        assertEquals(3, out.size)
        // 官方：is_system 不跳过；角色仍按 is_user 判定（narrator 才转 system）；输出新的在前
        assertEquals("assistant", out[0].role)
        assertEquals("user", out[1].role)
        assertEquals("assistant", out[2].role)
        assertEquals("答", out[0].content)
        assertEquals("问", out[1].content)
        assertEquals("旁白", out[2].content)
    }

    @Test
    fun `openai messages prefix per names behavior`() {
        val chat = listOf(
            ChatMessage(mes = "你好", isUser = true, name = "玩家"),
            ChatMessage(mes = "你好呀", isUser = false, name = "柳春娘"),
        )
        val none = PromptAssembler.toOpenAiMessages(chat, PromptAssembler.NAMES_NONE)
        assertEquals("你好呀", none[0].content)
        assertEquals("assistant", none[0].role)
        assertEquals("你好", none[1].content)
        assertEquals("user", none[1].role)

        val content = PromptAssembler.toOpenAiMessages(chat, PromptAssembler.NAMES_CONTENT)
        assertEquals("柳春娘: 你好呀", content[0].content)
        assertEquals("玩家: 你好", content[1].content)
    }
}
