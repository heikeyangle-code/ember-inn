package com.emberinn.engine.macros

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 {{pick}} 确定性差分：fixture 由 scripts/diff/pick-official.mjs 用官方
 * seedrandom@3.0.5 生成（chatIdHash=123456、contentHash/offset 同公式）。
 */
class PickDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pick outputs match official seedrandom fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/pick.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("input").jsonPrimitive.content
            val expected = case.getValue("expected").jsonPrimitive.content
            val chatIdHash = case.getValue("chatIdHash").jsonPrimitive.content.toLong()
            val env = MacroEnv(user = "User", char = "Character", chatIdHash = chatIdHash)
            val actual = MacroEngine.substitute(input, env)
            assertEquals("case $id", expected, actual)
        }
    }
}
