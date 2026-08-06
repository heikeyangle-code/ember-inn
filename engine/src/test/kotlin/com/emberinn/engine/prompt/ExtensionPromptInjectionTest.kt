package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionPromptInjectionTest {

    @Test
    fun `injects known extensions in official order`() {
        val injected = ExtensionPromptInjection.inject(
            systemPrompts = emptyList(),
            extensions = mapOf(
                "3_vectors" to ExtensionPrompt("3_vectors", "system", "向量"),
                "1_memory" to ExtensionPrompt("1_memory", "system", "记忆"),
                "2_floating_prompt" to ExtensionPrompt("2_floating_prompt", "system", "作者注释"),
            ),
        )
        assertEquals(listOf("summary", "authorsNote", "vectorsMemory"), injected.map { it.identifier })
    }

    @Test
    fun `skips empty and in-chat unknown extensions`() {
        val injected = ExtensionPromptInjection.inject(
            systemPrompts = emptyList(),
            extensions = mapOf(
                "empty" to ExtensionPrompt("empty", "system", ""),
                "in_chat" to ExtensionPrompt("in_chat", "system", "x", position = "in_chat"),
                "ok" to ExtensionPrompt("ok", "system", "y", position = "start"),
            ),
        )
        assertEquals(listOf("ok"), injected.map { it.identifier })
    }
}
