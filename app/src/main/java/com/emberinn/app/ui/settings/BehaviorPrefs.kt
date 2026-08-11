package com.emberinn.app.ui.settings

import android.content.Context

/** 官方 power-user 行为设置（对齐 power-user.js 默认值）。 */
data class BehaviorSettings(
    val userPromptBias: String = "",
    val showUserPromptBias: Boolean = true,
    val trimSpaces: Boolean = true,
    val trimSentences: Boolean = false,
    val pinExamples: Boolean = false,
    val namesAsStopStrings: Boolean = true,
)

object BehaviorPrefs {

    private const val NAME = "ember_behavior"

    fun load(context: Context): BehaviorSettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return BehaviorSettings(
            userPromptBias = p.getString("user_prompt_bias", "") ?: "",
            showUserPromptBias = p.getBoolean("show_user_prompt_bias", true),
            trimSpaces = p.getBoolean("trim_spaces", true),
            trimSentences = p.getBoolean("trim_sentences", false),
            pinExamples = p.getBoolean("pin_examples", false),
            namesAsStopStrings = p.getBoolean("names_as_stop_strings", true),
        )
    }

    fun save(context: Context, s: BehaviorSettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("user_prompt_bias", s.userPromptBias)
            .putBoolean("show_user_prompt_bias", s.showUserPromptBias)
            .putBoolean("trim_spaces", s.trimSpaces)
            .putBoolean("trim_sentences", s.trimSentences)
            .putBoolean("pin_examples", s.pinExamples)
            .putBoolean("names_as_stop_strings", s.namesAsStopStrings)
            .apply()
    }
}
