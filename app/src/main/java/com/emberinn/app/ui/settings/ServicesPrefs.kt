package com.emberinn.app.ui.settings

import android.content.Context
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
    // 官方 defaultSettings（extensions/translate/index.js:33-39）：
    // target_language='en'、internal_language='en'、provider='google'、auto_mode='none'、deepl_endpoint='free'
    fun translateProvider(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_provider", "google") ?: "google"

    fun translateAutoMode(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_auto_mode", "none") ?: "none"

    fun translateTargetLanguage(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_target_language", "en") ?: "en"

    fun translateApiKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_api_key", "") ?: ""

    fun translateUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_url", "") ?: ""

    /** 官方 extension_settings.translate.internal_language（默认 en）：出站译文的目标语言。 */
    fun translateInternalLanguage(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_internal_language", "en") ?: "en"

    /** 官方 extension_settings.translate.deepl_endpoint（默认 free）：DeepL 免费/Pro 端点选择。 */
    fun translateDeeplEndpoint(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("translation_deepl_endpoint", "free") ?: "free"

    // 图像
    /** 官方 defaultSettings.source = sources.extras（stable-diffusion/index.js L234）。 */
    fun imageSource(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_source", "extras") ?: "extras"

    fun imageUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_url", "") ?: ""

    /**
     * 官方逐源 URL/auth 字段（stable-diffusion/index.js defaultSettings L288-298/L339-342）：
     * auto_url='http://localhost:7860' + auto_auth、sdcpp_url='http://127.0.0.1:1234'（无 auth）、
     * vlad_url='http://localhost:7860' + vlad_auth、drawthings_url='http://localhost:7860' + drawthings_auth、
     * comfy_url='http://127.0.0.1:8188'、comfy_runpod_url=''。
     * URL 键未写过时回退旧单字段 sd_url（App 拆分前只有一处连接设置），再退官方默认。
     */
    private fun sdSourceField(context: Context, key: String, def: String, legacyUrl: Boolean = false): String {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (p.contains(key)) return p.getString(key, def) ?: def
        if (legacyUrl) {
            val legacy = p.getString("sd_url", "") ?: ""
            if (legacy.isNotEmpty()) return legacy
        }
        return def
    }

    /** 官方 auto_url（L288）。 */
    fun autoUrl(context: Context): String = sdSourceField(context, "sd_auto_url", "http://localhost:7860", legacyUrl = true)

    /** 官方 auto_auth：服务端 getBasicAuthHeader 恒发 Basic 头（空串也发）。 */
    fun autoAuth(context: Context): String = sdSourceField(context, "sd_auto_auth", "")

    /** 官方 sdcpp_url（L292）；sdcpp 服务端不发 auth。 */
    fun sdcppUrl(context: Context): String = sdSourceField(context, "sd_sdcpp_url", "http://127.0.0.1:1234", legacyUrl = true)

    /** 官方 vlad_url（L294）。 */
    fun vladUrl(context: Context): String = sdSourceField(context, "sd_vlad_url", "http://localhost:7860", legacyUrl = true)

    /** 官方 vlad_auth。 */
    fun vladAuth(context: Context): String = sdSourceField(context, "sd_vlad_auth", "")

    /** 官方 drawthings_url（L297）。 */
    fun drawthingsUrl(context: Context): String = sdSourceField(context, "sd_drawthings_url", "http://localhost:7860", legacyUrl = true)

    /** 官方 drawthings_auth。 */
    fun drawthingsAuth(context: Context): String = sdSourceField(context, "sd_drawthings_auth", "")

    /** 官方 comfy_url（L339）。 */
    fun comfyUrl(context: Context): String = sdSourceField(context, "sd_comfy_url", "http://127.0.0.1:8188", legacyUrl = true)

    /** 官方 comfy_runpod_url（L342），默认空；RunPod 走 Bearer imageApiKey。 */
    fun comfyRunpodUrl(context: Context): String = sdSourceField(context, "sd_comfy_runpod_url", "", legacyUrl = true)

    fun imageModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_model", "") ?: ""

    /** 官方 defaultSettings.steps = 20（stable-diffusion/index.js L246；settings.html 滑条 min1 max150）。 */
    fun imageSteps(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("sd_steps", 20)

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

    // ---- 官方 defaultSettings 补齐（stable-diffusion/index.js 默认值逐项核对） ----

    /** 官方 sd_snap（L280 默认 false）：自动调整的分辨率吸附到最近已知分辨率。 */
    fun imageSnap(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_snap", false)

    /** 官方 sd_minimal_prompt_processing（L283 默认 false）：LLM 生成 prompt 走最小后处理。 */
    fun imageMinimalPromptProcessing(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_minimal_prompt_processing", false)

    /** 官方 novel_anlas_guard（L321 默认 false）：自动调参避免消耗 Anlas（免费档）。 */
    fun novelAnlasGuard(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_novel_anlas_guard", false)

    /** 官方 novel_sm（L322 默认 false）：SMEA 采样变体；ddim / nai-diffusion-4-full 强制关。 */
    fun novelSm(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_novel_sm", false)

    /** 官方 novel_sm_dyn（L323 默认 false）：SMEA DYN 变体（依赖 SMEA 开启）。 */
    fun novelSmDyn(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_novel_sm_dyn", false)

    /** 官方 novel_decrisper（L324 默认 false）→ NovelAI dynamic_thresholding。 */
    fun novelDecrisper(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_novel_decrisper", false)

    /** 官方 novel_variety_boost（L325 默认 false）→ skip_cfg_above_sigma = calculateSkipCfgAboveSigma。 */
    fun novelVarietyBoost(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_novel_variety_boost", false)

    /** 官方 horde_karras（L273-274 默认 true）。直连 Horde 不发该字段（官方怪点）；extras 路径使用。 */
    fun imageHordeKarras(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_horde_karras", true)

    /** 官方 horde_sanitize（L274 默认 true）：Horde 侧 prompt 清洗开关。 */
    fun imageHordeSanitize(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_horde_sanitize", true)

    /**
     * 官方 horde_nsfw（L272 默认 false）。注意官方服务端笔误读 request.body.nfsw（undefined），
     * 直连 Horde 从不发送 nsfw——开关存在但直连路径无效果，为保 1:1 App 直连同样省略。
     */
    fun imageHordeNsfw(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_horde_nsfw", false)

    /** 官方 openai_style（L328 默认 'vivid'）：dall-e-3 图像风格。 */
    fun openaiStyle(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_openai_style", "vivid") ?: "vivid"

    /** 官方 openai_quality（L329 默认 'standard'）：dall-e-3/cogview/glm 质量。 */
    fun openaiQuality(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_openai_quality", "standard") ?: "standard"

    /** 官方 openai_quality_gpt（L330 默认 'auto'）：gpt-image-* 质量。 */
    fun openaiQualityGpt(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_openai_quality_gpt", "auto") ?: "auto"

    /** 官方 stability_style_preset（L354 默认 'anime'），客户端恒发送。 */
    fun stabilityStylePreset(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_stability_style_preset", "anime") ?: "anime"

    /** 官方 pollinations_enhance（L345 默认 false）：LLM 提示词增强。 */
    fun pollinationsEnhance(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_pollinations_enhance", false)

    /** 官方 bfl_upsampling（L357 默认 false）：BFL Prompt Upsampling。 */
    fun bflUpsampling(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_bfl_upsampling", false)

    /** 官方 google_enhance（L361 默认 true）：Google 生图提示词增强（服务端经 LLM 润色）。 */
    fun googleEnhance(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("sd_google_enhance", true)

    /** 官方 google_api（UI 无值时客户端兜底 'makersuite'，index.js L4616）。 */
    fun googleApi(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_google_api", "") ?: ""

    /** 官方 huggingface_model_id（默认 ''）：HF Inference 模型 id，独立于通用 model 字段。 */
    fun huggingfaceModelId(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_huggingface_model_id", "") ?: ""

    /** electronhub_quality：官方默认 undefined（省略）；UI 选择后为字符串。空串 = 未设置。 */
    fun electronhubQuality(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_electronhub_quality", "") ?: ""

    fun saveImageString(context: Context, key: String, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(key, value)
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

    /**
     * 官方 sd_comfy_placeholders（index.js L4248-L4250 / L4818-L4835）：自定义占位符
     * [{find, replace}] 数组，workflow 中 `"%find%"` → JSON.stringify(substituteParams(replace))。
     * JSON 数组存档；replace 的宏替换（{{user}} 等）在执行层做。
     */
    fun comfyPlaceholders(context: Context): List<Pair<String, String>> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("sd_comfy_placeholders", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
            arr.mapNotNull { el ->
                val o = el.jsonObject
                val f = o["find"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val r = o["replace"]?.jsonPrimitive?.contentOrNull ?: ""
                Pair(f, r)
            }
        }.getOrDefault(emptyList())
    }

    fun saveComfyPlaceholders(context: Context, list: List<Pair<String, String>>) {
        val json = buildString {
            append("[")
            list.forEachIndexed { i, (f, r) ->
                if (i > 0) append(",")
                append("{\"find\":")
                append(kotlinx.serialization.json.JsonPrimitive(f))
                append(",\"replace\":")
                append(kotlinx.serialization.json.JsonPrimitive(r))
                append("}")
            }
            append("]")
        }
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_comfy_placeholders", json)
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
        deeplEndpoint: String? = null,
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("translation_provider", provider)
            .putString("translation_auto_mode", autoMode)
            .putString("translation_target_language", targetLanguage)
            .putString("translation_api_key", apiKey)
            .apply { deeplEndpoint?.let { putString("translation_deepl_endpoint", it) } }
    }

    fun saveTranslateUrl(context: Context, url: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("translation_url", url)
            .apply()
    }

    fun saveImage(context: Context, source: String, model: String, steps: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("sd_source", source)
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
