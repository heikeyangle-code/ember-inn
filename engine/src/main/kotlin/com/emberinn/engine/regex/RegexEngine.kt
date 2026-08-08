package com.emberinn.engine.regex

/**
 * 正则脚本引擎，对齐官方 public/scripts/extensions/regex/engine.js runRegexScript：
 * - 跳过 disabled / 空 pattern / 空文本
 * - pattern 支持 /pat/flags 形式（i/m/s）
 * - substituteRegex：NONE/RAW/ESCAPED（ESCAPED 走 sanitizeRegexMacro 转义）
 * - 替换串支持 $1/$0 与 $<name> 命名组，{{match}} → 整段匹配
 * - trimStrings 从匹配值中删除（trim 串先做宏替换）
 * - 替换结果整体做宏替换（substituteParams）
 */
data class RegexScript(
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val disabled: Boolean = false,
    val substituteRegex: Int = 0,
)

object RegexEngine {

    const val SUBSTITUTE_NONE = 0
    const val SUBSTITUTE_RAW = 1
    const val SUBSTITUTE_ESCAPED = 2

    private val matchMacro = Regex("""\{\{match\}\}""", RegexOption.IGNORE_CASE)
    private val groupToken = Regex("""\$(\d+)|\$<([^>]+)>""")

    private data class ParsedRegex(val regex: Regex, val global: Boolean)

    fun apply(
        script: RegexScript,
        raw: String,
        substitute: (String) -> String = { it },
        characterOverride: String? = null,
    ): String {
        if (script.disabled || script.findRegex.isBlank() || raw.isEmpty()) return raw
        val regexString = when (script.substituteRegex) {
            SUBSTITUTE_RAW -> substitute(script.findRegex)
            SUBSTITUTE_ESCAPED -> sanitizeRegexMacro(substitute(script.findRegex))
            else -> script.findRegex
        }
        val parsed = parseRegex(regexString) ?: return raw

        val transform: (MatchResult) -> String = { mr ->
            var replace = matchMacro.replace(script.replaceString) { "$0" }
            replace = groupToken.replace(replace) { token ->
                val num = token.groupValues[1]
                val name = token.groupValues[2]
                val value = when {
                    num.isNotEmpty() -> mr.groupValues.getOrNull(num.toInt())
                    name.isNotEmpty() -> mr.groups[name]?.value
                    else -> null
                } ?: ""
                trim(value, script.trimStrings, substitute, characterOverride)
            }
            substitute(replace)
        }
        // 官方 JS String.replace：无 g 只替换第一个匹配
        if (parsed.global) return parsed.regex.replace(raw, transform)
        val first = parsed.regex.find(raw) ?: return raw
        return raw.replaceRange(first.range, transform(first))
    }

    private fun trim(
        value: String,
        trimStrings: List<String>,
        substitute: (String) -> String,
        characterOverride: String?,
    ): String {
        var out = value
        for (t in trimStrings) {
            if (t.isNotEmpty()) {
                val withOverride = if (characterOverride != null) t.replace("{{char}}", characterOverride) else t
                out = out.replace(substitute(withOverride), "")
            }
        }
        return out
    }

    /** 对齐 sanitizeRegexMacro：控制符转义 + 正则元字符加反斜杠。 */
    private fun sanitizeRegexMacro(x: String): String = buildString {
        for (c in x) {
            when (c) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u000B' -> append("\\v")
                '\u000C' -> append("\\f")
                '\u0000' -> append("\\0")
                else -> {
                    if (c in """.^$*+?{}[]\/|()""") append('\\')
                    append(c)
                }
            }
        }
    }

    /** 对齐 utils.js regexFromString：/pat/flags、非法 flags 回退整体正则、无 g 仅首匹配。 */
    private fun parseRegex(text: String): ParsedRegex? {
        val slash = Regex("^/(.*)/([a-z]*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text)
        if (slash != null) {
            val flags = slash.groupValues[2]
            val validFlags = flags.toSet().size == flags.length && flags.all { it in "gmixXsuUAJ" }
            if (validFlags) {
                val options = buildSet {
                    if ('i' in flags) add(RegexOption.IGNORE_CASE)
                    if ('m' in flags) add(RegexOption.MULTILINE)
                    if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
                }
                return runCatching { ParsedRegex(Regex(slash.groupValues[1], options), 'g' in flags) }.getOrNull()
            }
        }
        // 无斜杠形式或非法 flags：官方 RegExp(input)（无 g）
        return runCatching { ParsedRegex(Regex(text), false) }.getOrNull()
    }
}
