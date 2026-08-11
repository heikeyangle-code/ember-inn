package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js shouldAutoContinue。
 * fixture 由 scripts/diff/auto-continue-official.mjs 生成，禁止手改。
 */
class AutoContinueDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `auto continue matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/auto-continue.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content == "true"
            val tokenCounts = body["tokenCounts"]?.jsonObject
                ?.mapValues { (_, v) -> v.jsonPrimitive.content.toInt() }
                .orEmpty()

            val actual = AutoContinueEngine.shouldAutoContinue(
                messageChunk = body["messageChunk"]?.jsonPrimitive?.content ?: "",
                isImpersonate = body["isImpersonate"]?.jsonPrimitive?.content == "true",
                config = AutoContinueConfig(
                    enabled = body["enabled"]?.jsonPrimitive?.content == "true",
                    targetLength = body["targetLength"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    allowChatCompletions = body["allowChatCompletions"]?.jsonPrimitive?.content != "false",
                    isSendPress = body["isSendPress"]?.jsonPrimitive?.content == "true",
                    generationStopped = body["generationStopped"]?.jsonPrimitive?.content == "true",
                    mainApi = body["mainApi"]?.jsonPrimitive?.content ?: "openai",
                    textareaText = body["textareaText"]?.jsonPrimitive?.content ?: "",
                    lastMessageText = body["lastMessageText"]?.jsonPrimitive?.content,
                ),
                tokenCount = { text -> tokenCounts[text] ?: 0 },
            )

            assertEquals("case $id", expected, actual)
        }
    }
}
