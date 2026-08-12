package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/worldinfo-scan-official.mjs 生成，
 * 覆盖 checkWorldInfo 整体流程（关键词/常驻/递归/预算/min activations/分组/过滤/时间效果/概率）。
 */
class WorldInfoScanDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `checkWorldInfo outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/worldinfo-scan.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val settings = case.getValue("settings").jsonObject
            val maxContext = int(case, "maxContext", 100)
            val charaName = case["charaName"]?.jsonPrimitive?.contentOrNull ?: ""
            val tagKey = case["tagKey"]?.jsonPrimitive?.let { if (it.isString) it.content else null }
            val tagMap = case["tagMap"]?.jsonObject
            val characterTags = if (tagKey != null) {
                tagMap?.get(tagKey)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            } else {
                emptyList()
            }
            val global = GlobalScanData(characterName = charaName, characterTags = characterTags)
            val randomValues = case["random"]?.jsonArray?.map { it.jsonPrimitive.content.toDouble() }
                ?: listOf(99.0)

            val first = case.getValue("first").jsonObject
            val firstExpected = first.getValue("expected").jsonObject
            val firstResult = scan(
                chat = strList(first, "chat"),
                entries = parseEntries(first.getValue("entries").jsonArray),
                settings = settings,
                maxContext = maxContext,
                global = global,
                metadata = TimedEffectsMetadata(),
                random = randomValues.getOrElse(0) { 99.0 },
            )
            assertEquals("case $id first", firstExpected, resultToJson(firstResult))

            val expected = case.getValue("expected").jsonObject
            // 两段扫描用例：顶层 chat/entries 是第二段
            val secondChat = strList(case, "chat")
            val secondEntries = parseEntries(case.getValue("entries").jsonArray)
            val isTwoScan = secondChat != strList(first, "chat") ||
                secondEntries.map { it.uid } != parseEntries(first.getValue("entries").jsonArray).map { it.uid }
            if (isTwoScan) {
                val secondResult = scan(
                    chat = secondChat,
                    entries = secondEntries,
                    settings = settings,
                    maxContext = maxContext,
                    global = global,
                    metadata = firstResult.timedMetadata,
                    random = randomValues.getOrElse(1) { 99.0 },
                )
                assertEquals("case $id second", expected, resultToJson(secondResult))
            } else {
                assertEquals("case $id", expected, resultToJson(firstResult))
            }
        }
    }

    private fun scan(
        chat: List<String>,
        entries: List<WorldInfoEntry>,
        settings: JsonObject,
        maxContext: Int,
        global: GlobalScanData,
        metadata: TimedEffectsMetadata,
        random: Double,
    ): WorldInfoResult = WorldInfoScanner(random = RandomProvider { random }).scan(
        chat = chat,
        maxContext = maxContext,
        entries = entries,
        settings = WorldInfoSettings(
            depth = int(settings, "depth", 4),
            budgetPercent = int(settings, "budget", 25),
            budgetCap = int(settings, "budgetCap", 0),
            recursive = bool(settings, "recursive", false),
            minActivations = int(settings, "minActivations", 0),
            minActivationsDepthMax = int(settings, "minActivationsDepthMax", 0),
            maxRecursionSteps = int(settings, "maxRecursionSteps", 0),
            useGroupScoring = bool(settings, "useGroupScoring", false),
            caseSensitive = bool(settings, "caseSensitive", false),
            matchWholeWords = bool(settings, "matchWholeWords", false),
        ),
        global = global,
        timedMetadata = metadata,
    )

    private fun parseEntries(entries: JsonArray): List<WorldInfoEntry> =
        entries.map { el ->
            val obj = el.jsonObject
            WorldBookEntryParser.parse(obj, "w", obj.getValue("uid").jsonPrimitive.content.toInt())
        }

    private fun resultToJson(result: WorldInfoResult): JsonObject = buildJsonObject {
        put("before", result.worldInfoBefore)
        put("after", result.worldInfoAfter)
        put("uids", JsonArray(result.activated.map { JsonPrimitive(it.uid) }))
        put("em", JsonArray(result.emEntries.map { e ->
            buildJsonObject {
                put("position", e.position)
                put("content", e.content)
            }
        }))
        put("anBefore", JsonArray(result.anBefore.map { JsonPrimitive(it) }))
        put("anAfter", JsonArray(result.anAfter.map { JsonPrimitive(it) }))
        put("depth", JsonArray(result.depthEntries.map { d ->
            buildJsonObject {
                put("depth", d.depth)
                put("role", d.role)
                put("entries", JsonArray(d.entries.map { JsonPrimitive(it) }))
            }
        }))
    }

    private fun strList(obj: JsonObject, key: String): List<String> =
        obj[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun int(obj: JsonObject, key: String, def: Int): Int =
        obj[key]?.jsonPrimitive?.let { it.intOrNull ?: (it.content.toIntOrNull() ?: def) } ?: def

    private fun bool(obj: JsonObject, key: String, def: Boolean): Boolean =
        obj[key]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } ?: def
}
