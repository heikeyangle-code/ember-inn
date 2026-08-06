package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryPopulatorTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    private fun prompts() = PromptItems(
        listOf(PromptItem("chatHistory", "Chat History", marker = true)),
    )

    @Test
    fun `chat history is chronological with newChat first`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        val messages = listOf(
            PromptMessage("user", "先"),
            PromptMessage("assistant", "后"),
        )
        ChatHistoryPopulator.populate(
            messages = messages,
            chatCompletion = cc,
            prompts = prompts(),
            handler = handler,
            type = "normal",
            newChatPrompt = "新对话",
            env = env,
        )
        val chat = cc.getChat()
        assertEquals(listOf("newMainChat", "chatHistory", "chatHistory"), chat.map { it.identifier })
        assertEquals(listOf("新对话", "先", "后"), chat.map { it.content })
        // 100 - 3(预留) - 2(两条消息) + 3(归还) - 3(newChat插入) = 95
        assertEquals(95, cc.tokenBudget)
    }

    @Test
    fun `stops when budget exhausted`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(20, 0)
        val messages = listOf(
            PromptMessage("user", "一二三四五六七八九十"),
            PromptMessage("assistant", "一二三四五六七八九十"),
        )
        ChatHistoryPopulator.populate(
            messages = messages,
            chatCompletion = cc,
            prompts = prompts(),
            handler = handler,
            type = "normal",
            newChatPrompt = "新",
            env = env,
        )
        // 预算 20 - 3 预留 - 1 newChat = 16，第一条 10 放得下，第二条 10 放不下
        assertEquals(listOf("新", "一二三四五六七八九十"), cc.getChat().map { it.content })
    }

    @Test
    fun `new chat prompt macro substituted`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(100, 0)
        ChatHistoryPopulator.populate(
            messages = emptyList(),
            chatCompletion = cc,
            prompts = prompts(),
            handler = handler,
            type = "normal",
            newChatPrompt = "[Start a new Chat] {{char}}",
            env = env,
        )
        assertEquals(listOf("[Start a new Chat] 柳春娘"), cc.getChat().map { it.content })
    }

    @Test
    fun `tool invocations become tool calls and results`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        ChatHistoryPopulator.populate(
            messages = listOf(
                PromptMessage(
                    role = "assistant",
                    content = "",
                    toolInvocations = listOf(
                        ToolInvocation("call_1", "getWeather", "{\"city\":\"北京\"}", "晴"),
                        ToolInvocation("call_2", "getTime", "{}", "12:00"),
                    ),
                ),
                PromptMessage("user", "后来"),
            ),
            chatCompletion = cc,
            prompts = prompts(),
            handler = handler,
            type = "normal",
            newChatPrompt = "新",
            env = env,
            canUseTools = true,
        )
        val chat = cc.getChat()
        val toolMsg = chat.first { it.toolCalls != null }
        assertEquals(2, toolMsg.toolCalls!!.size)
        assertEquals("getWeather", toolMsg.toolCalls!![0].name)
        assertEquals("call_1", toolMsg.toolCalls!![0].id)
        assertEquals("晴", chat.first { it.role == "tool" }.content)
        assertEquals("call_1", chat.first { it.role == "tool" }.toolCallId)
        assertEquals("后来", chat.last { it.role == "user" }.content)
    }
}
