package com.emberinn.engine.worldinfo

/** 对齐官方 world-info.js parseRegexFromString：/pattern/flags → Regex（失败返回 null）。 */
object WorldRegexUtils {

    fun parse(text: String): Regex? {
        // 对齐官方：gimsuy；拒绝未转义斜杠；反转义 \/
        val m = Regex("""^/([\w\W]+?)/([gimsuy]*)$""").matchEntire(text) ?: return null
        var pattern = m.groupValues[1]
        if (Regex("""(^|[^\\])/""").containsMatchIn(pattern)) return null
        pattern = pattern.replace("\\/", "/")
        val flags = m.groupValues[2]
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
            // 边界：u/y 在 Kotlin Regex 无直接等价；g 的 lastIndex 状态语义官方也依赖外部 reset
        }
        return runCatching { Regex(pattern, options) }.getOrNull()
    }
}
