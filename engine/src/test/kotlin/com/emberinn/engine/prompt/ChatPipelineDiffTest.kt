package com.emberinn.engine.prompt

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
 * 官方行为差分：openai.js populateChatCompletion 操作计划。
 * fixture 由 scripts/diff/chat-pipeline-official.mjs 生成，禁止手改。
 */
class ChatPipelineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat pipeline plan matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/chat-pipeline.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val promptsObj = body["prompts"]!!.jsonObject
            val prompts = PromptItems(promptsObj["collection"]!!.jsonArray.map { el ->
                val o = el.jsonObject
                PromptItem(
                    identifier = o["identifier"]!!.jsonPrimitive.content,
                    name = o["name"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: "",
                    role = o["role"]?.jsonPrimitive?.content ?: "system",
                    content = o["content"]?.jsonPrimitive?.content ?: "",
                    injectionPosition = o["injection_position"]?.jsonPrimitive?.content?.toIntOrNull(),
                )
            })
            prompts.overriddenPrompts.addAll(promptsObj["overriddenPrompts"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())

            val messages = body["messages"]?.jsonArray?.map { el ->
                val o = el.jsonObject
                PromptMessage(
                    role = o["role"]?.jsonPrimitive?.content ?: "",
                    content = o["content"]?.jsonPrimitive?.content ?: "",
                    name = o["name"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                    identifier = o["identifier"]?.jsonPrimitive?.content,
                )
            } ?: emptyList()

            val actual = ChatCompletionPipelinePlan.plan(
                prompts = prompts,
                messages = messages,
                type = body["type"]?.jsonPrimitive?.content ?: "normal",
                bias = body["bias"]?.jsonPrimitive?.content ?: "",
                quietPrompt = body["quietPrompt"]?.jsonPrimitive?.content ?: "",
                pinExamples = body["pinExamples"]?.jsonPrimitive?.content == "true",
                toolBudgetReserve = body["toolTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                toolCallsEnabled = body["toolCallsEnabled"]?.jsonPrimitive?.content == "true",
                disabledPromptIds = body["disabledPromptIds"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                continuePrefill = body["continuePrefill"]?.jsonPrimitive?.content == "true",
                chatCompletionSource = body["chatCompletionSource"]?.jsonPrimitive?.content ?: "openai",
                assistantPrefill = body["assistantPrefill"]?.jsonPrimitive?.content ?: "",
            )

            assertEquals("case $id", canonical(expected), canonical(JsonArray(actual)))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
