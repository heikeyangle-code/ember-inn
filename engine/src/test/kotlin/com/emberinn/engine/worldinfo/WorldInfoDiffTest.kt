package com.emberinn.engine.worldinfo

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
 * 官方行为差分测试：fixture 由 scripts/diff/worldinfo-official.mjs 生成，
 * 覆盖 WorldInfoBuffer.matchKeys / getScore 与 parseDecorators。
 */
class WorldInfoDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `worldinfo outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/worldinfo.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fn = case.getValue("fn").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")
            val actual = when (fn) {
                "matchKeys" -> JsonPrimitive(runMatchKeys(args))
                "getScore" -> JsonPrimitive(runGetScore(args))
                "parseDecorators" -> {
                    val (decorators, content) = WorldInfoDecorators.parse(
                        args.getValue("content").jsonPrimitive.content,
                    )
                    JsonArray(decorators.map { JsonPrimitive(it) } + JsonPrimitive(content))
                }
                else -> error("unknown fn: $fn")
            }
            assertEquals("case $id ($fn)", expected, actual)
        }
    }

    private fun runMatchKeys(args: JsonObject): Boolean {
        val buffer = buffer(args)
        return buffer.matchKeys(
            haystack = str(args, "haystack"),
            needle = str(args, "needle"),
            entry = entry(args),
        )
    }

    private fun runGetScore(args: JsonObject): Int {
        return buffer(args).getScore(entry(args), WorldInfoConstants.STATE_INITIAL)
    }

    private fun buffer(args: JsonObject) = WorldInfoBuffer(
        messages = strList(args, "messages"),
        global = GlobalScanData(),
        settings = WorldInfoSettings(depth = 4),
    )

    private fun entry(args: JsonObject) = WorldInfoEntry(
        world = "w",
        uid = 0,
        order = 0,
        keys = strList(args, "key"),
        keySecondary = strList(args, "keysecondary"),
        selectiveLogic = args["selectiveLogic"]?.jsonPrimitive?.let { it.intOrNull ?: it.content.toIntOrNull() },
        caseSensitive = args["caseSensitive"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") },
        matchWholeWords = args["matchWholeWords"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") },
    )

    private fun str(args: JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.content ?: ""

    private fun strList(args: JsonObject, key: String): List<String> =
        args[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
