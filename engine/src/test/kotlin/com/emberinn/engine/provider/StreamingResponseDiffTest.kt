package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：openai.js getStreamingReply / tryParseStreamingError。
 * fixture 由 scripts/diff/streaming-response-official.mjs 生成，禁止手改。
 */
class StreamingResponseDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `streaming response matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/streaming-response.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonObject

            when (body.getValue("method").jsonPrimitive.content) {
                "stream" -> {
                    val result = StreamingReplyParser.process(
                        data = body["data"],
                        chatCompletionSource = body["source"]?.jsonPrimitive?.content
                            ?: body["chatCompletionSource"]?.jsonPrimitive?.content,
                        showThoughts = body["showThoughts"]?.jsonPrimitive?.content != "false",
                        state = StreamingState(),
                    )
                    assertEquals("case $id text", expected["text"]?.jsonPrimitive?.content ?: "", result.text)
                    assertEquals("case $id reasoning", expected["state"]?.jsonObject?.get("reasoning")?.jsonPrimitive?.content ?: "", result.state.reasoning)
                    assertEquals(
                        "case $id images",
                        expected["state"]?.jsonObject?.get("images")?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                        result.state.images,
                    )
                    assertEquals(
                        "case $id signature",
                        expected["state"]?.jsonObject?.get("signature")?.stringOrNull(),
                        result.state.signature,
                    )
                    val expectedTools = expected["state"]?.jsonObject?.get("toolSignatures")?.jsonObject
                        ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                        .orEmpty()
                    assertEquals("case $id toolSignatures", expectedTools, result.state.toolSignatures)
                }
                "error" -> {
                    val info = StreamingErrorParser.parse(
                        decoded = body["decoded"]?.jsonPrimitive?.content ?: "",
                        statusText = "Bad Request",
                    )
                    assertEquals("case $id quota", expected["quota"]?.jsonPrimitive?.content == "true", info.quotaError)
                    assertEquals("case $id moderation", expected["moderation"]?.jsonPrimitive?.content == "true", info.moderationError)
                    assertEquals("case $id errorMessage", expected["errorMessage"]?.stringOrNull(), info.errorMessage)
                    assertEquals("case $id message", expected["message"]?.stringOrNull(), info.message)
                    assertEquals("case $id detail", expected["detail"]?.stringOrNull(), info.detail)
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }
}
