package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/** 官方 nai-settings.js getNovelGenerationData 的输入（SillyTavern 1.18.0 / 8172dcd）。 */
data class NovelGenerationInput(
    val model: String,
    val temperature: Double = 1.0,
    val minLength: Int = 0,
    val tailFreeSampling: Double = 1.0,
    val repetitionPenalty: Double = 1.0,
    val repetitionPenaltyRange: Int = 1024,
    val repetitionPenaltySlope: Double = 0.9,
    val repetitionPenaltyFrequency: Double = 0.0,
    val repetitionPenaltyPresence: Double = 0.0,
    val topA: Double = 0.0,
    val topP: Double = 1.0,
    val topK: Int = 0,
    val minP: Double = 0.0,
    val math1Temp: Double = 1.0,
    val math1Quad: Double = 0.0,
    val math1QuadEntropyScale: Double = 0.0,
    val typicalP: Double = 1.0,
    val mirostatLr: Double = 0.1,
    val mirostatTau: Double = 5.0,
    val phraseRepPen: String = "off",
    val order: List<String>? = null,
    val logitBias: List<JsonElement> = emptyList(),
    val bannedTokens: List<String> = emptyList(),
    val prefix: String = "vanilla",
    val finalPrompt: String = "",
    val maxLength: Int = 200,
    val isImpersonate: Boolean = false,
    val isContinue: Boolean = false,
    val stoppingStrings: List<String> = emptyList(),
    val maximumOutputLength: Int = 600,
    val requestTokenProbabilities: Boolean = false,
)

/**
 * 官方 NovelAI 请求体纯逻辑（getNovelGenerationData / selectPrefix / getTokenizerTypeForModel 逐字移植）。
 * 差分：scripts/diff/novel-body-official.mjs → NovelBodyDiffTest。
 * 打桩登记见脚本头部：getStoppingStrings 注入、getBadWordIds/getTextTokens/calculateLogitBias 恒空、
 * getNovelMaxResponseTokens=512、tokenizers 常量。
 */
object NovelRequestBodyEngine {

    private const val TOKENIZER_NONE = 0
    private const val TOKENIZER_NERD = 1
    private const val TOKENIZER_NERD2 = 2
    private const val TOKENIZER_LLAMA3 = 3
    private val DEFAULT_ORDER = listOf("temperature", "tail_free_sampling", "repetition_penalty", "top_p", "top_k")

    /** 由“应用 novel 预设后的设置 JSON + 连接档案”组装请求输入（App 传输映射；字段名沿用官方 nai_settings）。 */
    fun fromSettingsJson(
        model: String,
        finalPrompt: String,
        maxLength: Int,
        isImpersonate: Boolean,
        isContinue: Boolean,
        stoppingStrings: List<String>,
        requestTokenProbabilities: Boolean,
        settings: kotlinx.serialization.json.JsonObject?,
        defaults: NovelGenerationInput = NovelGenerationInput(model = model),
    ): NovelGenerationInput {
        fun d(key: String): Double? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        fun i(key: String): Int? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
        fun s(key: String): String? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content
        return defaults.copy(
            model = model,
            temperature = d("temperature") ?: defaults.temperature,
            minLength = i("min_length") ?: defaults.minLength,
            tailFreeSampling = d("tail_free_sampling") ?: defaults.tailFreeSampling,
            repetitionPenalty = d("repetition_penalty") ?: defaults.repetitionPenalty,
            repetitionPenaltyRange = i("repetition_penalty_range") ?: defaults.repetitionPenaltyRange,
            repetitionPenaltySlope = d("repetition_penalty_slope") ?: defaults.repetitionPenaltySlope,
            repetitionPenaltyFrequency = d("repetition_penalty_frequency") ?: defaults.repetitionPenaltyFrequency,
            repetitionPenaltyPresence = d("repetition_penalty_presence") ?: defaults.repetitionPenaltyPresence,
            topA = d("top_a") ?: defaults.topA,
            topP = d("top_p") ?: defaults.topP,
            topK = i("top_k") ?: defaults.topK,
            minP = d("min_p") ?: defaults.minP,
            math1Temp = d("math1_temp") ?: defaults.math1Temp,
            math1Quad = d("math1_quad") ?: defaults.math1Quad,
            math1QuadEntropyScale = d("math1_quad_entropy_scale") ?: defaults.math1QuadEntropyScale,
            typicalP = d("typical_p") ?: defaults.typicalP,
            mirostatLr = d("mirostat_lr") ?: defaults.mirostatLr,
            mirostatTau = d("mirostat_tau") ?: defaults.mirostatTau,
            phraseRepPen = s("phrase_rep_pen") ?: defaults.phraseRepPen,
            prefix = s("prefix") ?: defaults.prefix,
            finalPrompt = finalPrompt,
            maxLength = maxLength,
            isImpersonate = isImpersonate,
            isContinue = isContinue,
            stoppingStrings = stoppingStrings,
            requestTokenProbabilities = requestTokenProbabilities,
        )
    }

    fun getTokenizerTypeForModel(model: String): Int = when {
        model.contains("clio") -> TOKENIZER_NERD
        model.contains("kayra") -> TOKENIZER_NERD2
        model.contains("erato") -> TOKENIZER_LLAMA3
        else -> TOKENIZER_NONE
    }

    fun selectPrefix(model: String, selectedPrefix: String, finalPrompt: String): String {
        val isNewModel = model.contains("clio") || model.contains("kayra") || model.contains("erato")
        if (!isNewModel) return "vanilla"
        val tail = finalPrompt.takeLast(1500)
        return if (tail.contains('}')) "special_instruct" else selectedPrefix
    }

    fun build(input: NovelGenerationInput): JsonObject {
        val isKayra = input.model.contains("kayra")
        val isErato = input.model.contains("erato")
        val tokenizerType = getTokenizerTypeForModel(input.model)

        // 官方 Erato：对每个以 \n 开头的停用词追加 12 个变体
        val stops = input.stoppingStrings.toMutableList()
        if (isErato) {
            val extra = mutableListOf<String>()
            for (stoppingString in stops) {
                if (stoppingString.startsWith("\n")) {
                    extra += ".$stoppingString"
                    extra += "!$stoppingString"
                    extra += "?$stoppingString"
                    extra += "*$stoppingString"
                    extra += "\"$stoppingString"
                    extra += "_$stoppingString"
                    extra += "...$stoppingString"
                    extra += ".\"$stoppingString"
                    extra += "?\"$stoppingString"
                    extra += "!\"$stoppingString"
                    extra += ".*$stoppingString"
                    extra += ")$stoppingString"
                }
            }
            stops.addAll(extra)
        }

        // 官方 getTextTokens 桩恒 []：每个停用词 → 空数组；非新模型字段省略（undefined）
        val stopSequences = if (tokenizerType != TOKENIZER_NONE) {
            stops.take(1024).map { JsonArray(emptyList()) }
        } else {
            null
        }
        val badWordIds = if (tokenizerType != TOKENIZER_NONE) JsonArray(emptyList()) else null
        val prefix = selectPrefix(input.model, input.prefix, input.finalPrompt)
        val logitBias = if (tokenizerType != TOKENIZER_NONE && input.logitBias.isNotEmpty()) {
            JsonArray(listOf(buildJsonObject { put("bias", JsonPrimitive(1)); put("sequence", JsonArray(emptyList())) }))
        } else {
            JsonArray(emptyList())
        }
        var prompt = input.finalPrompt
        if (isErato) prompt = "<|startoftext|><|reserved_special_token81|>" + prompt
        val adjustedMaxLength = if (isKayra || isErato) 512 else input.maximumOutputLength
        val maxLength = minOf(input.maxLength, adjustedMaxLength)
        val numLogprobs = if (input.requestTokenProbabilities) JsonPrimitive(10) else null

        return buildJsonObject {
            put("input", JsonPrimitive(prompt))
            put("model", JsonPrimitive(input.model))
            put("use_string", JsonPrimitive(true))
            put("temperature", jsNum(input.temperature))
            put("max_length", JsonPrimitive(maxLength))
            put("min_length", JsonPrimitive(input.minLength))
            put("tail_free_sampling", jsNum(input.tailFreeSampling))
            put("repetition_penalty", jsNum(input.repetitionPenalty))
            put("repetition_penalty_range", JsonPrimitive(input.repetitionPenaltyRange))
            put("repetition_penalty_slope", jsNum(input.repetitionPenaltySlope))
            put("repetition_penalty_frequency", jsNum(input.repetitionPenaltyFrequency))
            put("repetition_penalty_presence", jsNum(input.repetitionPenaltyPresence))
            put("top_a", jsNum(input.topA))
            put("top_p", jsNum(input.topP))
            put("top_k", JsonPrimitive(input.topK))
            put("min_p", jsNum(input.minP))
            put("math1_temp", jsNum(input.math1Temp))
            put("math1_quad", jsNum(input.math1Quad))
            put("math1_quad_entropy_scale", jsNum(input.math1QuadEntropyScale))
            put("typical_p", jsNum(input.typicalP))
            put("mirostat_lr", jsNum(input.mirostatLr))
            put("mirostat_tau", jsNum(input.mirostatTau))
            put("phrase_rep_pen", JsonPrimitive(input.phraseRepPen))
            if (stopSequences != null) put("stop_sequences", JsonArray(stopSequences))
            if (badWordIds != null) put("bad_words_ids", badWordIds)
            put("logit_bias_exp", logitBias)
            put("generate_until_sentence", JsonPrimitive(true))
            put("use_cache", JsonPrimitive(false))
            put("return_full_text", JsonPrimitive(false))
            put("prefix", JsonPrimitive(prefix))
            put("order", JsonArray((input.order ?: DEFAULT_ORDER).map { JsonPrimitive(it) }))
            if (numLogprobs != null) put("num_logprobs", numLogprobs)
        }
    }

    /** JS Number 序列化：整数值回 Long 字符串（1 → "1"），否则 Double.toString（0.9 → "0.9"）。 */
    private fun jsNum(d: Double): JsonPrimitive = JsonUnquotedLiteral(
        if (abs(d) < 9.2e18 && d == d.toLong().toDouble()) d.toLong().toString() else d.toString(),
    )
}
