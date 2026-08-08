package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** OpenAI createGenerationParameters 设置（对齐 openai.js settings 全厂商字段）。 */
data class OpenAiParamsSettings(
    val source: String = "openai",
    val temp: Double = 1.0,
    val freqPen: Double = 0.0,
    val presPen: Double = 0.0,
    val topP: Double = 1.0,
    val maxTokens: Int = 512,
    val stream: Boolean = false,
    val n: Int = 1,
    val userName: String = "",
    val charName: String = "",
    val groupNames: List<String> = emptyList(),
    val showThoughts: Boolean = false,
    val reasoningEffort: String? = null,
    val enableWebSearch: Boolean = false,
    val requestImages: Boolean = false,
    val requestImageResolution: String = "auto",
    val requestImageAspectRatio: String = "1:1",
    val customPromptPostProcessing: String = "NONE",
    val verbosity: String? = null,
    val seed: Int = -1,
    val requestTokenProbabilities: Boolean = false,
    val stopStrings: List<String> = emptyList(),
    val logitBias: Map<String, Double> = emptyMap(),
    val azureBaseUrl: String? = null,
    val azureDeploymentName: String? = null,
    val azureApiVersion: String? = null,
    val topK: Int = 40,
    val minP: Double = 0.1,
    val repetitionPenalty: Double = 1.05,
    val topA: Double = 0.5,
    val useFallback: Boolean = false,
    val provider: JsonElement? = null,
    val quantizations: List<String> = emptyList(),
    val allowFallbacks: Boolean = false,
    val middleout: Boolean = false,
    val nanogptProvider: String? = null,
    val nanogptPaygOverride: String? = null,
    val useSysprompt: Boolean = true,
    val vertexaiAuthMode: String? = null,
    val vertexaiRegion: String? = null,
    val vertexaiExpressProjectId: String? = null,
    val customUrl: String? = null,
    val customIncludeBody: Boolean = false,
    val customExcludeBody: Boolean = false,
    val customIncludeHeaders: Boolean = false,
    val zaiEndpoint: String? = null,
    val siliconflowEndpoint: String? = null,
    val minimaxEndpoint: String? = null,
    val workersAiAccountId: String? = null,
)

/** 对齐 openai.js createGenerationParameters（全厂商分支）。 */
object OpenAiParamsBuilder {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(
        settings: OpenAiParamsSettings,
        model: String,
        type: String,
        messagesJson: String,
    ): String {
        val messages = json.parseToJsonElement(messagesJson)
        val isO1 = settings.source in setOf("openai", "azure_openai") && model in setOf("o1-2024-12-17", "o1")
        val stream = settings.stream && type != "quiet" && !isO1
        val noMultiSwipe = type in setOf("quiet", "impersonate", "continue")
        val canMultiSwipe = settings.n > 1 && !noMultiSwipe &&
            settings.source in setOf("openai", "azure_openai", "custom", "xai", "aimlapi", "moonshot")
        val logitSources = setOf("openai", "azure_openai", "openrouter", "electronhub", "chutes", "custom")
        val logitBias = if (settings.logitBias.isNotEmpty() && settings.source in logitSources) {
            buildJsonObject { settings.logitBias.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        } else null

        val body = buildJsonObject {
            put("type", JsonPrimitive(type))
            put("messages", messages)
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(settings.temp))
            put("frequency_penalty", JsonPrimitive(settings.freqPen))
            put("presence_penalty", JsonPrimitive(settings.presPen))
            put("top_p", JsonPrimitive(settings.topP))
            put("max_tokens", JsonPrimitive(settings.maxTokens))
            put("stream", JsonPrimitive(stream))
            if (logitBias != null) put("logit_bias", logitBias)
            put("stop", JsonArray(settings.stopStrings.map { JsonPrimitive(it) }))
            put("chat_completion_source", JsonPrimitive(settings.source))
            if (canMultiSwipe) put("n", JsonPrimitive(settings.n))
            put("user_name", JsonPrimitive(settings.userName))
            put("char_name", JsonPrimitive(settings.charName))
            put("group_names", JsonArray(settings.groupNames.map { JsonPrimitive(it) }))
            put("include_reasoning", JsonPrimitive(settings.showThoughts))
            settings.reasoningEffort?.let { put("reasoning_effort", JsonPrimitive(it)) }
            put("enable_web_search", JsonPrimitive(settings.enableWebSearch))
            put("request_images", JsonPrimitive(settings.requestImages))
            put("request_image_resolution", JsonPrimitive(settings.requestImageResolution))
            put("request_image_aspect_ratio", JsonPrimitive(settings.requestImageAspectRatio))
            put("custom_prompt_post_processing", JsonPrimitive(settings.customPromptPostProcessing))
            settings.verbosity?.let { put("verbosity", JsonPrimitive(it)) }
        }.toMutableMap()

        if (settings.source == "azure_openai") {
            body["azure_base_url"] = settings.azureBaseUrl?.let { JsonPrimitive(it) } ?: JsonNull
            body["azure_deployment_name"] = settings.azureDeploymentName?.let { JsonPrimitive(it) } ?: JsonNull
            body["azure_api_version"] = settings.azureApiVersion?.let { JsonPrimitive(it) } ?: JsonNull
            if (Regex("^gpt-[34]").containsMatchIn(model)) body.remove("reasoning_effort")
        }

        if (settings.source == "openrouter") {
            body["top_k"] = JsonPrimitive(settings.topK)
            body["min_p"] = JsonPrimitive(settings.minP)
            body["repetition_penalty"] = JsonPrimitive(settings.repetitionPenalty)
            body["top_a"] = JsonPrimitive(settings.topA)
            body["use_fallback"] = JsonPrimitive(settings.useFallback)
            settings.provider?.let { body["provider"] = it }
            body["quantizations"] = JsonArray(settings.quantizations.map { JsonPrimitive(it) })
            body["allow_fallbacks"] = JsonPrimitive(settings.allowFallbacks)
            body["middleout"] = JsonPrimitive(settings.middleout)
        }

        if (settings.source == "nanogpt") {
            settings.nanogptProvider?.let { body["nanogpt_provider"] = JsonPrimitive(it) }
            settings.nanogptPaygOverride?.let { body["nanogpt_payg_override"] = JsonPrimitive(it) }
            body["top_k"] = JsonPrimitive(settings.topK)
            body["min_p"] = JsonPrimitive(settings.minP)
            body["repetition_penalty"] = JsonPrimitive(settings.repetitionPenalty)
            body["top_a"] = JsonPrimitive(settings.topA)
        }

        if (settings.source in setOf("makersuite", "vertexai")) {
            body["top_k"] = JsonPrimitive(settings.topK)
            body["stop"] = JsonArray(settings.stopStrings.take(5).filter { it.length in 1..16 }.map { JsonPrimitive(it) })
            body["use_sysprompt"] = JsonPrimitive(settings.useSysprompt)
            if (settings.source == "vertexai") {
                settings.vertexaiAuthMode?.let { body["vertexai_auth_mode"] = JsonPrimitive(it) }
                settings.vertexaiRegion?.let { body["vertexai_region"] = JsonPrimitive(it) }
                settings.vertexaiExpressProjectId?.let { body["vertexai_express_project_id"] = JsonPrimitive(it) }
            }
        }

        if (settings.source == "mistralai") {
            body["safe_prompt"] = JsonPrimitive(false)
            body["stop"] = JsonArray(settings.stopStrings.map { JsonPrimitive(it) })
        }

        if (settings.source == "custom") {
            settings.customUrl?.let { body["custom_url"] = JsonPrimitive(it) }
            body["custom_include_body"] = JsonPrimitive(settings.customIncludeBody)
            body["custom_exclude_body"] = JsonPrimitive(settings.customExcludeBody)
            body["custom_include_headers"] = JsonPrimitive(settings.customIncludeHeaders)
        }

        if (settings.source == "cohere") {
            body["top_p"] = JsonPrimitive(settings.topP.coerceIn(0.01, 0.99))
            body["top_k"] = JsonPrimitive(settings.topK)
            body["frequency_penalty"] = JsonPrimitive(settings.freqPen.coerceIn(0.0, 1.0))
            body["presence_penalty"] = JsonPrimitive(settings.presPen.coerceIn(0.0, 1.0))
            body["stop"] = JsonArray(settings.stopStrings.take(5).map { JsonPrimitive(it) })
        }

        if (settings.source == "perplexity") {
            body["top_k"] = JsonPrimitive(settings.topK)
            body["frequency_penalty"] = JsonPrimitive(settings.freqPen)
            body["presence_penalty"] = JsonPrimitive(settings.presPen)
            body.remove("stop")
        }

        if (settings.source == "groq") {
            body.remove("logprobs")
            body.remove("logit_bias")
            body.remove("top_logprobs")
            body.remove("n")
        }

        if (settings.source == "deepseek") {
            if ((body["top_p"] as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0) body["top_p"] = JsonPrimitive(Math.ulp(1.0))
        }

        if (settings.source == "xai") {
            if (model.contains("grok-3-mini")) {
                body.remove("presence_penalty")
                body.remove("frequency_penalty")
                body.remove("stop")
            } else {
                body.remove("reasoning_effort")
            }
            if (model.contains("grok-4") || model.contains("grok-code")) {
                body.remove("presence_penalty")
                body.remove("frequency_penalty")
                if (!model.contains("grok-4-fast-non-reasoning")) body.remove("stop")
            }
        }

        if (settings.source == "electronhub") body["top_k"] = JsonPrimitive(settings.topK)

        if (settings.source == "chutes") {
            body["min_p"] = JsonPrimitive(settings.minP)
            if (settings.topK > 0) body["top_k"] = JsonPrimitive(settings.topK) else body.remove("top_k")
            body["repetition_penalty"] = JsonPrimitive(settings.repetitionPenalty)
            body["stop"] = JsonArray(settings.stopStrings.map { JsonPrimitive(it) })
        }

        if (settings.source == "zai") {
            if ((body["top_p"] as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0) body["top_p"] = JsonPrimitive(0.01)
            body["stop"] = JsonArray(settings.stopStrings.take(1).map { JsonPrimitive(it) })
            body["zai_endpoint"] = JsonPrimitive(settings.zaiEndpoint ?: "common")
            body.remove("presence_penalty")
            body.remove("frequency_penalty")
        }

        if (settings.source == "siliconflow") body["siliconflow_endpoint"] = JsonPrimitive(settings.siliconflowEndpoint ?: "global")

        if (settings.source == "minimax") {
            body["minimax_endpoint"] = JsonPrimitive(settings.minimaxEndpoint ?: "global")
            val t = settings.temp
            if (t.isFinite()) body["temperature"] = JsonPrimitive(t.coerceIn(Math.ulp(1.0), 1.0))
        }

        if (settings.source == "workers_ai") {
            settings.workersAiAccountId?.let { body["workers_ai_account_id"] = JsonPrimitive(it) }
            if (settings.topK > 0) body["top_k"] = JsonPrimitive(minOf(settings.topK, 50)) else body.remove("top_k")
            body["repetition_penalty"] = JsonPrimitive(settings.repetitionPenalty)
            if (settings.seed >= 1) body["seed"] = JsonPrimitive(settings.seed) else body.remove("seed")
            body["top_p"] = JsonPrimitive(maxOf(settings.topP, 0.001))
            body.remove("n")
            body.remove("logit_bias")
        }

        if (settings.source == "moonshot" && Regex("kimi-k2.5").containsMatchIn(model)) {
            body.remove("temperature")
            body.remove("top_p")
            body.remove("frequency_penalty")
            body.remove("presence_penalty")
        }

        val seedSources = setOf("openai", "azure_openai", "openrouter", "mistralai", "custom", "cohere", "groq", "electronhub", "nanogpt", "xai", "pollinations", "aimlapi", "vertexai", "makersuite", "chutes")
        if (settings.source in seedSources && settings.seed >= 0) body["seed"] = JsonPrimitive(settings.seed)

        val oaiOrOpenrouterO1 = (settings.source in setOf("openai", "azure_openai") && Regex("^(o1|o3|o4)").containsMatchIn(model)) ||
            (settings.source == "openrouter" && Regex("^openai/(o1|o3|o4)").containsMatchIn(model))
        if (oaiOrOpenrouterO1) {
            body["max_completion_tokens"] = body.remove("max_tokens") ?: JsonNull
            body.remove("logprobs")
            body.remove("stop")
            body.remove("logit_bias")
            body.remove("temperature")
            body.remove("top_p")
            body.remove("frequency_penalty")
            body.remove("presence_penalty")
            if (Regex("^(openai/)?(o1)").containsMatchIn(model)) {
                if (messages is JsonArray) {
                    val newMessages = messages.map { el ->
                        val o = el.jsonObject
                        if (o["role"]?.jsonPrimitive?.content == "system") {
                            val m = o.toMutableMap()
                            m["role"] = JsonPrimitive("user")
                            JsonObject(m)
                        } else el
                    }
                    body["messages"] = JsonArray(newMessages)
                }
                body.remove("n")
            }
        }

        return JsonObject(body).toString()
    }
}
