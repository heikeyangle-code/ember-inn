package com.emberinn.engine.provider

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 chat-completions.js /bias 端点移植：
 * - getTokenizerModel → claude 无 bias；sentencepiece/web 族官方依赖 sentencepiece-js / web-tokenizers
 *   （App 未捆绑，按官方“tokenizer 不可用时返回 {}”语义兜底，登记边界）；
 * - 其余走 tiktoken（JTokkit），逐条目 encode：text 为 JSON 数字数组时直接用 id 列表，
 *   否则编码文本；result[tokenId] = value（后写覆盖先写）。
 */
object LogitBiasEngine {

    private val json = Json { ignoreUnknownKeys = true }
    private val registry = Encodings.newDefaultEncodingRegistry()

    private fun encodingFor(key: String): Encoding? = runCatching {
        when (key) {
            "o1", "gpt-4o" -> registry.getEncoding(EncodingType.O200K_BASE)
            "gpt-4-32k", "gpt-4", "gpt-3.5-turbo-0301", "gpt-3.5-turbo" ->
                registry.getEncoding(EncodingType.CL100K_BASE)
            else -> registry.getEncodingForModel(key).orElse(null)
                ?: registry.getEncoding(EncodingType.CL100K_BASE)
        }
    }.getOrNull()

    fun compute(model: String, entries: List<BiasEntry>): Map<String, Double> {
        val key = TokenizerModel.map(model)
        if (TokenizerModel.isClaude(key)) return emptyMap()
        if (TokenizerModel.isSentencepiece(key) || TokenizerModel.isWeb(key)) return emptyMap()
        val encoding = encodingFor(key) ?: return emptyMap()
        val out = linkedMapOf<String, Double>()
        for (entry in entries) {
            if (entry.text.isBlank()) continue
            val tokens: List<Int>? = try {
                val trimmed = entry.text.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    rawIds(trimmed) ?: encoding.encode(entry.text).toArray().toList()
                } else {
                    encoding.encode(entry.text).toArray().toList()
                }
            } catch (e: Exception) {
                null
            }
            if (tokens == null) continue
            for (token in tokens) out[token.toString()] = entry.value
        }
        return out
    }

    /** 官方 getEntryTokens：text 为 JSON 数字数组 → 直接用 token id。 */
    private fun rawIds(text: String): List<Int>? = runCatching {
        val arr = json.parseToJsonElement(text).jsonArray
        val ids = arr.mapNotNull { it.jsonPrimitive.intOrNull }
        if (ids.size == arr.size) ids else null
    }.getOrNull()
}
