package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：sse-stream.js parseStreamData。
 * fixture 由 scripts/diff/sse-stream-official.mjs 生成，禁止手改。
 */
class SseStreamDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sse chunk parser matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/sse-stream.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val actual = try {
                JsonArray(
                    SseChunkParser.parse(body.getValue("json").toString()).map { c ->
                        buildJsonObject {
                            put("data", c.data)
                            put("chunk", JsonPrimitive(c.chunk))
                            if (c.reasoningPresent) put("reasoning", JsonPrimitive(c.reasoning))
                        }
                    },
                )
            } catch (e: IllegalStateException) {
                buildJsonObject { put("error", JsonPrimitive(e.message ?: "")) }
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
