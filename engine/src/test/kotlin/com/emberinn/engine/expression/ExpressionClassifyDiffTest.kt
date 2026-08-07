package com.emberinn.engine.expression

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
 * 官方行为差分：expressions sampleClassifyText + utils.js 句子裁剪。
 * fixture 由 scripts/diff/expression-classify-official.mjs 生成，禁止手改。
 */
class ExpressionClassifyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sample classify text matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/expression-classify.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val text = body.getValue("text").jsonPrimitive.content
            val api = body["settings"]?.jsonObject?.get("expressions")?.jsonObject?.get("api")?.jsonPrimitive?.content
            val actual = JsonPrimitive(ExpressionEngine.sampleClassifyText(text, useLlm = api == "llm") ?: "")
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
