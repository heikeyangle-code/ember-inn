package com.emberinn.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.PromptManagerPrefs
import com.emberinn.app.data.ProviderState
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.SamplerParams
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderSpec
import com.emberinn.engine.prompt.PresetLibrary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 提供商管理（参照命理2：列表 + 详情编辑；底层协议仍按酒馆 1:1）。 */
class ProviderViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 官方 oai_settings.openai_max_context 默认 max_4k。 */
        const val DEFAULT_CONTEXT_WINDOW = 4095
        /** 官方 oai_settings.openai_max_tokens 默认。 */
        const val DEFAULT_MAX_TOKENS = 300
    }

    private val repo = ChatRepository(application)
    private val client = LlmClient()

    val providers: List<ProviderSpec> = ProviderRegistry.all()

    private val _profiles = MutableStateFlow(repo.profiles())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles

    private val _activeId = MutableStateFlow(repo.activeProfile()?.id ?: "")
    val activeId: StateFlow<String> = _activeId

    private val _providerId = MutableStateFlow("")
    val providerId: StateFlow<String> = _providerId

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId

    private val _apiVersion = MutableStateFlow("")
    val apiVersion: StateFlow<String> = _apiVersion

    private val _reverseProxy = MutableStateFlow("")
    val reverseProxy: StateFlow<String> = _reverseProxy
    private val _proxyPassword = MutableStateFlow("")
    val proxyPassword: StateFlow<String> = _proxyPassword
    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl
    private val _customIncludeBody = MutableStateFlow("")
    val customIncludeBody: StateFlow<String> = _customIncludeBody
    private val _customExcludeBody = MutableStateFlow("")
    val customExcludeBody: StateFlow<String> = _customExcludeBody
    private val _customIncludeHeaders = MutableStateFlow("")
    val customIncludeHeaders: StateFlow<String> = _customIncludeHeaders
    private val _customPromptPostProcessing = MutableStateFlow("")
    val customPromptPostProcessing: StateFlow<String> = _customPromptPostProcessing
    private val _bypassStatusCheck = MutableStateFlow(false)
    val bypassStatusCheck: StateFlow<Boolean> = _bypassStatusCheck
    private val _showExternalModels = MutableStateFlow(false)
    val showExternalModels: StateFlow<Boolean> = _showExternalModels
    private val _groupModels = MutableStateFlow(false)
    val groupModels: StateFlow<Boolean> = _groupModels
    private val _sortModels = MutableStateFlow("alphabetically")
    val sortModels: StateFlow<String> = _sortModels
    private val _azureDeploymentName = MutableStateFlow("")
    val azureDeploymentName: StateFlow<String> = _azureDeploymentName
    private val _azureOpenaiModel = MutableStateFlow("")
    val azureOpenaiModel: StateFlow<String> = _azureOpenaiModel
    private val _vertexaiAuthMode = MutableStateFlow("express")
    val vertexaiAuthMode: StateFlow<String> = _vertexaiAuthMode
    private val _vertexaiExpressProjectId = MutableStateFlow("")
    val vertexaiExpressProjectId: StateFlow<String> = _vertexaiExpressProjectId
    private val _nanogptProvider = MutableStateFlow("")
    val nanogptProvider: StateFlow<String> = _nanogptProvider
    private val _nanogptPaygOverride = MutableStateFlow(false)
    val nanogptPaygOverride: StateFlow<Boolean> = _nanogptPaygOverride

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _contextWindow = MutableStateFlow(DEFAULT_CONTEXT_WINDOW)
    val contextWindow: StateFlow<Int> = _contextWindow

    /** 上下文上限是否跟随模型自动拉满（默认关：官方保守机制，避免提示词爆炸变慢）。 */
    private val _contextAuto = MutableStateFlow(false)
    val contextAuto: StateFlow<Boolean> = _contextAuto

    private val _maxTokens = MutableStateFlow(DEFAULT_MAX_TOKENS)
    val maxTokens: StateFlow<Int> = _maxTokens

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var editingId: String? = null
    private val _editingSampler = MutableStateFlow(SamplerParams())
    val editingSampler: StateFlow<SamplerParams> = _editingSampler

    fun provider(): ProviderSpec? = ProviderRegistry.get(_providerId.value)

    fun openDetail(id: String) {
        val spec = ProviderRegistry.get(id) ?: return
        val existing = _profiles.value.firstOrNull { it.providerId == id }
        editingId = existing?.id
        _editingSampler.value = existing?.sampler ?: SamplerParams()
        _providerId.value = id
        _profileName.value = existing?.name?.ifBlank { spec.displayName } ?: spec.displayName
        _apiKey.value = existing?.apiKey.orEmpty()
        _baseUrl.value = existing?.baseUrlOverride?.takeIf { it.isNotBlank() } ?: spec.baseUrl
        _region.value = existing?.region?.takeIf { it.isNotBlank() }
            ?: spec.regionVariants.firstOrNull().orEmpty()
        _accountId.value = existing?.accountId.orEmpty()
        _apiVersion.value = existing?.apiVersionOverride?.takeIf { it.isNotBlank() } ?: spec.apiVersion
        _reverseProxy.value = existing?.reverseProxy.orEmpty()
        _proxyPassword.value = existing?.proxyPassword.orEmpty()
        _customUrl.value = existing?.customUrl.orEmpty()
        _customIncludeBody.value = existing?.customIncludeBody.orEmpty()
        _customExcludeBody.value = existing?.customExcludeBody.orEmpty()
        _customIncludeHeaders.value = existing?.customIncludeHeaders.orEmpty()
        _customPromptPostProcessing.value = existing?.customPromptPostProcessing.orEmpty()
        _bypassStatusCheck.value = existing?.bypassStatusCheck ?: false
        _showExternalModels.value = existing?.showExternalModels ?: false
        _groupModels.value = existing?.groupModels ?: false
        _sortModels.value = existing?.sortModels?.takeIf { it.isNotBlank() } ?: "alphabetically"
        _azureDeploymentName.value = existing?.azureDeploymentName.orEmpty()
        _azureOpenaiModel.value = existing?.azureOpenaiModel.orEmpty()
        _vertexaiAuthMode.value = existing?.vertexaiAuthMode?.takeIf { it.isNotBlank() } ?: "express"
        _vertexaiExpressProjectId.value = existing?.vertexaiExpressProjectId.orEmpty()
        _nanogptProvider.value = existing?.nanogptProvider.orEmpty()
        _nanogptPaygOverride.value = existing?.nanogptPaygOverride ?: false
        val model = existing?.model?.takeIf { it.isNotBlank() }
            ?: spec.defaultModels.firstOrNull().orEmpty()
        val list = spec.defaultModels.toMutableList()
        existing?.model?.takeIf { it.isNotBlank() && it !in list }?.let { list.add(0, it) }
        _models.value = list
        _selectedModel.value = model
        // 官方 1.18：只有“从未设置”才用默认值（4095/300）；用户存的值原样保留。
        val storedContext = existing?.contextWindow
        val legacyDefault = storedContext == null
        _contextAuto.value = false
        _contextWindow.value = storedContext ?: DEFAULT_CONTEXT_WINDOW
        val storedTokens = existing?.sampler?.maxTokens
        _maxTokens.value = storedTokens ?: DEFAULT_MAX_TOKENS
        _message.value = null
        _testing.value = false
    }

    fun setProfileName(value: String) {
        _profileName.value = value
    }

    /** 粘贴自动去空格。 */
    fun setApiKey(value: String) {
        _apiKey.value = value.filterNot { it.isWhitespace() }
    }

    fun setBaseUrl(value: String) {
        _baseUrl.value = value.trim()
    }

    fun setRegion(value: String) {
        _region.value = value
    }

    fun setAccountId(value: String) {
        _accountId.value = value
    }

    fun setApiVersion(value: String) {
        _apiVersion.value = value.trim()
    }

    /** 上下文上限（tokens），占比胶囊分母；手动输入即退出“按模型自动”。 */
    fun setContextWindow(value: String) {
        _contextAuto.value = false
        val n = value.filter { it.isDigit() }.toIntOrNull()
        _contextWindow.value = (n ?: DEFAULT_CONTEXT_WINDOW).coerceIn(256, 2_000_000)
    }

    /** 最大回复 tokens：推理模型思考会占额度，默认按官方 300。 */
    fun setMaxTokens(value: String) {
        val n = value.filter { it.isDigit() }.toIntOrNull()
        _maxTokens.value = (n ?: DEFAULT_MAX_TOKENS).coerceIn(64, 262_144)
    }

    fun selectModel(model: String) {
        _selectedModel.value = model
        val spec = provider()
        if (_contextAuto.value && spec != null) {
            _contextWindow.value = defaultContextFor(spec, model)
        }
    }

    fun testConnection() {
        val spec = provider() ?: return
        _testing.value = true
        _message.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (hasModelsEndpoint(spec)) {
                        client.models(spec, buildProfile())
                    } else {
                        // 无模型列表端点的提供商：最小对话探测验证 Key
                        client.chatCompletions(
                            spec,
                            buildProfile().copy(sampler = buildProfile().sampler.copy(maxTokens = 1, stream = false)),
                            listOf(CompletionMessage(role = "user", content = "ping")),
                        )
                        spec.defaultModels
                    }
                }
            }
            _testing.value = false
            result.onSuccess { models ->
                val list = models.ifEmpty { spec.defaultModels }
                _models.value = list
                if (_selectedModel.value.isBlank() || _selectedModel.value !in list) {
                    _selectedModel.value = list.firstOrNull() ?: ""
                }
                _message.value = if (models.isNotEmpty()) "连接成功，共 ${models.size} 个模型" else "连接成功"
            }.onFailure { e ->
                _message.value = humanError(e)
            }
        }
    }

    fun setTemperature(v: Double) { _editingSampler.value = _editingSampler.value.copy(temperature = v) }
    fun setTopP(v: Double) { _editingSampler.value = _editingSampler.value.copy(topP = v) }
    fun setPresencePenalty(v: Double) { _editingSampler.value = _editingSampler.value.copy(presencePenalty = v) }
    fun setFrequencyPenalty(v: Double) { _editingSampler.value = _editingSampler.value.copy(frequencyPenalty = v) }
    fun setTopK(v: String) {
        val n = v.filter { it.isDigit() }.toIntOrNull()
        _editingSampler.value = _editingSampler.value.copy(topK = (n ?: 0).coerceIn(0, 500))
    }
    fun setMinP(v: String) {
        _editingSampler.value = _editingSampler.value.copy(minP = v.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0)
    }
    fun setTopA(v: String) {
        _editingSampler.value = _editingSampler.value.copy(topA = v.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0)
    }
    fun setRepetitionPenalty(v: String) {
        _editingSampler.value = _editingSampler.value.copy(
            repetitionPenalty = v.toDoubleOrNull()?.coerceIn(1.0, 2.0) ?: 1.0,
        )
    }
    fun setSeed(v: String) {
        val n = v.filter { it.isDigit() || it == '-' }.toIntOrNull()
        _editingSampler.value = _editingSampler.value.copy(seed = (n ?: -1).coerceIn(-1, 1_000_000))
    }
    fun setN(v: String) {
        val n = v.filter { it.isDigit() }.toIntOrNull()
        _editingSampler.value = _editingSampler.value.copy(n = (n ?: 1).coerceIn(1, 8))
    }
    fun setStreaming(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(stream = v)
    }
    fun setUseFallback(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(useFallback = v)
    }
    fun setOpenRouterProviders(v: String) {
        _editingSampler.value = _editingSampler.value.copy(
            openRouterProviders = v.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
    fun setOpenRouterQuantizations(v: String) {
        _editingSampler.value = _editingSampler.value.copy(
            openRouterQuantizations = v.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
    fun setAllowFallbacks(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(allowFallbacks = v)
    }
    fun setMiddleout(v: String) {
        _editingSampler.value = _editingSampler.value.copy(middleout = v)
    }
    fun setRequestTokenProbabilities(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(requestTokenProbabilities = v)
    }
    fun setReasoningEffort(v: String) { _editingSampler.value = _editingSampler.value.copy(reasoningEffort = v) }
    fun setVerbosity(v: String) { _editingSampler.value = _editingSampler.value.copy(verbosity = v) }
    fun setUseSysprompt(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(useSysprompt = v)
    }
    fun setSquashSystemMessages(v: Boolean) {
        _editingSampler.value = _editingSampler.value.copy(squashSystemMessages = v)
    }
    fun setNamesBehavior(v: Int) { _editingSampler.value = _editingSampler.value.copy(namesBehavior = v) }
    fun setBiasPresetSelected(v: String) { _editingSampler.value = _editingSampler.value.copy(biasPresetSelected = v) }
    fun setSendIfEmpty(v: String) { _editingSampler.value = _editingSampler.value.copy(sendIfEmpty = v) }
    fun setNewChatPrompt(v: String) { _editingSampler.value = _editingSampler.value.copy(newChatPrompt = v) }
    fun setNewGroupChatPrompt(v: String) { _editingSampler.value = _editingSampler.value.copy(newGroupChatPrompt = v) }
    fun setNewExampleChatPrompt(v: String) { _editingSampler.value = _editingSampler.value.copy(newExampleChatPrompt = v) }
    fun setContinueNudgePrompt(v: String) { _editingSampler.value = _editingSampler.value.copy(continueNudgePrompt = v) }
    fun setWiFormat(v: String) { _editingSampler.value = _editingSampler.value.copy(wiFormat = v) }
    fun setScenarioFormat(v: String) { _editingSampler.value = _editingSampler.value.copy(scenarioFormat = v) }
    fun setPersonalityFormat(v: String) { _editingSampler.value = _editingSampler.value.copy(personalityFormat = v) }
    fun setGroupNudgePrompt(v: String) { _editingSampler.value = _editingSampler.value.copy(groupNudgePrompt = v) }
    fun setAssistantPrefill(v: String) { _editingSampler.value = _editingSampler.value.copy(assistantPrefill = v) }
    fun setContinuePrefill(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(continuePrefill = v) }
    fun setContinuePostfix(v: String) { _editingSampler.value = _editingSampler.value.copy(continuePostfix = v) }
    fun setFunctionCalling(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(functionCalling = v) }
    fun setShowThoughts(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(showThoughts = v) }
    fun setMediaInlining(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(mediaInlining = v) }
    fun setInlineImageQuality(v: String) { _editingSampler.value = _editingSampler.value.copy(inlineImageQuality = v) }
    fun setEnableWebSearch(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(enableWebSearch = v) }
    fun setToolReasoningMode(v: String) { _editingSampler.value = _editingSampler.value.copy(toolReasoningMode = v) }
    fun setMaxContextUnlocked(v: Boolean) { _editingSampler.value = _editingSampler.value.copy(maxContextUnlocked = v) }
    fun setToolCallRecurseLimit(v: Int) { _editingSampler.value = _editingSampler.value.copy(toolCallRecurseLimit = v) }
    fun setAssistantImpersonation(v: String) { _editingSampler.value = _editingSampler.value.copy(assistantImpersonation = v) }
    fun setReverseProxy(v: String) { _reverseProxy.value = v }
    fun setProxyPassword(v: String) { _proxyPassword.value = v }
    fun setCustomUrl(v: String) { _customUrl.value = v }
    fun setCustomIncludeBody(v: String) { _customIncludeBody.value = v }
    fun setCustomExcludeBody(v: String) { _customExcludeBody.value = v }
    fun setCustomIncludeHeaders(v: String) { _customIncludeHeaders.value = v }
    fun setCustomPromptPostProcessing(v: String) { _customPromptPostProcessing.value = v }
    fun setBypassStatusCheck(v: Boolean) { _bypassStatusCheck.value = v }
    fun setShowExternalModels(v: Boolean) { _showExternalModels.value = v }
    fun setGroupModels(v: Boolean) { _groupModels.value = v }
    fun setSortModels(v: String) { _sortModels.value = v }
    fun setAzureDeploymentName(v: String) { _azureDeploymentName.value = v }
    fun setAzureOpenaiModel(v: String) { _azureOpenaiModel.value = v }
    fun setVertexaiAuthMode(v: String) { _vertexaiAuthMode.value = v }
    fun setVertexaiExpressProjectId(v: String) { _vertexaiExpressProjectId.value = v }
    fun setNanogptProvider(v: String) { _nanogptProvider.value = v }
    fun setNanogptPaygOverride(v: Boolean) { _nanogptPaygOverride.value = v }

    /** 应用官方 OpenAI 采样预设：引擎 onSettingsPresetChange 纯循环（bind_preset_to_connection=官方默认 true）。 */
    fun applySamplerPreset(name: String) {
        if (name.isBlank()) return
        val official = PresetLibrary.samplerPresets("openai").firstOrNull { it.name == name }
        val preset = official?.settings ?: UserPresetStore.load(getApplication(), "sampler", name) ?: return
        val sam = _editingSampler.value
        val settings = kotlinx.serialization.json.buildJsonObject {
            put("temp_openai", kotlinx.serialization.json.JsonPrimitive(sam.temperature))
            put("top_p_openai", kotlinx.serialization.json.JsonPrimitive(sam.topP))
            put("freq_pen_openai", kotlinx.serialization.json.JsonPrimitive(sam.frequencyPenalty))
            put("pres_pen_openai", kotlinx.serialization.json.JsonPrimitive(sam.presencePenalty))
            put("top_k_openai", kotlinx.serialization.json.JsonPrimitive(sam.topK))
            put("top_a_openai", kotlinx.serialization.json.JsonPrimitive(sam.topA))
            put("min_p_openai", kotlinx.serialization.json.JsonPrimitive(sam.minP))
            put("repetition_penalty_openai", kotlinx.serialization.json.JsonPrimitive(sam.repetitionPenalty))
            put("seed", kotlinx.serialization.json.JsonPrimitive(sam.seed))
            put("n", kotlinx.serialization.json.JsonPrimitive(sam.n))
            put("stream_openai", kotlinx.serialization.json.JsonPrimitive(sam.stream))
            put("squash_system_messages", kotlinx.serialization.json.JsonPrimitive(sam.squashSystemMessages))
            put("names_behavior", kotlinx.serialization.json.JsonPrimitive(sam.namesBehavior))
            put("send_if_empty", kotlinx.serialization.json.JsonPrimitive(sam.sendIfEmpty))
            put("impersonation_prompt", kotlinx.serialization.json.JsonPrimitive(sam.impersonationPrompt))
            put("new_chat_prompt", kotlinx.serialization.json.JsonPrimitive(sam.newChatPrompt))
            put("new_group_chat_prompt", kotlinx.serialization.json.JsonPrimitive(sam.newGroupChatPrompt))
            put("new_example_chat_prompt", kotlinx.serialization.json.JsonPrimitive(sam.newExampleChatPrompt))
            put("continue_nudge_prompt", kotlinx.serialization.json.JsonPrimitive(sam.continueNudgePrompt))
            put("bias_preset_selected", kotlinx.serialization.json.JsonPrimitive(sam.biasPresetSelected))
            put("wi_format", kotlinx.serialization.json.JsonPrimitive(sam.wiFormat))
            put("scenario_format", kotlinx.serialization.json.JsonPrimitive(sam.scenarioFormat))
            put("personality_format", kotlinx.serialization.json.JsonPrimitive(sam.personalityFormat))
            put("group_nudge_prompt", kotlinx.serialization.json.JsonPrimitive(sam.groupNudgePrompt))
            put("assistant_prefill", kotlinx.serialization.json.JsonPrimitive(sam.assistantPrefill))
            put("assistant_impersonation", kotlinx.serialization.json.JsonPrimitive(sam.assistantImpersonation))
            put("continue_prefill", kotlinx.serialization.json.JsonPrimitive(sam.continuePrefill))
            put("continue_postfix", kotlinx.serialization.json.JsonPrimitive(sam.continuePostfix))
            put("function_calling", kotlinx.serialization.json.JsonPrimitive(sam.functionCalling))
            put("show_thoughts", kotlinx.serialization.json.JsonPrimitive(sam.showThoughts))
            put("media_inlining", kotlinx.serialization.json.JsonPrimitive(sam.mediaInlining))
            put("inline_image_quality", kotlinx.serialization.json.JsonPrimitive(sam.inlineImageQuality))
            put("enable_web_search", kotlinx.serialization.json.JsonPrimitive(sam.enableWebSearch))
            put("tool_reasoning_mode", kotlinx.serialization.json.JsonPrimitive(sam.toolReasoningMode))
            put("tool_call_recurse_limit", kotlinx.serialization.json.JsonPrimitive(sam.toolCallRecurseLimit))
            put("request_images", kotlinx.serialization.json.JsonPrimitive(sam.requestImages))
            put("request_image_aspect_ratio", kotlinx.serialization.json.JsonPrimitive(sam.requestImageAspectRatio))
            put("request_image_resolution", kotlinx.serialization.json.JsonPrimitive(sam.requestImageResolution))
            put("max_context_unlocked", kotlinx.serialization.json.JsonPrimitive(sam.maxContextUnlocked))
            put("openai_max_context", kotlinx.serialization.json.JsonPrimitive(_contextWindow.value))
            put("openai_max_tokens", kotlinx.serialization.json.JsonPrimitive(_maxTokens.value))
        }
        val applied = com.emberinn.engine.prompt.PresetApplyEngine.applyChatCompletionPresetJson(
            settings = settings,
            preset = preset,
            bindPresetToConnection = PresetPrefsStore.load(getApplication()).bindPresetToConnection,
        )
        fun d(key: String): Double? = (applied[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
        fun i(key: String): Int? = (applied[key] as? JsonPrimitive)?.content?.toIntOrNull()
        fun b(key: String): Boolean = (applied[key] as? JsonPrimitive)?.content == "true"
        fun s(key: String): String? = (applied[key] as? JsonPrimitive)?.content
        _editingSampler.value = sam.copy(
            temperature = d("temp_openai") ?: sam.temperature,
            topP = d("top_p_openai") ?: sam.topP,
            presencePenalty = d("pres_pen_openai") ?: sam.presencePenalty,
            frequencyPenalty = d("freq_pen_openai") ?: sam.frequencyPenalty,
            topK = i("top_k_openai") ?: sam.topK,
            minP = d("min_p_openai") ?: sam.minP,
            topA = d("top_a_openai") ?: sam.topA,
            repetitionPenalty = d("repetition_penalty_openai") ?: sam.repetitionPenalty,
            seed = i("seed") ?: sam.seed,
            n = i("n") ?: sam.n,
            stream = b("stream_openai"),
            squashSystemMessages = b("squash_system_messages"),
            namesBehavior = i("names_behavior") ?: sam.namesBehavior,
            sendIfEmpty = s("send_if_empty") ?: sam.sendIfEmpty,
            impersonationPrompt = s("impersonation_prompt") ?: sam.impersonationPrompt,
            newChatPrompt = s("new_chat_prompt") ?: sam.newChatPrompt,
            newGroupChatPrompt = s("new_group_chat_prompt") ?: sam.newGroupChatPrompt,
            newExampleChatPrompt = s("new_example_chat_prompt") ?: sam.newExampleChatPrompt,
            continueNudgePrompt = s("continue_nudge_prompt") ?: sam.continueNudgePrompt,
            biasPresetSelected = s("bias_preset_selected") ?: sam.biasPresetSelected,
            wiFormat = s("wi_format") ?: sam.wiFormat,
            scenarioFormat = s("scenario_format") ?: sam.scenarioFormat,
            personalityFormat = s("personality_format") ?: sam.personalityFormat,
            groupNudgePrompt = s("group_nudge_prompt") ?: sam.groupNudgePrompt,
            assistantPrefill = s("assistant_prefill") ?: sam.assistantPrefill,
            assistantImpersonation = s("assistant_impersonation") ?: sam.assistantImpersonation,
            continuePrefill = b("continue_prefill"),
            continuePostfix = s("continue_postfix") ?: sam.continuePostfix,
            functionCalling = b("function_calling"),
            showThoughts = b("show_thoughts"),
            mediaInlining = b("media_inlining"),
            inlineImageQuality = s("inline_image_quality") ?: sam.inlineImageQuality,
            enableWebSearch = b("enable_web_search"),
            toolReasoningMode = s("tool_reasoning_mode") ?: sam.toolReasoningMode,
            toolCallRecurseLimit = i("tool_call_recurse_limit") ?: sam.toolCallRecurseLimit,
            requestImages = b("request_images"),
            requestImageAspectRatio = s("request_image_aspect_ratio") ?: sam.requestImageAspectRatio,
            requestImageResolution = s("request_image_resolution") ?: sam.requestImageResolution,
            maxContextUnlocked = b("max_context_unlocked"),
        )
        // 官方 onSettingsPresetChange：prompts/prompt_order 直接写 PromptManager
        val presetJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        (preset["prompts"] as? kotlinx.serialization.json.JsonArray)?.let { arr ->
            runCatching {
                PromptManagerPrefs.savePrompts(
                    getApplication(),
                    presetJson.decodeFromJsonElement(ListSerializer(PromptItem.serializer()), arr),
                )
            }
        }
        (preset["prompt_order"] as? kotlinx.serialization.json.JsonArray)?.let { arr ->
            runCatching {
                val orders = arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val cid = obj["character_id"]?.jsonPrimitive?.contentOrNull
                    val order = obj["order"]?.jsonArray ?: return@mapNotNull null
                    cid to presetJson.decodeFromJsonElement(ListSerializer(PromptOrderEntry.serializer()), order)
                }.toMap()
                PromptManagerPrefs.saveOrders(getApplication(), orders)
            }
        }
        _maxTokens.value = i("openai_max_tokens") ?: _maxTokens.value
        _contextWindow.value = i("openai_max_context") ?: _contextWindow.value
        _message.value = "已应用采样预设：$name"
    }

    fun save() {
        val spec = provider() ?: return
        if (_selectedModel.value.isBlank() && spec.defaultModels.isNotEmpty()) {
            _selectedModel.value = spec.defaultModels.first()
        }
        val profile = buildProfile()
        repo.saveProfile(profile, active = true)
        refreshProfiles()
        ProviderState.refresh(profile)
        _message.value = "已保存"
    }

    fun switchActive(id: String) {
        repo.setActiveProfile(id)
        refreshProfiles()
        ProviderState.refresh(repo.activeProfile())
    }

    fun deleteProfile(id: String) {
        repo.deleteProfile(id)
        refreshProfiles()
        ProviderState.refresh(repo.activeProfile())
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 模型窗口优先，其次厂商默认窗口，兜底 8192。 */
    private fun defaultContextFor(spec: ProviderSpec, model: String): Int =
        spec.modelContexts[model] ?: spec.defaultContextWindow ?: 8192

    private fun buildProfile(): ConnectionProfile {
        val spec = provider() ?: return ConnectionProfile(providerId = "")
        val baseOverride = _baseUrl.value.trim().ifBlank { "" }
        return ConnectionProfile(
            id = editingId.orEmpty(),
            name = _profileName.value.ifBlank { spec.displayName },
            providerId = spec.id,
            apiKey = _apiKey.value,
            baseUrlOverride = if (baseOverride == spec.baseUrl) "" else baseOverride,
            model = _selectedModel.value,
            region = _region.value,
            accountId = _accountId.value,
            apiVersionOverride = _apiVersion.value,
            contextWindow = _contextWindow.value,
            reverseProxy = _reverseProxy.value,
            proxyPassword = _proxyPassword.value,
            customUrl = _customUrl.value,
            customIncludeBody = _customIncludeBody.value,
            customExcludeBody = _customExcludeBody.value,
            customIncludeHeaders = _customIncludeHeaders.value,
            customPromptPostProcessing = _customPromptPostProcessing.value,
            bypassStatusCheck = _bypassStatusCheck.value,
            showExternalModels = _showExternalModels.value,
            groupModels = _groupModels.value,
            sortModels = _sortModels.value,
            azureDeploymentName = _azureDeploymentName.value,
            azureOpenaiModel = _azureOpenaiModel.value,
            vertexaiAuthMode = _vertexaiAuthMode.value,
            vertexaiExpressProjectId = _vertexaiExpressProjectId.value,
            nanogptProvider = _nanogptProvider.value,
            nanogptPaygOverride = _nanogptPaygOverride.value,
            sampler = _editingSampler.value.copy(maxTokens = _maxTokens.value),
        )
    }

    private fun refreshProfiles() {
        _profiles.value = repo.profiles()
        _activeId.value = repo.activeProfile()?.id ?: ""
    }

    private fun hasModelsEndpoint(spec: ProviderSpec): Boolean =
        spec.modelsEndpoint.isNotBlank() || spec.id == "azure" || spec.id == "workers-ai"

    /** 人话报错。 */
    private fun humanError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "网络不通，请检查网络或地址"
            e is ConnectException -> "连不上服务器，请检查地址或网络"
            e is SocketTimeoutException -> "连接超时，请检查网络或地址"
            m.contains("HTTP 401") || m.contains("HTTP 403") -> "Key 不对或没有权限"
            m.contains("HTTP 404") -> "接口地址不对（404）"
            m.contains("HTTP 429") -> "请求太频繁，请稍后再试"
            m.contains("HTTP 500") || m.contains("HTTP 502") || m.contains("HTTP 503") -> "服务端暂时不可用"
            m.contains("需要账户 ID") || m.contains("Vertex AI") -> m
            else -> "连接失败：${m.ifBlank { "未知错误" }}"
        }
    }
}
