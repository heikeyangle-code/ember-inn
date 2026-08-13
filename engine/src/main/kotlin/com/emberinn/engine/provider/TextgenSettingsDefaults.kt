package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * textgenerationwebui_settings 默认值（官方 textgen-settings.js textgenerationwebui_settings 逐字），
 * 并按连接档案合并：type / server_urls / model 字段 / 采样参数 / maxContext。
 * 这是 App 传输配置映射（非官方纯逻辑），请求体本身由 TextgenRequestBodyEngine 差分保证。
 */
object TextgenSettingsDefaults {

    private val json = Json { ignoreUnknownKeys = true }

    /** 官方 APHRODITE_DEFAULT_ORDER（textgen-settings.js:102）。 */
    private val APHRODITE_DEFAULT_ORDER = listOf(
        "dry", "penalties", "no_repeat_ngram", "temperature", "top_nsigma", "top_p_top_k",
        "top_a", "min_p", "tfs", "eta_cutoff", "epsilon_cutoff", "typical_p", "quadratic", "xtc",
    )

    /** 官方默认对象（textgen-settings.js:150-230，逐字段）。 */
    fun defaults(): JsonObject = buildJsonObject {
        put("temp", 0.7)
        put("tfs", 1)
        put("epsilon_cutoff", 0)
        put("eta_cutoff", 0)
        put("typical_p", 1)
        put("min_p", 0)
        put("rep_pen", 1.2)
        put("rep_pen_range", 0)
        put("rep_pen_decay", 0)
        put("rep_pen_slope", 1)
        put("no_repeat_ngram_size", 0)
        put("penalty_alpha", 0)
        put("num_beams", 1)
        put("length_penalty", 1)
        put("min_length", 0)
        put("encoder_rep_pen", 1)
        put("freq_pen", 0)
        put("presence_pen", 0)
        put("skew", 0)
        put("do_sample", true)
        put("early_stopping", false)
        put("dynatemp", false)
        put("min_temp", 0)
        put("max_temp", 2.0)
        put("dynatemp_exponent", 1.0)
        put("smoothing_factor", 0.0)
        put("smoothing_curve", 1.0)
        put("dry_allowed_length", 2)
        put("dry_multiplier", 0.0)
        put("dry_base", 1.75)
        put("dry_sequence_breakers", "[\"\\n\", \":\", \"\\\"\", \"*\"]")
        put("dry_penalty_last_n", 0)
        put("max_tokens_second", 0)
        put("seed", -1)
        put("preset", "Default")
        put("add_bos_token", true)
        put("ban_eos_token", false)
        put("skip_special_tokens", true)
        put("include_reasoning", true)
        put("mirostat_mode", 0)
        put("mirostat_tau", 5)
        put("mirostat_eta", 0.1)
        put("guidance_scale", 1)
        put("negative_prompt", "")
        put("grammar_string", "")
        put("json_schema", null)
        put("json_schema_allow_empty", false)
        put("banned_tokens", "")
        put("global_banned_tokens", "")
        put("send_banned_tokens", true)
        put("ignore_eos_token", false)
        put("spaces_between_special_tokens", true)
        put("speculative_ngram", false)
        put("type", "ooba")
        put("mancer_model", "mytholite")
        put("togetherai_model", "Gryphe/MythoMax-L2-13b")
        put("infermaticai_model", "")
        put("ollama_model", "")
        put("openrouter_model", "openrouter/auto")
        put("openrouter_providers", kotlinx.serialization.json.JsonArray(emptyList()))
        put("openrouter_quantizations", kotlinx.serialization.json.JsonArray(emptyList()))
        put("vllm_model", "")
        put("aphrodite_model", "")
        put("dreamgen_model", "lucid-v1-extra-large/text")
        put("tabby_model", "")
        put("llamacpp_model", "")
        put("sampler_order", kotlinx.serialization.json.JsonArray(listOf(6, 0, 1, 3, 4, 2, 5).map { JsonPrimitive(it) }))
        put("sampler_priority", kotlinx.serialization.json.JsonArray(listOf(
            "repetition_penalty", "presence_penalty", "frequency_penalty", "dry", "temperature",
            "dynamic_temperature", "quadratic_sampling", "top_n_sigma", "top_k", "top_p", "typical_p",
            "epsilon_cutoff", "eta_cutoff", "tfs", "top_a", "min_p", "adaptive_p", "mirostat", "xtc",
            "encoder_repetition_penalty", "no_repeat_ngram",
        ).map { JsonPrimitive(it) }))
        put("samplers", kotlinx.serialization.json.JsonArray(listOf(
            "penalties", "dry", "top_n_sigma", "top_k", "typ_p", "top_p", "min_p", "xtc", "temperature", "adaptive_p",
        ).map { JsonPrimitive(it) }))
        put("samplers_priorities", kotlinx.serialization.json.JsonArray(APHRODITE_DEFAULT_ORDER.map { JsonPrimitive(it) }))
        put("logit_bias", kotlinx.serialization.json.JsonArray(emptyList()))
        put("n", 1)
        put("server_urls", buildJsonObject {})
        put("custom_model", "")
        put("openrouter_allow_fallbacks", true)
        put("xtc_threshold", 0.1)
        put("xtc_probability", 0)
        put("nsigma", 0.0)
        put("min_keep", 0)
        put("featherless_model", "")
        put("generic_model", "")
        put("adaptive_target", -0.01)
        put("adaptive_decay", 0.9)
    }

    /** 按连接档案合并：type、server_urls[type]、模型字段、常用采样、温度等；stored 为 textgen 预设应用后的设置（优先）。 */
    fun forProfile(provider: ProviderSpec, profile: ConnectionProfile, stored: JsonObject? = null): JsonObject {
        val type = provider.id.removePrefix("textgen-").let { if (it == "textgenerationwebui") "ooba" else it }
        val merged = (stored?.takeIf { it.isNotEmpty() } ?: defaults()).toMutableMap()
        merged["type"] = JsonPrimitive(type)
        val serverUrls = (merged["server_urls"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        serverUrls[type] = JsonPrimitive(profile.baseUrlOverride.ifBlank { provider.baseUrl })
        merged["server_urls"] = JsonObject(serverUrls)
        // 官方 getTextGenModel 的模型字段映射
        when (type) {
            "ooba" -> merged["custom_model"] = JsonPrimitive(profile.model)
            "generic" -> merged["generic_model"] = JsonPrimitive(profile.model)
            "mancer" -> merged["mancer_model"] = JsonPrimitive(profile.model)
            "togetherai" -> merged["togetherai_model"] = JsonPrimitive(profile.model)
            "infermaticai" -> merged["infermaticai_model"] = JsonPrimitive(profile.model)
            "dreamgen" -> merged["dreamgen_model"] = JsonPrimitive(profile.model)
            "openrouter" -> merged["openrouter_model"] = JsonPrimitive(profile.model)
            "vllm" -> merged["vllm_model"] = JsonPrimitive(profile.model)
            "aphrodite" -> merged["aphrodite_model"] = JsonPrimitive(profile.model)
            "ollama" -> merged["ollama_model"] = JsonPrimitive(profile.model)
            "featherless" -> merged["featherless_model"] = JsonPrimitive(profile.model)
            "tabby" -> merged["tabby_model"] = JsonPrimitive(profile.model)
            "llamacpp" -> merged["llamacpp_model"] = JsonPrimitive(profile.model)
        }
        val s = profile.sampler
        s.temperature.takeIf { it > 0 }?.let { merged["temp"] = JsonPrimitive(it) }
        s.topP.takeIf { it > 0 }?.let { merged["top_p"] = JsonPrimitive(it) }
        s.topK.takeIf { it > 0 }?.let { merged["top_k"] = JsonPrimitive(it) }
        s.minP.takeIf { it > 0 }?.let { merged["min_p"] = JsonPrimitive(it) }
        s.repetitionPenalty.takeIf { it > 0 }?.let { merged["rep_pen"] = JsonPrimitive(it) }
        s.frequencyPenalty.takeIf { it > 0 }?.let { merged["freq_pen"] = JsonPrimitive(it) }
        s.presencePenalty.takeIf { it > 0 }?.let { merged["presence_pen"] = JsonPrimitive(it) }
        if (s.seed >= 0) merged["seed"] = JsonPrimitive(s.seed)
        return JsonObject(merged)
    }
}
