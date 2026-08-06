package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionPipelineTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    private fun preparePrompts(
        selectedGroup: Boolean = false,
    ): PromptItems = PromptAssembler.preparePromptsForChatCompletion(
        scenario = "",
        charPersonality = "",
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

    @Test
    fun `pipeline assembles prompts in official order`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = preparePrompts()
        ChatCompletionPipeline.populate(
            prompts = prompts,
            chatCompletion = cc,
            handler = handler,
            env = env,
            type = "normal",
            messages = listOf(
                PromptMessage("user", "你好"),
                PromptMessage("assistant", "回应"),
            ),
            messageExamples = listOf(listOf(ExampleMessage("玩家", "示例"))),
            newChatPrompt = "[新对话]",
            newExampleChatPrompt = "[示例]",
        )

        val chat = cc.getChat()
        assertEquals(
            listOf(
                "main", "summary", "worldInfoBefore", "charDescription",
                "newChat", "dialogueExamples 0-0",
                "newMainChat", "chatHistory", "chatHistory",
                "bias",
            ),
            chat.map { it.identifier },
        )
        assertEquals("记忆", chat[1].content)
        assertEquals("前置", chat[2].content)
    }

    @Test
    fun `extension with start position injects into main`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = preparePrompts()
        // 记忆扩展在 PromptManager 合并后带 position=end（默认 IN_PROMPT）
        ChatCompletionPipeline.populate(
            prompts = prompts,
            chatCompletion = cc,
            handler = handler,
            env = env,
            type = "normal",
            messages = emptyList(),
            messageExamples = emptyList(),
            newChatPrompt = "[新对话]",
            newExampleChatPrompt = "[示例]",
        )
        val main = (cc.entries[cc.findMessageIndex("main")] as ChatEntry.Collection).collection.items
        // summary 注入 main 末尾
        assertEquals("summary", main.last().identifier)
        assertEquals("记忆", main.last().content)
    }

    @Test
    fun `group nudge appended to chat history`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = preparePrompts()
        ChatCompletionPipeline.populate(
            prompts = prompts,
            chatCompletion = cc,
            handler = handler,
            env = env,
            type = "normal",
            messages = listOf(PromptMessage("user", "你好")),
            messageExamples = emptyList(),
            newChatPrompt = "[新对话]",
            newExampleChatPrompt = "[示例]",
            selectedGroup = true,
        )
        val hist = (cc.entries[cc.findMessageIndex("chatHistory")] as ChatEntry.Collection).collection.items
        assertTrue(hist.last().identifier == "groupNudge")
        assertTrue(hist.last().content.startsWith("[Write the next reply only as 柳春娘.]"))
    }
}
