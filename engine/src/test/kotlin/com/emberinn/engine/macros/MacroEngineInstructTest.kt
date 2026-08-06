package com.emberinn.engine.macros

import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.NamesBehavior
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroEngineInstructTest {

    private val env = MacroEnv(
        user = "User",
        char = "Char",
        instruct = InstructSettings(
            enabled = true,
            inputSequence = "### Instruction:",
            outputSequence = "### Response:",
            systemSequence = "### Input:",
            lastSystemSequence = "### System:",
            stopSequence = "<end>",
            userAlignmentMessage = "对齐",
            namesBehavior = NamesBehavior.FORCE,
        ),
        context = ContextSettings(chatStart = "***", exampleSeparator = "---"),
        systemPromptContent = "系统提示",
        systemPromptEnabled = true,
        preferCharacterPrompt = true,
        character = CharacterFields(charPrompt = "角色覆盖"),
    )

    @Test
    fun `legacy trim removes macro and surrounding newlines`() {
        assertEquals("abc", MacroEngine.substitute("\n\n{{trim}}\n\nabc", env))
        assertEquals("ab", MacroEngine.substitute("a{{trim}}b", env))
    }

    @Test
    fun `trim with args is a utility macro`() {
        assertEquals("x", MacroEngine.substitute("{{trim:: x }}", env))
    }

    @Test
    fun `instruct macros resolve when enabled`() {
        assertEquals("### Instruction:", MacroEngine.substitute("{{instructUserPrefix}}", env))
        assertEquals("### Response:", MacroEngine.substitute("{{instructOutput}}", env))
        assertEquals("### Input:", MacroEngine.substitute("{{instructSystemPrefix}}", env))
        assertEquals("<end>", MacroEngine.substitute("{{instructStop}}", env))
        assertEquals("对齐", MacroEngine.substitute("{{instructUserFiller}}", env))
    }

    @Test
    fun `instruct macros are empty when disabled`() {
        val disabled = env.copy(instruct = env.instruct!!.copy(enabled = false))
        assertEquals("", MacroEngine.substitute("{{instructUserPrefix}}", disabled))
    }

    @Test
    fun `system and context macros resolve`() {
        assertEquals("角色覆盖", MacroEngine.substitute("{{systemPrompt}}", env))
        assertEquals("系统提示", MacroEngine.substitute("{{defaultSystemPrompt}}", env))
        assertEquals("***", MacroEngine.substitute("{{chatStart}}", env))
        assertEquals("---", MacroEngine.substitute("{{chatSeparator}}", env))
    }
}
