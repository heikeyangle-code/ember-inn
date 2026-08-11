package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：reasoning.js parseReasoningFromString / removeReasoningFromString / formatReasoning。
 * fixture 由 scripts/diff/reasoning-official.mjs 生成，禁止手改。
 */
class ReasoningDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reasoning matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/reasoning.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val settings = ReasoningSettings(
                autoParse = body.bool("autoParse"),
                trimSpaces = body.bool("trimSpaces"),
                template = ReasoningTemplate(
                    prefix = body.str("prefix", "<think>"),
                    suffix = body.str("suffix", "</think>"),
                    separator = body.str("separator", "\n"),
                ),
            )

            when (body.getValue("method").jsonPrimitive.content) {
                "remove" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content,
                    ReasoningEngine.removeReasoningFromString(body.str("text"), settings),
                )
                "parse" -> {
                    val actual = ReasoningEngine.parseReasoningFromString(
                        str = body.str("text"),
                        strict = body.bool("strict", true),
                        settings = settings,
                    )
                    if (expected is JsonNull) {
                        assertEquals("case $id", null, actual)
                    } else {
                        val e = expected.jsonObject
                        assertEquals("case $id reasoning", e["reasoning"]?.strOrNull(), actual?.reasoning)
                        assertEquals("case $id content", e["content"]?.strOrNull(), actual?.content)
                    }
                }
                "format" -> {
                    val actual = ReasoningEngine.formatReasoning(
                        reasoning = body.str("reasoning"),
                        content = body.str("content"),
                        settings = settings,
                    )
                    val e = expected.jsonObject
                    assertEquals("case $id formatted", e["formatted"]?.strOrNull(), actual.formatted)
                    assertEquals("case $id contentOnly", e["contentOnly"]?.strOrNull(), actual.contentOnly)
                }
            }
        }
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

    private fun kotlinx.serialization.json.JsonElement.strOrNull(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }
}
