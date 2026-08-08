package com.emberinn.engine.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：openai.js Message 媒体内联内容部分。
 * fixture 由 scripts/diff/media-inline-official.mjs 生成，禁止手改。
 */
class MediaInlineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media inline matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/media-inline.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val media = mutableListOf<MediaAttachment>()
            body["image"]?.jsonPrimitive?.let { media += MediaAttachment("image", it.content) }
            body["video"]?.jsonPrimitive?.let { media += MediaAttachment("video", it.content) }
            body["audio"]?.jsonPrimitive?.let { media += MediaAttachment("audio", it.content) }

            val actual = MediaInliner.inlineOpenAi(
                content = body["content"],
                media = media,
                quality = body["quality"]?.jsonPrimitive?.content ?: "auto",
            )
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
