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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** 设置项命中：点击后跳到设置 Tab（深链排后续）。 */
data class SettingsHit(
    val label: String,
    val description: String,
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
        SettingsHit("主题", "外观与主题：浅色/深色/跟随系统 + 预设主题"),
        SettingsHit("模型", "提供商与模型：API Key / 接口地址 / 默认模型"),
        SettingsHit("语音", "语音：TTS 朗读 / STT 输入（开发中）"),
        SettingsHit("服务", "服务：翻译 / 图像 / 向量（开发中）"),
        SettingsHit("数据与隐私", "数据与隐私：备份 / 导出 / 清除数据"),
        SettingsHit("关于", "关于：版本 / 开源协议 / 数据说明"),
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

    /** 编辑角色字段（README：分字段展示 + 点击展开编辑）：改写 rawJson 对应键，同步角色名与已有会话名。 */
    fun updateCharacter(record: CharacterRecord, newName: String, fields: Map<String, String>) {
        runCatching {
            val root = json.parseToJsonElement(record.rawJson).jsonObject.toMutableMap()
            val data = (root["data"] as? JsonObject)?.toMutableMap() ?: root
            fields.forEach { (key, value) -> data[key] = JsonPrimitive(value) }
            if (root["data"] is JsonObject) root["data"] = JsonObject(data)
            val newJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), JsonObject(root))
            val updated = record.copy(rawJson = newJson, name = newName)
            store.save(updated)
            chatStore.list().filter { it.characterId == record.id }.forEach { session ->
                chatStore.upsert(session.copy(name = newName))
            }
            refresh()
        }
    }

    fun openChat(characterId: String?, name: String): SessionRecord {
        val session = chatStore.findByCharacter(characterId)
            ?: chatStore.upsert(
                SessionRecord(
                    id = characterId ?: "ai",
                    characterId = characterId,
                    name = name,
                ),
            ).let { chatStore.get(characterId ?: "ai") }
            ?: SessionRecord(id = characterId ?: "ai", characterId = characterId, name = name)
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
