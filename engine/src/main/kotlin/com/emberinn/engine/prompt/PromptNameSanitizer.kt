package com.emberinn.engine.prompt

/**
 * 对齐官方 PromptManager.isValidName/sanitizeName（OpenAI name 字段规则）：
 * 只允许 [a-zA-Z0-9_]，1..64 字符；非法字符替换为 _，截断 64。
 */
object PromptNameSanitizer {

    private val VALID_NAME = Regex("^[a-zA-Z0-9_]{1,64}$")
    private val INVALID_CHARS = Regex("[^a-zA-Z0-9_]")

    fun isValidName(name: String): Boolean = VALID_NAME.matches(name)

    fun sanitizeName(name: String): String = name.replace(INVALID_CHARS, "_").take(64)
}
