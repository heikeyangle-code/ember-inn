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
}
