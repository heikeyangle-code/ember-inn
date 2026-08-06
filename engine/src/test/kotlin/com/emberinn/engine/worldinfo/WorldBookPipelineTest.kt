package com.emberinn.engine.worldinfo

import com.emberinn.engine.card.CharacterCardExporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookPipelineTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `entry parser fills defaults decorators and hash`() {
        val entryJson = buildJsonObject {
            put("key", JsonArray(listOf(JsonPrimitive("门"))))
            put("keysecondary", JsonArray(listOf(JsonPrimitive("钥匙"))))
            put("insertion_order", JsonPrimitive(5))
            put("enabled", JsonPrimitive(false))
            put("content", JsonPrimitive("@@activate\n正文"))
        }
        val e = WorldBookEntryParser.parse(entryJson, "w", 7)

        assertEquals(7, e.uid)
        assertEquals(5, e.order)
        assertEquals(true, e.disable)
        assertEquals(listOf("门"), e.keys)
        assertEquals(listOf("钥匙"), e.keySecondary)
        assertEquals("正文", e.content)
        assertEquals(listOf("@@activate"), e.decorators)
        assertEquals(true, e.selective)   // 官方模板默认 true
        assertEquals(100, e.probability) // 官方模板默认 100
        assertEquals(4, e.depth)         // DEFAULT_DEPTH
        assertNotEquals(0L, e.hash)
    }

    @Test
    fun `lore merger applies strategy and order`() {
        fun e(uid: Int, order: Int) = WorldInfoEntry(world = "w", uid = uid, order = order)
        val merged = WorldLoreMerger.merge(
            global = listOf(e(1, 99)),
            character = listOf(e(2, 50)),
            chat = listOf(e(3, 1)),
            persona = listOf(e(4, 2)),
            strategy = WorldLoreMerger.CHARACTER_FIRST,
        )
        assertEquals(listOf(3, 4, 2, 1), merged.map { it.uid })
    }

    @Test
    fun `chat jsonl roundtrip`() {
        val messages = listOf(
            json.parseToJsonElement("""{"role":"user","content":"你好"}"""),
            json.parseToJsonElement("""{"role":"assistant","content":"你好呀"}"""),
        )
        val text = ChatJsonl.export(messages)
        assertEquals(2, text.lineSequence().filter { it.isNotBlank() }.count())
        val back = ChatJsonl.import(text)
        assertEquals(2, back.size)
        val first = back[0] as JsonObject
        assertEquals("你好", first["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `json export to v2 cleans and pretty prints`() {
        val v3 = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"N","description":"D","extensions":{"fav":true,"depth_prompt":{"p":"x"}}},"fav":true,"chat":[]}"""
        val out = CharacterCardExporter.exportToV2Json(v3)
        assertTrue(out.contains("\"name\": \"N\""))
        assertTrue(out.contains("\"description\": \"D\""))
        assertTrue(out.contains("\"fav\": false"))
        assertTrue(out.contains("\"chat\"").not())
        assertTrue(out.contains("\n    \""))
    }
}
