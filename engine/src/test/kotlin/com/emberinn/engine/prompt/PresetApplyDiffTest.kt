package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * 官方行为差分：预设应用全链纯逻辑（类型识别/多区段校验/context/instruct/sysprompt/reasoning/
 * chat-completion 应用与迁移/保存过滤/名字匹配）。
 * fixture 由 scripts/diff/preset-apply-official.mjs 生成，禁止手改。
 */
class PresetApplyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }

    private fun strOrNull(el: JsonElement?): String? =
        el?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    @Test
    fun `preset apply engine matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/preset-apply.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val method = body.getValue("method").jsonPrimitive.content

            val actual: JsonElement = when (method) {
                "detectLegacy" -> PresetApplyEngine.detectLegacyImportType(
                    body["data"]?.takeUnless { it is JsonNull },
                )?.let { JsonPrimitive(it) } ?: JsonNull

                "isPossibly" -> {
                    val type = body.getValue("type").jsonPrimitive.content
                    val data = body["data"]?.takeUnless { it is JsonNull }
                    val result: Boolean? = when (type) {
                        "instruct" -> PresetApplyEngine.isPossiblyInstructData(data)
                        "context" -> PresetApplyEngine.isPossiblyContextData(data)
                        "sysprompt" -> PresetApplyEngine.isPossiblySystemPromptData(data)
                        "preset" -> PresetApplyEngine.isPossiblyTextCompletionData(data)
                        "reasoning" -> PresetApplyEngine.isPossiblyReasoningData(data)
                        "srw" -> PresetApplyEngine.isPossiblyStartReplyWithData(data)
                        else -> error("unknown type $type")
                    }
                    result?.let { JsonPrimitive(it) } ?: JsonNull
                }

                "masterSectionsValid" ->
                    PresetApplyEngine.masterSectionsValid(body["data"]?.jsonObject ?: JsonObject(emptyMap()))

                "applyContext" -> PresetApplyEngine.applyContextPresetJson(
                    body.getValue("powerUser").jsonObject,
                    body.getValue("preset").jsonObject,
                )

                "compileContext" -> PresetApplyEngine.getContextSettingsCompiled(body.getValue("powerUser").jsonObject)

                "autoFixStory" -> PresetApplyEngine.autoFixStoryString(
                    body["context"]?.takeUnless { it is JsonNull }?.jsonObject,
                ) ?: JsonNull

                "applyInstruct" -> PresetApplyEngine.applyInstructPresetJson(
                    body.getValue("powerUser").jsonObject,
                    body.getValue("preset").jsonObject,
                )

                "migrateInstruct" -> PresetApplyEngine.migrateInstructModeSettings(body.getValue("settings").jsonObject)

                "applySysprompt" -> PresetApplyEngine.applySyspromptPresetJson(
                    body.getValue("powerUser").jsonObject,
                    body.getValue("preset").jsonObject,
                )

                "applyReasoning" -> PresetApplyEngine.applyReasoningPresetJson(
                    body.getValue("powerUser").jsonObject,
                    body.getValue("template").jsonObject,
                )

                "migrateChatCompletion" ->
                    PresetApplyEngine.migrateChatCompletionSettings(body.getValue("settings").jsonObject)

                "applyChatCompletion" -> PresetApplyEngine.applyChatCompletionPresetJson(
                    body.getValue("settings").jsonObject,
                    body.getValue("preset").jsonObject,
                    body.getValue("bindPresetToConnection").jsonPrimitive.content == "true",
                )

                "chatCompletionBody" ->
                    PresetApplyEngine.getChatCompletionPresetBody(body.getValue("settings").jsonObject)

                "filterPresetSettings" -> PresetApplyEngine.filterPresetSettings(
                    settings = body.getValue("settings").jsonObject,
                    apiId = body.getValue("apiId").jsonPrimitive.content,
                    name = body["name"]?.jsonPrimitive?.content ?: "",
                    currentName = body["currentName"]?.jsonPrimitive?.content ?: "",
                    isAdvancedFormatting = body.getValue("isAdvancedFormatting").jsonPrimitive.content == "true",
                    genAmount = body.getValue("extraGen").jsonObject.getValue("genamt").jsonPrimitive.content.toInt(),
                    maxLength = body.getValue("extraGen").jsonObject.getValue("max_length").jsonPrimitive.content.toInt(),
                )

                "matchExact" -> PresetApplyEngine.matchPresetNameExact(
                    body.getValue("names").jsonArray.map { it.jsonPrimitive.content },
                    body["name"]?.jsonPrimitive?.content ?: "",
                )?.let { JsonPrimitive(it) } ?: JsonNull

                "findMatching" -> PresetApplyEngine.findMatchingTemplateName(
                    body["name"]?.jsonPrimitive?.content ?: "",
                    body.getValue("candidateNames").jsonArray.map { it.jsonPrimitive.content },
                )?.let { JsonPrimitive(it) } ?: JsonNull

                "applyTextgen" -> PresetApplyEngine.applyTextgenPreset(
                    body.getValue("settings").jsonObject,
                    body.getValue("preset").jsonObject,
                    body["orders"]?.jsonObject ?: JsonObject(emptyMap()),
                )

                "applyNovel" -> PresetApplyEngine.applyNovelPreset(
                    body.getValue("settings").jsonObject,
                    body.getValue("preset").jsonObject,
                    body["defaults"]?.jsonObject ?: JsonObject(emptyMap()),
                )

                "applyKobold" -> PresetApplyEngine.applyKoboldPreset(
                    body.getValue("settings").jsonObject,
                    body.getValue("preset").jsonObject,
                    body["keys"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    body["defaults"]?.jsonObject ?: JsonObject(emptyMap()),
                    body["sliderKeys"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                )

                "generationParams" -> {
                    val state = body.getValue("state").jsonObject
                    val r = PresetApplyEngine.applyGenerationParamsFromPreset(
                        body.getValue("preset").jsonObject,
                        state.getValue("amountGen").jsonPrimitive.content.toInt(),
                        state.getValue("maxContext").jsonPrimitive.content.toInt(),
                    )
                    buildJsonObject {
                        put("needsUnlock", JsonPrimitive(r.needsUnlock))
                        put("amountGen", JsonPrimitive(r.amountGen))
                        put("maxContext", JsonPrimitive(r.maxContext))
                    }
                }

                "autoSelect" -> PresetApplyEngine.autoSelectPresetDecision(
                    body["charName"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                    body["candidateNames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    body["selectedPreset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                )?.let { JsonPrimitive(it) } ?: JsonNull

                "sensitiveFields" -> JsonArray(
                    PresetApplyEngine.detectSensitivePresetFields(body["preset"]?.jsonObject ?: JsonObject(emptyMap()))
                        .map { JsonPrimitive(it) },
                )

                else -> error("unknown method $method")
            }

            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }
}
