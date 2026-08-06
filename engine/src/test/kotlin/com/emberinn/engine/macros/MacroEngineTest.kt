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
        assertEquals("{{unknownmacro::x}}", MacroEngine.substitute("{{unknownmacro::x}}", env))
    }

    @Test
    fun `scoped if truthy and else branch`() {
        assertEquals("有用户", MacroEngine.substitute("{{if user}}有用户{{else}}没有{{/if}}", env))
        assertEquals("没有", MacroEngine.substitute("{{if 0}}有{{else}}没有{{/if}}", env))
    }

    @Test
    fun `scoped if inverted`() {
        assertEquals("有", MacroEngine.substitute("{{if !user}}没有{{else}}有{{/if}}", env))
    }

    @Test
    fun `nested scoped if`() {
        assertEquals("ABC", MacroEngine.substitute("{{if user}}A{{if char}}B{{/if}}C{{/if}}", env))
    }

    @Test
    fun `inline if macro`() {
        assertEquals("你好", MacroEngine.substitute("{{if user::你好}}", env))
        assertEquals("", MacroEngine.substitute("{{if 0::你好}}", env))
    }

    @Test
    fun `space form roll works`() {
        repeat(20) {
            val v = MacroEngine.substitute("{{roll 1d6}}", env).toInt()
            assertTrue(v in 1..6)
        }
    }
}


    @Test
    fun `variable macros set get inc dec and shorthand`() {
        val local = MemoryVariableStore()
        val env2 = env.copy(local = local)

        assertEquals("", MacroEngine.substitute("{{setvar::hp::100}}", env2))
        assertEquals("100", MacroEngine.substitute("{{getvar::hp}}", env2))
        assertEquals("101", MacroEngine.substitute("{{incvar::hp}}", env2))
        assertEquals("100", MacroEngine.substitute("{{decvar::hp}}", env2))
        assertEquals("true", MacroEngine.substitute("{{hasvar::hp}}", env2))
        assertEquals("100", MacroEngine.substitute("{{.hp}}", env2))
    }

    @Test
    fun `addvar numeric and string append`() {
        val local = MemoryVariableStore()
        val env2 = env.copy(local = local)
        MacroEngine.substitute("{{setvar::n::5}}", env2)
        MacroEngine.substitute("{{addvar::n::3}}", env2)
        assertEquals("8", MacroEngine.substitute("{{getvar::n}}", env2))
        MacroEngine.substitute("{{addvar::s::ab}}", env2)
        assertEquals("0ab", MacroEngine.substitute("{{getvar::s}}", env2))
    }

    @Test
    fun `if with variable condition`() {
        val local = MemoryVariableStore()
        local.set("flag", "true")
        val env2 = env.copy(local = local)
        assertEquals("开了", MacroEngine.substitute("{{if .flag}}开了{{else}}关了{{/if}}", env2))
    }
