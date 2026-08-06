package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 世界书条目 JSON → WorldInfoEntry。
 * 默认值对齐官方 newWorldInfoEntryDefinition；
 * 装饰器解析 + hash 计算对齐 getSortedEntries（{uid, world, ...rest, decorators} 序列化）。
 */
object WorldBookEntryParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(entryJson: JsonObject, world: String, uid: Int): WorldInfoEntry {
        val raw = entryJson.toMutableMap()

        val content = raw["content"]?.jsonPrimitive?.contentOrNull() ?: ""
        val (decorators, newContent) = WorldInfoDecorators.parse(content)
        raw["content"] = JsonPrimitive(newContent)
        if (decorators.isNotEmpty()) raw["decorators"] = JsonArray(decorators.map { JsonPrimitive(it) })

        val hashObj = buildJsonObject {
            put("uid", JsonPrimitive(uid))
            put("world", JsonPrimitive(world))
            raw.forEach { (k, v) -> put(k, v) }
        }
        val hash = StringHash.get(hashObj.toString())

        val keys = stringArray(raw["key"] ?: raw["keys"])
        val keySecondary = stringArray(raw["keysecondary"] ?: raw["secondary_keys"])
        val enabledRaw = raw["enabled"]?.let { boolOf(it, true) }
        val disable = raw["disable"]?.let { boolOf(it, false) } ?: (enabledRaw?.let { !it } ?: false)
        return WorldInfoEntry(
            world = world,
            uid = uid,
            order = intOf(raw["order"] ?: raw["insertion_order"], 100),
            name = strOf(raw["comment"], ""),
            keys = keys,
            keySecondary = keySecondary,
            content = newContent,
            disable = disable,
            constant = boolOf(raw["constant"], false),
            position = intOf(raw["position"], 0),
            depth = intOf(raw["depth"], WorldInfoConstants.DEFAULT_DEPTH),
            role = raw["role"]?.jsonPrimitive?.let { if (it.isString) it.content else it.intOrNull?.toString() },
            selective = raw["selective"]?.let { boolOf(it, true) } ?: true,
            selectiveLogic = raw["selectiveLogic"]?.let { intOf(it, WorldInfoConstants.AND_ANY) },
            caseSensitive = raw["case_sensitive"]?.let { boolOf(it, false) } ?: raw["caseSensitive"]?.let { boolOf(it, false) },
            matchWholeWords = raw["match_whole_words"]?.let { boolOf(it, false) } ?: raw["matchWholeWords"]?.let { boolOf(it, false) },
            scanDepth = raw["scan_depth"]?.let { intOf(it, 0) } ?: raw["scanDepth"]?.let { intOf(it, 0) },
            matchPersonaDescription = boolOf(raw["matchPersonaDescription"], false),
            matchCharacterDescription = boolOf(raw["matchCharacterDescription"], false),
            matchCharacterPersonality = boolOf(raw["matchCharacterPersonality"], false),
            matchCharacterDepthPrompt = boolOf(raw["matchCharacterDepthPrompt"], false),
            matchScenario = boolOf(raw["matchScenario"], false),
            matchCreatorNotes = boolOf(raw["matchCreatorNotes"], false),
            preventRecursion = boolOf(raw["preventRecursion"] ?: raw["prevent_recursion"], false),
            excludeRecursion = boolOf(raw["excludeRecursion"] ?: raw["exclude_recursion"], false),
            delayUntilRecursion = raw["delayUntilRecursion"]?.let { delayLevelOf(it) }
                ?: raw["delay_until_recursion"]?.let { delayLevelOf(it) } ?: 0,
            useProbability = raw["useProbability"]?.let { boolOf(it, true) } ?: true,
            probability = raw["probability"]?.let { probabilityOf(it) } ?: 100,
            ignoreBudget = boolOf(raw["ignoreBudget"] ?: raw["ignore_budget"], false),
            triggers = stringArray(raw["triggers"]),
            decorators = decorators,
            outletName = strOf(raw["outletName"], ""),
            hash = hash,
            sticky = raw["sticky"]?.let { intOf(it, 0) },
            cooldown = raw["cooldown"]?.let { intOf(it, 0) },
            delay = raw["delay"]?.let { intOf(it, 0) },
            group = strOf(raw["group"], "").ifEmpty { null },
            groupWeight = raw["groupWeight"]?.let { intOf(it, 100) } ?: raw["group_weight"]?.let { intOf(it, 100) },
            groupOverride = raw["groupOverride"]?.let { boolOf(it, false) } ?: raw["group_override"]?.let { boolOf(it, false) },
            useGroupScoring = raw["useGroupScoring"]?.let { boolOf(it, false) } ?: raw["use_group_scoring"]?.let { boolOf(it, false) },
            characterFilter = parseCharacterFilter(raw),
        )
    }

    private fun parseCharacterFilter(raw: Map<String, JsonElement>): CharacterFilter? {
        val obj = raw["characterFilter"]?.jsonObject
        val names = obj?.let { stringArray(it["names"]) }
            ?: stringArray(raw["characterFilterNames"])
        val tags = obj?.let { stringArray(it["tags"]) }
            ?: stringArray(raw["characterFilterTags"])
        val exclude = obj?.let { boolOf(it["isExclude"], false) }
            ?: boolOf(raw["characterFilterExclude"], false)
        if (names.isEmpty() && tags.isEmpty()) return null
        return CharacterFilter(names = names, tags = tags, isExclude = exclude)
    }

    private fun strOf(el: JsonElement?, def: String): String =
        el?.jsonPrimitive?.contentOrNull() ?: def

    private fun intOf(el: JsonElement?, def: Int): Int =
        el?.jsonPrimitive?.let { p ->
            p.intOrNull ?: p.contentOrNull()?.trim()?.toDoubleOrNull()?.toInt()
        } ?: def

    private fun probabilityOf(el: JsonElement): Int =
        el.jsonPrimitive.let { p ->
            p.intOrNull ?: p.contentOrNull()?.trim()?.removeSuffix("%")?.toDoubleOrNull()?.toInt() ?: 100
        }

    /** 官方 delayUntilRecursion === true 视为 1。 */
    private fun delayLevelOf(el: JsonElement): Int =
        el.jsonPrimitive.let { p ->
            if (p.booleanOrNull == true) 1 else intOf(el, 0)
        }

    private fun boolOf(el: JsonElement?, def: Boolean): Boolean =
        el?.jsonPrimitive?.let { p ->
            p.booleanOrNull ?: p.contentOrNull()?.toBooleanStrictOrNull()
        } ?: def

    private fun stringArray(el: JsonElement?): List<String> {
        if (el == null) return emptyList()
        return if (el is JsonArray) el.mapNotNull { it.jsonPrimitive.contentOrNull() }
        else el.jsonPrimitive.contentOrNull()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (isString) content else null
}
