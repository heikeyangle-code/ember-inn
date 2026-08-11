package com.emberinn.engine.prompt

/**
 * 官方 reasoning.js parseReasoningFromString / removeReasoningFromString / formatReasoning
 * + utils.js trimSpaces 的引擎移植。
 */
data class ReasoningTemplate(
    val prefix: String = "",
    val suffix: String = "",
    val separator: String = "",
)

data class ReasoningSettings(
    val autoParse: Boolean = false,
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
