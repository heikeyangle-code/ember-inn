package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 additional-headers.js getMancerHeaders / getInfermaticAIHeaders / getFeatherlessHeaders 差分。
 * fixture 由 scripts/diff/textgen-headers-official.mjs（官方函数逐字）生成，禁止手改。
 */
class TextgenHeadersDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `textgen provider headers match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/textgen-headers.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonObject.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val expected = case.getValue("output").jsonObject.entries.associate { (k, v) ->
                k to (v as JsonPrimitive).content
            }
            val providerId = when {
                id.startsWith("mancer") -> "textgen-mancer"
                id.startsWith("infermaticai") -> "textgen-infermaticai"
                else -> "textgen-featherless"
            }
            val hasKey = id.endsWith("with_key")
            val key = if (hasKey) {
                when (providerId) {
                    "textgen-mancer" -> "mancer-key"
                    "textgen-infermaticai" -> "infermatic-key"
                    else -> "feather-key"
                }
            } else {
                ""
            }
            val actual = TextgenHeaders.forProvider(providerId, key)
            assertEquals("case $id", expected, actual)
        }
    }
}
