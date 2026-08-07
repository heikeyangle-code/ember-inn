package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扩展字段透传契约测试（防遗漏，配合 docs/HANDOFF.md「扩展行为待接清单」）：
 * vectorized / automationId / displayIndex / addMemo 官方核心扫描不消费，但数据必须 1:1 透传；
 * 将来接 RAG / 快捷回复自动化 / 世界书编辑器时，在对应 TODO(EXT-x) 处消费，不得删掉透传。
 */
class WorldInfoExtensionFieldsKeepTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val entryWithExtensionFields: JsonObject = buildJsonObject {
        put("uid", JsonPrimitive(7))
        put("key", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("k"))))
        put("keysecondary", kotlinx.serialization.json.JsonArray(emptyList()))
        put("comment", JsonPrimitive("备注"))
        put("content", JsonPrimitive("内容"))
        put("constant", JsonPrimitive(false))
        put("vectorized", JsonPrimitive(true))
        put("addMemo", JsonPrimitive(true))
        put("automationId", JsonPrimitive("qr_auto_1"))
        put("displayIndex", JsonPrimitive(42))
        put("extensions", buildJsonObject {
            put("vectorized", JsonPrimitive(true))
            put("automation_id", JsonPrimitive("qr_auto_1"))
            put("display_index", JsonPrimitive(42))
            put("custom_keep", JsonPrimitive("原始值"))
        })
    }

    @Test
    fun `world info file round trip keeps extension fields`() {
        val raw = buildJsonObject {
            put("name", JsonPrimitive("扩展字段"))
            put("entries", JsonObject(mapOf("7" to entryWithExtensionFields)))
            put("extensions", buildJsonObject { put("keep", JsonPrimitive(1)) })
        }.toString()

        val parsed = WorldInfoFileCodec.parse(raw)
        val serialized = WorldInfoFileCodec.serialize(parsed)
        val reparsed = WorldInfoFileCodec.parse(serialized)

        assertEquals(parsed.rawEntries, reparsed.rawEntries)
        assertEquals(parsed.extensions, reparsed.extensions)

        val kept = reparsed.rawEntries.getValue("7")
        assertEquals(true, kept["vectorized"]?.jsonPrimitive?.let { it.contentOrBool() })
        assertEquals(true, kept["addMemo"]?.jsonPrimitive?.let { it.contentOrBool() })
        assertEquals("qr_auto_1", kept["automationId"]?.jsonPrimitive?.content)
        assertEquals(42, kept["displayIndex"]?.jsonPrimitive?.let { it.content.toIntOrNull() })
        assertEquals("原始值", kept["extensions"]?.jsonObject?.get("custom_keep")?.jsonPrimitive?.content)
    }

    @Test
    fun `world book to character book round trip keeps extension fields`() {
        val characterBook = WorldInfoConverter.toCharacterBook("测试", mapOf("7" to entryWithExtensionFields))
        val entries = WorldInfoConverter.toWorldEntries(characterBook)
        val back = entries.getValue("7")

        assertTrue(back["vectorized"]?.jsonPrimitive?.let { it.contentOrBool() } == true)
        assertTrue(back["addMemo"]?.jsonPrimitive?.let { it.contentOrBool() } == true)
        assertEquals("qr_auto_1", back["automationId"]?.jsonPrimitive?.content)
        assertEquals(42, back["displayIndex"]?.jsonPrimitive?.let { it.content.toIntOrNull() })
        // 原始 extensions 整体保留（含未知键）
        assertEquals("原始值", back["extensions"]?.jsonObject?.get("custom_keep")?.jsonPrimitive?.content)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrBool(): Boolean =
        content == "true" || content == "1"
}
