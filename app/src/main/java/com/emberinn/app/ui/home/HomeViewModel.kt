package com.emberinn.app.ui.home

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.SessionRecord
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

/** 角色详情页可编辑字段快照（官方 readFromV2/charaFormatData 归一后的 v2 字段全集）。 */
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
    val content: String,
    val comment: String,
    val constant: Boolean,
    val selective: Boolean,
    val enabled: Boolean,
    val insertionOrder: Int,
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

    /** 全局搜索（大小写不敏感）：角色名/描述、会话名/最后消息、世界书条目、设置项。 */
    fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        val characters = store.list()
        return SearchResults(
            characters = characters.filter {
                it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
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

    private fun worldBookHits(character: CharacterRecord, query: String): List<WorldInfoHit> = runCatching {
        val root = json.parseToJsonElement(character.rawJson).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val entries = data["character_book"]?.jsonObject?.get("entries") as? JsonArray ?: return@runCatching emptyList()
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
        SettingsHit("语音", "语音：TTS 朗读 / STT 输入（开发中）"),
        SettingsHit("服务", "服务：翻译 / 图像 / 向量（开发中）"),
        SettingsHit("数据与隐私", "数据与隐私：备份 / 导出 / 清除数据", route = "data"),
        SettingsHit("关于", "关于：版本 / 开源协议 / 数据说明", route = "about"),
    )

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

    /** 通用角色卡 data 层改写（V2 的 data 对象或 V1 的 root）：transform 返回新 data，落盘并同步名字。 */
    fun updateCharacterData(record: CharacterRecord, transform: (JsonObject) -> JsonObject) {
        runCatching {
            val root = json.parseToJsonElement(record.rawJson).jsonObject.toMutableMap()
            val hadData = root["data"] is JsonObject
            val newData = transform((root["data"] as? JsonObject) ?: JsonObject(root))
            if (hadData) {
                root["data"] = newData
            } else {
                root.clear()
                root.putAll(newData)
            }
            saveJson(record, JsonObject(root))
        }
    }

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

    private fun dataLayer(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw).jsonObject
        return root["data"]?.jsonObject ?: root
    }

    // ---- 角色详情页（P1-4）----

    /** 读取角色卡字段（官方 v2 归一后字段；tags 逗号拼接、depth_prompt 兼容对象/字符串、talkativeness 读 extensions）。 */
    fun readCharacterFields(record: CharacterRecord): CharacterDetailFields = runCatching {
        val data = dataLayer(record.rawJson)
        val ext = data["extensions"]?.jsonObject
        fun str(key: String): String = data[key]?.jsonPrimitive?.contentOrNull ?: ""
        val dp = data["depth_prompt"]
        val dpObj = (dp as? JsonObject)
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
            tags = data["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString(", ") ?: "",
            depthPrompt = dpObj?.get("prompt")?.jsonPrimitive?.contentOrNull ?: dp?.jsonPrimitive?.contentOrNull ?: "",
            depthPromptDepth = dpObj?.get("depth")?.jsonPrimitive?.contentOrNull ?: "4",
            depthPromptRole = dpObj?.get("role")?.jsonPrimitive?.contentOrNull ?: "system",
            talkativeness = ext?.get("talkativeness")?.jsonPrimitive?.floatOrNull
                ?: data["talkativeness"]?.jsonPrimitive?.floatOrNull ?: 0.5f,
            alternateGreetings = data["alternate_greetings"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        )
    }.getOrElse {
        CharacterDetailFields(
            name = record.name,
            description = record.description,
            personality = "", scenario = "", firstMes = "", mesExample = "",
            systemPrompt = "", postHistoryInstructions = "", creatorNotes = "",
            creator = "", characterVersion = "", tags = "", depthPrompt = "",
            depthPromptDepth = "4", depthPromptRole = "system", talkativeness = 0.5f,
            alternateGreetings = emptyList(),
        )
    }

    /** 保存角色字段：v2 归一写回（tags 数组、depth_prompt 对象、talkativeness 进 extensions、alternate_greetings 数组）。 */
    fun saveCharacterFields(record: CharacterRecord, fields: CharacterDetailFields) {
        updateCharacterData(record) { data ->
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
            m["depth_prompt"] = buildJsonObject {
                put("prompt", JsonPrimitive(fields.depthPrompt))
                put("depth", JsonPrimitive(fields.depthPromptDepth))
                put("role", JsonPrimitive(fields.depthPromptRole))
            }
            val ext = (m["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (ext.isNotEmpty() || m["extensions"] is JsonObject) {
                ext["talkativeness"] = JsonPrimitive(fields.talkativeness)
                m["extensions"] = JsonObject(ext)
            } else {
                m["talkativeness"] = JsonPrimitive(fields.talkativeness)
            }
            if (fields.alternateGreetings.isEmpty()) {
                m.remove("alternate_greetings")
            } else {
                m["alternate_greetings"] = JsonArray(fields.alternateGreetings.map(::JsonPrimitive))
            }
            JsonObject(m)
        }
    }

    /** 读取角色卡内嵌世界书条目（兼容 v2 keys / v1 key、enabled / disable 反向）。 */
    fun readWorldEntries(record: CharacterRecord): List<WorldEntryDraft> = runCatching {
        val data = dataLayer(record.rawJson)
        val entries = data["character_book"]?.jsonObject?.get("entries") as? JsonArray ?: return@runCatching emptyList()
        entries.mapNotNull { el ->
            val e = (el as? JsonObject) ?: return@mapNotNull null
            val keys = (e["keys"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: listOfNotNull(e["key"]?.jsonPrimitive?.contentOrNull)
            val enabledRaw = e["enabled"]?.jsonPrimitive?.booleanOrNull
            val disableRaw = e["disable"]?.jsonPrimitive?.booleanOrNull
            WorldEntryDraft(
                id = e["id"]?.jsonPrimitive?.intOrNull ?: 0,
                keys = keys.joinToString(", "),
                content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                comment = e["comment"]?.jsonPrimitive?.contentOrNull ?: "",
                constant = e["constant"]?.jsonPrimitive?.booleanOrNull ?: false,
                selective = e["selective"]?.jsonPrimitive?.booleanOrNull ?: true,
                enabled = enabledRaw ?: (disableRaw?.let { !it } ?: true),
                insertionOrder = e["insertion_order"]?.jsonPrimitive?.intOrNull
                    ?: e["order"]?.jsonPrimitive?.intOrNull ?: 100,
            )
        }
    }.getOrDefault(emptyList())

    /** 保存世界书条目（v2 格式：keys/comment/content/constant/selective/enabled/insertion_order/position）。 */
    fun saveWorldEntries(record: CharacterRecord, entries: List<WorldEntryDraft>) {
        updateCharacterData(record) { data ->
            val m = data.toMutableMap()
            val entriesJson = JsonArray(entries.map { d ->
                buildJsonObject {
                    put("id", JsonPrimitive(d.id))
                    put("keys", JsonArray(d.keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive)))
                    put("content", JsonPrimitive(d.content))
                    put("comment", JsonPrimitive(d.comment))
                    put("constant", JsonPrimitive(d.constant))
                    put("selective", JsonPrimitive(d.selective))
                    put("enabled", JsonPrimitive(d.enabled))
                    put("insertion_order", JsonPrimitive(d.insertionOrder))
                    put("position", JsonPrimitive("before_char"))
                }
            })
            val book = (m["character_book"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            book["name"] = JsonPrimitive(book["name"]?.jsonPrimitive?.contentOrNull ?: "Character Book")
            book["entries"] = entriesJson
            m["character_book"] = JsonObject(book)
            JsonObject(m)
        }
    }

    /** 点角色卡片进聊天：续聊该角色最近会话，没有才新建（README 首页：点卡片=进聊天）。 */
    fun openOrResume(characterId: String?, name: String): SessionRecord {
        chatStore.findByCharacter(characterId)?.let { refresh(); return it }
        return newSession(characterId, name)
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
