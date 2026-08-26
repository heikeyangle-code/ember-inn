package com.emberinn.app.ui.settings

import android.content.Context

/**
 * 语音（TTS）偏好，字段对齐官方 tts 扩展（public/scripts/extensions/tts/settings.html）。
 * 官方 1.18 无 STT：本页只做 TTS；在线提供商（Edge/ElevenLabs…）为 P3 引擎层，本机引擎 = Android 系统 TTS。
 */
object VoicePrefs {

    private const val NAME = "ember_voice"

    /** 朗读所需全部配置（TtsReader 读取用）。 */
    data class Values(
        val enabled: Boolean,
        val voice: String,
        val rate: Float,
        val autoGeneration: Boolean,
        val narrateUser: Boolean,
        val periodicAutoGeneration: Boolean,
        val narrateByParagraphs: Boolean,
        val narrateQuotedOnly: Boolean,
        val narrateDialoguesOnly: Boolean,
        val narrateTranslatedOnly: Boolean,
        val skipCodeblocks: Boolean,
        val skipTags: Boolean,
        val passAsterisks: Boolean,
        val multiVoiceEnabled: Boolean,
        val applyRegex: Boolean,
        val regexPattern: String,
        /** 外部 TTS 后端 id（"system" = Android 系统 TTS，对齐官方 extension_settings.tts.provider）。 */
        val ttsProvider: String,
        val ttsEndpoint: String,
        val ttsApiKey: String,
        val ttsModel: String,
    )

    fun read(context: Context): Values = Values(
        enabled = enabled(context),
        voice = voice(context),
        rate = rate(context),
        autoGeneration = autoGeneration(context),
        narrateUser = narrateUser(context),
        periodicAutoGeneration = periodicAutoGeneration(context),
        narrateByParagraphs = narrateByParagraphs(context),
        narrateQuotedOnly = narrateQuotedOnly(context),
        narrateDialoguesOnly = narrateDialoguesOnly(context),
        narrateTranslatedOnly = narrateTranslatedOnly(context),
        skipCodeblocks = skipCodeblocks(context),
        skipTags = skipTags(context),
        passAsterisks = passAsterisks(context),
        multiVoiceEnabled = multiVoiceEnabled(context),
        applyRegex = applyRegex(context),
        regexPattern = regexPattern(context),
        ttsProvider = ttsProvider(context),
        ttsEndpoint = ttsEndpoint(context),
        ttsApiKey = ttsApiKey(context),
        ttsModel = ttsModel(context),
    )

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_enabled", false)

    fun voice(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_voice", "") ?: ""

    /** 语速（官方 playback_rate：滑条 0–3 步长 0.05，默认 1；settings.html:89）。 */
    fun rate(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("playback_rate", 1.0f)

    /** 官方 defaultSettings.auto_generation = true（tts/index.js:31）。 */
    fun autoGeneration(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_auto_generation", true)

    fun narrateUser(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_user", false)

    /** 官方 tts_periodic_auto_generation（流式期间按段朗读）；defaultSettings 无此键 → falsy 默认关。 */
    fun periodicAutoGeneration(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_periodic_auto_generation", false)

    fun narrateByParagraphs(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_by_paragraphs", false)

    /** 官方 tts_narrate_quoted（只读引号内文本）；非 defaultSettings 键 → 默认关。 */
    fun narrateQuotedOnly(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_quoted", false)

    /** 官方 tts_narrate_dialogues（移除星号包裹内容而非仅 * 字符）；默认关。 */
    fun narrateDialoguesOnly(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_dialogues", false)

    /** 官方 tts_narrate_translated_only（有 extra.display_text 时读译文）；默认关。 */
    fun narrateTranslatedOnly(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_narrate_translated_only", false)

    /** 官方 tts_pass_asterisks（保留 * 交给引擎）；defaultSettings 无此键 → 默认关。 */
    fun passAsterisks(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_pass_asterisks", false)

    /** 官方 tts_multi_voice_enabled（引号/星号/其余文本分声线，需 voiceMap）；defaultSettings=false。 */
    fun multiVoiceEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_multi_voice_enabled", false)

    /** 官方 defaultSettings 无此键 → undefined falsy → 默认关（tts/index.js:867-874）。 */
    fun skipCodeblocks(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_skip_codeblocks", false)

    fun skipTags(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_skip_tags", false)

    fun applyRegex(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("tts_apply_regex", false)

    fun regexPattern(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_regex_pattern", "") ?: ""

    /** 外部 TTS 后端 id（"system" = Android 系统 TTS）。 */
    fun ttsProvider(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_provider", "system") ?: "system"

    fun ttsEndpoint(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_endpoint", "") ?: ""

    fun ttsApiKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_api_key", "") ?: ""

    fun ttsModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("tts_model", "") ?: ""

    fun saveTtsProvider(context: Context, provider: String, endpoint: String, apiKey: String, model: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("tts_provider", provider)
            .putString("tts_endpoint", endpoint)
            .putString("tts_api_key", apiKey)
            .putString("tts_model", model)
            .apply()
    }

    fun save(
        context: Context,
        enabled: Boolean,
        voice: String,
        rate: Float,
        autoGeneration: Boolean,
        narrateUser: Boolean,
        periodicAutoGeneration: Boolean,
        narrateByParagraphs: Boolean,
        narrateQuotedOnly: Boolean,
        narrateDialoguesOnly: Boolean,
        narrateTranslatedOnly: Boolean,
        skipCodeblocks: Boolean,
        skipTags: Boolean,
        passAsterisks: Boolean,
        multiVoiceEnabled: Boolean,
        applyRegex: Boolean,
        regexPattern: String,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("tts_enabled", enabled)
            .putString("tts_voice", voice)
            .putFloat("playback_rate", rate)
            .putBoolean("tts_auto_generation", autoGeneration)
            .putBoolean("tts_narrate_user", narrateUser)
            .putBoolean("tts_periodic_auto_generation", periodicAutoGeneration)
            .putBoolean("tts_narrate_by_paragraphs", narrateByParagraphs)
            .putBoolean("tts_narrate_quoted", narrateQuotedOnly)
            .putBoolean("tts_narrate_dialogues", narrateDialoguesOnly)
            .putBoolean("tts_narrate_translated_only", narrateTranslatedOnly)
            .putBoolean("tts_skip_codeblocks", skipCodeblocks)
            .putBoolean("tts_skip_tags", skipTags)
            .putBoolean("tts_pass_asterisks", passAsterisks)
            .putBoolean("tts_multi_voice_enabled", multiVoiceEnabled)
            .putBoolean("tts_apply_regex", applyRegex)
            .putString("tts_regex_pattern", regexPattern)
            .apply()
    }
}
