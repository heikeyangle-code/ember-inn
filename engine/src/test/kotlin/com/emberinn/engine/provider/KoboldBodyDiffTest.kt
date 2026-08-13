package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 Kobold 请求体差分：src/endpoints/backends/kobold.js 逐字移植。
 * fixture 由 scripts/diff/kobold-body-official.mjs 生成，禁止手改。
 */
class KoboldBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `kobold request body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/kobold-body.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonObject

            val input = KoboldRequestBodyEngine.Input(
                apiServer = body["api_server"]?.jsonPrimitive?.contentOrNull ?: "",
                prompt = body["prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                maxContextLength = body["max_context_length"]?.jsonPrimitive?.intOrNull ?: 0,
                maxLength = body["max_length"]?.jsonPrimitive?.intOrNull ?: 0,
                guiSettings = body["gui_settings"]?.jsonPrimitive?.booleanOrNull ?: false,
                streaming = body["streaming"]?.jsonPrimitive?.booleanOrNull ?: false,
                repPen = body["rep_pen"]?.jsonPrimitive?.doubleOrNull,
                repPenRange = body["rep_pen_range"]?.jsonPrimitive?.intOrNull,
                repPenSlope = body["rep_pen_slope"]?.jsonPrimitive?.doubleOrNull,
                temperature = body["temperature"]?.jsonPrimitive?.doubleOrNull,
                tfs = body["tfs"]?.jsonPrimitive?.doubleOrNull,
                topA = body["top_a"]?.jsonPrimitive?.doubleOrNull,
                topK = body["top_k"]?.jsonPrimitive?.intOrNull,
                topP = body["top_p"]?.jsonPrimitive?.doubleOrNull,
                minP = body["min_p"]?.jsonPrimitive?.doubleOrNull,
                typical = body["typical"]?.jsonPrimitive?.doubleOrNull,
                samplerOrder = (body["sampler_order"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.intOrNull },
                singleline = body["singleline"]?.jsonPrimitive?.booleanOrNull ?: false,
                useDefaultBadwordsids = body["use_default_badwordsids"]?.jsonPrimitive?.booleanOrNull ?: false,
                mirostat = body["mirostat"]?.jsonPrimitive?.intOrNull,
                mirostatEta = body["mirostat_eta"]?.jsonPrimitive?.doubleOrNull,
                mirostatTau = body["mirostat_tau"]?.jsonPrimitive?.doubleOrNull,
                grammar = body["grammar"]?.jsonPrimitive?.contentOrNull,
                samplerSeed = body["sampler_seed"]?.jsonPrimitive?.intOrNull,
                stopSequence = body["stop_sequence"]?.jsonPrimitive?.contentOrNull,
            )
            val actual = KoboldRequestBodyEngine.build(input)
            assertEquals("case $id api_server", expected["api_server"]?.jsonPrimitive?.contentOrNull, actual.apiServer)
            assertEquals("case $id url", expected["url"]?.jsonPrimitive?.contentOrNull, actual.url)
            assertEquals("case $id body", expected["body"]?.jsonPrimitive?.contentOrNull, actual.body)
        }
    }
}
