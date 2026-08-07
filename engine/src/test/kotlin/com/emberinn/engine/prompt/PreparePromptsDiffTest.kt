package com.emberinn.engine.prompt

import com.emberinn.engine.macros.CharacterFields
import com.emberinn.engine.macros.MacroEnv
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
 * 官方行为差分：public/scripts/openai.js preparePromptsForChatCompletion。
 * fixture 由 scripts/diff/prepare-prompts-official.mjs 生成（oai_settings/substituteParams/
 * promptManager 打桩），禁止手改。
 */
class PreparePromptsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `prepare prompts matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/prepare-prompts.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val envObj = body.getValue("env").jsonObject
            val env = MacroEnv(
                user = envObj["user"]?.jsonPrimitive?.content ?: "",
                char = envObj["char"]?.jsonPrimitive?.content ?: "",
                character = CharacterFields(
                    personality = body["charPersonality"]?.jsonPrimitive?.content ?: "",
                    scenario = body["scenario"]?.jsonPrimitive?.content ?: "",
                ),
            )

            val actual = PromptAssembler.preparePromptsForChatCompletion(
                scenario = body["scenario"]?.jsonPrimitive?.content ?: "",
                charPersonality = body["charPersonality"]?.jsonPrimitive?.content ?: "",
                name2 = body["name2"]?.jsonPrimitive?.content ?: "",
                worldInfoBefore = body["worldInfoBefore"]?.jsonPrimitive?.content ?: "",
                worldInfoAfter = body["worldInfoAfter"]?.jsonPrimitive?.content ?: "",
                charDescription = body["charDescription"]?.jsonPrimitive?.content ?: "",
                quietPrompt = body["quietPrompt"]?.jsonPrimitive?.content ?: "",
                bias = body["bias"]?.jsonPrimitive?.content ?: "",
                extensionPrompts = parseExtensions(body["extensionPrompts"]?.jsonObject ?: JsonObject(emptyMap())),
                systemPromptOverride = body["systemPromptOverride"]?.jsonPrimitive?.content ?: "",
                jailbreakPromptOverride = body["jailbreakPromptOverride"]?.jsonPrimitive?.content ?: "",
                type = body["type"]?.jsonPrimitive?.content ?: "normal",
                userOrder = parseOrder(body["userOrder"]?.jsonArray),
                userPrompts = parsePrompts(body["userPrompts"]?.jsonArray),
                env = env,
                personaDescription = body["personaDescription"]?.jsonPrimitive?.content ?: "",
                personaInPrompt = body["personaInPrompt"]?.jsonPrimitive?.content == "true",
                impersonationPrompt = body["oai"]?.jsonObject?.get("impersonation_prompt")?.jsonPrimitive?.content
                    ?: body["impersonationPrompt"]?.jsonPrimitive?.content ?: "",
                personalityFormat = body["oai"]?.jsonObject?.get("personality_format")?.jsonPrimitive?.content ?: "",
                scenarioFormat = body["oai"]?.jsonObject?.get("scenario_format")?.jsonPrimitive?.content ?: "",
                groupNudge = body["oai"]?.jsonObject?.get("group_nudge_prompt")?.jsonPrimitive?.content ?: "",
                wiFormat = body["oai"]?.jsonObject?.get("wi_format")?.jsonPrimitive?.content ?: "{0}",
            )

            val actualJson = serialize(actual)
            assertEquals("case $id", canonical(normalizePrompts(expected)), canonical(normalizePrompts(actualJson)))
        }
    }

    private fun parseOrder(arr: JsonArray?): List<PromptOrderEntry> =
        arr?.map { el ->
            val o = el.jsonObject
            PromptOrderEntry(
                identifier = o["identifier"]!!.jsonPrimitive.content,
                enabled = o["enabled"]?.jsonPrimitive?.content != "false",
                injectionPosition = o["injection_position"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionDepth = o["injection_depth"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionOrder = o["injection_order"]?.jsonPrimitive?.content?.toIntOrNull(),
                role = o["role"]?.jsonPrimitive?.content,
            )
        } ?: emptyList()

    private fun parsePrompts(arr: JsonArray?): List<PromptItem> =
        arr?.map { el ->
            val o = el.jsonObject
            PromptItem(
                identifier = o["identifier"]!!.jsonPrimitive.content,
                name = o["name"]?.jsonPrimitive?.content ?: o["identifier"]!!.jsonPrimitive.content,
                content = o["content"]?.jsonPrimitive?.content ?: "",
                role = o["role"]?.jsonPrimitive?.content ?: "system",
                systemPrompt = o["system_prompt"]?.jsonPrimitive?.content != "false",
                marker = o["marker"]?.jsonPrimitive?.content == "true",
                enabled = o["enabled"]?.jsonPrimitive?.content != "false",
                injectionPosition = o["injection_position"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionDepth = o["injection_depth"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionOrder = o["injection_order"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionTrigger = o["injection_trigger"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                forbidOverrides = o["forbid_overrides"]?.jsonPrimitive?.content == "true",
                position = o["position"]?.jsonPrimitive?.content,
                extension = o["extension"]?.jsonPrimitive?.content == "true",
            )
        } ?: emptyList()

    private fun parseExtensions(obj: JsonObject): Map<String, ExtensionPrompt> =
        obj.mapValues { (key, el) ->
            val o = el.jsonObject
            val roleNum = o["role"]?.jsonPrimitive?.content?.toIntOrNull()
            val posNum = o["position"]?.jsonPrimitive?.content?.toIntOrNull()
            ExtensionPrompt(
                identifier = key,
                role = when (roleNum) { 0 -> "system"; 1 -> "user"; 2 -> "assistant"; else -> "system" },
                content = o["value"]?.jsonPrimitive?.content ?: "",
                position = when (posNum) { 0 -> "end"; 1 -> "in_chat"; 2 -> "start"; else -> "" },
            )
        }

    private fun serialize(items: PromptItems): JsonElement = buildJsonObject {
        put(
            "collection",
            JsonArray(
                items.collection.map { p ->
                    buildJsonObject {
                        put("identifier", JsonPrimitive(p.identifier))
                        put("name", p.name.asJson())
                        put("role", p.role.asJson())
                        put("content", JsonPrimitive(p.content))
                        put("system_prompt", p.systemPrompt.asJson())
                        put("marker", p.marker.asJson())
                        put("enabled", p.enabled.asJson())
                        put("injection_position", p.injectionPosition?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("injection_depth", p.injectionDepth?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("injection_order", p.injectionOrder?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("injection_trigger", JsonArray(p.injectionTrigger.map { JsonPrimitive(it) }))
                        put("forbid_overrides", p.forbidOverrides.asJson())
                        put("position", p.position.asJson())
                        put("extension", p.extension.asJson())
                    }
                },
            ),
        )
        put("overriddenPrompts", JsonArray(items.overriddenPrompts.map { JsonPrimitive(it) }))
    }

    private fun String?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
    private fun Boolean.asJson(): JsonElement = JsonPrimitive(this)

    /** 只比较行为相关字段：官方 Prompt 会丢失 marker/enabled 等元数据，null/undefined 与 Kotlin 默认值语义等价。 */
    private fun normalizePrompts(el: JsonElement): JsonElement {
        val root = el.jsonObject
        return buildJsonObject {
            put("collection", JsonArray(root.getValue("collection").jsonArray.map { normalizePrompt(it.jsonObject) }))
            put("overriddenPrompts", root["overriddenPrompts"] ?: JsonArray(emptyList()))
        }
    }

    private fun normalizePrompt(o: JsonObject): JsonElement = buildJsonObject {
        fun putOrNull(key: String, v: JsonElement?) = put(key, v ?: JsonNull)
        putOrNull("identifier", o["identifier"])
        putOrNull("content", o["content"])
        val role = o["role"]
        put(
            "role",
            if (role == null || role is JsonNull || (role is JsonPrimitive && role.content == "system")) JsonPrimitive("system")
            else role,
        )
        val sys = o["system_prompt"]
        put(
            "system_prompt",
            if (sys == null || sys is JsonNull || (sys is JsonPrimitive && sys.content == "true")) JsonPrimitive("system")
            else JsonPrimitive("user"),
        )
        putOrNull("injection_position", o["injection_position"])
        putOrNull("injection_depth", o["injection_depth"])
        putOrNull("injection_order", o["injection_order"])
        val marker = o["marker"]
        put(
            "marker",
            if (marker == null || marker is JsonNull || (marker is JsonPrimitive && marker.content == "false")) JsonPrimitive("false")
            else marker,
        )
        putOrNull("position", o["position"])
        putOrNull("extension", o["extension"])
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}
