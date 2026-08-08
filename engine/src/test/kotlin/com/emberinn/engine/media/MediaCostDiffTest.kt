package com.emberinn.engine.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：媒体 token 成本估算（getImageTokenCost + addVideo/addAudio 的 263/32 规则）。
 * fixture 由 scripts/diff/media-cost-official.mjs 生成，禁止手改。
 */
class MediaCostDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media token cost matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/media-cost.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content.toInt()
            val method = body.getValue("method").jsonPrimitive.content

            val actual = when (method) {
                "image" -> MediaTokenCost.imageTokens(
                    width = body.getValue("width").jsonPrimitive.content.toInt(),
                    height = body.getValue("height").jsonPrimitive.content.toInt(),
                    quality = body.getValue("quality").jsonPrimitive.content,
                )
                "video" -> {
                    val duration = body["duration"]?.jsonPrimitive?.content
                    if (duration == "throw") MediaTokenCost.videoTokensFallback()
                    else MediaTokenCost.videoTokens(duration!!.toDouble())
                }
                "audio" -> {
                    val duration = body["duration"]?.jsonPrimitive?.content
                    if (duration == "throw") MediaTokenCost.audioTokensFallback()
                    else MediaTokenCost.audioTokens(duration!!.toDouble())
                }
                else -> error("unknown method $method")
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
