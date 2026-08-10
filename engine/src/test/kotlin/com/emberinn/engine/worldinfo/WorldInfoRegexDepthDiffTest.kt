package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：world-info.js BUILDING PROMPT 的 regexDepth 计算。
 * fixture 由 scripts/diff/worldinfo-regex-depth-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class WorldInfoRegexDepthDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `regex depth matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/worldinfo-regex-depth.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val position = body.getValue("position").jsonPrimitive.intOrNull ?: error("position missing")
            val depth = body["depth"]?.jsonPrimitive?.intOrNull

            val actual = WorldInfoConstants.regexDepthOf(position, depth)
                ?.let { JsonPrimitive(it) } ?: JsonNull
            assertEquals("case $id", expected, actual)
        }
    }
}
