package com.emberinn.engine.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：prompt-converters.js Claude/Gemini 媒体块转换。
 * fixture 由 scripts/diff/media-convert-official.mjs 生成，禁止手改。
 */
class MediaConvertDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media convert matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/media-convert.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val target = body.getValue("target").jsonPrimitive.content
            val part = body.getValue("part").jsonObject

            val actual = when (target) {
                "claude" -> MediaConverter.convertClaudePart(part, body["name"]?.jsonPrimitive?.content)
                "gemini" -> MediaConverter.convertGeminiPart(part, body["model"]?.jsonPrimitive?.content ?: "")
                else -> error("unknown target")
            } ?: JsonNull

            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
