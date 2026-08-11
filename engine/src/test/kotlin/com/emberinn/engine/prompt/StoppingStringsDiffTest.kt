package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js getStoppingStrings + power-user.js getCustomStoppingStrings。
 * fixture 由 scripts/diff/stopping-strings-official.mjs 生成，禁止手改。
 */
class StoppingStringsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `stopping strings match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/stopping-strings.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonArray.map { it.jsonPrimitive.content }

            val actual = when (body.getValue("method").jsonPrimitive.content) {
                "custom" -> StoppingStringsEngine.customStoppingStrings(body.toCustomConfig())
                "get" -> StoppingStringsEngine.getStoppingStrings(
                    api = body["api"]?.jsonPrimitive?.content ?: "openai",
                    config = body.toStoppingConfig(),
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

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default

    private fun JsonObject.toCustomConfig(): CustomStoppingConfig = CustomStoppingConfig(
        rawJson = str("customRaw"),
        macro = bool("customMacro", true),
        ephemeral = this["ephemeral"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
        limit = int("limit"),
    )

    private fun JsonObject.toStoppingConfig(): StoppingStringsConfig {
        val name1 = str("name1", "User")
        val name2 = str("name2", "Char")
        val instruct = InstructSettings(
            enabled = bool("instructEnabled"),
            wrap = bool("instructWrap", true),
            macro = bool("instructMacro", true),
            stopSequence = str("instructStopSequence"),
            inputSequence = str("instructInputSequence"),
            outputSequence = str("instructOutputSequence"),
            firstOutputSequence = str("instructFirstOutputSequence"),
            lastOutputSequence = str("instructLastOutputSequence"),
            systemSequence = str("instructSystemSequence"),
            lastSystemSequence = str("instructLastSystemSequence"),
            sequencesAsStopStrings = bool("instructSequencesAsStopStrings", true),
        )
        val context = ContextSettings(
            chatStart = str("chatStart", "***"),
            exampleSeparator = str("exampleSeparator", "***"),
            useStopStrings = bool("useStopStrings", true),
            namesAsStopStrings = bool("namesAsStopStrings", true),
        )
        val groupId = str("groupId")
        val groupMemberNames = if (groupId.isEmpty()) {
            emptyList()
        } else {
            val groups = this["groups"]?.jsonArray.orEmpty()
            val group = groups.firstOrNull { it.jsonObject.str("id") == groupId }?.jsonObject
                ?: return StoppingStringsConfig()
            val members = group["members"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            val characters = this["characters"]?.jsonArray.orEmpty()
            members.mapNotNull { avatar ->
                characters.firstOrNull { it.jsonObject.str("avatar") == avatar }
                    ?.jsonObject?.str("name")
            }
        }
        return StoppingStringsConfig(
            isImpersonate = bool("isImpersonate"),
            isContinue = bool("isContinue"),
            namesAsStopStrings = bool("namesAsStopStrings", true),
            name1 = name1,
            name2 = name2,
            chatLastIsUser = bool("chatLastIsUser"),
            groupMemberNames = groupMemberNames,
            selectedGroup = groupId.isNotEmpty(),
            singleLine = bool("singleLine"),
            instruct = instruct,
            context = context,
            env = MacroEnv(user = name1, char = name2),
            custom = toCustomConfig(),
        )
    }
}
