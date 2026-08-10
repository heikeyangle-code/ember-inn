package com.emberinn.app.data

import android.content.Context

/** 生成偏好：自动续写（官方 power_user.auto_continue）+ 思考入提示词（官方 power_user.reasoning.add_to_prompts），默认都关。 */
object GenerationPrefs {

    private const val NAME = "ember_generation"

    fun autoContinueEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("auto_continue_enabled", false)

    fun autoContinueTargetLength(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("auto_continue_target_length", 0)

    fun allowChatCompletions(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("auto_continue_allow_chat_completions", true)

    /** 官方 power_user.reasoning.add_to_prompts（默认关）。 */
    fun reasoningToPrompts(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("reasoning_to_prompts", false)

    fun saveReasoningToPrompts(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("reasoning_to_prompts", enabled)
            .apply()
    }

    fun saveAutoContinue(context: Context, enabled: Boolean, targetLength: Int, allowChatCompletions: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_continue_enabled", enabled)
            .putInt("auto_continue_target_length", targetLength)
            .putBoolean("auto_continue_allow_chat_completions", allowChatCompletions)
            .apply()
    }
}
