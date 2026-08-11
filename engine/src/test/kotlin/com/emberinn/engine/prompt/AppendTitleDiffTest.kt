package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：script.js coreChat.map 的 append_title 标题追加。 */
class AppendTitleDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `append title matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/append-title.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content
            val extra = body["extra"]?.jsonObject ?: JsonObject(emptyMap())
            val titles = mutableListOf<String>()
            if (extra["append_title"]?.jsonPrimitive?.content == "true") {
                extra["title"]?.jsonPrimitive?.content?.let { titles += it }
            }
            extra["media"]?.jsonArray?.forEach { me ->
                val mo = me.jsonObject
                if (mo["append_title"]?.jsonPrimitive?.content == "true") {
                    mo["title"]?.jsonPrimitive?.content?.let { titles += it }
                }
            }
            assertEquals(
                "case $id",
                expected,
                PromptAssembler.appendMessageTitles(body["mes"]?.jsonPrimitive?.content ?: "", titles),
            )
        }
    }
}
