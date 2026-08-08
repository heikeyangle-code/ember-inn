package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAI createGenerationParameters 核心设置（对齐 openai.js settings 字段）。 */
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
)

/** 对齐 openai.js createGenerationParameters 核心（OpenAI/Azure 公共字段）。 */
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
        val logitBias = if (settings.logitBias.isNotEmpty()) {
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

        if (settings.requestTokenProbabilities && settings.source in setOf("openai", "azure_openai", "custom", "deepseek", "xai", "aimlapi", "chutes")) {
            body["logprobs"] = JsonPrimitive(5)
        }

        val isVision = model.contains("gpt") && model.contains("vision")
        if (settings.source in setOf("openai", "azure_openai") && isVision) {
            body.remove("logit_bias")
            body.remove("stop")
            body.remove("logprobs")
        }
        if (settings.source in setOf("openai", "azure_openai") && Regex("gpt-4.5").containsMatchIn(model)) body.remove("logprobs")

        if (settings.source in setOf("openai", "azure_openai") && settings.seed >= 0) {
            body["seed"] = JsonPrimitive(settings.seed)
        }

        if (settings.source in setOf("openai", "azure_openai") && Regex("^(o1|o3|o4)").containsMatchIn(model)) {
            body["max_completion_tokens"] = body.remove("max_tokens") ?: JsonNull
            body.remove("logprobs")
            body.remove("stop")
            body.remove("logit_bias")
            body.remove("temperature")
            body.remove("top_p")
            body.remove("frequency_penalty")
            body.remove("presence_penalty")
            if (Regex("^(openai/)?(o1)").containsMatchIn(model)) body.remove("n")
        }

        return JsonObject(body).toString()
    }
}
