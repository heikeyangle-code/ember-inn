package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 chat-templates.js 差分：deriveTemplatesFromChatTemplate / bindModelTemplates。
 * fixture 由 scripts/diff/chat-template-official.mjs 生成（25 例）。
 */
class ChatTemplateDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat template outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/chat-template.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val kind = c.getValue("kind").jsonPrimitive.content
            val expected = json.parseToJsonElement(c.getValue("expected").jsonPrimitive.content)
            when (kind) {
                "derive" -> {
                    val template = c.getValue("template").jsonPrimitive.content
                    val hash = c.getValue("hash").jsonPrimitive.content
                    val out = ChatTemplateEngine.deriveTemplatesFromChatTemplate(template, hash)
                    val expectedObj = expected.jsonObject
                    assertEquals("derive($template, $hash).context", nullableString(expectedObj["context"]), out.context)
                    assertEquals("derive($template, $hash).instruct", nullableString(expectedObj["instruct"]), out.instruct)
                }
                "bind" -> {
                    val before = json.parseToJsonElement(c.getValue("before").jsonPrimitive.content).jsonObject
                    val status = c.getValue("status").jsonPrimitive.content
                    val result = ChatTemplateEngine.bindModelTemplates(before, status)
                    val expectedObj = expected.jsonObject
                    assertEquals("bind($status).result", expectedObj.getValue("result").jsonPrimitive.content, result.bound.toString())
                    assertEquals("bind($status).after", expectedObj.getValue("after"), result.powerUser)
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    private fun nullableString(el: kotlinx.serialization.json.JsonElement?): String? =
        (el as? JsonPrimitive)?.contentOrNull
}
