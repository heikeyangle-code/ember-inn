package com.emberinn.engine.prompt

/**
 * 官方 power-user.js fixMarkdown 的引擎移植（forDisplay=true/false 两条路径）。
 * - forDisplay=false：只修强调符两侧空格（cleanUpMessage 用）
 * - forDisplay=true：额外补奇数个 * / "（messageFormatting 显示用）
 */
object FixMarkdown {

    private val formatRegex = Regex("([*_]{1,2})([\\s\\S]*?)\\1")
    private val spaceRegex = Regex(
        "(\\*|_)([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)" +
            "|([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)(\\*|_)",
    )

    fun fix(text: String, forDisplay: Boolean): String {
        val matches = formatRegex.findAll(text).toList()
        var newText = text
        // 从后往前替换，避免下标错位（官方 matches 逆序遍历）
        for (m in matches.asReversed()) {
            val replacement = m.value.replace(spaceRegex, "$1$4")
            newText = newText.replaceRange(m.range.first, m.range.last + 1, replacement)
        }

        if (!forDisplay) {
            return newText
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
}
