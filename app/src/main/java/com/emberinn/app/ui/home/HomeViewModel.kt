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
import com.emberinn.engine.card.CharacterCardExporter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    fun refresh() {
        _characters.value = store.list()
        _recentSessions.value = chatStore.recent(8)
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
                val avatarPath = if (format == CardFormat.PNG) store.saveAvatar(id, bytes) else null
                val seedColor = if (format == CardFormat.PNG) extractSeed(bytes) else null
                store.save(
                    CharacterRecord(
                        id = id,
                        name = name,
                        description = description,
                        rawJson = cardJson,
                        avatarPath = avatarPath,
                        seedColor = seedColor,
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

    private suspend fun extractSeed(bytes: ByteArray): Long? = withContext(Dispatchers.Default) {
        runCatching {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val palette = Palette.from(bmp).generate()
            val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return@runCatching null
            swatch.rgb.toLong() and 0xFFFFFFFFL
        }.getOrNull()
    }
}
