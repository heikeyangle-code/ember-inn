package com.emberinn.app.data

import android.content.Context

/** 生成偏好：自动续写（官方 power_user.auto_continue）+ 思考入提示词（官方 power_user.reasoning.add_to_prompts），默认都关。 */
object GenerationPrefs {

    private const val NAME = "ember_generation"

    fun autoContinueEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("auto_continue_enabled", false)

    /** 官方默认 target_length=400（0 = 不触发，与官方语义相反）。 */
    fun autoContinueTargetLength(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("auto_continue_target_length", 400)

    /** 官方默认 allow_chat_completions=false。 */
    fun allowChatCompletions(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("auto_continue_allow_chat_completions", false)

    /** 官方 oai_settings.send_if_empty：最后一条是 AI 且输入为空时发送的默认消息（默认关）。 */
    fun sendIfEmpty(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("send_if_empty", "") ?: ""

    fun saveSendIfEmpty(context: Context, text: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("send_if_empty", text)
            .apply()
    }

    /** 官方 power_user.continue_on_send：空输入且最后一条是 AI 时按“继续”发送（默认关）。 */
    fun continueOnSend(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("continue_on_send", false)

    fun saveContinueOnSend(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("continue_on_send", enabled)
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
