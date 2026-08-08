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
 * 官方行为差分：特殊协议请求体（Mistral/xAI/AI21/Cohere）。
 * fixture 由 scripts/diff/special-bodies-official.mjs 生成（逐字提取官方 requestBody 构造段 + 真 convert*Messages），禁止手改。
 */
class SpecialBodiesDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `special provider bodies match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/special-bodies.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val provider = case.getValue("args").jsonObject.getValue("provider").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val actual = build(provider, body)
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun build(provider: String, body: JsonObject): JsonElement {
        val model = body["model"]?.jsonPrimitive?.content ?: ""
        val messages = body["messages"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val params = SamplerParams(
            temperature = body["temperature"]?.let { it.jsonPrimitive.content.toDouble() } ?: 1.0,
            topP = body["top_p"]?.let { it.jsonPrimitive.content.toDouble() } ?: 1.0,
            maxTokens = body["max_tokens"]?.let { it.jsonPrimitive.content.toInt() } ?: 512,
            frequencyPenalty = body["frequency_penalty"]?.let { it.jsonPrimitive.content.toDouble() } ?: 0.0,
            presencePenalty = body["presence_penalty"]?.let { it.jsonPrimitive.content.toDouble() } ?: 0.0,
            stream = body["stream"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            reasoningEffort = body["reasoning_effort"]?.jsonPrimitive?.content ?: "",
        )
        val tools = body["tools"]?.jsonArray?.mapNotNull { t ->
            val fn = t.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            ToolDefinition(
                name = fn["name"]?.jsonPrimitive?.content ?: "",
                description = fn["description"]?.jsonPrimitive?.content ?: "",
                parameters = fn["parameters"]?.jsonObject ?: JsonObject(emptyMap()),
            )
        } ?: emptyList()
        val options = ProviderRequestOptions(
            tools = tools,
            toolChoice = body["tool_choice"]?.jsonPrimitive?.content,
            jsonSchema = body["json_schema"]?.jsonObject,
        )
        val names = body["names"]?.jsonObject?.let { n ->
            PromptNames(
                userName = n["userName"]?.jsonPrimitive?.content ?: "",
                charName = n["charName"]?.jsonPrimitive?.content ?: "",
                groupNames = n["groupNames"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
            )
        } ?: PromptNames()

        val text = when (provider) {
            "mistral" -> MistralRequestBuilder.build(model, messages, params, options, names)
            "xai" -> XaiRequestBuilder.build(model, messages, params, options, names)
            "ai21" -> Ai21RequestBuilder.build(model, messages, params, options, names)
            "cohere" -> CohereRequestBuilder.build(model, messages, params, options, names)
            else -> error("unknown provider $provider")
        }
        return json.parseToJsonElement(text)
    }

    /** 排序键 + 数字归一（1 与 1.0 等价），与其它差分测试一致。 */
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
