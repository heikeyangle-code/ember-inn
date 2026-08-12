package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 发送路径请求体回归：stop/seed/n/top_k/min_p/top_a/repetition_penalty/logit_bias 按官方 createGenerationParameters 各厂商分支进 body。 */
class ChatRequestSendPathTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `openai compatible body carries send path fields`() {
        val raw = ChatRequestBuilder.buildOpenAiCompatible(
            model = "gpt-4o",
            messages = listOf(CompletionMessage(role = "user", content = "hi")),
            params = SamplerParams(
                stream = true,
                seed = 42,
                n = 2,
                topK = 50,
                logitBias = mapOf("123" to -1.0),
            ),
            options = ProviderRequestOptions(stopSequences = listOf("END", "STOP")),
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertEquals(listOf("END", "STOP"), body["stop"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(42, body["seed"]?.jsonPrimitive?.content?.toInt())
        assertEquals(2, body["n"]?.jsonPrimitive?.content?.toInt())
        // 官方 OpenAI 源不发 top_k（仅 openrouter/nanogpt/workers_ai/perplexity/electronhub/chutes 分支发）
        assertNull(body["top_k"])
        assertNotNull(body["logit_bias"])
        assertEquals(-1.0, body["logit_bias"]?.jsonObject?.get("123")?.jsonPrimitive?.content?.toDouble())
    }

    @Test
    fun `openrouter carries official sampling params`() {
        val raw = ChatRequestBuilder.buildOpenAiCompatible(
            model = "openai/gpt-4o",
            messages = listOf(CompletionMessage(role = "user", content = "hi")),
            params = SamplerParams(topK = 32, minP = 0.1, topA = 0.5, repetitionPenalty = 1.05),
            source = "openrouter",
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertEquals(32, body["top_k"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0.1, body["min_p"]?.jsonPrimitive?.content?.toDouble()!!, 1e-9)
        assertEquals(0.5, body["top_a"]?.jsonPrimitive?.content?.toDouble()!!, 1e-9)
        assertEquals(1.05, body["repetition_penalty"]?.jsonPrimitive?.content?.toDouble()!!, 1e-9)
    }

    @Test
    fun `workers ai clamps and prunes official fields`() {
        val raw = ChatRequestBuilder.buildOpenAiCompatible(
            model = "@cf/meta/llama-3.3-70b-instruct",
            messages = listOf(CompletionMessage(role = "user", content = "hi")),
            params = SamplerParams(topK = 100, repetitionPenalty = 1.1, seed = 0, n = 2),
            source = "workers_ai",
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertEquals(50, body["top_k"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1.1, body["repetition_penalty"]?.jsonPrimitive?.content?.toDouble()!!, 1e-9)
        assertNull(body["seed"])
        assertNull(body["n"])
    }

    @Test
    fun `zai and minimax and moonshot official branches`() {
        val zai = json.parseToJsonElement(
            ChatRequestBuilder.buildOpenAiCompatible(
                model = "glm-5",
                messages = listOf(CompletionMessage(role = "user", content = "hi")),
                params = SamplerParams(topP = 0.0, presencePenalty = 0.5, frequencyPenalty = 0.5),
                options = ProviderRequestOptions(stopSequences = listOf("A", "B", "C")),
                source = "zai",
            ),
        ).jsonObject
        assertEquals(0.01, zai["top_p"]?.jsonPrimitive?.content?.toDouble()!!, 1e-9)
        assertEquals(1, zai["stop"]?.jsonArray?.size)
        assertNull(zai["presence_penalty"])
        assertNull(zai["frequency_penalty"])

        val minimax = json.parseToJsonElement(
            ChatRequestBuilder.buildOpenAiCompatible(
                model = "MiniMax-M3",
                messages = listOf(CompletionMessage(role = "user", content = "hi")),
                params = SamplerParams(temperature = 0.0),
                source = "minimax",
            ),
        ).jsonObject
        assertEquals(Math.ulp(1.0), minimax["temperature"]?.jsonPrimitive?.content?.toDouble()!!, 1e-12)

        val moonshot = json.parseToJsonElement(
            ChatRequestBuilder.buildOpenAiCompatible(
                model = "kimi-k2.5",
                messages = listOf(CompletionMessage(role = "user", content = "hi")),
                params = SamplerParams(temperature = 0.8, topP = 0.9),
                source = "moonshot",
            ),
        ).jsonObject
        assertNull(moonshot["temperature"])
        assertNull(moonshot["top_p"])
    }
}
