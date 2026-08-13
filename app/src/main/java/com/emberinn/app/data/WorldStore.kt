package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.worldinfo.WorldBookEntryParser
import com.emberinn.engine.worldinfo.WorldInfoEntry
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 外置世界书存储（对齐官方 data/default-user/worlds 目录的 *.json：{name, entries:{uid:entry}}）。 */
class WorldStore(context: Context) {

    private val dir = File(context.filesDir, "worlds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    companion object {
        /** 外置世界书条目缓存：按文件修改时间失效，避免每次发送都重读重解析。 */
        private val entriesCache = mutableMapOf<String, Pair<Long, List<WorldInfoEntry>>>()
    }

    data class WorldFile(
        val name: String,
        val displayName: String,
        val entryCount: Int,
    )

    fun list(): List<WorldFile> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { f ->
                runCatching {
                    val root = json.parseToJsonElement(f.readText()).jsonObject
                    WorldFile(
                        name = f.nameWithoutExtension,
                        displayName = root["name"]?.jsonPrimitive?.contentOrNull ?: f.nameWithoutExtension,
                        entryCount = (root["entries"] as? JsonObject)?.size ?: 0,
                    )
                }.getOrNull()
            }

    fun entries(name: String): List<WorldInfoEntry> {
        val file = fileOf(name) ?: return emptyList()
        val stamp = file.lastModified()
        synchronized(entriesCache) {
            entriesCache[name]?.let { (t, list) -> if (t == stamp) return list }
        }
        val parsed = runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            (root["entries"] as? JsonObject)?.let { entriesObj ->
                entriesObj.entries.mapIndexed { index, (_, el) ->
                    WorldBookEntryParser.parse(el.jsonObject, name, index + 1)
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
        synchronized(entriesCache) {
            if (entriesCache.size > 64) entriesCache.clear()
            entriesCache[name] = stamp to parsed
        }
        return parsed
    }

    /** 读取外置世界条目为全字段草稿（复用 CharacterCardEdit 的 v2 归一解析）。 */
    fun drafts(name: String): List<WorldEntryDraft> {
        val file = fileOf(name) ?: return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val entriesArr = (root["entries"] as? JsonObject)?.values?.toList() ?: return@runCatching emptyList()
            val fakeCard = buildJsonObject {
                put(
                    "data",
                    buildJsonObject {
                        put(
                            "character_book",
                            buildJsonObject { put("entries", JsonArray(entriesArr)) },
                        )
                    },
                )
            }
            CharacterCardEdit.readWorldEntries(fakeCard.toString())
        }.getOrDefault(emptyList())
    }

    /** 保存外置世界条目（草稿 → 官方 world 文件 {name, entries:{uid:entry}}）。 */
    fun saveDrafts(name: String, displayName: String, drafts: List<WorldEntryDraft>) {
        val fakeCard = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put(
                        "character_book",
                        buildJsonObject { put("entries", JsonArray(emptyList())) },
                    )
                },
            )
        }
        val normalized = runCatching {
            CharacterCardEdit.applyWorldEntries(fakeCard.toString(), drafts)
        }.getOrNull()?.let { json.parseToJsonElement(it).jsonObject["data"]?.jsonObject }
        val entriesArr = normalized?.get("character_book")?.jsonObject?.get("entries")?.jsonArray
            ?: JsonArray(emptyList())
        val payload = buildJsonObject {
            put("name", JsonPrimitive(displayName))
            put(
                "entries",
                JsonObject(
                    entriesArr.mapIndexed { index, el ->
                        val obj = (el as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                        if (obj.containsKey("keys") && !obj.containsKey("key")) {
                            obj["key"] = obj.remove("keys")!!
                        }
                        (index + 1).toString() to JsonObject(obj)
                    }.toMap(),
                ),
            )
        }
        val safe = sanitize(name)
        File(dir, "$safe.json").writeText(json.encodeToString(JsonObject.serializer(), payload))
    }

    fun create(name: String) {
        if (name.isBlank() || fileOf(name) != null) return
        save(name, name, emptyList())
    }

    fun save(name: String, displayName: String, entries: List<WorldInfoEntry>) {
        dir.mkdirs()
        val safe = sanitize(name)
        val payload = buildJsonObject {
            put("name", JsonPrimitive(displayName))
            put(
                "entries",
                JsonObject(entries.mapIndexed { index, e ->
                    (index + 1).toString() to entryJson(e)
                }.toMap()),
            )
        }
        File(dir, "$safe.json").writeText(json.encodeToString(JsonObject.serializer(), payload))
    }

    fun rename(oldName: String, newName: String): Boolean {
        val file = fileOf(oldName) ?: return false
        val newSafe = sanitize(newName)
        if (fileOf(newName) != null || newSafe.isBlank()) return false
        return file.renameTo(File(dir, "$newSafe.json"))
    }

    fun delete(name: String) {
        fileOf(name)?.delete()
    }

    fun duplicate(name: String): String {
        val entries = entries(name)
        var newName = "$name-copy"
        var i = 2
        while (fileOf(newName) != null) {
            newName = "$name-copy-$i"
            i++
        }
        create(newName)
        save(newName, newName, entries)
        return newName
    }

    /** 导入官方世界文件（{name, entries:{uid:entry}}）；同名已存在则失败。 */
    fun importWorld(fileName: String, content: String): Boolean = runCatching {
        val root = json.parseToJsonElement(content).jsonObject
        if (root["entries"] !is JsonObject) return@runCatching false
        val name = fileName.removeSuffix(".json").trim().ifBlank {
            root["name"]?.jsonPrimitive?.contentOrNull ?: "world"
        }
        if (fileOf(name) != null) return@runCatching false
        File(dir, sanitize(name) + ".json").writeText(content)
        true
    }.getOrDefault(false)

    /** 导出世界文件原始 JSON（官方格式）。 */
    fun export(name: String): String? = fileOf(name)?.readText()

    private fun entryJson(e: WorldInfoEntry): JsonObject {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        fun put(key: String, value: kotlinx.serialization.json.JsonElement) { fields[key] = value }
        put("id", JsonPrimitive(e.uid))
        // 官方 world 文件字段：key / keysecondary（数组）
        if (e.keys.isNotEmpty()) put("key", JsonArray(e.keys.map(::JsonPrimitive)))
        if (e.keySecondary.isNotEmpty()) put("keysecondary", JsonArray(e.keySecondary.map(::JsonPrimitive)))
        put("content", JsonPrimitive(e.content))
        put("comment", JsonPrimitive(e.name))
        put("constant", JsonPrimitive(e.constant))
        put("selective", JsonPrimitive(e.selective))
        e.selectiveLogic?.let { put("selectiveLogic", JsonPrimitive(it)) }
        put("enabled", JsonPrimitive(!e.disable))
        put("insertion_order", JsonPrimitive(e.order))
        put("position", JsonPrimitive(e.position))
        e.depth?.let { put("depth", JsonPrimitive(it)) }
        e.role?.let { put("role", JsonPrimitive(it)) }
        put("matchPersonaDescription", JsonPrimitive(e.matchPersonaDescription))
        put("matchCharacterDescription", JsonPrimitive(e.matchCharacterDescription))
        put("matchCharacterPersonality", JsonPrimitive(e.matchCharacterPersonality))
        put("matchCharacterDepthPrompt", JsonPrimitive(e.matchCharacterDepthPrompt))
        put("matchScenario", JsonPrimitive(e.matchScenario))
        put("matchCreatorNotes", JsonPrimitive(e.matchCreatorNotes))
        put("useProbability", JsonPrimitive(e.useProbability))
        put("probability", JsonPrimitive(e.probability))
        if (e.triggers.isNotEmpty()) put("triggers", JsonArray(e.triggers.map(::JsonPrimitive)))
        e.outletName?.let { put("outletName", JsonPrimitive(it)) }
        e.sticky?.let { put("sticky", JsonPrimitive(it)) }
        e.cooldown?.let { put("cooldown", JsonPrimitive(it)) }
        e.delay?.let { put("delay", JsonPrimitive(it)) }
        e.group?.let { put("group", JsonPrimitive(it)) }
        e.groupWeight?.let { put("groupWeight", JsonPrimitive(it)) }
        e.groupOverride?.let { put("groupOverride", JsonPrimitive(it)) }
        e.useGroupScoring?.let { put("useGroupScoring", JsonPrimitive(it)) }
        e.automationId?.let { put("automationId", JsonPrimitive(it)) }
        put("addMemo", JsonPrimitive(e.addMemo))
        // 官方把这些字段放进 entry.extensions（world-info.js setWIOriginalDataValue 映射）
        val ext = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        e.caseSensitive?.let { ext["case_sensitive"] = JsonPrimitive(it) }
        e.matchWholeWords?.let { ext["match_whole_words"] = JsonPrimitive(it) }
        e.scanDepth?.let { ext["scan_depth"] = JsonPrimitive(it) }
        ext["prevent_recursion"] = JsonPrimitive(e.preventRecursion)
        ext["exclude_recursion"] = JsonPrimitive(e.excludeRecursion)
        ext["delay_until_recursion"] = JsonPrimitive(e.delayUntilRecursion)
        ext["ignore_budget"] = JsonPrimitive(e.ignoreBudget)
        ext["vectorized"] = JsonPrimitive(e.vectorized)
        e.displayIndex?.let { ext["display_index"] = JsonPrimitive(it) }
        put("extensions", JsonObject(ext))
        return JsonObject(fields)
    }

    private fun fileOf(name: String): File? {
        val f = File(dir, sanitize(name) + ".json")
        return f.takeIf { it.exists() }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "world" }
}
