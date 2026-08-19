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

/** Caption 扩展纯函数差分（scripts/diff/caption-official.mjs → 17 例）。 */
class CaptionDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `caption matches official fixtures`() {
        val res = checkNotNull(javaClass.getResource("/diff/caption.json"))
        val root = json.parseToJsonElement(res.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertEquals(17, cases.size)

        for (el in cases) {
            val c = el.jsonObject
            val id = c.getValue("id").jsonPrimitive.int
            val name = c.getValue("name").jsonPrimitive.content
            val tag = c["_tag"]?.jsonPrimitive?.contentOrNull ?: ""
            val input = c.getValue("input")
            val expected = c.getValue("expected")

            when (tag) {
                "prompt" -> {
                    val i = input.jsonObject
                    val external = i["external"]?.jsonPrimitive?.contentOrNull
                    val sp = i["sp"]?.jsonPrimitive?.contentOrNull ?: ""
                    val actual = CaptionEngine.resolvePrompt(external, sp)
                    assertEquals("case $id $name", expected.jsonPrimitive.content, actual)
                }
                "wrap" -> {
                    val i = input.jsonObject
                    val actual = CaptionEngine.wrapCaptionTemplate(
                        template = i.getValue("template").jsonPrimitive.content,
                        caption = i.getValue("caption").jsonPrimitive.content,
                        user = i.getValue("user").jsonPrimitive.content,
                        char = i.getValue("char").jsonPrimitive.content,
                    )
                    assertEquals("case $id $name", expected.jsonPrimitive.content, actual)
                }
                "mm" -> {
                    val i = input.jsonObject
                    val prompt = i.getValue("prompt").jsonPrimitive.content
                    val url = i.getValue("url").jsonPrimitive.content
                    val actual = CaptionEngine.multimodalRequest(prompt, url)
                    val actualJson = buildJsonArray {
                        actual.forEach { add(buildJsonObject {
                            put("role", it.role); put("content", it.content)
                            put("images", buildJsonArray { it.images.forEach { s -> add(kotlinx.serialization.json.JsonPrimitive(s)) } })
                        }) }
                    }
                    assertEquals("case $id $name", expected, actualJson)
                }
                "video" -> {
                    val filename = input.jsonPrimitive.content
                    val actual = CaptionEngine.isVideo(filename)
                    assertEquals("case $id $name",
                        expected.jsonPrimitive.booleanOrNull ?: false,
                        actual)
                }
            }
        }
    }
}
