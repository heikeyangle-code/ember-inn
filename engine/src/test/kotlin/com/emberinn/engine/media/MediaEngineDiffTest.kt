package com.emberinn.engine.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：media 纯逻辑（类型/显示/索引）。
 * fixture 由 scripts/diff/media-engine-official.mjs 生成，禁止手改。
 */
class MediaEngineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media engine matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/media-engine.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val method = body.getValue("method").jsonPrimitive.content

            val actual = when (method) {
                "display" -> {
                    val mes = body["mes"]!!.jsonObject
                    val extra = mes["extra"]?.jsonObject
                    JsonPrimitive(
                        MediaEngine.getMediaDisplay(
                            extraMediaDisplay = extra?.get("media_display")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                            powerUserMediaDisplay = mes["power_user_media_display"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        ),
                    )
                }
                "index" -> {
                    val mes = body["mes"]!!.jsonObject
                    val extra = mes["extra"]?.jsonObject
                    val mediaCount = extra?.get("media")?.jsonArray?.size ?: 0
                    MediaEngine.getMediaIndex(
                        mediaCount = mediaCount,
                        mediaIndex = extra?.get("media_index"),
                    )
                }
                "mime" -> {
                    val mime = body["mime"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: ""
                    MediaEngine.typeFromMime(mime)?.let { JsonPrimitive(it) } ?: JsonNull
                }
                else -> error("unknown method $method")
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
