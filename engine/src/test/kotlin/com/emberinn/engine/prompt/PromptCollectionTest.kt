package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCollectionTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `default order matches official`() {
        val ids = PromptCollection.getCollection(env).map { it.identifier }
        assertEquals(
            listOf("main", "worldInfoBefore", "personaDescription", "charDescription", "charPersonality", "scenario", "nsfw", "worldInfoAfter", "dialogueExamples", "chatHistory", "jailbreak"),
            ids,
        )
    }

    @Test
    fun `main prompt macros substituted`() {
        val main = PromptCollection.getCollection(env).first()
        assertEquals("Write 柳春娘's next reply in a fictional chat between 柳春娘 and 玩家.", main.content)
    }

    @Test
    fun `merge system prompts into markers`() {
        val merged = PromptCollection.mergeSystemPrompts(
            PromptCollection.getCollection(env),
            listOf(
                PromptMessage("system", "世界前置", identifier = "worldInfoBefore"),
                PromptMessage("system", "角色描述", identifier = "charDescription"),
            ),
        )
        val wi = merged.first { it.identifier == "worldInfoBefore" }
        val desc = merged.first { it.identifier == "charDescription" }
        assertEquals("世界前置", wi.content)
        assertEquals("角色描述", desc.content)
        assertTrue(merged.first { it.identifier == "main" }.content.startsWith("Write"))
    }
}
