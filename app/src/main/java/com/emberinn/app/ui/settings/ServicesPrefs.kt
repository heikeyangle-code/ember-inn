package com.emberinn.app.ui.settings

import android.content.Context
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /** 官方 extension_settings.translate.internal_language（默认 en）：出站译文的目标语言。 */
    fun translateInternalLanguage(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_internal_language", "en") ?: "en"

    // 图像
    fun imageSource(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_source", "auto") ?: "auto"

    fun imageUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_url", "") ?: ""

    fun imageModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_model", "") ?: ""

    fun imageSteps(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_steps", 30)

    fun imageApiKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_api_key", "") ?: ""

    // 官方 stable-diffusion 扩展核心参数（defaultSettings 1:1 默认值）
    fun imagePromptPrefix(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_prompt_prefix", "") ?: ""

    fun imageNegativePrompt(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_negative_prompt", "") ?: ""

    fun imageSampler(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_sampler", "DDIM") ?: "DDIM"

    fun imageScheduler(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_scheduler", "normal") ?: "normal"

    fun imageSeed(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong("sd_seed", -1L)

    fun imageScale(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("sd_scale", 7f).toDouble()

    fun imageWidth(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_width", 512)

    fun imageHeight(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_height", 512)

    fun imageRestoreFaces(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_restore_faces", false)

    fun imageClipSkip(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_clip_skip", 1)

    fun imageVae(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_vae", "") ?: ""

    fun imageEnableHr(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_enable_hr", false)

    /** ADetailer（官方 extension_settings.sd.adetailer_face，仅 auto 后端生效）。 */
    fun imageADetailerFace(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_adetailer_face", false)

    fun saveImageADetailerFace(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("sd_adetailer_face", enabled)
            .apply()
    }

    fun imageHrUpscaler(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_hr_upscaler", "Latent") ?: "Latent"

    fun imageHrScale(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("sd_hr_scale", 1.0f).toDouble()

    fun imageHrSecondPassSteps(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_hr_second_pass_steps", 0)

    fun imageDenoisingStrength(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("sd_denoising_strength", 0.7f).toDouble()

    /** Refine 模式：生成前允许手动编辑提示词（官方 sd_refine_mode）。 */
    fun imageRefineMode(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_refine_mode", false)

    /** Interactive 模式：发送含触发词的消息自动生图（官方 sd_interactive_mode）。 */
    fun imageInteractiveMode(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_interactive_mode", false)

    /** Multimodal captioning：基于头像生成人物/人像提示（官方 sd_multimodal_captioning）。 */
    fun imageMultimodalCaptioning(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_multimodal_captioning", false)

    /** Free mode LLM 扩展：FREE 模式用 LLM 扩写主题提示（官方 sd_free_extend）。 */
    fun imageFreeExtend(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_free_extend", false)

    fun saveImageModeToggle(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, enabled)
            .apply()
    }

    /** 角色提示词前缀（官方 extension_settings.sd.character_prompts，按角色 id 存）。 */
    fun imageCharaPrompt(context: Context, characterId: String?): String =
        if (characterId.isNullOrBlank()) "" else imageCharaPromptMap(context)[characterId] ?: ""

    fun imageCharaNegativePrompt(context: Context, characterId: String?): String =
        if (characterId.isNullOrBlank()) "" else imageCharaNegativeMap(context)[characterId] ?: ""

    fun saveImageCharaPrompts(context: Context, characterId: String?, positive: String, negative: String) {
        if (characterId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val pos = imageCharaPromptMap(context).toMutableMap()
        val neg = imageCharaNegativeMap(context).toMutableMap()
        if (positive.isBlank()) pos.remove(characterId) else pos[characterId] = positive
        if (negative.isBlank()) neg.remove(characterId) else neg[characterId] = negative
        prefs.edit()
            .putString("sd_character_prompts", kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.JsonObject(pos.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) }),
            ))
            .putString("sd_character_negative_prompts", kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.JsonObject(neg.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) }),
            ))
            .apply()
    }

    fun saveImageAdvanced(
        context: Context,
        promptPrefix: String,
        negativePrompt: String,
        sampler: String,
        scheduler: String,
        seed: Long,
        scale: Double,
        width: Int,
        height: Int,
        restoreFaces: Boolean,
        clipSkip: Int,
        vae: String,
        enableHr: Boolean,
        hrUpscaler: String,
        hrScale: Double,
        hrSecondPassSteps: Int,
        denoisingStrength: Double,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_prompt_prefix", promptPrefix)
            .putString("sd_negative_prompt", negativePrompt)
            .putString("sd_sampler", sampler)
            .putString("sd_scheduler", scheduler)
            .putLong("sd_seed", seed)
            .putFloat("sd_scale", scale.toFloat())
            .putInt("sd_width", width)
            .putInt("sd_height", height)
            .putBoolean("sd_restore_faces", restoreFaces)
            .putInt("sd_clip_skip", clipSkip)
            .putString("sd_vae", vae)
            .putBoolean("sd_enable_hr", enableHr)
            .putString("sd_hr_upscaler", hrUpscaler)
            .putFloat("sd_hr_scale", hrScale.toFloat())
            .putInt("sd_hr_second_pass_steps", hrSecondPassSteps)
            .putFloat("sd_denoising_strength", denoisingStrength.toFloat())
            .apply()
    }

    private fun imageCharaPromptMap(context: Context): Map<String, String> =
        readStringMap(context, "sd_character_prompts")

    private fun imageCharaNegativeMap(context: Context): Map<String, String> =
        readStringMap(context, "sd_character_negative_prompts")

    private fun readStringMap(context: Context, key: String): Map<String, String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(key, null) ?: return emptyMap()
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                .mapNotNull { (k, v) -> k to (v.jsonPrimitive.contentOrNull ?: return@mapNotNull null) }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    /** 官方 sd_comfy_type：standard / runpod_serverless（settings.html L230-233 默认 standard）。 */
    fun comfyType(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_comfy_type", "standard") ?: "standard"

    fun saveComfyType(context: Context, type: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_comfy_type", type)
            .apply()
    }

    fun saveImageApiKey(context: Context, key: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_api_key", key)
            .apply()
    }

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

    // 向量 Data Bank 高级参数（官方 vectors 扩展默认值：sizeThresholdDb=5KB / chunkCountDb=5 / overlapPercentDb=0）
    fun vectorSizeThresholdDb(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_size_threshold_db", 5)

    fun vectorChunkCountDb(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_chunk_count_db", 5)

    fun vectorOverlapPercentDb(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("vector_overlap_percent_db", 0)

    fun saveVectorAdvanced(context: Context, sizeThresholdDb: Int, chunkCountDb: Int, overlapPercentDb: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("vector_size_threshold_db", sizeThresholdDb)
            .putInt("vector_chunk_count_db", chunkCountDb)
            .putInt("vector_overlap_percent_db", overlapPercentDb)
            .apply()
    }

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
