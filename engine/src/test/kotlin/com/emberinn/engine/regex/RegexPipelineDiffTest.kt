package com.emberinn.engine.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * 官方行为差分：regex/engine.js getRegexedString（正则整体管线）。
 * fixture 由 scripts/diff/regex-pipeline-official.mjs 生成，禁止手改。
 */
class RegexPipelineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `regex pipeline matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/regex-pipeline.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content

            val scripts = body["scripts"]?.jsonArray?.map { el ->
                val o = el.jsonObject
                RegexPipelineScript(
                    findRegex = o["findRegex"]?.jsonPrimitive?.content ?: "",
                    replaceString = o["replaceString"]?.jsonPrimitive?.content ?: "",
                    trimStrings = o["trimStrings"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    disabled = o["disabled"]?.jsonPrimitive?.content == "true",
                    substituteRegex = o["substituteRegex"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    placement = o["placement"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() } ?: emptyList(),
                    markdownOnly = o["markdownOnly"]?.jsonPrimitive?.content == "true",
                    promptOnly = o["promptOnly"]?.jsonPrimitive?.content == "true",
                    runOnEdit = o["runOnEdit"]?.jsonPrimitive?.content != "false",
                    minDepth = o["minDepth"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
                    maxDepth = o["maxDepth"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
                )
            } ?: emptyList()

            val actual = RegexPipelineEngine.apply(
                raw = body["raw"]?.jsonPrimitive?.content ?: "",
                placement = body["placement"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                scripts = scripts,
                isMarkdown = body["isMarkdown"]?.jsonPrimitive?.content == "true",
                isPrompt = body["isPrompt"]?.jsonPrimitive?.content == "true",
                isEdit = body["isEdit"]?.jsonPrimitive?.content == "true",
                depth = body["depth"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
                disabledExtensions = body["disabledExtensions"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                characterOverride = body["characterOverride"]?.jsonPrimitive?.content,
            )
            assertEquals("case $id", expected, actual)
        }
    }
}
