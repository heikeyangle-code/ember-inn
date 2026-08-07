package com.emberinn.engine.slash

/**
 * 斜杠命令解析器核心，对齐官方 SlashCommandParser.parseCommand：
 * - 命令名：/ 后到空白或命令结束（| 或行尾）
 * - 命名参数：name=value（官方 testNamedArgument 规则 \w+=），值支持引号
 * - 无名参数：按空白分词，引号内整体保留
 * - 管道 / 闭包 / 双管道由 SlashEngine.parseChain/resolveClosures 实现（本文件只解析单命令）
 */
object SlashParser {

    fun parse(line: String, rawQuotes: Boolean = false): CommandInvocation {
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
        val namedLists = linkedMapOf<String, List<String>>()
        while (index < text.length && text[index] != '|') {
            val eq = findEquals(text, index)
            if (eq < 0 || eq == index) break
            val key = text.substring(index, eq)
            if (!key.all { it.isLetterOrDigit() || it == '_' }) break
            index = eq + 1
            if (text[index] == '[') {
                val (items, next) = parseListValue(text, index)
                if (items.size == 1 && items[0].startsWith("[")) {
                    named[key] = items[0]
                } else {
                    namedLists[key] = items
                    named[key] = items.joinToString("|")
                }
                index = next
            } else {
                val (value, next) = parseValue(text, index, rawQuotes)
                named[key] = value
                index = next
            }
            skipWhitespace(text, index)?.let { index = it }
        }

        // 无名参数
        val unnamed = mutableListOf<String>()
        while (index < text.length && text[index] != '|') {
            if (text[index].isWhitespace()) { index++; continue }
            if (text[index] == '[') {
                // 官方 list 值 [a|b|c]：拆成多个无名参数
                val (items, next) = parseListValue(text, index)
                unnamed.addAll(items)
                index = next
                continue
            }
            val (value, next) = parseValue(text, index, rawQuotes)
            unnamed.add(value)
            index = next
        }

        return CommandInvocation(
            name = name.toString().lowercase(),
            namedArgs = named,
            namedLists = namedLists,
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
    private fun parseValue(text: String, from: Int, rawQuotes: Boolean = false): Pair<String, Int> {
        var i = from
        if (i < text.length && (text[i] == '"' || text[i] == '\'')) {
            if (rawQuotes) {
                // 官方 rawQuotes：保留引号原样
                var j = i + 1
                while (j < text.length && text[j] != text[i]) {
                    if (text[j] == '\\' && j + 1 < text.length) j++
                    j++
                }
                if (j >= text.length) throw SlashParseException("未闭合的引号")
                return text.substring(i, j + 1) to (j + 1)
            }
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
        while (i < text.length && !text[i].isWhitespace() && text[i] != '|') {
            // 官方转义：\x 按字面字符
            if (text[i] == '\\' && i + 1 < text.length) { sb.append(text[i + 1]); i += 2 } else { sb.append(text[i]); i++ }
        }
        return sb.toString() to i
    }

    /** 官方 list 值：含 | 时拆成多项；否则（JSON 数组）保留为单值。 */
    private fun parseListValue(text: String, from: Int): Pair<List<String>, Int> {
        var i = from + 1
        val sb = StringBuilder()
        var closed = false
        var quote: Char? = null
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                if (c == '\\' && i + 1 < text.length) { sb.append(text[i + 1]); i++ }
                else {
                    sb.append(c)
                    if (c == quote) quote = null
                }
            } else {
                when {
                    c == '"' || c == '\'' -> { quote = c; sb.append(c) }
                    c == ']' -> { closed = true; i++; break }
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (!closed) throw SlashParseException("未闭合的 list 值")
        val inner = sb.toString()
        return if (inner.contains('|')) {
            inner.split('|').map { it.trim() }.filter { it.isNotEmpty() } to i
        } else {
            listOf("[" + inner + "]") to i
        }
    }

    private fun skipWhitespace(text: String, from: Int): Int? {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
