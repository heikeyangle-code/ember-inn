package com.emberinn.engine.group

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：group-chats.js activate* 群聊激活策略。
 * fixture 由 scripts/diff/group-activation-official.mjs 生成，禁止手改。
 */
class GroupActivationDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `group activation matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/group-activation.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val members = body.getValue("members").jsonArray.map { avatarEl ->
                val avatar = avatarEl.jsonPrimitive.content
                val member = body.getValue("characters").jsonArray.first { it.jsonObject["avatar"]?.jsonPrimitive?.content == avatar }.jsonObject
                GroupMember(
                    avatar = avatar,
                    name = member["name"]?.jsonPrimitive?.content ?: "",
                    talkativeness = member["talkativeness"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5,
                )
            }
            val chat = body["chat"]?.jsonArray?.map { el ->
                val o = el.jsonObject
                GroupMessage(
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    isUser = o["is_user"]?.jsonPrimitive?.content == "true",
                    isSystem = o["is_system"]?.jsonPrimitive?.content == "true",
                    originalAvatar = o["original_avatar"]?.jsonPrimitive?.content,
                    extraType = o["extra"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
                )
            } ?: emptyList()
            val lastMessage = body["lastMessage"]?.takeIf { it !is JsonNull }?.jsonObject?.let { o ->
                GroupMessage(
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    isUser = o["is_user"]?.jsonPrimitive?.content == "true",
                    originalAvatar = o["original_avatar"]?.jsonPrimitive?.content,
                    extraType = o["extra"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
                )
            }
            val randoms = body["randoms"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() } ?: emptyList()
            var idx = 0
            val random: () -> Double = { randoms.getOrNull(idx++) ?: 0.5 }

            val method = body.getValue("method").jsonPrimitive.content
            val result = when (method) {
                "list" -> GroupActivationEngine.listOrder(members)
                "impersonate" -> GroupActivationEngine.impersonate(members, random)
                "swipe" -> GroupActivationEngine.swipe(members, chat, body["allowSystem"]?.jsonPrimitive?.content == "true", random)
                "pooled" -> GroupActivationEngine.pooled(members, chat, lastMessage, body["isUserInput"]?.jsonPrimitive?.content == "true", random)
                "natural" -> GroupActivationEngine.natural(
                    members,
                    body["input"]?.jsonPrimitive?.content ?: "",
                    lastMessage,
                    body["allowSelfResponses"]?.jsonPrimitive?.content == "true",
                    body["isUserInput"]?.jsonPrimitive?.content == "true",
                    random,
                )
                else -> error("unknown method $method")
            }
            val actual = JsonArray(result.map { JsonPrimitive(it) })
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
