package com.emberinn.engine.card

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/card-png-official.mjs 生成，
 * 覆盖 character-card-parser.js write/read（chara/ccv3 双写、旧块清理、ccv3 优先、往返）。
 */
class CardPngDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `png card read and write match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/card-png.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fn = case.getValue("fn").jsonPrimitive.content
            val input = Base64.getDecoder().decode(case.getValue("inputPng").jsonPrimitive.content)
            val expected = case.getValue("expected").jsonPrimitive.content

            when (fn) {
                "write" -> {
                    val out = CharacterCardCodec.writeToPng(input, case.getValue("data").jsonPrimitive.content)
                    assertEquals("case $id", expected, Base64.getEncoder().encodeToString(out))
                }
                "read" -> assertEquals("case $id", expected, CharacterCardCodec.readFromPng(input))
                else -> error("unknown fn: $fn")
            }
        }
    }
}
