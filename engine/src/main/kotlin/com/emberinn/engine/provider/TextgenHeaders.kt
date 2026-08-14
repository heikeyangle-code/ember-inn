package com.emberinn.engine.provider

/**
 * 官方 src/additional-headers.js getMancerHeaders / getInfermaticAIHeaders / getFeatherlessHeaders 移植。
 * 差分：scripts/diff/textgen-headers-official.mjs → TextgenHeadersDiffTest。
 */
object TextgenHeaders {

    /** 官方 constants.js FEATHERLESS_HEADERS。 */
    val FEATHERLESS_HEADERS: Map<String, String> = mapOf(
        "HTTP-Referer" to "https://sillytavern.app",
        "X-Title" to "SillyTavern",
    )

    /**
     * 按 provider id 返回官方对应 API 的请求头；非 textgen 三家返回空（由 applyAuth 走默认 Bearer）。
     * mancer：X-API-KEY + Authorization；infermaticai：Authorization；featherless：HTTP-Referer/X-Title + Authorization。
     */
    fun forProvider(providerId: String, apiKey: String): Map<String, String> = when (providerId) {
        "textgen-mancer" -> if (apiKey.isBlank()) {
            emptyMap()
        } else {
            mapOf("X-API-KEY" to apiKey, "Authorization" to "Bearer $apiKey")
        }
        "textgen-infermaticai" -> if (apiKey.isBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to "Bearer $apiKey")
        }
        "textgen-featherless" -> if (apiKey.isBlank()) {
            FEATHERLESS_HEADERS
        } else {
            FEATHERLESS_HEADERS + ("Authorization" to "Bearer $apiKey")
        }
        else -> emptyMap()
    }
}
