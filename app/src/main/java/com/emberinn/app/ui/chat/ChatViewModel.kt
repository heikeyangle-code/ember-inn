package com.emberinn.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

class ChatViewModel(application: Application, private val sessionId: String) : AndroidViewModel(application) {

    private val chatStore = ChatStore(application)
    private val charStore = CharacterStore(application)

    private val _messages = MutableStateFlow(chatStore.messages(sessionId))
    val messages: StateFlow<List<JsonElement>> = _messages

    val characterId: String? = chatStore.get(sessionId)?.characterId

    val accentColor: Long? = characterId?.let { id -> charStore.list().firstOrNull { it.id == id }?.seedColor }

    fun send(text: String, userName: String = "User") {
        if (text.isBlank()) return
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        chatStore.append(sessionId, true, text, userName)
        chatStore.append(sessionId, false, "（模型接入前，这是占位回复。下一步接入提供商配置。）", charName)
        _messages.value = chatStore.messages(sessionId)
    }
}
