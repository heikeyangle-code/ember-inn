package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js cleanUpMessage / cleanGroupMessage + power-user.js fixMarkdown。
 * fixture 由 scripts/diff/cleanup-official.mjs 生成，禁止手改。
 */
class CleanUpDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `cleanup matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/cleanup.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content

            val actual = when (body.str("method")) {
                "fixMarkdown" -> FixMarkdown.fix(body.str("text"), body.bool("forDisplay"))
                "cleanGroup" -> CleanUpMessageEngine.cleanGroupMessage(
                    getMessage = body.str("text"),
                    memberNames = body.memberNames(),
                    currentSpeakerName = body.str("name2", "Char"),
                    trimmingEnabled = !body.bool("disableGroupTrimming"),
                )
                "clean" -> CleanUpMessageEngine.clean(
                    getMessage = body.str("text"),
                    config = body.toCleanUpConfig(),
                )
                else -> error("unknown method in case $id")
            }

            assertEquals("case $id", expected, actual)
        }
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.memberNames(): List<String> {
        val groupId = str("groupId")
        if (groupId.isEmpty()) return emptyList()
        val groups = this["groups"]?.jsonArray.orEmpty()
        val group = groups.firstOrNull { it.jsonObject.str("id") == groupId }?.jsonObject ?: return emptyList()
        val members = group["members"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val characters = this["characters"]?.jsonArray.orEmpty()
        return members.mapNotNull { avatar ->
            characters.firstOrNull { it.jsonObject.str("avatar") == avatar }
                ?.jsonObject?.str("name")
        }
    }

    private fun JsonObject.toCleanUpConfig(): CleanUpConfig {
        val mainApi = str("mainApi", "openai")
        val instructEnabled = bool("instructEnabled")
        return CleanUpConfig(
            userPromptBias = this["userPromptBias"]?.jsonPrimitive?.content,
            isImpersonate = bool("isImpersonate"),
            isContinue = bool("isContinue"),
            displayIncompleteSentences = bool("displayIncompleteSentences"),
            stoppingStrings = this["stoppingStrings"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
            includeUserPromptBias = bool("includeUserPromptBias", true),
            trimNames = bool("trimNames", true),
            trimWrongNames = bool("trimWrongNames", true),
            collapseNewlines = bool("collapseNewlines"),
            allowName1Display = bool("allowName1Display", true),
            allowName2Display = bool("allowName2Display", true),
            name1 = str("name1", "User"),
            name2 = str("name2", "Char"),
            isInstruct = instructEnabled && mainApi != "openai",
            instructStopSequence = str("instructStopSequence"),
            instructInputSequence = str("instructInputSequence"),
            instructOutputSequence = str("instructOutputSequence"),
            instructLastOutputSequence = str("instructLastOutputSequence"),
            instructSequencesAsStopStrings = bool("instructSequencesAsStopStrings"),
            autoFixMarkdown = bool("autoFixMarkdown"),
            trimSentences = bool("trimSentences"),
            trimSpaces = bool("trimSpaces"),
            hasReasoningPrefix = bool("hasReasoningPrefix"),
            groupMemberNames = memberNames(),
            groupTrimmingEnabled = !bool("disableGroupTrimming"),
        )
    }
}
