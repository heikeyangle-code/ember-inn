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
        assertEquals("https://api.deepseek.com/v1", ProviderRegistry.get("deepseek")!!.baseUrl)
        assertEquals("https://api.cohere.com/v2", ProviderRegistry.get("cohere")!!.baseUrl)
        assertTrue(ProviderRegistry.get("lmstudio")!!.requiresKey == false)
        assertEquals("https://api.together.xyz/v1", ProviderRegistry.get("together")!!.baseUrl)
        assertEquals("https://api.cerebras.ai/v1", ProviderRegistry.get("cerebras")!!.baseUrl)
        assertEquals("https://api.sambanova.ai/v1", ProviderRegistry.get("sambanova")!!.baseUrl)
        assertEquals("https://integrate.api.nvidia.com/v1", ProviderRegistry.get("nvidia")!!.baseUrl)
        assertEquals("https://models.github.ai/inference", ProviderRegistry.get("github-models")!!.baseUrl)
        assertEquals("https://router.huggingface.co/v1", ProviderRegistry.get("huggingface")!!.baseUrl)
        assertEquals("https://api.hunyuan.cloud.tencent.com/v1", ProviderRegistry.get("hunyuan")!!.baseUrl)
        assertEquals("https://api.stepfun.com/v1", ProviderRegistry.get("stepfun")!!.baseUrl)
        assertEquals("https://api.lingyiwanwu.com/v1", ProviderRegistry.get("lingyiwanwu")!!.baseUrl)
        assertEquals("https://qianfan.baidubce.com/v2", ProviderRegistry.get("qianfan")!!.baseUrl)
        assertEquals("https://spark-api-open.xf-yun.com/v1", ProviderRegistry.get("iflytek")!!.baseUrl)
        assertEquals("http://localhost:1234/v1", ProviderRegistry.get("lmstudio")!!.baseUrl)
        assertEquals(setOf("openai-compatible", "anthropic", "google", "vertexai", "mistral", "xai", "cohere", "ai21", "textgenerationwebui", "novel", "kobold"), providers.map { it.protocol }.toSet())
    }

    @Test
    fun `context defaults parse by provider and model`() {
        val openai = ProviderRegistry.get("openai")!!
        assertEquals(272000, openai.defaultContextWindow)
        assertEquals(272000, openai.modelContexts["gpt-5.5"])
        assertEquals(400000, openai.modelContexts["gpt-5.4"])
        val anthropic = ProviderRegistry.get("anthropic")!!
        assertEquals(1000000, anthropic.defaultContextWindow)
        assertEquals(200000, anthropic.modelContexts["claude-haiku-4-5"])
        val google = ProviderRegistry.get("google")!!
        assertEquals(1048576, google.defaultContextWindow)
        assertEquals(16384, openai.defaultMaxTokens)
        // 未知模型兜底厂商默认窗口，再兜底 8192
        val custom = ProviderRegistry.get("custom")!!
        assertEquals(null, custom.defaultContextWindow)
        assertTrue(custom.modelContexts.isEmpty())
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
