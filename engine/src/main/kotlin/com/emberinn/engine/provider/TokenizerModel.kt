package com.emberinn.engine.provider

/**
 * 官方 src/endpoints/tokenizers.js getTokenizerModel 逐字移植：
 * 模型名 → 分词器键；bias 端点再按键分 sentencepiece/web/tiktoken 三族。
 * 差分：scripts/diff/tokenizer-model-official.mjs → TokenizerModelDiffTest。
 */
object TokenizerModel {

    /** 官方 sentencepieceTokenizers。 */
    val SENTENCEPIECE = setOf("llama", "nerdstash", "nerdstash_v2", "mistral", "yi", "gemma", "jamba")

    /** 官方 webTokenizers。 */
    val WEB = setOf("claude", "llama3", "command-r", "command-a", "qwen2", "nemo", "deepseek")

    /** 官方 tokenizers.js TEXT_COMPLETION_MODELS（bias 走 tiktoken，键=模型名）。 */
    val TEXT_COMPLETION_MODELS = setOf(
        "gpt-3.5-turbo-instruct", "gpt-3.5-turbo-instruct-0914", "text-davinci-003",
        "text-davinci-002", "text-davinci-001", "text-curie-001", "text-babbage-001",
        "text-ada-001", "code-davinci-002", "code-davinci-001", "code-cushman-002",
        "code-cushman-001", "text-davinci-edit-001", "code-davinci-edit-001",
        "text-embedding-ada-002", "text-similarity-davinci-001", "text-similarity-curie-001",
        "text-similarity-babbage-001", "text-similarity-ada-001",
        "text-search-davinci-doc-001", "text-search-curie-doc-001",
        "text-search-babbage-doc-001", "text-search-ada-doc-001",
        "code-search-babbage-code-001", "code-search-ada-code-001",
    )

    fun map(requestModel: String): String {
        val m = requestModel
        if (m == "o1" || m.contains("o1-preview") || m.contains("o1-mini") || m.contains("o3-mini")) return "o1"
        if (m.contains("gpt-5") || m.contains("o3") || m.contains("o4-mini")) return "o1"
        if (m.contains("gpt-4o") || m.contains("chatgpt-4o-latest")) return "gpt-4o"
        if (m.contains("gpt-4.1") || m.contains("gpt-4.5")) return "gpt-4o"
        if (m.contains("gpt-4-32k")) return "gpt-4-32k"
        if (m.contains("gpt-4")) return "gpt-4"
        if (m.contains("gpt-3.5-turbo-0301")) return "gpt-3.5-turbo-0301"
        if (m.contains("gpt-3.5-turbo")) return "gpt-3.5-turbo"
        if (m in TEXT_COMPLETION_MODELS) return m
        if (m.contains("claude")) return "claude"
        if (m.contains("llama3") || m.contains("llama-3")) return "llama3"
        if (m.contains("llama")) return "llama"
        if (m.contains("mistral")) return "mistral"
        if (m.contains("yi")) return "yi"
        if (m.contains("deepseek")) return "deepseek"
        if (m.contains("gemma") || m.contains("gemini") || m.contains("learnlm")) return "gemma"
        if (m.contains("jamba")) return "jamba"
        if (m.contains("qwen2")) return "qwen2"
        if (m.contains("command-r")) return "command-r"
        if (m.contains("command-a")) return "command-a"
        if (m.contains("nemo")) return "nemo"
        return "gpt-3.5-turbo"
    }

    /** bias 端点：claude 无 bias。 */
    fun isClaude(key: String): Boolean = key == "claude"

    /** sentencepiece 族（App 未捆绑 sentencepiece-js，登记边界）。 */
    fun isSentencepiece(key: String): Boolean = key in SENTENCEPIECE

    /** web 族（App 未捆绑 web-tokenizers，登记边界）。 */
    fun isWeb(key: String): Boolean = key in WEB
}
