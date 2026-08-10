package com.emberinn.engine.regex

import org.junit.Assert.assertEquals
import org.junit.Test

class RegexEngineTest {

    @Test
    fun `numbered groups replace`() {
        val script = RegexScript(findRegex = "(\\w+) (\\w+)", replaceString = "$2 $1")
        assertEquals("b a", RegexEngine.apply(script, "a b"))
    }

    @Test
    fun `named group replace`() {
        val script = RegexScript(findRegex = "(?<x>a)", replaceString = "$<x>$<x>")
        assertEquals("aa", RegexEngine.apply(script, "a"))
    }

    @Test
    fun `match macro whole match`() {
        val script = RegexScript(findRegex = "x(.)", replaceString = "[{{match}}]")
        assertEquals("[xa]b", RegexEngine.apply(script, "xab"))
    }

    @Test
    fun `trim strings removed from match`() {
        val script = RegexScript(findRegex = "x(abc)x", replaceString = "$1", trimStrings = listOf("a"))
        assertEquals("bc", RegexEngine.apply(script, "xabcx"))
    }

    @Test
    fun `disabled script returns raw`() {
        val script = RegexScript(findRegex = "a", replaceString = "b", disabled = true)
        assertEquals("a", RegexEngine.apply(script, "a"))
    }

    @Test
    fun `flags case insensitive`() {
        val script = RegexScript(findRegex = "/a/gi", replaceString = "x")
        assertEquals("xb", RegexEngine.apply(script, "Ab"))
    }

    @Test
    fun `non native js flags skip script like official RegExp constructor`() {
        // 官方 new RegExp(pattern, 'x'/'X'/'A'/'J'/'U') 抛 SyntaxError → 脚本跳过（原样返回）
        for (flag in listOf("x", "X", "A", "J", "U")) {
            val script = RegexScript(findRegex = "/你好/$flag", replaceString = "哈喽")
            assertEquals("flag $flag should skip", "你好", RegexEngine.apply(script, "你好"))
        }
    }

    @Test
    fun `unicode flag keeps script applying`() {
        // 官方 u 是原生 flag；Java 正则无等价物，近似忽略但仍执行
        val script = RegexScript(findRegex = "/你好/u", replaceString = "哈喽")
        assertEquals("哈喽", RegexEngine.apply(script, "你好"))
    }
}
