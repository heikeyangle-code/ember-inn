package com.emberinn.engine.prompt

/**
 * 对齐官方 public/scripts/reasoning.js PromptReasoning.addToMessage（1:1）：
 * - add_to_prompts=false 时仅 isPrefix（continue 最后一条）仍注入；
 * - max_additions 只限制非 prefix 注入；
 * - REASONING_PLACEHOLDER（零宽空格）与空 reasoning 不注入；
 * - prefix/separator/suffix 先过宏替换（官方 substituteParams，App 传入 MacroEngine 包装）。
 * 注意：官方 counter/prefix 状态是单例实例级的；本引擎按一次总装一个实例使用。
 */
data class ReasoningPromptSettings(
    val addToPrompts: Boolean = false,
    val maxAdditions: Int = 1,
    val prefix: String = "<think>",
    val suffix: String = "</think>",
    val separator: String = "\n",
)

class PromptReasoningEngine(
    private val substitute: (String) -> String = { it },
) {
    var counter: Int = 0
        private set
    var prefixLength: Int = -1
        private set
    var prefixReasoning: String = ""
        private set
    var prefixReasoningFormatted: String = ""
        private set
    var prefixDuration: Long? = null
        private set
    var prefixIncomplete: Boolean = false
        private set

    fun isLimitReached(settings: ReasoningPromptSettings): Boolean =
        !settings.addToPrompts || counter >= settings.maxAdditions

    fun addToMessage(
        content: String,
        reasoning: String?,
        isPrefix: Boolean,
        duration: Long?,
        settings: ReasoningPromptSettings,
    ): String {
        // 官方 addToMessage：非 prefix 受开关/次数限制；prefix 恒注入
        if (!isPrefix && (!settings.addToPrompts || counter >= settings.maxAdditions)) return content
        if (reasoning.isNullOrEmpty() || reasoning == REASONING_PLACEHOLDER) return content

        counter++

        // 官方 substituteParams(power_user.reasoning.prefix/separator/suffix)
        val prefix = substitute(settings.prefix)
        val separator = substitute(settings.separator)
        val suffix = substitute(settings.suffix)

        if (isPrefix && content.isEmpty()) {
            val formattedReasoning = prefix + reasoning
            prefixReasoning = reasoning
            prefixReasoningFormatted = formattedReasoning
            prefixLength = formattedReasoning.length
            prefixDuration = duration
            prefixIncomplete = true
            return formattedReasoning
        }

        val formattedReasoning = prefix + reasoning + suffix + separator
        if (isPrefix) {
            prefixReasoning = reasoning
            prefixReasoningFormatted = formattedReasoning
            prefixLength = formattedReasoning.length
            prefixDuration = duration
            prefixIncomplete = false
        }
        return formattedReasoning + content
    }

    /** 官方 removePrefix：continue 续写时剥离已注入的 prefix。 */
    fun removePrefix(content: String): String =
        if (prefixLength > 0) content.substring(prefixLength) else content

    companion object {
        const val REASONING_PLACEHOLDER = "\u200B"
    }
}
