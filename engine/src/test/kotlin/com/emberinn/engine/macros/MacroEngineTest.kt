package com.emberinn.engine.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEngineTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `user and char macros substitute`() {
        assertEquals("玩家和柳春娘", MacroEngine.substitute("{{user}}和{{char}}", env))
    }

    @Test
    fun `iso time and date formats`() {
        val out = MacroEngine.substitute("{{isodate}} {{isotime}}", env)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(out))
    }

    @Test
    fun `random picks one of list`() {
        val list = listOf("a", "b", "c")
        repeat(20) {
            val out = MacroEngine.substitute("{{random::a::b::c}}", env)
            assertTrue(out in list)
        }
    }

    @Test
    fun `roll dice with modifier`() {
        repeat(30) {
            val v = MacroEngine.substitute("{{roll::2d6+4}}", env).toInt()
            assertTrue(v in 6..16)
        }
    }

    @Test
    fun `pick is stable for same input`() {
        val same = MacroEngine.substitute("{{pick::红::蓝::绿}}", env)
        val same2 = MacroEngine.substitute("{{pick::红::蓝::绿}}", env)
        assertEquals(same, same2)
    }

    @Test
    fun `unknown macro stays as-is`() {
        assertEquals("{{if x}}保留{{/if}}", MacroEngine.substitute("{{if x}}保留{{/if}}", env))
    }
}
