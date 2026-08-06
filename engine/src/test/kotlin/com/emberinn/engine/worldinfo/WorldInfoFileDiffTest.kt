package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/worldinfo-file-official.mjs 生成，
 * 覆盖 convertWorldInfoToCharacterBook 与 convertCharacterBook。
 */
class WorldInfoFileDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `world info conversions match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/worldinfo-file.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fn = case.getValue("fn").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected").jsonObject

            val actual = when (fn) {
                "toCharacterBook" -> {
                    val name = args.getValue("name").jsonPrimitive.content
                    val rawEntries = linkedMapOf<String, JsonObject>()
                    args.getValue("entries").jsonArray.forEach { el ->
                        val obj = el.jsonObject
                        val uid = obj["uid"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                            ?: rawEntries.size.toString()
                        rawEntries[uid] = obj
                    }
                    WorldInfoConverter.toCharacterBook(name, rawEntries)
                }
                "toWorldEntries" -> {
                    val characterBook = args.getValue("characterBook").jsonObject
                    val entries = WorldInfoConverter.toWorldEntries(characterBook)
                    buildJsonObject {
                        put("entries", JsonObject(entries))
                        put("originalData", characterBook)
                    }
                }
                else -> error("unknown fn: $fn")
            }
            assertEquals("case $id", expected, actual)
        }
    }

    @Test
    fun `world info file round trips raw entries`() {
        val raw = """{"name":"测试","entries":{"0":{"key":["a"],"content":"x","extensions":{"p":1}}},"extensions":{"v":2}}"""
        val parsed = WorldInfoFileCodec.parse(raw)
        assertEquals("测试", parsed.name)
        assertEquals(1, parsed.entries.size)
        assertEquals("a", parsed.entries[0].keys.first())
        val serialized = WorldInfoFileCodec.serialize(parsed)
        val reparsed = WorldInfoFileCodec.parse(serialized)
        assertEquals(parsed.rawEntries, reparsed.rawEntries)
        assertEquals(parsed.extensions, reparsed.extensions)
    }
}
