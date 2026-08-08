package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 官方 src/endpoints/tokenizers.js TEXT_COMPLETION_MODELS（走 /completions 的 OpenAI 模型）。 */
val TEXT_COMPLETION_MODELS = listOf(
    "gpt-3.5-turbo-instruct",
    "gpt-3.5-turbo-instruct-0914",
    "text-davinci-003",
    "text-davinci-002",
    "text-davinci-001",
    "text-curie-001",
    "text-babbage-001",
    "text-ada-001",
    "code-davinci-002",
    "code-davinci-001",
    "code-cushman-002",
    "code-cushman-001",
    "text-davinci-edit-001",
    "code-davinci-edit-001",
    "text-embedding-ada-002",
    "text-similarity-davinci-001",
    "text-similarity-curie-001",
    "text-similarity-babbage-001",
    "text-similarity-ada-001",
    "text-search-davinci-doc-001",
    "text-search-curie-doc-001",
    "text-search-babbage-doc-001",
    "text-search-ada-doc-001",
    "text-search-code-davinci-code-001",
    "text-search-code-curie-code-001",
    "text-search-code-babbage-code-001",
    "text-search-code-ada-code-001",
    "code-search-babbage-code-001",
    "code-search-ada-code-001",
)

/** JS 数字序列化：整数输出为整数。 */
private fun jsNum(value: Double): JsonPrimitive =
    if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
        JsonPrimitive(value.toInt())
    } else {
        JsonPrimitive(value)
    }

/**
 * OpenAI 文本补全请求体（官方 sendChatCompletionRequest 的 isTextCompletion 分支）。
 * body 差分见 text-completion-body-official.mjs。
 */
object TextCompletionRequestBuilder {

    fun build(
        model: String,
        prompt: String,
        params: SamplerParams,
    ): String = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("prompt", JsonPrimitive(prompt))
        put("temperature", jsNum(params.temperature))
        put("max_tokens", params.maxTokens)
        put("stream", params.stream)
        put("presence_penalty", jsNum(params.presencePenalty))
        put("frequency_penalty", jsNum(params.frequencyPenalty))
        put("top_p", jsNum(params.topP))
    }.toString()
}
