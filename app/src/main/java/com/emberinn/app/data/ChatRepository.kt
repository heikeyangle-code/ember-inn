package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 真实模型对话仓库：连接档案 → 历史消息 → LlmClient（OpenAI 兼容）。
 * 未配置连接时返回 null，由 ViewModel 走占位回复。
 */
class ChatRepository(context: Context) {

    private val store = ProviderStore(File(context.filesDir, "provider"))
    private val client = LlmClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun profile(): ConnectionProfile? = store.load()

    fun profiles(): List<ConnectionProfile> = store.profiles()

    fun activeProfile(): ConnectionProfile? = store.load()

    fun saveProfile(profile: ConnectionProfile, active: Boolean = true) = store.save(profile, active)

    fun setActiveProfile(id: String) = store.setActive(id)

    fun deleteProfile(id: String) = store.delete(id)

    suspend fun chat(
        history: List<JsonElement>,
    ): String? = withContext(Dispatchers.IO) {
        val profile = store.load() ?: return@withContext null
        val provider = ProviderRegistry.get(profile.providerId) ?: return@withContext null
        val messages = history.mapNotNull { el ->
            val obj = el.jsonObject
            val role = if (obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true) "user" else "assistant"
            val content = obj["mes"]?.jsonPrimitive?.content ?: return@mapNotNull null
            CompletionMessage(role = role, content = content)
        }
        client.chatCompletions(provider, profile, messages)
    }

    suspend fun streamChat(
        history: List<JsonElement>,
        onDelta: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val profile = store.load() ?: return@withContext false
        val provider = ProviderRegistry.get(profile.providerId) ?: return@withContext false
        val messages = history.mapNotNull { el ->
            val obj = el.jsonObject
            val role = if (obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true) "user" else "assistant"
            val content = obj["mes"]?.jsonPrimitive?.content ?: return@mapNotNull null
            CompletionMessage(role = role, content = content)
        }
        client.streamChatCompletions(provider, profile, messages, onDelta = onDelta, onDone = {})
        true
    }
}
