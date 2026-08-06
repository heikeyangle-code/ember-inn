package com.emberinn.engine.regex

/**
 * 正则脚本引擎，对齐官方 public/scripts/extensions/regex/engine.js runRegexScript：
 * - 跳过 disabled / 空 pattern / 空文本
 * - pattern 支持 /pat/flags 形式（i/m/s）
 * - 替换串支持 $1/$0 与 $<name> 命名组，{{match}} → 整段匹配
 * - trimStrings 从匹配值中删除
 * - 宏替换（substituteParams）在 Stage 2 接入
 */
data class RegexScript(
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val disabled: Boolean = false,
)

object RegexEngine {

    private val matchMacro = Regex("""\{\{match\}\}""", RegexOption.IGNORE_CASE)
    private val groupToken = Regex("""\$(\d+)|\$<([^>]+)>""")

    fun apply(script: RegexScript, raw: String): String {
        if (script.disabled || script.findRegex.isBlank() || raw.isEmpty()) return raw
        val regex = parseRegex(script.findRegex) ?: return raw

        return regex.replace(raw) { mr ->
            var replace = matchMacro.replace(script.replaceString, "$0")
            replace = groupToken.replace(replace) { token ->
                val num = token.groupValues[1]
                val name = token.groupValues[2]
                val value = when {
                    num.isNotEmpty() -> mr.groupValues.getOrNull(num.toInt())
                    name.isNotEmpty() -> mr.groups[name]?.value
                    else -> null
                } ?: ""
                trim(value, script.trimStrings)
            }
            replace
        }
    }

    private fun trim(value: String, trimStrings: List<String>): String {
        var out = value
        for (t in trimStrings) if (t.isNotEmpty()) out = out.replace(t, "")
        return out
    }

    private fun parseRegex(text: String): Regex? {
        val m = Regex("^/(.*)/([a-z]*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text)
        if (m == null) return runCatching { Regex(text) }.getOrNull()
        val options = buildSet {
            if ('i' in m.groupValues[2]) add(RegexOption.IGNORE_CASE)
            if ('m' in m.groupValues[2]) add(RegexOption.MULTILINE)
            if ('s' in m.groupValues[2]) add(RegexOption.DOT_MATCHES_ALL)
        }
        return runCatching { Regex(m.groupValues[1], options) }.getOrNull()
    }
}
