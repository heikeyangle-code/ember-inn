package com.emberinn.engine.card

import com.emberinn.engine.worldinfo.WorldBookEntryParser
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：导入后的角色卡必须保留可读的内嵌世界书（data.character_book.entries）。
 * 覆盖 JSON 导入与 PNG（chara/ccv3）读写两条 App 实际路径。
 */
class WorldBookImportTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val v2Card = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "name": "角色A",
          "description": "描述",
          "data": {
            "name": "角色A",
            "description": "描述",
            "extensions": {},
            "character_book": {
              "name": "Book",
              "entries": [
                { "keys": ["地点", "city"], "content": "世界内容", "enabled": true, "insertion_order": 50 },
                { "key": "旧词", "content": "旧内容", "disable": true, "order": 30 }
              ]
            }
          }
        }
    """.trimIndent()

    /** card-png diff fixture 里的最小合法 PNG。 */
    private val tinyPng: ByteArray =
        Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAAAAAAAAAAAAAAukGgPAAAAA0lEQVQBAgNTNxetAAAAAElFTkSuQmCC")

    private fun entriesOf(raw: String): Int {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val entries = data["character_book"]?.jsonObject?.get("entries")?.jsonArray ?: return 0
        return entries.size
    }

    private fun assertEntriesReadable(raw: String) {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val entries = data["character_book"]!!.jsonObject["entries"]!!.jsonArray
        assertEquals(2, entries.size)
        val parsed = entries.mapIndexed { i, el ->
            WorldBookEntryParser.parse(el.jsonObject, "character", i)
        }
        assertEquals(listOf("地点", "city"), parsed[0].keys)
        assertEquals("世界内容", parsed[0].content)
        assertTrue(parsed[1].disable)
        assertEquals(listOf("旧词"), parsed[1].keys)
    }

    @Test
    fun `json import keeps embedded world book readable`() {
        val imported = JsonImporter.import(v2Card.toByteArray())
        assertEquals(2, entriesOf(imported))
        assertEntriesReadable(imported)
    }

    @Test
    fun `png import keeps embedded world book readable`() {
        val png = CharacterCardCodec.writeToPng(tinyPng, v2Card)
        val read = CharacterCardCodec.readFromPng(png)
        assertEquals(2, entriesOf(read))
        assertEntriesReadable(read)
    }
}
