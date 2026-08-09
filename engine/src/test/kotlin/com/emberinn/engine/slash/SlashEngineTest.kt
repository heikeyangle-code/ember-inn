package com.emberinn.engine.slash

import org.junit.Assert.assertEquals
import org.junit.Test

class SlashEngineTest {

    @Test
    fun `single pipe injects previous output as unnamed arg`() {
        // /echo abc → "abc"；/echo 无参数 → 注入 "abc"
        assertEquals("abc", SlashEngine.execute("/echo abc | /echo"))
    }

    @Test
    fun `double pipe does not inject`() {
        assertEquals("", SlashEngine.execute("/echo abc || /echo"))
    }

    @Test
    fun `closure evaluated as value`() {
        val out = SlashEngine.execute("/sendas name=X {: /echo 你好 :}")
        assertEquals("OK:sendas:X:你好", out)
    }

    @Test
    fun `closure output piped`() {
        assertEquals("OK:sys:内层", SlashEngine.execute("/echo {: /sys 内层 :} | /echo"))
    }

    @Test
    fun `pass forwards unnamed args and pipe value`() {
        assertEquals("Hello World", SlashEngine.execute("/pass Hello World"))
        assertEquals("abc", SlashEngine.execute("/echo abc | /pass"))
        assertEquals("", SlashEngine.execute("/pass"))
    }

    @Test
    fun `pass closure resolves value`() {
        assertEquals("test", SlashEngine.execute("/pass {: /echo test :}"))
    }

    @Test
    fun `var pipe and arg macros resolve in chain`() {
        assertEquals("Hello", SlashEngine.execute("/let key=greeting Hello || /pass {{var::greeting}}"))
        assertEquals("b", SlashEngine.execute("/let key=list [\"a\",\"b\",\"c\"] || /pass {{var::list::1}}"))
        assertEquals("", SlashEngine.execute("/pass {{var::unknown}}"))
        assertEquals("abc", SlashEngine.execute("/echo abc | /pass {{pipe}}"))
        assertEquals("world", SlashEngine.execute("/qr-arg hello world || /echo {{arg::hello}}"))
        assertEquals("", SlashEngine.execute("/let key=test {\"k\":\"v\"} || /pass {{var::test::error}}"))
    }

    @Test
    fun `echo keeps raw quotes per official`() {
        assertEquals("\"hello world\"", SlashEngine.execute("/echo \"hello world\""))
        assertEquals("OK:sys:hello world", SlashEngine.execute("/sys \"hello world\""))
    }

    @Test
    fun `parser-flag toggles strict escaping for subsequent commands`() {
        // 默认 loose：两个反斜杠 → 值保留 a \| b（一个反斜杠）
        assertEquals("a \\| b", SlashEngine.execute("/echo a \\\\| b"))
        // STRICT_ESCAPING 开启后：两个反斜杠也被转义 → 管道分隔，值只剩 a \
        assertEquals("a \\", SlashEngine.execute("/parser-flag STRICT_ESCAPING on | /echo a \\\\| b"))
        // 关掉后恢复 loose
        assertEquals(
            "a \\| b",
            SlashEngine.execute("/parser-flag STRICT_ESCAPING off | /echo a \\\\| b"),
        )
    }

    @Test
    fun `parser-flag default state is on when omitted`() {
        assertEquals("a \\", SlashEngine.execute("/parser-flag STRICT_ESCAPING | /echo a \\\\| b"))
    }

    @Test
    fun `comments are discarded between commands`() {
        assertEquals("hi", SlashEngine.execute("// 注释 | /echo hi"))
        assertEquals("hi", SlashEngine.execute("/# 注释 | /echo hi"))
        assertEquals("b", SlashEngine.execute("/echo a /* 块注释 *| /echo b"))
    }

    @Test
    fun `plain text between commands is discarded`() {
        assertEquals("ok", SlashEngine.execute("随便说说 | /echo ok"))
    }

    @Test
    fun `escaped closure is not resolved`() {
        // \{: 被转义：不执行；非 split 值不判闭包，反斜杠原样保留（官方 slash-parser 差分 18 例确认）
        assertEquals("\\{:", SlashEngine.execute("/echo \\{:"))
    }

    @Test
    fun `getvar macro in arguments resolves via macro engine`() {
        assertEquals("value", SlashEngine.execute("/let key=x value || /echo {{getvar::x}}"))
    }
}
