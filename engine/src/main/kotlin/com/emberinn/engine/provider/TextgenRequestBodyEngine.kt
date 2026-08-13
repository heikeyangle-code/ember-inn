package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * 官方 Text Completion 请求体（textgen-settings.js createTextGenGenerationData / getTextGenModel /
 * getTextGenServer / getCustomTokenBans / calculateLogitBias，SillyTavern 1.18.0 / 8172dcd 逐字移植）。
 * 差分：scripts/diff/textgen-body-official.mjs → TextgenBodyDiffTest（27 例）。
 *
 * 打桩对应（与脚本一致）：tokenize 由调用方注入（tokenizer 行为不属本差分）；stoppingStrings 由调用方传入；
 * substituteParams 恒等（宏替换在调用方完成）；bannedInMacros 由调用方传入。
 */
object TextgenRequestBodyEngine {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- 官方 textgen_types（逐字）----
    private const val OOBA = "ooba"
    private const val MANCER = "mancer"
    private const val VLLM = "vllm"
    private const val APHRODITE = "aphrodite"
    private const val TABBY = "tabby"
    private const val KOBOLDCPP = "koboldcpp"
    private const val TOGETHERAI = "togetherai"
    private const val LLAMACPP = "llamacpp"
    private const val OLLAMA = "ollama"
    private const val INFERMATICAI = "infermaticai"
    private const val DREAMGEN = "dreamgen"
    private const val OPENROUTER = "openrouter"
    private const val FEATHERLESS = "featherless"
    private const val HUGGINGFACE = "huggingface"
    private const val GENERIC = "generic"

    private val APHRODITE_DEFAULT_ORDER = listOf(
        "dry", "penalties", "no_repeat_ngram", "temperature", "top_nsigma", "top_p_top_k",
        "top_a", "min_p", "tfs", "eta_cutoff", "epsilon_cutoff", "typical_p", "quadratic", "xtc",
    )

    data class BuildInput(
        val settings: JsonObject,
        val model: String? = null,
        val finalPrompt: String? = null,
        val maxTokens: Int? = null,
        val isImpersonate: Boolean = false,
        val isContinue: Boolean = false,
        val cfgValues: JsonObject? = null,
        val type: String = "quiet",
        val stoppingStrings: List<String> = emptyList(),
        val maxContext: Int = 4096,
        val requestTokenProbabilities: Boolean = false,
        val bannedInMacros: List<String> = emptyList(),
        val dynatempTypes: String = "",
        val tokenize: (String) -> List<Int> = { text -> text.map { it.code } },
    )

    /** 官方 getTextGenModel；无匹配返回 null；Ollama 无模型抛错（官方 toastr+throw）。 */
    fun getTextGenModel(settings: JsonObject): String? {
        return when (str(settings, "type")) {
            OOBA -> str(settings, "custom_model")?.takeIf { it.isNotEmpty() }
            GENERIC -> str(settings, "generic_model")?.takeIf { it.isNotEmpty() }
            MANCER -> str(settings, "mancer_model")
            TOGETHERAI -> str(settings, "togetherai_model")
            INFERMATICAI -> str(settings, "infermaticai_model")
            DREAMGEN -> str(settings, "dreamgen_model")
            OPENROUTER -> str(settings, "openrouter_model")
            VLLM -> str(settings, "vllm_model")
            APHRODITE -> str(settings, "aphrodite_model")
            OLLAMA -> str(settings, "ollama_model")?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("No Ollama model selected")
            FEATHERLESS -> str(settings, "featherless_model")
            HUGGINGFACE -> "tgi"
            TABBY -> str(settings, "tabby_model")?.takeIf { it.isNotEmpty() }
            LLAMACPP -> str(settings, "llamacpp_model")?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    fun build(input: BuildInput): String {
        val s = input.settings
        val type = str(s, "type")
        val model = input.model ?: getTextGenModel(s)
        val canMultiSwipe = !input.isContinue && !input.isImpersonate && input.type != "quiet"
        val dynatemp = bool(s, "dynatemp") == true && type != null && input.dynatempTypes.contains(type)
        val bans = customTokenBans(s, input)
        val jsonSchema: JsonObject? = when (val v = s["json_schema"]) {
            is JsonObject -> if (bool(s, "json_schema_allow_empty") == true || v.isNotEmpty()) v else null
            else -> null
        }
        val tempVal: Double? = if (dynatemp) ((num(s, "min_temp") ?: 0.0) + (num(s, "max_temp") ?: 0.0)) / 2.0 else num(s, "temp")

        val params = buildJsonObject {
            putJson("prompt", input.finalPrompt?.let { JsonPrimitive(it) })
            putJson("model", model?.let { JsonPrimitive(it) })
            putJson("max_new_tokens", input.maxTokens?.let { JsonPrimitive(it) } ?: JsonNull)
            putJson("max_tokens", input.maxTokens?.let { JsonPrimitive(it) } ?: JsonNull)
            putJson("logprobs", if (input.requestTokenProbabilities) jsNum(getLogprobsNumber(type).toDouble()) else null)
            putJson("temperature", jsNum(tempVal))
            putJson("top_p", jsNum(num(s, "top_p")))
            putJson("typical_p", jsNum(num(s, "typical_p")))
            putJson("typical", jsNum(num(s, "typical_p")))
            putJson("sampler_seed", seedOrNull(s))
            putJson("min_p", jsNum(num(s, "min_p")))
            putJson("repetition_penalty", jsNum(num(s, "rep_pen")))
            putJson("frequency_penalty", jsNum(num(s, "freq_pen")))
            putJson("presence_penalty", jsNum(num(s, "presence_pen")))
            putJson("top_k", jsNum(num(s, "top_k")))
            putJson("skew", jsNum(num(s, "skew")))
            putJson("min_length", if (type == OOBA) jsNum(num(s, "min_length")) else null)
            putJson("minimum_message_content_tokens", if (type == DREAMGEN) jsNum(num(s, "min_length")) else null)
            putJson("min_tokens", jsNum(num(s, "min_length")))
            putJson("num_beams", if (type == OOBA) jsNum(num(s, "num_beams")) else null)
            putJson("length_penalty", if (type == OOBA) jsNum(num(s, "length_penalty")) else null)
            putJson("early_stopping", if (type == OOBA) bool(s, "early_stopping")?.let { JsonPrimitive(it) } else JsonNull)
            putJson("add_bos_token", bool(s, "add_bos_token")?.let { JsonPrimitive(it) })
            putJson("dynamic_temperature", if (dynatemp) JsonPrimitive(true) else null)
            putJson("dynatemp_low", if (dynatemp) jsNum(num(s, "min_temp")) else null)
            putJson("dynatemp_high", if (dynatemp) jsNum(num(s, "max_temp")) else null)
            putJson("dynatemp_range", if (dynatemp) jsNum(((num(s, "max_temp") ?: 0.0) - (num(s, "min_temp") ?: 0.0)) / 2.0) else null)
            putJson("dynatemp_exponent", if (dynatemp) jsNum(num(s, "dynatemp_exponent")) else null)
            putJson("smoothing_factor", jsNum(num(s, "smoothing_factor")))
            putJson("smoothing_curve", jsNum(num(s, "smoothing_curve")))
            putJson("dry_allowed_length", jsNum(num(s, "dry_allowed_length")))
            putJson("dry_multiplier", jsNum(num(s, "dry_multiplier")))
            putJson("dry_base", jsNum(num(s, "dry_base")))
            putJson("dry_sequence_breakers", replaceMacrosInList(str(s, "dry_sequence_breakers"))?.let { JsonPrimitive(it) })
            putJson("dry_penalty_last_n", jsNum(num(s, "dry_penalty_last_n")))
            putJson("max_tokens_second", jsNum(num(s, "max_tokens_second")))
            putJson("sampler_priority", if (type == OOBA) strList(s, "sampler_priority")?.let { JsonArray(it.map { JsonPrimitive(it) }) } else JsonNull)
            putJson("samplers", if (type == LLAMACPP) strList(s, "samplers")?.let { JsonArray(it.map { JsonPrimitive(it) }) } else null)
            putJson("stopping_strings", JsonArray(input.stoppingStrings.map { JsonPrimitive(it) }))
            putJson("stop", JsonArray(input.stoppingStrings.map { JsonPrimitive(it) }))
            putJson("truncation_length", JsonPrimitive(input.maxContext))
            putJson("ban_eos_token", bool(s, "ban_eos_token")?.let { JsonPrimitive(it) })
            putJson("skip_special_tokens", bool(s, "skip_special_tokens")?.let { JsonPrimitive(it) })
            putJson("include_reasoning", bool(s, "include_reasoning")?.let { JsonPrimitive(it) })
            putJson("top_a", jsNum(num(s, "top_a")))
            putJson("tfs", jsNum(num(s, "tfs")))
            putJson("epsilon_cutoff", if (type == OOBA || type == MANCER) jsNum(num(s, "epsilon_cutoff")) else JsonNull)
            putJson("eta_cutoff", if (type == OOBA || type == MANCER) jsNum(num(s, "eta_cutoff")) else JsonNull)
            putJson("mirostat_mode", jsNum(num(s, "mirostat_mode")))
            putJson("mirostat_tau", jsNum(num(s, "mirostat_tau")))
            putJson("mirostat_eta", jsNum(num(s, "mirostat_eta")))
            putJson("custom_token_bans", if (type == APHRODITE || type == MANCER) JsonArray(toIntArray(bans.bannedTokens).map { JsonPrimitive(it) }) else JsonPrimitive(bans.bannedTokens))
            putJson("banned_strings", JsonArray(bans.bannedStrings.map { JsonPrimitive(it) }))
            putJson("api_type", type?.let { JsonPrimitive(it) })
            putJson("api_server", JsonPrimitive(getTextGenServer(s, type)))
            putJson("sampler_order", if (type == KOBOLDCPP) intList(s, "sampler_order")?.let { JsonArray(it.map { JsonPrimitive(it) }) } else null)
            putJson("xtc_threshold", jsNum(num(s, "xtc_threshold")))
            putJson("xtc_probability", jsNum(num(s, "xtc_probability")))
            putJson("nsigma", jsNum(num(s, "nsigma")))
            putJson("top_n_sigma", jsNum(num(s, "nsigma")))
            putJson("min_keep", jsNum(num(s, "min_keep")))
            putJson("adaptive_target", jsNum(num(s, "adaptive_target")))
            putJson("adaptive_decay", jsNum(num(s, "adaptive_decay")))
        }

        val nonAphrodite = buildJsonObject {
            putJson("rep_pen", jsNum(num(s, "rep_pen")))
            putJson("rep_pen_range", jsNum(num(s, "rep_pen_range")))
            putJson("repetition_decay", if (type == TABBY) jsNum(num(s, "rep_pen_decay")) else null)
            putJson("repetition_penalty_range", jsNum(num(s, "rep_pen_range")))
            putJson("encoder_repetition_penalty", if (type == OOBA) jsNum(num(s, "encoder_rep_pen")) else null)
            putJson("no_repeat_ngram_size", if (type == OOBA) jsNum(num(s, "no_repeat_ngram_size")) else null)
            putJson("penalty_alpha", if (type == OOBA) jsNum(num(s, "penalty_alpha")) else null)
            putJson("temperature_last", if (type == OOBA || type == APHRODITE || type == TABBY) bool(s, "temperature_last")?.let { JsonPrimitive(it) } else null)
            putJson("speculative_ngram", if (type == TABBY) jsNum(num(s, "speculative_ngram")) else null)
            putJson("do_sample", if (type == OOBA) bool(s, "do_sample")?.let { JsonPrimitive(it) } else null)
            putJson("seed", if (type == HUGGINGFACE) (seedOrNull(s) ?: jsNum(randomSeed())) else seedOrNull(s))
            putJson("guidance_scale", jsNum(input.cfgValues?.get("guidanceScale")?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull ?: num(s, "guidance_scale") ?: 1.0))
            putJson("negative_prompt", JsonPrimitive(input.cfgValues?.get("negativePrompt")?.jsonPrimitive?.contentOrNull ?: str(s, "negative_prompt") ?: ""))
            putJson("grammar_string", str(s, "grammar_string")?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) })
            putJson("json_schema", if (type == TABBY || type == LLAMACPP) jsonSchema else null)
            putJson("repeat_penalty", jsNum(num(s, "rep_pen")))
            putJson("repeat_last_n", jsNum(num(s, "rep_pen_range")))
            putJson("n_predict", input.maxTokens?.let { JsonPrimitive(it) } ?: JsonNull)
            putJson("num_predict", input.maxTokens?.let { JsonPrimitive(it) } ?: JsonNull)
            putJson("num_ctx", JsonPrimitive(input.maxContext))
            putJson("mirostat", jsNum(num(s, "mirostat_mode")))
            putJson("ignore_eos", bool(s, "ban_eos_token")?.let { JsonPrimitive(it) })
            putJson("n_probs", if (input.requestTokenProbabilities) JsonPrimitive(10) else null)
            putJson("rep_pen_slope", jsNum(num(s, "rep_pen_slope")))
        }

        val vllm = buildJsonObject {
            putJson("n", if (canMultiSwipe) jsNum(num(s, "n")) else JsonPrimitive(1))
            putJson("ignore_eos", bool(s, "ignore_eos_token")?.let { JsonPrimitive(it) })
            putJson("spaces_between_special_tokens", bool(s, "spaces_between_special_tokens")?.let { JsonPrimitive(it) })
            putJson("seed", seedOrNull(s))
        }

        val aphrodite = buildJsonObject {
            putJson("n", if (canMultiSwipe) jsNum(num(s, "n")) else JsonPrimitive(1))
            putJson("frequency_penalty", jsNum(num(s, "freq_pen")))
            putJson("presence_penalty", jsNum(num(s, "presence_pen")))
            putJson("repetition_penalty", jsNum(num(s, "rep_pen")))
            putJson("seed", seedOrNull(s))
            putJson("stop", JsonArray(input.stoppingStrings.map { JsonPrimitive(it) }))
            putJson("temperature", jsNum(tempVal))
            putJson("temperature_last", bool(s, "temperature_last")?.let { JsonPrimitive(it) })
            putJson("top_p", jsNum(num(s, "top_p")))
            putJson("top_k", jsNum(num(s, "top_k")))
            putJson("top_a", jsNum(num(s, "top_a")))
            putJson("min_p", jsNum(num(s, "min_p")))
            putJson("tfs", jsNum(num(s, "tfs")))
            putJson("eta_cutoff", jsNum(num(s, "eta_cutoff")))
            putJson("epsilon_cutoff", jsNum(num(s, "epsilon_cutoff")))
            putJson("typical_p", jsNum(num(s, "typical_p")))
            putJson("smoothing_factor", jsNum(num(s, "smoothing_factor")))
            putJson("smoothing_curve", jsNum(num(s, "smoothing_curve")))
            putJson("ignore_eos", bool(s, "ignore_eos_token")?.let { JsonPrimitive(it) })
            putJson("min_tokens", jsNum(num(s, "min_length")))
            putJson("skip_special_tokens", bool(s, "skip_special_tokens")?.let { JsonPrimitive(it) })
            putJson("spaces_between_special_tokens", bool(s, "spaces_between_special_tokens")?.let { JsonPrimitive(it) })
            putJson("guided_grammar", str(s, "grammar_string")?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) })
            putJson("guided_json", jsonSchema)
            putJson("early_stopping", JsonPrimitive(false))
            putJson("include_stop_str_in_output", JsonPrimitive(false))
            putJson("dynatemp_min", if (dynatemp) jsNum(num(s, "min_temp")) else null)
            putJson("dynatemp_max", if (dynatemp) jsNum(num(s, "max_temp")) else null)
            putJson("dynatemp_exponent", if (dynatemp) jsNum(num(s, "dynatemp_exponent")) else null)
            putJson("xtc_threshold", jsNum(num(s, "xtc_threshold")))
            putJson("xtc_probability", jsNum(num(s, "xtc_probability")))
            putJson("nsigma", jsNum(num(s, "nsigma")))
            putJson("custom_token_bans", JsonArray(toIntArray(bans.bannedTokens).map { JsonPrimitive(it) }))
            putJson("no_repeat_ngram_size", jsNum(num(s, "no_repeat_ngram_size")))
            putJson("sampler_priority", if (type == APHRODITE && strList(s, "samplers_priorities") != APHRODITE_DEFAULT_ORDER) strList(s, "samplers_priorities")?.let { JsonArray(it.map { JsonPrimitive(it) }) } else null)
        }

        // 官方顺序：OPENROUTER/KOBOLDCPP/HUGGINGFACE/MANCER/TABBY·LLAMACPP 的修改先作用于 params，
        // 之后才 switch 合并 vllm/aphrodite/nonAphrodite（键顺序与官方 JSON.stringify 一致）
        val preMerge = params.toMutableMap()
        if (type == OPENROUTER) {
            setOpt(preMerge, "provider", s["openrouter_providers"] as? JsonArray)
            setOpt(preMerge, "quantizations", s["openrouter_quantizations"] as? JsonArray)
            setOpt(preMerge, "allow_fallbacks", bool(s, "openrouter_allow_fallbacks")?.let { JsonPrimitive(it) })
        }
        if (type == KOBOLDCPP) {
            setOpt(preMerge, "grammar", str(s, "grammar_string")?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) })
            setOpt(preMerge, "grammar_retain_state", if (!str(s, "grammar_string").isNullOrEmpty() && input.isContinue) JsonPrimitive(true) else null)
            setOpt(preMerge, "trim_stop", JsonPrimitive(true))
            setOpt(preMerge, "dry_sequence_breakers", parseSequenceBreakers(str(s, "dry_sequence_breakers")))
        }
        if (type == HUGGINGFACE) {
            setOpt(preMerge, "top_p", jsNum((num(s, "top_p") ?: 0.0).coerceIn(0.0, 0.999)))
            setOpt(preMerge, "stop", JsonArray(input.stoppingStrings.take(4).map { JsonPrimitive(it) }))
        }
        if (type == MANCER) {
            setOpt(preMerge, "n", if (canMultiSwipe) jsNum(num(s, "n")) else JsonPrimitive(1))
            setOpt(preMerge, "epsilon_cutoff", jsNum((num(s, "epsilon_cutoff") ?: 0.0) / 1000.0))
            setOpt(preMerge, "eta_cutoff", jsNum((num(s, "eta_cutoff") ?: 0.0) / 1000.0))
            setOpt(preMerge, "dynatemp_mode", if (preMerge["dynamic_temperature"] == JsonPrimitive(true)) JsonPrimitive(1) else JsonPrimitive(0))
            setOpt(preMerge, "dynatemp_min", preMerge["dynatemp_low"])
            setOpt(preMerge, "dynatemp_max", preMerge["dynatemp_high"])
            preMerge.remove("dynatemp_low")
            preMerge.remove("dynatemp_high")
            setOpt(preMerge, "dry_sequence_breakers", parseSequenceBreakers(str(s, "dry_sequence_breakers")))
        }
        if (type == TABBY || type == LLAMACPP) {
            setOpt(preMerge, "n", if (canMultiSwipe) jsNum(num(s, "n")) else JsonPrimitive(1))
        }

        var finalObj = JsonObject(preMerge).let { base ->
            when (type) {
                VLLM, INFERMATICAI -> base.mergeInto(vllm)
                APHRODITE -> base.mergeInto(aphrodite)
                else -> base.mergeInto(nonAphrodite)
            }
        }

        val logitBiasList = (s["logit_bias"] as? JsonArray)?.takeIf { it.isNotEmpty() }
        if (logitBiasList != null) {
            finalObj = finalObj.mergeInto(buildJsonObject { putJson("logit_bias", calculateLogitBias(s, input.tokenize)) })
        }
        if (type == LLAMACPP || type == OLLAMA) {
            val logitBiasArray = buildJsonArray {
                (finalObj["logit_bias"] as? JsonObject)?.forEach { (k, v) ->
                    add(buildJsonArray { add(JsonPrimitive(k)); add(v) })
                }
                toIntArray(bans.bannedTokens).forEach { add(buildJsonArray { add(JsonPrimitive(it)); add(JsonPrimitive(false)) }) }
            }
            val sequenceBreakers = parseSequenceBreakers(str(s, "dry_sequence_breakers"))
            val llamaParams = buildJsonObject {
                putJson("logit_bias", logitBiasArray)
                putJson("grammar", str(s, "grammar_string")?.let { JsonPrimitive(it) })
                putJson("cache_prompt", JsonPrimitive(true))
                if (sequenceBreakers != null && sequenceBreakers.isNotEmpty()) putJson("dry_sequence_breakers", sequenceBreakers)
            }
            finalObj = finalObj.mergeInto(llamaParams)
        }
        if (type == LLAMACPP || type == APHRODITE) {
            finalObj = if (jsonSchema != null) {
                finalObj.removeKeys("grammar_string", "grammar", "guided_grammar")
            } else {
                finalObj.removeKeys("json_schema", "guided_json")
            }
        }
        // JS：base 中条件为 false 的键以 undefined 占位（epsilon/eta/early_stopping/sampler_priority），
        // 后续合并赋值时保持原位置；最终仍为占位的键输出时省略（maxTokens 四个字段除外，JS 明确输出 null）
        val nullPlaceholders = finalObj.entries
            .filter { it.value == JsonNull && it.key !in setOf("max_new_tokens", "max_tokens", "n_predict", "num_predict") }
            .map { it.key }
        if (nullPlaceholders.isNotEmpty()) {
            finalObj = finalObj.removeKeys(*nullPlaceholders.toTypedArray())
        }
        return finalObj.toString()
    }

    // ---- 官方 helper 移植 ----

    private fun getTextGenServer(s: JsonObject, type: String?): String = when (type) {
        FEATHERLESS -> "https://api.featherless.ai/v1"
        MANCER -> "https://neuro.mancer.tech"
        TOGETHERAI -> "https://api.together.xyz"
        INFERMATICAI -> "https://api.totalgpt.ai"
        DREAMGEN -> "https://dreamgen.com"
        OPENROUTER -> "https://openrouter.ai/api"
        else -> (s["server_urls"] as? JsonObject)?.get(type ?: "")?.jsonPrimitive?.contentOrNull ?: ""
    }

    private fun getLogprobsNumber(type: String?): Int =
        if (type == VLLM || type == INFERMATICAI) 5 else 10

    private data class TokenBans(val bannedTokens: String, val bannedStrings: List<String>)

    private fun customTokenBans(s: JsonObject, input: BuildInput): TokenBans {
        if (bool(s, "send_banned_tokens") != true ||
            (str(s, "banned_tokens").isNullOrEmpty() && str(s, "global_banned_tokens").isNullOrEmpty() && input.bannedInMacros.isEmpty())
        ) {
            return TokenBans("", emptyList())
        }
        val bannedTokens = mutableListOf<Int>()
        val bannedStrings = mutableListOf<String>()
        val sequences = (
            (str(s, "banned_tokens") ?: "").split("\n") +
                (str(s, "global_banned_tokens") ?: "").split("\n") +
                input.bannedInMacros
            )
            .filter { it.isNotEmpty() }
            .distinct()
        for (line in sequences) {
            when {
                line.startsWith("[") && line.endsWith("]") -> {
                    runCatching {
                        val tokens = json.parseToJsonElement(line).jsonArray
                        if (tokens.all { it.jsonPrimitive.intOrNull != null }) {
                            bannedTokens += tokens.map { it.jsonPrimitive.content.toInt() }
                        }
                    }
                }
                line.startsWith("\"") && line.endsWith("\"") -> bannedStrings += line.substring(1, line.length - 1)
                else -> runCatching { bannedTokens += input.tokenize(line) }
            }
        }
        return TokenBans(bannedTokens.distinct().joinToString(","), bannedStrings)
    }

    private fun calculateLogitBias(s: JsonObject, tokenize: (String) -> List<Int>): JsonObject {
        val biasPreset = (s["logit_bias"] as? JsonArray) ?: return JsonObject(emptyMap())
        if (biasPreset.isEmpty()) return JsonObject(emptyMap())
        val result = mutableMapOf<String, Double>()
        fun addBias(bias: Double, sequence: List<Int>) {
            if (sequence.isEmpty()) return
            for (logit in sequence) result[logit.toString()] = bias
        }
        for (entry in biasPreset) {
            val e = entry.jsonObject
            val text = str(e, "text") ?: continue
            if (text.isEmpty()) continue
            val trimmed = text.trim()
            if (trimmed.isEmpty()) continue
            val value = e["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            when {
                trimmed.startsWith("{") && trimmed.endsWith("}") ->
                    addBias(value, tokenize(trimmed.substring(1, trimmed.length - 1)))
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    runCatching {
                        val tokens = json.parseToJsonElement(trimmed).jsonArray
                        if (tokens.all { it.jsonPrimitive.intOrNull != null }) {
                            addBias(value, tokens.map { it.jsonPrimitive.content.toInt() })
                        }
                    }
                }
                else -> addBias(value, tokenize(" $trimmed"))
            }
        }
        // JS 对象键顺序：整数型键按数值升序排在前面，其余按插入序（JSON.stringify 语义）
        val sortedKeys = result.keys.sortedWith(
            compareBy({ if (it.toIntOrNull() != null) 0 else 1 }, { it.toIntOrNull() ?: 0 }, { it })
        )
        return buildJsonObject { sortedKeys.forEach { k -> putJson(k, jsNum(result[k])) } }
    }

    private fun toIntArray(value: String): List<Int> {
        if (value.isEmpty()) return emptyList()
        return value.split(",").mapNotNull { it.toIntOrNull() }
    }

    private fun replaceMacrosInList(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        return runCatching {
            val array = json.parseToJsonElement(value).jsonArray
            val items = array.map { it.jsonPrimitive.contentOrNull ?: it.toString() }
            buildJsonArray { items.forEach { add(JsonPrimitive(it)) } }.toString()
        }.getOrElse {
            value.split(",").joinToString(",")
        }
    }

    /** 官方 params.parseSequenceBreakers：JSON 数组优先，否则逗号切分；null/不可解析且非字符串 → null。 */
    private fun parseSequenceBreakers(value: String?): JsonArray? {
        if (value == null) return null
        return runCatching {
            val parsed = json.parseToJsonElement(value)
            parsed as? JsonArray
        }.getOrElse {
            if (value.isNotEmpty()) JsonArray(value.split(",").map { JsonPrimitive(it) }) else null
        }
    }

    private fun jsNum(value: Double?): JsonPrimitive? {
        if (value == null) return null
        return if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            JsonPrimitive(value.toInt())
        } else {
            JsonPrimitive(value)
        }
    }

    private fun seedOrNull(s: JsonObject): JsonPrimitive? {
        val seed = num(s, "seed") ?: return null
        return if (seed >= 0) jsNum(seed) else null
    }

    private fun randomSeed(): Double = Random.nextDouble(0.0, 4294967296.0)

    private fun str(s: JsonObject, key: String): String? =
        (s[key] as? JsonPrimitive)?.contentOrNull

    private fun num(s: JsonObject, key: String): Double? =
        (s[key] as? JsonPrimitive)?.doubleOrNull

    private fun bool(s: JsonObject, key: String): Boolean? =
        (s[key] as? JsonPrimitive)?.booleanOrNull

    private fun strList(s: JsonObject, key: String): List<String>? =
        (s[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }

    private fun intList(s: JsonObject, key: String): List<Int>? =
        (s[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putJson(key: String, value: JsonElement?) {
        if (value != null) put(key, value)
    }

    private fun setOpt(map: MutableMap<String, JsonElement>, key: String, value: JsonElement?) {
        if (value != null) map[key] = value else map.remove(key)
    }

    private fun JsonObject.mergeInto(other: JsonObject): JsonObject =
        JsonObject(toMutableMap().apply { putAll(other) })

    private fun JsonObject.removeKeys(vararg keys: String): JsonObject =
        JsonObject(toMutableMap().apply { keys.forEach { remove(it) } })
}
