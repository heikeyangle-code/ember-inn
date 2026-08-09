package com.emberinn.app.ui.settings

import android.content.Context

/**
 * 服务偏好：翻译 / 图像生成 / 向量检索，字段对齐官方扩展设置：
 * - 翻译 translate（extensions/translate/index.html：translation_auto_mode / provider / target_language）
 * - 图像 stable-diffusion（extensions/stable-diffusion/settings.html：source / URL / model / steps）
 * - 向量 vectors（扩展向量化用 OpenAI 兼容嵌入；本地离线 BagOfGram）
 */
object ServicesPrefs {

    private const val NAME = "ember_services"

    // 翻译
    fun translateProvider(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_provider", "libre") ?: "libre"

    fun translateAutoMode(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_auto_mode", "none") ?: "none"

    fun translateTargetLanguage(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_target_language", "zh") ?: "zh"

    fun translateApiKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_api_key", "") ?: ""

    fun translateUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_url", "") ?: ""

    // 图像
    fun imageSource(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_source", "auto") ?: "auto"

    fun imageUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_url", "") ?: ""

    fun imageModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_model", "") ?: ""

    fun imageSteps(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_steps", 30)

    // 向量
    fun vectorProvider(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("vector_provider", "local") ?: "local"

    fun vectorUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("vector_url", "") ?: ""

    fun vectorApiKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("vector_api_key", "") ?: ""

    fun vectorModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("vector_model", "text-embedding-3-small") ?: "text-embedding-3-small"

    // 向量开关与检索参数（对齐官方 vectors 扩展 settings 默认值）
    fun vectorEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("vector_enabled", false)

    fun vectorEnabledChats(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("vector_enabled_chats", false)

    fun vectorEnabledFiles(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("vector_enabled_files", false)

    fun vectorQuery(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_query", 2)

    fun vectorInsert(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_insert", 3)

    fun vectorProtect(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_protect", 5)

    fun vectorThreshold(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("vector_threshold", 0.25f).toDouble()

    fun saveTranslate(
        context: Context,
        provider: String,
        autoMode: String,
        targetLanguage: String,
        apiKey: String,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("translation_provider", provider)
            .putString("translation_auto_mode", autoMode)
            .putString("translation_target_language", targetLanguage)
            .putString("translation_api_key", apiKey)
            .apply()
    }

    fun saveTranslateUrl(context: Context, url: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("translation_url", url)
            .apply()
    }

    fun saveImage(context: Context, source: String, url: String, model: String, steps: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_source", source)
            .putString("sd_url", url)
            .putString("sd_model", model)
            .putInt("sd_steps", steps)
            .apply()
    }

    fun saveVector(
        context: Context,
        provider: String,
        url: String,
        apiKey: String,
        model: String,
        enabled: Boolean,
        enabledChats: Boolean,
        enabledFiles: Boolean,
        query: Int,
        insert: Int,
        protect: Int,
        threshold: Double,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("vector_provider", provider)
            .putString("vector_url", url)
            .putString("vector_api_key", apiKey)
            .putString("vector_model", model)
            .putBoolean("vector_enabled", enabled)
            .putBoolean("vector_enabled_chats", enabledChats)
            .putBoolean("vector_enabled_files", enabledFiles)
            .putInt("vector_query", query)
            .putInt("vector_insert", insert)
            .putInt("vector_protect", protect)
            .putFloat("vector_threshold", threshold.toFloat())
            .apply()
    }
}
