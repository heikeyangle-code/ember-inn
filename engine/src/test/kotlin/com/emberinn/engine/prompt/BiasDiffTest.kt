package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js extractMessageBias / getBiasStrings / removeMacros。
 * fixture 由 scripts/diff/bias-official.mjs 生成（Handlebars 官方同版本 vendor），禁止手改。
 */
class BiasDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `bias matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/bias.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            when (body.getValue("method").jsonPrimitive.content) {
                "bias" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content,
                    BiasEngine.extractMessageBias(body["text"]?.jsonPrimitive?.content ?: ""),
                )
                "removeMacros" -> {
                    val text = when (val t = body["text"]) {
                        is JsonNull -> null
                        is JsonPrimitive -> t.content
                        else -> null
                    }
                    assertEquals("case $id", expected.jsonPrimitive.content, BiasEngine.removeMacros(text))
                }
                "get" -> {
                    val result = BiasEngine.getBiasStrings(
                        textareaText = body["textareaText"]?.jsonPrimitive?.content ?: "",
                        type = body["type"]?.jsonPrimitive?.content ?: "normal",
                        config = BiasConfig(
                            userPromptBias = body["userPromptBias"]?.jsonPrimitive?.content ?: "",
                            chat = body["chat"]?.jsonArray.orEmpty().map { it.jsonObject.toBiasChatMessage() },
                        ),
                    )
                    val e = expected.jsonObject
                    assertEquals("case $id messageBias", e["messageBias"]?.jsonPrimitive?.content ?: "", result.messageBias)
                    assertEquals("case $id promptBias", e["promptBias"]?.jsonPrimitive?.content ?: "", result.promptBias)
                    assertEquals("case $id isUserPromptBias", e["isUserPromptBias"]?.jsonPrimitive?.content == "true", result.isUserPromptBias)
                }
            }
        }
    }

    private fun JsonObject.toBiasChatMessage(): BiasChatMessage {
        val extra = this["extra"]?.jsonObject
        return BiasChatMessage(
            isUser = this["is_user"]?.jsonPrimitive?.content == "true",
            isSystem = this["is_system"]?.jsonPrimitive?.content == "true",
            isNarrator = extra?.get("type")?.jsonPrimitive?.content == "narrator",
            bias = extra?.get("bias")?.jsonPrimitive?.content,
        )
    }
}
