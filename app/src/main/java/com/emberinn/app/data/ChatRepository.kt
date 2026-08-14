package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.PromptMessage
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
import com.emberinn.engine.provider.TextgenSettingsDefaults
import com.emberinn.engine.provider.toChatMLJson
import com.emberinn.engine.provider.toCompletionMessage
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.ExtensionPromptEngine
import com.emberinn.engine.prompt.InstructMode
import com.emberinn.engine.prompt.CustomStoppingConfig
import com.emberinn.engine.prompt.StoppingStringsConfig
import com.emberinn.engine.prompt.StoppingStringsEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.regex.RegexPipelineScript
import com.emberinn.app.ui.settings.KoboldSettingsStore
import com.emberinn.app.ui.settings.NovelSettingsStore
import com.emberinn.app.ui.settings.PresetSettingsStore
import com.emberinn.app.ui.settings.TextgenSettingsStore
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

/** dryRun 预览结果：全文 + 总 token + 官方 TokenHandler 分节计数。 */
data class PromptPreview(
    val text: String,
    val tokens: Int,
    val counts: Map<String, Int> = emptyMap(),
)

class ChatRepository(private val context: Context) {

    private val store = ProviderStore(File(context.filesDir, "provider"))
    private val client = LlmClient()
    private val promptFactory = ChatPromptFactory()

    /** 角色卡解析缓存预热（打开聊天/切角色时调用，首次发送不再等解析）。 */
    fun warmCard(characterRawJson: String?) = promptFactory.warmCardCache(characterRawJson)
    private val json = Json { ignoreUnknownKeys = true }

    /** 连接档案缓存：按 profiles.json 修改时间失效，避免每次发送都重读重解析。 */
    private val profileFile = File(context.filesDir, "provider/profiles.json")
    private var profileCacheStamp = -1L
    private var profileCacheValue: ConnectionProfile? = null

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

    fun profile(): ConnectionProfile? {
        val stamp = profileFile.lastModified()
        if (stamp != profileCacheStamp) {
            profileCacheValue = store.load()
            profileCacheStamp = stamp
        }
        return profileCacheValue
    }

    fun profiles(): List<ConnectionProfile> = store.profiles()

    fun activeProfile(): ConnectionProfile? = profile()

    fun saveProfile(profile: ConnectionProfile, active: Boolean = true) = store.save(profile, active)

    fun setActiveProfile(id: String) = store.setActive(id)

    fun deleteProfile(id: String) = store.delete(id)

    suspend fun chat(
        history: List<JsonElement>,
        maxTokensOverride: Int? = null,
    ): String? = withContext(Dispatchers.IO) {
        val profile = profile() ?: return@withContext null
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
        val profile = profile() ?: return@withContext null
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
        val profile = profile() ?: return@withContext null
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
        personaPosition: Int = 0,
        personaDepth: Int = 2,
        personaRole: Int = 0,
        anSettings: com.emberinn.engine.prompt.AuthorsNoteSettings = com.emberinn.engine.prompt.AuthorsNoteSettings(),
        charaNote: com.emberinn.engine.prompt.CharaNote? = null,
        vectorStore: VectorStore? = null,
        vectorChatSettings: VectorChatSettings = VectorChatSettings(),
        vectorWorldSettings: VectorSettings = VectorSettings(),
        vectorDataBank: List<VectorFileRef> = emptyList(),
        vectorFileText: (String) -> String? = { null },
        inChatExtensions: List<PromptItem> = emptyList(),
        userPrompts: List<PromptItem> = emptyList(),
        userOrder: List<com.emberinn.engine.prompt.PromptOrderEntry> = emptyList(),
        previewOnly: Boolean = false,
        onPreview: ((PromptPreview) -> Unit)? = null,
        worldInfoSettings: WorldInfoSettings = WorldInfoSettings(),
        globalRegexScripts: List<RegexPipelineScript> = emptyList(),
        regexScopedAllowed: Boolean = false,
        regexPresetScripts: List<RegexPipelineScript> = emptyList(),
        regexPresetAllowed: Boolean = false,
        isContinue: Boolean = false,
        regexEnabled: Boolean = true,
        reasoningToPrompts: Boolean = false,
        reasoningTemplate: com.emberinn.engine.prompt.ReasoningTemplate = com.emberinn.engine.prompt.ReasoningTemplate(),
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
        val profile = profile() ?: return null
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
        // 官方 script.js runGenerate：isContinue 且 main_api=openai（chat completion）时给 cyclePrompt
        // 追加 continue_postfix（除非已以空格结尾）；textgen/novel/kobold 不走该路径。
        val effectiveCyclePrompt = if (
            isContinue &&
            provider.protocol !in setOf("textgenerationwebui", "novel", "kobold") &&
            !cyclePrompt.endsWith(" ")
        ) {
            cyclePrompt + profile.sampler.continuePostfix
        } else {
            cyclePrompt
        }
        val effectiveMediaInlining = mediaInlining && profile.sampler.mediaInlining
        val prepared = promptFactory.prepare(
            characterRawJson = characterRawJson,
            history = history,
            userName = userName,
            charName = charName,
            model = effectiveModel,
            maxContextTokens = effectiveContextWindow,
            maxTokens = effectiveMaxTokens,
            type = type,
            userPrompts = userPrompts,
            userOrder = userOrder,
            textareaText = textareaText,
            continuePrefill = continuePrefill || profile.sampler.continuePrefill,
            impersonationPrompt = if (impersonationPrompt == ChatPromptFactory.DEFAULT_IMPERSONATION_PROMPT) {
                profile.sampler.impersonationPrompt
            } else {
                impersonationPrompt
            },
            cyclePrompt = effectiveCyclePrompt,
            imageInlining = effectiveMediaInlining && imageOk,
            videoInlining = effectiveMediaInlining && videoOk,
            audioInlining = effectiveMediaInlining && audioOk,
            namesBehavior = profile.sampler.namesBehavior,
            sendIfEmpty = profile.sampler.sendIfEmpty,
            newChatPrompt = profile.sampler.newChatPrompt,
            newGroupChatPrompt = profile.sampler.newGroupChatPrompt,
            newExampleChatPrompt = profile.sampler.newExampleChatPrompt,
            continueNudgePrompt = profile.sampler.continueNudgePrompt,
            wiFormat = profile.sampler.wiFormat,
            scenarioFormat = profile.sampler.scenarioFormat,
            personalityFormat = profile.sampler.personalityFormat,
            groupNudgePrompt = profile.sampler.groupNudgePrompt,
            // 官方 createGenerationParameters（openai.js:2816）：Claude 冒充模式用 assistant_impersonation 作预填
            assistantPrefill = if (type == "impersonate" && chatCompletionSource == "claude") {
                profile.sampler.assistantImpersonation
            } else {
                profile.sampler.assistantPrefill
            },
            toolReasoningMode = profile.sampler.toolReasoningMode,
            chatMetadata = chatMetadata,
            chatCompletionSource = chatCompletionSource,
            personaDescription = personaDescription,
            personaInPrompt = personaInPrompt,
            personaPosition = personaPosition,
            personaDepth = personaDepth,
            personaRole = personaRole,
            anSettings = anSettings,
            charaNote = charaNote,
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
            reasoningTemplate = reasoningTemplate,
            scriptInjections = scriptInjections,
            useCharacterDepthPrompt = useCharacterDepthPrompt,
            // 官方 prepareOpenAIMessages：squash 仅 chat completion 且 dryRun=false；textgen/novel/kobold 不执行
            squashSystemMessages = profile.sampler.squashSystemMessages &&
                !previewOnly &&
                provider.protocol !in setOf("textgenerationwebui", "novel", "kobold"),
            // 官方 oai_settings.function_calling 总开关：false 时即使有工具注册也不启用
            canUseTools = options.hasTools && profile.sampler.functionCalling,
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
        // 官方 PromptManager.messages：总装后保留，供 Prompt Manager 检查弹窗按 identifier 查看。
        PromptAssemblyCache.lastMessages = prepared.messages
        if (previewOnly) {
            // dryRun：只总装不发送（官方 Generate dryRun）；展示 role: content 全文 + 分节 token（含 start_chat 预留 3）
            onPreview?.invoke(
                PromptPreview(
                    text = prepared.messages.joinToString("\n\n") { "${it.role}: ${it.content}" },
                    tokens = prepared.counts.values.sum() + 3,
                    counts = prepared.counts,
                )
            )
            return null
        }
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
        // 官方 createGenerationParameters：bias_preset_selected 在 logitBiasSources 且预设非空时计算 logit_bias
        val bias = if (
            profile.sampler.biasPresetSelected.isNotBlank() &&
            provider.id in setOf("openai", "azure", "openrouter", "electronhub", "chutes", "custom")
        ) {
            val entries = profile.sampler.biasPresets[profile.sampler.biasPresetSelected].orEmpty()
            if (entries.isNotEmpty()) com.emberinn.engine.provider.LogitBiasEngine.compute(effectiveModel, entries) else emptyMap()
        } else {
            emptyMap()
        }
        val finalOptions = options.copy(
            stopSequences = stopSequences,
            enableWebSearch = profile.sampler.enableWebSearch,
            requestImages = profile.sampler.requestImages,
            aspectRatio = profile.sampler.requestImageAspectRatio,
            imageSize = profile.sampler.requestImageResolution,
            customIncludeBody = profile.customIncludeBody,
            customExcludeBody = profile.customExcludeBody,
            customIncludeHeaders = profile.customIncludeHeaders,
        )
        val effectiveProfile = profile.copy(
            model = effectiveModel,
            sampler = sampler.copy(maxTokens = effectiveMaxTokens, logitBias = bias),
            contextWindow = effectiveContextWindow,
        )
        // 官方后端 chat-completions.js /generate：custom_prompt_post_processing 对所有 chat completion
        // 源先过 postProcessPrompt（merge/semi/strict/single 系列）；text completion 不执行。
        val isTextCompletion = provider.protocol == "textgenerationwebui" || provider.protocol == "novel" || provider.protocol == "kobold"
        val chatMessages = if (!isTextCompletion && profile.customPromptPostProcessing.isNotBlank()) {
            com.emberinn.engine.provider.ProviderConverters.postProcessPrompt(
                messages = prepared.messages.map { it.toChatMLJson() },
                type = profile.customPromptPostProcessing,
                names = com.emberinn.engine.provider.PromptNames(
                    charName = charName,
                    userName = userName,
                    groupNames = stopGroupMemberNames,
                ),
            ).mapNotNull { it.toCompletionMessage() }
        } else {
            prepared.messages
        }
        // Text Completion（textgen/novel）：官方 getTextGenGenerationData / getNovelGenerationData 路径。
        // 提示词 = 官方 createRawPrompt（story string/instruct/历史/输出序列，引擎差分）；
        // textgen 请求体由 TextgenRequestBodyEngine 差分，novel 请求体由 NovelRequestBodyEngine 差分。
        if (isTextCompletion) {
            val presetState = PresetSettingsStore.load(context)
            val env = MacroEnv(user = userName, char = charName)
            // 官方 sysprompt（script.js 4629-4633）：非 openai 路径 enabled 才消费 content；
            // post_history（jailbreak）按官方 4689-4701 作为 user 消息注入（continue 插到末条前，否则追加）。
            val sys = presetState.sysprompt
            val systemPrompt = if (sys.enabled) sys.content else ""
            // InstructMode.createRawPrompt 走 PromptMessage（官方 createRawPrompt 入参）；
            // prepared.messages 是 CompletionMessage，textgen 链只取 role/content/name。
            val textPrompt = prepared.messages.map { m ->
                PromptMessage(role = m.role, content = m.content, name = m.name, identifier = m.identifier)
            }.toMutableList()
            if (sys.enabled && sys.postHistory.isNotBlank()) {
                val jailbreak = PromptMessage(role = "user", content = sys.postHistory)
                if (isContinue && textPrompt.isNotEmpty()) {
                    textPrompt.add(textPrompt.size - 1, jailbreak)
                } else {
                    textPrompt.add(jailbreak)
                }
            }
            val raw = InstructMode.createRawPrompt(
                prompt = textPrompt,
                api = provider.protocol,
                instructOverride = false,
                quietToLoud = false,
                systemPrompt = systemPrompt,
                prefill = "",
                instruct = presetState.instruct,
                context = presetState.context,
                env = env,
            )
            val finalPrompt = (raw as InstructMode.RawPrompt.Text).text
            val opts = when (provider.protocol) {
                "novel" -> finalOptions.copy(
                    textGenPrompt = finalPrompt,
                    textGenIsContinue = isContinue,
                    novelSettings = NovelSettingsStore.load(context),
                )
                "kobold" -> finalOptions.copy(
                    textGenPrompt = finalPrompt,
                    textGenIsContinue = isContinue,
                    koboldSettings = KoboldSettingsStore.load(context),
                )
                else -> finalOptions.copy(
                    textGenPrompt = finalPrompt,
                    textGenIsContinue = isContinue,
                    textGenSettings = TextgenSettingsDefaults.forProfile(
                        provider,
                        effectiveProfile,
                        TextgenSettingsStore.load(context),
                    ),
                )
            }
            return client.streamChatCompletionsAsync(
                provider = provider,
                profile = effectiveProfile,
                messages = prepared.messages,
                onDelta = onDelta,
                onDone = onDone,
                onError = onError,
                options = opts,
                onReasoning = onReasoning,
                onToolCalls = onToolCalls,
            )
        }
        // 官方 createGenerationParameters：isO1（openai/azure_openai + o1-2024-12-17/o1）强制非流式
        if (provider.id in setOf("openai", "azure") && effectiveModel in setOf("o1-2024-12-17", "o1")) {
            return try {
                val full = client.chatCompletions(provider, effectiveProfile, chatMessages, finalOptions)
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
            messages = chatMessages,
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
        val profile = profile() ?: return onError(IllegalStateException("未配置模型"))
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
        val profile = profile() ?: return null
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
