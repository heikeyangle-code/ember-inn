package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRequestOptions
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.worldinfo.VectorChatSettings
import com.emberinn.engine.worldinfo.VectorFileRef
import com.emberinn.engine.worldinfo.VectorSettings
import com.emberinn.engine.worldinfo.VectorStore
import com.emberinn.engine.worldinfo.WorldInfoSettings
import com.emberinn.engine.media.MediaCapability
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.macros.MemoryVariableStore
import com.emberinn.engine.macros.VariableStore
import com.emberinn.engine.provider.ProviderStore
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.ExtensionPromptEngine
import com.emberinn.engine.prompt.CustomStoppingConfig
import com.emberinn.engine.prompt.StoppingStringsConfig
import com.emberinn.engine.prompt.StoppingStringsEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.regex.RegexPipelineScript
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

    /** 会话级变量存储（官方聊天级 local variables）：预置本卡变量，setvar 跨消息保留。 */
    private var localVariables: VariableStore = MemoryVariableStore()
    private var seededCardRaw: String? = null

    private fun syncLocalVariables(characterRawJson: String?) {
        val raw = characterRawJson.orEmpty()
        if (raw == seededCardRaw) return
        seededCardRaw = raw
        val next = MemoryVariableStore()
        CharacterCardEdit.readVariables(raw).forEach { (k, v) -> next.set(k, v) }
        localVariables = next
    }

    companion object {
        /** 官方 oai_settings.openai_max_context 默认 max_4k。 */
        const val DEFAULT_CONTEXT_WINDOW = 4095
        /** 官方 oai_settings.openai_max_tokens 默认。 */
        const val DEFAULT_MAX_TOKENS = 300
    }

    fun profile(): ConnectionProfile? = store.load()

    fun profiles(): List<ConnectionProfile> = store.profiles()

    fun activeProfile(): ConnectionProfile? = store.load()

    fun saveProfile(profile: ConnectionProfile, active: Boolean = true) = store.save(profile, active)

    fun setActiveProfile(id: String) = store.setActive(id)

    fun deleteProfile(id: String) = store.delete(id)

    suspend fun chat(
        history: List<JsonElement>,
        maxTokensOverride: Int? = null,
    ): String? = withContext(Dispatchers.IO) {
        val profile = store.load() ?: return@withContext null
        val provider = ProviderRegistry.get(profile.providerId) ?: return@withContext null
        val messages = history.mapNotNull { el ->
            val obj = el.jsonObject
            val role = if (obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true) "user" else "assistant"
            val content = obj["mes"]?.jsonPrimitive?.content ?: return@mapNotNull null
            CompletionMessage(role = role, content = content)
        }
        val effective = if (maxTokensOverride != null && maxTokensOverride > 0) {
            profile.copy(sampler = profile.sampler.copy(maxTokens = maxTokensOverride))
        } else {
            profile
        }
        client.chatCompletions(provider, effective, messages)
    }

    /** 官方 /genraw：直接以给定提示请求（system/prefill/length 可选），不读写聊天。 */
    suspend fun rawGenerate(
        prompt: String,
        system: String = "",
        prefill: String = "",
        maxTokensOverride: Int? = null,
        stopSequences: List<String> = emptyList(),
    ): String? = withContext(Dispatchers.IO) {
        val profile = store.load() ?: return@withContext null
        val provider = ProviderRegistry.get(profile.providerId) ?: return@withContext null
        val messages = buildList {
            if (system.isNotBlank()) add(CompletionMessage(role = "system", content = system))
            add(CompletionMessage(role = "user", content = prompt))
            if (prefill.isNotBlank()) add(CompletionMessage(role = "assistant", content = prefill))
        }
        val effective = if (maxTokensOverride != null && maxTokensOverride > 0) {
            profile.copy(sampler = profile.sampler.copy(maxTokens = maxTokensOverride))
        } else {
            profile
        }
        client.chatCompletions(
            provider,
            effective,
            messages,
            options = ProviderRequestOptions(stopSequences = stopSequences),
        )
    }

    /** 官方 caption 扩展 multimodal：用当前提供商发视觉请求生成图片描述。 */
    suspend fun captionImage(dataUrl: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val profile = store.load() ?: return@withContext null
        val provider = ProviderRegistry.get(profile.providerId) ?: return@withContext null
        val messages = listOf(
            CompletionMessage(role = "system", content = "You are an image captioning assistant."),
            CompletionMessage(
                role = "user",
                content = prompt,
                media = listOf(MediaAttachment(type = "image", url = dataUrl, title = "")),
            ),
        )
        client.chatCompletions(provider, profile, messages)
    }

    /**
     * 总装流式发送：角色卡 + 历史 → PromptPipeline 出最终消息 → SSE 流式。
     * 返回可取消会话（停止按钮用）；未配置连接/提供商返回 null。
     */
    fun streamPrepared(
        characterRawJson: String?,
        localVariables: VariableStore? = null,
        history: List<JsonElement>,
        userName: String,
        charName: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit,
        options: ProviderRequestOptions = ProviderRequestOptions(),
        type: String = "generate",
        /** 官方 Generate 的 textareaText（getBiasStrings 输入；regenerate/swipe/continue 空串）。 */
        textareaText: String = "",
        continuePrefill: Boolean = false,
        impersonationPrompt: String = ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT,
        cyclePrompt: String = "",
        onReasoning: ((String) -> Unit)? = null,
        onToolCalls: ((JsonElement) -> Unit)? = null,
        stopGroupMemberNames: List<String> = emptyList(),
        mediaInlining: Boolean = false,
        chatMetadata: JsonObject? = null,
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
        vectorStore: VectorStore? = null,
        vectorChatSettings: VectorChatSettings = VectorChatSettings(),
        vectorWorldSettings: VectorSettings = VectorSettings(),
        vectorDataBank: List<VectorFileRef> = emptyList(),
        vectorFileText: (String) -> String? = { null },
        inChatExtensions: List<PromptItem> = emptyList(),
        worldInfoSettings: WorldInfoSettings = WorldInfoSettings(),
        globalRegexScripts: List<RegexPipelineScript> = emptyList(),
        regexScopedAllowed: Boolean = false,
        regexPresetScripts: List<RegexPipelineScript> = emptyList(),
        regexPresetAllowed: Boolean = false,
        isContinue: Boolean = false,
        regexEnabled: Boolean = true,
        reasoningToPrompts: Boolean = false,
        scriptInjections: List<ExtensionPromptEngine.ScriptInject> = emptyList(),
        /** 官方 generate：群聊深度提示存在时用群聊提示，否则角色卡深度提示。 */
        useCharacterDepthPrompt: Boolean = true,
        /** 官方 generateQuietPrompt 的 quietPrompt（记忆扩展 DEFAULT 总结器）。 */
        quietPrompt: String = "",
        /** 官方 memory 扩展注入参数。 */
        memorySummary: String = "",
        memoryTemplate: String = com.emberinn.engine.prompt.MemoryEngine.DEFAULT_TEMPLATE,
        memoryPosition: Int = 0,
        memoryRole: Int = 0,
        memoryDepth: Int = 2,
        memoryScan: Boolean = false,
        collapseNewlines: Boolean = false,
        exampleSeparator: String = "***",
        userPromptBias: String = "",
        pinExamples: Boolean = false,
        stripExamples: Boolean = false,
        namesAsStopStrings: Boolean = true,
        externalWorlds: Map<String, List<com.emberinn.engine.worldinfo.WorldInfoEntry>> = emptyMap(),
        linkedWorld: String? = null,
        chatMetadataWorld: String? = null,
        globalWorlds: List<String> = emptyList(),
        worldInsertStrategy: Int = com.emberinn.engine.worldinfo.WorldLoreMerger.CHARACTER_FIRST,
        wiIncludeNames: Boolean = true,
        onPrepared: ((ChatPromptFactory.Prepared) -> Unit)? = null,
    ): LlmClient.StreamSession? {
        val profile = store.load() ?: return null
        val provider = ProviderRegistry.get(profile.providerId) ?: return null
        // 官方 1.18：未设置时用 openai_max_context / openai_max_tokens 默认；用户存值原样保留。
        // 角色级模型覆盖（README：模型/上下文/最大回复/采样，本角色覆盖全局；存卡内扩展字段）
        val override = characterRawJson?.let { CharacterCardEdit.readModelOverride(it) }
        val effectiveModel = override?.model?.ifBlank { null } ?: profile.model
        val effectiveContextWindow = override?.contextWindow?.takeIf { it > 0 }
            ?: profile.contextWindow.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW
        // 官方 getMaxPromptTokens = getMaxContextTokens - getMaxResponseTokens，不额外扣安全余量
        val effectiveMaxTokens =
            override?.maxTokens?.takeIf { it > 0 }
                ?: profile.sampler.maxTokens.takeIf { it > 0 }
                ?: DEFAULT_MAX_TOKENS
        val sampler = profile.sampler.copy(
            temperature = override?.temperature ?: profile.sampler.temperature,
            topP = override?.topP ?: profile.sampler.topP,
            presencePenalty = override?.presencePenalty ?: profile.sampler.presencePenalty,
            frequencyPenalty = override?.frequencyPenalty ?: profile.sampler.frequencyPenalty,
        )
        // 官方 populateChatCompletion：Claude 走 claude 分支（assistant prefill 等），其余 openai
        val chatCompletionSource = when (provider.protocol) {
            "anthropic" -> "claude"
            else -> "openai"
        }
        // 官方 isImage/Video/AudioInliningSupported：main_api=openai + media_inlining + 模型白名单 + source 分支
        val mediaSource = mediaSourceOf(provider)
        val imageOk = mediaSource != null && MediaCapability.isImageInliningSupported(mediaSource, effectiveModel)
        val videoOk = mediaSource != null && MediaCapability.isVideoInliningSupported(mediaSource, effectiveModel)
        val audioOk = mediaSource != null && MediaCapability.isAudioInliningSupported(mediaSource, effectiveModel)
        // 变量存储：调用方可注入（测试/特殊场景）；默认会话级一份，卡变化时重预置
        if (localVariables != null) this.localVariables = localVariables
        syncLocalVariables(characterRawJson)
        val prepared = promptFactory.prepare(
            characterRawJson = characterRawJson,
            history = history,
            userName = userName,
            charName = charName,
            model = effectiveModel,
            maxContextTokens = effectiveContextWindow,
            maxTokens = effectiveMaxTokens,
            type = type,
            textareaText = textareaText,
            continuePrefill = continuePrefill,
            impersonationPrompt = impersonationPrompt,
            cyclePrompt = cyclePrompt,
            imageInlining = mediaInlining && imageOk,
            videoInlining = mediaInlining && videoOk,
            audioInlining = mediaInlining && audioOk,
            chatMetadata = chatMetadata,
            chatCompletionSource = chatCompletionSource,
            personaDescription = personaDescription,
            personaInPrompt = personaInPrompt,
            vectorStore = vectorStore,
            vectorChatSettings = vectorChatSettings,
            vectorWorldSettings = vectorWorldSettings,
            vectorDataBank = vectorDataBank,
            vectorFileText = vectorFileText,
            inChatExtensions = inChatExtensions,
            worldInfoSettings = worldInfoSettings,
            globalRegexScripts = globalRegexScripts,
            regexScopedAllowed = regexScopedAllowed,
            regexPresetScripts = regexPresetScripts,
            regexPresetAllowed = regexPresetAllowed,
            isContinue = isContinue,
            regexEnabled = regexEnabled,
            reasoningToPrompts = reasoningToPrompts,
            scriptInjections = scriptInjections,
            useCharacterDepthPrompt = useCharacterDepthPrompt,
            canUseTools = options.hasTools,
            quietPrompt = quietPrompt,
            memorySummary = memorySummary,
            memoryTemplate = memoryTemplate,
            memoryPosition = memoryPosition,
            memoryRole = memoryRole,
            memoryDepth = memoryDepth,
            memoryScan = memoryScan,
            collapseNewlines = collapseNewlines,
            exampleSeparator = exampleSeparator,
            currentApi = provider.id,
            currentModel = effectiveModel,
            userPromptBias = userPromptBias,
            pinExamples = pinExamples,
            stripExamples = stripExamples,
            externalWorlds = externalWorlds,
            linkedWorld = linkedWorld,
            chatMetadataWorld = chatMetadataWorld,
            globalWorlds = globalWorlds,
            worldInsertStrategy = worldInsertStrategy,
            wiIncludeNames = wiIncludeNames,
            localVariables = this.localVariables,
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
        val stopSequences = StoppingStringsEngine.getStoppingStrings(
            api = provider.protocol,
            config = StoppingStringsConfig(
                isContinue = isContinue,
                name1 = userName,
                name2 = charName,
                chatLastIsUser = history.lastOrNull()?.jsonObject?.get("is_user")?.jsonPrimitive?.content == "true",
                groupMemberNames = stopGroupMemberNames,
                selectedGroup = stopGroupMemberNames.isNotEmpty(),
                namesAsStopStrings = namesAsStopStrings,
                env = MacroEnv(user = userName, char = charName),
            ),
        )
        val finalOptions = options.copy(stopSequences = stopSequences)
        val effectiveProfile = profile.copy(
            model = effectiveModel,
            sampler = sampler.copy(maxTokens = effectiveMaxTokens),
            contextWindow = effectiveContextWindow,
        )
        // 官方 createGenerationParameters：isO1（openai/azure_openai + o1-2024-12-17/o1）强制非流式
        if (provider.id in setOf("openai", "azure") && effectiveModel in setOf("o1-2024-12-17", "o1")) {
            return try {
                val full = client.chatCompletions(provider, effectiveProfile, prepared.messages, finalOptions)
                onDelta(full)
                onDone()
                null
            } catch (e: Exception) {
                onError?.invoke(e)
                null
            }
        }
        return client.streamChatCompletionsAsync(
            provider = provider,
            // 请求体同样用有效值：老档案 512 自动升级为厂商建议（如 16384）
            profile = effectiveProfile,
            messages = prepared.messages,
            onDelta = onDelta,
            onDone = onDone,
            onError = onError,
            options = finalOptions,
            onReasoning = onReasoning,
            onToolCalls = onToolCalls,
        )
    }

    /** 记忆扩展 RAW 总结器：官方 generateRaw({prompt, systemPrompt, responseLength})。 */
    fun summarizeRaw(
        systemPrompt: String,
        userPrompt: String,
        responseLength: Int = 0,
        onDelta: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val profile = store.load() ?: return onError(IllegalStateException("未配置模型"))
        val provider = ProviderRegistry.get(profile.providerId) ?: return onError(IllegalStateException("未配置模型"))
        val sampler = if (responseLength > 0) profile.sampler.copy(maxTokens = responseLength) else profile.sampler
        val sb = StringBuilder()
        client.streamChatCompletionsAsync(
            provider = provider,
            profile = profile.copy(sampler = sampler),
            messages = listOf(
                CompletionMessage(role = "system", content = systemPrompt),
                CompletionMessage(role = "user", content = userPrompt),
            ),
            onDelta = { sb.append(it); onDelta(it) },
            onDone = { onResult(sb.toString()) },
            onError = onError,
        )
    }

    /** 记忆扩展 DEFAULT 总结器：官方 generateQuietPrompt（quietPrompt + 当前上下文，结果不落盘）。 */
    fun generateQuietSummary(
        history: List<JsonElement>,
        quietPrompt: String,
        responseLength: Int = 0,
        onDelta: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ): LlmClient.StreamSession? {
        val profile = store.load() ?: return null
        val provider = ProviderRegistry.get(profile.providerId) ?: return null
        val prepared = promptFactory.prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "Assistant",
            model = profile.model,
            maxContextTokens = profile.contextWindow.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW,
            maxTokens = if (responseLength > 0) responseLength else profile.sampler.maxTokens.takeIf { it > 0 } ?: 512,
            type = "quiet",
            quietPrompt = quietPrompt,
        )
        if (prepared.messages.isEmpty()) {
            onError(IllegalStateException("上下文太小，装不下总结提示词"))
            return null
        }
        val sb = StringBuilder()
        return client.streamChatCompletionsAsync(
            provider = provider,
            profile = profile,
            messages = prepared.messages,
            onDelta = { sb.append(it); onDelta(it) },
            onDone = { onResult(sb.toString()) },
            onError = onError,
        )
    }

    /** provider.id → 官方 chat_completion_sources（无官方分支的返回 null = 不支持媒体内联）。 */
    private fun mediaSourceOf(provider: com.emberinn.engine.provider.ProviderSpec): String? = when (provider.id) {
        "openai" -> MediaCapability.Source.OPENAI
        "azure" -> MediaCapability.Source.AZURE_OPENAI
        "anthropic" -> MediaCapability.Source.CLAUDE
        "google" -> MediaCapability.Source.MAKERSUITE
        "vertexai" -> MediaCapability.Source.VERTEXAI
        "openrouter" -> MediaCapability.Source.OPENROUTER
        "custom" -> MediaCapability.Source.CUSTOM
        "mistral" -> MediaCapability.Source.MISTRALAI
        "cohere" -> MediaCapability.Source.COHERE
        "xai" -> MediaCapability.Source.XAI
        "moonshot" -> MediaCapability.Source.MOONSHOT
        "zhipu" -> MediaCapability.Source.ZAI
        "siliconflow" -> MediaCapability.Source.SILICONFLOW
        "workers-ai" -> MediaCapability.Source.WORKERS
        else -> null
    }
}
