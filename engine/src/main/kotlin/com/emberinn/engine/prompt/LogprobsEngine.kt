package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 Token 概率解析（openai.js parseOpenAIChatLogprobs / parseOpenAITextLogprobs /
 * parseChatCompletionLogprobs 1:1）。差分：scripts/diff/logprobs-official.mjs（20 例）。
 * 边界（登记）：text 解析 top_logprobs 整体缺失时官方抛 TypeError（响应契约恒带），不生成用例。
 */
object LogprobsEngine {

    data class TokenLogprobs(val token: String, val topLogprobs: List<Pair<String, Double>>)

    const val SOURCE_AIMLAPI = "aimlapi"
    const val SOURCE_OPENAI = "openai"
    const val SOURCE_AZURE_OPENAI = "azure_openai"
    const val SOURCE_DEEPSEEK = "deepseek"
    const val SOURCE_XAI = "xai"
    const val SOURCE_CUSTOM = "custom"
    const val SOURCE_CHUTES = "chutes"

    /** 官方 parseOpenAIChatLogprobs：content 数组 → token + topLogprobs（chosen 不在 top 时追加）。 */
    fun parseOpenAIChatLogprobs(logprobs: JsonObject?): List<TokenLogprobs>? {
        val content = logprobs?.get("content") as? JsonArray ?: return null
        return content.map { el ->
            val o = el.jsonObject
            val token = o["token"]?.jsonPrimitive?.contentOrNull ?: ""
            val logprob = o["logprob"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val top = (o["top_logprobs"] as? JsonArray) ?: JsonArray(emptyList())
            val topPairs = top.mapNotNull { t ->
                val to = t.jsonObject
                val tk = to["token"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val lp = to["logprob"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                tk to lp
            }
            val chosenInTop = topPairs.any { it.first == token }
            TokenLogprobs(token, if (chosenInTop) topPairs else topPairs + (token to logprob))
        }
    }

    /** 官方 parseOpenAITextLogprobs：tokens/token_logprobs/top_logprobs 数组 → TokenLogprobs 列表。 */
    fun parseOpenAITextLogprobs(logprobs: JsonObject?): List<TokenLogprobs>? {
        val tokens = logprobs?.get("tokens") as? JsonArray ?: return null
        val tokenLogprobs = (logprobs["token_logprobs"] as? JsonArray) ?: JsonArray(emptyList())
        val topLogprobs = (logprobs["top_logprobs"] as? JsonArray) ?: JsonArray(emptyList())
        return tokens.mapIndexed { i, tokenEl ->
            val token = tokenEl.jsonPrimitive.contentOrNull ?: ""
            val topObj = topLogprobs.getOrNull(i)?.jsonObject
            val topPairs = topObj?.let { o ->
                o.mapNotNull { (k, v) ->
                    val lp = v.jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                    k to lp
                }
            } ?: emptyList()
            val chosenInTop = topPairs.any { it.first == token }
            val logprob = tokenLogprobs.getOrNull(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            TokenLogprobs(token, if (chosenInTop) topPairs else topPairs + (token to logprob))
        }
    }

    /** 官方 parseChatCompletionLogprobs：按 source 分支选 chat/text 解析；未知 source → null。 */
    fun parseChatCompletionLogprobs(
        data: JsonObject?,
        source: String,
        textCompletionModel: Boolean,
    ): List<TokenLogprobs>? {
        if (data == null) return null
        val choice = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val choiceLogprobs = choice?.get("logprobs")?.jsonObject
        when (source) {
            SOURCE_AIMLAPI -> {
                val hasContent = choiceLogprobs?.containsKey("content") == true
                return if (hasContent) parseOpenAIChatLogprobs(choiceLogprobs)
                else parseOpenAITextLogprobs(choiceLogprobs)
            }
            SOURCE_OPENAI, SOURCE_AZURE_OPENAI, SOURCE_DEEPSEEK, SOURCE_XAI, SOURCE_CUSTOM, SOURCE_CHUTES -> {
                val choices = data["choices"] as? JsonArray ?: return null
                if (choices.isEmpty()) return null
                return if (textCompletionModel) parseOpenAITextLogprobs(choiceLogprobs)
                else parseOpenAIChatLogprobs(choiceLogprobs)
            }
            else -> return null
        }
    }
}
