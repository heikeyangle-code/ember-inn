package com.emberinn.engine.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2NormalizerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `normalize keeps data and preserves chat string`() {
        val input = """
            {"spec":"chara_card_v3","spec_version":"3.0",
             "data":{"name":"测试","description":"描述","extensions":{"fav":true,"talkativeness":0.7}},
             "chat":"已有会话","json_data":"x"}
        """.trimIndent()
        val out = json.parseToJsonElement(V2Normalizer.normalize(input)).jsonObject

        assertEquals("测试", out["name"]?.jsonPrimitive?.content)
        assertEquals("描述", out["description"]?.jsonPrimitive?.content)
        assertEquals("已有会话", out["chat"]?.jsonPrimitive?.content)
        assertTrue(out.containsKey("data"))
        assertFalse(out.containsKey("json_data"))
        assertEquals(0.7, out["talkativeness"]?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.001)
        assertEquals("true", out["fav"]?.jsonPrimitive?.content)
    }

    @Test
    fun `normalize backfills defaults and chat when missing`() {
        val input = """{"spec":"chara_card_v3","data":{"name":"新卡","description":"d"}}"""
        val out = json.parseToJsonElement(V2Normalizer.normalize(input)).jsonObject
        assertEquals(0.5, out["talkativeness"]?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.001)
        assertEquals("false", out["fav"]?.jsonPrimitive?.content)
        assertTrue(out["chat"]?.jsonPrimitive?.content?.startsWith("新卡 - ") == true)
    }

    @Test
    fun `legacy build includes depth prompt`() {
        val out = json.parseToJsonElement(
            V2Normalizer.buildV2FromLegacy(
                name = "A",
                description = "d",
                firstMes = "hi",
                createDate = "2026-01-01T00:00:00Z",
                chat = "A - 2026-01-01 00:00",
                depthPromptPrompt = "深度提示",
                depthPromptDepth = 6,
                depthPromptRole = "assistant",
            ),
        ).jsonObject
        val depth = out["data"]!!.jsonObject["extensions"]!!.jsonObject["depth_prompt"]!!.jsonObject
        assertEquals("深度提示", depth["prompt"]?.jsonPrimitive?.content)
        assertEquals(6, depth["depth"]?.jsonPrimitive?.content?.toInt())
        assertEquals("assistant", depth["role"]?.jsonPrimitive?.content)
    }
}
