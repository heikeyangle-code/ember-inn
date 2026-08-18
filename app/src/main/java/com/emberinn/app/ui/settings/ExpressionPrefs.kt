package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.engine.expression.ExpressionApi
import com.emberinn.engine.expression.ExpressionEngine
import com.emberinn.engine.expression.ExpressionPromptType

/** 表情精灵偏好（对齐官方 extensions/expressions extension_settings.expressions）。 */
data class ExpressionSettingsApp(
    val enabled: Boolean = false,
    /** 官方 expressions.api（默认 none=99；App 消费 none/llm，local/extras/webllm 需对应后端）。 */
    val api: ExpressionApi = ExpressionApi.NONE,
    /** 官方 expressions.llmPrompt（默认 DEFAULT_LLM_PROMPT，{{labels}} 占位）。 */
    val llmPrompt: String = ExpressionEngine.DEFAULT_LLM_PROMPT,
    /** 官方 expressions.promptType（raw=generateRaw / full=generateQuietPrompt）。 */
    val promptType: ExpressionPromptType = ExpressionPromptType.RAW,
    /** 官方 expressions.filterAvailable：仅 LLM/WebLLM 有效，标签集过滤为有立绘的标签。 */
    val filterAvailable: Boolean = false,
    val fallbackExpression: String = "",
    val allowMultiple: Boolean = false,
    val rerollIfSame: Boolean = false,
    val customLabels: Set<String> = emptySet(),
)

object ExpressionPrefs {

    private const val NAME = "ember_expression"

    fun load(context: Context): ExpressionSettingsApp {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return ExpressionSettingsApp(
            enabled = p.getBoolean("enabled", false),
            api = ExpressionApi.entries.firstOrNull { it.value == p.getInt("api", ExpressionApi.NONE.value) }
                ?: ExpressionApi.NONE,
            llmPrompt = p.getString("llm_prompt", ExpressionEngine.DEFAULT_LLM_PROMPT)
                ?: ExpressionEngine.DEFAULT_LLM_PROMPT,
            promptType = if (p.getString("prompt_type", "raw") == "full") {
                ExpressionPromptType.FULL
            } else {
                ExpressionPromptType.RAW
            },
            filterAvailable = p.getBoolean("filter_available", false),
            fallbackExpression = p.getString("fallback", "") ?: "",
            allowMultiple = p.getBoolean("allowMultiple", false),
            rerollIfSame = p.getBoolean("rerollIfSame", false),
            customLabels = (p.getStringSet("customLabels", emptySet()) ?: emptySet()).toSet(),
        )
    }

    fun save(context: Context, s: ExpressionSettingsApp) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putInt("api", s.api.value)
            .putString("llm_prompt", s.llmPrompt)
            .putString("prompt_type", s.promptType.value)
            .putBoolean("filter_available", s.filterAvailable)
            .putString("fallback", s.fallbackExpression)
            .putBoolean("allowMultiple", s.allowMultiple)
            .putBoolean("rerollIfSame", s.rerollIfSame)
            .putStringSet("customLabels", s.customLabels)
            .apply()
    }
}
