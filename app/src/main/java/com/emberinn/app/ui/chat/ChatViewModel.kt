package com.emberinn.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ChatStore
import com.emberinn.app.data.ChatRepository
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
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

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private var streamSession: LlmClient.StreamSession? = null

    val characterId: String? = chatStore.get(sessionId)?.characterId

    val accentColor: Long? = characterId?.let { id -> charStore.list().firstOrNull { it.id == id }?.seedColor }

    val providerConfigured: Boolean
        get() = chatRepository.profile() != null

    fun saveProvider(profile: ConnectionProfile) {
        chatRepository.saveProfile(profile)
    }

    fun send(text: String, userName: String = "User") {
        if (text.isBlank() || _isStreaming.value) return
        val charName = chatStore.get(sessionId)?.name ?: "Assistant"
        chatStore.append(sessionId, true, text, userName)
        _messages.value = chatStore.messages(sessionId)

        if (!providerConfigured) {
            chatStore.append(sessionId, false, "（未配置模型。请到设置里选择提供商并填入 API Key。）", charName)
            _messages.value = chatStore.messages(sessionId)
            return
        }

        val characterRawJson = characterId?.let { id -> charStore.list().firstOrNull { it.id == id }?.rawJson }
        val history = chatStore.messages(sessionId)
        _streamingText.value = ""
        _isStreaming.value = true

        streamSession = chatRepository.streamPrepared(
            characterRawJson = characterRawJson,
            history = history,
            userName = userName,
            charName = charName,
            onDelta = { delta ->
                _streamingText.value += delta
            },
            onDone = {
                _isStreaming.value = false
                streamSession = null
                val reply = _streamingText.value
                if (reply.isNotBlank()) {
                    chatStore.append(sessionId, false, reply, charName)
                    _messages.value = chatStore.messages(sessionId)
                }
                _streamingText.value = ""
            },
            onError = {
                _isStreaming.value = false
                streamSession = null
                val partial = _streamingText.value
                chatStore.append(sessionId, false, partial.ifEmpty { "（请求失败，请检查提供商配置。）" }, charName)
                _messages.value = chatStore.messages(sessionId)
                _streamingText.value = ""
            },
        )
    }

    /** 停止按钮：取消当前流式请求（官方 abortController 语义）。 */
    fun stop() {
        streamSession?.cancel()
        streamSession = null
    }
}
