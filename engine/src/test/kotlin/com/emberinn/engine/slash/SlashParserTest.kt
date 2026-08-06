package com.emberinn.engine.slash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashParserTest {

    @Test
    fun `parses named and unnamed args`() {
        val inv = SlashParser.parse("/sendas name=柳春娘 at=1 你好啊")
        assertEquals("sendas", inv.name)
        assertEquals("柳春娘", inv.namedArgs["name"])
        assertEquals("1", inv.namedArgs["at"])
        assertEquals(listOf("你好啊"), inv.unnamedArgs)
    }

    @Test
    fun `quoted unnamed arg stays whole`() {
        val inv = SlashParser.parse("/sys \"hello world\"")
        assertEquals(listOf("hello world"), inv.unnamedArgs)
    }

    @Test
    fun `quoted named value with spaces`() {
        val inv = SlashParser.parse("/persona name=\"柳 春 娘\"")
        assertEquals("柳 春 娘", inv.namedArgs["name"])
    }

    @Test
    fun `unknown command throws`() {
        try {
            SlashRegistry.execute("/nosuchcmd x")
            assertTrue(false)
        } catch (e: SlashParseException) {
            assertTrue(e.message!!.contains("未知命令"))
        }
    }

    @Test
    fun `builtin continue and sendas execute`() {
        assertEquals("OK:continue", SlashRegistry.execute("/continue"))
        assertEquals("OK:sendas:柳春娘:你好", SlashRegistry.execute("/sendas name=柳春娘 你好"))
    }

    @Test
    fun `help lists commands`() {
        assertTrue(SlashRegistry.execute("/help").contains("/continue"))
    }
}
