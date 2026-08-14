package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方 tokenizers.js getTokenizerModel 差分（37 例）。 */
class TokenizerModelDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `getTokenizerModel matches official fixtures`() {
        val root = json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/diff/tokenizer-model.json")).readText(),
        ).jsonObject
        for (caseEl in root.getValue("cases").jsonArray) {
            val case = caseEl.jsonObject
            val model = case.getValue("model").jsonPrimitive.content
            val expected = case.getValue("key").jsonPrimitive.content
            assertEquals("model=$model", expected, TokenizerModel.map(model))
        }
    }
}
