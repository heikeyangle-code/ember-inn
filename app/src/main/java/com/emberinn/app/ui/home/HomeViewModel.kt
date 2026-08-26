package com.emberinn.app.ui.home

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.data.CharacterRegexScript
import com.emberinn.app.data.CharacterDetailFields
import com.emberinn.app.data.ModelOverride
import com.emberinn.app.data.ThemeRecipe
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.engine.card.CardFormat
import com.emberinn.engine.card.CardImporter
import com.emberinn.engine.card.CharXImporter
import com.emberinn.engine.card.CharacterCardExporter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/** 全局搜索结果（README：角色 + 会话 + 世界书 + 设置）。 */
data class SearchResults(
    val characters: List<CharacterRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val worldInfo: List<WorldInfoHit> = emptyList(),
    val settings: List<SettingsHit> = emptyList(),
)

/** 世界书命中：所属角色 + 条目 key + 内容（用于搜索结果的详情弹层）。 */
data class WorldInfoHit(
    val characterName: String,
    val characterId: String,
    val key: String,
    val content: String,
)

/** 设置项命中：route 对应 SettingsScreen deepLink（appearance/providers/data/about），空则只跳设置 Tab。 */
data class SettingsHit(
    val label: String,
    val description: String,
    val route: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = CharacterStore(application)
    private val chatStore = ChatStore(application)
    private val json = Json { ignoreUnknownKeys = true }

    private val _characters = MutableStateFlow(store.list())
    val characters: StateFlow<List<CharacterRecord>> = _characters

    private val _recentSessions = MutableStateFlow(chatStore.recent(8))
    val recentSessions: StateFlow<List<SessionRecord>> = _recentSessions

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 卡片预览缓存：一次 refresh 算好，避免网格滚动时逐卡读盘（README：UI 线程不做 IO）。 */
    private var previewCache: Map<String, String> = emptyMap()
    private var sessionPreviewCache: Map<String, String> = emptyMap()

    fun refresh() {
        _characters.value = store.list()
        _recentSessions.value = chatStore.recent(8)
        val sessions = chatStore.list()
        previewCache = characters.value
            .mapNotNull { c -> c.id to (sessions.filter { it.characterId == c.id }.maxByOrNull { it.updatedAt }?.let { chatStore.lastMessage(it.id) }) }
            .filter { (_, v) -> !v.isNullOrBlank() }
            .associate { (k, v) -> k to v!! }
        sessionPreviewCache = sessions.mapNotNull { it.id to chatStore.lastMessage(it.id) }
            .filter { (_, v) -> !v.isNullOrBlank() }
            .associate { (k, v) -> k to v!! }
    }

    /** README 首页卡片：角色的最近消息预览（refresh 时缓存，不在组合期读盘）。 */
    fun lastMessageFor(characterId: String?): String? =
        characterId?.let { previewCache[it] }

    fun lastMessage(sessionId: String): String? = sessionPreviewCache[sessionId]

    /** 全域搜索（大小写不敏感）：角色名/描述、会话名/最后消息、世界书条目、设置项。
     *  fuzzy=官方 power_user.fuzzy_search（默认关）：开启后走加权模糊匹配并按得分排序
     *  （fuse.js 语义近似：名称命中远重于描述命中）。 */
    fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        val fuzzy = com.emberinn.app.ui.settings.BehaviorPrefs.load(getApplication()).fuzzySearch
        val characters = store.list()
        return SearchResults(
            characters = if (fuzzy) {
                characters.mapNotNull { c ->
                    val f = readCharacterFields(c)
                    listOf(
                        20.0 to c.name,
                        10.0 to f.tags,
                        3.0 to c.description,
                        3.0 to f.mesExample,
                        2.0 to f.scenario,
                        2.0 to f.personality,
                        2.0 to f.firstMes,
                        2.0 to f.creatorNotes,
                        1.0 to f.creator,
                        1.0 to f.alternateGreetings.joinToString("\n"),
                    ).mapNotNull { (w, text) -> fuzzyScore(text, q, w) }.minOrNull()?.let { c to it }
                }.sortedBy { it.second }.map { it.first }
            } else {
                characters.filter {
                    it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
                }
            },
            sessions = chatStore.list().filter { session ->
                session.name.contains(q, ignoreCase = true) ||
                    (chatStore.lastMessage(session.id)?.contains(q, ignoreCase = true) == true)
            },
            worldInfo = characters.flatMap { c -> worldBookHits(c, q) },
            settings = settingsCatalog.filter {
                it.label.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
            },
        )
    }

    /** 模糊得分（越低越优）：子序列匹配 + 跳跃惩罚/字段权重（官方 fuse.js threshold 0.2 语义近似）。 */
    private fun fuzzyScore(haystack: String, needle: String, weight: Double): Double? {
        if (needle.isEmpty()) return null
        val h = haystack.lowercase()
        val n = needle.lowercase()
        var hi = 0
        var gaps = 0
        for (ch in n) {
            val idx = h.indexOf(ch, hi)
            if (idx < 0) return null
            gaps += idx - hi
            hi = idx + 1
        }
        val dispersion = gaps.toDouble() / h.length.coerceAtLeast(1)
        return dispersion / weight
    }

    private fun worldBookHits(character: CharacterRecord, query: String): List<WorldInfoHit> = runCatching {
        val root = json.parseToJsonElement(character.rawJson).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val book = data["character_book"]?.jsonObject ?: root["character_book"]?.jsonObject
        val entries = book?.get("entries") as? JsonArray ?: return@runCatching emptyList()
        entries.mapNotNull { el ->
            val e = el.jsonObject
            val key = e["key"]?.jsonPrimitive?.contentOrNull ?: ""
            val keys = e["keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val content = e["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val comment = e["comment"]?.jsonPrimitive?.contentOrNull ?: ""
            val haystack = listOf(key, *keys.toTypedArray(), content, comment).joinToString("\n")
            if (haystack.contains(query, ignoreCase = true)) {
                WorldInfoHit(
                    characterName = character.name,
                    characterId = character.id,
                    key = key.ifBlank { keys.firstOrNull().orEmpty() }.ifBlank { "未命名条目" },
                    content = content.ifBlank { comment }.ifBlank { "（空条目）" },
                )
            } else null
        }
    }.getOrDefault(emptyList())

    private val settingsCatalog = listOf(
        SettingsHit("主题", "外观与主题：浅色/深色/跟随系统 + 预设主题", route = "appearance"),
        SettingsHit("模型", "提供商与模型：API Key / 接口地址 / 默认模型", route = "providers"),
        SettingsHit("AI 响应", "采样参数 / 预设 / 提示词管理", route = "ai"),
        SettingsHit("预设", "补全 / 上下文 / 指令 / 系统提示预设管理", route = "presets"),
        SettingsHit("高级格式化", "Instruct / 上下文模板", route = "formatting"),
        SettingsHit("用户设置", "行为 / 流式 / 排序 / 提示音 / 提示偏置", route = "user"),
        SettingsHit("人设", "用户人设卡管理与默认人设", route = "personas"),
        SettingsHit("语音", "语音：TTS 朗读配置与试听", route = "voice"),
        SettingsHit("服务", "服务：翻译 / 图像 / 向量", route = "services"),
        SettingsHit("记忆", "总结记忆：模板 / 深度 / 触发", route = "memory"),
        SettingsHit("扩展", "扩展管理：主题 / 数据库 / 端点", route = "extensions"),
        SettingsHit("快捷回复", "全局快捷回复槽位与 automationId", route = "quickreplies"),
        SettingsHit("世界书", "扫描深度 / 递归 / 预算", route = "worldinfo"),
        SettingsHit("作者注释", "authors-note：提示 / 位置 / 深度 / 间隔", route = "authorsnote"),
        SettingsHit("正则脚本", "全局正则（GLOBAL 分桶）", route = "regex"),
        SettingsHit("图像描述", "caption：生成图片说明的提示与模型", route = "caption"),
        SettingsHit("表情", "角色表情包：触发词 / 图片映射", route = "expression"),
        SettingsHit("数据与隐私", "数据与隐私：备份 / 导出 / 清除数据", route = "data"),
        SettingsHit("关于", "关于：版本 / 开源协议 / 数据说明", route = "about"),
    )

    /** 从 URL 导入角色卡（README 兼容目标：对齐官方 content-manager URL 导入）。 */
    fun importCardFromUrl(url: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val bytes = download(url) ?: error("下载失败，请检查地址或网络")
                val format = detectFormatFromUrl(url, bytes)
                importCard(bytes, format)
                true
            }.onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message ?: "导入失败") }
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val client = OkHttpClient.Builder().followRedirects(true).build()
        val request = Request.Builder().url(url).header("User-Agent", "EmberInn/0.1").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.bytes()
        }
    }.getOrNull()

    private fun detectFormatFromUrl(url: String, bytes: ByteArray): CardFormat {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> CardFormat.PNG
            path.endsWith(".charx") -> CardFormat.CHARX
            path.endsWith(".json") -> CardFormat.JSON
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> CardFormat.PNG
            else -> CardFormat.JSON
        }
    }

    fun importCard(bytes: ByteArray, format: CardFormat) {
        viewModelScope.launch {
            runCatching {
                val cardJson = CardImporter.import(bytes, format)
                val root = json.parseToJsonElement(cardJson).jsonObject
                val data = root["data"]?.jsonObject ?: root
                val name = data["name"]?.jsonPrimitive?.contentOrNull ?: "未命名角色"
                val description = data["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val id = UUID.randomUUID().toString()
                val paths = when (format) {
                    CardFormat.PNG -> {
                        val avatar = store.saveAvatar(id, bytes)
                        AssetPaths(avatar, extractSeed(bytes), null, null)
                    }
                    CardFormat.CHARX -> extractCharXAssets(id, bytes)
                    else -> AssetPaths(null, null, null, null)
                }
                val avatarPath = paths.avatarPath
                val seedColor = paths.seedColor
                val backgroundPath = paths.backgroundPath
                val voicePath = paths.voicePath
                store.save(
                    CharacterRecord(
                        id = id,
                        name = name,
                        description = description,
                        rawJson = cardJson,
                        avatarPath = avatarPath,
                        seedColor = seedColor,
                        backgroundPath = backgroundPath,
                        voicePath = voicePath,
                    ),
                )
                refresh()
                _message.value = "已导入：$name"
            }.onFailure { e ->
                _message.value = "导入失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun togglePin(record: CharacterRecord) {
        store.save(record.copy(pinned = !record.pinned))
        refresh()
    }

    fun delete(record: CharacterRecord) {
        store.delete(record.id)
        chatStore.deleteByCharacter(record.id)
        refresh()
    }

    /** 导出走官方同款流程：V2 归一（readFromV2/charaFormatData）+ 私有字段清理 + 4 空格缩进。 */
    fun exportJson(record: CharacterRecord): String =
        CharacterCardExporter.exportToV2Json(record.rawJson)

    private fun saveJson(record: CharacterRecord, root: JsonObject) {
        val newJson = json.encodeToString(JsonObject.serializer(), root)
        val newName = ((root["data"] as? JsonObject) ?: root)["name"]?.jsonPrimitive?.contentOrNull
        val updated = record.copy(rawJson = newJson, name = newName?.ifBlank { record.name } ?: record.name)
        store.save(updated)
        if (newName != null && newName != record.name) {
            chatStore.list().filter { it.characterId == record.id }.forEach { session ->
                chatStore.upsert(session.copy(name = newName))
            }
        }
        refresh()
    }

    // ---- 角色详情页（P1-4）----

    /** 读取角色卡字段（官方 v2 归一字段；tags 逗号拼接、depth_prompt 兼容对象/字符串、talkativeness 读 extensions）。 */
    fun readCharacterFields(record: CharacterRecord): CharacterDetailFields =
        CharacterCardEdit.readFields(record.rawJson, record.name, record.description)

    /** 保存角色字段：v2 归一写回（tags 数组、depth_prompt 进 extensions、talkativeness 进 extensions、alternate_greetings 数组）。 */
    fun saveCharacterFields(record: CharacterRecord, fields: CharacterDetailFields) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyFields(record.rawJson, fields)).jsonObject
        saveJson(record, root)
    }

    /** 读取角色卡内嵌世界书条目（兼容 v2 keys / v1 key、enabled / disable 反向、keys 逗号字符串）。 */
    fun readWorldEntries(record: CharacterRecord): List<WorldEntryDraft> =
        CharacterCardEdit.readWorldEntries(record.rawJson)

    /** 保存世界书条目：只覆盖编辑字段，未知字段原样保留；v1（key/order/disable）归一为 v2。 */
    fun saveWorldEntries(record: CharacterRecord, entries: List<WorldEntryDraft>) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyWorldEntries(record.rawJson, entries)).jsonObject
        saveJson(record, root)
    }

    /** 官方 data.extensions.world：保存角色关联的外置世界。 */
    fun saveWorldLink(record: CharacterRecord, worldName: String) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyWorldLink(record.rawJson, worldName)).jsonObject
        saveJson(record, root)
    }

    /** 官方 data.extensions.sd_character_prompt：读取 SD 扩展共享的 {positive, negative}。 */
    fun readSdCharacterPromptShared(record: CharacterRecord): Pair<String, String>? =
        CharacterCardEdit.readSdCharacterPrompt(record.rawJson)

    /** 官方 Shareable 勾选：写入/移除角色卡的 sd_character_prompt（全空 = 移除）。 */
    fun saveSdCharacterPromptShared(record: CharacterRecord, positive: String, negative: String) {
        val root = json.parseToJsonElement(
            CharacterCardEdit.applySdCharacterPrompt(record.rawJson, positive, negative),
        ).jsonObject
        saveJson(record, root)
    }

    /** 读取该卡正则脚本（官方 data.extensions.regex_scripts）。 */
    fun readRegexScripts(record: CharacterRecord): List<CharacterRegexScript> =
        CharacterCardEdit.readRegexScripts(record.rawJson)

    /** 保存该卡正则脚本：只覆盖官方字段，未知字段原样保留。 */
    fun saveRegexScripts(record: CharacterRecord, scripts: List<CharacterRegexScript>) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyRegexScripts(record.rawJson, scripts)).jsonObject
        saveJson(record, root)
    }

    /** 读取角色级模型覆盖。 */
    fun readModelOverride(record: CharacterRecord): ModelOverride =
        CharacterCardEdit.readModelOverride(record.rawJson)

    /** 保存角色级模型覆盖（全空 = 跟随全局）。 */
    fun saveModelOverride(record: CharacterRecord, o: ModelOverride) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyModelOverride(record.rawJson, o)).jsonObject
        saveJson(record, root)
    }

    /** 读取角色级主题配方。 */
    fun readThemeRecipe(record: CharacterRecord): ThemeRecipe =
        CharacterCardEdit.readThemeRecipe(record.rawJson)

    /** 保存角色级主题配方（全空 = 跟随全局）。 */
    fun saveThemeRecipe(record: CharacterRecord, r: ThemeRecipe) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyThemeRecipe(record.rawJson, r)).jsonObject
        saveJson(record, root)
    }

    /** 按 id 查角色（主题配方/背景应用用）。 */
    fun findCharacter(id: String): CharacterRecord? = store.list().firstOrNull { it.id == id }

    /** 读取该卡变量（README 自定义扩展）。 */
    fun readVariables(record: CharacterRecord): Map<String, String> =
        CharacterCardEdit.readVariables(record.rawJson)

    /** 保存该卡变量。 */
    fun saveVariables(record: CharacterRecord, variables: Map<String, String>) {
        val root = json.parseToJsonElement(CharacterCardEdit.applyVariables(record.rawJson, variables)).jsonObject
        saveJson(record, root)
    }

    /** 点角色卡片进聊天：续聊该角色最近会话，没有才新建（README 首页：点卡片=进聊天）。 */
    fun openOrResume(characterId: String?, name: String): SessionRecord {
        chatStore.findByCharacter(characterId)?.let { refresh(); return it }
        return newSession(characterId, name)
    }

    /** 角色主页故事轨道：该角色的全部会话（Character≠Conversation，一角色多故事；归档故事隐藏）。 */
    fun sessionsForCharacter(characterId: String): List<SessionRecord> =
        chatStore.list().filter { it.characterId == characterId && !it.archived }.sortedByDescending { it.updatedAt }

    /** 书架排序数据源（官方 sort_field=date_last_chat / chat_size）：
     *  characterId → (最近聊天时间戳, 会话数)；归档故事不计。 */
    fun characterActivity(): Map<String, Pair<Long, Int>> =
        chatStore.list()
            .filterNot { it.archived }
            .filterNotNullCharacterId()
            .groupBy { it.characterId!! }
            .mapValues { (_, sessions) -> Pair(sessions.maxOf { it.updatedAt }, sessions.size) }

    private fun List<SessionRecord>.filterNotNullCharacterId(): List<SessionRecord> = filter { !it.characterId.isNullOrBlank() }

    /** 书架排序数据源（官方 sort_field=data_size / create_date，src/endpoints/characters.js
     *  processCharacter + calculateDataSize）：一次解析 rawJson。
     *  data_size = card data 对象各字段值长度和（官方口径的卡内容体量代理）；
     *  create_date 取卡内值（ISO 8601 / 毫秒数均兼容），缺失退化为本地导入时间（官方同：回退文件 ctime）。 */
    fun characterMeta(): Map<String, Pair<Int, Long>> =
        characters.value.associate { r ->
            r.id to runCatching {
                val root = json.parseToJsonElement(r.rawJson).jsonObject
                val data = root["data"]?.jsonObject ?: root
                val dataSize = data.values.sumOf { it.toString().length }
                val createDate = parseCardDate(data["create_date"]?.jsonPrimitive?.contentOrNull) ?: r.importedAt
                dataSize to createDate
            }.getOrDefault(r.rawJson.length to r.importedAt)
        }

    /** 卡内 create_date → 毫秒：兼容标准 ISO、非补位月日时分秒（moment 宽容格式）、纯毫秒三种写法。 */
    private fun parseCardDate(raw: String?): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        s.toLongOrNull()?.let { return it }
        runCatching { return java.time.Instant.parse(s).toEpochMilli() }
        return runCatching {
            java.text.SimpleDateFormat("yyyy-M-d'T'H:m:s.SSS'Z'", java.util.Locale.ROOT)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(s)?.time
        }.getOrNull()
    }

    /** 新建空白会话（README：每个角色可开多个会话，UUID 会话 id）。 */
    fun newSession(characterId: String?, name: String): SessionRecord {
        val session = SessionRecord(
            id = UUID.randomUUID().toString(),
            characterId = characterId,
            name = name,
        )
        chatStore.upsert(session)
        refresh()
        return session
    }

    fun clearMessage() { _message.value = null }

    private data class AssetPaths(
        val avatarPath: String?,
        val seedColor: Long?,
        val backgroundPath: String?,
        val voicePath: String?,
    )

    /** CharX 资产入库（官方 persist auxiliary assets）：icon→头像，background/voice→assets 目录。 */
    private fun extractCharXAssets(id: String, bytes: ByteArray): AssetPaths = runCatching {
        val assets = CharXImporter.extractAssets(bytes)
        val iconBytes = assets.icon?.data
        val avatarPath = iconBytes?.let { store.saveAvatar(id, it) }
        val seedColor = iconBytes?.let { extractSeedBlocking(it) }
        val assetsDir = File(getApplication<Application>().filesDir, "assets").apply { mkdirs() }
        var backgroundPath: String? = null
        var voicePath: String? = null
        assets.assets.forEach { asset ->
            val data = asset.data ?: return@forEach
            when (asset.type) {
                "background" -> {
                    backgroundPath = File(assetsDir, "$id-background.${asset.ext.ifBlank { "png" }}").also { it.writeBytes(data) }.absolutePath
                }
                "voice" -> {
                    voicePath = File(assetsDir, "$id-voice.${asset.ext.ifBlank { "mp3" }}").also { it.writeBytes(data) }.absolutePath
                }
            }
        }
        AssetPaths(avatarPath, seedColor, backgroundPath, voicePath)
    }.getOrElse { AssetPaths(null, null, null, null) }

    private fun extractSeedBlocking(bytes: ByteArray): Long? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val palette = Palette.from(bmp).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return@runCatching null
        swatch.rgb.toLong() and 0xFFFFFFFFL
    }.getOrNull()

    private suspend fun extractSeed(bytes: ByteArray): Long? = withContext(Dispatchers.Default) {
        runCatching {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val palette = Palette.from(bmp).generate()
            val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return@runCatching null
            swatch.rgb.toLong() and 0xFFFFFFFFL
        }.getOrNull()
    }
}
