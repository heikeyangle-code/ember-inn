package com.emberinn.engine.card

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
 * 官方行为差分：src/byaf.js 纯逻辑（宏替换/示例/备选开场/角色书转换）。
 * fixture 由 scripts/diff/byaf-macros-official.mjs 生成，禁止手改。
 */
class ByafMacrosDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `byaf pure logic matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/byaf-macros.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val method = body.getValue("method").jsonPrimitive.content
            val args = body.getValue("args").jsonArray
            val expected = case.getValue("expected")

            val actual = when (method) {
                "replaceMacros" -> JsonPrimitive(ByafImporter.replaceMacros(args.getOrNull(0)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content))
                "formatExampleMessages" -> JsonPrimitive(ByafImporter.formatExampleMessages(args.getOrNull(0)))
                "formatAlternateGreetings" -> {
                    val el = args.getOrNull(0)
                    val scenarios = if (el == null || el is JsonNull) emptyList() else el.jsonArray.map { it.jsonObject }
                    JsonArray(ByafImporter.formatAlternateGreetings(scenarios).map { JsonPrimitive(it) })
                }
                "convertCharacterBook" -> ByafImporter.convertCharacterBook(args.getOrNull(0)) ?: JsonNull
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
