package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/instruct-official.mjs 从官方源码生成，
 * 这里用同一输入跑 Kotlin 引擎并逐 case 比对输出。
 */
class InstructModeDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `instruct outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/instruct.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fn = case.getValue("fn").jsonPrimitive.content
            val instruct = json.decodeFromJsonElement<InstructSettings>(case.getValue("instruct"))
            val context = json.decodeFromJsonElement<ContextSettings>(case.getValue("context"))
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            val actual = runCase(fn, instruct, context, args)
            assertEquals("case $id ($fn)", expected, actual)
        }
    }

    private fun runCase(
        fn: String,
        instruct: InstructSettings,
        context: ContextSettings,
        args: JsonObject,
    ): JsonElement {
        val name1 = argStr(args, "name1")
        val name2 = argStr(args, "name2")
        val env = MacroEnv(user = name1, char = name2)
        val selectedGroup = argBool(args, "selectedGroup", false)

        return when (fn) {
            "formatChat" -> JsonPrimitive(
                InstructMode.formatChat(
                    name = argStr(args, "name"),
                    mes = argStr(args, "mes"),
                    isUser = argBool(args, "isUser"),
                    isNarrator = argBool(args, "isNarrator"),
                    forceAvatar = argStr(args, "forceAvatar"),
                    name1 = name1,
                    name2 = name2,
                    forceOutputSequence = ForceOutputSequence.fromValue(argInt(args, "forceOutputSequence")),
                    instruct = instruct,
                    env = env,
                    selectedGroup = selectedGroup,
                ),
            )

            "formatStoryString" -> JsonPrimitive(
                InstructMode.formatStoryString(
                    storyString = argStr(args, "storyString"),
                    context = context,
                    instruct = instruct,
                    env = env,
                ),
            )

            "formatExamples" -> JsonArray(
                InstructMode.formatExamples(
                    mesExamplesArray = argStrList(args, "mesExamplesArray"),
                    name1 = name1,
                    name2 = name2,
                    context = context,
                    instruct = instruct,
                    env = env,
                    selectedGroup = selectedGroup,
                    groupBotNames = argStrList(args, "groupBotNames"),
                ).map { JsonPrimitive(it) },
            )

            "formatPrompt" -> JsonPrimitive(
                InstructMode.formatPrompt(
                    name = argStr(args, "name"),
                    isImpersonate = argBool(args, "isImpersonate"),
                    promptBias = argStr(args, "promptBias"),
                    name1 = name1,
                    name2 = name2,
                    isQuiet = argBool(args, "isQuiet"),
                    isQuietToLoud = argBool(args, "isQuietToLoud"),
                    instruct = instruct,
                    env = env,
                    selectedGroup = selectedGroup,
                ),
            )

            "stoppingSequences" -> JsonArray(
                InstructMode.stoppingSequences(
                    name1 = name1,
                    name2 = name2,
                    instruct = instruct,
                    context = context,
                    env = env,
                ).map { JsonPrimitive(it) },
            )

            "createRawPrompt" -> {
                val prompt = args.getValue("prompt").jsonArray.map { m ->
                    val o = m.jsonObject
                    PromptMessage(
                        role = o.getValue("role").jsonPrimitive.content,
                        content = o.getValue("content").jsonPrimitive.content,
                    )
                }
                val result = InstructMode.createRawPrompt(
                    prompt = prompt,
                    api = argStr(args, "api"),
                    instructOverride = argBool(args, "instructOverride"),
                    quietToLoud = argBool(args, "quietToLoud"),
                    systemPrompt = argStr(args, "systemPrompt"),
                    prefill = argStr(args, "prefill"),
                    instruct = instruct,
                    context = context,
                    env = env,
                    name1 = name1,
                    name2 = name2,
                )
                when (result) {
                    is InstructMode.RawPrompt.Text -> JsonPrimitive(result.text)
                    is InstructMode.RawPrompt.Messages -> JsonArray(result.messages.map { m ->
                        buildJsonObject {
                            put("role", m.role)
                            put("content", m.content)
                            m.name?.let { put("name", it) }
                        }
                    })
                }
            }

            else -> error("unknown fn: $fn")
        }
    }

    private fun argStr(args: JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun argBool(args: JsonObject, key: String, def: Boolean = false): Boolean =
        args[key]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } ?: def

    private fun argInt(args: JsonObject, key: String): Int =
        args[key]?.jsonPrimitive?.let { it.intOrNull ?: (it.content.toIntOrNull() ?: 0) } ?: 0

    private fun argStrList(args: JsonObject, key: String): List<String> =
        args[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
