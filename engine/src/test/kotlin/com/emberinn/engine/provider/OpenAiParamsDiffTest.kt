package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：openai.js createGenerationParameters 核心（OpenAI/Azure）。
 * fixture 由 scripts/diff/openai-params-official.mjs 生成，禁止手改。
 */
class OpenAiParamsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `openai params match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/openai-params.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = json.parseToJsonElement(case.getValue("expected").toString())

            val s = body["settings"]!!.jsonObject
            val settings = OpenAiParamsSettings(
                source = s["source"]?.jsonPrimitive?.content ?: "openai",
                temp = s["temp"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                freqPen = s["freqPen"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                presPen = s["presPen"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                topP = s["topP"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                maxTokens = s["maxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                stream = s["stream"]?.jsonPrimitive?.content == "true",
                n = s["n"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                userName = s["userName"]?.jsonPrimitive?.content ?: "",
                charName = s["charName"]?.jsonPrimitive?.content ?: "",
                groupNames = s["groupNames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                showThoughts = s["showThoughts"]?.jsonPrimitive?.content == "true",
                reasoningEffort = s["reasoningEffort"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                enableWebSearch = s["enableWebSearch"]?.jsonPrimitive?.content == "true",
                requestImages = s["requestImages"]?.jsonPrimitive?.content == "true",
                requestImageResolution = s["requestImageResolution"]?.jsonPrimitive?.content ?: "auto",
                requestImageAspectRatio = s["requestImageAspectRatio"]?.jsonPrimitive?.content ?: "1:1",
                customPromptPostProcessing = s["customPromptPostProcessing"]?.jsonPrimitive?.content ?: "NONE",
                verbosity = s["verbosity"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                seed = s["seed"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                requestTokenProbabilities = s["requestTokenProbabilities"]?.jsonPrimitive?.content == "true",
                stopStrings = s["stopStrings"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                logitBias = s["logitBias"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 } ?: emptyMap(),
                azureBaseUrl = s["azureBaseUrl"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                azureDeploymentName = s["azureDeploymentName"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                azureApiVersion = s["azureApiVersion"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
            )
            val actual = json.parseToJsonElement(
                OpenAiParamsBuilder.build(
                    settings,
                    body["model"]?.jsonPrimitive?.content ?: "",
                    body["type"]?.jsonPrimitive?.content ?: "normal",
                    body["messages"]?.toString() ?: "[]",
                ),
            )
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (!el.isString) {
                val d = el.content.toDoubleOrNull()
                if (d != null && d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    kotlinx.serialization.json.JsonPrimitive(d.toLong().toString())
                } else el
            } else el
        }
        else -> el
    }
}
