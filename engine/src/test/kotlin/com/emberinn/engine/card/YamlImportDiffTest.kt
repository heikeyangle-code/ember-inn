package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：characters.js importFromYaml。
 * fixture 由 scripts/diff/yaml-import-official.mjs 生成（fs/写盘/时间打桩），禁止手改。
 */
class YamlImportDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `yaml import matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/yaml-import.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val yamlText = body.getValue("yaml").jsonPrimitive.content
            val expected = case.getValue("expected")

            val actual = json.parseToJsonElement(
                YamlImporter.import(
                    yamlText.toByteArray(Charsets.UTF_8),
                    now = "2026-08-08T00:00:00.000Z",
                    chatNow = "2026-08-08@00h00m00s000ms",
                ),
            )
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    /** 官方 JSON 键顺序取决于 JS 对象插入序，与语义无关；差分只比较值。 */
    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }

}
