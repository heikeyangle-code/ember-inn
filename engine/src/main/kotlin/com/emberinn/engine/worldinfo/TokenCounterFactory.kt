package com.emberinn.engine.worldinfo

/**
 * Token 计数器工厂：OpenAI 模型走 JTokkit（tiktoken 移植），
 * 未知模型回退 cl100k_base。Claude/Gemini 等官方 web tokenizer 属于边界。
 */
object TokenCounterFactory {
    fun forModel(model: String): TokenCounter = OpenAiTokenCounter(model)
}
