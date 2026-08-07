package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：utils.js splitRecursive / trimToEndSentence / trimToStartSentence。
 * fixture 由 scripts/diff/vector-utils-official.mjs 生成，禁止手改。
 */
class VectorUtilsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `vector text utils match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/vector-utils.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fn = case.getValue("fn").jsonPrimitive.content
            val args = case.getValue("args").jsonArray.map { it.jsonPrimitive.content }
            val expectedEl = case.getValue("expected")
            val expected = if (expectedEl is JsonArray) {
                expectedEl.map { it.jsonPrimitive.content }.joinToString("\u0000")
            } else {
                expectedEl.jsonPrimitive.content
            }

            val actual = when (fn) {
                "splitRecursive" -> VectorTextUtils.splitRecursive(
                    args[0],
                    args.getOrNull(1)?.toIntOrNull() ?: 3,
                ).joinToString("\u0000")
                "trimToEndSentence" -> VectorTextUtils.trimToEndSentence(args[0])
                "trimToStartSentence" -> VectorTextUtils.trimToStartSentence(args[0])
                else -> error("unknown fn: $fn")
            }
            assertEquals("case $id ($fn)", expected, actual)
        }
    }
}
