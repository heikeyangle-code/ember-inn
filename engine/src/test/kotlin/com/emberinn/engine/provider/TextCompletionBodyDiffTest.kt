package com.emberinn.engine.provider

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：OpenAI 文本补全请求体（isTextCompletion 分支）。
 * fixture 由 scripts/diff/text-completion-body-official.mjs 生成（逐字提取 + 真 convertTextCompletionPrompt），禁止手改。
 */
class TextCompletionBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `text completion body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/text-completion-body.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val messages = body["messages"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
            val prompt = ProviderConverters.convertTextCompletionPrompt(JsonArray(messages))
            val params = SamplerParams(
                temperature = body["temperature"]?.let { it.jsonPrimitive.content.toDouble() } ?: 1.0,
                topP = body["top_p"]?.let { it.jsonPrimitive.content.toDouble() } ?: 1.0,
                maxTokens = body["max_tokens"]?.let { it.jsonPrimitive.content.toInt() } ?: 512,
                frequencyPenalty = body["frequency_penalty"]?.let { it.jsonPrimitive.content.toDouble() } ?: 0.0,
                presencePenalty = body["presence_penalty"]?.let { it.jsonPrimitive.content.toDouble() } ?: 0.0,
                stream = body["stream"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            )
            val actual = json.parseToJsonElement(
                TextCompletionRequestBuilder.build(
                    model = body["model"]?.jsonPrimitive?.content ?: "",
                    prompt = prompt,
                    params = params,
                ),
            )
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        is JsonPrimitive -> if (el.isString) el else {
            el.content.toBigDecimalOrNull()?.let { n ->
                if (n.stripTrailingZeros().scale() <= 0) JsonPrimitive(n.toBigInteger().toString()) else JsonPrimitive(n.toPlainString())
            } ?: el
        }
        else -> el
    }
}
