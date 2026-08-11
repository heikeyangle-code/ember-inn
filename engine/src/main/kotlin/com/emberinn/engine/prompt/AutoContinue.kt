package com.emberinn.engine.prompt

/**
 * 官方 script.js shouldAutoContinue 的引擎移植（script.js:5657）。
 * getTokenCount 由调用方注入（App 用 TokenCounterFactory）。
 */
data class AutoContinueConfig(
    val enabled: Boolean = false,
    val targetLength: Int = 0,
    val allowChatCompletions: Boolean = true,
    val isSendPress: Boolean = false,
    val generationStopped: Boolean = false,
    val mainApi: String = "openai",
    val textareaText: String = "",
    val lastMessageText: String? = null,
)

object AutoContinueEngine {

    private const val USABLE_LENGTH = 5

    fun shouldAutoContinue(
        messageChunk: String,
        isImpersonate: Boolean,
        config: AutoContinueConfig,
        tokenCount: (String) -> Int,
    ): Boolean {
        if (!config.enabled) return false
        if (isImpersonate) return false
        if (config.isSendPress) return false
        if (config.generationStopped) return false
        if (config.targetLength <= 0) return false
        if (config.mainApi == "openai" && !config.allowChatCompletions) return false
        if (config.textareaText.isNotEmpty()) return false
        if (messageChunk.trim().length <= USABLE_LENGTH) return false

        val lastMessage = config.lastMessageText ?: return false
        val messageLength = tokenCount(lastMessage)
        return messageLength < config.targetLength
    }
}
