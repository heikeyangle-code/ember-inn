package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
 * 官方行为差分：populateChatCompletion 工具 token 预分配。
 * fixture 由 scripts/diff/tool-budget-official.mjs 生成，禁止手改。
 */
class ToolBudgetDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool budget matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/tool-budget.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val result = ToolBudgetEngine.preallocate(
                canPerform = body["canPerform"]?.jsonPrimitive?.content == "true",
                toolDataJson = body["toolData"]?.toString() ?: "{}",
                tokenCount = body["tokenCount"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
            )
            val actual = buildJsonObject {
                put("reserve", JsonPrimitive(result.reserve))
                put("toolMessage", result.toolMessage?.let { JsonArray(it) } ?: JsonNull)
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
