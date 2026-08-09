package com.emberinn.engine.slash

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/**
 * 斜杠链式执行引擎，对齐官方 SlashCommandParser.parseClosure + executeDirect：
 * - 顺序扫描整段文本：命令 / 注释 / /parser-flag / 普通文本（丢弃）
 * - 单管道 |：前一条命令的输出注入下一条的无名参数（官方 scope.pipe + injectPipe）
 * - 双管道 ||：不注入
 * - /parser-flag STRICT_ESCAPING on|off 立即生效，影响后续命令解析
 * - 闭包 {: ... :} 预解析为立即执行并取其 pipe 输出（官方惰性闭包，近似已登记）
 */
object SlashEngine {

    fun execute(text: String, state: SlashState = SlashState()): String {
        val resolved = resolveClosures(text)
        val tok = SlashTokenizer(resolved, strictEscaping = state.strictEscaping)
        var injectPipe = true
        while (true) {
            tok.discardWhitespace()
            if (tok.index >= resolved.length) break
            when {
                tok.testBlockComment() -> tok.parseBlockComment()
                tok.testComment() -> tok.parseComment()
                tok.testParserFlag() -> tok.parseParserFlag(state)
                tok.testCommand() -> {
                    val inv = tok.parseCommand(
                        rawQuotesFor = { name -> SlashRegistry.get(name)?.rawQuotes == true },
                        splitFor = { name ->
                            val def = SlashRegistry.get(name)
                            (def?.splitUnnamedArgument == true) to def?.splitUnnamedArgumentCount
                        },
                    )
                    val def = SlashRegistry.get(inv.name)
                        ?: throw SlashParseException("未知命令: /${inv.name}")
                    var finalInv = inv
                    if (injectPipe && inv.unnamedArgs.isEmpty()) {
                        finalInv = inv.copy(unnamedArgs = listOf(state.pipeValue))
                    }
                    finalInv = substituteInvocation(finalInv, state)
                    finalInv = finalInv.copy(
                        namedArgs = finalInv.namedArgs.mapValues { (_, v) -> v.replace("\u0001", "") },
                        namedLists = finalInv.namedLists.mapValues { (_, v) -> v.map { it.replace("\u0001", "") } },
                        unnamedArgs = finalInv.unnamedArgs.map { it.replace("\u0001", "") },
                    )
                    state.pipeValue = def.callback(finalInv, state)
                    injectPipe = true
                }
                else -> {
                    // 官方：命令之间的普通文本直接丢弃
                    while (!tok.testCommandEnd()) tok.take()
                }
            }
            tok.discardWhitespace()
            if (tok.testSymbol("|")) {
                tok.take()
                if (tok.testSymbol("|")) {
                    injectPipe = false
                    tok.take()
                }
            }
        }
        return state.pipeValue
    }

    /** 对齐官方：命令参数在执行前过宏替换（{{var}}/{{pipe}}/{{arg}} 等）。 */
    private fun substituteInvocation(invocation: CommandInvocation, state: SlashState): CommandInvocation {
        val env = MacroEnv(user = "", char = "", slash = state)
        return invocation.copy(
            namedArgs = invocation.namedArgs.mapValues { (_, v) -> MacroEngine.substitute(v, env) },
            namedLists = invocation.namedLists.mapValues { (_, v) -> v.map { MacroEngine.substitute(it, env) } },
            unnamedArgs = invocation.unnamedArgs.map { MacroEngine.substitute(it, env) },
        )
    }

    /**
     * 把 {: 链 :} 替换为其执行输出（加控制字符占位，保持单个参数）。
     * 转义判定对齐官方 testSymbol：{ 前反斜杠为奇数个时不是闭包。
     */
    private fun resolveClosures(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val isClosure = text.startsWith("{:", i) && !isEscaped(text, i)
            if (isClosure) {
                var depth = 1
                var j = i + 2
                var quote: Char? = null
                var end = -1
                while (j < text.length) {
                    val c = text[j]
                    if (quote != null) {
                        if (c == quote && !isEscaped(text, j)) quote = null
                    } else if (c == '"' || c == '\'') {
                        quote = c
                    } else if (text.startsWith("{:", j) && !isEscaped(text, j)) {
                        depth++; j += 1
                    } else if (text.startsWith(":}", j) && !isEscaped(text, j)) {
                        depth--
                        if (depth == 0) { end = j; break }
                        j += 1
                    }
                    j++
                }
                if (end < 0) { sb.append(text, i, text.length); break }
                val inner = text.substring(i + 2, end)
                val output = runCatching { execute(inner) }.getOrElse { "" }
                sb.append('\u0001').append(output).append('\u0001')
                i = end + 2
            } else {
                sb.append(text[i]); i++
            }
        }
        return sb.toString()
    }

    /** 前一个（非转义链）反斜杠为奇数个 → 该位置被转义。 */
    private fun isEscaped(text: String, index: Int): Boolean {
        var backslashes = 0
        var i = index - 1
        while (i >= 0 && text[i] == '\\') {
            backslashes++
            i--
        }
        return backslashes % 2 == 1
    }
}
