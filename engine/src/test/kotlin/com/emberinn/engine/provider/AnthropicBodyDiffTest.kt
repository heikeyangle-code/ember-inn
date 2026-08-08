package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：sendClaudeRequest 的 requestBody 构造段。
 * fixture 由 scripts/diff/anthropic-body-official.mjs 生成（真 convertClaudeMessages），禁止手改。
 */
class AnthropicBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `anthropic request body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/anthropic-body.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expectedBody = json.parseToJsonElement(
                case.getValue("expected").jsonObject.getValue("body").toString(),
            )
            val expectedBeta = case.getValue("expected").jsonObject.getValue("betaHeaders").jsonArray
                .map { it.jsonPrimitive.content }

            val rawMessages = body["messages"]?.jsonArray.orEmpty().map { it.jsonObject }
            val tools = body["tools"]?.jsonArray.orEmpty().map { t ->
                val fn = t.jsonObject["function"]?.jsonObject ?: t.jsonObject
                AnthropicTool(
                    name = fn["name"]?.jsonPrimitive?.content ?: "",
                    description = fn["description"]?.jsonPrimitive?.content ?: "",
                    parameters = fn["parameters"]?.jsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap()),
                )
            }
            val reasoningBudget: Any = body["reasoningBudget"]?.jsonPrimitive?.let { p ->
                if (p.isString) p.content else p.content.toIntOrNull() ?: 1024
            } ?: 1024

            val result = AnthropicRequestBuilder.buildFromChatML(
                model = body["model"]!!.jsonPrimitive.content,
                messages = rawMessages,
                maxTokens = body["max_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                temperature = body["temperature"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                stream = body["stream"]?.jsonPrimitive?.content == "true",
                topP = body["top_p"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                topK = body["top_k"]?.jsonPrimitive?.content?.toIntOrNull(),
                stop = body["stop"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                useSystemPrompt = body["use_sysprompt"]?.jsonPrimitive?.content == "true",
                systemPrompt = body["systemPrompt"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                assistantPrefill = body["assistant_prefill"]?.jsonPrimitive?.content ?: "",
                tools = tools,
                toolChoice = body["tool_choice"]?.jsonPrimitive?.let { if (it.isString) it.content else null },
                jsonSchema = body["json_schema"]?.jsonObject,
                enableWebSearch = body["enable_web_search"]?.jsonPrimitive?.content == "true",
                includeReasoning = body["include_reasoning"]?.jsonPrimitive?.content == "true",
                verbosity = body["verbosity"]?.jsonPrimitive?.content ?: "",
                reasoningBudget = reasoningBudget,
                enableSystemPromptCache = body["enableSystemPromptCache"]?.jsonPrimitive?.content == "true",
                cachingAtDepth = body["cachingAtDepth"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
            )

            val actualBody = json.parseToJsonElement(result.body)
            assertEquals("case $id body", expectedBody, actualBody)
            assertEquals("case $id beta", expectedBeta, result.betaHeaders)
        }
    }
}
