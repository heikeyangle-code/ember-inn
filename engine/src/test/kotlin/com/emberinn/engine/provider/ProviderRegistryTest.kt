package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun `registry loads data driven providers`() {
        val providers = ProviderRegistry.all()
        assertTrue(providers.size >= 18)
        val openai = ProviderRegistry.get("openai")!!
        assertEquals("https://api.openai.com/v1", openai.baseUrl)
        assertEquals("openai-compatible", openai.protocol)
        assertTrue(ProviderRegistry.get("ollama")!!.requiresKey == false)
        assertEquals(setOf("openai-compatible", "anthropic", "google"), providers.map { it.protocol }.toSet())
    }

    @Test
    fun `chat request body builds messages and tools`() {
        val body = ChatRequestBuilder.buildOpenAiCompatible(
            model = "gpt-4o",
            messages = listOf(
                CompletionMessage("system", "你是助手"),
                CompletionMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = listOf(ToolCall("call_1", "getWeather", "{\"city\":\"北京\"}")),
                ),
                CompletionMessage("tool", "晴", toolCallId = "call_1"),
            ),
        )
        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("gpt-4o", root["model"]?.toString()?.trim('"'))
        val messages = root["messages"]!!.toString()
        assertTrue(messages.contains("\"tool_calls\""))
        assertTrue(messages.contains("\"tool_call_id\":\"call_1\""))
    }
}
