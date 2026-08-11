package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.EmEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：script.js Generate 世界书 EM 示例 unshift/push（baseChatReplace + parseMesExamples）。 */
class EmExamplesDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `world example assembly matches official fixtures`() {
        val root = json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/diff/em-examples.json")).readText(),
        ).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val id = c.getValue("id").jsonPrimitive.content
            val body = c.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = c.getValue("expected").jsonArray.map { it.jsonPrimitive.content }
            val emEntries = body["emEntries"]!!.jsonArray.map { e ->
                val o = e.jsonObject
                EmEntry(
                    position = o["position"]?.jsonPrimitive?.intOrNull ?: 1,
                    content = o["content"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
            val actual = ExampleAssembler.assembleWithWorldExamples(
                baseMesExamples = body["base"]?.jsonPrimitive?.contentOrNull ?: "",
                emEntries = emEntries,
                substitute = { it.replace("{{user}}", "User").replace("{{char}}", "Char") },
                collapseNewlines = body["collapse"]?.jsonPrimitive?.booleanOrNull ?: false,
                isInstruct = body["isInstruct"]?.jsonPrimitive?.booleanOrNull ?: false,
                exampleSeparator = body["exampleSeparator"]?.jsonPrimitive?.contentOrNull ?: "",
                mainApiIsOpenAi = body["mainApiIsOpenAi"]?.jsonPrimitive?.booleanOrNull ?: true,
            )
            assertEquals("case $id", expected, actual)
        }
    }
}
