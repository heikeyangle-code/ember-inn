package com.emberinn.engine.slash

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
 * 官方行为差分：SlashCommandParser testSymbol（转义判定）。
 * fixture 由 scripts/diff/slash-escape-official.mjs 生成，禁止手改。
 */
class SlashEscapeDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `slash escape matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/slash-escape.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val result = SlashEscape.testSymbol(
                text = body.getValue("text").jsonPrimitive.content,
                index = body["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                sequence = body.getValue("sequence").jsonPrimitive.content,
                strict = body["strict"]?.jsonPrimitive?.content == "true",
                offset = body["offset"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                jumpedEscapeSequence = body["jumped"]?.jsonPrimitive?.content == "true",
            )
            val actual = buildJsonObject {
                put("found", JsonPrimitive(result.found))
                put("index", JsonPrimitive(result.index))
                put("jumped", JsonPrimitive(result.jumpedEscapeSequence))
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
