package com.emberinn.engine.card

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

    internal fun formatExampleMessages(examples: JsonElement?): String {
        if (examples !is JsonArray) return ""
        val sb = StringBuilder()
        for (example in examples) {
            val text = example.jsonObject["text"]?.jsonPrimitive?.contentOrNull() ?: continue
            if (text.isEmpty()) continue
            sb.append("<START>\n").append(replaceMacros(text)).append("\n")
        }
        // 官方 formattedExamples += ...; return formattedExamples.trimEnd()
        return sb.toString().trimEnd()
    }

    internal fun formatAlternateGreetings(scenarios: List<JsonObject>): List<String> {
        if (scenarios.size <= 1) return emptyList()
        val firstScenarioFirst: String? = scenarios[0]["firstMessages"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull()
        val greetings = linkedSetOf<String>()
        for (scenario in scenarios.drop(1)) {
            val firstMessages = scenario["firstMessages"]?.jsonArray ?: continue
            if (firstMessages.isEmpty()) continue
            val first = firstMessages.first().jsonObject
            val text = first["text"]?.jsonPrimitive?.contentOrNull() ?: continue
            // 官方：仅当 text 存在且与第一个场景的首条不同
            if (text != firstScenarioFirst) greetings.add(replaceMacros(text))
        }
        return greetings.toList()
    }

    internal fun convertCharacterBook(items: JsonElement?): JsonObject? {
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
        val manifest = json.parseToJsonElement(String(manifestJson, Charsets.UTF_8)).jsonObject

        val characters = manifest["characters"]?.jsonArray ?: error("Invalid BYAF file: missing characters array")
        if (characters.isEmpty()) error("Invalid BYAF file: characters array is empty")
        val characterPath = characters.first().jsonPrimitive.content
        val characterJson = files[characterPath] ?: error("BYAF: character JSON not found: $characterPath")
        val character = json.parseToJsonElement(String(characterJson, Charsets.UTF_8)).jsonObject

        val scenarios = mutableListOf<JsonObject>()
        manifest["scenarios"]?.jsonArray?.forEach { pathElement ->
            val path = pathElement.jsonPrimitive.content
            val scenarioJson = files[path] ?: return@forEach
            scenarios += json.parseToJsonElement(String(scenarioJson, Charsets.UTF_8)).jsonObject
        }
        val scenario = scenarios.firstOrNull() ?: JsonObject(emptyMap())
        val author = manifest["author"]?.jsonObject ?: JsonObject(emptyMap())

        val name = character["name"]?.jsonPrimitive?.contentOrNull()
            ?: character["displayName"]?.jsonPrimitive?.contentOrNull() ?: ""
        val displayName = character["displayName"]?.jsonPrimitive?.contentOrNull()
        val isNsfw = character["isNSFW"]?.jsonPrimitive?.let { p ->
            p.booleanOrNull ?: (p.content == "true")
        } == true
        val firstMessages = arrayOrNull(scenario["firstMessages"])
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

    /** BYAF 资源：头像/背景（对齐 ByafParser.getCharacterImages/getChatBackgrounds）。 */
    data class ByafAsset(val filename: String, val data: ByteArray, val label: String = "")

    data class ByafAssets(
        val images: List<ByafAsset>,
        val backgrounds: List<ByafAsset>,
    )

    fun extractAssets(zipBytes: ByteArray): ByafAssets {
        val files = readZip(zipBytes)
        val manifest = json.parseToJsonElement(String(files["manifest.json"] ?: return ByafAssets(emptyList(), emptyList()), Charsets.UTF_8))
            .jsonObject
        val characterPath = manifest["characters"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return ByafAssets(emptyList(), emptyList())
        val character = json.parseToJsonElement(String(files[characterPath] ?: return ByafAssets(emptyList(), emptyList()), Charsets.UTF_8))
            .jsonObject
        val characterName = character["name"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
        val baseDir = characterPath.substringBeforeLast('/', "")

        // 头像：character.images[]，路径相对角色文件目录
        val images = mutableListOf<ByafAsset>()
        character["images"]?.jsonArray?.forEach { imageEl ->
            val image = imageEl.jsonObject
            val imagePath = image["path"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEach
            val fullPath = joinRelative(baseDir, imagePath)
            val data = files[fullPath] ?: return@forEach
            images.add(ByafAsset(filename = imagePath.substringAfterLast('/'), data = data, label = image["label"]?.jsonPrimitive?.content ?: ""))
        }

        // 背景：scenarios[].backgroundImage，按字节去重
        val backgrounds = mutableListOf<ByafAsset>()
        var index = 1
        manifest["scenarios"]?.jsonArray?.forEach { pathEl ->
            val path = pathEl.jsonPrimitive.content
            val scenario = json.parseToJsonElement(String(files[path] ?: return@forEach, Charsets.UTF_8)).jsonObject
            val bgPath = scenario["backgroundImage"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: return@forEach
            val data = files[bgPath] ?: return@forEach
            if (backgrounds.none { it.data.contentEquals(data) }) {
                backgrounds.add(ByafAsset(filename = "${characterName} bg $index", data = data))
                index++
            }
        }

        return ByafAssets(images = images, backgrounds = backgrounds)
    }

    private fun joinRelative(baseDir: String, path: String): String {
        val parts = (if (baseDir.isEmpty()) "" else "$baseDir/") + path
        val segments = mutableListOf<String>()
        for (seg in parts.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(seg)
            }
        }
        return segments.joinToString("/")
    }

    /** BYAF 聊天背景（对齐官方 getChatFromScenario 的 chatBackgrounds 参数）。 */
    data class ByafChatBackground(val name: String, val paths: List<String>)

    /**
     * 对齐官方 ByafParser.getChatFromScenario：返回聊天消息列表（jsonl 行）。
     * 含 chat_metadata（模型设置/场景/示例/系统提示）与开场白；human/ai 消息按时间交错重排。
     */
    fun chatFromScenario(
        scenarioJson: JsonElement?,
        userName: String,
        characterName: String,
        chatBackgrounds: List<ByafChatBackground> = emptyList(),
        now: String = Instant.now().toString(),
    ): List<JsonElement> {
        val scenario = scenarioJson as? JsonObject ?: JsonObject(emptyMap())
        val messages = arrayOrNull(scenario["messages"])

        // 官方：空数组→现在；null→undefined（开场白不写 send_date）；否则取首个带 createdAt 的原始值
        val chatStartDate: JsonElement? = when {
            messages != null && messages.isEmpty() -> JsonPrimitive(now)
            messages != null -> messages.firstOrNull { it.jsonObject.containsKey("createdAt") }
                ?.jsonObject?.get("createdAt")
            else -> null
        }

        val backgroundImage = scenario["backgroundImage"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
        val chatBackground = chatBackgrounds.firstOrNull { backgroundImage in it.paths }?.name ?: ""

        val chat = mutableListOf<JsonElement>()
        chat += buildJsonObject {
            put("user_name", JsonPrimitive("unused"))
            put("character_name", JsonPrimitive("unused"))
            put("chat_metadata", buildJsonObject {
                // 官方 scenario 不做宏替换；system_prompt 做
                put("scenario", JsonPrimitive(scenario["narrative"]?.jsonPrimitive?.contentOrNull() ?: ""))
                put("mes_example", JsonPrimitive(formatExampleMessages(scenario["exampleMessages"])))
                put("system_prompt", JsonPrimitive(replaceMacros(scenario["formattingInstructions"]?.jsonPrimitive?.contentOrNull())))
                put("mes_examples_optional", scenario["canDeleteExampleMessages"] ?: JsonPrimitive(false))
                put("byaf_model_settings", buildJsonObject {
                    put("model", scenario["model"] ?: JsonPrimitive(""))
                    put("temperature", scenario["temperature"] ?: JsonPrimitive(1.2))
                    put("top_k", scenario["topK"] ?: JsonPrimitive(40))
                    put("top_p", scenario["topP"] ?: JsonPrimitive(0.9))
                    put("min_p", scenario["minP"] ?: JsonPrimitive(0.1))
                    put("min_p_enabled", scenario["minPEnabled"] ?: JsonPrimitive(true))
                    put("repeat_penalty", scenario["repeatPenalty"] ?: JsonPrimitive(1.05))
                    put("repeat_penalty_tokens", scenario["repeatLastN"] ?: JsonPrimitive(256))
                    put("by_prompt_template", scenario["promptTemplate"] ?: JsonPrimitive("general"))
                    put("grammar", scenario["grammar"] ?: JsonNull)
                })
                put("chat_backgrounds", if (chatBackground.isEmpty()) JsonArray(emptyList()) else JsonArray(listOf(JsonPrimitive(chatBackground))))
                put("custom_background", if (chatBackground.isEmpty()) JsonPrimitive("") else JsonPrimitive("url(\"${encodeUri(chatBackground)}\")"))
            })
        }

        // 开场白（官方仅在 text 非空时加入）
        val firstMessages = arrayOrNull(scenario["firstMessages"])
        if (!firstMessages.isNullOrEmpty()) {
            val firstText = firstMessages.first().jsonObject["text"]?.jsonPrimitive?.contentOrNull()
            if (!firstText.isNullOrEmpty()) {
                chat += buildJsonObject {
                    put("name", JsonPrimitive(characterName))
                    put("is_user", JsonPrimitive(false))
                    if (chatStartDate != null) put("send_date", chatStartDate)
                    put("mes", JsonPrimitive(firstText))
                }
            }
        }

        fun newestOutput(ai: JsonObject): JsonObject {
            val outputs = arrayOrNull(ai["outputs"]) ?: return JsonObject(emptyMap())
            // 官方 reduce：Date(activeTimestamp) 比较，相等保留前一个
            val best = outputs.fold<JsonElement, JsonObject?>(null) { acc, el ->
                val obj = el.jsonObject
                if (acc == null) obj
                else {
                    val a = obj["activeTimestamp"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Double.NaN
                    val b = acc["activeTimestamp"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Double.NaN
                    if (a >= b) obj else acc
                }
            }
            return best ?: JsonObject(emptyMap())
        }

        fun swipes(ai: JsonObject): List<String> =
            arrayOrNull(ai["outputs"])?.map { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull() ?: "" }
                ?: emptyList()

        fun sendDate(el: JsonElement?): JsonElement {
            val content = el?.jsonPrimitive?.content ?: return JsonNull
            val n = content.toLongOrNull()
            return if (n != null) JsonPrimitive(n) else JsonNull
        }

        fun putMessageText(builder: kotlinx.serialization.json.JsonObjectBuilder, text: String?) {
            if (text != null) builder.put("mes", JsonPrimitive(text))
        }

        val userMessages = messages?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "human" } ?: emptyList()
        val characterMessages = messages?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "ai" } ?: emptyList()

        if (userMessages.isNotEmpty() && userMessages.size == characterMessages.size) {
            for (i in userMessages.indices) {
                chat += buildJsonObject {
                    put("name", JsonPrimitive(userName))
                    put("is_user", JsonPrimitive(true))
                    put("send_date", sendDate(userMessages[i].jsonObject["createdAt"]))
                    putMessageText(this, userMessages[i].jsonObject["text"]?.jsonPrimitive?.contentOrNull())
                }
                val ai = characterMessages[i].jsonObject
                val newest = newestOutput(ai)
                val aiSwipes = swipes(ai)
                chat += buildJsonObject {
                    put("name", JsonPrimitive(characterName))
                    put("is_user", JsonPrimitive(false))
                    put("send_date", sendDate(newest["createdAt"]))
                    putMessageText(this, newest["text"]?.jsonPrimitive?.contentOrNull())
                    put("swipes", JsonArray(aiSwipes.map { JsonPrimitive(it) }))
                    put("swipe_id", JsonPrimitive(aiSwipes.indexOf(newest["text"]?.jsonPrimitive?.contentOrNull() ?: "")))
                }
            }
        } else if (messages != null) {
            for (m in messages) {
                val obj = m.jsonObject
                val isUser = obj["type"]?.jsonPrimitive?.content == "human"
                val aiMessage = if (!isUser) newestOutput(obj) else null
                chat += buildJsonObject {
                    put("name", JsonPrimitive(if (isUser) userName else characterName))
                    put("is_user", JsonPrimitive(isUser))
                    put("send_date", sendDate(if (isUser) obj["createdAt"] else aiMessage?.get("createdAt")))
                    putMessageText(this, if (isUser) obj["text"]?.jsonPrimitive?.contentOrNull() else aiMessage?.get("text")?.jsonPrimitive?.contentOrNull())
                    if (!isUser && aiMessage != null) {
                        val aiSwipes = swipes(obj)
                        put("swipes", JsonArray(aiSwipes.map { JsonPrimitive(it) }))
                        put("swipe_id", JsonPrimitive(aiSwipes.indexOf(aiMessage["text"]?.jsonPrimitive?.contentOrNull() ?: "")))
                    }
                }
            }
        }

        return chat
    }

    /** JS encodeURI：保留未保留字符与 ;,/?:@&=+$#，其余按 UTF-8 百分号编码。 */
    private fun encodeUri(value: String): String {
        val sb = StringBuilder()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            val keep = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c in "-_.!~*'();,/?:@&=+$#".map { it.code }
            if (keep) sb.append(c.toChar()) else sb.append('%').append(HEX[c ushr 4]).append(HEX[c and 0x0f])
        }
        return sb.toString()
    }

    private val HEX = "0123456789ABCDEF"

    private fun arrayOrNull(el: JsonElement?): JsonArray? =
        if (el == null || el is JsonNull) null else el.jsonArray

    private fun sendDateElement(el: JsonElement?): JsonElement {
        val p = el?.jsonPrimitive ?: return JsonNull
        val n = p.content.toLongOrNull()
        return if (n != null) JsonPrimitive(n) else p
    }

    private fun doubleOf(el: JsonElement?, def: Double): Double =
        el?.jsonPrimitive?.let { it.content.toDoubleOrNull() } ?: def

    private fun intOf(el: JsonElement?, def: Int): Int =
        el?.jsonPrimitive?.let { it.content.toIntOrNull() } ?: def

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (isString) content else null
}
