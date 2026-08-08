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

    fun import(zipBytes: ByteArray, now: String = Instant.now().toString()): String {
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
        return buildCard(manifest["author"]?.jsonObject ?: JsonObject(emptyMap()), character, scenarios, now)
    }

    /** 对齐官方 ByafParser.getCharacterCard：manifest/character/scenarios → V2 卡 JSON。 */
    internal fun buildCard(
        manifest: JsonObject,
        character: JsonObject,
        scenarios: List<JsonObject>,
        now: String,
    ): String {
        val scenario = scenarios.firstOrNull() ?: JsonObject(emptyMap())

        val name = character["name"]?.jsonPrimitive?.contentOrNull()
            ?: character["displayName"]?.jsonPrimitive?.contentOrNull() ?: ""
        val displayName = character["displayName"]?.jsonPrimitive?.contentOrNull()
        // 官方 getCharacterCard：character?.isNSFW 原始真值（字符串 "false" 也是 true）
        val isNsfwRaw = character["isNSFW"]
        val isNsfw = when {
            isNsfwRaw == null || isNsfwRaw is JsonNull -> false
            isNsfwRaw is JsonPrimitive && isNsfwRaw.isString -> true
            else -> isNsfwRaw.jsonPrimitive.content != "false"
        }
        val firstMessages = arrayOrNull(scenario["firstMessages"])
        val firstMes = firstMessages?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull() ?: ""

        return buildJsonObject {
            put("spec", JsonPrimitive("chara_card_v2"))
            put("spec_version", JsonPrimitive("2.0"))
            put("create_date", JsonPrimitive(now))
            put("data", buildJsonObject {
                put("name", JsonPrimitive(name))
                put("description", JsonPrimitive(replaceMacros(character["persona"]?.jsonPrimitive?.contentOrNull())))
                put("personality", JsonPrimitive(""))
                put("scenario", JsonPrimitive(replaceMacros(scenario["narrative"]?.jsonPrimitive?.contentOrNull())))
                put("first_mes", JsonPrimitive(replaceMacros(firstMes)))
                put("mes_example", JsonPrimitive(formatExampleMessages(scenario["exampleMessages"])))
                put("creator_notes", JsonPrimitive(manifest["backyardURL"]?.jsonPrimitive?.contentOrNull() ?: ""))
                put("system_prompt", JsonPrimitive(replaceMacros(scenario["formattingInstructions"]?.jsonPrimitive?.contentOrNull())))
                put("post_history_instructions", JsonPrimitive(""))
                put("alternate_greetings", JsonArray(formatAlternateGreetings(scenarios).map { JsonPrimitive(it) }))
                convertCharacterBook(character["loreItems"])?.let { put("character_book", it) }
                put("tags", JsonArray(if (isNsfw) listOf(JsonPrimitive("nsfw")) else emptyList()))
                put("creator", JsonPrimitive(manifest["name"]?.jsonPrimitive?.contentOrNull() ?: ""))
                put("character_version", JsonPrimitive(""))
                put("extensions", buildJsonObject {
                    if (displayName != null) put("display_name", JsonPrimitive(displayName))
                })
            })
        }.toString()
    }

    /** BYAF 完整导入计划（对齐 importFromByaf；App 层按计划落盘）。 */
    data class ByafChatPlan(val filePath: String, val fileName: String, val content: String)
    data class ByafBackgroundPlan(val filePath: String, val data: ByteArray)
    data class ByafIconPlan(val filePath: String, val data: ByteArray)

    data class ByafImportPlan(
        val fileName: String,
        val cardJson: String,
        val avatar: ByteArray?,
        val chats: List<ByafChatPlan>,
        val backgrounds: List<ByafBackgroundPlan>,
        val icons: List<ByafIconPlan>,
    )

    fun importPlan(
        zipBytes: ByteArray,
        userName: String,
        now: String = Instant.now().toString(),
        chatNow: String = V2Normalizer.humanizedDateTime(),
        preservedFileName: String? = null,
        existingFiles: Set<String> = emptySet(),
    ): ByafImportPlan {
        val files = readZip(zipBytes)
        val manifest = json.parseToJsonElement(String(files["manifest.json"] ?: error("BYAF: manifest.json not found"), Charsets.UTF_8)).jsonObject
        val characters = manifest["characters"]?.jsonArray ?: error("Invalid BYAF file: missing characters array")
        val characterPath = characters.first().jsonPrimitive.content
        val character = json.parseToJsonElement(String(files[characterPath] ?: error("BYAF: character JSON not found: $characterPath"), Charsets.UTF_8)).jsonObject
        val scenarios = mutableListOf<JsonObject>()
        manifest["scenarios"]?.jsonArray?.forEach { pathElement ->
            val path = pathElement.jsonPrimitive.content
            val scenarioJson = files[path] ?: return@forEach
            scenarios += json.parseToJsonElement(String(scenarioJson, Charsets.UTF_8)).jsonObject
        }
        val author = manifest["author"]?.jsonObject ?: JsonObject(emptyMap())

        var cardJson = V2Normalizer.normalize(buildCard(author, character, scenarios, now), now = chatNow)
        val parsedCard = json.parseToJsonElement(cardJson).jsonObject
        val cardName = parsedCard["name"]?.jsonPrimitive?.content ?: ""
        val displayName = character["displayName"]?.jsonPrimitive?.content ?: ""
        val fileName = preservedFileName ?: sanitizeReplacement(displayName.ifEmpty { cardName })
        val fileNameBase = fileName.substringAfterLast('/')

        val chats = mutableListOf<ByafChatPlan>()
        val backgroundPlans = mutableListOf<ByafBackgroundPlan>()
        val iconPlans = mutableListOf<ByafIconPlan>()

        if (preservedFileName == null) {
            // 背景：按数据去重，名称 = {fileName}_bg + 唯一后缀
            val updatedBackgrounds = mutableListOf<ByafChatBackground>()
            val seenData = mutableListOf<ByteArray>()
            for (scenario in scenarios) {
                val bgPath = scenario["backgroundImage"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: continue
                val data = files[bgPath] ?: continue
                if (seenData.any { it.contentEquals(data) }) continue
                seenData += data
                val ext = if (bgPath.contains('.')) bgPath.substringAfterLast('.') else "png"
                val base = "${fileNameBase}_bg"
                val unique = uniqueName(base) { candidate ->
                    "/images/$fileNameBase/$candidate.$ext" in existingFiles ||
                        backgroundPlans.any { it.filePath.endsWith("/$candidate.$ext") }
                }
                val newFile = "$unique.$ext"
                val filePath = "/images/$fileNameBase/$newFile"
                backgroundPlans += ByafBackgroundPlan(filePath, data)
                updatedBackgrounds += ByafChatBackground(name = filePath, paths = listOf(bgPath))
            }

            for (scenario in scenarios) {
                val title = (scenario["title"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: "").ifEmpty { cardName }
                val chatName = sanitizeReplacement("$title - $chatNow imported.jsonl")
                val filePath = "/chats/$fileNameBase/$chatName"
                val content = chatFromScenario(scenario, userName, cardName, updatedBackgrounds, now).joinToString("\n") { it.toString() }
                chats += ByafChatPlan(filePath = filePath, fileName = chatName, content = content)
            }

            if (chats.isNotEmpty()) {
                val firstChat = chats.first().fileName
                val chatBase = firstChat.substringBeforeLast('.')
                cardJson = json.parseToJsonElement(cardJson).jsonObject.toMutableMap().apply {
                    put("chat", JsonPrimitive(chatBase))
                }.let { JsonObject(it).toString() }
            }

            // 备用图标（第一张是头像，其余按 label 唯一名写入角色目录）
            val assets = extractAssets(zipBytes)
            for (icon in assets.images.drop(1)) {
                val ext = if (icon.filename.contains('.')) icon.filename.substringAfterLast('.') else "png"
                val label = sanitizeReplacement(icon.label).ifEmpty { "alt" }
                val folder = "/chars/${sanitizeReplacement(cardName)}"
                val unique = uniqueName(label) { candidate ->
                    "$folder/$candidate.$ext" in existingFiles ||
                        iconPlans.any { it.filePath.endsWith("/$candidate.$ext") }
                }
                iconPlans += ByafIconPlan(filePath = "$folder/$unique.$ext", data = icon.data)
            }
        }

        val assets = extractAssets(zipBytes)
        val avatar = assets.images.firstOrNull()?.data
        return ByafImportPlan(
            fileName = fileName,
            cardJson = cardJson,
            avatar = avatar,
            chats = chats,
            backgrounds = backgroundPlans,
            icons = iconPlans,
        )
    }

    private fun uniqueName(base: String, exists: (String) -> Boolean): String {
        var name = base
        var i = 1
        while (exists(name)) {
            name = "$base (${i++})"
        }
        return name
    }

    /** 对齐 sanitize-filename replacement='_'：非法/控制字符替换为 _，保留名/去尾部点空格/255 字节截断。 */
    private fun sanitizeReplacement(name: String): String {
        val forbidden = "\\\\/?<>\\\\:*|\\\"".toSet()
        val removed = buildString {
            for (c in name) {
                val code = c.code
                if (c in forbidden || code in 0..31 || code in 0x80..0x9f) append('_') else append(c)
            }
        }
        val noReserved = when {
            removed.all { it == '.' } -> ""
            Regex("""^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?$""", RegexOption.IGNORE_CASE).matches(removed) -> ""
            else -> removed
        }
        return noReserved.trimEnd('.', ' ').take(255)
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
            val fullPath = urlJoin(baseDir, imagePath)
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

    /** 官方 url-join：只归一重复斜杠，不折叠 ../（BYAF 路径按 zip 原样查）。 */
    private fun urlJoin(baseDir: String, path: String): String {
        val joined = (if (baseDir.isEmpty()) "" else "$baseDir/") + path
        return joined.replace(Regex("/+"), "/")
    }

    /** 官方 getCharacterImages/getChatBackgrounds 的 1:1 输出（差分见 byaf-assets-official.mjs）。 */
    data class ByafImageAsset(val filename: String, val data: ByteArray, val label: String)
    data class ByafBackgroundAsset(val name: String, val data: ByteArray, val paths: List<String>)

    /** JS 模板字符串语义：缺失→"undefined"、null→"null"。 */
    private fun jsString(el: JsonElement?): String = when (el) {
        null -> "undefined"
        is JsonNull -> "null"
        is JsonPrimitive -> el.content
        else -> el.toString()
    }

    fun extractCharacterImages(
        files: Map<String, ByteArray>,
        character: JsonObject,
        characterPath: String,
        defaultAvatar: ByteArray,
    ): List<ByafImageAsset> {
        val characterImages = character["images"]?.jsonArray
        if (characterImages == null || characterImages.isEmpty()) {
            return listOf(ByafImageAsset(filename = "", data = defaultAvatar, label = ""))
        }
        val out = mutableListOf<ByafImageAsset>()
        val baseDir = if (characterPath.contains('/')) characterPath.substringBeforeLast('/') else ""
        for (imageEl in characterImages) {
            val image = imageEl.jsonObject
            val imagePath = image["path"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: continue
            val fullPath = urlJoin(baseDir, imagePath)
            val data = files[fullPath] ?: continue
            out += ByafImageAsset(
                filename = imagePath.substringAfterLast('/'),
                data = data,
                label = image["label"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: "",
            )
        }
        if (out.isEmpty()) return listOf(ByafImageAsset(filename = "", data = defaultAvatar, label = ""))
        return out
    }

    fun extractChatBackgrounds(
        files: Map<String, ByteArray>,
        character: JsonObject,
        scenarios: List<JsonObject>,
    ): List<ByafBackgroundAsset> {
        val backgrounds = mutableListOf<ByafBackgroundAsset>()
        var index = 1
        val characterName = jsString(character["name"])
        for (scenario in scenarios) {
            val bgPath = scenario["backgroundImage"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: continue
            val data = files[bgPath] ?: continue
            val existing = backgrounds.indexOfFirst { it.data.contentEquals(data) }
            if (existing != -1) {
                backgrounds[existing] = backgrounds[existing].copy(paths = backgrounds[existing].paths + bgPath)
                continue
            }
            backgrounds += ByafBackgroundAsset(name = "$characterName bg $index", data = data, paths = listOf(bgPath))
            index++
        }
        return backgrounds
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
