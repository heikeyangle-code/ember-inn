package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：tool-calling.js ToolManager.parseToolCalls。
 * fixture 由 scripts/diff/tool-calls-official.mjs 生成，禁止手改。
 */
class ToolCallDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool calls match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/tool-calls.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val chunks = body["chunks"]?.jsonArray
                ?: listOfNotNull(body["parsed"])
            val toolSignatures = body["toolSignatures"]?.jsonObject
                ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                .orEmpty()
            val accumulator = ToolCallAccumulator(
                toolCallingSupported = body["supported"]?.jsonPrimitive?.content != "false",
            )
            for (chunk in chunks) {
                accumulator.parse(chunk, toolSignatures)
            }
            assertEquals("case $id", expected, accumulator.snapshot())
        }
    }
}
