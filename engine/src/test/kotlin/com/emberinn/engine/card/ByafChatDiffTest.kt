package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：src/byaf.js getChatFromScenario。
 * fixture 由 scripts/diff/byaf-chat-official.mjs 生成（Date/encodeURI 打桩），禁止手改。
 */
class ByafChatDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `byaf chat matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/byaf-chat.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expectedText = case.getValue("expected").jsonPrimitive.content
            val expectedLines = expectedText.split('\n').filter { it.isNotBlank() }
            val expected = JsonArray(expectedLines.map { json.parseToJsonElement(it) })

            val backgrounds = body["chatBackgrounds"]?.jsonArray?.map { el ->
                val o = el.jsonObject
                ByafImporter.ByafChatBackground(
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    paths = o["paths"]?.jsonArray?.mapNotNull { it.jsonPrimitive.takeIf { p -> p.isString }?.content } ?: emptyList(),
                )
            } ?: emptyList()

            val actual = JsonArray(
                ByafImporter.chatFromScenario(
                    scenarioJson = body["scenario"],
                    userName = body["userName"]?.jsonPrimitive?.content ?: "",
                    characterName = body["characterName"]?.jsonPrimitive?.content ?: "",
                    chatBackgrounds = backgrounds,
                    now = "2026-08-08T00:00:00.000Z",
                ).map { it },
            )

            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
