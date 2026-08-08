package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    @Test
    fun `provider converters and budget calculators match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/claude-messages.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("moreCases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val target = case.getValue("target").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")
            val messagesEl = args["messages"]
            val messages = (messagesEl as? JsonArray)?.map { it.jsonObject }
            val namesObj = args["names"]?.jsonObject
            val names = PromptNames(
                userName = namesObj?.get("userName")?.jsonPrimitive?.content ?: "",
                charName = namesObj?.get("charName")?.jsonPrimitive?.content ?: "",
                groupNames = namesObj?.get("groupNames")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            )
            val placeholder = args["promptPlaceholder"]?.jsonPrimitive?.content ?: "Let's get started."
            val tokenProvider: (Int) -> String = { "TOKEN$it" }

            val actualJson: JsonElement = when (target) {
                "cohere" -> buildJsonObject {
                    put("chatHistory", JsonArray(ProviderConverters.convertCohere(messages.orEmpty(), names, placeholder)))
                }
                "ai21" -> JsonArray(ProviderConverters.convertAI21(messages, names, placeholder))
                "mistral" -> JsonArray(ProviderConverters.convertMistral(messages, names, args["enablePrefix"]?.jsonPrimitive?.content == "true"))
                "xai" -> JsonArray(ProviderConverters.convertXAI(messages, names))
                "merge" -> JsonArray(ProviderConverters.mergeMessages(
                    messages.orEmpty(),
                    names,
                    strict = args["strict"]?.jsonPrimitive?.content == "true",
                    placeholders = args["placeholders"]?.jsonPrimitive?.content == "true",
                    single = args["single"]?.jsonPrimitive?.content == "true",
                    tools = args["tools"]?.jsonPrimitive?.content == "true",
                    promptPlaceholder = placeholder,
                    mediaToken = tokenProvider,
                ))
                "postProcess" -> JsonArray(ProviderConverters.postProcessPrompt(
                    messages.orEmpty(),
                    args["type"]?.jsonPrimitive?.content ?: "",
                    names,
                    promptPlaceholder = placeholder,
                    mediaToken = tokenProvider,
                ))
                "assistantPrefix" -> JsonArray(ProviderConverters.addAssistantPrefix(
                    messages.orEmpty(),
                    args["tools"]?.jsonArray?.map { it.jsonObject }.orEmpty(),
                    args["property"]?.jsonPrimitive?.content ?: "prefix",
                ))
                "textCompletion" -> JsonPrimitive(ProviderConverters.convertTextCompletionPrompt(args["messages"] ?: JsonNull))
                "claudeBudget" -> anyToJson(ProviderConverters.calculateClaudeBudgetTokens(
                    args["maxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                    args["reasoningEffort"]?.jsonPrimitive?.content ?: "",
                    args["stream"]?.jsonPrimitive?.content == "true",
                    args["isAdaptiveModel"]?.jsonPrimitive?.content == "true",
                ))
                "googleBudget" -> anyToJson(ProviderConverters.calculateGoogleBudgetTokens(
                    args["maxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512,
                    args["reasoningEffort"]?.jsonPrimitive?.content ?: "",
                    args["model"]?.jsonPrimitive?.content ?: "",
                ))
                "openRouterDepth" -> {
                    val ms = messages.orEmpty().map { JsonObject(it) }.toMutableList()
                    ProviderConverters.cachingAtDepthForOpenRouterClaude(
                        ms,
                        args["cachingAtDepth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        args["ttl"]?.jsonPrimitive?.content ?: "5m",
                    )
                    JsonArray(ms)
                }
                "openRouterSys" -> {
                    val ms = messages.orEmpty().map { JsonObject(it) }.toMutableList()
                    ProviderConverters.cachingSystemPromptForOpenRouter(ms, args["ttl"]?.jsonPrimitive?.content)
                    JsonArray(ms)
                }
                "embedMedia" -> {
                    val ms = messages.orEmpty().map { JsonObject(it) }.toMutableList()
                    ProviderConverters.embedOpenRouterMedia(
                        ms,
                        audio = args["audio"]?.jsonPrimitive?.content != "false",
                        video = args["video"]?.jsonPrimitive?.content != "false",
                    )
                    JsonArray(ms)
                }
                "reasoningContent" -> {
                    val ms = messages.orEmpty().map { JsonObject(it) }.toMutableList()
                    ProviderConverters.addReasoningContentToToolCalls(ms)
                    JsonArray(ms)
                }
                "openRouterSignatures" -> {
                    val ms = messages.orEmpty().map { JsonObject(it) }.toMutableList()
                    ProviderConverters.addOpenRouterSignatures(
                        ms,
                        args["model"]?.jsonPrimitive?.content ?: "",
                        enableThoughtSignatures = args["enableThoughtSignatures"]?.jsonPrimitive?.content != "false",
                    )
                    JsonArray(ms)
                }
                else -> error("unknown target $target")
            }

            assertEquals("case $id ($target)", canonical(expected), canonical(actualJson))
        }
    }

    private fun anyToJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        else -> error("unsupported $v")
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
