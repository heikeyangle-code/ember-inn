package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：reasoning.js PromptReasoning.addToMessage。
 * fixture 由 scripts/diff/prompt-reasoning-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class PromptReasoningDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `addToMessage matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/prompt-reasoning.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonObject

            val engine = PromptReasoningEngine()
            val settingsBody = body["settings"]?.jsonObject ?: error("case $id: settings missing")
            val settings = ReasoningPromptSettings(
                addToPrompts = bool(settingsBody, "add_to_prompts"),
                maxAdditions = int(settingsBody, "max_additions"),
                prefix = str(settingsBody, "prefix"),
                suffix = str(settingsBody, "suffix"),
                separator = str(settingsBody, "separator"),
            )
            val first = engine.addToMessage(
                content = str(body, "content"),
                reasoning = body["reasoning"]?.jsonPrimitive?.contentOrNull,
                isPrefix = bool(body, "isPrefix"),
                duration = body["duration"]?.jsonPrimitive?.longOrNull,
                settings = settings,
            )
            val second = if (body["secondContent"] == null) {
                null
            } else {
                engine.addToMessage(
                    content = str(body, "secondContent"),
                    reasoning = body["secondReasoning"]?.jsonPrimitive?.contentOrNull,
                    isPrefix = bool(body, "secondIsPrefix"),
                    duration = body["secondDuration"]?.jsonPrimitive?.longOrNull,
                    settings = settings,
                )
            }

            val actual = buildJsonObject {
                put("first", JsonPrimitive(first))
                put("second", if (second == null) JsonNull else JsonPrimitive(second))
                put("counter", JsonPrimitive(engine.counter))
                put("prefixLength", JsonPrimitive(engine.prefixLength))
                put("prefixIncomplete", JsonPrimitive(engine.prefixIncomplete))
            }
            assertEquals("case $id", expected, actual)
        }
    }

    private fun str(o: JsonObject, key: String): String =
        o[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun bool(o: JsonObject, key: String): Boolean =
        o[key]?.jsonPrimitive?.booleanOrNull ?: false

    private fun int(o: JsonObject, key: String): Int =
        o[key]?.jsonPrimitive?.intOrNull ?: 0
}
