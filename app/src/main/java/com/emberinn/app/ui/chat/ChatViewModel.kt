package com.emberinn.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.engine.provider.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

class ChatViewModel(application: Application, private val sessionId: String) : AndroidViewModel(application) {

    private val chatStore = ChatStore(application)
    private val charStore = CharacterStore(application)
    private val chatRepository = ChatRepository(application)

    private val _messages = MutableStateFlow(chatStore.messages(sessionId))
    val messages: StateFlow<List<JsonElement>> = _messages

    val characterId: String? = chatStore.get(sessionId)?.characterId

    val accentColor: Long? = characterId?.let { id -> charStore.list().firstOrNull { it.id == id }?.seedColor }

    val providerConfigured: Boolean
        get() = chatRepository.profile() != null

    fun saveProvider(profile: ConnectionProfile) {
        chatRepository.saveProfile(profile)
    }

    fun send(text: String, userName: String = "User") {
        if (text.isBlank()) return
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        chatStore.append(sessionId, true, text, userName)
        _messages.value = chatStore.messages(sessionId)

        if (!providerConfigured) {
            chatStore.append(sessionId, false, "（未配置模型。请到设置里选择提供商并填入 API Key。）", charName)
            _messages.value = chatStore.messages(sessionId)
            return
        }

        viewModelScope.launch {
            val history = chatStore.messages(sessionId)
            val reply = withContext(Dispatchers.IO) { chatRepository.chat(history) }
            chatStore.append(sessionId, false, reply ?: "（请求失败，请检查提供商配置。）", charName)
            _messages.value = chatStore.messages(sessionId)
        }
    }
}
