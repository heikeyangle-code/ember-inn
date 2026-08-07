package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.macros.MemoryVariableStore
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对齐官方 populateChatHistory：每条历史消息过 preparePrompt（宏替换）。 */
class ChatHistoryPrepareTest {

    private fun prompts(): PromptItems {
        val items = PromptItems()
        items.add(PromptItem(identifier = "chatHistory", name = "chatHistory", role = "user", content = ""))
        return items
    }

    private fun env() = MacroEnv(
        user = "小明",
        char = "小红",
        local = MemoryVariableStore(),
        global = MemoryVariableStore(),
    )

    @Test
    fun `history messages get macro substituted`() {
        val tokenHandler = TokenHandler(TokenCounter { it.length })
        val chatCompletion = ChatCompletion(tokenHandler)
        chatCompletion.setTokenBudget(10000, 0)
        ChatHistoryPopulator.populate(
            messages = listOf(
                PromptMessage(role = "user", content = "{{char}}你好{{user}}", name = "User"),
            ),
            chatCompletion = chatCompletion,
            prompts = prompts(),
            handler = tokenHandler,
            type = "normal",
            newChatPrompt = "",
            env = env(),
        )
        // newChatPrompt 空 → 仍插入空 newMainChat
        val chat = chatCompletion.getChat()
        val text = chat.joinToString(" ") { it.content }
        assertTrue("实际: $text", text.contains("小红你好小明"))
        assertTrue("实际: $text", !text.contains("{{"))
    }
}
