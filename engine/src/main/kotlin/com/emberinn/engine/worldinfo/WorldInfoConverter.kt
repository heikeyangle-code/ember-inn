package com.emberinn.engine.worldinfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
 * 世界书 ↔ 角色书互转，逐字段对齐官方：
 * - toCharacterBook：characters.js convertWorldInfoToCharacterBook
 * - toWorldEntries：world-info.js convertCharacterBook
 *
 * 注意：vectorized / automationId / displayIndex / addMemo 在这里必须继续透传（写回 extensions 或顶层字段），
 * 官方核心不消费它们，消费方是 RAG / 快捷回复自动化 / 编辑器（见 WorldInfoFile.kt 契约与 HANDOFF 待接清单）。
 */
object WorldInfoConverter {

    private val json = Json { ignoreUnknownKeys = true }

    /** 对齐 convertWorldInfoToCharacterBook（输入为世界书原始条目 JSON）。 */
    fun toCharacterBook(name: String, rawEntries: Map<String, JsonObject>): JsonObject = buildJsonObject {
        put("entries", JsonArray(rawEntries.values.map { entry -> buildJsonObject {
            // 官方 entry.position == 0 ? 'before_char' : 'after_char'（缺失默认 after_char）
            val position = intOf(entry["position"]) ?: 1
            val uid = strOf(entry["uid"]) ?: ""
            put("id", uid.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(uid))
            // 官方：未定义字段（undefined）在 JSON.stringify 中被省略
            entry["key"]?.let { put("keys", it) }
            entry["keysecondary"]?.let { put("secondary_keys", it) }
            entry["comment"]?.let { put("comment", it) }
            entry["content"]?.let { put("content", it) }
            entry["constant"]?.let { put("constant", it) }
            entry["selective"]?.let { put("selective", it) }
            entry["order"]?.let { put("insertion_order", it) }
            put("enabled", JsonPrimitive(!(boolOf(entry["disable"]) ?: false)))
            put("position", JsonPrimitive(if (position == 0) "before_char" else "after_char"))
            put("use_regex", JsonPrimitive(true))
            val ext = entry["extensions"]?.jsonObject ?: JsonObject(emptyMap())
            put("extensions", buildJsonObject {
                ext.forEach { (k, v) -> put(k, v) }
                entry["position"]?.let { put("position", JsonPrimitive(position)) }
                entry["excludeRecursion"]?.let { put("exclude_recursion", it) }
                entry["displayIndex"]?.let { put("display_index", it) }
                put("probability", intOf(entry["probability"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("useProbability", JsonPrimitive(boolOf(entry["useProbability"]) ?: false))
                put("depth", JsonPrimitive(intOf(entry["depth"]) ?: 4))
                put("selectiveLogic", JsonPrimitive(intOf(entry["selectiveLogic"]) ?: 0))
                put("outlet_name", JsonPrimitive(strOf(entry["outletName"]) ?: ""))
                put("group", JsonPrimitive(strOf(entry["group"]) ?: ""))
                put("group_override", JsonPrimitive(boolOf(entry["groupOverride"]) ?: false))
                put("group_weight", intOf(entry["groupWeight"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("prevent_recursion", JsonPrimitive(boolOf(entry["preventRecursion"]) ?: false))
                put("delay_until_recursion", delayUntilRecursionOf(entry["delayUntilRecursion"]))
                put("scan_depth", intOf(entry["scanDepth"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("match_whole_words", boolOf(entry["matchWholeWords"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("use_group_scoring", JsonPrimitive(boolOf(entry["useGroupScoring"]) ?: false))
                put("case_sensitive", boolOf(entry["caseSensitive"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("automation_id", JsonPrimitive(strOf(entry["automationId"]) ?: ""))
                put("role", intOf(entry["role"])?.let { JsonPrimitive(it) } ?: JsonPrimitive(0))
                put("vectorized", JsonPrimitive(boolOf(entry["vectorized"]) ?: false))
                put("sticky", intOf(entry["sticky"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cooldown", intOf(entry["cooldown"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("delay", intOf(entry["delay"])?.let { JsonPrimitive(it) } ?: JsonNull)
                put("match_persona_description", JsonPrimitive(boolOf(entry["matchPersonaDescription"]) ?: false))
                put("match_character_description", JsonPrimitive(boolOf(entry["matchCharacterDescription"]) ?: false))
                put("match_character_personality", JsonPrimitive(boolOf(entry["matchCharacterPersonality"]) ?: false))
                put("match_character_depth_prompt", JsonPrimitive(boolOf(entry["matchCharacterDepthPrompt"]) ?: false))
                put("match_scenario", JsonPrimitive(boolOf(entry["matchScenario"]) ?: false))
                put("match_creator_notes", JsonPrimitive(boolOf(entry["matchCreatorNotes"]) ?: false))
                put("triggers", entry["triggers"] ?: JsonArray(emptyList()))
                put("ignore_budget", JsonPrimitive(boolOf(entry["ignoreBudget"]) ?: false))
            })
        }}))
        put("name", JsonPrimitive(name))
    }

    /** 对齐 convertCharacterBook：返回按 id 键控的世界书条目 JSON。 */
    fun toWorldEntries(characterBook: JsonObject): Map<String, JsonObject> {
        val result = linkedMapOf<String, JsonObject>()
        val entries = characterBook["entries"]?.jsonArray ?: return result
        entries.forEachIndexed { index, el ->
            val entry = el.jsonObject
            val id = entry["id"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                ?: index.toString()
            val ext = entry["extensions"]?.jsonObject ?: JsonObject(emptyMap())
            val position = intOf(ext["position"])
                ?: if (strOf(entry["position"]) == "before_char") 0 else 1

            result[id] = buildJsonObject {
                put("uid", id.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(id))
                put("key", entry["keys"] ?: JsonArray(emptyList()))
                put("keysecondary", entry["secondary_keys"] ?: JsonArray(emptyList()))
                put("comment", JsonPrimitive(strOf(entry["comment"]) ?: ""))
                put("content", JsonPrimitive(strOf(entry["content"]) ?: ""))
                put("constant", JsonPrimitive(boolOf(entry["constant"]) ?: false))
                put("vectorized", JsonPrimitive(extBool(ext, "vectorized") ?: false))
                put("selective", JsonPrimitive(boolOf(entry["selective"]) ?: false))
                put("selectiveLogic", JsonPrimitive(extInt(ext, "selectiveLogic") ?: 0))
                put("addMemo", JsonPrimitive(strOf(entry["comment"]) != null))
                // 官方 entry.insertion_order 缺失时省略（undefined 不序列化）
                entry["insertion_order"]?.let { put("order", it) }
                put("position", JsonPrimitive(position))
                // 官方 !entry.enabled：enabled 缺失时 disable=true
                put("disable", JsonPrimitive(!(boolOf(entry["enabled"]) ?: false)))
                put("ignoreBudget", JsonPrimitive(extBool(ext, "ignore_budget") ?: false))
                put("excludeRecursion", JsonPrimitive(extBool(ext, "exclude_recursion") ?: false))
                put("preventRecursion", JsonPrimitive(extBool(ext, "prevent_recursion") ?: false))
                put("matchPersonaDescription", JsonPrimitive(extBool(ext, "match_persona_description") ?: false))
                put("matchCharacterDescription", JsonPrimitive(extBool(ext, "match_character_description") ?: false))
                put("matchCharacterPersonality", JsonPrimitive(extBool(ext, "match_character_personality") ?: false))
                put("matchCharacterDepthPrompt", JsonPrimitive(extBool(ext, "match_character_depth_prompt") ?: false))
                put("matchScenario", JsonPrimitive(extBool(ext, "match_scenario") ?: false))
                put("matchCreatorNotes", JsonPrimitive(extBool(ext, "match_creator_notes") ?: false))
                put("delayUntilRecursion", ext["delay_until_recursion"] ?: JsonPrimitive(false))
                put("probability", JsonPrimitive(extInt(ext, "probability") ?: 100))
                put("useProbability", JsonPrimitive(extBool(ext, "useProbability") ?: true))
                put("depth", JsonPrimitive(extInt(ext, "depth") ?: 4))
                put("outletName", JsonPrimitive(strOf(ext["outlet_name"]) ?: ""))
                put("group", JsonPrimitive(strOf(ext["group"]) ?: ""))
                put("groupOverride", JsonPrimitive(extBool(ext, "group_override") ?: false))
                put("groupWeight", JsonPrimitive(extInt(ext, "group_weight") ?: 100))
                put("scanDepth", ext["scan_depth"] ?: JsonNull)
                put("caseSensitive", ext["case_sensitive"] ?: JsonNull)
                put("matchWholeWords", ext["match_whole_words"] ?: JsonNull)
                put("useGroupScoring", ext["use_group_scoring"] ?: JsonNull)
                put("automationId", JsonPrimitive(strOf(ext["automation_id"]) ?: ""))
                put("role", JsonPrimitive(extInt(ext, "role") ?: 0))
                put("sticky", ext["sticky"] ?: JsonNull)
                put("cooldown", ext["cooldown"] ?: JsonNull)
                put("delay", ext["delay"] ?: JsonNull)
                put("triggers", ext["triggers"] ?: JsonArray(emptyList()))
                put("displayIndex", JsonPrimitive(extInt(ext, "display_index") ?: index))
                put("extensions", entry["extensions"] ?: JsonObject(emptyMap()))
            }
        }
        return result
    }

    private fun strOf(el: JsonElement?): String? =
        el?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }

    private fun intOf(el: JsonElement?): Int? =
        el?.jsonPrimitive?.let { p -> p.intOrNull ?: p.content.toIntOrNull() }

    private fun boolOf(el: JsonElement?): Boolean? =
        el?.jsonPrimitive?.let { p -> p.booleanOrNull ?: (p.content == "true") }

    private fun extInt(ext: JsonObject, key: String): Int? = intOf(ext[key])

    private fun extBool(ext: JsonObject, key: String): Boolean? = boolOf(ext[key])

    /** 官方 toCharacterBook 直接透传 delayUntilRecursion（true 保持 true）。 */
    private fun delayUntilRecursionOf(el: JsonElement?): JsonElement {
        val p = el?.jsonPrimitive ?: return JsonPrimitive(false)
        return if (p.booleanOrNull == true) JsonPrimitive(true) else el
    }
}
