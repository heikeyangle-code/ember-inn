package com.emberinn.app.data

/**
 * 聊天朗读文本处理（纯逻辑，可单测），逐步对齐官方 tts 扩展 index.js processAndQueueTtsMessage
 * （index.js:671-712）的处理顺序：
 * 1. skip_codeblocks：移除 ```...``` / ~~~...~~~ 块
 * 2. skip_tags：移除 <tag>...</tag>
 * 3. pass_asterisks=false：narrate_dialogues_only 时移除星号包裹内容，否则仅移除 * 字符
 * 4. apply_regex：按用户正则移除并折叠空白（/pat/flags 形式）
 * 5. narrate_quoted_only：joinQuotedBlocks 抽取引号块（分隔符 ' ... '，保留引号字符）
 * 6. 移除内嵌图片 ![alt](url)
 * 7. 末尾恒定折叠空白为单空格并 trim（官方最后一步 \s+ → ' '）
 * 宏替换与 narrate_translated_only 的文本选取在调用方完成（官方 tts/index.js:671-674）。
 */
object TtsTextProcessor {

    fun prepare(
        text: String,
        skipCodeblocks: Boolean,
        skipTags: Boolean,
        applyRegex: Boolean,
        regexPattern: String,
        passAsterisks: Boolean = false,
        dialoguesOnly: Boolean = false,
        quotedOnly: Boolean = false,
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
            // 官方 index.js:685-688：dialogues_only 去星号内容，否则只去 * 字符
            t = if (dialoguesOnly) t.replace(Regex("\\*[^*]*?(\\*|$)"), "").trim() else t.replace("*", "").trim()
        }
        if (applyRegex && regexPattern.isNotBlank()) {
            val pattern = parseUserRegex(regexPattern)
            if (pattern != null) {
                t = pattern.replace(t, "").replace(Regex("""\s+"""), " ").trim()
            }
        }
        if (quotedOnly) {
            // 官方 index.js:701-703：separator 取 provider.separator || ' ... '；includeQuotes=true
            t = joinQuotedBlocks(t)
        }
        t = t.replace(Regex("""!\[.*?]\([^)]*\)"""), "")
        // 官方 index.js:711：末尾恒定折叠空白
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * 官方 joinQuotedBlocks（tts/index.js:780-838）逐字移植：
     * 栈式扫描引号对，只收集最外层配对块；无匹配时返回原文。
     * 引号对覆盖 EN/DE/FR/JP 排版双引号、排版单引号、日式直角引号与对称双引号（含全角＂）。
     */
    fun joinQuotedBlocks(
        text: String,
        separator: String = " ... ",
        includeQuotes: Boolean = true,
        returnEmptyOnNoQuotes: Boolean = false,
    ): String {
        if (text.isEmpty()) return text
        val openToClose = mapOf(
            '„' to '“',   // DE low-high
            '“' to '”',   // EN
            '«' to '»',   // FR open « close »
            '»' to '«',   // Some locales open »
            '‘' to '’',   // typographic singles
            '‚' to '‘',
            '「' to '」', // Japanese corner quotes
            '『' to '』',
            '"' to '"',   // symmetric doubles
            '＂' to '＂',
        )
        val segments = mutableListOf<String>()
        // 栈元素：(expectedClose, start)；按下标逐 code unit 扫描，与 JS text[i] 一致
        val stack = ArrayDeque<Pair<Char, Int>>()
        for (i in text.indices) {
            val ch = text[i]
            val top = stack.lastOrNull()
            if (top != null && ch == top.first) {
                val finished = stack.removeLast()
                if (stack.isEmpty()) {
                    // Only collect outermost quotes (contains all nested content)
                    segments.add(text.substring(finished.second, i + 1))
                }
                continue
            }
            val close = openToClose[ch]
            if (close != null) {
                stack.addLast(close to i)
            }
            // stray closer that doesn't match current top → ignore
        }
        if (segments.isEmpty()) return if (returnEmptyOnNoQuotes) "" else text
        val cleaned = if (includeQuotes) segments else segments.map { it.substring(1, it.length - 1) }
        return cleaned.joinToString(separator)
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
