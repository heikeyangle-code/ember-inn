package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：sendMakerSuiteRequest getGeminiBody 请求体构造。
 * fixture 由 scripts/diff/gemini-body-official.mjs 生成（消息转换/预算/安全设置打桩），禁止手改。
 */
class GeminiBodyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `gemini request body matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/gemini-body.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = json.parseToJsonElement(case.getValue("expected").toString())

            val messages = body["messages"]?.jsonArray.orEmpty().map { m ->
                val obj = m.jsonObject
                val parts = obj["parts"]?.jsonArray
                CompletionMessage(
                    role = obj["role"]?.jsonPrimitive?.let { if (it.isString) it.content else null } ?: "user",
                    content = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: obj["content"]?.jsonPrimitive?.content ?: "",
                )
            }
            val tools = body["tools"]?.jsonArray.orEmpty().mapNotNull { t ->
                val fn = t.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
                GeminiFunctionTool(
                    name = fn["name"]?.jsonPrimitive?.content ?: "",
                    description = fn["description"]?.jsonPrimitive?.content ?: "",
                    parameters = fn["parameters"]?.jsonObject,
                )
            }
            val reasoningBudget: Any = body["reasoningBudget"]?.jsonPrimitive?.let { p ->
                if (p.isString) p.content else p.content.toIntOrNull() ?: 0
            } ?: 0
            val toolChoice = body["tool_choice"]
            val jsonSchema = body["json_schema"]?.jsonObject

            val actual = GoogleRequestBuilder.build(
                model = body["model"]!!.jsonPrimitive.content,
                messages = messages,
                maxOutputTokens = body["max_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                temperature = body["temperature"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                stream = body["stream"]?.jsonPrimitive?.content == "true",
                topP = body["top_p"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                topK = body["top_k"]?.jsonPrimitive?.let { if (it.isString) null else it.content.toIntOrNull() },
                stop = body["stop"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                seed = body["seed"]?.jsonPrimitive?.content?.toLongOrNull(),
                useSystemPrompt = body["use_sysprompt"]?.jsonPrimitive?.content == "true",
                systemInstructionParts = body["systemInstructionParts"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                tools = tools,
                toolChoice = toolChoice,
                enableWebSearch = body["enable_web_search"]?.jsonPrimitive?.content == "true",
                requestImages = body["request_images"]?.jsonPrimitive?.content == "true",
                aspectRatio = body["request_image_aspect_ratio"]?.jsonPrimitive?.content ?: "",
                imageSize = body["request_image_resolution"]?.jsonPrimitive?.content ?: "",
                reasoningEffort = body["reasoning_effort"]?.jsonPrimitive?.content ?: "",
                includeReasoning = body["include_reasoning"]?.jsonPrimitive?.content == "true",
                reasoningBudget = reasoningBudget,
                responseMimeType = if (jsonSchema != null) "application/json" else body["responseMimeType"]?.jsonPrimitive?.content,
                responseSchema = jsonSchema?.get("value")?.jsonObject,
            )

            assertEquals("case $id", expected, json.parseToJsonElement(actual))
        }
    }
}
