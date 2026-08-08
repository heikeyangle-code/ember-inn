package com.emberinn.engine.persona

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
 * 官方行为差分：personas.js 纯逻辑（状态/临时锁/连接/解析）。
 * fixture 由 scripts/diff/persona-engine-official.mjs 生成，禁止手改。
 */
class PersonaEngineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `persona engine matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/persona-engine.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val method = body.getValue("method").jsonPrimitive.content

            val actual = when (method) {
                "states" -> {
                    val pu = body["powerUser"]!!.jsonObject
                    val desc = pu["persona_descriptions"]!!.jsonObject[body["avatarId"]!!.jsonPrimitive.content]?.jsonObject
                    val s = PersonaEngine.states(
                        avatarId = body["avatarId"]!!.jsonPrimitive.content,
                        defaultPersona = pu["default_persona"]?.jsonPrimitive?.content,
                        chatPersona = body["chatPersona"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        connections = desc?.get("connections")?.jsonArray?.map { PersonaConnection(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["id"]!!.jsonPrimitive.content) } ?: emptyList(),
                        selectedGroup = body["selectedGroup"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        charAvatar = body["charAvatar"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                    )
                    buildJsonObject {
                        put("avatarId", JsonPrimitive(s.avatarId))
                        put("default", JsonPrimitive(s.isDefault))
                        put("locked", buildJsonObject {
                            put("chat", JsonPrimitive(s.lockedChat))
                            put("character", JsonPrimitive(s.lockedCharacter))
                        })
                    }
                }
                "temporary" -> {
                    val personas = body["personas"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                    val t = PersonaEngine.temporaryLockInfo(
                        userAvatar = body["userAvatar"]!!.jsonPrimitive.content,
                        chatPersona = body["chatPersona"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        defaultPersona = body["defaultPersona"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        personas = personas,
                    )
                    buildJsonObject {
                        put("isTemporary", JsonPrimitive(t.isTemporary))
                        put("hasDifferentChatLock", JsonPrimitive(t.hasDifferentChatLock))
                        put("hasDifferentDefaultLock", JsonPrimitive(t.hasDifferentDefaultLock))
                        put("info", JsonPrimitive(t.info))
                    }
                }
                "connected" -> JsonArray(PersonaEngine.connected(parseDescriptors(body), body["characterKey"]!!.jsonPrimitive.content).map { JsonPrimitive(it) })
                "connectionObj" -> PersonaEngine.connectionObj(
                    body["selectedGroup"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                    body["charAvatar"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                )?.let { buildJsonObject { put("type", JsonPrimitive(it.type)); put("id", JsonPrimitive(it.id)) } } ?: JsonNull
                "descriptor" -> {
                    val pu = body["powerUser"]!!.jsonObject
                    val defaults = PersonaDescriptor(
                        description = pu["persona_description"]?.jsonPrimitive?.content ?: "",
                        position = pu["persona_description_position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        depth = pu["persona_description_depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
                        role = pu["persona_description_role"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        lorebook = pu["persona_description_lorebook"]?.jsonPrimitive?.content ?: "",
                    )
                    val d = PersonaEngine.getOrCreateDescriptor(
                        body["userAvatar"]!!.jsonPrimitive.content,
                        mutableMapOf(),
                        defaults,
                    )
                    buildJsonObject {
                        put("description", JsonPrimitive(d.description))
                        put("position", JsonPrimitive(d.position))
                        put("depth", JsonPrimitive(d.depth))
                        put("role", JsonPrimitive(d.role))
                        put("lorebook", JsonPrimitive(d.lorebook))
                        put("connections", JsonArray(emptyList()))
                        put("title", JsonPrimitive(d.title))
                    }
                }
                "resolve" -> {
                    val r = PersonaEngine.resolve(
                        chatMetaPersona = body["chatMetaPersona"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        userAvatars = body["userAvatars"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        connectedPersonas = body["connectedPersonas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        defaultPersona = body["defaultPersona"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        allowMultiConnections = body["allowMultiConnections"]?.jsonPrimitive?.content == "true",
                        userAvatar = body["userAvatar"]!!.jsonPrimitive.content,
                        personaAutoLock = body["personaAutoLock"]?.jsonPrimitive?.content == "true",
                    )
                    buildJsonObject {
                        put("chatPersona", JsonPrimitive(r.chatPersona ?: ""))
                        put("connectType", r.connectType?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("unlockChat", JsonPrimitive(r.unlockChat))
                        put("clearDefault", JsonPrimitive(r.clearDefault))
                        put("willSwitch", JsonPrimitive(r.willSwitch))
                        put("autoLock", JsonPrimitive(r.autoLock))
                    }
                }
                else -> error("unknown method $method")
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun parseDescriptors(body: JsonObject): Map<String, PersonaDescriptor> {
        val pu = body["powerUser"]?.jsonObject
        val source = body["personaDescriptions"]?.jsonObject ?: pu?.get("persona_descriptions")?.jsonObject ?: JsonObject(emptyMap())
        return source.mapValues { (_, el) ->
            val o = el.jsonObject
            PersonaDescriptor(
                description = o["description"]?.jsonPrimitive?.content ?: "",
                connections = o["connections"]?.jsonArray?.map { PersonaConnection(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["id"]!!.jsonPrimitive.content) } ?: emptyList(),
            )
        } ?: emptyMap()
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
