package com.emberinn.engine.prompt

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
 * 官方行为差分：script.js collapseNewlines / parseMesExamples。
 * fixture 由 scripts/diff/prompt-utils-official.mjs 生成，禁止手改。
 */
class PromptUtilsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `prompt utils match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/prompt-utils.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val method = body.getValue("method").jsonPrimitive.content
            val actual = when (method) {
                "collapse" -> JsonPrimitive(PromptUtils.collapseNewlines(body.getValue("text").jsonPrimitive.content))
                "parseExamples" -> {
                    val sep = body["power_user"]?.jsonObject?.get("context")?.jsonObject
                        ?.get("example_separator")?.jsonPrimitive?.content ?: ""
                    val result = PromptUtils.parseMesExamples(
                        examplesStr = body.getValue("text").jsonPrimitive.content,
                        isInstruct = body["isInstruct"]?.jsonPrimitive?.content == "true",
                        exampleSeparator = sep,
                        mainApiIsOpenAi = (body["main_api"]?.jsonPrimitive?.content ?: "openai") == "openai",
                    )
                    JsonArray(result.map { JsonPrimitive(it) })
                }
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
