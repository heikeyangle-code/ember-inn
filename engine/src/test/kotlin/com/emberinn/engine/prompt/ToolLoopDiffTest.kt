package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js 工具循环决策（canPerformToolCalls/shouldDeleteMessage/
 * shouldStopGeneration/递归）+ tool-calling.js canPerformToolCalls。
 * fixture 由 scripts/diff/tool-loop-official.mjs 生成，禁止手改。
 */
class ToolLoopDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool loop decision matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/tool-loop.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val c = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            val actual = ToolLoopPlanner.decide(
                dryRun = c["dryRun"]?.jsonPrimitive?.content == "true",
                type = c["type"]?.jsonPrimitive?.content ?: "normal",
                depth = c["depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                recurseLimit = c["recurseLimit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5,
                toolCallingSupported = c["toolCallingSupported"]?.jsonPrimitive?.content != "false",
                isStreaming = c["isStreaming"]?.jsonPrimitive?.content == "true",
                isStreamFinished = c["isStreamFinished"]?.jsonPrimitive?.content != "false",
                isStreamWithToolCalls = c["isStreamWithToolCalls"]?.jsonPrimitive?.content == "true",
                hasToolCalls = c["hasToolCalls"]?.jsonPrimitive?.content == "true",
                lastMessageExists = c["lastMessageExists"]?.jsonPrimitive?.content != "false",
                lastMessageMes = c["lastMessageMes"]?.jsonPrimitive?.content ?: "",
                hasReasoning = c["hasReasoning"]?.jsonPrimitive?.content == "true",
                streamingResult = c["streamingResult"]?.jsonPrimitive?.content ?: "",
                invocationCount = c["invocationCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                stealthCalls = c["stealthCalls"]?.jsonPrimitive?.content == "true",
            )
            val actualJson = JsonObject(
                linkedMapOf(
                    "canPerformToolCalls" to JsonPrimitive(actual.canPerformToolCalls),
                    "shouldDeleteMessage" to JsonPrimitive(actual.shouldDeleteMessage),
                    "shouldStopGeneration" to JsonPrimitive(actual.shouldStopGeneration),
                    "shouldRecurse" to JsonPrimitive(actual.shouldRecurse),
                    "nextDepth" to JsonPrimitive(actual.nextDepth),
                ),
            )
            assertEquals("case $id", canonical(expected), canonical(actualJson))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        is JsonPrimitive -> {
            if (!el.isString) {
                val d = el.content.toDoubleOrNull()
                if (d != null && d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    JsonPrimitive(d.toLong().toString())
                } else if (d != null) {
                    JsonPrimitive(d.toString().lowercase())
                } else {
                    el
                }
            } else {
                el
            }
        }
        else -> el
    }
}
