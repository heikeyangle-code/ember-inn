package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：nai-settings.js getNovelGenerationData（Kayra/Clio/Erato/旧模型）。
 * fixture 由 scripts/diff/novel-body-official.mjs 生成。
 */
class NovelBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `novel request body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/novel-body.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonArray

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val id = c.getValue("id").jsonPrimitive.content
            val s = c.getValue("settings").jsonObject
            val e = c.getValue("extra").jsonObject

            fun d(key: String): Double = s[key]?.jsonPrimitive?.contentOrNull()?.toDoubleOrNull() ?: 0.0
            fun i(key: String): Int = s[key]?.jsonPrimitive?.contentOrNull()?.toIntOrNull() ?: 0
            fun str(key: String): String = s[key]?.jsonPrimitive?.contentOrNull() ?: ""
            fun extra(key: String): String = e[key]?.jsonPrimitive?.contentOrNull() ?: ""

            val input = NovelGenerationInput(
                model = str("model_novel"),
                temperature = d("temperature"),
                minLength = i("min_length"),
                tailFreeSampling = d("tail_free_sampling"),
                repetitionPenalty = d("repetition_penalty"),
                repetitionPenaltyRange = i("repetition_penalty_range"),
                repetitionPenaltySlope = d("repetition_penalty_slope"),
                repetitionPenaltyFrequency = d("repetition_penalty_frequency"),
                repetitionPenaltyPresence = d("repetition_penalty_presence"),
                topA = d("top_a"),
                topP = d("top_p"),
                topK = i("top_k"),
                minP = d("min_p"),
                math1Temp = d("math1_temp"),
                math1Quad = d("math1_quad"),
                math1QuadEntropyScale = d("math1_quad_entropy_scale"),
                typicalP = d("typical_p"),
                mirostatLr = d("mirostat_lr"),
                mirostatTau = d("mirostat_tau"),
                phraseRepPen = str("phrase_rep_pen"),
                order = (s["order"] as? JsonArray)?.map { it.jsonPrimitive.content },
                logitBias = s["logit_bias"]?.jsonArray ?: JsonArray(emptyList()),
                bannedTokens = (s["banned_tokens"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonPrimitive.content },
                prefix = str("prefix"),
                finalPrompt = extra("finalPrompt").ifBlank { "hello" },
                maxLength = e["maxLength"]?.jsonPrimitive?.contentOrNull()?.toIntOrNull() ?: 200,
                isImpersonate = extra("isImpersonate") == "true",
                isContinue = extra("isContinue") == "true",
                stoppingStrings = (e["stoppingStrings"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonPrimitive.content },
                maximumOutputLength = e["maximumOutputLength"]?.jsonPrimitive?.contentOrNull()?.toIntOrNull() ?: 600,
                requestTokenProbabilities = extra("requestTokenProbabilities") == "true",
            )
            val actual = NovelRequestBodyEngine.build(input)
            val expected = json.parseToJsonElement(c.getValue("expected").jsonPrimitive.content)
            assertEquals("case $id", expected, actual)
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content
}
