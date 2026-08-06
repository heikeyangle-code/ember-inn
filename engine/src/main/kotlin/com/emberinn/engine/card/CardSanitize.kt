package com.emberinn.engine.card

/** 对齐官方 sanitize-filename（替换为 _）：Windows 非法字符 + 控制字符。 */
object CardSanitize {

    private const val FORBIDDEN = """\/:*?"<>|"""

    fun sanitizeName(name: String): String = buildString {
        for (c in name) {
            append(if (c in FORBIDDEN || c.code < 32) '_' else c)
        }
    }
}
