package com.emberinn.engine.prompt

import com.emberinn.engine.macros.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：openai.js setOpenAIMessages 的 chat→messages 构造循环（names/isSameModel/narrator/工具调用过滤）。 */
class SetOpenAiMessagesDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `setOpenAIMessages matches official fixtures`() {
        val root = json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/diff/set-openai-messages.json")).readText(),
        ).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val id = c.getValue("id").jsonPrimitive.content
            val body = c.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = c.getValue("expected").jsonArray
            val chat = body["chat"]!!.jsonArray.map { it.jsonObject.toChatMessage() }
            val actual = PromptAssembler.toOpenAiMessages(
                chat = chat,
                namesBehavior = body["namesBehavior"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: PromptAssembler.NAMES_DEFAULT,
                selectedGroup = body["selectedGroup"]?.jsonPrimitive?.content == "true",
                user = body["name1"]?.jsonPrimitive?.contentOrNull ?: "User",
                name2 = body["name2"]?.jsonPrimitive?.contentOrNull ?: "Char",
                currentApi = body["currentApi"]?.jsonPrimitive?.contentOrNull ?: "",
                currentModel = body["currentModel"]?.jsonPrimitive?.contentOrNull ?: "",
            )
            assertEquals("case $id count", expected.size, actual.size)
            expected.forEachIndexed { index, expEl ->
                val exp = expEl.jsonObject
                val got = actual[index]
                assertEquals("case $id[$index] role", exp["role"]?.jsonPrimitive?.content, got.role)
                assertEquals("case $id[$index] content", exp["content"]?.jsonPrimitive?.content, got.content)
                assertEquals("case $id[$index] name", exp["name"]?.jsonPrimitive?.contentOrNull, got.name)
                val expReasoning = exp["reasoning"]?.jsonPrimitive?.contentOrNull ?: ""
                assertEquals("case $id[$index] reasoning", expReasoning, got.reasoning ?: "")
                assertEquals(
                    "case $id[$index] signature",
                    exp["signature"]?.jsonPrimitive?.contentOrNull,
                    got.signature,
                )
                val expInvocations = exp["invocations"]?.jsonArray.orEmpty().map { inv ->
                    val o = inv.jsonObject
                    ToolInvocation(
                        id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        parameters = o["parameters"]?.jsonPrimitive?.contentOrNull ?: "",
                        result = o["result"]?.jsonPrimitive?.contentOrNull ?: "",
                        reasoning = o["reasoning"]?.jsonPrimitive?.contentOrNull,
                        signature = o["signature"]?.jsonPrimitive?.contentOrNull,
                    )
                }
                assertEquals("case $id[$index] invocations", expInvocations, got.toolInvocations.orEmpty())
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObject.toChatMessage(): ChatMessage {
        val extra = this["extra"]?.jsonObject
        return ChatMessage(
            mes = this["mes"]?.jsonPrimitive?.contentOrNull ?: "",
            isUser = this["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true,
            name = this["name"]?.jsonPrimitive?.contentOrNull,
            narrator = extra?.get("type")?.jsonPrimitive?.contentOrNull == "narrator",
            forceAvatar = this["force_avatar"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true,
            api = extra?.get("api")?.jsonPrimitive?.contentOrNull,
            model = extra?.get("model")?.jsonPrimitive?.contentOrNull,
            reasoningSignature = extra?.get("reasoning_signature")?.jsonPrimitive?.contentOrNull,
            reasoning = extra?.get("reasoning")?.jsonPrimitive?.contentOrNull,
            toolInvocations = extra?.get("tool_invocations")?.jsonArray?.mapNotNull { inv ->
                val o = inv.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ToolInvocation(
                    id = id,
                    name = name,
                    parameters = o["parameters"]?.jsonPrimitive?.contentOrNull ?: "{}",
                    result = o["result"]?.jsonPrimitive?.contentOrNull ?: "",
                    reasoning = o["reasoning"]?.jsonPrimitive?.contentOrNull,
                    signature = o["signature"]?.jsonPrimitive?.contentOrNull,
                )
            },
        )
    }
}
