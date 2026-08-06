package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueExamplesPopulatorTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    private fun prompts() = PromptItems(
        listOf(PromptItem("dialogueExamples", "Chat Examples", marker = true)),
    )

    @Test
    fun `inserts example group after marker with newChat first`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(1000, 0)
        DialogueExamplesPopulator.populate(
            chatCompletion = cc,
            handler = handler,
            prompts = prompts(),
            dialogues = listOf(
                listOf(ExampleMessage("玩家", "你好"), ExampleMessage("柳春娘", "你好呀")),
            ),
            newExampleChatPrompt = "[Example Chat]",
            env = env,
        )
        val items = (cc.entries[0] as ChatEntry.Collection).collection.items
        assertEquals(listOf("newChat", "dialogueExamples 0-0", "dialogueExamples 0-1"), items.map { it.identifier })
        assertEquals("玩家", items[1].name)
        assertEquals("柳春娘", items[2].name)
    }

    @Test
    fun `breaks when group cannot afford`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(5, 0)
        DialogueExamplesPopulator.populate(
            chatCompletion = cc,
            handler = handler,
            prompts = prompts(),
            dialogues = listOf(
                listOf(ExampleMessage("玩家", "很长很长很长很长很长")),
            ),
            newExampleChatPrompt = "[Example Chat]",
            env = env,
        )
        assertEquals(0, cc.getChat().size)
    }
}
