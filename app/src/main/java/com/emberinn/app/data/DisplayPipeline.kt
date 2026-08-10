package com.emberinn.app.data

/**
 * 消息显示管线：对齐官方 script.js messageFormatting 的显示侧步骤
 * （正则显示位点由 ChatViewModel 调用 RegexPipelineEngine 完成，本对象只做纯文本步骤）。
 * - fixMarkdown：官方 power-user.js fixMarkdown(text, forDisplay=true) 1:1
 * - encodeTags：官方 power_user.encode_tags（负向断言不可用时走官方 fallback：< 和 > 全转义）
 */
object DisplayPipeline {

    private val pairRegex = Regex("([*_]{1,2})([\\s\\S]*?)\\1")
    private val spaceRegex = Regex(
        "(\\*|_)([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)" +
            "|([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)(\\*|_)",
    )

    fun fixMarkdown(text: String): String {
        val matches = pairRegex.findAll(text).toList()
        var newText = text
        // 从后往前替换，避免下标错位
        for (m in matches.asReversed()) {
            val replacement = m.value.replace(spaceRegex, "$1$4")
            newText = newText.replaceRange(m.range.first, m.range.last + 1, replacement)
        }

        // 官方 forDisplay=true：修未配对的 * 和 "（奇数个时行尾补一个）
        val lines = newText.split('\n').toMutableList()
        for (i in lines.indices) {
            for (ch in listOf('*', '"')) {
                if (lines[i].contains(ch) && lines[i].count { it == ch } % 2 == 1) {
                    lines[i] = lines[i].trimEnd() + ch
                }
            }
        }
        return lines.joinToString("\n")
    }

    /** 官方 encode_tags fallback（无负向断言支持时）：先 < 后 > 全转义。 */
    fun encodeTags(text: String): String = text.replace("<", "&lt;").replace(">", "&gt;")

    /**
     * 官方 onProgressStreaming 的流式定界符补齐：* / " / ``` / ~~~ 出现奇数次时，
     * 在行尾补一个（多字符定界符前加换行），避免流式途中 markdown 结构断裂。
     * isFinal=true 时官方跳过补齐（保存时走 cleanUpMessage）。
     */
    fun balanceStreamingDelimiters(text: String, isFinal: Boolean = false): String {
        if (isFinal) return text
        var out = text
        for (delimiter in listOf("*", "\"", "```", "~~~")) {
            if (countOccurrences(out, delimiter) % 2 == 1) {
                val separator = if (delimiter.length > 1) "\n" else ""
                out = out.trimEnd() + separator + delimiter
            }
        }
        return out
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val idx = text.indexOf(needle, from)
            if (idx < 0) break
            count++
            from = idx + needle.length
        }
        return count
    }
}
