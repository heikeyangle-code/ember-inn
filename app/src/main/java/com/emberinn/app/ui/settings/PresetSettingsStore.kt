package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.PromptManagerPrefs
import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.PresetApplyEngine
import com.emberinn.engine.prompt.PresetLibrary
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptOrderEntry
import com.emberinn.engine.prompt.ReasoningSettings
import com.emberinn.engine.prompt.SyspromptSettings
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderStore
import com.emberinn.engine.provider.SamplerParams
import com.emberinn.engine.provider.TextgenSettingsDefaults
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 预设应用后的生效设置（官方 power_user.context / instruct / sysprompt / reasoning + context 全局字段）。
 * 引擎 PresetApplyEngine 判定（官方差分锁定），App 只负责落盘 + 写真实消费位点：
 * - context 全局字段 trim_sentences/names_as_stop_strings → BehaviorPrefs（CleanUp/停用词真实消费）；
 *   example_separator → RenderPrefs（示例分隔符）；story_string 等文本补全字段 → textgen 后端接入时消费（登记）。
 * - instruct → InstructSettings 持久化（官方 instruct 模式引擎已备，textgen 后端接入时消费，登记）。
 * - sysprompt → 官方只对 main_api != 'openai' 的文本补全后端生效；App 无文本补全后端，持久化待后端（登记）。
 * - reasoning → template（prefix/suffix/separator）进总装（PromptReasoning.addToMessage）与显示（formatReasoning）。
 */
@Serializable
data class PresetSettingsState(
    val context: ContextSettings = ContextSettings(),
    val contextGlobals: PresetApplyEngine.ContextGlobals = PresetApplyEngine.ContextGlobals(),
    val instruct: InstructSettings = InstructSettings(),
    val sysprompt: SyspromptSettings = SyspromptSettings(),
    val reasoning: ReasoningSettings = ReasoningSettings(),
    /** 官方 oai_settings.extensions：预设里的扩展配置（onSettingsPresetChange 直接赋值；App 无扩展消费，持久化登记）。 */
    val openaiExtensions: JsonObject = JsonObject(emptyMap()),
    /** 官方 power_user.context_derived（默认关）：连接模型时按元数据派生 context 模板。 */
    val contextDerived: Boolean = false,
    /** 官方 power_user.instruct_derived（默认关）：连接模型时按元数据派生 instruct 模板。 */
    val instructDerived: Boolean = false,
    /** 官方 bind_model_templates（默认关）：模型切换时自动激活绑定的 context/instruct 模板。 */
    val bindModelTemplates: Boolean = false,
    /** 官方 power_user.model_templates_mappings：模型 id / chat template hash → {context, instruct}。 */
    val modelTemplateMappings: JsonObject = JsonObject(emptyMap()),
    /** 官方 power_user.context_size_derived（默认关）：koboldcpp/llamacpp 连接时按后端 n_ctx 自动改上下文。 */
    val contextSizeDerived: Boolean = false,
    /** 官方 power_user.chat_template_hash：最近连接模型（koboldcpp/llamacpp）的 chat template sha256。 */
    val chatTemplateHash: String = "",
)

object PresetSettingsStore {

    /** 活动连接协议 → 采样预设目录（官方按 main_api 取 preset manager）。 */
    fun samplerPresetType(context: Context): String {
        val profile = ProviderStore(File(context.filesDir, "provider")).load() ?: return "openai"
        val provider = ProviderRegistry.get(profile.providerId) ?: return "openai"
        return when (provider.protocol) {
            "textgenerationwebui" -> "textgen"
            "novel" -> "novel"
            "kobold" -> "kobold"
            else -> "openai"
        }
    }

    /** 当前协议的采样预设名（官方 presetManager.getAllPresets；kobold 含 GUI 特殊预设）。 */
    fun samplerPresetNames(context: Context): List<String> {
        val type = samplerPresetType(context)
        val base = PresetLibrary.samplerPresets(type).map { it.name } + UserPresetStore.list(context, "sampler")
        return if (type == "kobold") listOf("gui") + base else base
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(context: Context) = File(context.filesDir, "preset_settings_v1.json")

    fun load(context: Context): PresetSettingsState = runCatching {
        json.decodeFromString(PresetSettingsState.serializer(), file(context).readText())
    }.getOrDefault(PresetSettingsState())

    fun save(context: Context, state: PresetSettingsState) {
        file(context).writeText(json.encodeToString(PresetSettingsState.serializer(), state))
    }

    /** 生效设置表单直接编辑（官方 Advanced Formatting 表单语义）：落盘 + 写真实消费位点。 */
    fun update(context: Context, state: PresetSettingsState) {
        save(context, state)
        val behavior = BehaviorPrefs.load(context)
        BehaviorPrefs.save(
            context,
            behavior.copy(
                trimSentences = state.contextGlobals.trimSentences,
                namesAsStopStrings = state.context.namesAsStopStrings,
            ),
        )
        RenderPrefs.setExampleSeparator(context, state.context.exampleSeparator)
    }

    /** 官方 context_presets change：应用 + 写全局消费位点 + 记录选中预设名；bind_to_context 联动选同名 instruct。 */
    fun applyContext(context: Context, preset: JsonObject): ContextSettings {
        val state = load(context)
        val result = PresetApplyEngine.applyContextPreset(state.context, state.contextGlobals, preset)
        val behavior = BehaviorPrefs.load(context)
        BehaviorPrefs.save(
            context,
            behavior.copy(
                trimSentences = result.globals.trimSentences,
                namesAsStopStrings = result.context.namesAsStopStrings,
            ),
        )
        RenderPrefs.setExampleSeparator(context, result.context.exampleSeparator)
        save(context, state.copy(context = result.context, contextGlobals = result.globals))
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(contextPreset = result.presetName))
        // 官方 power-user.js：context change 后若 instruct.bind_to_context，选同名 instruct 模板
        if (result.context.preset.isNotBlank() && state.instruct.bindToContext) {
            val instructJson = presetJsonOf("instruct", result.context.preset, context)
            if (instructJson.isNotEmpty() && instructJson["name"]?.jsonPrimitive?.contentOrNull != state.instruct.preset) {
                applyInstruct(context, instructJson)
            }
        }
        return result.context
    }

    /** 官方 instruct_presets change；bind_to_context 联动选同名 context 模板。 */
    fun applyInstruct(context: Context, preset: JsonObject): InstructSettings {
        val state = load(context)
        val result = PresetApplyEngine.applyInstructPreset(state.instruct, preset)
        save(context, state.copy(instruct = result))
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(instructPreset = result.preset))
        // 官方 instruct-mode.js：instruct change 后若 bind_to_context，选同名 context 模板
        if (result.bindToContext && result.preset.isNotBlank()) {
            val ctxMatch = PresetLibrary.contextPresetsRaw().firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == result.preset
            } ?: UserPresetStore.load(context, "context", result.preset)
            if (ctxMatch != null && ctxMatch["name"]?.jsonPrimitive?.contentOrNull != state.context.preset) {
                applyContext(context, ctxMatch)
            }
        }
        return result
    }

    internal fun presetJsonOf(type: String, name: String, context: Context): JsonObject =
        when (type) {
            "instruct" -> PresetLibrary.instructPresetsRaw().firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == name
            } ?: UserPresetStore.load(context, "instruct", name) ?: JsonObject(emptyMap())
            "context" -> PresetLibrary.contextPresetsRaw().firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == name
            } ?: UserPresetStore.load(context, "context", name) ?: JsonObject(emptyMap())
            else -> JsonObject(emptyMap())
        }

    /** 官方 sysprompt $select.on('change')：enabled 自动置 true。 */
    fun applySysprompt(context: Context, preset: JsonObject): SyspromptSettings {
        val state = load(context)
        val result = PresetApplyEngine.applySyspromptPreset(state.sysprompt, preset)
        save(context, state.copy(sysprompt = result))
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(syspromptPreset = result.name))
        return result
    }

    /** 官方 reasoning $select.on('change')。 */
    fun applyReasoning(context: Context, preset: JsonObject): ReasoningSettings {
        val state = load(context)
        val result = PresetApplyEngine.applyReasoningPreset(state.reasoning, preset)
        save(context, state.copy(reasoning = result))
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(reasoningPreset = result.name))
        return result
    }

    /** 官方预设 onSettingsPresetChange：选中即应用到当前活动连接（bind_preset_to_connection 默认 true）。
     *  按活动协议分发：textgenerationwebui → textgen 预设；其余 → openai 采样预设（全字段回写）。 */
    fun applySampler(context: Context, name: String): Boolean {
        val repo = ChatRepository(context)
        val profile = repo.profile() ?: return false
        val provider = ProviderRegistry.get(profile.providerId)
        if (provider?.protocol == "kobold") {
            // 官方 GUI KoboldAI Settings：不落盘预设，直接使用当前 UI 设置
            if (name == "gui") {
                PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(samplerPreset = "gui"))
                return true
            }
            val preset = PresetLibrary.samplerPresets("kobold").firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sampler", name) ?: return false
            val sliderKeys = listOf(
                "temp", "rep_pen", "rep_pen_range", "top_p", "min_p", "top_a", "top_k",
                "typical", "tfs", "rep_pen_slope", "sampler_order", "mirostat",
                "mirostat_tau", "mirostat_eta", "grammar", "seed",
            )
            val defaults = buildJsonObject {
                put("temp", JsonPrimitive(1))
                put("rep_pen", JsonPrimitive(1))
                put("rep_pen_range", JsonPrimitive(0))
                put("top_p", JsonPrimitive(1))
                put("min_p", JsonPrimitive(0))
                put("top_a", JsonPrimitive(1))
                put("top_k", JsonPrimitive(0))
                put("typical", JsonPrimitive(1))
                put("tfs", JsonPrimitive(1))
                put("rep_pen_slope", JsonPrimitive(0.9))
                put("sampler_order", kotlinx.serialization.json.JsonArray(listOf(0, 1, 2, 3, 4, 5, 6).map { JsonPrimitive(it) }))
                put("mirostat", JsonPrimitive(0))
                put("mirostat_tau", JsonPrimitive(5.0))
                put("mirostat_eta", JsonPrimitive(0.1))
                put("use_default_badwordsids", JsonPrimitive(false))
                put("grammar", JsonPrimitive(""))
                put("seed", JsonPrimitive(-1))
            }
            val base = KoboldSettingsStore.load(context)
            val applied = PresetApplyEngine.applyKoboldPreset(
                settings = base,
                preset = preset,
                keys = sliderKeys + listOf("extensions", "streaming_kobold", "use_default_badwordsids"),
                defaults = defaults,
                sliderKeys = sliderKeys,
            )
            KoboldSettingsStore.save(context, applied)
            val genParams = PresetApplyEngine.applyGenerationParamsFromPreset(
                preset, profile.sampler.maxTokens, profile.contextWindow,
            )
            if (genParams.maxContext != profile.contextWindow || genParams.amountGen != profile.sampler.maxTokens) {
                repo.saveProfile(
                    profile.copy(
                        contextWindow = genParams.maxContext,
                        sampler = profile.sampler.copy(maxTokens = genParams.amountGen),
                    ),
                    active = true,
                )
            }
            PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(samplerPreset = name))
            return true
        }
        if (provider?.protocol == "novel") {
            val preset = PresetLibrary.samplerPresets("novel").firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sampler", name) ?: return false
            val base = NovelSettingsStore.load(context)
            val defaults = buildJsonObject {
                put("default_order", kotlinx.serialization.json.JsonArray(listOf(
                    "temperature", "tail_free_sampling", "repetition_penalty", "top_p", "top_k",
                ).map { JsonPrimitive(it) }))
                put("default_preamble", JsonPrimitive(""))
            }
            val applied = PresetApplyEngine.applyNovelPreset(base, preset, defaults)
            NovelSettingsStore.save(context, applied)
            // 官方 setGenerationParamsFromPreset：max_length/genamt → 连接上下文/最大回复
            val genParams = PresetApplyEngine.applyGenerationParamsFromPreset(
                preset, profile.sampler.maxTokens, profile.contextWindow,
            )
            if (genParams.maxContext != profile.contextWindow || genParams.amountGen != profile.sampler.maxTokens) {
                repo.saveProfile(
                    profile.copy(
                        contextWindow = genParams.maxContext,
                        sampler = profile.sampler.copy(maxTokens = genParams.amountGen),
                    ),
                    active = true,
                )
            }
            PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(samplerPreset = name))
            return true
        }
        if (provider?.protocol == "textgenerationwebui") {
            val preset = PresetLibrary.samplerPresets("textgen").firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sampler", name) ?: return false
            val defaults = TextgenSettingsDefaults.defaults()
            val stored = TextgenSettingsStore.load(context)
            val base = JsonObject(defaults.toMutableMap().apply { stored.forEach { (k, v) -> put(k, v) } })
            val orders = buildJsonObject {
                put("sampler_order", defaults["sampler_order"] ?: JsonPrimitive(""))
                put("sampler_priority", defaults["sampler_priority"] ?: JsonPrimitive(""))
                put("samplers_priorities", defaults["samplers_priorities"] ?: JsonPrimitive(""))
                put("samplers", defaults["samplers"] ?: JsonPrimitive(""))
            }
            val applied = PresetApplyEngine.applyTextgenPreset(base, preset, orders)
            TextgenSettingsStore.save(context, applied)
            PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(samplerPreset = name))
            return true
        }
        val preset = PresetLibrary.samplerPresets("openai").firstOrNull { it.name == name }?.settings
            ?: UserPresetStore.load(context, "sampler", name) ?: return false
        val appliedJson = PresetApplyEngine.applyChatCompletionPresetJson(
            settings = buildJsonObject {
                samplerSettingsJson(profile.sampler, profile.contextWindow, profile.sampler.maxTokens).forEach { (k, v) -> put(k, v) }
                connectionSettingsJson(profile).forEach { (k, v) -> put(k, v) }
            },
            preset = preset,
            bindPresetToConnection = PresetPrefsStore.load(context).bindPresetToConnection,
        )
        fun d(key: String): Double? = (appliedJson[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
        fun i(key: String): Int? = (appliedJson[key] as? JsonPrimitive)?.content?.toIntOrNull()
        fun b(key: String): Boolean = (appliedJson[key] as? JsonPrimitive)?.content == "true"
        fun s(key: String): String? = (appliedJson[key] as? JsonPrimitive)?.content
        fun l(key: String): List<String>? =
            (appliedJson[key] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        // 连接类：当前 provider 对应模型键生效（openai_model/claude_model/openrouter_model/google_model/...）
        val modelKey = providerModelKey(profile.providerId)
        val pid = provider?.id ?: ""
        val updated = profile.copy(
            model = modelKey?.let { s(it)?.takeIf { v -> v.isNotBlank() } } ?: profile.model,
            sampler = profile.sampler.copy(
                temperature = d("temp_openai") ?: profile.sampler.temperature,
                topP = d("top_p_openai") ?: profile.sampler.topP,
                frequencyPenalty = d("freq_pen_openai") ?: profile.sampler.frequencyPenalty,
                presencePenalty = d("pres_pen_openai") ?: profile.sampler.presencePenalty,
                topK = i("top_k_openai") ?: profile.sampler.topK,
                topA = d("top_a_openai") ?: profile.sampler.topA,
                minP = d("min_p_openai") ?: profile.sampler.minP,
                repetitionPenalty = d("repetition_penalty_openai") ?: profile.sampler.repetitionPenalty,
                seed = i("seed") ?: profile.sampler.seed,
                n = i("n") ?: profile.sampler.n,
                stream = b("stream_openai"),
                squashSystemMessages = b("squash_system_messages"),
                maxTokens = i("openai_max_tokens") ?: profile.sampler.maxTokens,
                reasoningEffort = s("reasoning_effort") ?: profile.sampler.reasoningEffort,
                verbosity = s("verbosity") ?: profile.sampler.verbosity,
                useSysprompt = b("use_sysprompt"),
                useFallback = b("openrouter_use_fallback"),
                allowFallbacks = b("openrouter_allow_fallbacks"),
                middleout = s("openrouter_middleout") ?: profile.sampler.middleout,
                openRouterProviders = l("openrouter_providers") ?: profile.sampler.openRouterProviders,
                openRouterQuantizations = l("openrouter_quantizations") ?: profile.sampler.openRouterQuantizations,
                // 官方 oai_settings 其余生成字段（全字段回写）
                namesBehavior = i("names_behavior") ?: profile.sampler.namesBehavior,
                sendIfEmpty = s("send_if_empty") ?: profile.sampler.sendIfEmpty,
                impersonationPrompt = s("impersonation_prompt") ?: profile.sampler.impersonationPrompt,
                newChatPrompt = s("new_chat_prompt") ?: profile.sampler.newChatPrompt,
                newGroupChatPrompt = s("new_group_chat_prompt") ?: profile.sampler.newGroupChatPrompt,
                newExampleChatPrompt = s("new_example_chat_prompt") ?: profile.sampler.newExampleChatPrompt,
                continueNudgePrompt = s("continue_nudge_prompt") ?: profile.sampler.continueNudgePrompt,
                biasPresetSelected = s("bias_preset_selected") ?: profile.sampler.biasPresetSelected,
                wiFormat = s("wi_format") ?: profile.sampler.wiFormat,
                scenarioFormat = s("scenario_format") ?: profile.sampler.scenarioFormat,
                personalityFormat = s("personality_format") ?: profile.sampler.personalityFormat,
                groupNudgePrompt = s("group_nudge_prompt") ?: profile.sampler.groupNudgePrompt,
                assistantPrefill = s("assistant_prefill") ?: profile.sampler.assistantPrefill,
                assistantImpersonation = s("assistant_impersonation") ?: profile.sampler.assistantImpersonation,
                continuePrefill = b("continue_prefill"),
                continuePostfix = s("continue_postfix") ?: profile.sampler.continuePostfix,
                functionCalling = b("function_calling"),
                showThoughts = b("show_thoughts"),
                mediaInlining = b("media_inlining"),
                inlineImageQuality = s("inline_image_quality") ?: profile.sampler.inlineImageQuality,
                enableWebSearch = b("enable_web_search"),
                toolReasoningMode = s("tool_reasoning_mode") ?: profile.sampler.toolReasoningMode,
                toolCallRecurseLimit = i("tool_call_recurse_limit") ?: profile.sampler.toolCallRecurseLimit,
                requestImages = b("request_images"),
                requestImageAspectRatio = s("request_image_aspect_ratio") ?: profile.sampler.requestImageAspectRatio,
                requestImageResolution = s("request_image_resolution") ?: profile.sampler.requestImageResolution,
                maxContextUnlocked = b("max_context_unlocked"),
            ),
            contextWindow = i("openai_max_context") ?: profile.contextWindow,
            reverseProxy = s("reverse_proxy") ?: profile.reverseProxy,
            proxyPassword = s("proxy_password") ?: profile.proxyPassword,
            customUrl = s("custom_url") ?: profile.customUrl,
            customIncludeBody = s("custom_include_body") ?: profile.customIncludeBody,
            customExcludeBody = s("custom_exclude_body") ?: profile.customExcludeBody,
            customIncludeHeaders = s("custom_include_headers") ?: profile.customIncludeHeaders,
            customPromptPostProcessing = s("custom_prompt_post_processing") ?: profile.customPromptPostProcessing,
            bypassStatusCheck = b("bypass_status_check"),
            showExternalModels = b("show_external_models"),
            groupModels = b("group_models"),
            sortModels = s("sort_models") ?: profile.sortModels,
            azureDeploymentName = s("azure_deployment_name") ?: profile.azureDeploymentName,
            azureOpenaiModel = s("azure_openai_model") ?: profile.azureOpenaiModel,
            vertexaiAuthMode = s("vertexai_auth_mode") ?: profile.vertexaiAuthMode,
            vertexaiExpressProjectId = s("vertexai_express_project_id") ?: profile.vertexaiExpressProjectId,
            nanogptProvider = s("nanogpt_provider") ?: profile.nanogptProvider,
            nanogptPaygOverride = b("nanogpt_payg_override"),
            baseUrlOverride = if (pid == "azure" && appliedJson["azure_base_url"] != null) {
                s("azure_base_url") ?: profile.baseUrlOverride
            } else {
                profile.baseUrlOverride
            },
            apiVersionOverride = if (pid == "azure" && appliedJson["azure_api_version"] != null) {
                s("azure_api_version") ?: profile.apiVersionOverride
            } else {
                profile.apiVersionOverride
            },
            region = if (appliedJson["vertexai_region"] != null) {
                s("vertexai_region") ?: profile.region
            } else {
                profile.region
            },
            accountId = if (appliedJson["workers_ai_account_id"] != null) {
                s("workers_ai_account_id") ?: profile.accountId
            } else {
                profile.accountId
            },
        )
        // 官方 onSettingsPresetChange：prompts / prompt_order 直接写 oai_settings
        // （= PromptManager serviceSettings），App 落 PromptManagerPrefs（全局条目 + 按角色顺序表）。
        (preset["prompts"] as? JsonArray)?.let { arr ->
            runCatching {
                PromptManagerPrefs.savePrompts(
                    context,
                    json.decodeFromJsonElement(ListSerializer(PromptItem.serializer()), arr),
                )
            }
        }
        (preset["prompt_order"] as? JsonArray)?.let { arr ->
            runCatching {
                val orders = arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val cid = obj["character_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val order = obj["order"]?.jsonArray ?: return@mapNotNull null
                    cid to json.decodeFromJsonElement(ListSerializer(PromptOrderEntry.serializer()), order)
                }.toMap()
                PromptManagerPrefs.saveOrders(context, orders)
            }
        }
        // 官方 onSettingsPresetChange：extensions 无 DOM 选择器，直接 oai_settings.extensions = preset.extensions || {}
        (preset["extensions"] as? JsonObject)?.let { ext ->
            val state = load(context)
            save(context, state.copy(openaiExtensions = ext))
        }
        repo.saveProfile(updated, active = true)
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(samplerPreset = name))
        return true
    }

    /** 官方 settingsToUpdate 的连接模型键（openai_model/claude_model/...）。 */
    private fun providerModelKey(providerId: String): String? = when (providerId) {
        "openai" -> "openai_model"
        "anthropic" -> "claude_model"
        "openrouter" -> "openrouter_model"
        "google" -> "google_model"
        "mistral" -> "mistralai_model"
        "xai" -> "xai_model"
        "cohere" -> "cohere_model"
        "deepseek" -> "deepseek_model"
        "groq" -> "groq_model"
        "moonshot" -> "moonshot_model"
        "zhipu" -> "glm_model"
        "dashscope" -> "dashscope_model"
        "siliconflow" -> "siliconflow_model"
        "minimax" -> "minimax_model"
        "perplexity" -> "perplexity_model"
        "workers-ai" -> "workers_ai_model"
        "azure" -> "azure_openai_model"
        "zai" -> "zai_model"
        "custom" -> "custom_model"
        "electronhub" -> "electronhub_model"
        "chutes" -> "chutes_model"
        "nanogpt" -> "nanogpt_model"
        "aimlapi" -> "aimlapi_model"
        "pollinations" -> "pollinations_model"
        "cometapi" -> "cometapi_model"
        else -> null
    }

    /** 官方 openai.js getChatCompletionPreset 的输入：App 支持字段 → 官方 oai_settings 键（全字段，预设应用后回写不丢）。 */
    fun samplerSettingsJson(sampler: SamplerParams, contextWindow: Int, maxTokens: Int): JsonObject = buildJsonObject {
        put("temp_openai", JsonPrimitive(sampler.temperature))
        put("top_p_openai", JsonPrimitive(sampler.topP))
        put("freq_pen_openai", JsonPrimitive(sampler.frequencyPenalty))
        put("pres_pen_openai", JsonPrimitive(sampler.presencePenalty))
        put("top_k_openai", JsonPrimitive(sampler.topK))
        put("top_a_openai", JsonPrimitive(sampler.topA))
        put("min_p_openai", JsonPrimitive(sampler.minP))
        put("repetition_penalty_openai", JsonPrimitive(sampler.repetitionPenalty))
        put("seed", JsonPrimitive(sampler.seed))
        put("n", JsonPrimitive(sampler.n))
        put("stream_openai", JsonPrimitive(sampler.stream))
        put("openai_max_context", JsonPrimitive(contextWindow))
        put("openai_max_tokens", JsonPrimitive(maxTokens))
        put("reasoning_effort", JsonPrimitive(sampler.reasoningEffort))
        sampler.verbosity?.let { put("verbosity", JsonPrimitive(it)) }
        put("use_sysprompt", JsonPrimitive(sampler.useSysprompt))
        put("squash_system_messages", JsonPrimitive(sampler.squashSystemMessages))
        put("names_behavior", JsonPrimitive(sampler.namesBehavior))
        put("send_if_empty", JsonPrimitive(sampler.sendIfEmpty))
        put("impersonation_prompt", JsonPrimitive(sampler.impersonationPrompt))
        put("new_chat_prompt", JsonPrimitive(sampler.newChatPrompt))
        put("new_group_chat_prompt", JsonPrimitive(sampler.newGroupChatPrompt))
        put("new_example_chat_prompt", JsonPrimitive(sampler.newExampleChatPrompt))
        put("continue_nudge_prompt", JsonPrimitive(sampler.continueNudgePrompt))
        put("bias_preset_selected", JsonPrimitive(sampler.biasPresetSelected))
        put("wi_format", JsonPrimitive(sampler.wiFormat))
        put("scenario_format", JsonPrimitive(sampler.scenarioFormat))
        put("personality_format", JsonPrimitive(sampler.personalityFormat))
        put("group_nudge_prompt", JsonPrimitive(sampler.groupNudgePrompt))
        put("assistant_prefill", JsonPrimitive(sampler.assistantPrefill))
        put("assistant_impersonation", JsonPrimitive(sampler.assistantImpersonation))
        put("continue_prefill", JsonPrimitive(sampler.continuePrefill))
        put("continue_postfix", JsonPrimitive(sampler.continuePostfix))
        put("function_calling", JsonPrimitive(sampler.functionCalling))
        put("show_thoughts", JsonPrimitive(sampler.showThoughts))
        put("media_inlining", JsonPrimitive(sampler.mediaInlining))
        put("inline_image_quality", JsonPrimitive(sampler.inlineImageQuality))
        put("enable_web_search", JsonPrimitive(sampler.enableWebSearch))
        put("tool_reasoning_mode", JsonPrimitive(sampler.toolReasoningMode))
        put("tool_call_recurse_limit", JsonPrimitive(sampler.toolCallRecurseLimit))
        put("request_images", JsonPrimitive(sampler.requestImages))
        put("request_image_aspect_ratio", JsonPrimitive(sampler.requestImageAspectRatio))
        put("request_image_resolution", JsonPrimitive(sampler.requestImageResolution))
        put("max_context_unlocked", JsonPrimitive(sampler.maxContextUnlocked))
        put("openrouter_use_fallback", JsonPrimitive(sampler.useFallback))
        put("openrouter_allow_fallbacks", JsonPrimitive(sampler.allowFallbacks))
        put("openrouter_middleout", JsonPrimitive(sampler.middleout))
        put("openrouter_providers", kotlinx.serialization.json.JsonArray(sampler.openRouterProviders.map { JsonPrimitive(it) }))
        put("openrouter_quantizations", kotlinx.serialization.json.JsonArray(sampler.openRouterQuantizations.map { JsonPrimitive(it) }))
    }

    /** 官方 settingsToUpdate 的连接类字段（bind_preset_to_connection 由引擎键表 gating）。 */
    fun connectionSettingsJson(profile: ConnectionProfile): JsonObject = buildJsonObject {
        put("reverse_proxy", JsonPrimitive(profile.reverseProxy))
        put("proxy_password", JsonPrimitive(profile.proxyPassword))
        put("custom_url", JsonPrimitive(profile.customUrl))
        put("custom_include_body", JsonPrimitive(profile.customIncludeBody))
        put("custom_exclude_body", JsonPrimitive(profile.customExcludeBody))
        put("custom_include_headers", JsonPrimitive(profile.customIncludeHeaders))
        put("custom_prompt_post_processing", JsonPrimitive(profile.customPromptPostProcessing))
        put("bypass_status_check", JsonPrimitive(profile.bypassStatusCheck))
        put("show_external_models", JsonPrimitive(profile.showExternalModels))
        put("group_models", JsonPrimitive(profile.groupModels))
        put("sort_models", JsonPrimitive(profile.sortModels))
        put("azure_base_url", JsonPrimitive(profile.baseUrlOverride))
        put("azure_deployment_name", JsonPrimitive(profile.azureDeploymentName))
        put("azure_api_version", JsonPrimitive(profile.apiVersionOverride))
        put("azure_openai_model", JsonPrimitive(profile.azureOpenaiModel))
        put("vertexai_auth_mode", JsonPrimitive(profile.vertexaiAuthMode))
        put("vertexai_region", JsonPrimitive(profile.region))
        put("vertexai_express_project_id", JsonPrimitive(profile.vertexaiExpressProjectId))
        put("workers_ai_account_id", JsonPrimitive(profile.accountId))
        put("nanogpt_provider", JsonPrimitive(profile.nanogptProvider))
        put("nanogpt_payg_override", JsonPrimitive(profile.nanogptPaygOverride))
    }
}
