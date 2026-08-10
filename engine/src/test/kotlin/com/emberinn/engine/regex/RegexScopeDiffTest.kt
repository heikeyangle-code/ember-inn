package com.emberinn.engine.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 差分：regex/engine.js getRegexScripts + getScriptsByType 的全局/预设/该卡分桶与 allowedOnly 过滤。
 * fixture 由 scripts/diff/regex-scope-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class RegexScopeDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `regex scope resolution matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/regex-scope.json"))
        val fixture = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = fixture["cases"]!!.jsonArray
        var count = 0
        for (case in cases) {
            val id = case.jsonObject["id"]!!.jsonPrimitive.content
            val body = case.jsonObject["args"]!!.jsonObject["body"]!!.jsonObject
            val expected = case.jsonObject["expected"]!!.jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" }

            val actual = RegexScopeResolver.resolve(
                global = scripts(body["global"]),
                preset = scripts(body["preset"]),
                scoped = scripts(body["scoped"]),
                allowedOnly = body["allowedOnly"]?.jsonPrimitive?.content == "true",
                scopedAllowed = (body["scopedAllowed"] as? JsonArray)?.any { it.jsonPrimitive.content == (body["avatar"]?.jsonPrimitive?.content ?: "chara") } == true,
                presetAllowed = (body["presetAllowed"] as? JsonArray)?.any { it.jsonPrimitive.content == "preset" } == true,
            ).map { it.scriptName }
            assertEquals("case $id", expected, actual)
            count++
        }
        assertTrue("expected >= 7 cases, got $count", count >= 7)
    }

    private fun scripts(el: kotlinx.serialization.json.JsonElement?): List<RegexPipelineScript> =
        (el as? JsonArray)?.mapNotNull { item ->
            val o = item.jsonObject
            o["scriptName"]?.jsonPrimitive?.contentOrNull?.let { name ->
                RegexPipelineScript(findRegex = "", replaceString = "", scriptName = name)
            }
        } ?: emptyList()
}
