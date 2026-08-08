package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRequestOptions
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
    private val promptFactory = ChatPromptFactory()
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

    /**
     * 总装流式发送：角色卡 + 历史 → PromptPipeline 出最终消息 → SSE 流式。
     * 返回可取消会话（停止按钮用）；未配置连接/提供商返回 null。
     */
    fun streamPrepared(
        characterRawJson: String?,
        history: List<JsonElement>,
        userName: String,
        charName: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit,
        options: ProviderRequestOptions = ProviderRequestOptions(),
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonationPrompt: String = ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT,
        cyclePrompt: String = "",
        onReasoning: ((String) -> Unit)? = null,
    ): LlmClient.StreamSession? {
        val profile = store.load() ?: return null
        val provider = ProviderRegistry.get(profile.providerId) ?: return null
        val prepared = promptFactory.prepare(
            characterRawJson = characterRawJson,
            history = history,
            userName = userName,
            charName = charName,
            model = profile.model,
            type = type,
            continuePrefill = continuePrefill,
            impersonationPrompt = impersonationPrompt,
            cyclePrompt = cyclePrompt,
        )
        return client.streamChatCompletionsAsync(
            provider = provider,
            profile = profile,
            messages = prepared.messages,
            onDelta = onDelta,
            onDone = onDone,
            onError = onError,
            options = options,
            onReasoning = onReasoning,
        )
    }
}
