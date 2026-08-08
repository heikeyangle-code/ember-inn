package com.emberinn.engine.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/regex-official.mjs 生成。
 * 宏替换桩与官方 substituteParams 一致：{{user}}/{{char}}。
 */
class RegexDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `regex outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/regex.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content

            val script = RegexScript(
                findRegex = str(args, "findRegex"),
                replaceString = str(args, "replaceString"),
                trimStrings = strList(args, "trimStrings"),
                disabled = bool(args, "disabled"),
                substituteRegex = int(args, "substituteRegex"),
            )
            val actual = RegexEngine.apply(script, str(args, "raw"), substitute = { text ->
                text.replace("{{user}}", "Alice").replace("{{char}}", "Bob")
            })
            assertEquals("case $id", expected, actual)
        }
    }

    private fun str(args: kotlinx.serialization.json.JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.content ?: ""

    private fun bool(args: kotlinx.serialization.json.JsonObject, key: String): Boolean =
        args[key]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } ?: false

    private fun int(args: kotlinx.serialization.json.JsonObject, key: String): Int =
        args[key]?.jsonPrimitive?.let { it.intOrNull ?: (it.content.toIntOrNull() ?: 0) } ?: 0

    private fun strList(args: kotlinx.serialization.json.JsonObject, key: String): List<String> =
        args[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
