package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 角色卡 JSON 解析缓存：详情页 6 个读取函数共用一个解析结果（大卡一次解析，不再重复 parse）。
 * LRU 上限 16 张卡；保存/导入会生成新 raw 字符串，天然失效。
 */
private val parsedCardCache = object : LinkedHashMap<String, JsonObject>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?): Boolean = size > 16
}

private fun parseCached(raw: String): JsonObject = synchronized(parsedCardCache) {
    parsedCardCache.getOrPut(raw) { Json.parseToJsonElement(raw).jsonObject }
}

/** 角色详情页可编辑字段快照（官方 v2 归一字段；tags 逗号拼接、depth_prompt 兼容对象/字符串、talkativeness 读 extensions）。 */
data class CharacterDetailFields(
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val mesExample: String,
    val systemPrompt: String,
    val postHistoryInstructions: String,
    val creatorNotes: String,
    val creator: String,
    val characterVersion: String,
    val tags: String,
    val depthPrompt: String,
    val depthPromptDepth: String,
    val depthPromptRole: String,
    val talkativeness: Float,
    val alternateGreetings: List<String>,
)

/** 世界书条目编辑草稿（字段对齐官方 v2DataWorldInfoEntry 常用项）。 */
data class WorldEntryDraft(
    val id: Int,
    val keys: String,
    val keySecondary: String = "",
    val content: String,
    val comment: String,
    val constant: Boolean,
    val selective: Boolean,
    val selectiveLogic: Int = 0,
    val enabled: Boolean,
    val insertionOrder: Int,
    /** 官方 world_info_position 整数：0 before / 1 after / 2 ANTop / 3 ANBottom / 4 atDepth / 5 EMTop / 6 EMBottom / 7 outlet。 */
    val position: Int = 0,
    val depth: Int? = 4,
    val role: String = "system",
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = false,
    val scanDepth: Int? = null,
    val matchPersonaDescription: Boolean = false,
    val matchCharacterDescription: Boolean = false,
    val matchCharacterPersonality: Boolean = false,
    val matchCharacterDepthPrompt: Boolean = false,
    val matchScenario: Boolean = false,
    val matchCreatorNotes: Boolean = false,
    val preventRecursion: Boolean = false,
    val excludeRecursion: Boolean = false,
    val delayUntilRecursion: Int = 0,
    val useProbability: Boolean = true,
    val probability: Int = 100,
    val ignoreBudget: Boolean = false,
    val triggers: String = "",
    val outletName: String = "",
    val sticky: Int? = null,
    val cooldown: Int? = null,
    val delay: Int? = null,
    val group: String = "",
    val groupWeight: Int = 100,
    val groupOverride: Boolean = false,
    val useGroupScoring: Boolean = false,
    val characterFilterNames: String = "",
    val characterFilterTags: String = "",
    val characterFilterExclude: Boolean = false,
    val vectorized: Boolean = false,
    val addMemo: Boolean = false,
    val automationId: String = "",
    val displayIndex: Int? = null,
)



/** 角色级主题配方（README 承诺：seed/背景/形状/字体/风格档位/浅深锁定，角色卡驱动主题；官方无对应字段）。 */
data class ThemeRecipe(
    val seed: String = "",
    val background: String = "",
    val shape: String = "",        // "" 跟随全局 / "square" 4dp / "rounded" 16dp / "circle" 24dp
    val font: String = "",         // "" 系统 / "lxgw" 霞鹜文楷 / "source" 思源宋体
    val style: String = "",        // "" 跟随全局 / "airy" / "calm" / "vivid"
    val lockMode: String = "",     // "" 跟随全局 / "system" / "light" / "dark"
)

/** 角色级模型覆盖（README 角色页承诺；官方无角色级字段，存 data.extensions.emberinn_model_override，见第 8 节）。 */
data class ModelOverride(
    val model: String = "",
    val maxTokens: Int? = null,
    val contextWindow: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val presencePenalty: Double? = null,
    val frequencyPenalty: Double? = null,
)

/** 该卡正则脚本（对齐官方 char-data.js RegexScriptData；缺失字段用官方默认）。 */
data class CharacterRegexScript(
    val id: String,
    val scriptName: String,
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val placement: List<Int> = listOf(1, 2, 5, 6),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = true,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val substituteRegex: Int = 0,
)

/**
 * 角色卡 data 层读改写（纯逻辑，App 详情页 + 单元测试共用）。
 *
 * 关键点（对照官方 readFromV2 / char-data.js / slash-commands.js）：
 * - depth_prompt / talkativeness 的官方位置是 data.extensions；读优先 extensions、兼容旧 data 顶层；
 * - 保存按 V2 归一写回，并同步 readFromV2 会提升到根部的字段（name/description/…/tags/talkativeness/fav），
 *   保证整卡 root 与 data 一致（导出/其它客户端读到的是同一份字段）；
 * - 世界书保存只覆盖编辑字段，其余未知字段（probability/vectorized/automationId/displayIndex/extensions/…）
 *   原样保留，v1（key/order/disable）在保存时归一为 v2（keys/insertion_order/enabled）。
 */
object CharacterCardEdit {

    private val json = Json { ignoreUnknownKeys = true }

    fun dataLayer(root: JsonObject): JsonObject = root["data"]?.jsonObject ?: root

    fun readFields(raw: String, fallbackName: String, fallbackDescription: String): CharacterDetailFields =
        runCatching {
            val root = parseCached(raw)
            val data = dataLayer(root)
            val ext = data["extensions"]?.jsonObject
            fun str(key: String): String = (data[key] as? JsonPrimitive)?.contentOrNull ?: ""
            val dpExt = ext?.get("depth_prompt")?.jsonObject
            val legacyDp = data["depth_prompt"]
            val legacyDpObj = legacyDp as? JsonObject
            CharacterDetailFields(
                name = str("name"),
                description = str("description"),
                personality = str("personality"),
                scenario = str("scenario"),
                firstMes = str("first_mes"),
                mesExample = str("mes_example"),
                systemPrompt = str("system_prompt"),
                postHistoryInstructions = str("post_history_instructions"),
                creatorNotes = str("creator_notes"),
                creator = str("creator"),
                characterVersion = str("character_version"),
                tags = (data["tags"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(", ") ?: "",
                depthPrompt = dpExt?.get("prompt")?.jsonPrimitive?.contentOrNull
                    ?: legacyDpObj?.get("prompt")?.jsonPrimitive?.contentOrNull
                    ?: (legacyDp as? JsonPrimitive)?.contentOrNull ?: "",
                depthPromptDepth = dpExt?.get("depth")?.jsonPrimitive?.contentOrNull
                    ?: legacyDpObj?.get("depth")?.jsonPrimitive?.contentOrNull ?: "4",
                depthPromptRole = dpExt?.get("role")?.jsonPrimitive?.contentOrNull
                    ?: legacyDpObj?.get("role")?.jsonPrimitive?.contentOrNull ?: "system",
                talkativeness = ext?.get("talkativeness")?.jsonPrimitive?.floatOrNull
                    ?: (data["talkativeness"] as? JsonPrimitive)?.floatOrNull ?: 0.5f,
                alternateGreetings = (data["alternate_greetings"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
            )
        }.getOrElse {
            CharacterDetailFields(
                name = fallbackName,
                description = fallbackDescription,
                personality = "", scenario = "", firstMes = "", mesExample = "",
                systemPrompt = "", postHistoryInstructions = "", creatorNotes = "",
                creator = "", characterVersion = "", tags = "", depthPrompt = "",
                depthPromptDepth = "4", depthPromptRole = "system", talkativeness = 0.5f,
                alternateGreetings = emptyList(),
            )
        }

    /** 读取角色卡内嵌世界书条目（兼容 v2 keys / v1 key、enabled / disable 反向、keys 逗号字符串）。 */
    fun readWorldEntries(raw: String): List<WorldEntryDraft> = runCatching {
        val root = parseCached(raw)
        val data = dataLayer(root)
        val entries = (bookOf(root, data)?.get("entries") as? JsonArray)
            ?: return@runCatching emptyList()
        entries.mapIndexedNotNull { i, el ->
            val e = (el as? JsonObject) ?: return@mapIndexedNotNull null
            val keysArr = (e["keys"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val keysStr = (e["keys"] as? JsonPrimitive)?.contentOrNull
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            val keySingle = (e["key"] as? JsonPrimitive)?.contentOrNull
            val allKeys = keysArr ?: keysStr ?: listOfNotNull(keySingle)
            val enabledRaw = (e["enabled"] as? JsonPrimitive)?.booleanOrNull
            val disableRaw = (e["disable"] as? JsonPrimitive)?.booleanOrNull
            fun str(key: String): String = (e[key] as? JsonPrimitive)?.contentOrNull ?: ""
            fun bool(key: String, def: Boolean): Boolean = (e[key] as? JsonPrimitive)?.booleanOrNull ?: def
            fun int(key: String): Int? = (e[key] as? JsonPrimitive)?.intOrNull
            fun intOr(key: String, def: Int): Int = int(key) ?: def
            fun list(obj: JsonObject, key: String): List<String> =
                (obj[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?: (obj[key] as? JsonPrimitive)?.contentOrNull
                        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()
            val ext = (e["extensions"] as? JsonObject) ?: JsonObject(emptyMap())
            fun extBool(key: String, def: Boolean): Boolean = (ext[key] as? JsonPrimitive)?.booleanOrNull ?: def
            fun extInt(key: String): Int? = (ext[key] as? JsonPrimitive)?.intOrNull
            fun extStr(key: String): String = (ext[key] as? JsonPrimitive)?.contentOrNull ?: ""
            val filter = e["characterFilter"]?.jsonObject
            WorldEntryDraft(
                id = (e["id"] as? JsonPrimitive)?.intOrNull ?: (i + 1),
                keys = allKeys.joinToString(", "),
                keySecondary = list(e, "keysecondary").ifEmpty { list(e, "secondary_keys") }.joinToString(", "),
                content = (e["content"] as? JsonPrimitive)?.contentOrNull ?: "",
                comment = (e["comment"] as? JsonPrimitive)?.contentOrNull ?: "",
                constant = (e["constant"] as? JsonPrimitive)?.booleanOrNull ?: false,
                selective = (e["selective"] as? JsonPrimitive)?.booleanOrNull ?: true,
                selectiveLogic = intOr("selectiveLogic", 0),
                enabled = enabledRaw ?: (disableRaw?.let { !it } ?: true),
                insertionOrder = (e["insertion_order"] as? JsonPrimitive)?.intOrNull
                    ?: (e["order"] as? JsonPrimitive)?.intOrNull ?: 100,
                position = intOr("position", 0),
                depth = int("depth") ?: 4,
                role = str("role").ifBlank { "system" },
                caseSensitive = bool("caseSensitive", false) || extBool("case_sensitive", false),
                matchWholeWords = bool("matchWholeWords", false) || extBool("match_whole_words", false),
                scanDepth = int("scanDepth") ?: extInt("scan_depth"),
                matchPersonaDescription = bool("matchPersonaDescription", false),
                matchCharacterDescription = bool("matchCharacterDescription", false),
                matchCharacterPersonality = bool("matchCharacterPersonality", false),
                matchCharacterDepthPrompt = bool("matchCharacterDepthPrompt", false),
                matchScenario = bool("matchScenario", false),
                matchCreatorNotes = bool("matchCreatorNotes", false),
                preventRecursion = bool("preventRecursion", false) || extBool("prevent_recursion", false),
                excludeRecursion = bool("excludeRecursion", false) || extBool("exclude_recursion", false),
                delayUntilRecursion = int("delayUntilRecursion") ?: extInt("delay_until_recursion") ?: 0,
                useProbability = bool("useProbability", true),
                probability = intOr("probability", 100),
                ignoreBudget = bool("ignoreBudget", false) || extBool("ignore_budget", false),
                triggers = list(e, "triggers").joinToString(", "),
                outletName = str("outletName"),
                sticky = int("sticky"),
                cooldown = int("cooldown"),
                delay = int("delay"),
                group = str("group"),
                groupWeight = intOr("groupWeight", 100),
                groupOverride = bool("groupOverride", false),
                useGroupScoring = bool("useGroupScoring", false),
                characterFilterNames = filter?.let { list(it, "names") }?.joinToString(", ")
                    ?: list(e, "characterFilterNames").joinToString(", "),
                characterFilterTags = filter?.let { list(it, "tags") }?.joinToString(", ")
                    ?: list(e, "characterFilterTags").joinToString(", "),
                characterFilterExclude = filter?.let { (it["isExclude"] as? JsonPrimitive)?.booleanOrNull ?: false }
                    ?: bool("characterFilterExclude", false),
                vectorized = bool("vectorized", false) || extBool("vectorized", false),
                addMemo = bool("addMemo", false),
                automationId = str("automationId").ifBlank { extStr("automation_id") },
                displayIndex = int("displayIndex") ?: int("display_index") ?: extInt("display_index"),
            )
        }
    }.getOrDefault(emptyList())

    /** 官方 data.extensions.world：角色关联的外置世界名。 */
    fun readWorldLink(raw: String): String? = runCatching {
        dataLayer(parseCached(raw))["extensions"]?.jsonObject
            ?.get("world")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrDefault(null)

    /** 保存角色关联的外置世界（官方 data.extensions.world）。 */
    fun applyWorldLink(raw: String, worldName: String): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (worldName.isBlank()) ext.remove("world") else ext["world"] = JsonPrimitive(worldName)
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }

    /** 保存角色字段：v2 归一写回（tags 数组、depth_prompt 进 extensions、talkativeness 进 extensions、alternate_greetings 数组）。 */
    fun applyFields(raw: String, fields: CharacterDetailFields): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        fun put(key: String, v: String) { m[key] = JsonPrimitive(v) }
        put("name", fields.name)
        put("description", fields.description)
        put("personality", fields.personality)
        put("scenario", fields.scenario)
        put("first_mes", fields.firstMes)
        put("mes_example", fields.mesExample)
        put("system_prompt", fields.systemPrompt)
        put("post_history_instructions", fields.postHistoryInstructions)
        put("creator_notes", fields.creatorNotes)
        put("creator", fields.creator)
        put("character_version", fields.characterVersion)
        val tags = fields.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) m.remove("tags") else m["tags"] = JsonArray(tags.map(::JsonPrimitive))

        // 官方位置：data.extensions.depth_prompt / data.extensions.talkativeness（char-data.js / slash-commands.js）
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        ext["talkativeness"] = JsonPrimitive(fields.talkativeness)
        ext["depth_prompt"] = buildJsonObject {
            put("prompt", JsonPrimitive(fields.depthPrompt))
            put("depth", JsonPrimitive(fields.depthPromptDepth.toIntOrNull() ?: 4))
            put("role", JsonPrimitive(fields.depthPromptRole))
        }
        m["extensions"] = JsonObject(ext)
        // 归一：旧顶层字段迁进 extensions 后移除，避免双份语义
        m.remove("depth_prompt")
        m.remove("talkativeness")

        if (fields.alternateGreetings.isEmpty()) {
            m.remove("alternate_greetings")
        } else {
            m["alternate_greetings"] = JsonArray(fields.alternateGreetings.map(::JsonPrimitive))
        }
        JsonObject(m)
    }

    /** 保存世界书条目：只覆盖编辑字段，未知字段原样保留；v1 字段归一为 v2。 */
    fun applyWorldEntries(raw: String, entries: List<WorldEntryDraft>): String {
        val rootOfRaw = json.parseToJsonElement(raw).jsonObject
        return updateData(raw) { data ->
            val m = data.toMutableMap()
            val originalEntries = (bookOf(rootOfRaw, data)?.get("entries") as? JsonArray)
                ?.map { it as? JsonObject } ?: emptyList()
            val entriesJson = JsonArray(entries.mapIndexed { i, d ->
                val base = originalEntries.getOrNull(i)?.toMutableMap() ?: mutableMapOf()
                base.remove("key")
                base.remove("order")
                base.remove("disable")
                base.remove("caseSensitive")
                base.remove("matchWholeWords")
                base.remove("scanDepth")
                base.remove("preventRecursion")
                base.remove("excludeRecursion")
                base.remove("delayUntilRecursion")
                base.remove("ignoreBudget")
                base.remove("vectorized")
                base.remove("automationId")
                base.remove("displayIndex")
                base.remove("display_index")
                base.remove("characterFilter")
                base.remove("characterFilterNames")
                base.remove("characterFilterTags")
                base.remove("characterFilterExclude")
                base["id"] = JsonPrimitive(d.id)
                base["keys"] = JsonArray(
                    d.keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive),
                )
                if (d.keySecondary.isNotBlank()) {
                    base["keysecondary"] = JsonArray(
                        d.keySecondary.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive),
                    )
                } else {
                    base.remove("keysecondary")
                }
                base["content"] = JsonPrimitive(d.content)
                base["comment"] = JsonPrimitive(d.comment)
                base["constant"] = JsonPrimitive(d.constant)
                base["selective"] = JsonPrimitive(d.selective)
                base["selectiveLogic"] = JsonPrimitive(d.selectiveLogic)
                base["enabled"] = JsonPrimitive(d.enabled)
                base["insertion_order"] = JsonPrimitive(d.insertionOrder)
                base["position"] = JsonPrimitive(d.position)
                d.depth?.let { base["depth"] = JsonPrimitive(it) } ?: base.remove("depth")
                if (d.role.isNotBlank()) base["role"] = JsonPrimitive(d.role) else base.remove("role")
                base["matchPersonaDescription"] = JsonPrimitive(d.matchPersonaDescription)
                base["matchCharacterDescription"] = JsonPrimitive(d.matchCharacterDescription)
                base["matchCharacterPersonality"] = JsonPrimitive(d.matchCharacterPersonality)
                base["matchCharacterDepthPrompt"] = JsonPrimitive(d.matchCharacterDepthPrompt)
                base["matchScenario"] = JsonPrimitive(d.matchScenario)
                base["matchCreatorNotes"] = JsonPrimitive(d.matchCreatorNotes)
                base["useProbability"] = JsonPrimitive(d.useProbability)
                base["probability"] = JsonPrimitive(d.probability)
                base["delayUntilRecursion"] = JsonPrimitive(d.delayUntilRecursion)
                if (d.triggers.isNotBlank()) {
                    base["triggers"] = JsonArray(d.triggers.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
                } else {
                    base.remove("triggers")
                }
                if (d.outletName.isNotBlank()) base["outletName"] = JsonPrimitive(d.outletName) else base.remove("outletName")
                d.sticky?.let { base["sticky"] = JsonPrimitive(it) } ?: base.remove("sticky")
                d.cooldown?.let { base["cooldown"] = JsonPrimitive(it) } ?: base.remove("cooldown")
                d.delay?.let { base["delay"] = JsonPrimitive(it) } ?: base.remove("delay")
                if (d.group.isNotBlank()) base["group"] = JsonPrimitive(d.group) else base.remove("group")
                base["groupWeight"] = JsonPrimitive(d.groupWeight)
                base["groupOverride"] = JsonPrimitive(d.groupOverride)
                base["useGroupScoring"] = JsonPrimitive(d.useGroupScoring)
                val filterNames = d.characterFilterNames.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val filterTags = d.characterFilterTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (filterNames.isNotEmpty() || filterTags.isNotEmpty()) {
                    base["characterFilter"] = buildJsonObject {
                        if (filterNames.isNotEmpty()) put("names", JsonArray(filterNames.map(::JsonPrimitive)))
                        if (filterTags.isNotEmpty()) put("tags", JsonArray(filterTags.map(::JsonPrimitive)))
                        put("isExclude", JsonPrimitive(d.characterFilterExclude))
                    }
                } else {
                    base.remove("characterFilter")
                }
                base["addMemo"] = JsonPrimitive(d.addMemo)
                if (d.automationId.isNotBlank()) base["automationId"] = JsonPrimitive(d.automationId) else base.remove("automationId")
                // 官方把下列字段放 entry.extensions（world-info.js setWIOriginalDataValue 映射）
                val ext = (base["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                ext["case_sensitive"] = JsonPrimitive(d.caseSensitive)
                ext["match_whole_words"] = JsonPrimitive(d.matchWholeWords)
                d.scanDepth?.let { ext["scan_depth"] = JsonPrimitive(it) } ?: ext.remove("scan_depth")
                ext["prevent_recursion"] = JsonPrimitive(d.preventRecursion)
                ext["exclude_recursion"] = JsonPrimitive(d.excludeRecursion)
                ext["delay_until_recursion"] = JsonPrimitive(d.delayUntilRecursion)
                ext["ignore_budget"] = JsonPrimitive(d.ignoreBudget)
                ext["vectorized"] = JsonPrimitive(d.vectorized)
                d.displayIndex?.let { ext["display_index"] = JsonPrimitive(it) } ?: ext.remove("display_index")
                base["extensions"] = JsonObject(ext)
                JsonObject(base)
            })
            val book = (data["character_book"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (!book.containsKey("name")) book["name"] = JsonPrimitive("Character Book")
            book["entries"] = entriesJson
            m["character_book"] = JsonObject(book)
            JsonObject(m)
        }
    }

    /** 官方 RegexScriptData 全部可编辑字段（保存时按此覆盖）。 */
    private val regexFields = listOf(
        "scriptName", "findRegex", "replaceString", "trimStrings", "placement",
        "disabled", "markdownOnly", "promptOnly", "runOnEdit", "minDepth", "maxDepth", "substituteRegex",
    )

    /** 读取该卡正则脚本（官方位置 data.extensions.regex_scripts）。 */
    fun readRegexScripts(raw: String): List<CharacterRegexScript> = runCatching {
        val root = parseCached(raw)
        val data = dataLayer(root)
        val ext = data["extensions"]?.jsonObject ?: return@runCatching emptyList()
        (ext["regex_scripts"] as? JsonArray)?.mapIndexedNotNull { i, el ->
            val e = el as? JsonObject ?: return@mapIndexedNotNull null
            fun str(key: String): String = (e[key] as? JsonPrimitive)?.contentOrNull ?: ""
            fun bool(key: String, def: Boolean): Boolean = (e[key] as? JsonPrimitive)?.booleanOrNull ?: def
            fun int(key: String): Int? = (e[key] as? JsonPrimitive)?.intOrNull
            CharacterRegexScript(
                id = str("id").ifBlank { (i + 1).toString() },
                scriptName = str("scriptName"),
                findRegex = str("findRegex"),
                replaceString = str("replaceString"),
                trimStrings = (e["trimStrings"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
                placement = (e["placement"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: listOf(1, 2, 5, 6),
                disabled = bool("disabled", false),
                markdownOnly = bool("markdownOnly", false),
                promptOnly = bool("promptOnly", false),
                runOnEdit = bool("runOnEdit", true),
                minDepth = int("minDepth"),
                maxDepth = int("maxDepth"),
                substituteRegex = int("substituteRegex") ?: 0,
            )
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** 保存该卡正则脚本：只覆盖官方字段，未知字段原样保留。 */
    fun applyRegexScripts(raw: String, scripts: List<CharacterRegexScript>): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val original = (ext["regex_scripts"] as? JsonArray)?.map { it as? JsonObject } ?: emptyList()
        val scriptsJson = JsonArray(scripts.mapIndexed { i, s ->
            val base = original.getOrNull(i)?.toMutableMap() ?: mutableMapOf()
            regexFields.forEach { base.remove(it) }
            base["id"] = JsonPrimitive(s.id.ifBlank { (i + 1).toString() })
            base["scriptName"] = JsonPrimitive(s.scriptName)
            base["findRegex"] = JsonPrimitive(s.findRegex)
            base["replaceString"] = JsonPrimitive(s.replaceString)
            base["trimStrings"] = JsonArray(s.trimStrings.map(::JsonPrimitive))
            base["placement"] = JsonArray(s.placement.map(::JsonPrimitive))
            base["disabled"] = JsonPrimitive(s.disabled)
            base["markdownOnly"] = JsonPrimitive(s.markdownOnly)
            base["promptOnly"] = JsonPrimitive(s.promptOnly)
            base["runOnEdit"] = JsonPrimitive(s.runOnEdit)
            if (s.minDepth != null) base["minDepth"] = JsonPrimitive(s.minDepth) else base.remove("minDepth")
            if (s.maxDepth != null) base["maxDepth"] = JsonPrimitive(s.maxDepth) else base.remove("maxDepth")
            base["substituteRegex"] = JsonPrimitive(s.substituteRegex)
            JsonObject(base)
        })
        ext["regex_scripts"] = scriptsJson
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }


    /** 读取该卡变量（README 自定义扩展，data.extensions.emberinn_variables，字符串值）。 */
    fun readVariables(raw: String): Map<String, String> = runCatching {
        val data = dataLayer(parseCached(raw))
        val ext = data["extensions"]?.jsonObject ?: return@runCatching emptyMap()
        (ext["emberinn_variables"] as? JsonObject)?.mapNotNull { (k, v) ->
            val value = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to value
        }?.toMap() ?: emptyMap()
    }.getOrDefault(emptyMap())

    /** 保存该卡变量：JSON 对象，字符串值。 */
    fun applyVariables(raw: String, variables: Map<String, String>): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (variables.isEmpty()) {
            ext.remove("emberinn_variables")
        } else {
            ext["emberinn_variables"] = JsonObject(
                variables.filterValues { it.isNotEmpty() }.mapValues { (_, v) -> JsonPrimitive(v) },
            )
        }
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }


    /** 读取角色级模型覆盖（null = 跟随全局）。 */
    fun readModelOverride(raw: String): ModelOverride = runCatching {
        val data = dataLayer(parseCached(raw))
        val ext = data["extensions"]?.jsonObject ?: return@runCatching ModelOverride()
        val o = ext["emberinn_model_override"]?.jsonObject ?: return@runCatching ModelOverride()
        fun str(key: String): String = (o[key] as? JsonPrimitive)?.contentOrNull ?: ""
        fun int(key: String): Int? = (o[key] as? JsonPrimitive)?.intOrNull
        fun dbl(key: String): Double? = (o[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
        ModelOverride(
            model = str("model"),
            maxTokens = int("maxTokens"),
            contextWindow = int("contextWindow"),
            temperature = dbl("temperature"),
            topP = dbl("topP"),
            presencePenalty = dbl("presencePenalty"),
            frequencyPenalty = dbl("frequencyPenalty"),
        )
    }.getOrDefault(ModelOverride())

    /** 保存角色级模型覆盖；全空则移除字段（回到跟随全局）。 */
    fun applyModelOverride(raw: String, o: ModelOverride): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val empty = o.model.isBlank() && o.maxTokens == null && o.contextWindow == null &&
            o.temperature == null && o.topP == null && o.presencePenalty == null && o.frequencyPenalty == null
        if (empty) {
            ext.remove("emberinn_model_override")
        } else {
            ext["emberinn_model_override"] = buildJsonObject {
                if (o.model.isNotBlank()) put("model", JsonPrimitive(o.model))
                o.maxTokens?.let { put("maxTokens", JsonPrimitive(it)) }
                o.contextWindow?.let { put("contextWindow", JsonPrimitive(it)) }
                o.temperature?.let { put("temperature", JsonPrimitive(it)) }
                o.topP?.let { put("topP", JsonPrimitive(it)) }
                o.presencePenalty?.let { put("presencePenalty", JsonPrimitive(it)) }
                o.frequencyPenalty?.let { put("frequencyPenalty", JsonPrimitive(it)) }
            }
        }
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }


    /** 读取角色级主题配方（空字段 = 跟随全局）。 */
    fun readThemeRecipe(raw: String): ThemeRecipe = runCatching {
        val data = dataLayer(parseCached(raw))
        val ext = data["extensions"]?.jsonObject ?: return@runCatching ThemeRecipe()
        val o = ext["emberinn_theme_recipe"]?.jsonObject ?: return@runCatching ThemeRecipe()
        fun str(key: String): String = (o[key] as? JsonPrimitive)?.contentOrNull ?: ""
        ThemeRecipe(
            seed = str("seed"),
            background = str("background"),
            shape = str("shape"),
            font = str("font"),
            style = str("style"),
            lockMode = str("lockMode"),
        )
    }.getOrDefault(ThemeRecipe())

    /** 保存角色级主题配方；全空则移除字段（回到跟随全局）。 */
    fun applyThemeRecipe(raw: String, r: ThemeRecipe): String = updateData(raw) { data ->
        val m = data.toMutableMap()
        val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val empty = r.seed.isBlank() && r.background.isBlank() && r.shape.isBlank() &&
            r.font.isBlank() && r.style.isBlank() && r.lockMode.isBlank()
        if (empty) {
            ext.remove("emberinn_theme_recipe")
        } else {
            ext["emberinn_theme_recipe"] = buildJsonObject {
                if (r.seed.isNotBlank()) put("seed", JsonPrimitive(r.seed))
                if (r.background.isNotBlank()) put("background", JsonPrimitive(r.background))
                if (r.shape.isNotBlank()) put("shape", JsonPrimitive(r.shape))
                if (r.font.isNotBlank()) put("font", JsonPrimitive(r.font))
                if (r.style.isNotBlank()) put("style", JsonPrimitive(r.style))
                if (r.lockMode.isNotBlank()) put("lockMode", JsonPrimitive(r.lockMode))
            }
        }
        m["extensions"] = JsonObject(ext)
        JsonObject(m)
    }
    /** 主题配方 → JSON 文本（导出/分享）。 */
    fun themeRecipeToJson(r: ThemeRecipe): String = buildJsonObject {
        if (r.seed.isNotBlank()) put("seed", JsonPrimitive(r.seed))
        if (r.background.isNotBlank()) put("background", JsonPrimitive(r.background))
        if (r.shape.isNotBlank()) put("shape", JsonPrimitive(r.shape))
        if (r.font.isNotBlank()) put("font", JsonPrimitive(r.font))
        if (r.style.isNotBlank()) put("style", JsonPrimitive(r.style))
        if (r.lockMode.isNotBlank()) put("lockMode", JsonPrimitive(r.lockMode))
    }.toString()

    /** 主题配方 JSON → 对象（导入；缺字段为空=跟随全局）。 */
    fun themeRecipeFromJson(text: String): ThemeRecipe = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        ThemeRecipe(
            seed = o["seed"]?.jsonPrimitive?.contentOrNull ?: "",
            background = o["background"]?.jsonPrimitive?.contentOrNull ?: "",
            shape = o["shape"]?.jsonPrimitive?.contentOrNull ?: "",
            font = o["font"]?.jsonPrimitive?.contentOrNull ?: "",
            style = o["style"]?.jsonPrimitive?.contentOrNull ?: "",
            lockMode = o["lockMode"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }.getOrDefault(ThemeRecipe())


    /** 世界书官方位置是 data.character_book；兼容历史卡把 character_book 放在根部的写法。 */
    private fun bookOf(root: JsonObject, data: JsonObject): JsonObject? =
        data["character_book"]?.jsonObject ?: root["character_book"]?.jsonObject

    /** data 层改写并落回整卡；V2 卡同步 readFromV2 会提升到根部的字段，V1 卡整卡即 data。 */
    private fun updateData(raw: String, transform: (JsonObject) -> JsonObject): String {
        val root = json.parseToJsonElement(raw).jsonObject.toMutableMap()
        val hadData = root["data"] is JsonObject
        val newData = transform(dataLayer(JsonObject(root)))
        if (hadData) {
            root["data"] = newData
            mirrorRootFields(root, newData)
        } else {
            root.clear()
            root.putAll(newData)
        }
        return JsonObject(root).toString()
    }

    /** 对齐官方 readFromV2 的 fieldMappings：data 有值才覆盖根字段；talkativeness/fav 从 extensions 提升。 */
    private fun mirrorRootFields(root: MutableMap<String, JsonElement>, data: JsonObject) {
        listOf("name", "description", "personality", "scenario", "first_mes", "mes_example", "tags")
            .forEach { key ->
                val v = data[key]
                if (v != null) root[key] = v else root.remove(key)
            }
        val ext = data["extensions"]?.jsonObject
        val talk = ext?.get("talkativeness") ?: data["talkativeness"]
        if (talk != null) root["talkativeness"] = talk else root.remove("talkativeness")
        val fav = ext?.get("fav") ?: data["fav"]
        if (fav != null) root["fav"] = fav else root.remove("fav")
    }
}
