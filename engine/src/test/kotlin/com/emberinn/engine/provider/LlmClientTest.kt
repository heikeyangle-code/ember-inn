package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmClientTest {

    private val provider = ProviderRegistry.get("openai")!!

    @Test
    fun `chat completions sends auth and parses body`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"你好"}}]}""")
                .build(),
        )

        val client = LlmClient()
        val out = client.chatCompletions(
            provider,
            ConnectionProfile(
                providerId = "openai",
                apiKey = "sk-test",
                model = "gpt-4o",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.url.encodedPath)
        assertEquals("Bearer sk-test", request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("gpt-4o", body["model"]?.toString()?.trim('"'))
        assertTrue(out.contains("你好"))
        server.close()
    }

    @Test
    fun `stream parses sse deltas`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                )
                .build(),
        )

        val deltas = mutableListOf<String>()
        var done = false
        LlmClient().streamChatCompletions(
            provider,
            ConnectionProfile(
                providerId = "openai",
                apiKey = "sk",
                model = "gpt-4o",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
            onDelta = { deltas += it },
            onDone = { done = true },
        )
        assertEquals(listOf("你", "好"), deltas)
        assertTrue(done)
        server.close()
    }

    @Test
    fun `provider store round trips`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "provider-store-test-${System.nanoTime()}")
        val store = ProviderStore(dir)
        store.save(ConnectionProfile(providerId = "openai", apiKey = "k", model = "gpt-4o"))
        val loaded = store.load()
        assertEquals("openai", loaded?.providerId)
        assertEquals("k", loaded?.apiKey)
        dir.deleteRecursively()
    }

    @Test
    fun `models endpoint parses ids`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}""")
                .build(),
        )
        val models = LlmClient().models(
            provider,
            ConnectionProfile(
                providerId = "openai",
                apiKey = "sk",
                model = "",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
        )
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), models)
        server.close()
    }
    @Test
    fun `anthropic chat uses messages endpoint and parses content`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"content":[{"type":"text","text":"你好"}]}""")
                .build(),
        )
        val anthropic = ProviderRegistry.get("anthropic")!!
        val out = LlmClient().chatCompletions(
            anthropic,
            ConnectionProfile(
                providerId = "anthropic",
                apiKey = "sk-ant",
                model = "claude-sonnet-5",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/messages", request.url.encodedPath)
        assertEquals("sk-ant", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertTrue(request.body!!.utf8().contains("\"stream\":false"))
        assertEquals("你好", out)
        server.close()
    }

    @Test
    fun `google chat uses generateContent and parses candidates`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"candidates":[{"content":{"parts":[{"text":"你好"}]}}]}""")
                .build(),
        )
        val google = ProviderRegistry.get("google")!!
        val out = LlmClient().chatCompletions(
            google,
            ConnectionProfile(
                providerId = "google",
                apiKey = "gkey",
                model = "gemini-3.6-flash",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.6-flash:generateContent", request.url.encodedPath)
        assertEquals("key=gkey", request.url.query)
        assertEquals("你好", out)
        server.close()
    }

    @Test
    fun `google models endpoint strips models prefix and filters methods`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {"models":[
                      {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent","embedContent"]},
                      {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["embedContent"]}
                    ]}
                    """.trimIndent(),
                )
                .build(),
        )
        val models = LlmClient().models(
            ProviderRegistry.get("google")!!,
            ConnectionProfile(providerId = "google", apiKey = "gkey", model = "", baseUrlOverride = server.url("/").toString().trimEnd('/')),
        )
        assertEquals(listOf("gemini-3.6-flash"), models)
        server.close()
    }

    @Test
    fun `azure models endpoint uses api-key header and api-version`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"value":[{"id":"gpt-5.4"}]}""")
                .build(),
        )
        val models = LlmClient().models(
            ProviderRegistry.get("azure")!!,
            ConnectionProfile(
                providerId = "azure",
                apiKey = "azkey",
                model = "",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
        )
        val request = server.takeRequest()
        assertEquals("/openai/models", request.url.encodedPath)
        assertEquals("2024-12-01", request.url.queryParameter("api-version"))
        assertEquals("azkey", request.headers["api-key"])
        assertEquals(listOf("gpt-5.4"), models)
        server.close()
    }

    @Test
    fun `anthropic sse parses content block deltas`() {
        val chunks = SseParser.parse(
            "event: content_block_start\n" +
                "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"你\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"好\"}}\n\n" +
                "event: message_stop\n" +
                "data: {\"type\":\"message_stop\"}\n\n",
            "anthropic",
        )
        assertEquals(listOf("你", "好"), chunks.filter { it.content.isNotEmpty() }.map { it.content })
        assertTrue(chunks.any { it.done })
    }

    @Test
    fun `google sse parses candidate text`() {
        val chunks = SseParser.parse(
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"你\"}]}}]}\n\n" +
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"好\"}]}}]}\n\n",
            "google",
        )
        assertEquals(listOf("你", "好"), chunks.map { it.content })
    }

    @Test
    fun `provider registry loads latest fields and 20 plus providers`() {
        val all = ProviderRegistry.all()
        assertTrue(all.size >= 20)
        val openai = all.first { it.id == "openai" }
        assertTrue(openai.description.isNotBlank())
        assertTrue(openai.defaultModels.isNotEmpty())
        assertTrue(all.any { it.id == "zhipu" })
        assertTrue(all.any { it.id == "dashscope" })
        assertTrue(all.any { it.id == "volcengine" })
    }

    @Test
    fun `provider store keeps multiple profiles and switches active`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "provider-store-multi-${System.nanoTime()}")
        val store = ProviderStore(dir)
        store.save(ConnectionProfile(providerId = "openai", apiKey = "k1", model = "gpt-5.5"), active = true)
        val deepseek = ConnectionProfile(id = "ds-1", name = "DeepSeek 主用", providerId = "deepseek", apiKey = "k2", model = "deepseek-v4-flash")
        store.save(deepseek, active = false)
        assertEquals("openai", store.load()?.providerId)
        assertEquals(2, store.profiles().size)
        store.save(deepseek, active = true)
        assertEquals("deepseek", store.load()?.providerId)
        store.delete("ds-1")
        assertEquals("openai", store.load()?.providerId)
        dir.deleteRecursively()
    }
}
