package com.emberinn.app.ui.settings

import android.content.Context

/**
 * 服务偏好：翻译 / 图像生成 / 向量检索，字段对齐官方扩展设置：
 * - 翻译 translate（extensions/translate/index.html：translation_auto_mode / provider / target_language）
 * - 图像 stable-diffusion（extensions/stable-diffusion/settings.html：source / URL / model / steps）
 * - 向量 vectors（扩展向量化用 OpenAI 兼容嵌入；本地离线 BagOfGram）
 * 执行层（TTS/翻译/图像请求）为 P3 引擎服务，本页只持久化配置。
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

    fun saveImage(context: Context, source: String, url: String, model: String, steps: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_source", source)
            .putString("sd_url", url)
            .putString("sd_model", model)
            .putInt("sd_steps", steps)
            .apply()
    }

    fun saveVector(context: Context, provider: String, url: String, apiKey: String, model: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("vector_provider", provider)
            .putString("vector_url", url)
            .putString("vector_api_key", apiKey)
            .putString("vector_model", model)
            .apply()
    }
}
