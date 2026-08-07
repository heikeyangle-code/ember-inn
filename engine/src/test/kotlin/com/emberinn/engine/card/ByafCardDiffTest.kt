package com.emberinn.engine.card

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
 * 官方行为差分：src/byaf.js getCharacterCard。
 * fixture 由 scripts/diff/byaf-card-official.mjs 生成（Date 打桩），禁止手改。
 */
class ByafCardDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `byaf card matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/byaf-card.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val manifest = body["manifest"] as? JsonObject ?: JsonObject(emptyMap())
            val author = manifest["author"] as? JsonObject ?: JsonObject(emptyMap())
            val character = body["character"] as? JsonObject ?: JsonObject(emptyMap())
            val scenarios = body["scenarios"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.jsonObject } ?: emptyList()

            val actual = json.parseToJsonElement(
                ByafImporter.buildCard(author, character, scenarios, now = "2026-08-08T00:00:00.000Z"),
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
