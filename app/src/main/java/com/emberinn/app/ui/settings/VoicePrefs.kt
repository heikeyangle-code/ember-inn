package com.emberinn.app.ui.settings

import android.content.Context

/**
 * 语音（TTS）偏好，字段对齐官方 tts 扩展（public/scripts/extensions/tts/settings.html）。
 * 官方 1.18 无 STT：本页只做 TTS；在线提供商（Edge/ElevenLabs…）为 P3 引擎层，本机引擎 = Android 系统 TTS。
 */
object VoicePrefs {

    private const val NAME = "ember_voice"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_enabled", false)

    fun voice(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_voice", "") ?: ""

    /** 语速，Android TextToSpeech 实际支持约 0.5–2.0（1.0 = 正常）。 */
    fun rate(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("playback_rate", 1.0f)

    fun autoGeneration(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_auto_generation", false)

    fun narrateUser(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_user", false)

    fun narrateByParagraphs(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_by_paragraphs", false)

    fun skipCodeblocks(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_skip_codeblocks", true)

    fun skipTags(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_skip_tags", false)

    fun applyRegex(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_apply_regex", false)

    fun regexPattern(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_regex_pattern", "") ?: ""

    fun save(
        context: Context,
        enabled: Boolean,
        voice: String,
        rate: Float,
        autoGeneration: Boolean,
        narrateUser: Boolean,
        narrateByParagraphs: Boolean,
        skipCodeblocks: Boolean,
        skipTags: Boolean,
        applyRegex: Boolean,
        regexPattern: String,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("tts_enabled", enabled)
            .putString("tts_voice", voice)
            .putFloat("playback_rate", rate)
            .putBoolean("tts_auto_generation", autoGeneration)
            .putBoolean("tts_narrate_user", narrateUser)
            .putBoolean("tts_narrate_by_paragraphs", narrateByParagraphs)
            .putBoolean("tts_skip_codeblocks", skipCodeblocks)
            .putBoolean("tts_skip_tags", skipTags)
            .putBoolean("tts_apply_regex", applyRegex)
            .putString("tts_regex_pattern", regexPattern)
            .apply()
    }
}
