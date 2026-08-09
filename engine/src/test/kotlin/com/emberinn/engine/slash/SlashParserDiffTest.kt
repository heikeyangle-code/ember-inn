package com.emberinn.engine.slash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 差分：SlashCommandParser 参数解析核心（parseCommand/named/unnamed/quoted/list/value + 转义）。
 * fixture 由 scripts/diff/slash-parser-official.mjs 从官方源码逐字提取生成，禁止手改。
 */
class SlashParserDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parser core matches official`() {
        val resource = checkNotNull(javaClass.getResource("/diff/slash-parser.json"))
        val fixture = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = fixture["cases"]!!.jsonArray
        var count = 0
        for (case in cases) {
            val id = case.jsonObject["id"]!!.jsonPrimitive.content
            val body = case.jsonObject["args"]!!.jsonObject["body"]!!.jsonObject
            val text = body["text"]!!.jsonPrimitive.content
            val strict = body["strict"]?.jsonPrimitive?.content == "true"
            val expected = case.jsonObject["expected"]!!.jsonObject

            val inv = SlashParser.parse(
                line = text,
                strictEscaping = strict,
                rawQuotesFor = { name -> name == "echo" },
                splitFor = { name ->
                    when (name) {
                        "let", "setvar" -> true to 1
                        "qr-arg" -> true to 2
                        else -> false to null
                    }
                },
            )
            val actual = buildJsonObject {
                put("name", JsonPrimitive(inv.name))
                put(
                    "named",
                    JsonObject(inv.namedArgs.mapValues { (_, v) -> JsonPrimitive(v) }),
                )
                put("unnamed", JsonArray(inv.unnamedArgs.map(::JsonPrimitive)))
                put("index", JsonPrimitive(inv.endIndex))
            }
            assertEquals("case $id", expected, actual)
            count++
        }
        assertTrue("expected >= 18 cases, got $count", count >= 18)
    }
}
