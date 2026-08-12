package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：world-info.js WorldInfoTimedEffects 类
 * （ensureChatMetadata/checkTimedEffectOfType/checkDelayEffect/checkTimedEffects/
 * setTimedEffects/setTimedEffect/isValidEffectType/isEffectActive/cleanUp/getEffectMetadata）。
 * fixture 由 scripts/diff/worldinfo-timed-effects-official.mjs 生成，禁止手改。
 */
class WorldInfoTimedEffectsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `timed effects match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/worldinfo-timed-effects.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val chat = args["chat"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val entries = args["entries"]?.jsonArray?.map { entryJson(it.jsonObject) } ?: emptyList()
            val isDryRun = args["isDryRun"]?.jsonPrimitive?.content == "true"
            val metadata = TimedEffectsMetadata()
            args["metadata"]?.jsonObject?.get("timedWorldInfo")?.jsonObject?.let { tw ->
                tw["sticky"]?.jsonObject?.forEach { (k, v) ->
                    metadata.sticky[k] = effect(v.jsonObject)
                }
                tw["cooldown"]?.jsonObject?.forEach { (k, v) ->
                    metadata.cooldown[k] = effect(v.jsonObject)
                }
            }

            val te = WorldInfoTimedEffects(chat.size, entries, metadata, isDryRun)
            when (args["op"]?.jsonPrimitive?.content) {
                "check" -> te.checkTimedEffects()
                "setTimedEffects" -> te.setTimedEffects(entries)
                "setTimedEffect" -> {
                    val type = args["type"]?.jsonPrimitive?.content ?: ""
                    val entry = entryJson(args["entry"]!!.jsonObject)
                    te.setTimedEffect(type, entry, args["newState"]?.jsonPrimitive?.content == "true")
                }
                "cleanUp" -> te.cleanUp()
            }

            val expected = case.getValue("expected").jsonObject
            assertEquals(
                "case $id",
                canonical(expected["buffers"] ?: JsonObject(emptyMap())),
                canonical(
                    buildJson(
                        "sticky" to entries.filter { te.isEffectActive("sticky", it) }.map { it.hash },
                        "cooldown" to entries.filter { te.isEffectActive("cooldown", it) }.map { it.hash },
                        "delay" to entries.filter { te.isEffectActive("delay", it) }.map { it.hash },
                    ),
                ),
            )
            assertEquals(
                "case $id metadata",
                canonical(expected["metadata"] ?: JsonObject(emptyMap())),
                canonical(metadataJson(metadata)),
            )
        }
    }

    private fun entryJson(o: JsonObject): WorldInfoEntry = WorldInfoEntry(
        world = o["world"]?.jsonPrimitive?.content ?: "",
        uid = o["uid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        order = 0,
        hash = o["hash"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        sticky = o["sticky"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
        cooldown = o["cooldown"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
        delay = o["delay"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull(),
    )

    private fun effect(o: JsonObject): TimedEffect = TimedEffect(
        hash = o["hash"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        start = o["start"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        end = o["end"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        protected = o["protected"]?.jsonPrimitive?.content == "true",
    )

    private fun metadataJson(m: TimedEffectsMetadata): JsonObject = JsonObject(
        linkedMapOf(
            "timedWorldInfo" to JsonObject(
                linkedMapOf(
                    "sticky" to JsonObject(m.sticky.entries.sortedBy { it.key }.associate { (k, v) -> k to effectJson(v) }),
                    "cooldown" to JsonObject(m.cooldown.entries.sortedBy { it.key }.associate { (k, v) -> k to effectJson(v) }),
                ),
            ),
        ),
    )

    private fun effectJson(v: TimedEffect): JsonObject = JsonObject(
        linkedMapOf(
            "hash" to JsonPrimitive(v.hash),
            "start" to JsonPrimitive(v.start),
            "end" to JsonPrimitive(v.end),
            "protected" to JsonPrimitive(v.protected),
        ),
    )

    private fun buildJson(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(
        linkedMapOf(*pairs.map { (k, v) ->
            k to when (v) {
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                is JsonObject -> v
                else -> JsonPrimitive(v.toString())
            }
        }.toTypedArray()),
    )

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        is JsonPrimitive -> {
            if (!el.isString) {
                val d = el.content.toDoubleOrNull()
                if (d != null && d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    JsonPrimitive(d.toLong().toString())
                } else if (d != null) {
                    JsonPrimitive(d.toString().lowercase())
                } else {
                    el
                }
            } else {
                el
            }
        }
        else -> el
    }
}
