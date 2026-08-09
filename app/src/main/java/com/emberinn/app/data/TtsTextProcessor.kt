package com.emberinn.app.data

/**
 * 聊天朗读文本处理（纯逻辑，可单测），对齐官方 tts 扩展 index.js：
 * - skip_codeblocks：移除 ```...``` / ~~~...~~~ 块
 * - skip_tags：移除 <tag>...</tag>
 * - pass_asterisks=false（官方默认）：移除 * 字符
 * - apply_regex：按用户正则移除并折叠空白（/pat/flags 形式）
 * - 移除内嵌图片 ![alt](url)
 * 近似登记：官方先 substituteParams 宏替换，本实现不替换；多语音/对话专属/引号专属未实现。
 */
object TtsTextProcessor {

    fun prepare(
        text: String,
        skipCodeblocks: Boolean,
        skipTags: Boolean,
        applyRegex: Boolean,
        regexPattern: String,
        passAsterisks: Boolean = false,
    ): String {
        var t = text
        if (skipCodeblocks) {
            t = t.replace(Regex("```[\\s\\S]*?```"), "").trim()
            t = t.replace(Regex("~~~[\\s\\S]*?~~~"), "").trim()
        }
        if (skipTags) {
            t = t.replace(Regex("<.*?>[\\s\\S]*?<\\/.*?>"), "").trim()
        }
        if (!passAsterisks) {
            t = t.replace("*", "").trim()
        }
        if (applyRegex && regexPattern.isNotBlank()) {
            val pattern = parseUserRegex(regexPattern)
            if (pattern != null) {
                t = pattern.replace(t, "").replace(Regex("""\s+"""), " ").trim()
            }
        }
        t = t.replace(Regex("""!\[.*?]\([^)]*\)"""), "")
        return t.trim()
    }

    /** 对齐官方 regexFromString：/pat/flags 形式，非法回退整体正则。 */
    private fun parseUserRegex(pattern: String): Regex? {
        val slash = Regex("""^/(.*)/([a-z]*)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(pattern)
        if (slash != null) {
            val flags = slash.groupValues[2]
            if (flags.all { it in "i" }) {
                return runCatching {
                    if ('i' in flags) Regex(slash.groupValues[1], RegexOption.IGNORE_CASE)
                    else Regex(slash.groupValues[1])
                }.getOrNull()
            }
        }
        return runCatching { Regex(pattern) }.getOrNull()
    }
}
