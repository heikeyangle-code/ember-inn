package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：characters.js getCharaCardV2 + unsetPrivateFields（JSON 导出）。
 * fixture 由 scripts/diff/json-export-official.mjs 生成，禁止手改。
 */
class JsonExportDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `json export matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/json-export.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = json.parseToJsonElement(case.getValue("expected").jsonPrimitive.content)

            val actual = json.parseToJsonElement(
                CharacterCardExporter.exportToV2Json(
                    body.getValue("json").jsonPrimitive.content,
                    now = "2026-08-08T00:00:00.000Z",
                ),
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
