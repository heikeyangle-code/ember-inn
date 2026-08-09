package com.emberinn.app.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.GroupRecord
import com.emberinn.app.data.GroupStore
import com.emberinn.engine.group.GroupGenerationMode
import com.emberinn.app.data.SessionRecord
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 会话列表（聊天 Tab）：按时间倒序、置顶优先；新建/置顶/删除/导出。 */
class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val chatStore = ChatStore(application)
    private val charStore = CharacterStore(application)
    private val groupStore = GroupStore(application)

    private val _sessions = MutableStateFlow(chatStore.list())
    val sessions: StateFlow<List<SessionRecord>> = _sessions

    private val _characters = MutableStateFlow(charStore.list())
    val characters: StateFlow<List<CharacterRecord>> = _characters

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        _sessions.value = chatStore.list()
        _characters.value = charStore.list()
    }

    /** 最后一条消息预览（无消息返回 null）。 */
    fun previewOf(sessionId: String): String? = chatStore.lastMessage(sessionId)

    /** 导出聊天原始 JSONL（官方聊天文件格式，可直接进酒馆）。 */
    fun exportJsonl(sessionId: String): String? = chatStore.exportJsonl(sessionId)

    /** 新建群聊会话：GroupRecord + 会话（groupId 关联，成员来自角色列表）。 */
    fun newGroupSession(
        memberIds: List<String>,
        name: String,
        generationMode: Int = GroupGenerationMode.APPEND,
        activationStrategy: String = "natural",
    ): SessionRecord? {
        if (memberIds.size < 2) {
            _message.value = "群聊至少选 2 个角色"
            return null
        }
        val groupId = UUID.randomUUID().toString()
        groupStore.save(
            GroupRecord(
                id = groupId,
                name = name.ifBlank { "群聊" },
                members = memberIds,
                generationMode = generationMode,
                activationStrategy = activationStrategy,
            ),
        )
        val session = SessionRecord(
            id = UUID.randomUUID().toString(),
            characterId = null,
            name = name.ifBlank { "群聊" },
            groupId = groupId,
        )
        chatStore.upsert(session)
        refresh()
        return session
    }

    /** 新建空白会话：默认 AI 对话，或指定角色；每个角色可开多个会话。 */
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

    fun togglePin(record: SessionRecord) {
        chatStore.upsert(record.copy(pinned = !record.pinned))
        refresh()
    }

    fun delete(record: SessionRecord) {
        chatStore.delete(record.id)
        refresh()
    }

    fun clearMessage() {
        _message.value = null
    }
}
