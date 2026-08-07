package com.emberinn.engine.card

/**
 * 对齐官方 characters.js 使用的 sanitize-filename@1.6.3（无 replacement 选项）：
 * 非法字符/控制字符直接删除，保留名处理，去尾部点空格，UTF-8 截断 255 字节。
 */
object CardSanitize {

    private val ILLEGAL = "\\/?<>\\:*|\"".toSet()
    private val WINDOWS_RESERVED = Regex("""^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?$""", RegexOption.IGNORE_CASE)

    fun sanitizeName(name: String): String {
        val removed = buildString {
            for (c in name) {
                val code = c.code
                val isControl = code in 0..31 || code in 0x80..0x9f
                if (c !in ILLEGAL && !isControl) append(c)
            }
        }
        val noReserved = when {
            removed.all { it == '.' } -> ""
            WINDOWS_RESERVED.matches(removed) -> ""
            else -> removed
        }
        val noTrailing = noReserved.trimEnd('.', ' ')
        return truncateUtf8(noTrailing, 255)
    }

    /** 对齐 truncate-utf8-bytes：按 UTF-8 字节数截断，不切断多字节字符。 */
    private fun truncateUtf8(s: String, maxBytes: Int): String {
        var bytes = 0
        var index = 0
        while (index < s.length) {
            val cp = s.codePointAt(index)
            val width = Character.charCount(cp)
            val encodedLength = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + encodedLength > maxBytes) break
            bytes += encodedLength
            index += width
        }
        return s.substring(0, index)
    }
}
