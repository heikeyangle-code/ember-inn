package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js getMaxContextTokens/getMaxResponseTokens/getMaxPromptTokens。
 * fixture 由 scripts/diff/token-budget-official.mjs 生成，禁止手改。
 */
class TokenBudgetDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `token budget matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/token-budget.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content.toInt()
            val config = TokenBudgetConfig(
                mainApi = body["mainApi"]?.jsonPrimitive?.content ?: "openai",
                maxContext = body["maxContext"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                openaiMaxContext = body["openaiMaxContext"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                openaiMaxTokens = body["openaiMaxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                amountGen = body["amountGen"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                novelModel = body["novelModel"]?.jsonPrimitive?.content ?: "",
                novelTier = when (val t = body["novelTier"]) {
                    is JsonNull -> null
                    is JsonPrimitive -> t.content.toIntOrNull()
                    else -> null
                },
            )

            val actual = when (body.getValue("method").jsonPrimitive.content) {
                "context" -> TokenBudgetEngine.getMaxContextTokens(config)
                "response" -> TokenBudgetEngine.getMaxResponseTokens(config)
                "prompt" -> TokenBudgetEngine.getMaxPromptTokens(
                    config,
                    body["override"]?.jsonPrimitive?.content?.toIntOrNull(),
                )
                else -> error("unknown method in case $id")
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
