package com.emberinn.engine.group

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 * 官方行为差分：group-chats.js getGroupDepthPrompts。
 * fixture 由 scripts/diff/group-depth-official.mjs 生成，禁止手改。
 */
class GroupDepthDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `group depth prompts match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/group-depth.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val group = body["groups"]?.jsonArray?.firstOrNull()?.jsonObject
            val membersList = body.getValue("characters").jsonArray.map { el ->
                val o = el.jsonObject
                val dp = o["data"]?.jsonObject?.get("extensions")?.jsonObject?.get("depth_prompt")?.jsonObject
                GroupDepthMember(
                    avatar = o["avatar"]!!.jsonPrimitive.content,
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    depthPrompt = dp?.get("prompt")?.jsonPrimitive?.content,
                    depth = dp?.get("depth")?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
                    role = dp?.get("role")?.jsonPrimitive?.content ?: "system",
                )
            }
            val actual = GroupDepthPromptsEngine.collect(
                groupId = body["groupId"]!!.jsonPrimitive.content,
                generationMode = group?.get("generation_mode")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                members = group?.get("members")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                disabledMembers = group?.get("disabled_members")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                characterCards = membersList,
                characterId = body["characterId"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
            val actualJson = JsonArray(actual.map {
                buildJsonObject {
                    put("text", JsonPrimitive(it.text))
                    put("depth", JsonPrimitive(it.depth))
                    put("role", JsonPrimitive(it.role))
                }
            })
            assertEquals("case $id", canonical(expected), canonical(actualJson))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
