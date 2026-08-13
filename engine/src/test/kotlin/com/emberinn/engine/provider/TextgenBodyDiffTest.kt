package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 Text Completion 请求体差分：createTextGenGenerationData / getTextGenModel / getTextGenServer。
 * fixture 由 scripts/diff/textgen-body-official.mjs（官方函数逐字）生成，禁止手改。
 */
class TextgenBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `textgen request body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/textgen-body.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val settings = case.getValue("settings").jsonObject
            val extra = case.getValue("extra").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content

            val input = TextgenRequestBodyEngine.BuildInput(
                settings = settings,
                finalPrompt = extra["finalPrompt"]?.jsonPrimitive?.content ?: "hello",
                maxTokens = extra["maxTokens"]?.jsonPrimitive?.intOrNull,
                isImpersonate = extra["isImpersonate"]?.jsonPrimitive?.booleanOrNull ?: false,
                isContinue = extra["isContinue"]?.jsonPrimitive?.booleanOrNull ?: false,
                cfgValues = extra["cfgValues"]?.jsonObject,
                type = extra["type"]?.jsonPrimitive?.content ?: "quiet",
                stoppingStrings = extra["stoppingStrings"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                maxContext = extra["maxContext"]?.jsonPrimitive?.intOrNull ?: 4096,
                requestTokenProbabilities = extra["requestTokenProbabilities"]?.jsonPrimitive?.booleanOrNull ?: false,
                bannedInMacros = extra["bannedInMacros"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                dynatempTypes = extra["dynatempTypes"]?.jsonPrimitive?.content ?: "",
                tokenize = { text -> text.map { it.code } },
            )
            val actual = try {
                TextgenRequestBodyEngine.build(input)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
