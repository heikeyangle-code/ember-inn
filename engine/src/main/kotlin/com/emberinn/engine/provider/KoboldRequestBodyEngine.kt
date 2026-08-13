package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 官方 Kobold 请求体（src/endpoints/backends/kobold.js /generate 逐字移植）：
 * 非 gui_settings 全字段 + stop_sequence 条件 + localhost→127.0.0.1 + 流式/非流式 URL。
 * 差分：scripts/diff/kobold-body-official.mjs → KoboldBodyDiffTest（12 例）。
 */
object KoboldRequestBodyEngine {

    data class Input(
        val apiServer: String,
        val prompt: String,
        val maxContextLength: Int,
        val maxLength: Int,
        val guiSettings: Boolean = false,
        val streaming: Boolean = false,
        val repPen: Double? = null,
        val repPenRange: Int? = null,
        val repPenSlope: Double? = null,
        val temperature: Double? = null,
        val tfs: Double? = null,
        val topA: Double? = null,
        val topK: Int? = null,
        val topP: Double? = null,
        val minP: Double? = null,
        val typical: Double? = null,
        val samplerOrder: List<Int>? = null,
        val singleline: Boolean = false,
        val useDefaultBadwordsids: Boolean = false,
        val mirostat: Int? = null,
        val mirostatEta: Double? = null,
        val mirostatTau: Double? = null,
        val grammar: String? = null,
        val samplerSeed: Int? = null,
        val stopSequence: String? = null,
    )

    data class Result(val apiServer: String, val url: String, val body: String)

    /** 由“应用 kobold 预设后的设置 JSON + 连接档案”组装请求输入（App 传输映射；字段沿用官方 kai_settings）。 */
    fun fromSettingsJson(
        apiServer: String,
        prompt: String,
        maxContextLength: Int,
        maxLength: Int,
        streaming: Boolean,
        settings: kotlinx.serialization.json.JsonObject?,
    ): Input {
        fun d(key: String): Double? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        fun i(key: String): Int? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
        fun b(key: String): Boolean = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
        fun s(key: String): String? = (settings?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content
        return Input(
            apiServer = apiServer,
            prompt = prompt,
            maxContextLength = maxContextLength,
            maxLength = maxLength,
            streaming = streaming,
            repPen = d("rep_pen"),
            repPenRange = i("rep_pen_range"),
            repPenSlope = d("rep_pen_slope"),
            temperature = d("temp"),
            tfs = d("tfs"),
            topA = d("top_a"),
            topK = i("top_k"),
            topP = d("top_p"),
            minP = d("min_p"),
            typical = d("typical"),
            samplerOrder = (settings?.get("sampler_order") as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull },
            useDefaultBadwordsids = b("use_default_badwordsids"),
            mirostat = i("mirostat"),
            mirostatEta = d("mirostat_eta"),
            mirostatTau = d("mirostat_tau"),
            grammar = s("grammar"),
            samplerSeed = i("seed"),
        )
    }

    fun build(input: Input): Result {
        var apiServer = input.apiServer
        if (apiServer.contains("localhost")) {
            apiServer = apiServer.replace("localhost", "127.0.0.1")
        }
        val thisSettings: JsonObject = if (input.guiSettings) {
            buildJsonObject {
                put("prompt", input.prompt)
                put("use_story", false)
                put("use_memory", false)
                put("use_authors_note", false)
                put("use_world_info", false)
                put("max_context_length", input.maxContextLength)
                put("max_length", input.maxLength)
            }
        } else {
            buildJsonObject {
                put("prompt", input.prompt)
                put("use_story", false)
                put("use_memory", false)
                put("use_authors_note", false)
                put("use_world_info", false)
                put("max_context_length", input.maxContextLength)
                put("max_length", input.maxLength)
                putOpt("rep_pen", jsNum(input.repPen))
                putOpt("rep_pen_range", input.repPenRange?.let { JsonPrimitive(it) })
                putOpt("rep_pen_slope", jsNum(input.repPenSlope))
                putOpt("temperature", jsNum(input.temperature))
                putOpt("tfs", jsNum(input.tfs))
                putOpt("top_a", jsNum(input.topA))
                putOpt("top_k", input.topK?.let { JsonPrimitive(it) })
                putOpt("top_p", jsNum(input.topP))
                putOpt("min_p", jsNum(input.minP))
                putOpt("typical", jsNum(input.typical))
                putOpt("sampler_order", input.samplerOrder?.let { JsonArray(it.map { JsonPrimitive(it) }) })
                put("singleline", input.singleline)
                put("use_default_badwordsids", input.useDefaultBadwordsids)
                putOpt("mirostat", input.mirostat?.let { JsonPrimitive(it) })
                putOpt("mirostat_eta", jsNum(input.mirostatEta))
                putOpt("mirostat_tau", jsNum(input.mirostatTau))
                putOpt("grammar", input.grammar?.let { JsonPrimitive(it) })
                putOpt("sampler_seed", input.samplerSeed?.let { JsonPrimitive(it) })
                if (!input.stopSequence.isNullOrEmpty()) {
                    put("stop_sequence", input.stopSequence)
                }
            }
        }
        val url = if (input.streaming) "$apiServer/extra/generate/stream" else "$apiServer/v1/generate"
        return Result(apiServer = apiServer, url = url, body = thisSettings.toString())
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putOpt(key: String, value: JsonElement?) {
        if (value != null) put(key, value)
    }

    private fun jsNum(value: Double?): JsonPrimitive? {
        if (value == null) return null
        return if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            JsonPrimitive(value.toInt())
        } else {
            JsonPrimitive(value)
        }
    }
}
