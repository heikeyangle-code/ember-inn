package com.emberinn.engine.slash

/**
 * 斜杠命令解析器，对齐官方 SlashCommandParser（release 8172dcd / 1.18.0）逐字移植：
 * - testSymbol/testSymbolLooseyGoosey 由 SlashEscape 承担（差分 13 例）
 * - parseCommand / parseNamedArgument / parseUnnamedArgument（split+count）/ parseQuotedValue /
 *   parseListValue / parseValue 按官方 tokenizer 语义（index/jumpedEscapeSequence/behind/ahead）
 * - STRICT_ESCAPING：默认 false（官方默认），影响所有定界符的奇偶反斜杠判定
 * - REPLACE_GETVAR：官方新宏引擎下为 no-op（replaceGetvar 直接原样返回），宏展开由 MacroEngine 完成
 * - rawQuotes：官方语义 = 整段直到命令结束（| / 闭包 / 文本结束）作为单个值，保留引号
 *
 * 边界（源码对照 + 单测）：闭包 {: ... :} 由 SlashEngine.resolveClosures 预解析（官方是惰性闭包对象）；
 * 注释（双斜杠 / 井号斜杠 / 块注释）与 /parser-flag 由 SlashEngine 顺序循环处理。
 */
object SlashParser {

    fun parse(
        line: String,
        rawQuotes: Boolean = false,
        strictEscaping: Boolean = false,
        rawQuotesFor: (String) -> Boolean = { false },
        splitFor: (String) -> Pair<Boolean, Int?> = { false to null },
    ): CommandInvocation {
        val tok = SlashTokenizer(line, strictEscaping)
        tok.rawQuotes = rawQuotes
        return tok.parseCommand({ name -> rawQuotes || rawQuotesFor(name) }, splitFor)
    }
}

/** 官方 SlashCommandParser 的逐字符 tokenizer 核心移植。 */
class SlashTokenizer(
    private val text: String,
    var strictEscaping: Boolean = false,
) {
    var index = 0
        private set
    var rawQuotes: Boolean = false
        internal set
    private var jumpedEscapeSequence = false

    val char: Char get() = if (index < text.length) text[index] else '\u0000'
    val ahead: String get() = if (index + 1 < text.length) text.substring(index + 1) else ""
    val behind: String get() = text.substring(0, index)
    val endOfText: Boolean
        get() = index >= text.length ||
            (char.isWhitespace() && ahead.isNotEmpty() && ahead.all { it.isWhitespace() })

    /** 官方 take()：取当前字符并把 jumpedEscapeSequence 复位。 */
    fun take(length: Int = 1): String {
        jumpedEscapeSequence = false
        val end = minOf(index + length, text.length)
        val s = text.substring(index, end)
        index = end
        return s
    }

    /** 官方 testSymbol：委托 SlashEscape（差分 1:1），并按返回值推进 index/jumped。 */
    fun testSymbol(sequence: String, offset: Int = 0): Boolean {
        val r = SlashEscape.testSymbol(text, index, sequence, strictEscaping, offset, jumpedEscapeSequence)
        index = r.index
        jumpedEscapeSequence = r.jumpedEscapeSequence
        return r.found
    }

    /** 官方 testSymbol(/\s/)：当前（或跳过转义后）字符是空白且未被转义。 */
    fun testWhitespace(): Boolean {
        if (index >= text.length) return false
        val effective = index + (if (jumpedEscapeSequence) -1 else 0)
        val c = text.getOrNull(effective) ?: return false
        if (!c.isWhitespace()) return false
        return testSymbol(c.toString())
    }

    fun discardWhitespace() {
        while (testWhitespace()) take()
    }

    /** 官方 testCommandEnd（根闭包）：文本结束 或 宏括号外的 |。 */
    fun testCommandEnd(): Boolean {
        if (index >= text.length) return true
        if (testSymbol("|") && !isInsideMacroBraces()) return true
        return false
    }

    /** 官方 isInsideMacroBraces：behind 里未闭合的 {{...}} 深度。 */
    private fun isInsideMacroBraces(): Boolean {
        val behindText = behind
        var depth = 0
        var i = 0
        while (i < behindText.length) {
            if (i + 1 < behindText.length && behindText[i] == '{' && behindText[i + 1] == '{') {
                depth++
                i++
            } else if (i + 1 < behindText.length && behindText[i] == '}' && behindText[i + 1] == '}') {
                depth = maxOf(0, depth - 1)
                i++
            }
            i++
        }
        return depth > 0
    }

    fun testCommand(): Boolean = testSymbol("/")
    fun testBlockComment(): Boolean = testSymbol("/*")
    fun testComment(): Boolean = testSymbol("//") || testSymbol("/#")
    fun testParserFlag(): Boolean = testSymbol("/parser-flag ")
    fun testClosure(): Boolean = testSymbol("{:")

    /** 官方 parseCommand：/ 命令名 → 命名参数 → 无名参数（rawQuotes/split 按命令定义决定）。 */
    fun parseCommand(
        rawQuotesFor: (String) -> Boolean,
        splitFor: (String) -> Pair<Boolean, Int?>,
    ): CommandInvocation {
        val start = index
        take() // discard "/"
        val name = StringBuilder()
        while (!char.isWhitespace() && !testCommandEnd()) name.append(take())
        val cmdName = name.toString()
        discardWhitespace()
        rawQuotes = false
        if (rawQuotesFor(cmdName)) rawQuotes = true
        val (split, splitCount) = splitFor(cmdName)

        // 命名参数
        val named = linkedMapOf<String, String>()
        val namedLists = linkedMapOf<String, List<String>>()
        while (testNamedArgument()) {
            val (key, value, list) = parseNamedArgument()
            if (list != null) namedLists[key] = list
            named[key] = value
            discardWhitespace()
        }
        discardWhitespace()

        // 无名参数
        val unnamed = if (testUnnamedArgument()) parseUnnamedArgument(split, splitCount) else emptyList()
        return CommandInvocation(
            name = cmdName.lowercase(),
            namedArgs = named,
            namedLists = namedLists,
            unnamedArgs = unnamed,
            raw = text.substring(start),
        )
    }

    /** 官方 testNamedArgument：/^\w+=/（\w = [A-Za-z0-9_]）。 */
    fun testNamedArgument(): Boolean =
        Regex("^\\w+=").containsMatchIn(char.toString() + ahead)

    /** 官方 parseNamedArgument：key= 后按 闭包/引号/list/普通值 取。 */
    private fun parseNamedArgument(): Triple<String, String, List<String>?> {
        val key = StringBuilder()
        while (char.isLetterOrDigit() || char == '_') key.append(take())
        take() // discard "="
        val value = when {
            testClosure() -> throw SlashParseException("闭包参数需由 SlashEngine 预解析：{:")
            testQuotedValue() -> parseQuotedValue()
            testListValue() -> parseListValue()
            testValue() -> parseValue()
            else -> ""
        }
        // 官方 list 值保留原始 [..] 字符串；引擎侧按 | 拆为 namedLists 供 /let 等使用
        val list = if (value.startsWith("[") && value.endsWith("]") && value.length >= 2) {
            val inner = value.substring(1, value.length - 1)
            if (inner.contains('|')) inner.split('|').map { it.trim() }.filter { it.isNotEmpty() } else null
        } else null
        return Triple(key.toString(), value, list)
    }

    fun testUnnamedArgument(): Boolean = !testCommandEnd()

    /** 官方 parseUnnamedArgument(split, splitCount, rawQuotes) 的核心移植。 */
    private fun parseUnnamedArgument(split: Boolean, splitCount: Int?): List<String> {
        var isList = split
        val listValues = mutableListOf<String>()
        var value = if (jumpedEscapeSequence) take() else ""

        if (!split && !rawQuotes && testQuotedValue()) {
            listValues += parseQuotedValue()
            isList = true
        }
        var splitActive = split
        while (!testCommandEnd()) {
            if (splitActive && splitCount != null && listValues.size >= splitCount) {
                splitActive = false
                if (testQuotedValue()) {
                    listValues += parseQuotedValue()
                }
            }
            when {
                testClosure() -> throw SlashParseException("闭包参数需由 SlashEngine 预解析：{:")
                splitActive -> {
                    when {
                        testQuotedValue() -> listValues += parseQuotedValue()
                        testListValue() -> listValues += parseListValue()
                        testValue() -> listValues += parseValue()
                        else -> throw SlashParseException("意外的无名参数结束")
                    }
                    discardWhitespace()
                }
                else -> value += take()
            }
        }
        if (isList && value.isNotEmpty()) listValues += value
        if (isList) {
            val trimmed = listValues.toMutableList()
            if (trimmed.isNotEmpty() && !trimmed.first().startsWith("\"")) {
                trimmed[0] = trimmed[0].trimStart()
            }
            if (trimmed.size > 1 && !trimmed.last().endsWith("\"")) {
                trimmed[trimmed.lastIndex] = trimmed[trimmed.lastIndex].trimEnd()
            }
            if (trimmed.firstOrNull()?.isEmpty() == true) trimmed.removeAt(0)
            if (trimmed.lastOrNull()?.isEmpty() == true) trimmed.removeAt(trimmed.lastIndex)
            return trimmed
        }
        return listOf(value.trim())
    }

    fun testQuotedValue(): Boolean = testSymbol("\"")

    private fun testQuotedValueEnd(): Boolean {
        if (endOfText) throw SlashParseException("未闭合的引号")
        if (!strictEscaping && testCommandEnd()) throw SlashParseException("未闭合的引号")
        return testSymbol("\"")
    }

    /** 官方 parseQuotedValue：逐字符取到闭合引号，转义由 testSymbol 消费。 */
    private fun parseQuotedValue(): String {
        take() // 丢弃开引号
        val sb = StringBuilder()
        while (!testQuotedValueEnd()) sb.append(take())
        take() // 丢弃闭引号
        return sb.toString()
    }

    fun testListValue(): Boolean = testSymbol("[")

    private fun testListValueEnd(): Boolean {
        if (endOfText) throw SlashParseException("未闭合的 list 值")
        return testSymbol("]")
    }

    /** 官方 parseListValue：取 [..] 原始文本（含括号）。 */
    private fun parseListValue(): String {
        val sb = StringBuilder(take()) // '['
        while (!testListValueEnd()) sb.append(take())
        sb.append(take()) // ']'
        return sb.toString()
    }

    fun testValue(): Boolean = !testWhitespace()

    private fun testValueEnd(): Boolean {
        if (testWhitespace()) return true
        return testCommandEnd()
    }

    /** 官方 parseValue：先取被转义的第一个字符，然后逐字符到空白/命令结束。 */
    private fun parseValue(): String {
        val sb = StringBuilder(if (jumpedEscapeSequence) take() else "")
        while (!testValueEnd()) sb.append(take())
        return sb.toString()
    }

    // ---- 官方 parseClosure 循环里对注释与 /parser-flag 的处理（SlashEngine 顺序调用） ----

    /** 官方 parseComment：// 或 /# 注释，取到 | 为止（丢弃）。 */
    fun parseComment() {
        take() // 丢弃第一个 /
        take() // 丢弃第二个 / 或 #
        while (index < text.length && !testSymbol("|")) take()
    }

    /** 官方 parseBlockComment：块注释（斜杠星号开头、星号竖线结尾），支持嵌套。 */
    fun parseBlockComment() {
        take() // '/'
        take() // '*'
        while (true) {
            if (testBlockComment()) {
                parseBlockComment()
                continue
            }
            if (testSymbol("*|")) {
                take(2)
                return
            }
            if (index >= text.length) return
            take()
        }
    }

    /** 官方 parseParserFlag：/parser-flag FLAG [on|off]，立即影响后续解析。 */
    fun parseParserFlag(state: SlashState) {
        take(13) // "/parser-flag "
        rawQuotes = false
        val args = parseUnnamedArgument(split = true, splitCount = null)
        val flag = args.firstOrNull() ?: return
        val on = isTrueBoolean(args.getOrNull(1) ?: "on")
        when (flag.uppercase()) {
            "STRICT_ESCAPING" -> {
                strictEscaping = on
                state.strictEscaping = on
            }
            "REPLACE_GETVAR" -> {
                state.replaceGetvar = on
            }
        }
    }

    private fun isTrueBoolean(value: String): Boolean =
        value.lowercase() in setOf("on", "true", "1", "yes", "y")
}
