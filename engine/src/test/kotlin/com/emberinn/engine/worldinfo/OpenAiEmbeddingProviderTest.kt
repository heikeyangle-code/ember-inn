package com.emberinn.engine.worldinfo

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiEmbeddingProviderTest {

    @Test
    fun `openai compatible embeddings posts and parses`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"data":[{"embedding":[0.1,0.2]},{"embedding":[0.3,0.4]}]}""")
                .build(),
        )
        val provider = OpenAiCompatibleEmbeddingProvider(
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "sk-embed",
            model = "text-embedding-3-small",
        )
        val vectors = provider.embed(listOf("甲", "乙"))
        val request = server.takeRequest()
        assertEquals("/embeddings", request.url.encodedPath)
        assertEquals("Bearer sk-embed", request.headers["Authorization"])
        assertEquals(2, vectors.size)
        assertEquals(listOf(0.1, 0.2), vectors[0])
        assertEquals(listOf(0.3, 0.4), vectors[1])
        server.close()
    }
}
