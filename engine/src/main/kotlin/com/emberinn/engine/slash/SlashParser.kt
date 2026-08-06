package com.emberinn.engine.slash

/**
 * 斜杠命令解析器核心，对齐官方 SlashCommandParser.parseCommand：
 * - 命令名：/ 后到空白或命令结束（| 或行尾）
 * - 命名参数：name=value（官方 testNamedArgument 规则 \w+=），值支持引号
 * - 无名参数：按空白分词，引号内整体保留
 * - 管道 / 闭包 / 转义等高级语法暂未实现（Stage 2）
 */
object SlashParser {

    fun parse(line: String): CommandInvocation {
        val text = line.trimStart()
        if (!text.startsWith("/")) throw SlashParseException("不是斜杠命令：$line")

        var index = 1
        val name = StringBuilder()
        while (index < text.length && !text[index].isWhitespace() && text[index] != '|') {
            name.append(text[index]); index++
        }
        if (name.isEmpty()) throw SlashParseException("空命令名")

        skipWhitespace(text, index)?.let { index = it }

        // 命名参数：name=value
        val named = linkedMapOf<String, String>()
        while (index < text.length && text[index] != '|') {
            val eq = findEquals(text, index)
            if (eq < 0 || eq == index) break
            val key = text.substring(index, eq)
            if (!key.all { it.isLetterOrDigit() || it == '_' }) break
            index = eq + 1
            val (value, next) = parseValue(text, index)
            named[key] = value
            index = next
            skipWhitespace(text, index)?.let { index = it }
        }

        // 无名参数
        val unnamed = mutableListOf<String>()
        while (index < text.length && text[index] != '|') {
            if (text[index].isWhitespace()) { index++; continue }
            val (value, next) = parseValue(text, index)
            unnamed.add(value)
            index = next
        }

        return CommandInvocation(
            name = name.toString().lowercase(),
            namedArgs = named,
            unnamedArgs = unnamed,
            raw = text,
        )
    }

    private fun findEquals(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return if (i < text.length && text[i] == '=') i else -1
    }

    /** 解析一个值：引号字符串 或 普通 token（到空白/| 结束）。返回 (值, 新位置)。 */
    private fun parseValue(text: String, from: Int): Pair<String, Int> {
        var i = from
        if (i < text.length && (text[i] == '"' || text[i] == ''')) {
            val quote = text[i]; i++
            val sb = StringBuilder()
            while (i < text.length && text[i] != quote) {
                if (text[i] == '\\' && i + 1 < text.length) { sb.append(text[i + 1]); i += 2 }
                else { sb.append(text[i]); i++ }
            }
            if (i >= text.length) throw SlashParseException("未闭合的引号")
            return sb.toString() to (i + 1)
        }
        val sb = StringBuilder()
        while (i < text.length && !text[i].isWhitespace() && text[i] != '|') { sb.append(text[i]); i++ }
        return sb.toString() to i
    }

    private fun skipWhitespace(text: String, from: Int): Int? {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
