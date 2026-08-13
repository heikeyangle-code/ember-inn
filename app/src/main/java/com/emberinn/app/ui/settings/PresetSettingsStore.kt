package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.app.data.ChatRepository
import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.PresetApplyEngine
import com.emberinn.engine.prompt.PresetLibrary
import com.emberinn.engine.prompt.ReasoningSettings
import com.emberinn.engine.prompt.SyspromptSettings
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.SamplerParams
import com.emberinn.engine.provider.TextgenSettingsDefaults
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

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
)

object PresetSettingsStore {

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

    /** 官方 context_presets change：应用 + 写全局消费位点 + 记录选中预设名。 */
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
        return result.context
    }

    /** 官方 instruct_presets change。 */
    fun applyInstruct(context: Context, preset: JsonObject): InstructSettings {
        val state = load(context)
        val result = PresetApplyEngine.applyInstructPreset(state.instruct, preset)
        save(context, state.copy(instruct = result))
        PresetPrefsStore.save(context, PresetPrefsStore.load(context).copy(instructPreset = result.preset))
        return result
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
            val base = JsonObject(defaults.toMutableMap().apply { putAll(stored) })
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
            settings = samplerSettingsJson(profile.sampler, profile.contextWindow, profile.sampler.maxTokens),
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
                maxTokens = i("openai_max_tokens") ?: profile.sampler.maxTokens,
                reasoningEffort = s("reasoning_effort") ?: profile.sampler.reasoningEffort,
                verbosity = s("verbosity") ?: profile.sampler.verbosity,
                useSysprompt = b("use_sysprompt"),
                useFallback = b("openrouter_use_fallback"),
                allowFallbacks = b("openrouter_allow_fallbacks"),
                middleout = s("openrouter_middleout") ?: profile.sampler.middleout,
                openRouterProviders = l("openrouter_providers") ?: profile.sampler.openRouterProviders,
                openRouterQuantizations = l("openrouter_quantizations") ?: profile.sampler.openRouterQuantizations,
            ),
            contextWindow = i("openai_max_context") ?: profile.contextWindow,
        )
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
        put("openrouter_use_fallback", JsonPrimitive(sampler.useFallback))
        put("openrouter_allow_fallbacks", JsonPrimitive(sampler.allowFallbacks))
        put("openrouter_middleout", JsonPrimitive(sampler.middleout))
        put("openrouter_providers", kotlinx.serialization.json.JsonArray(sampler.openRouterProviders.map { JsonPrimitive(it) }))
        put("openrouter_quantizations", kotlinx.serialization.json.JsonArray(sampler.openRouterQuantizations.map { JsonPrimitive(it) }))
    }
}
