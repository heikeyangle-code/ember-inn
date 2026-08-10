package com.emberinn.engine.macros

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：core-macros.js {{outlet::key}} 宏。
 * fixture 由 scripts/diff/outlet-macro-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class OutletMacroDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `outlet macro matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/outlet-macro.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.contentOrNull ?: ""

            val key = body.getValue("key").jsonPrimitive.contentOrNull ?: ""
            // 官方 extension_prompts 以 customWIOutlet_{key} 为键，引擎 env.outlets 以裸 key 为键
            val outlets = (body["prompts"]?.jsonObject ?: error("prompts missing"))
                .mapKeys { (id, _) -> id.removePrefix("customWIOutlet_") }
                .mapValues { (_, v) -> v.jsonObject["value"]?.jsonPrimitive?.contentOrNull ?: "" }
            val env = MacroEnv(user = "User", char = "Character", outlets = outlets)
            val actual = MacroEngine.substitute("{{outlet::$key}}", env)
            assertEquals("case $id", expected, actual)
        }
    }
}
