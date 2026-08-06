package com.emberinn.engine.card

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * BYAF（Backyard Archive Format）导入，对齐官方 src/byaf.js：
 * ZIP：manifest.json（characters[0]、scenarios[]、author）→ character.json + scenario.json；
 * 宏 #{user}/#{character}/{user}/{character} 替换；loreItems → character_book。
 */
object ByafImporter {

    private val json = Json { ignoreUnknownKeys = true }

    // 官方顺序：#\{user\}: → #\{character\}: → {character}(?!}) → {user}(?!})
    private val macroUserColon = Regex("""#\{user\}:""", RegexOption.IGNORE_CASE)
    private val macroCharColon = Regex("""#\{character\}:""", RegexOption.IGNORE_CASE)
    private val macroCharBare = Regex("""\{character}(?!})""", RegexOption.IGNORE_CASE)
    private val macroUserBare = Regex("""\{user}(?!})""", RegexOption.IGNORE_CASE)

    fun replaceMacros(str: String?): String {
        if (str == null) return ""
        return macroUserBare.replace(
            macroCharBare.replace(
                macroCharColon.replace(macroUserColon.replace(str) { "{{user}}:" }) { "{{char}}:" },
            ) { "{{char}}" },
        ) { "{{user}}" }
    }

    private fun formatExampleMessages(examples: JsonElement?): String {
        if (examples !is JsonArray) return ""
        val sb = StringBuilder()
        for (example in examples) {
            val text = example.jsonObject["text"]?.jsonPrimitive?.contentOrNull() ?: continue
            if (text.isBlank()) continue
            sb.append("<START>\n").append(replaceMacros(text)).append("\n")
        }
        return sb.toString()
    }

    private fun formatAlternateGreetings(scenarios: List<JsonObject>): List<String> {
        if (scenarios.size <= 1) return emptyList()
        val firstScenarioFirst = scenarios[0]["firstMessages"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull() ?: ""
        val greetings = linkedSetOf<String>()
        for (scenario in scenarios.drop(1)) {
            val firstMessages = scenario["firstMessages"]?.jsonArray ?: continue
            if (firstMessages.isEmpty()) continue
            val first = firstMessages.first().jsonObject
            val text = first["text"]?.jsonPrimitive?.contentOrNull() ?: continue
            if (text != firstScenarioFirst) greetings.add(replaceMacros(text))
        }
        return greetings.toList()
    }

    private fun convertCharacterBook(items: JsonElement?): JsonObject? {
        if (items !is JsonArray || items.isEmpty()) return null
        val entries = mutableListOf<JsonElement>()
        items.forEachIndexed { index, item ->
            if (item !is JsonObject) return@forEachIndexed
            val key = item["key"]?.jsonPrimitive?.contentOrNull() ?: ""
            val value = item["value"]?.jsonPrimitive?.contentOrNull() ?: ""
            entries += buildJsonObject {
                put("keys", JsonArray(replaceMacros(key).split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                put("content", JsonPrimitive(replaceMacros(value)))
                put("extensions", JsonObject(emptyMap()))
                put("enabled", JsonPrimitive(true))
                put("insertion_order", JsonPrimitive(index))
            }
        }
        if (entries.isEmpty()) return null
        return buildJsonObject {
            put("entries", JsonArray(entries))
            put("extensions", JsonObject(emptyMap()))
        }
    }

    fun import(zipBytes: ByteArray): String {
        val files = readZip(zipBytes)
        val manifestJson = files["manifest.json"] ?: error("BYAF: manifest.json not found")
        val manifest = json.parseToJsonElement(manifestJson).jsonObject

        val characters = manifest["characters"]?.jsonArray ?: error("Invalid BYAF file: missing characters array")
        if (characters.isEmpty()) error("Invalid BYAF file: characters array is empty")
        val characterPath = characters.first().jsonPrimitive.content
        val characterJson = files[characterPath] ?: error("BYAF: character JSON not found: $characterPath")
        val character = json.parseToJsonElement(characterJson).jsonObject

        val scenarios = mutableListOf<JsonObject>()
        manifest["scenarios"]?.jsonArray?.forEach { pathElement ->
            val path = pathElement.jsonPrimitive.content
            val scenarioJson = files[path] ?: return@forEach
            scenarios += json.parseToJsonElement(scenarioJson).jsonObject
        }
        val scenario = scenarios.firstOrNull() ?: JsonObject(emptyMap())
        val author = manifest["author"]?.jsonObject ?: JsonObject(emptyMap())

        val name = character["name"]?.jsonPrimitive?.contentOrNull()
            ?: character["displayName"]?.jsonPrimitive?.contentOrNull() ?: ""
        val displayName = character["displayName"]?.jsonPrimitive?.contentOrNull()
        val isNsfw = character["isNSFW"]?.jsonPrimitive?.contentOrNull()?.toBooleanStrictOrNull() == true
        val firstMessages = scenario["firstMessages"]?.jsonArray
        val firstMes = firstMessages?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull() ?: ""

        return buildJsonObject {
            put("spec", JsonPrimitive("chara_card_v2"))
            put("spec_version", JsonPrimitive("2.0"))
            put("create_date", JsonPrimitive(Instant.now().toString()))
            put("data", buildJsonObject {
                put("name", JsonPrimitive(name))
                put("description", JsonPrimitive(replaceMacros(character["persona"]?.jsonPrimitive?.contentOrNull())))
                put("personality", JsonPrimitive(""))
                put("scenario", JsonPrimitive(replaceMacros(scenario["narrative"]?.jsonPrimitive?.contentOrNull())))
                put("first_mes", JsonPrimitive(replaceMacros(firstMes)))
                put("mes_example", JsonPrimitive(formatExampleMessages(scenario["exampleMessages"])))
                put("creator_notes", JsonPrimitive(author["backyardURL"]?.jsonPrimitive?.contentOrNull() ?: ""))
                put("system_prompt", JsonPrimitive(replaceMacros(scenario["formattingInstructions"]?.jsonPrimitive?.contentOrNull())))
                put("post_history_instructions", JsonPrimitive(""))
                put("alternate_greetings", JsonArray(formatAlternateGreetings(scenarios).map { JsonPrimitive(it) }))
                convertCharacterBook(character["loreItems"])?.let { put("character_book", it) }
                put("tags", JsonArray(if (isNsfw) listOf(JsonPrimitive("nsfw")) else emptyList()))
                put("creator", JsonPrimitive(author["name"]?.jsonPrimitive?.contentOrNull() ?: ""))
                put("character_version", JsonPrimitive(""))
                put("extensions", buildJsonObject {
                    if (displayName != null) put("display_name", JsonPrimitive(displayName))
                })
            })
        }.toString()
    }

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) files[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }
        return files
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (isString) content else null
}
