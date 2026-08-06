package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerCompletionTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `system prompts merge into markers with bias and extensions`() {
        val merged = PromptAssembler.preparePromptsForChatCompletion(
            scenario = "场景",
            charPersonality = "性格",
            name2 = "柳春娘",
            worldInfoBefore = "前置",
            worldInfoAfter = "",
            charDescription = "描述",
            quietPrompt = "",
            bias = "预填充",
            extensionPrompts = mapOf(
                "1_memory" to ExtensionPrompt("1_memory", "system", "记忆"),
            ),
            systemPromptOverride = "",
            jailbreakPromptOverride = "",
            type = "normal",
            userOrder = emptyList(),
            userPrompts = emptyList(),
            env = env,
        )

        val wi = merged.collection.first { it.identifier == "worldInfoBefore" }
        val desc = merged.collection.first { it.identifier == "charDescription" }
        val persona = merged.collection.first { it.identifier == "charPersonality" }
        assertEquals("前置", wi.content)
        assertEquals("描述", desc.content)
        assertEquals("柳春娘's personality: 性格", persona.content)
        assertEquals("bias", merged.collection.first { it.identifier == "bias" }.identifier)
        assertEquals("assistant", merged.collection.first { it.identifier == "bias" }.role)
        assertEquals("预填充", merged.collection.first { it.identifier == "bias" }.content)
        assertEquals("记忆", merged.collection.first { it.identifier == "summary" }.content)
    }

    @Test
    fun `main override substitutes original`() {
        val merged = PromptAssembler.preparePromptsForChatCompletion(
            scenario = "",
            charPersonality = "",
            name2 = "柳春娘",
            worldInfoBefore = "",
            worldInfoAfter = "",
            charDescription = "",
            quietPrompt = "",
            bias = "",
            extensionPrompts = emptyMap(),
            systemPromptOverride = "重写：{{char}}（原：{{original}}）",
            jailbreakPromptOverride = "",
            type = "normal",
            userOrder = emptyList(),
            userPrompts = emptyList(),
            env = env,
        )
        val main = merged.collection.first { it.identifier == "main" }
        assertTrue(main.content.startsWith("重写：柳春娘（原：Write 柳春娘's next reply"))
        assertTrue("main" in merged.overriddenPrompts)
    }

    @Test
    fun `disabled main is not overridden`() {
        val merged = PromptAssembler.preparePromptsForChatCompletion(
            scenario = "",
            charPersonality = "",
            name2 = "柳春娘",
            worldInfoBefore = "",
            worldInfoAfter = "",
            charDescription = "",
            quietPrompt = "",
            bias = "",
            extensionPrompts = emptyMap(),
            systemPromptOverride = "不应生效",
            jailbreakPromptOverride = "",
            type = "normal",
            userOrder = listOf(PromptOrderEntry("main", enabled = false)),
            userPrompts = emptyList(),
            env = env,
        )
        val main = merged.collection.first { it.identifier == "main" }
        assertEquals("", main.content)
        assertTrue(merged.overriddenPrompts.isEmpty())
    }

    @Test
    fun `jailbreak override applies`() {
        val merged = PromptAssembler.preparePromptsForChatCompletion(
            scenario = "",
            charPersonality = "",
            name2 = "柳春娘",
            worldInfoBefore = "",
            worldInfoAfter = "",
            charDescription = "",
            quietPrompt = "",
            bias = "",
            extensionPrompts = emptyMap(),
            systemPromptOverride = "",
            jailbreakPromptOverride = "禁止剧透",
            type = "normal",
            userOrder = emptyList(),
            userPrompts = emptyList(),
            env = env,
        )
        val jailbreak = merged.collection.first { it.identifier == "jailbreak" }
        assertEquals("禁止剧透", jailbreak.content)
        assertTrue("jailbreak" in merged.overriddenPrompts)
    }
}
