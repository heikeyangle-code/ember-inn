package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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

/** Translate 差分（scripts/diff/translate-official.mjs → 38 例）。 */
class TranslateDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `translate matches official fixtures`() {
        val res = checkNotNull(javaClass.getResource("/diff/translate.json"))
        val root = json.parseToJsonElement(res.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertEquals(38, cases.size)

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
                "prov-google-url" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.googleBatchUrl(i.getValue("reqId").jsonPrimitive.int)
                    assertEquals("case $id $name url", expected.jsonObject["url"]!!.jsonPrimitive.content, actual)
                }
                "prov-google-body" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.googleBody(
                        i.getValue("text").jsonPrimitive.content,
                        i.getValue("target").jsonPrimitive.content,
                        0,
                    )
                    assertEquals("case $id $name body", expected.jsonObject["body"]!!.jsonPrimitive.content, actual)
                }
                "prov-google-parse" -> {
                    val i = input.jsonObject
                    val actual = TranslateEngine.googleParse(i.getValue("response").jsonPrimitive.content)
                    val exp = expected.jsonObject["text"]
                    if (exp == null || exp is kotlinx.serialization.json.JsonNull) {
                        assertEquals("case $id $name parse-fail", null, actual)
                    } else {
                        assertEquals("case $id $name text", exp.jsonPrimitive.content, actual)
                    }
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
                // 图片链接切段（官方 translate()）：[{isLink, text}]
                "seg" -> {
                    val i = input.jsonObject
                    val text = i.getValue("text").jsonPrimitive.content
                    val actual = TranslateEngine.imageLinkSegments(text)
                    val actualJson = buildJsonArray {
                        actual.forEach { s ->
                            add(buildJsonObject {
                                put("isLink", s.isLink)
                                put("text", s.text)
                            })
                        }
                    }
                    assertEquals("case $id $name",
                        expected.jsonObject.getValue("segments"), actualJson)
                }
                // 分块调用序列（chunkedTranslate）
                "chunk" -> {
                    val i = input.jsonObject
                    val provider = i.getValue("provider").jsonPrimitive.content
                    val text = i.getValue("text").jsonPrimitive.content
                    val actual = TranslateEngine.chunked(text, provider)
                    val actualJson = buildJsonArray { actual.forEach { add(JsonPrimitive(it)) } }
                    // 长度辅助断言（官方脚本对超限无分隔符场景额外记录 lengths）
                    expected.jsonObject["lengths"]?.let { lens ->
                        assertEquals("case $id $name lengths", lens,
                            buildJsonArray { actual.forEach { add(JsonPrimitive(it.length)) } })
                    }
                    assertEquals("case $id $name",
                        expected.jsonObject.getValue("calls"), actualJson)
                }
            }
        }
    }
}
