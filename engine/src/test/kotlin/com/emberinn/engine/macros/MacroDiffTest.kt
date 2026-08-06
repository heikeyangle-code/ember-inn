package com.emberinn.engine.macros

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/macros-official.mjs 从官方
 * MacroEngine.e2e.js 提取（环境固定 name1=User / name2=Character、空变量表）。
 */
class MacroDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `macro outputs match official e2e fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/macros.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("input").jsonPrimitive.content
            val expected = case.getValue("expected").jsonPrimitive.content
            val local = MemoryVariableStore()
            val global = MemoryVariableStore()
            (case["local"] as? JsonObject)?.forEach { (k, v) -> local.set(k, v.jsonPrimitive.content) }
            (case["global"] as? JsonObject)?.forEach { (k, v) -> global.set(k, v.jsonPrimitive.content) }
            val env = MacroEnv(user = "User", char = "Character", local = local, global = global)
            val actual = MacroEngine.substitute(input, env)
            assertEquals("case $id", expected, actual)
        }
    }
}
