package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 差分（官方用 js-yaml 'yaml' 包）。
 * fixture 由 scripts/diff/yaml-merge-official.mjs（官方函数逐字）生成，禁止手改。
 */
class YamlMergeDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `yaml merge and exclude match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/yaml-merge.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonObject.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("input").jsonObject
            val obj = input.getValue("obj").jsonObject
            val yaml = input.getValue("yaml").jsonPrimitive.content
            val expected = case.getValue("output").jsonObject
            val actual = if (id.startsWith("merge")) {
                YamlMerge.merge(obj, yaml)
            } else {
                YamlMerge.excludeKeys(obj, yaml)
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
