package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：world-info.js parseRegexFromString。
 * fixture 由 scripts/diff/regex-parse-official.mjs 生成，禁止手改。
 */
class RegexParseDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `regex parse matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/regex-parse.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("args").jsonObject.getValue("input").jsonPrimitive.content
            val expected = case.getValue("expected")

            val regex = WorldRegexUtils.parse(input)
            val actual = if (regex == null) {
                JsonNull
            } else {
                kotlinx.serialization.json.buildJsonObject {
                    // JS RegExp.source 会把 / 序列化为 \/，Kotlin 端做同样转义后对比（语义一致）
                    put("source", kotlinx.serialization.json.JsonPrimitive(regex.pattern.replace("/", "\\/")))
                    put("flags", kotlinx.serialization.json.JsonPrimitive(flagsOf(regex)))
                }
            }
            assertEquals("case $id", expected, actual)
        }
    }

    private fun flagsOf(regex: Regex): String = buildString {
        // JS flags 按字母序：g i m s u y（本实现仅 i/m/s）
        if (RegexOption.IGNORE_CASE in regex.options) append('i')
        if (RegexOption.MULTILINE in regex.options) append('m')
        if (RegexOption.DOT_MATCHES_ALL in regex.options) append('s')
    }
}
