package com.emberinn.engine.macros

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：MacroEngine.trimScopedContent（作用域宏内容裁剪）。
 * fixture 由 scripts/diff/macro-trim-official.mjs 生成，禁止手改。
 */
class MacroTrimDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `trim scoped content matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/macro-trim.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val content = args.getValue("content").jsonPrimitive.content
            val trimIndent = args.getValue("options").jsonObject["trimIndent"]?.jsonPrimitive?.content != "false"
            val expected = case.getValue("expected").jsonPrimitive.content

            val actual = if (trimIndent) {
                MacroEngine.trimScopedContent(content)
            } else {
                MacroEngine.trimScopedContent(content, trimIndent = false)
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
