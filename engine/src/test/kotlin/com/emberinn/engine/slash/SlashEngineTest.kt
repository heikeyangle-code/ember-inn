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
}
