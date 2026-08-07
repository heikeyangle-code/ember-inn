package com.emberinn.engine.macros

import org.junit.Assert.assertEquals
import org.junit.Test

/** 通用作用域宏测试（对齐官方 MacroCstWalker.processScopedMacros + MacroEngine.trimScopedContent）。 */
class MacroScopedTest {

    private val store = MemoryVariableStore()

    private fun env() = MacroEnv(
        user = "User",
        char = "Char",
        local = store,
        global = MemoryVariableStore(),
    )

    @Test
    fun `setvar scoped stores content as last argument`() {
        val out = MacroEngine.substitute("{{setvar::x}}你好{{/setvar}}{{getvar::x}}", env())
        assertEquals("你好", out)
    }

    @Test
    fun `hash flag preserves whitespace`() {
        val out = MacroEngine.substitute("{{#setvar::x}}  有空格  {{/setvar}}{{getvar::x}}", env())
        assertEquals("  有空格  ", out)
    }

    @Test
    fun `default trims and dedents scoped content`() {
        val out = MacroEngine.substitute(
            "{{setvar::x}}\n    行一\n    行二\n{{/setvar}}{{getvar::x}}",
            env(),
        )
        assertEquals("行一\n行二", out)
    }

    @Test
    fun `nested scoped macros evaluate innermost first`() {
        val input = "{{setvar::a}}A{{setvar::b}}B{{/setvar}}C{{/setvar}}{{getvar::a}}|{{getvar::b}}"
        val out = MacroEngine.substitute(input, env())
        println("DEBUG nested: input=$input")
        println("DEBUG nested: out=$out")
        println("DEBUG nested: store.a=${store.get("a")} store.b=${store.get("b")}")
        assertEquals("AC|B", out)
    }

    @Test
    fun `unmatched closing stays raw`() {
        val out = MacroEngine.substitute("前{{/setvar}}后", env())
        assertEquals("前{{/setvar}}后", out)
    }

    @Test
    fun `empty scoped content stores empty value`() {
        val out = MacroEngine.substitute("{{setvar::x}}{{/setvar}}{{getvar::x}}", env())
        assertEquals("", out)
    }
}
