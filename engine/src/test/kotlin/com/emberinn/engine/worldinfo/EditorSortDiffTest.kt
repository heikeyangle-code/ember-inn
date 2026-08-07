package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：sortWorldInfoEntries（world-info.js）。
 * fixture 由 scripts/diff/editor-sort-official.mjs 生成，禁止手改。
 */
class EditorSortDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `editor sort matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/editor-sort.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val customSort = args.getValue("customSort").jsonObject
            val entries = args.getValue("entries").jsonArray.map { it.jsonObject }
                .map { e ->
                    WorldInfoEntry(
                        world = "w",
                        uid = e["uid"]!!.jsonPrimitive.content.toInt(),
                        order = e["order"]!!.jsonPrimitive.content.toInt(),
                        name = e["name"]?.jsonPrimitive?.content ?: "",
                        content = e["content"]?.jsonPrimitive?.content ?: "",
                        constant = e["constant"]?.jsonPrimitive?.content == "true",
                        disable = e["disable"]?.jsonPrimitive?.content == "true",
                        displayIndex = e["displayIndex"]?.jsonPrimitive?.content?.toIntOrNull(),
                    )
                }
            val rule = customSort["sortRule"]?.jsonPrimitive?.content ?: "custom"
            val order = customSort["sortOrder"]?.jsonPrimitive?.content ?: "asc"
            val field = customSort["sortField"]?.jsonPrimitive?.content ?: "uid"
            val expected = case.getValue("expected").jsonArray.map { it.jsonPrimitive.content.toInt() }

            val actual = WorldInfoEditorSort.sort(entries, rule = rule, order = order, field = field)
            assertEquals("case $id", expected, actual.map { it.uid })
        }
    }
}
