package com.emberinn.engine.worldinfo

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

/** OpenAI token 计数（JTokkit = tiktoken 官方移植），用于世界书预算 1:1。 */
class OpenAiTokenCounter(private val model: String = "gpt-4o") : TokenCounter {

    private val registry = Encodings.newDefaultEncodingRegistry()
    private val encoding = runCatching { registry.getEncodingForModel(model) }
        .getOrElse { registry.getEncoding(EncodingType.CL100K_BASE) }

    override fun count(text: String): Int = encoding.countTokens(text)
}
