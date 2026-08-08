package com.emberinn.engine.group

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
import org.junit.Test

/**
 * 官方行为差分：group-chats.js + script.js 群聊循环纯逻辑。
 * fixture 由 scripts/diff/group-loop-official.mjs 生成，禁止手改。
 */
class GroupLoopDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `group loop matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/group-loop.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val actual = if (body.getValue("method").jsonPrimitive.content == "continue") {
                val settings = body["settings"]!!.jsonObject
                JsonPrimitive(
                    GroupLoopEngine.shouldAutoContinue(
                        messageChunk = body["messageChunk"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        isImpersonate = body["isImpersonate"]?.jsonPrimitive?.content == "true",
                        settings = AutoContinueSettings(
                            enabled = settings["enabled"]?.jsonPrimitive?.content == "true",
                            targetLength = settings["target_length"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            allowChatCompletions = settings["allow_chat_completions"]?.jsonPrimitive?.content == "true",
                        ),
                        userInputEmpty = body["userInputEmpty"]?.jsonPrimitive?.content != "false",
                        lastMessageTokens = body["lastMessageTokens"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
                        isOpenAi = body["isOpenAi"]?.jsonPrimitive?.content == "true",
                    ),
                )
            } else {
                val plan = GroupLoopEngine.planGeneration(
                    type = body["type"]?.jsonPrimitive?.content ?: "normal",
                    activatedMembers = body["activatedMembers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    showQueue = body["showQueue"]?.jsonPrimitive?.content == "true",
                )
                buildJsonObject {
                    val queueByAvatar = plan.queueOrder.toMap()
                    put("plan", JsonArray(plan.plan.map {
                        buildJsonObject {
                            put("avatar", JsonPrimitive(it.avatar))
                            put("generateType", JsonPrimitive(it.generateType))
                            val q = queueByAvatar[it.avatar]
                            if (q != null) put("queue", JsonPrimitive(q)) else put("queue", JsonNull)
                        }
                    }))
                    put("queueOrder", JsonArray(plan.queueOrder.map { (a, q) ->
                        JsonArray(listOf(JsonPrimitive(a), JsonPrimitive(q)))
                    }))
                }
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
