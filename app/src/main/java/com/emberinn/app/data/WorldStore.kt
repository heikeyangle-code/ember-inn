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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 外置世界书存储（对齐官方 data/default-user/worlds/*.json：{name, entries:{uid:entry}}）。 */
class WorldStore(context: Context) {

    private val dir = File(context.filesDir, "worlds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

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
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            (root["entries"] as? JsonObject)?.let { entriesObj ->
                entriesObj.entries.mapIndexed { index, (_, el) ->
                    WorldBookEntryParser.parse(el.jsonObject, name, index + 1)
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
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

    private fun entryJson(e: WorldInfoEntry): JsonObject {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        fun put(key: String, value: kotlinx.serialization.json.JsonElement) { fields[key] = value }
        put("id", JsonPrimitive(e.uid))
        if (e.keys.isNotEmpty()) put("keys", JsonArray(e.keys.map(::JsonPrimitive)))
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
        e.caseSensitive?.let { put("caseSensitive", JsonPrimitive(it)) }
        e.matchWholeWords?.let { put("matchWholeWords", JsonPrimitive(it)) }
        e.scanDepth?.let { put("scanDepth", JsonPrimitive(it)) }
        put("matchPersonaDescription", JsonPrimitive(e.matchPersonaDescription))
        put("matchCharacterDescription", JsonPrimitive(e.matchCharacterDescription))
        put("matchCharacterPersonality", JsonPrimitive(e.matchCharacterPersonality))
        put("matchCharacterDepthPrompt", JsonPrimitive(e.matchCharacterDepthPrompt))
        put("matchScenario", JsonPrimitive(e.matchScenario))
        put("matchCreatorNotes", JsonPrimitive(e.matchCreatorNotes))
        put("preventRecursion", JsonPrimitive(e.preventRecursion))
        put("excludeRecursion", JsonPrimitive(e.excludeRecursion))
        put("delayUntilRecursion", JsonPrimitive(e.delayUntilRecursion))
        put("useProbability", JsonPrimitive(e.useProbability))
        put("probability", JsonPrimitive(e.probability))
        put("ignoreBudget", JsonPrimitive(e.ignoreBudget))
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
        e.displayIndex?.let { put("displayIndex", JsonPrimitive(it)) }
        put("vectorized", JsonPrimitive(e.vectorized))
        put("addMemo", JsonPrimitive(e.addMemo))
        return JsonObject(fields)
    }

    private fun fileOf(name: String): File? {
        val f = File(dir, sanitize(name) + ".json")
        return f.takeIf { it.exists() }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "world" }
}
