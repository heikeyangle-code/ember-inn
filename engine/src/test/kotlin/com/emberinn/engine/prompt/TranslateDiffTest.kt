package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** Translate 差分（scripts/diff/translate-official.mjs → 19 例）。 */
class TranslateDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `translate matches official fixtures`() {
        val res = checkNotNull(javaClass.getResource("/diff/translate.json"))
        val root = json.parseToJsonElement(res.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertEquals(19, cases.size)

        for (el in cases) {
            val c = el.jsonObject
            val id = c.getValue("id").jsonPrimitive.int
            val name = c.getValue("name").jsonPrimitive.content
            val tag = c.getValue("_tag").jsonPrimitive.content
            val input = c.getValue("input")
            val expected = c.getValue("expected")

            when (tag) {
                "msg" -> {
                    val i = input.jsonObject
                    val mes = i.getValue("mes").jsonPrimitive.content
                    val charName = i.getValue("charName").jsonPrimitive.content
                    val override = i["nameOverride"]?.jsonPrimitive?.contentOrNull
                    val textToTranslate = TranslateEngine.substituteParamsNameOverride(mes, charName, override)
                    assertEquals("case $id $name textToTranslate",
                        expected.jsonObject["textToTranslate"]!!.jsonPrimitive.content, textToTranslate)
                    // 额外校验 display_key 语义：写 extra.display_text
                    val display = expected.jsonObject["extra"]!!.jsonObject.keys
                    assert(display.contains("display_text")) { "case $id: extra.display_text key missing" }
                }
                "reasoning" -> {
                    val i = input.jsonObject
                    val reasoning = i.getValue("reasoning").jsonPrimitive.content
                    val charName = i.getValue("charName").jsonPrimitive.content
                    val override = i["nameOverride"]?.jsonPrimitive?.contentOrNull
                    val textToTranslate = TranslateEngine.substituteParamsNameOverride(reasoning, charName, override)
                    assertEquals("case $id $name textToTranslate",
                        expected.jsonObject["textToTranslate"]!!.jsonPrimitive.content, textToTranslate)
                    val keys = expected.jsonObject["extra"]!!.jsonObject.keys
                    assert(keys.contains("reasoning_display_text")) { "case $id: reasoning_display_text key missing" }
                }
                "key" -> {
                    assertEquals("case $id $name", "display_text", expected.jsonObject["display_key"]!!.jsonPrimitive.content)
                    assertEquals("case $id $name", "reasoning_display_text", expected.jsonObject["reasoning_key"]!!.jsonPrimitive.content)
                }
                "prov-libre" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.libreBody(
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                        i["apiKey"]?.jsonPrimitive?.contentOrNull,
                    )
                    val actualJson = buildJsonObject {
                        actual.forEach { (k, v) -> put(k, v) }
                    }
                    assertEquals("case $id $name", expected, actualJson)
                }
                "prov-google" -> {
                    val i = input.jsonObject
                    val (endpoint, formKey) = TranslateEngine.googleEndpoint(i.getValue("target").jsonPrimitive.content)
                    val expectedUrl = expected.jsonObject["url"]!!.jsonPrimitive.content
                    assertEquals("case $id $name url", expectedUrl, endpoint)
                    assertEquals("case $id $name formKey", "q", formKey)
                }
                "prov-lingva" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.lingvaUrl(
                        i.getValue("baseUrl").jsonPrimitive.content,
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                    )
                    assertEquals("case $id $name", expected.jsonPrimitive.content, actual)
                }
                "prov-deepl" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.deeplBody(
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                        i.getValue("apiKey").jsonPrimitive.content,
                    )
                    val actualJson = buildJsonObject {
                        put("auth_key", actual.auth_key)
                        put("text", buildJsonArray { actual.text.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                        put("target_lang", actual.target_lang)
                    }
                    assertEquals("case $id $name", expected, actualJson)
                }
                "prov-deeplx" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.deeplxBody(
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                        i.getValue("baseUrl").jsonPrimitive.content,
                    )
                    val actualJson = buildJsonObject {
                        put("text", actual.text); put("source_lang", actual.source_lang)
                        put("target_lang", actual.target_lang); put("url", actual.url)
                    }
                    assertEquals("case $id $name", expected, actualJson)
                }
                "prov-onering" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.oneringBody(
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                        i.getValue("baseUrl").jsonPrimitive.content,
                        i.getValue("internalLang").jsonPrimitive.content,
                        i.getValue("targetLang").jsonPrimitive.content,
                    )
                    val actualJson = buildJsonObject {
                        put("text", actual.text); put("from_lang", actual.from_lang)
                        put("to_lang", actual.to_lang); put("url", actual.url)
                    }
                    assertEquals("case $id $name", expected, actualJson)
                }
            }
        }
    }
}
