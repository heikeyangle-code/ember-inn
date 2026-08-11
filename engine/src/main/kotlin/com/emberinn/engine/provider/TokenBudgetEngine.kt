package com.emberinn.engine.provider

/**
 * 官方 script.js getMaxContextTokens / getMaxResponseTokens / getMaxPromptTokens
 * + nai-settings.js getKayraMaxContextTokens 的引擎移植。
 */
data class TokenBudgetConfig(
    val mainApi: String = "openai",
    val maxContext: Int = 0,
    val openaiMaxContext: Int = 0,
    val openaiMaxTokens: Int = 0,
    val amountGen: Int = 0,
    val novelModel: String = "",
    val novelTier: Int? = null,
)

object TokenBudgetEngine {

    fun getMaxContextTokens(config: TokenBudgetConfig): Int {
        when (config.mainApi) {
            "kobold", "koboldhorde", "textgenerationwebui" -> return config.maxContext
            "novel" -> {
                var max = config.maxContext
                if (config.novelModel.contains("clio")) {
                    max = minOf(max, 8192)
                }
                if (config.novelModel.contains("kayra")) {
                    max = minOf(max, 8192)
                    val subscriptionLimit = getKayraMaxContextTokens(config.novelTier)
                    if (subscriptionLimit != null && max > subscriptionLimit) {
                        max = subscriptionLimit
                    }
                }
                if (config.novelModel.contains("erato")) {
                    max = minOf(max, 8192) - 10
                }
                return max
            }
            "openai" -> return config.openaiMaxContext
        }
        return 1487
    }

    fun getMaxResponseTokens(config: TokenBudgetConfig): Int {
        when (config.mainApi) {
            "kobold", "koboldhorde", "textgenerationwebui", "novel" -> return config.amountGen
            "openai" -> return config.openaiMaxTokens
        }
        return 0
    }

    fun getMaxPromptTokens(
        config: TokenBudgetConfig,
        overrideResponseLength: Int? = null,
    ): Int {
        val override = if (overrideResponseLength == null || overrideResponseLength <= 0) {
            null
        } else {
            overrideResponseLength
        }
        return getMaxContextTokens(config) - (override ?: getMaxResponseTokens(config))
    }

    fun getKayraMaxContextTokens(tier: Int?): Int? = when (tier) {
        1 -> 4096
        2, 3 -> 8192
        else -> null
    }
}
