package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：chat-completions.js 的 OpenAI 兼容 requestBody 构造（createGenerationParameters 输出 → API body）。
 * fixture 由 scripts/diff/chat-request-body-official.mjs 生成，禁止手改。
 * 消息转换（moonshot addAssistantPrefix / perplexity STRICT）属 LlmClient 层，由既有差分与单测覆盖；
 * 本差分输入统一取官方 expected.messages，锁 body 字段构造。
 */
class ChatRequestBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `openai compatible request body matches official backend fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/chat-request-body.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val s = args.getValue("settings").jsonObject
            val model = args["model"]?.jsonPrimitive?.content ?: ""
            val source = s["source"]?.jsonPrimitive?.content ?: "openai"
            val includeReasoning = s["showThoughts"]?.jsonPrimitive?.content == "true"
            val reasoningEffort = s["reasoningEffort"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty()
            val expected = case.getValue("expected")
            // 官方 createGenerationParameters：isO1（openai/azure_openai + o1-2024-12-17/o1）强制非流式
            val isO1 = source in setOf("openai", "azure_openai") && model in setOf("o1-2024-12-17", "o1")
            // 官方已应用消息转换（o1 system→user / moonshot partial / schema 注入），
            // 直接以官方输出消息作为输入，锁 body 字段构造本身。
            val messages = (expected.jsonObject["messages"] as? JsonArray) ?: JsonArray(emptyList())

            val params = SamplerParams(
                temperature = s["temp"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                topP = s["topP"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                maxTokens = s["maxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                presencePenalty = s["presPen"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                frequencyPenalty = s["freqPen"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                stream = s["stream"]?.jsonPrimitive?.content == "true" && !isO1,
                n = s["n"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                includeReasoning = includeReasoning,
                reasoningEffort = reasoningEffort,
                verbosity = s["verbosity"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                seed = s["seed"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                topK = s["topK"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                minP = s["minP"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                topA = s["topA"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                repetitionPenalty = s["repetitionPenalty"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                useFallback = s["useFallback"]?.jsonPrimitive?.content == "true",
                openRouterProviders = s["provider"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                openRouterQuantizations = s["quantizations"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                allowFallbacks = s["allowFallbacks"]?.jsonPrimitive?.content != "false",
                middleout = s["middleout"]?.jsonPrimitive?.content ?: "on",
                requestTokenProbabilities = s["requestTokenProbabilities"]?.jsonPrimitive?.content == "true",
            )
            val options = ProviderRequestOptions(
                enableWebSearch = s["enableWebSearch"]?.jsonPrimitive?.content == "true",
                stopSequences = s["stopStrings"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
            val extra = if (source == "openrouter") {
                OpenRouterParams.extra(params.middleout, options.enableWebSearch, includeReasoning, reasoningEffort)
            } else {
                null
            }
            val actual = json.parseToJsonElement(
                ChatRequestBuilder.buildOpenAiCompatibleFromChatML(
                    model = model,
                    messages = messages.map { it.jsonObject },
                    params = params,
                    options = options,
                    extra = extra,
                    source = source,
                ),
            )
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        is JsonPrimitive -> {
            if (!el.isString) {
                val d = el.content.toDoubleOrNull()
                if (d != null && d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    JsonPrimitive(d.toLong().toString())
                } else if (d != null) {
                    JsonPrimitive(d.toString().lowercase())
                } else {
                    el
                }
            } else {
                el
            }
        }
        else -> el
    }
}
