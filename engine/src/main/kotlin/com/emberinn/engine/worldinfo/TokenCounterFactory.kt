package com.emberinn.engine.worldinfo

/**
 * Token 计数器工厂：OpenAI 模型走 JTokkit（tiktoken 移植），
 * 未知模型回退 cl100k_base。Claude/Gemini 等官方 web tokenizer 属于边界。
 */
object TokenCounterFactory {
    /** Claude/Llama3 走 HF BPE（模型文件打包）；其余 OpenAI 系走 tiktoken；sentencepiece 族登记边界。 */
    fun forModel(model: String): TokenCounter {
        com.emberinn.engine.tokenizer.HfBpeTokenizer.forModel(model)?.let { tok ->
            return TokenCounter { tok.count(it) }
        }
        return OpenAiTokenCounter(model)
    }
}
