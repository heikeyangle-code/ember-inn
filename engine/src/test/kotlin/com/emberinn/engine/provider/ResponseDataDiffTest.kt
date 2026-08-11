package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js extractMessageFromData / extractJsonFromData。
 * fixture 由 scripts/diff/response-data-official.mjs 生成，禁止手改。
 */
class ResponseDataDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `response data extraction matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/response-data.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content
            val data = body["data"]

            val actual = when (body.getValue("method").jsonPrimitive.content) {
                "message" -> ResponseDataExtractor.extractMessageFromData(
                    data = data,
                    activeApi = body["activeApi"]?.jsonPrimitive?.content
                        ?: body["mainApi"]?.jsonPrimitive?.content,
                )
                "json" -> ResponseDataExtractor.extractJsonFromData(
                    data = data,
                    mainApi = body["mainApi"]?.jsonPrimitive?.content,
                    chatCompletionSource = body["chatCompletionSource"]?.jsonPrimitive?.content,
                    returnInvalidJson = body["returnInvalidJson"]?.jsonPrimitive?.content == "true",
                )
                else -> error("unknown method in case $id")
            }

            assertEquals("case $id", expected, actual)
        }
    }
}
