package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 官方行为差分：convertClaudeMessages + convertGooglePrompt 整链。
 * fixture 由 scripts/diff/prompt-converters-official.mjs 逐字提取生成，禁止手改。
 */
class PromptConvertersDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `claude and google prompt converters match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/claude-messages.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val messages = args.getValue("messages").jsonArray.map { it.jsonObject }
            val namesObj = args["names"]?.jsonObject
            val names = PromptNames(
                userName = namesObj?.get("userName")?.jsonPrimitive?.content ?: "",
                charName = namesObj?.get("charName")?.jsonPrimitive?.content ?: "",
                groupNames = namesObj?.get("groupNames")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            )

            // Claude
            val claudeExpected = case["claude"]
            val claudeThrows = args["claudeThrows"]?.jsonPrimitive?.content == "true"
            if (claudeExpected == null || claudeExpected is JsonNull || claudeThrows) {
                assertThrows("case $id claude should throw", RuntimeException::class.java) {
                    ClaudeMessagesConverter.convert(
                        messages,
                        args["assistantPrefill"]?.jsonPrimitive?.content ?: "",
                        args["useSysPrompt"]?.jsonPrimitive?.content == "true",
                        args["useTools"]?.jsonPrimitive?.content == "true",
                        names,
                        args["promptPlaceholder"]?.jsonPrimitive?.content ?: "Let's get started.",
                    )
                }
            } else {
                val actual = ClaudeMessagesConverter.convert(
                    messages,
                    args["assistantPrefill"]?.jsonPrimitive?.content ?: "",
                    args["useSysPrompt"]?.jsonPrimitive?.content == "true",
                    args["useTools"]?.jsonPrimitive?.content == "true",
                    names,
                    args["promptPlaceholder"]?.jsonPrimitive?.content ?: "Let's get started.",
                )
                val actualJson = buildJsonObject {
                    put("messages", JsonArray(actual.messages))
                    put("systemPrompt", JsonArray(actual.systemPrompt))
                }
                assertEquals("case $id claude", canonical(claudeExpected), canonical(actualJson))
            }

            // Google
            val googleExpected = case.getValue("google")
            val googleActual = GooglePromptConverter.convert(
                messages,
                args["model"]?.jsonPrimitive?.content ?: "",
                args["useSysPrompt"]?.jsonPrimitive?.content == "true",
                names,
                args["enableThoughtSignatures"]?.jsonPrimitive?.content != "false",
            )
            val googleActualJson = buildJsonObject {
                put("contents", JsonArray(googleActual.contents))
                put("system_instruction", buildJsonObject {
                    put("parts", JsonArray(googleActual.systemInstructionParts))
                })
            }
            assertEquals("case $id google", canonical(googleExpected), canonical(googleActualJson))
        }
    }

    @Test
    fun `caching at depth matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/claude-messages.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cachingCases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val messages = args.getValue("messages").jsonArray.map { it.jsonObject }.toMutableList()
            val expected = case.getValue("expected")

            ClaudeMessagesConverter.atDepth(
                messages,
                args["cachingAtDepth"]!!.jsonPrimitive.content.toInt(),
                args["ttl"]?.jsonPrimitive?.content ?: "5m",
            )

            assertEquals("caching case $id", canonical(expected), canonical(JsonArray(messages)))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
