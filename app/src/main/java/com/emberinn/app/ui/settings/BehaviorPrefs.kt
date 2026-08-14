package com.emberinn.app.ui.settings

import android.content.Context

/** 官方 power-user 行为设置（对齐 power-user.js 默认值）。 */
data class BehaviorSettings(
    val userPromptBias: String = "",
    val showUserPromptBias: Boolean = true,
    /** 官方 power_user.allow_name2_display（默认关）：显示时保留 AI 消息正文里的“角色名:”前缀，默认剥掉。 */
    val allowName2Display: Boolean = false,
    val trimSpaces: Boolean = true,
    val trimSentences: Boolean = false,
    val pinExamples: Boolean = false,
    val stripExamples: Boolean = false,
    val namesAsStopStrings: Boolean = true,
    val messageTokenCount: Boolean = false,
    val autoSwipe: Boolean = false,
    val autoSwipeMinimumLength: Int = 0,
    val autoSwipeBlacklist: Set<String> = emptySet(),
    val autoSwipeBlacklistThreshold: Int = 2,
)

object BehaviorPrefs {

    private const val NAME = "ember_behavior"

    fun load(context: Context): BehaviorSettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return BehaviorSettings(
            userPromptBias = p.getString("user_prompt_bias", "") ?: "",
            showUserPromptBias = p.getBoolean("show_user_prompt_bias", true),
            allowName2Display = p.getBoolean("allow_name2_display", false),
            trimSpaces = p.getBoolean("trim_spaces", true),
            trimSentences = p.getBoolean("trim_sentences", false),
            pinExamples = p.getBoolean("pin_examples", false),
            stripExamples = p.getBoolean("strip_examples", false),
            namesAsStopStrings = p.getBoolean("names_as_stop_strings", true),
            messageTokenCount = p.getBoolean("message_token_count_enabled", false),
            autoSwipe = p.getBoolean("auto_swipe", false),
            autoSwipeMinimumLength = p.getInt("auto_swipe_minimum_length", 0),
            autoSwipeBlacklist = (p.getStringSet("auto_swipe_blacklist", emptySet()) ?: emptySet()).toSet(),
            autoSwipeBlacklistThreshold = p.getInt("auto_swipe_blacklist_threshold", 2),
        )
    }

    fun save(context: Context, s: BehaviorSettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("user_prompt_bias", s.userPromptBias)
            .putBoolean("show_user_prompt_bias", s.showUserPromptBias)
            .putBoolean("allow_name2_display", s.allowName2Display)
            .putBoolean("trim_spaces", s.trimSpaces)
            .putBoolean("trim_sentences", s.trimSentences)
            .putBoolean("pin_examples", s.pinExamples)
            .putBoolean("strip_examples", s.stripExamples)
            .putBoolean("names_as_stop_strings", s.namesAsStopStrings)
            .putBoolean("message_token_count_enabled", s.messageTokenCount)
        com.emberinn.app.data.DisplayCacheVersion.bump()
            .putBoolean("auto_swipe", s.autoSwipe)
            .putInt("auto_swipe_minimum_length", s.autoSwipeMinimumLength)
            .putStringSet("auto_swipe_blacklist", s.autoSwipeBlacklist)
            .putInt("auto_swipe_blacklist_threshold", s.autoSwipeBlacklistThreshold)
            .apply()
    }
}
