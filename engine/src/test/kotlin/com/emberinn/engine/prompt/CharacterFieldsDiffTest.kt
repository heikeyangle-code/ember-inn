package com.emberinn.engine.prompt

import com.emberinn.engine.group.GroupCharacterCards
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js getCharacterCardFields。
 * fixture 由 scripts/diff/character-fields-official.mjs 生成，禁止手改。
 */
class CharacterFieldsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `character fields match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/character-fields.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val chid = body["chid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val characterEl = body["characters"]?.jsonArray?.getOrNull(chid)
            val character = characterEl?.takeIf { it !is JsonNull }?.jsonObject?.let { parseCharacter(it) }
            val meta = body["chat_metadata"]?.jsonObject ?: JsonObject(emptyMap())
            val pu = body["power_user"]?.jsonObject ?: JsonObject(emptyMap())
            val groupCards = body["groupCards"]?.takeIf { it !is JsonNull }?.jsonObject?.let { g ->
                GroupCharacterCards(
                    description = g["description"]?.jsonPrimitive?.content ?: "",
                    personality = g["personality"]?.jsonPrimitive?.content ?: "",
                    scenario = g["scenario"]?.jsonPrimitive?.content ?: "",
                    mesExamples = g["mesExamples"]?.jsonPrimitive?.content ?: "",
                )
            }

            val result = CharacterCardFieldsEngine.fields(
                character = character,
                personaDescription = pu["persona_description"]?.jsonPrimitive?.content ?: "",
                preferCharacterPrompt = pu["prefer_character_prompt"]?.jsonPrimitive?.content != "false",
                preferCharacterJailbreak = pu["prefer_character_jailbreak"]?.jsonPrimitive?.content != "false",
                chatMetadataSystem = meta["system_prompt"]?.jsonPrimitive?.content ?: "",
                chatMetadataScenario = meta["scenario"]?.jsonPrimitive?.content ?: "",
                chatMetadataMesExample = meta["mes_example"]?.jsonPrimitive?.content ?: "",
                groupCards = groupCards,
            )

            val actual = buildJsonObject {
                put("system", JsonPrimitive(result.system))
                put("mesExamples", JsonPrimitive(result.mesExamples))
                put("description", JsonPrimitive(result.description))
                put("personality", JsonPrimitive(result.personality))
                put("persona", JsonPrimitive(result.persona))
                put("scenario", JsonPrimitive(result.scenario))
                put("jailbreak", JsonPrimitive(result.jailbreak))
                put("version", JsonPrimitive(result.version))
                put("charDepthPrompt", JsonPrimitive(result.charDepthPrompt))
                put("creatorNotes", JsonPrimitive(result.creatorNotes))
                put("firstMessage", JsonPrimitive(result.firstMessage))
                put("alternateGreetings", JsonArray(result.alternateGreetings.map { JsonPrimitive(it) }))
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun parseCharacter(o: JsonObject): CharacterCardSource {
        val data = o["data"]?.jsonObject
        val depth = data?.get("extensions")?.jsonObject?.get("depth_prompt")?.jsonObject
        return CharacterCardSource(
            name = o["name"]?.jsonPrimitive?.content ?: "",
            description = o["description"]?.jsonPrimitive?.content ?: "",
            personality = o["personality"]?.jsonPrimitive?.content ?: "",
            scenario = o["scenario"]?.jsonPrimitive?.content ?: "",
            mesExample = o["mes_example"]?.jsonPrimitive?.content ?: "",
            firstMessage = o["first_mes"]?.jsonPrimitive?.content ?: "",
            systemPrompt = data?.get("system_prompt")?.jsonPrimitive?.content ?: "",
            postHistoryInstructions = data?.get("post_history_instructions")?.jsonPrimitive?.content ?: "",
            characterVersion = data?.get("character_version")?.jsonPrimitive?.content ?: "",
            creatorNotes = data?.get("creator_notes")?.jsonPrimitive?.content ?: "",
            depthPrompt = depth?.get("prompt")?.jsonPrimitive?.content ?: "",
            alternateGreetings = data?.get("alternate_greetings")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        )
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
