package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolRequestBuildersTest {

    private val messages = listOf(
        CompletionMessage("system", "系统"),
        CompletionMessage("user", "你好"),
        CompletionMessage("assistant", "回应"),
    )

    @Test
    fun `anthropic body splits system and roles`() {
        val root = Json.parseToJsonElement(AnthropicRequestBuilder.build("claude-sonnet-4-5", messages)).jsonObject
        assertEquals("系统", root["system"]?.toString()?.trim('"'))
        assertEquals("user", root["messages"]!!.jsonArray[0].jsonObject["role"]?.toString()?.trim('"'))
        assertEquals("assistant", root["messages"]!!.jsonArray[1].jsonObject["role"]?.toString()?.trim('"'))
    }

    @Test
    fun `google body maps roles and config`() {
        val root = Json.parseToJsonElement(GoogleRequestBuilder.build("gemini-2.5-pro", messages, maxOutputTokens = 256)).jsonObject
        assertEquals("model", root["contents"]!!.jsonArray[1].jsonObject["role"]?.toString()?.trim('"'))
        assertTrue(root.toString().contains("\"systemInstruction\""))
        assertTrue(root.toString().contains("\"maxOutputTokens\":256"))
    }
}
