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
}
