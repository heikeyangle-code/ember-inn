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
 * 官方行为差分：group-chats.js getGroupCharacterCardsLazy（APPEND 角色卡合并）。
 * fixture 由 scripts/diff/group-cards-official.mjs 生成，禁止手改。
 */
class GroupCardsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `group cards match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/group-cards.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val group = body.getValue("groups").jsonArray.first().jsonObject
            val characters = body.getValue("characters").jsonArray.map { el ->
                val o = el.jsonObject
                GroupCardMember(
                    avatar = o["avatar"]!!.jsonPrimitive.content,
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    description = o["description"]?.jsonPrimitive?.content ?: "",
                    personality = o["personality"]?.jsonPrimitive?.content ?: "",
                    scenario = o["scenario"]?.jsonPrimitive?.content ?: "",
                    mesExample = o["mes_example"]?.jsonPrimitive?.content ?: "",
                )
            }
            val meta = body["chat_metadata"]?.jsonObject ?: JsonObject(emptyMap())
            val result = GroupCharacterCardsEngine.cards(
                groupId = body["groupId"]!!.jsonPrimitive.content,
                generationMode = group["generation_mode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                members = group["members"]!!.jsonArray.map { it.jsonPrimitive.content },
                disabledMembers = group["disabled_members"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                joinPrefix = group["generation_mode_join_prefix"]?.jsonPrimitive?.content ?: "",
                joinSuffix = group["generation_mode_join_suffix"]?.jsonPrimitive?.content ?: "",
                characterCards = characters,
                characterId = body["characterId"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                scenarioOverride = meta["scenario"]?.jsonPrimitive?.content ?: "",
                mesExamplesOverride = meta["mes_example"]?.jsonPrimitive?.content ?: "",
            )

            val actual = result?.let {
                buildJsonObject {
                    put("description", JsonPrimitive(it.description))
                    put("personality", JsonPrimitive(it.personality))
                    put("scenario", JsonPrimitive(it.scenario))
                    put("mesExamples", JsonPrimitive(it.mesExamples))
                }
            } ?: JsonNull

            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
