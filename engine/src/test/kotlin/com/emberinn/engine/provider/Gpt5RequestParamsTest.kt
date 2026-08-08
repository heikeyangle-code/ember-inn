package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 官方 openai.js gpt-5 分支：max_tokens → max_completion_tokens + 删不支持参数（仅 openai/azure_openai/openrouter）。 */
class Gpt5RequestParamsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val msgs = listOf(CompletionMessage(role = "user", content = "hi"))

    private fun body(model: String, source: String = "openai", params: SamplerParams = SamplerParams(), tools: Boolean = false): Map<String, Any?> {
        val raw = ChatRequestBuilder.buildOpenAiCompatible(
            model = model,
            messages = msgs,
            params = params,
            source = source,
            options = if (tools) ProviderRequestOptions(
                tools = listOf(ToolDefinition("ping", "p", kotlinx.serialization.json.JsonObject(emptyMap()))),
            ) else ProviderRequestOptions(),
        )
        val obj = json.parseToJsonElement(raw).jsonObject
        return obj.mapValues { (_, v) ->
            val prim = v as? kotlinx.serialization.json.JsonPrimitive
            prim?.content?.toIntOrNull() ?: prim?.content ?: v.toString()
        }
    }

    @Test
    fun `gpt-5 uses max_completion_tokens and drops unsupported sampling params`() {
        val b = body("gpt-5.5", params = SamplerParams(maxTokens = 16384))
        assertEquals(16384, b["max_completion_tokens"])
        assertFalse("max_tokens 必须删除", b.containsKey("max_tokens"))
        assertFalse(b.containsKey("temperature"))
        assertFalse(b.containsKey("top_p"))
        assertFalse(b.containsKey("frequency_penalty"))
        assertFalse(b.containsKey("presence_penalty"))
    }

    @Test
    fun `gpt-5-chat-latest drops tools and tool_choice`() {
        val b = body("gpt-5-chat-latest", tools = true)
        assertTrue(b.containsKey("max_completion_tokens"))
        assertFalse("tools 必须删除", b.containsKey("tools"))
        assertFalse(b.containsKey("tool_choice"))
        assertTrue("官方 chat-latest 保留 temperature", b.containsKey("temperature"))
    }

    @Test
    fun `gpt-5-4 without reasoning effort keeps temperature but drops penalties`() {
        val b = body("gpt-5.4", params = SamplerParams(reasoningEffort = ""))
        assertTrue(b.containsKey("max_completion_tokens"))
        assertTrue(b.containsKey("temperature"))
        assertTrue(b.containsKey("top_p"))
        assertFalse(b.containsKey("frequency_penalty"))
        assertFalse(b.containsKey("presence_penalty"))
    }

    @Test
    fun `gpt-5 with reasoning effort drops sampling params`() {
        val b = body("gpt-5.4", params = SamplerParams(reasoningEffort = "high"))
        assertFalse(b.containsKey("temperature"))
        assertFalse(b.containsKey("top_p"))
        assertFalse(b.containsKey("frequency_penalty"))
        assertFalse(b.containsKey("presence_penalty"))
    }

    @Test
    fun `non gpt-sources keep max_tokens unchanged`() {
        val b = body("gpt-5.5", source = "other", params = SamplerParams(maxTokens = 777))
        assertEquals(777, b["max_tokens"])
        assertFalse("custom 不转换", b.containsKey("max_completion_tokens"))
        assertTrue(b.containsKey("temperature"))
    }

    @Test
    fun `non gpt-5 models are untouched`() {
        val b = body("gpt-4o", params = SamplerParams(maxTokens = 888))
        assertEquals(888, b["max_tokens"])
        assertFalse(b.containsKey("max_completion_tokens"))
    }
}
