package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：characters.js readFromV2（V3→V2 归一）。
 * fixture 由 scripts/diff/char-v2-official.mjs 生成，禁止手改。
 */
class CharV2DiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `v2 normalization matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/char-v2.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val char = case.getValue("args").jsonObject.getValue("char")
            val expected = case.getValue("expected")

            val actual = json.parseToJsonElement(
                V2Normalizer.normalize(char.toString(), now = "2026-08-08@00h00m00s000ms"),
            )
            assertEquals("case $id", expected, actual)
        }
    }
}
