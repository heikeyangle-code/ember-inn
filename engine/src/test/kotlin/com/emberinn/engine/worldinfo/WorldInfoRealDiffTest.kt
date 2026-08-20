package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil

/**
 * 真实行为比对（阶段2）：fixture 由 .scratch/audit/probe4-wi.mjs 在官方 SillyTavern 真实浏览器中
 * 运行真正 checkWorldInfo（真实 getTokenCountAsync=NONE/guesstimate、真实 substituteParams/getRegexedString）生成。
 * 本测试在 EmberInn 引擎上用完全相同输入（相同 chat/entries/settings）重跑，逐字符 diff 最终 prompt 字符串。
 * token 计数用官方 guesstimate = ceil(utf8字节/3.35)，与浏览器端一致。
 */
class WorldInfoRealDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `real-browser official outputs match engine char by char`() {
        val resource = checkNotNull(javaClass.getResource("/diff/wi-official-real.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        var failures = 0
        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("input").jsonObject
            val settings = input.getValue("settings").jsonObject
            val chat = strList(input, "chat")
            val maxContext = int(input, "maxContext", 100)
            val entries = parseEntries(input.getValue("entries").jsonArray)

            val result = WorldInfoScanner(
                tokenCounter = guesstimate,
                random = RandomProvider { 99.0 },
            ).scan(
                chat = chat,
                maxContext = maxContext,
                entries = entries,
                settings = WorldInfoSettings(
                    depth = int(settings, "world_info_depth", 2),
                    budgetPercent = int(settings, "world_info_budget", 25),
                    budgetCap = int(settings, "world_info_budget_cap", 0),
                    recursive = bool(settings, "world_info_recursive", false),
                    maxRecursionSteps = int(settings, "world_info_max_recursion_steps", 0),
                ),
            )

            val official = OfficialResult(
                before = str(case, "before"),
                after = str(case, "after"),
                uids = case["uids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                depth = case["depth"]?.jsonArray?.map { it.jsonObject } ?: emptyList(),
            )

            val engineUids = result.activated.map { "probe_world.${it.uid}" }
            val engineDepth = result.depthEntries.map { d ->
                mapOf(
                    "depth" to JsonPrimitive(d.depth),
                    "entries" to JsonArray(d.entries.map { JsonPrimitive(it) }),
                )
            }

            val problems = mutableListOf<String>()
            if (result.worldInfoBefore != official.before) problems.add("before: engine=${show(result.worldInfoBefore)} official=${show(official.before)}")
            if (result.worldInfoAfter != official.after) problems.add("after: engine=${show(result.worldInfoAfter)} official=${show(official.after)}")
            if (engineUids != official.uids) problems.add("uids: engine=$engineUids official=${official.uids}")
            if (engineDepth.map { it["depth"] } != official.depth.map { it["depth"] } ||
                engineDepth.map { it["entries"] } != official.depth.map { it["entries"] }
            ) {
                problems.add("depth: engine=$engineDepth official=${official.depth}")
            }

            if (problems.isNotEmpty()) {
                failures++
                println("MISMATCH [$id]:\n  " + problems.joinToString("\n  "))
            } else {
                println("OK [$id] before=${show(result.worldInfoBefore)} uids=$engineUids")
            }
        }

        assertEquals("real-browser official vs engine mismatches", 0, failures)
    }

    private data class OfficialResult(
        val before: String,
        val after: String,
        val uids: List<String>,
        val depth: List<JsonObject>,
    )

    private fun show(s: String) = s.replace("\n", "\\n")

    /** 官方 tokenizers.js guesstimate：ceil(utf8 字节 / 3.35)。 */
    private val guesstimate: TokenCounter = TokenCounter { text ->
        val bytes = text.encodeToByteArray().size
        ceil(bytes / 3.35).toInt()
    }

    private fun parseEntries(entries: JsonArray): List<WorldInfoEntry> =
        entries.map { el ->
            val obj = el.jsonObject
            val uid = obj.getValue("uid").jsonPrimitive.content.toInt()
            WorldBookEntryParser.parse(obj, "probe_world", uid)
        }

    private fun strList(obj: JsonObject, key: String): List<String> =
        obj[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun str(obj: JsonObject, key: String): String =
        obj[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun int(obj: JsonObject, key: String, def: Int): Int =
        obj[key]?.jsonPrimitive?.let { it.intOrNull ?: (it.content.toIntOrNull() ?: def) } ?: def

    private fun bool(obj: JsonObject, key: String, def: Boolean): Boolean =
        obj[key]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } ?: def
}
