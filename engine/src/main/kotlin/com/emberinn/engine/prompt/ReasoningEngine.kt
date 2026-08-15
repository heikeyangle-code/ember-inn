package com.emberinn.engine.prompt

/**
 * 官方 reasoning.js parseReasoningFromString / removeReasoningFromString / formatReasoning
 * + utils.js trimSpaces 的引擎移植。
 */
@kotlinx.serialization.Serializable
data class ReasoningTemplate(
    /** 官方 power_user.reasoning 默认：prefix <think> / suffix </think> / separator \n。 */
    val prefix: String = "<think>",
    val suffix: String = "</think>",
    val separator: String = "\n",
)

@kotlinx.serialization.Serializable
data class ReasoningSettings(
    /** 官方 power_user.reasoning.name 默认 "Think XML"（预设名，仅用于回显/保存）。 */
    val name: String = "Think XML",
    @kotlinx.serialization.SerialName("auto_parse")
    val autoParse: Boolean = false,
    /** 官方 power_user.reasoning.add_to_prompts（默认关）。 */
    @kotlinx.serialization.SerialName("add_to_prompts")
    val addToPrompts: Boolean = false,
    /** 官方 power_user.reasoning.auto_expand（默认关，UI 自动展开）。 */
    @kotlinx.serialization.SerialName("auto_expand")
    val autoExpand: Boolean = false,
    /** 官方 power_user.reasoning.show_hidden（默认关，UI 显示隐藏思考）。 */
    @kotlinx.serialization.SerialName("show_hidden")
    val showHidden: Boolean = false,
    /** 官方 power_user.reasoning.max_additions（默认 1，非 prefix 注入上限）。 */
    @kotlinx.serialization.SerialName("max_additions")
    val maxAdditions: Int = 1,
    val trimSpaces: Boolean = false,
    val template: ReasoningTemplate = ReasoningTemplate(),
)

data class ParsedReasoning(
    val reasoning: String,
    val content: String,
)

data class FormattedReasoning(
    val formatted: String,
    val contentOnly: String,
)

object ReasoningEngine {

    fun removeReasoningFromString(str: String, settings: ReasoningSettings): String {
        if (!settings.autoParse) return str
        return parseReasoningFromString(str, settings = settings)?.content ?: str
    }

    fun parseReasoningFromString(
        str: String,
        strict: Boolean = true,
        settings: ReasoningSettings = ReasoningSettings(),
        template: ReasoningTemplate? = null,
    ): ParsedReasoning? {
        val t = template ?: settings.template
        if (t.prefix.isEmpty() || t.suffix.isEmpty()) return null

        return try {
            val start = if (strict) "^\\s*?" else ""
            val regex = Regex(
                "${start}${CleanUpMessageEngine.escapeRegex(t.prefix)}(.*?)${CleanUpMessageEngine.escapeRegex(t.suffix)}",
                RegexOption.DOT_MATCHES_ALL,
            )
            val match = regex.find(str)
            if (match == null) {
                return ParsedReasoning(reasoning = "", content = str)
            }
            val reasoning = match.groupValues[1]
            val content = str.replaceRange(match.range, "")
            ParsedReasoning(
                reasoning = trimSpaces(reasoning, settings.trimSpaces),
                content = trimSpaces(content, settings.trimSpaces),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun formatReasoning(
        reasoning: String,
        content: String,
        settings: ReasoningSettings = ReasoningSettings(),
        template: ReasoningTemplate? = null,
        substitute: (String) -> String = { it },
    ): FormattedReasoning {
        val t = template ?: settings.template
        if (reasoning.isEmpty() || t.prefix.isEmpty() || t.suffix.isEmpty()) {
            return FormattedReasoning(formatted = content, contentOnly = content)
        }
        val prefix = substitute(t.prefix)
        val suffix = substitute(t.suffix)
        val separator = substitute(t.separator)
        return FormattedReasoning(
            formatted = prefix + reasoning + suffix + separator + content,
            contentOnly = content,
        )
    }

    private fun trimSpaces(input: String, enabled: Boolean): String =
        if (enabled) input.trim() else input
}
