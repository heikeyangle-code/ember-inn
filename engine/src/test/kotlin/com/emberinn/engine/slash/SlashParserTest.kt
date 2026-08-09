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

    @Test
    fun `rawQuotes keeps quotes as one value per official`() {
        val inv = SlashParser.parse("/echo \"hello world\"", rawQuotes = true)
        assertEquals(listOf("\"hello world\""), inv.unnamedArgs)
    }

    @Test
    fun `split unnamed with count merges the rest into one value`() {
        // 对齐官方 /let：splitUnnamedArgumentCount=1，第一个拆开、其余合并
        val inv = SlashParser.parse(
            "/let key=greeting Hello World",
            splitFor = { name -> (name == "let") to 1 },
        )
        assertEquals(listOf("Hello", "World"), inv.unnamedArgs)
    }

    @Test
    fun `split unnamed with count two keeps two values`() {
        // 对齐官方 /qr-arg：splitUnnamedArgumentCount=2
        val inv = SlashParser.parse(
            "/qr-arg hello world",
            splitFor = { name -> (name == "qr-arg") to 2 },
        )
        assertEquals(listOf("hello", "world"), inv.unnamedArgs)
    }

    @Test
    fun `escaped pipe stays inside the value`() {
        // 输入 a \| b（一个反斜杠）：转义判定消费反斜杠，值里保留管道字符
        val inv = SlashParser.parse("/echo a \\| b", rawQuotes = true)
        assertEquals(listOf("a | b"), inv.unnamedArgs)
    }

    @Test
    fun `strict escaping makes even backslashes consume the escape`() {
        // 输入 a \\| b（两个反斜杠）：loose 只认单反斜杠 → 第二个反斜杠保留为文本（值 a \| b）；
        // STRICT 下偶数个反斜杠也转义 → 消费一个、管道仍分隔（值 a \）
        val loose = SlashParser.parse("/echo a \\\\| b", rawQuotes = true)
        assertEquals(listOf("a \\| b"), loose.unnamedArgs)
        val strict = SlashParser.parse("/echo a \\\\| b", rawQuotes = true, strictEscaping = true)
        assertEquals(listOf("a \\"), strict.unnamedArgs)
    }

    @Test
    fun `named list value keeps raw bracket form`() {
        val inv = SlashParser.parse("/let key=list [\"a\",\"b\",\"c\"]")
        assertEquals("list", inv.namedArgs["key"])
        assertEquals("[\"a\",\"b\",\"c\"]", inv.unnamedArgs.first())
    }
}
