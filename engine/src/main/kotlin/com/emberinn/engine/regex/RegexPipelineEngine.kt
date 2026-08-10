package com.emberinn.engine.regex

/** 正则脚本完整字段（对齐 getRegexedString 所需）。 */
data class RegexPipelineScript(
    /** 官方 RegexScriptData.scriptName（UI/差分标识用）。 */
    val scriptName: String = "",
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val disabled: Boolean = false,
    val substituteRegex: Int = 0,
    val placement: List<Int> = emptyList(),
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = true,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
)

/**
 * 官方 regex SCRIPT_TYPES 优先级：GLOBAL(0) → PRESET(2) → SCOPED(1)（Object.values 顺序），
 * allowedOnly 时按 character_allowed_regex / preset_allowed_regex 过滤（getScriptsByType 纯逻辑，差分覆盖）。
 */
object RegexScopeResolver {

    fun resolve(
        global: List<RegexPipelineScript> = emptyList(),
        preset: List<RegexPipelineScript> = emptyList(),
        scoped: List<RegexPipelineScript> = emptyList(),
        allowedOnly: Boolean = false,
        scopedAllowed: Boolean = false,
        presetAllowed: Boolean = false,
    ): List<RegexPipelineScript> {
        val result = mutableListOf<RegexPipelineScript>()
        result += global
        if (!allowedOnly || presetAllowed) result += preset
        if (!allowedOnly || scopedAllowed) result += scoped
        return result
    }
}

/** 对齐官方 regex/engine.js getRegexedString：placement/markdown/prompt/编辑/深度/禁用扩展 过滤后逐条执行。 */
object RegexPipelineEngine {

    fun apply(
        raw: String,
        placement: Int,
        scripts: List<RegexPipelineScript>,
        isMarkdown: Boolean = false,
        isPrompt: Boolean = false,
        isEdit: Boolean = false,
        depth: Int? = null,
        disabledExtensions: Set<String> = emptySet(),
        substitute: (String) -> String = { it },
        characterOverride: String? = null,
    ): String {
        var finalString = raw
        if ("regex" in disabledExtensions || raw.isEmpty()) return finalString

        for (script in scripts) {
            val applies = (script.markdownOnly && isMarkdown) ||
                (script.promptOnly && isPrompt) ||
                (!script.markdownOnly && !script.promptOnly && !isMarkdown && !isPrompt)
            if (!applies) continue

            if (isEdit && !script.runOnEdit) continue

            if (depth != null) {
                if (script.minDepth != null && script.minDepth >= -1 && depth < script.minDepth) continue
                if (script.maxDepth != null && script.maxDepth >= 0 && depth > script.maxDepth) continue
            }

            if (script.placement.contains(placement)) {
                finalString = RegexEngine.apply(
                    RegexScript(
                        findRegex = script.findRegex,
                        replaceString = script.replaceString,
                        trimStrings = script.trimStrings,
                        disabled = script.disabled,
                        substituteRegex = script.substituteRegex,
                    ),
                    finalString,
                    substitute,
                    characterOverride,
                )
            }
        }
        return finalString
    }
}
