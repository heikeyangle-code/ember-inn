package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 总装器（PromptPipeline.populate = 生产唯一组装路径）行为测试。
 * ChatCompletionPipeline 旧副本已删除，避免双实现分叉。
 */
class PromptPipelineAssemblerTest {

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

    private fun populate(
        prompts: PromptItems,
        cc: ChatCompletion,
        handler: TokenHandler,
        type: String = "normal",
        messages: List<PromptMessage> = emptyList(),
        messageExamples: List<List<ExampleMessage>> = emptyList(),
        newChatPrompt: String = "[新对话]",
        newExampleChatPrompt: String = "[示例]",
        selectedGroup: Boolean = false,
        cyclePrompt: String = "",
    ) {
        PromptPipeline.populate(
            chatCompletion = cc,
            handler = handler,
            input = PromptPipeline.PopulateInput(
                prompts = prompts,
                messages = messages,
                messageExamples = messageExamples,
                bias = "预填充",
                type = type,
                cyclePrompt = cyclePrompt,
                env = env,
                newChatPrompt = newChatPrompt,
                newExampleChatPrompt = newExampleChatPrompt,
                selectedGroup = selectedGroup,
            ),
        )
    }

    @Test
    fun `pipeline assembles prompts in official order`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = preparePrompts()
        populate(
            prompts = prompts,
            cc = cc,
            handler = handler,
            // App/官方 setOpenAIMessages 传入的是“新的在前”
            messages = listOf(
                PromptMessage("assistant", "回应"),
                PromptMessage("user", "你好"),
            ),
            messageExamples = listOf(listOf(ExampleMessage("玩家", "示例"))),
        )

        val chat = cc.getChat()
        assertEquals(
            listOf(
                "main", "summary", "worldInfoBefore", "charDescription",
                "newChat", "dialogueExamples 0-0",
                "newMainChat", "chatHistory-1", "chatHistory-2",
                "bias",
            ),
            chat.map { it.identifier },
        )
        assertEquals("记忆", chat[1].content)
        assertEquals("前置", chat[2].content)
    }

    @Test
    fun `extension with end position injects into main`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = preparePrompts()
        populate(
            prompts = prompts,
            cc = cc,
            handler = handler,
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
        populate(
            prompts = prompts,
            cc = cc,
            handler = handler,
            messages = listOf(PromptMessage("user", "你好")),
            selectedGroup = true,
        )
        val hist = (cc.entries[cc.findMessageIndex("chatHistory")] as ChatEntry.Collection).collection.items
        assertTrue(hist.last().identifier == "groupNudge")
        assertTrue(hist.last().content.startsWith("[Write the next reply only as 柳春娘.]"))
    }

    @Test
    fun `in-chat injection inserts absolute prompts by depth`() {
        // 官方：入参新的在前（setOpenAIMessages 输出），函数内部按深度插入后整体 reverse
        val messages = listOf(
            PromptMessage("user", "C"),
            PromptMessage("assistant", "B"),
            PromptMessage("user", "A"),
        )
        val absolute = listOf(
            PromptItem("p1", "P1", content = "S1", role = "system", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 1, injectionOrder = 100),
            PromptItem("p2", "P2", content = "U1", role = "user", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 2, injectionOrder = 50),
        )
        val out = PromptPipeline.populationInjectionPrompts(absolute, messages)
        assertEquals(listOf("A", "U1", "B", "S1", "C"), out.map { it.content })
    }

    @Test
    fun `in-chat injection sorts orders descending and merges extensions at 100`() {
        val absolute = listOf(
            PromptItem("p1", "P1", content = "LOW", role = "system", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 0, injectionOrder = 100),
            PromptItem("p2", "P2", content = "HIGH", role = "system", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 0, injectionOrder = 200),
        )
        val ext = listOf(
            PromptItem("x", "X", content = "EXT", role = "system", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 0, injectionOrder = 100),
        )
        val out = PromptPipeline.populationInjectionPrompts(
            absolute,
            listOf(PromptMessage("user", "M")),
            ext,
        )
        assertEquals(listOf("M", "LOW\nEXT", "HIGH"), out.map { it.content })
    }

    @Test
    fun `continue nudge via pipeline moves last message and appends nudge`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(10000, 0)
        val prompts = PromptItems(
            listOf(PromptItem("chatHistory", "Chat History", marker = true)),
        )
        populate(
            prompts = prompts,
            cc = cc,
            handler = handler,
            type = "continue",
            messages = listOf(
                PromptMessage("assistant", "旧回复"),
                PromptMessage("user", "问"),
            ),
            cyclePrompt = "旧回复",
        )
        val chat = cc.getChat().map { it.content }
        // 历史里只剩用户消息 + newChat；末尾集合 = 旧回复 + nudge
        assertEquals(listOf("[新对话]", "问", "旧回复", "[Continue your last message without repeating its original content.]"), chat)
    }

    @Test
    fun `persona in chat injects at depth`() {
        val injected = PromptPipeline.populationInjectionPrompts(
            absolutePrompts = emptyList(),
            messages = listOf(PromptMessage("assistant", "回应"), PromptMessage("user", "你好")),
            inChatExtensions = listOf(
                PromptItem("personaDescription", "Persona", content = "人设文本", role = "system", injectionPosition = PromptInjection.ABSOLUTE, injectionDepth = 1, injectionOrder = 100),
            ),
        )
        assertEquals(listOf("你好", "人设文本", "回应"), injected.map { it.content })
    }

    @Test
    fun `in chat extensions are injected through prepare pipeline`() {
        val result = PromptPipeline.prepare(
            PromptPipeline.PrepareInput(
                name2 = "柳春娘",
                charDescription = "描述",
                messages = listOf(
                    PromptMessage("user", "你好"),
                    PromptMessage("assistant", "回应"),
                ),
                env = env,
                maxContextTokens = 10000,
                maxTokens = 256,
                tokenCounter = TokenCounter { it.length },
                inChatExtensions = listOf(
                    PromptItem("groupDepthPrompt0", "群聊深度提示 1", content = "群聊深度提示文本", role = "system", injectionDepth = 1, injectionOrder = 100),
                ),
            ),
        )
        assertTrue(result.messages.any { it.content == "群聊深度提示文本" })
    }

    @Test
    fun `negative depth in chat extension is ignored`() {
        val injected = PromptPipeline.populationInjectionPrompts(
            absolutePrompts = emptyList(),
            messages = listOf(PromptMessage("assistant", "回应"), PromptMessage("user", "你好")),
            inChatExtensions = listOf(
                PromptItem("neg", "负深度", content = "不应出现", role = "system", injectionDepth = -1, injectionOrder = 100),
            ),
        )
        assertTrue(injected.none { it.content == "不应出现" })
    }
}
