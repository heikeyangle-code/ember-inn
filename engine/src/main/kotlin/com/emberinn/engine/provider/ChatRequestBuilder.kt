package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaInliner
import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 采样参数（对齐官方 OpenAI 请求体字段）。 */
@Serializable
data class SamplerParams(
    val temperature: Double = 1.0,
    val topP: Double = 1.0,
    val maxTokens: Int = 300,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val stream: Boolean = false,
    /** 官方 reasoning_effort：auto/low/medium/high/min/max，默认 auto。 */
    val reasoningEffort: String = "auto",
    /** 官方 include_reasoning（思考内容是否随响应返回），默认关。 */
    val includeReasoning: Boolean = false,
    /** 官方 enableAdaptiveThinking（Claude opus-4-6/sonnet-4-6 是否走 adaptive），默认开。 */
    val enableAdaptiveThinking: Boolean = true,
    /** 官方 claude.enableSystemPromptCache（OpenRouter/Claude 系统提示缓存），默认关。 */
    val enableSystemPromptCache: Boolean = false,
    /** 官方 cachingAtDepth（消息缓存深度，-1 = 关）。 */
    val cachingAtDepth: Int = -1,
    /** 官方 claude.extendedTTL：false→5m，true→1h。 */
    val cacheTTL: String = "5m",
    /** 官方 settings.seed（-1 = 不发送）。 */
    val seed: Int = -1,
    /** 官方 settings.n（多回复/多 swipe）。 */
    val n: Int = 1,
    /** 官方 settings.top_k_openai（仅支持源发送；官方默认 0 = 不发送）。 */
    val topK: Int = 0,
    /** 官方 settings.min_p_openai（OpenRouter/NanoGPT/Chutes）。 */
    val minP: Double = 0.0,
    /** 官方 settings.top_a_openai（OpenRouter/NanoGPT）。 */
    val topA: Double = 0.0,
    /** 官方 settings.repetition_penalty_openai（OpenRouter/NanoGPT/Chutes/Workers AI）。 */
    val repetitionPenalty: Double = 1.0,
    /** 官方 openrouter_use_fallback（true → route=fallback）。 */
    val useFallback: Boolean = false,
    /** 官方 openrouter_providers（provider.order 数组）。 */
    val openRouterProviders: List<String> = emptyList(),
    /** 官方 openrouter_quantizations（provider.quantizations 数组）。 */
    val openRouterQuantizations: List<String> = emptyList(),
    /** 官方 openrouter_allow_fallbacks（默认 true）。 */
    val allowFallbacks: Boolean = true,
    /** 官方 openrouter_middleout：on/off/auto，默认 on。 */
    val middleout: String = "on",
    /** 官方 power_user.request_token_probabilities → openai/azure/custom logprobs（top_logprobs=5）。 */
    val requestTokenProbabilities: Boolean = false,
    /** 官方 use_sysprompt（Claude/Gemini 是否把 system 消息作独立 system 角色；官方默认 false）。 */
    val useSysprompt: Boolean = false,
    /** 官方 oai_settings.squash_system_messages：总装后合并连续无名 system 消息（官方默认 false）。 */
    val squashSystemMessages: Boolean = false,
    /** 官方 oai_settings.names_behavior：character_names_behavior.DEFAULT=0。 */
    val namesBehavior: Int = 0,
    /** 官方 oai_settings.send_if_empty：末条是 assistant 时补的 user 消息。 */
    val sendIfEmpty: String = "",
    /** 官方 oai_settings.impersonation_prompt（openai.js default_impersonation_prompt）。 */
    val impersonationPrompt: String = "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]",
    /** 官方 oai_settings.new_chat_prompt（default_new_chat_prompt）。 */
    val newChatPrompt: String = "[Start a new Chat]",
    /** 官方 oai_settings.new_group_chat_prompt（default_new_group_chat_prompt）。 */
    val newGroupChatPrompt: String = "[Start a new group chat. Group members: {{group}}]",
    /** 官方 oai_settings.new_example_chat_prompt（default_new_example_chat_prompt）。 */
    val newExampleChatPrompt: String = "[Example Chat]",
    /** 官方 oai_settings.continue_nudge_prompt（default_continue_nudge_prompt）。 */
    val continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
    /** 官方 oai_settings.bias_preset_selected（logit_bias 预设名）。 */
    val biasPresetSelected: String = "Default (none)",
    /** 官方 oai_settings.bias_presets（预设名 → 条目表）。 */
    val biasPresets: Map<String, List<BiasEntry>> = DEFAULT_BIAS_PRESETS,
    /** 官方 oai_settings.wi_format（世界书 {0} 占位）。 */
    val wiFormat: String = "{0}",
    /** 官方 oai_settings.scenario_format。 */
    val scenarioFormat: String = "{{scenario}}",
    /** 官方 oai_settings.personality_format。 */
    val personalityFormat: String = "{{personality}}",
    /** 官方 oai_settings.group_nudge_prompt。 */
    val groupNudgePrompt: String = "[Write the next reply only as {{char}}.]",
    /** 官方 oai_settings.assistant_prefill（Claude 继续生成预填）。 */
    val assistantPrefill: String = "",
    /** 官方 oai_settings.assistant_impersonation（冒充模式 assistant 预填）。 */
    val assistantImpersonation: String = "",
    /** 官方 oai_settings.continue_prefill（continue 时给最后 assistant 加预填，默认 false）。 */
    val continuePrefill: Boolean = false,
    /** 官方 oai_settings.continue_postfix：NONE=''/SPACE=' '/NEWLINE='\n'/DOUBLE_NEWLINE='\n\n'，默认 SPACE。 */
    val continuePostfix: String = " ",
    /** 官方 oai_settings.function_calling（工具调用总开关，默认 false）。 */
    val functionCalling: Boolean = false,
    /** 官方 oai_settings.show_thoughts（是否展示推理/思考，默认 true）。 */
    val showThoughts: Boolean = true,
    /** 官方 oai_settings.media_inlining（图片/视频/音频 data URL 内联，默认 true）。 */
    val mediaInlining: Boolean = true,
    /** 官方 oai_settings.inline_image_quality：auto/low/high。 */
    val inlineImageQuality: String = "auto",
    /** 官方 oai_settings.enable_web_search（联网搜索，默认 false）。 */
    val enableWebSearch: Boolean = false,
    /** 官方 oai_settings.tool_reasoning_mode（tool_reasoning_modes.DISABLED）。 */
    val toolReasoningMode: String = "disabled",
    /** 官方 oai_settings.tool_call_recurse_limit（默认 5）。 */
    val toolCallRecurseLimit: Int = 5,
    /** 官方 oai_settings.request_images（图片生成模型请求图像，默认 false）。 */
    val requestImages: Boolean = false,
    /** 官方 oai_settings.request_image_aspect_ratio。 */
    val requestImageAspectRatio: String = "",
    /** 官方 oai_settings.request_image_resolution。 */
    val requestImageResolution: String = "",
    /** 官方 oai_settings.max_context_unlocked（解锁上下文上限输入，默认 false）。 */
    val maxContextUnlocked: Boolean = false,
    /** 官方 settings.logit_bias（仅支持源发送）。 */
    val logitBias: Map<String, Double> = emptyMap(),
    /** 官方 settings.verbosity（gpt-5 系）。 */
    val verbosity: String? = null,
)

/** 官方 bias_presets 条目（id/text/value）。 */
@Serializable
data class BiasEntry(
    val id: String = "",
    val text: String,
    val value: Double,
)

/** 官方 openai.js default_bias_presets（bias 预设表默认值）。 */
val DEFAULT_BIAS_PRESETS: Map<String, List<BiasEntry>> = mapOf(
    "Default (none)" to emptyList(),
    "Anti-bond" to listOf(
        BiasEntry(id = "22154f79-dd98-41bc-8e34-87015d6a0eaf", text = " bond", value = -50.0),
        BiasEntry(id = "8ad2d5c4-d8ef-49e4-bc5e-13e7f4690e0f", text = " future", value = -50.0),
        BiasEntry(id = "52a4b280-0956-4940-ac52-4111f83e4046", text = " bonding", value = -50.0),
        BiasEntry(id = "e63037c7-c9d1-4724-ab2d-7756008b433b", text = " connection", value = -25.0),
    ),
)

private val OPENAI_REASONING_EFFORT_MODELS = setOf(
    "o1", "o3-mini", "o3-mini-2025-01-31", "o4-mini", "o4-mini-2025-04-16",
    "o3", "o3-2025-04-16", "gpt-5", "gpt-5-2025-08-07", "gpt-5-mini",
    "gpt-5-mini-2025-08-07", "gpt-5-nano", "gpt-5-nano-2025-08-07",
    "gpt-5.1", "gpt-5.1-2025-11-13", "gpt-5.1-chat-latest", "gpt-5.2",
    "gpt-5.2-2025-12-11", "gpt-5.2-chat-latest", "gpt-5.3-chat-latest",
    "gpt-5.4", "gpt-5.4-2026-03-05", "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17",
    "gpt-5.4-nano", "gpt-5.4-nano-2026-03-17", "gpt-5.5", "gpt-5.5-2026-04-23",
)

private val OPENAI_REASONING_EFFORT_MAP = mapOf("min" to "minimal")
private val OPENAI_FIXED_REASONING_EFFORT = mapOf("gpt-5.3-chat-latest" to "medium")
private val OPENAI_VERBOSITY_MODELS = Regex("^gpt-5")

/** 工具定义（官方 request.body.tools 的 function 结构）。 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = JsonObject(emptyMap()),
)

/**
 * 发送请求时的能力开关（工具/结构化输出/联网搜索/图像模态/安全设置）。
 * 对应官方 oai_settings 与后端 request.body 的相关字段，App 层按设置填充。
 */
@Serializable
data class ProviderRequestOptions(
    val tools: List<ToolDefinition> = emptyList(),
    val toolChoice: String? = null,
    val jsonSchema: JsonObject? = null,
    val enableWebSearch: Boolean = false,
    val requestImages: Boolean = false,
    val aspectRatio: String = "",
    val imageSize: String = "",
    val safetySettings: JsonArray = JsonArray(emptyList()),
    /** 官方 createGenerationParameters.stop / 后端 request.body.stop。 */
    val stopSequences: List<String> = emptyList(),
    /** 官方 oai_settings.custom_include_body / custom_exclude_body / custom_include_headers（YAML 字符串）。 */
    val customIncludeBody: String = "",
    val customExcludeBody: String = "",
    val customIncludeHeaders: String = "",
    /** Text Completion（textgen）专用：最终提示词（story string 组装由调用方完成）。 */
    val textGenPrompt: String? = null,
    /** Text Completion（textgen/novel）专用：官方 CFG Scale cfgValues（guidanceScale + textgen 负向提示）。 */
    val textGenCfgValues: JsonObject? = null,
    /** Text Completion（textgen）专用：textgenerationwebui_settings（默认见 TextgenSettingsDefaults）。 */
    val textGenSettings: JsonObject? = null,
    /** Text Completion 专用：生成类型语义（impersonate/continue 影响请求体分支）。 */
    val textGenIsImpersonate: Boolean = false,
    val textGenIsContinue: Boolean = false,
    val textGenType: String = "normal",
    /** NovelAI 专用：应用 novel 预设后的设置（字段见 NovelGenerationInput）。 */
    val novelSettings: JsonObject? = null,
    /** Kobold 专用：应用 kobold 预设后的设置（字段见 KoboldRequestBodyEngine）。 */
    val koboldSettings: JsonObject? = null,
) {
    val hasTools: Boolean get() = tools.isNotEmpty()

    /** 官方 OpenAI 风格 tools 数组（type=function）。 */
    fun openAiTools(): JsonArray = JsonArray(tools.map { t ->
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("function", buildJsonObject {
                put("name", JsonPrimitive(t.name))
                put("description", JsonPrimitive(t.description))
                put("parameters", t.parameters)
            })
        }
    })
}

/** OpenAI 兼容 Chat Completions 请求体构建。 */
object ChatRequestBuilder {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildOpenAiCompatible(
        model: String,
        messages: List<CompletionMessage>,
        params: SamplerParams = SamplerParams(),
        options: ProviderRequestOptions = ProviderRequestOptions(),
        source: String = "openai",
    ): String = buildOpenAiCompatibleFromChatML(model, messages.map { messageJson(it) }, params, options, source = source)

    /**
     * 直接吃官方 ChatML 消息（供 OpenRouter 等先做签名/缓存/媒体处理，再序列化）。
     * extra 额外请求体字段（如 OpenRouter 的 transforms/plugins/reasoning）。
     */
    fun buildOpenAiCompatibleFromChatML(
        model: String,
        messages: List<JsonObject>,
        params: SamplerParams = SamplerParams(),
        options: ProviderRequestOptions = ProviderRequestOptions(),
        extra: JsonObject? = null,
        source: String = "openai",
    ): String {
        // LlmClient 传真实 provider.id；Azure 归一为官方 azure_openai 常量
        val officialSource = when (source) {
            "azure" -> "azure_openai"
            else -> source
        }
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", params.maxTokens)
            put("presence_penalty", params.presencePenalty)
            put("frequency_penalty", params.frequencyPenalty)
            put("stream", params.stream)
            // 官方后端 requestBody 恒带 stop（generate_data.stop，空数组也发；text completion 走独立 builder）
            put("stop", JsonArray(options.stopSequences.map { JsonPrimitive(it) }))
            if (params.logitBias.isNotEmpty() && officialSource in setOf("openai", "azure_openai", "openrouter", "electronhub", "chutes", "custom")) {
                put("logit_bias", buildJsonObject {
                    params.logitBias.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            if (officialSource in setOf("openai", "azure_openai", "openrouter", "mistralai", "custom", "cohere", "groq", "electronhub", "nanogpt", "xai", "pollinations", "aimlapi", "vertexai", "makersuite", "chutes") && params.seed >= 0) {
                put("seed", JsonPrimitive(params.seed))
            }
            if (params.n > 1 && officialSource in setOf("openai", "azure_openai", "custom", "xai", "aimlapi", "moonshot")) {
                put("n", JsonPrimitive(params.n))
            }
            if (params.reasoningEffort.isNotBlank() && officialSource in setOf("custom", "openai") && model in OPENAI_REASONING_EFFORT_MODELS) {
                put("reasoning_effort", JsonPrimitive(OPENAI_FIXED_REASONING_EFFORT[model] ?: OPENAI_REASONING_EFFORT_MAP[params.reasoningEffort] ?: params.reasoningEffort))
            }
            if (!params.verbosity.isNullOrBlank() && officialSource in setOf("custom", "openai") && OPENAI_VERBOSITY_MODELS.containsMatchIn(model)) {
                put("verbosity", JsonPrimitive(params.verbosity))
            }
            if (options.hasTools) {
                put("tools", options.openAiTools())
                options.toolChoice?.let { put("tool_choice", JsonPrimitive(it)) }
            }
            options.jsonSchema?.let { schema ->
                // 官方后端 setJsonObjectFormat 源（moonshot/zai/siliconflow）走 json_object；
                // 其余源走 json_schema 结构。
                if (officialSource in setOf("moonshot", "zai", "siliconflow")) {
                    put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
                } else {
                    put("response_format", buildJsonObject {
                        put("type", JsonPrimitive("json_schema"))
                        put("json_schema", buildJsonObject {
                            put("name", JsonPrimitive(schema["name"]?.let { it.toString().trim('"') } ?: "json"))
                            put("strict", JsonPrimitive(schema["strict"]?.jsonPrimitive?.content?.toBoolean() ?: true))
                            put("schema", schema["value"] ?: schema)
                        })
                    })
                }
            }
            if (extra != null) extra.forEach { (k, v) -> put(k, v) }
        }
        return applyGpt5Params(
            applyO1Params(applySourceParams(body, officialSource, model, params), model, params, officialSource, messages),
            model,
            params,
            officialSource,
        ).toString()
    }

    /**
     * 对齐官方 createGenerationParameters / chat-completions.js 的 o1/o3/o4 分支：
     * max_completion_tokens 替代 max_tokens，删掉不支持参数；纯 o1 把 system 消息改 user，删 n/tools/tool_choice。
     */
    private fun applyO1Params(
        body: JsonObject,
        model: String,
        params: SamplerParams,
        source: String,
        messages: List<JsonObject>,
    ): JsonObject {
        val isO1Family = (source in setOf("openai", "azure_openai") && Regex("^(o1|o3|o4)").containsMatchIn(model)) ||
            (source == "openrouter" && Regex("^openai/(o1|o3|o4)").containsMatchIn(model))
        if (!isO1Family) return body
        val map = body.toMutableMap()
        map["max_completion_tokens"] = map.remove("max_tokens") ?: JsonNull
        map.remove("logprobs")
        map.remove("top_logprobs")
        map.remove("stop")
        map.remove("logit_bias")
        map.remove("temperature")
        map.remove("top_p")
        map.remove("frequency_penalty")
        map.remove("presence_penalty")
        if (Regex("^(openai/)?(o1)").containsMatchIn(model)) {
            val newMessages = messages.map { el ->
                if (el["role"]?.jsonPrimitive?.content == "system") {
                    el.toMutableMap().apply { put("role", JsonPrimitive("user")) }.let { JsonObject(it) }
                } else {
                    el
                }
            }
            map["messages"] = JsonArray(newMessages)
            map.remove("n")
            map.remove("tools")
            map.remove("tool_choice")
        }
        return JsonObject(map)
    }

    /**
     * 对齐官方 openai.js createGenerationParameters 各厂商采样参数分支
     * （openrouter/nanogpt/workers_ai/perplexity/electronhub/chutes/zai/minimax/moonshot/deepseek）。
     */
    private fun applySourceParams(
        body: JsonObject,
        source: String,
        model: String,
        params: SamplerParams,
    ): JsonObject {
        val map = body.toMutableMap()
        when (source) {
            "openrouter", "nanogpt" -> {
                map["top_k"] = JsonPrimitive(params.topK)
                map["min_p"] = JsonPrimitive(params.minP)
                map["repetition_penalty"] = JsonPrimitive(params.repetitionPenalty)
                map["top_a"] = JsonPrimitive(params.topA)
                if (source == "openrouter") {
                    if (!params.verbosity.isNullOrBlank()) map["verbosity"] = JsonPrimitive(params.verbosity)
                    if (params.useFallback) map["route"] = JsonPrimitive("fallback")
                    if (params.openRouterProviders.isNotEmpty() || params.openRouterQuantizations.isNotEmpty()) {
                        map["provider"] = buildJsonObject {
                            if (params.openRouterProviders.isNotEmpty()) {
                                put("order", JsonArray(params.openRouterProviders.map { JsonPrimitive(it) }))
                            }
                            put("allow_fallbacks", JsonPrimitive(params.allowFallbacks))
                            if (params.openRouterQuantizations.isNotEmpty()) {
                                put("quantizations", JsonArray(params.openRouterQuantizations.map { JsonPrimitive(it) }))
                            }
                        }
                    }
                }
            }
            "workers_ai" -> {
                if (params.topK > 0) map["top_k"] = JsonPrimitive(minOf(params.topK, 50)) else map.remove("top_k")
                map["repetition_penalty"] = JsonPrimitive(params.repetitionPenalty)
                if (params.seed >= 1) map["seed"] = JsonPrimitive(params.seed) else map.remove("seed")
                map["top_p"] = JsonPrimitive(maxOf(params.topP, 0.001))
                map.remove("n")
                map.remove("logit_bias")
            }
            "perplexity" -> {
                map["top_k"] = JsonPrimitive(params.topK)
                map["reasoning_effort"] = JsonPrimitive(params.reasoningEffort.ifBlank { "auto" })
                map.remove("stop")
            }
            "electronhub" -> map["top_k"] = JsonPrimitive(params.topK)
            "chutes" -> {
                map["min_p"] = JsonPrimitive(params.minP)
                if (params.topK > 0) map["top_k"] = JsonPrimitive(params.topK) else map.remove("top_k")
                map["repetition_penalty"] = JsonPrimitive(params.repetitionPenalty)
            }
            "zai" -> {
                if ((map["top_p"] as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0) {
                    map["top_p"] = JsonPrimitive(0.01)
                }
                val stops = (map["stop"] as? JsonArray)?.toList().orEmpty().take(1)
                map["stop"] = JsonArray(stops)
                map.remove("presence_penalty")
                map.remove("frequency_penalty")
                map["thinking"] = buildJsonObject {
                    put("type", JsonPrimitive(if (params.includeReasoning) "enabled" else "disabled"))
                }
            }
            "minimax" -> {
                val t = params.temperature
                if (t.isFinite()) map["temperature"] = JsonPrimitive(t.coerceIn(Math.ulp(1.0), 1.0))
            }
            "moonshot" -> {
                map["thinking"] = buildJsonObject {
                    put("type", JsonPrimitive(if (params.includeReasoning) "enabled" else "disabled"))
                }
                if (Regex("kimi-k2.5").containsMatchIn(model)) {
                    map.remove("temperature")
                    map.remove("top_p")
                    map.remove("frequency_penalty")
                    map.remove("presence_penalty")
                }
            }
            "deepseek" -> {
                if ((map["top_p"] as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0) {
                    map["top_p"] = JsonPrimitive(Math.ulp(1.0))
                }
            }
            "openai", "azure_openai", "custom" -> {
                // 官方后端 openai/custom 分支：logprobs>0 → top_logprobs=logprobs, logprobs=true
                if (params.requestTokenProbabilities) {
                    map["logprobs"] = JsonPrimitive(true)
                    map["top_logprobs"] = JsonPrimitive(5)
                }
            }
        }
        return JsonObject(map)
    }

    /**
     * 对齐官方 openai.js createGenerationParameters 的 gpt-5 分支
     * （gptSources = openai / azure_openai / openrouter）：
     * max_tokens → max_completion_tokens，并删掉 gpt-5 不支持的采样参数。
     */
    private fun applyGpt5Params(
        body: JsonObject,
        model: String,
        params: SamplerParams,
        source: String,
    ): JsonObject {
        val gptSources = setOf("openai", "azure_openai", "openrouter")
        if (source !in gptSources || !Regex("gpt-5").containsMatchIn(model)) return body
        val map = body.toMutableMap()
        map["max_completion_tokens"] = map.remove("max_tokens") ?: JsonNull
        map.remove("logprobs")
        map.remove("top_logprobs")
        if (Regex("gpt-5-chat-latest").containsMatchIn(model)) {
            map.remove("tools")
            map.remove("tool_choice")
        } else if (
            Regex("gpt-5\\.(1|2|3|4)").containsMatchIn(model) &&
            !Regex("chat-latest").containsMatchIn(model) &&
            params.reasoningEffort.isBlank()
        ) {
            map.remove("frequency_penalty")
            map.remove("presence_penalty")
            map.remove("logit_bias")
            map.remove("stop")
        } else {
            map.remove("temperature")
            map.remove("top_p")
            map.remove("frequency_penalty")
            map.remove("presence_penalty")
            map.remove("logit_bias")
            map.remove("stop")
        }
        return JsonObject(map)
    }

    private fun messageJson(message: CompletionMessage): JsonObject = buildJsonObject {
        put("role", message.role)
        val hasMedia = message.media?.isNotEmpty() == true
        put(
            "content",
            if (hasMedia) MediaInliner.inlineOpenAi(JsonPrimitive(message.content), message.media.orEmpty())
            else JsonPrimitive(message.content),
        )
        message.name?.let { put("name", it) }
        message.toolCallId?.let { put("tool_call_id", it) }
        message.toolCalls?.let { calls ->
            put("tool_calls", JsonArray(calls.map { call ->
                buildJsonObject {
                    put("id", call.id)
                    put("type", call.type)
                    put("function", buildJsonObject {
                        put("name", call.name)
                        put("arguments", call.arguments)
                    })
                    // 对齐官方 serializeMessages：tool_calls 条目透传 signature
                    call.signature?.let { put("signature", it) }
                }
            }))
        }
        // 对齐官方 serializeMessages：message.signature / message.reasoning 透传（推理链/OpenRouter/Custom）
        message.signature?.let { put("signature", it) }
        message.reasoning?.let { put("reasoning", it) }
    }
}
