package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 发送路径请求体回归：stop/seed/n/top_k/logit_bias 必须按官方后端字段进 OpenAI 兼容 body。 */
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
        assertEquals(50, body["top_k"]?.jsonPrimitive?.content?.toInt())
        assertNotNull(body["logit_bias"])
        assertEquals(-1.0, body["logit_bias"]?.jsonObject?.get("123")?.jsonPrimitive?.content?.toDouble())
    }
}
