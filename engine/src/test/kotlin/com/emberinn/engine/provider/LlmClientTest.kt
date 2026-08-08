package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.ToolCall
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
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

    @Test
    fun `anthropic derives numeric thinking budget from effort`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"content":[{"type":"text","text":"ok"}]}""")
                .build(),
        )
        val anthropic = ProviderRegistry.get("anthropic")!!
        LlmClient().chatCompletions(
            anthropic,
            ConnectionProfile(
                providerId = "anthropic",
                apiKey = "sk-ant",
                model = "claude-haiku-4-5",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(maxTokens = 512, reasoningEffort = "low"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        // official calculateClaudeBudgetTokens: max(floor(512*0.1),1024)=1024 → min(1024,21333)
        assertEquals(1024, body["thinking"]?.jsonObject?.get("budget_tokens")?.toString()?.toInt())
        // max_tokens <= 1024 时官方 +1024
        assertEquals(1536, body["max_tokens"]?.toString()?.toInt())
        server.close()
    }

    @Test
    fun `anthropic adaptive effort passes string to output_config`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"content":[{"type":"text","text":"ok"}]}""")
                .build(),
        )
        val anthropic = ProviderRegistry.get("anthropic")!!
        LlmClient().chatCompletions(
            anthropic,
            ConnectionProfile(
                providerId = "anthropic",
                apiKey = "sk-ant",
                model = "claude-opus-4-7",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(reasoningEffort = "high"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("adaptive", body["thinking"]?.jsonObject?.get("type")?.toString()?.trim('"'))
        assertEquals("high", body["output_config"]?.jsonObject?.get("effort")?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `anthropic adaptive auto omits thinking`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"content":[{"type":"text","text":"ok"}]}""")
                .build(),
        )
        val anthropic = ProviderRegistry.get("anthropic")!!
        LlmClient().chatCompletions(
            anthropic,
            ConnectionProfile(
                providerId = "anthropic",
                apiKey = "sk-ant",
                model = "claude-opus-4-7",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(reasoningEffort = "auto"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(null, body["thinking"])
        server.close()
    }

    @Test
    fun `google derives thinking level for gemini 3`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
                .build(),
        )
        val google = ProviderRegistry.get("google")!!
        LlmClient().chatCompletions(
            google,
            ConnectionProfile(
                providerId = "google",
                apiKey = "gkey",
                model = "gemini-3.6-flash",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(reasoningEffort = "low"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val thinking = body["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals("low", thinking?.get("thinkingLevel")?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `google auto effort leaves thinking budget unset`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
                .build(),
        )
        val google = ProviderRegistry.get("google")!!
        LlmClient().chatCompletions(
            google,
            ConnectionProfile(
                providerId = "google",
                apiKey = "gkey",
                model = "gemini-3.6-flash",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(reasoningEffort = "auto"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val thinking = body["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals(null, thinking?.get("thinkingLevel"))
        assertEquals(null, thinking?.get("thinkingBudget"))
        server.close()
    }

    @Test
    fun `mistral chat uses converted messages and openai-style response`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"你好"}}]}""")
                .build(),
        )
        val mistral = ProviderRegistry.get("mistral")!!
        val out = LlmClient().chatCompletions(
            mistral,
            ConnectionProfile(
                providerId = "mistral",
                apiKey = "sk-m",
                model = "mistral-large-latest",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.url.encodedPath)
        assertEquals("Bearer sk-m", request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("hi", body["messages"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.toString()?.trim('"'))
        assertEquals("你好", out)
        server.close()
    }

    @Test
    fun `xai chat maps reasoning effort to high or low`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .build(),
        )
        val xai = ProviderRegistry.get("xai")!!
        LlmClient().chatCompletions(
            xai,
            ConnectionProfile(
                providerId = "xai",
                apiKey = "sk-x",
                model = "grok-4.3",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(reasoningEffort = "medium"),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("low", body["reasoning_effort"]?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `ai21 chat uses studio chat completions endpoint`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .build(),
        )
        val ai21 = ProviderRegistry.get("ai21")!!
        LlmClient().chatCompletions(
            ai21,
            ConnectionProfile(
                providerId = "ai21",
                apiKey = "sk-a",
                model = "jamba-large",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("jamba-large", body["model"]?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `cohere chat uses v2 chat endpoint and parses message blocks`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"message":{"role":"assistant","content":[{"type":"text","text":"你好"}]}}""")
                .build(),
        )
        val cohere = ProviderRegistry.get("cohere")!!
        val out = LlmClient().chatCompletions(
            cohere,
            ConnectionProfile(
                providerId = "cohere",
                apiKey = "sk-c",
                model = "command-r-plus",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("/chat", request.url.encodedPath)
        assertEquals("Bearer sk-c", request.headers["Authorization"])
        assertEquals("你好", out)
        server.close()
    }

    @Test
    fun `openrouter sends referer headers and reasoning exclude`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .build(),
        )
        val openrouter = ProviderRegistry.get("openrouter")!!
        LlmClient().chatCompletions(
            openrouter,
            ConnectionProfile(
                providerId = "openrouter",
                apiKey = "sk-or",
                model = "anthropic/claude-sonnet-5",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            ),
            listOf(CompletionMessage("user", "hi")),
        )
        val request = server.takeRequest()
        assertEquals("https://github.com/heikeyangle-code/ember-inn", request.headers["HTTP-Referer"])
        assertEquals("EmberInn", request.headers["X-Title"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(true, body["reasoning"]?.jsonObject?.get("exclude")?.toString()?.toBoolean())
        assertEquals(0, body["transforms"]?.jsonArray?.size)
        server.close()
    }

    @Test
    fun `cohere sse parses content delta chunks`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"type\":\"content-delta\",\"delta\":{\"message\":{\"content\":{\"text\":\"你\"}}}}\n\n" +
                        "data: {\"type\":\"content-delta\",\"delta\":{\"message\":{\"content\":{\"text\":\"好\"}}}}\n\n" +
                        "data: {\"type\":\"message-end\"}\n\n",
                )
                .build(),
        )
        val cohere = ProviderRegistry.get("cohere")!!
        val deltas = mutableListOf<String>()
        var done = false
        LlmClient().streamChatCompletions(
            cohere,
            ConnectionProfile(
                providerId = "cohere",
                apiKey = "sk-c",
                model = "command-r-plus",
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
    fun `openrouter claude caching adds cache control`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .build(),
        )
        val openrouter = ProviderRegistry.get("openrouter")!!
        LlmClient().chatCompletions(
            openrouter,
            ConnectionProfile(
                providerId = "openrouter",
                apiKey = "sk-or",
                model = "anthropic/claude-sonnet-5",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(enableSystemPromptCache = true, cachingAtDepth = 2),
            ),
            listOf(
                CompletionMessage("system", "你是助手"),
                CompletionMessage("user", "hi"),
                CompletionMessage("assistant", "hello"),
                CompletionMessage("user", "again"),
            ),
        )
        val raw = server.takeRequest().body!!.utf8()
        assertTrue(raw.contains("cache_control"))
        assertTrue(raw.contains("5m"))
        server.close()
    }

    @Test
    fun `deepseek applies semi tools processing and reasoning effort`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .build(),
        )
        val deepseek = ProviderRegistry.get("deepseek")!!
        LlmClient().chatCompletions(
            deepseek,
            ConnectionProfile(
                providerId = "deepseek",
                apiKey = "sk-d",
                model = "deepseek-v4-flash",
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
                sampler = SamplerParams(includeReasoning = true, reasoningEffort = "high"),
            ),
            listOf(
                CompletionMessage("assistant", "hello", toolCalls = listOf(ToolCall("call_1", "f", "{}"))),
                CompletionMessage("tool", "result", toolCallId = "call_1"),
                CompletionMessage("user", "next"),
            ),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("high", body["reasoning_effort"]?.toString()?.trim('"'))
        assertTrue(body["messages"]!!.toString().contains("reasoning_content"))
        server.close()
    }

    @Test
    fun `openai body includes tools tool choice and response format`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""").build())
        val schema = Json.parseToJsonElement("""{"name":"my_schema","value":{"type":"object"}}""").jsonObject
        LlmClient().chatCompletions(
            provider,
            ConnectionProfile(providerId = "openai", apiKey = "sk", model = "gpt-4o", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(
                tools = listOf(ToolDefinition("getWeather", "weather", Json.parseToJsonElement("""{"type":"object","properties":{"city":{"type":"string"}}}""").jsonObject)),
                toolChoice = "auto",
                jsonSchema = schema,
            ),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("getWeather", body["tools"]?.jsonArray?.get(0)?.jsonObject?.get("function")?.jsonObject?.get("name")?.toString()?.trim('"'))
        assertEquals("auto", body["tool_choice"]?.toString()?.trim('"'))
        assertEquals("my_schema", body["response_format"]?.jsonObject?.get("json_schema")?.jsonObject?.get("name")?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `anthropic passes tools and web search`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"content":[{"type":"text","text":"ok"}]}""").build())
        val anthropic = ProviderRegistry.get("anthropic")!!
        LlmClient().chatCompletions(
            anthropic,
            ConnectionProfile(providerId = "anthropic", apiKey = "sk-ant", model = "claude-opus-4-7", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(
                tools = listOf(ToolDefinition("getWeather", "weather", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)),
                enableWebSearch = true,
            ),
        )
        val raw = server.takeRequest().body!!.utf8()
        assertTrue(raw.contains("input_schema"))
        assertTrue(raw.contains("web_search_20250305"))
        server.close()
    }

    @Test
    fun `gemini passes tools safety and image modality`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""").build())
        val google = ProviderRegistry.get("google")!!
        LlmClient().chatCompletions(
            google,
            ConnectionProfile(providerId = "google", apiKey = "gkey", model = "gemini-2.5-flash-image-preview", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(
                tools = listOf(ToolDefinition("getWeather", "weather", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)),
                safetySettings = JsonArray(listOf(Json.parseToJsonElement("""{"category":"HARM_CATEGORY_HARASSMENT","threshold":"BLOCK_NONE"}""").jsonObject)),
                requestImages = true,
                aspectRatio = "16:9",
            ),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("getWeather", body["tools"]?.jsonArray?.get(0)?.jsonObject?.get("function_declarations")?.jsonArray?.get(0)?.jsonObject?.get("name")?.toString()?.trim('"'))
        assertEquals("HARM_CATEGORY_HARASSMENT", body["safetySettings"]?.jsonArray?.get(0)?.jsonObject?.get("category")?.toString()?.trim('"'))
        assertEquals("16:9", body["generationConfig"]?.jsonObject?.get("imageConfig")?.jsonObject?.get("aspectRatio")?.toString()?.trim('"'))
        assertTrue(body["generationConfig"]?.jsonObject?.get("responseModalities")!!.toString().contains("image"))
        server.close()
    }

    @Test
    fun `mistral body includes tools and json schema`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""").build())
        val mistral = ProviderRegistry.get("mistral")!!
        LlmClient().chatCompletions(
            mistral,
            ConnectionProfile(providerId = "mistral", apiKey = "sk-m", model = "mistral-large-latest", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(
                tools = listOf(ToolDefinition("getWeather", "weather", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)),
                jsonSchema = Json.parseToJsonElement("""{"name":"my_schema","value":{"type":"object"}}""").jsonObject,
            ),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertTrue(body["tools"]!!.toString().contains("getWeather"))
        assertEquals("my_schema", body["response_format"]?.jsonObject?.get("json_schema")?.jsonObject?.get("name")?.toString()?.trim('"'))
        server.close()
    }

    @Test
    fun `ai21 json schema appends schema message`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""").build())
        val ai21 = ProviderRegistry.get("ai21")!!
        LlmClient().chatCompletions(
            ai21,
            ConnectionProfile(providerId = "ai21", apiKey = "sk-a", model = "jamba-large", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(jsonSchema = Json.parseToJsonElement("""{"name":"s","value":{"type":"object"}}""").jsonObject),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("json_object", body["response_format"]?.jsonObject?.get("type")?.toString()?.trim('"'))
        assertTrue(body["messages"]!!.toString().contains("JSON schema for the response"))
        server.close()
    }

    @Test
    fun `cohere removes dollar schema from tool parameters`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("""{"message":{"role":"assistant","content":[{"type":"text","text":"ok"}]}}""").build())
        val cohere = ProviderRegistry.get("cohere")!!
        val params = Json.parseToJsonElement("""{"type":"object","properties":{},"required":[],"\u0024schema":"https://json-schema.org/draft/2020-12/schema"}""").jsonObject
        LlmClient().chatCompletions(
            cohere,
            ConnectionProfile(providerId = "cohere", apiKey = "sk-c", model = "command-r-plus", baseUrlOverride = server.url("/").toString().trimEnd('/')),
            listOf(CompletionMessage("user", "hi")),
            options = ProviderRequestOptions(tools = listOf(ToolDefinition("getWeather", "weather", params))),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val toolParams = body["tools"]?.jsonArray?.get(0)?.jsonObject?.get("function")?.jsonObject?.get("parameters")?.jsonObject
        assertEquals(null, toolParams?.get("\$schema"))
        assertEquals(0, toolParams?.get("required")?.jsonArray?.size)
        server.close()
    }
}
