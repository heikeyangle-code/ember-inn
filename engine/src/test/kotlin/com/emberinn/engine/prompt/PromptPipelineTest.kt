package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPipelineTest {

    private val counter = TokenCounter { it.length / 4 }

    @Test
    fun `parse example block into individual messages`() {
        val block = "This is how Char should talk\nUser: 你好\nChar: 你好呀\nUser: 再见"
        val messages = PromptPipeline.parseExampleIntoIndividual(block, name1 = "User", name2 = "Char")
        // 官方：块内每条 user/bot 行都产出（含末尾用户行）
        assertEquals(3, messages.size)
        assertEquals("example_user", messages[0].name)
        assertEquals("你好", messages[0].content)
        assertEquals("example_assistant", messages[1].name)
        assertEquals("你好呀", messages[1].content)
        assertEquals("example_user", messages[2].name)
        assertEquals("再见", messages[2].content)
    }

    @Test
    fun `set openai message examples from raw blocks`() {
        val blocks = listOf("<START>\nUser: hi\nChar: hello")
        val examples = PromptPipeline.setOpenAIMessageExamples(blocks, "User", "Char")
        assertEquals(1, examples.size)
        assertEquals("hi", examples[0][0].content)
    }

    @Test
    fun `prepare assembles system history and examples in official order`() {
        val env = MacroEnv(user = "User", char = "Char")
        val result = PromptPipeline.prepare(
            PromptPipeline.PrepareInput(
                name2 = "Char",
                charDescription = "描述",
                charPersonality = "性格",
                scenario = "场景",
                worldInfoBefore = "世界书前",
                worldInfoAfter = "世界书后",
                bias = "",
                type = "generate",
                quietPrompt = "",
                messages = listOf(
                    PromptMessage("user", "第一条", name = null, identifier = "chatHistory"),
                    PromptMessage("assistant", "回复一", name = "Char", identifier = "chatHistory"),
                    PromptMessage("user", "第二条", name = null, identifier = "chatHistory"),
                ),
                messageExamples = listOf(listOf(ExampleMessage("example_user", "示例1"))),
                env = env,
                maxContextTokens = 10000,
                maxTokens = 256,
                tokenCounter = counter,
            ),
        )
        val texts = result.messages.map { it.content }
        assertTrue(texts.any { it.contains("世界书前") })
        assertTrue(texts.any { it.contains("描述") })
        assertTrue(texts.any { it.contains("第一条") })
        assertTrue(texts.any { it.contains("示例1") })
        assertTrue(texts.any { it.contains("第二条") })
        // 官方顺序：系统提示在最前，历史在中间，最后是 assistant 回复之后的控制提示
        assertEquals("user", result.messages.first { it.content.contains("第一条") }.role)
        assertEquals("assistant", result.messages.first { it.content.contains("回复一") }.role)
    }

    @Test
    fun `continue prefill shifts first message and prepends prefill for claude`() {
        val env = MacroEnv(user = "User", char = "Char")
        val result = PromptPipeline.prepare(
            PromptPipeline.PrepareInput(
                name2 = "Char",
                messages = listOf(PromptMessage("assistant", "继续这段", name = "Char")),
                env = env,
                maxContextTokens = 10000,
                maxTokens = 256,
                tokenCounter = counter,
                type = "continue",
                continuePrefill = true,
                assistantPrefill = "Sure.",
                chatCompletionSource = "claude",
            ),
        )
        val continueMsg = result.messages.firstOrNull { it.identifier == "continuePrefill" }
        assertTrue(continueMsg != null)
        assertTrue(continueMsg!!.content.startsWith("Sure.\n\n"))
    }
}
