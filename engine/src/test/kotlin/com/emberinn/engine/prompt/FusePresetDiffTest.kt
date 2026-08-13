package com.emberinn.engine.prompt

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
 * 官方 /preset 选择名差分：exact + Fuse.js 7.1 模糊回退。
 * fixture 由 scripts/diff/preset-fuzzy-official.mjs 用真实 fuse.js@7.1.0 生成，禁止手改。
 */
class FusePresetDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun strOrNull(el: JsonElement?): String? =
        el?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    @Test
    fun `preset name selection matches official Fuse fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/preset-fuzzy.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val list = args.getValue("list").jsonArray.map { it.jsonPrimitive.content }
            val query = strOrNull(args["query"]) ?: ""
            val expected = strOrNull(case["expected"])

            val actual = FusePresetSearch.selectPresetName(list, query)
            assertEquals("case $id", expected, actual)
        }
    }
}
