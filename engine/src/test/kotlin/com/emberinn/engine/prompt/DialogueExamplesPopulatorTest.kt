package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueExamplesPopulatorTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `inserts example group after marker`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(1000, 0)
        DialogueExamplesPopulator.populate(
            cc, handler,
            listOf(listOf(ExampleMessage("玩家", "你好"), ExampleMessage("柳春娘", "你好呀"))),
            "[Example Chat]",
            env,
        )
        val ids = cc.messages.map { it.identifier }
        assertEquals("dialogueExamples", ids.first())
        assertEquals("newChat", ids[1])
        assertEquals("dialogueExamples 0-0", ids[2])
        assertEquals("dialogueExamples 0-1", ids[3])
        assertEquals(2, cc.messages[2].name?.let { 1 } ?: 0 + 1)
    }

    @Test
    fun `breaks when group cannot afford`() {
        val handler = TokenHandler(TokenCounter { it.length })
        val cc = ChatCompletion(handler)
        cc.setTokenBudget(5, 0)
        DialogueExamplesPopulator.populate(
            cc, handler,
            listOf(listOf(ExampleMessage("玩家", "很长很长很长很长很长"))),
            "[Example Chat]",
            env,
        )
        // 只应有 marker，组放不下
        assertEquals(1, cc.messages.size)
    }
}
