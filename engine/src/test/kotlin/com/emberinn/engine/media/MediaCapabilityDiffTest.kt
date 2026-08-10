package com.emberinn.engine.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 差分：openai.js isImageInliningSupported / isVideoInliningSupported / isAudioInliningSupported。
 * fixture 由 scripts/diff/media-capability-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class MediaCapabilityDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media capability matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/media-capability.json"))
        val fixture = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = fixture["cases"]!!.jsonArray
        var count = 0
        for (case in cases) {
            val id = case.jsonObject["id"]!!.jsonPrimitive.content
            val body = case.jsonObject["args"]!!.jsonObject["body"]!!.jsonObject
            val source = body["source"]!!.jsonPrimitive.content
            val model = body["model"]!!.jsonPrimitive.content
            val mods = (body["modalities"] as? JsonObject) ?: JsonObject(emptyMap())
            val expected = case.jsonObject["expected"]!!.jsonObject

            val actual = mapOf(
                "image" to MediaCapability.isImageInliningSupported(source, model, mods["vision"]?.jsonPrimitive?.content == "true"),
                "video" to MediaCapability.isVideoInliningSupported(source, model, mods["video"]?.jsonPrimitive?.content == "true"),
                "audio" to MediaCapability.isAudioInliningSupported(source, model, mods["audio"]?.jsonPrimitive?.content == "true"),
            )
            assertEquals("case $id", expected, JsonObject(actual.mapValues { (_, v) ->
                kotlinx.serialization.json.JsonPrimitive(v)
            }))
            count++
        }
        assertTrue("expected >= 24 cases, got $count", count >= 24)
    }
}
