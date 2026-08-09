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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 真实模型对话仓库：连接档案 → 历史消息 → LlmClient（OpenAI 兼容）。
 * 未配置连接时返回 null，由 ViewModel 走占位回复。
 */
/** 上下文预算不足、必选提示词放不下（对齐官方 TokenBudgetExceededError 的用户提示）。 */
class ContextBudgetException(message: String) : Exception(message)

class ChatRepository(context: Context) {

    private val store = ProviderStore(File(context.filesDir, "provider"))
    private val client = LlmClient()
    private val promptFactory = ChatPromptFactory()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** 旧档案固定默认 8192 视为“未设置”：取保守中间档，避免自动拉满模型窗口导致提示词爆炸。 */
        const val DEFAULT_CONTEXT_WINDOW = 32_768
        /** 上下文预算中留给必选提示词（角色卡/世界书/系统提示）的安全余量。 */
        const val PROMPT_BUDGET_RESERVE = 2_048
    }

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
        mediaInlining: Boolean = false,
        chatMetadata: JsonObject? = null,
        onPrepared: ((ChatPromptFactory.Prepared) -> Unit)? = null,
    ): LlmClient.StreamSession? {
        val profile = store.load() ?: return null
        val provider = ProviderRegistry.get(profile.providerId) ?: return null
        // 旧档案默认值（512 / 8192）视为“未设置”：
        // 上下文取保守中间档（官方机制：默认小值、用户可手动调大，不再自动拉满模型窗口）；
        // 最大回复取厂商默认后按上下文钳制，保证预算 = 上下文 − 最大回复 恒为正。
        val effectiveContextWindow = if (profile.contextWindow <= 0 || profile.contextWindow == 8192) {
            DEFAULT_CONTEXT_WINDOW
        } else {
            profile.contextWindow
        }
        val effectiveMaxTokens = (if (profile.sampler.maxTokens == 512) {
            provider.defaultMaxTokens ?: 512
        } else profile.sampler.maxTokens)
            .coerceAtMost((effectiveContextWindow - PROMPT_BUDGET_RESERVE).coerceAtLeast(512))
        // 官方 populateChatCompletion：Claude 走 claude 分支（assistant prefill 等），其余 openai
        val chatCompletionSource = when (provider.protocol) {
            "anthropic" -> "claude"
            else -> "openai"
        }
        val prepared = promptFactory.prepare(
            characterRawJson = characterRawJson,
            history = history,
            userName = userName,
            charName = charName,
            model = profile.model,
            maxContextTokens = effectiveContextWindow,
            maxTokens = effectiveMaxTokens,
            type = type,
            continuePrefill = continuePrefill,
            impersonationPrompt = impersonationPrompt,
            cyclePrompt = cyclePrompt,
            imageInlining = mediaInlining,
            videoInlining = mediaInlining,
            audioInlining = mediaInlining,
            chatMetadata = chatMetadata,
            chatCompletionSource = chatCompletionSource,
        )
        onPrepared?.invoke(prepared)
        // 对齐官方 TokenBudgetExceededError：必选提示词都放不下时明确报错，绝不发送空提示词。
        if (prepared.messages.isEmpty()) {
            onError(
                ContextBudgetException(
                    "上下文上限太小，装不下必要提示词（角色卡/世界书/系统提示）。" +
                        "请调大“上下文上限”或调小“最大回复”。",
                ),
            )
            return null
        }
        return client.streamChatCompletionsAsync(
            provider = provider,
            // 请求体同样用有效值：老档案 512 自动升级为厂商建议（如 16384）
            profile = profile.copy(sampler = profile.sampler.copy(maxTokens = effectiveMaxTokens)),
            messages = prepared.messages,
            onDelta = onDelta,
            onDone = onDone,
            onError = onError,
            options = options,
            onReasoning = onReasoning,
        )
    }
}
