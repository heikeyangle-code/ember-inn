package com.emberinn.engine.prompt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 预设应用全链纯逻辑（对照官方 release 8172dcd）。
 *
 * JSON 级函数逐字移植官方：
 * - preset-manager.js：isPossibly* / performMasterImport legacy 识别 / getPresetSettings 过滤；
 * - power-user.js：contextControls 应用循环 + getContextSettings + autoFixStoryString；
 * - instruct-mode.js：controls 应用循环 + migrateInstructModeSettings；
 * - sysprompt.js / reasoning.js：select change 的纯字段部分；
 * - openai.js：settingsToUpdate + onSettingsPresetChange 纯循环 + migrateChatCompletionSettings + getChatCompletionPreset。
 * 打桩登记：DOM/save/事件剥除；Fuse 模糊匹配未移植（App 子串近似，HANDOFF 8.6 登记）。
 */
/** 官方 power_user.sysprompt（enabled 默认 true；默认 "Neutral - Chat"）。 */
@Serializable
data class SyspromptSettings(
    val enabled: Boolean = true,
    val name: String = "Neutral - Chat",
    val content: String = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.",
    val postHistory: String = "",
)

object PresetApplyEngine {

    // ------------------------------------------------------------------
    // 类型识别（preset-manager.js:209-233 逐字）
    // ------------------------------------------------------------------

    private fun hasKeys(data: JsonElement?, keys: List<String>): Boolean? {
        if (data == null || data is JsonNull) return null
        if (data !is JsonObject) return false
        return keys.all { data.containsKey(it) }
    }

    fun isPossiblyInstructData(data: JsonElement?): Boolean? =
        hasKeys(data, listOf("name", "input_sequence", "output_sequence"))

    fun isPossiblyContextData(data: JsonElement?): Boolean? =
        hasKeys(data, listOf("name", "story_string"))

    fun isPossiblySystemPromptData(data: JsonElement?): Boolean? =
        hasKeys(data, listOf("name", "content"))

    fun isPossiblyTextCompletionData(data: JsonElement?): Boolean? =
        hasKeys(data, listOf("temp", "top_k", "top_p", "rep_pen"))

    fun isPossiblyReasoningData(data: JsonElement?): Boolean? =
        hasKeys(data, listOf("name", "prefix", "suffix", "separator"))

    fun isPossiblyStartReplyWithData(data: JsonElement?): Boolean? {
        if (data == null || data is JsonNull) return null
        if (data !is JsonObject) return false
        return data.containsKey("value") && data.containsKey("show")
    }

    /** 官方 performMasterImport 的 legacy 单文件识别顺序：instruct → context → sysprompt → preset → reasoning。 */
    fun detectLegacyImportType(data: JsonElement?): String? {
        if (data == null || data !is JsonObject) return null
        if (isPossiblyInstructData(data) == true) return "instruct"
        if (isPossiblyContextData(data) == true) return "context"
        if (isPossiblySystemPromptData(data) == true) return "sysprompt"
        if (isPossiblyTextCompletionData(data) == true) return "preset"
        if (isPossiblyReasoningData(data) == true) return "reasoning"
        return null
    }

    /** 官方 masterSections：key → isValid（srw 无 legacy 单文件识别，只出现在多区段）。 */
    fun masterSectionsValid(data: JsonObject): JsonObject = buildJsonObject {
        fun putValid(key: String, valid: Boolean?) {
            if (!data.containsKey(key)) return
            put(key, when (valid) {
                null -> JsonNull
                true -> JsonPrimitive(true)
                else -> JsonPrimitive(false)
            })
        }
        putValid("instruct", isPossiblyInstructData(data["instruct"]))
        putValid("context", isPossiblyContextData(data["context"]))
        putValid("sysprompt", isPossiblySystemPromptData(data["sysprompt"]))
        putValid("preset", isPossiblyTextCompletionData(data["preset"]))
        putValid("reasoning", isPossiblyReasoningData(data["reasoning"]))
        putValid("srw", isPossiblyStartReplyWithData(data["srw"]))
    }

    // ------------------------------------------------------------------
    // JS 语义小工具
    // ------------------------------------------------------------------

    /** JS truthy：null/""/false/0 → false；其余 true（字符串 "false" 为 truthy）。 */
    internal fun jsTruthy(el: JsonElement?): Boolean = when (el) {
        null, JsonNull -> false
        is JsonObject, is JsonArray -> true
        is JsonPrimitive -> when {
            el.isString -> el.content.isNotEmpty()
            el.content == "true" -> true
            el.content == "false" -> false
            else -> el.content.toDoubleOrNull()?.let { it != 0.0 } ?: true
        }
    }

    /** JS `x || ''`：非空字符串原样，其余空串。 */
    internal fun orEmptyString(el: JsonElement?): String =
        (el as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content ?: ""

    // ------------------------------------------------------------------
    // context（power-user.js:354-369 contextControls + 2037-2074 + autoFixStoryString）
    // ------------------------------------------------------------------

    internal data class ContextControl(
        val property: String,
        val isGlobal: Boolean,
        val isCheckbox: Boolean,
        val defaultValue: JsonPrimitive?,
    )

    internal val contextControls = listOf(
        ContextControl("story_string", false, false, null),
        ContextControl("example_separator", false, false, null),
        ContextControl("chat_start", false, false, null),
        ContextControl("use_stop_strings", false, true, JsonPrimitive(false)),
        ContextControl("names_as_stop_strings", false, true, JsonPrimitive(true)),
        ContextControl("story_string_position", false, false, JsonPrimitive(0)),
        ContextControl("story_string_depth", false, false, JsonPrimitive(1)),
        ContextControl("story_string_role", false, false, JsonPrimitive(0)),
        ContextControl("always_force_name2", true, true, JsonPrimitive(true)),
        ContextControl("trim_sentences", true, true, JsonPrimitive(false)),
        ContextControl("single_line", true, true, JsonPrimitive(false)),
    )

    /** 官方 loadContextSettings.autoFixStoryString（逐字；null 原样返回）。 */
    fun autoFixStoryString(context: JsonObject?): JsonObject? {
        if (context == null) return null
        if (context.containsKey("story_string_position")) return context
        var storyString = orEmptyString(context["story_string"])

        fun autoFixMissingField(field: String, position: String) {
            if (storyString.contains("{{$field}}")) return
            val fieldTemplate = "{{#if $field}}{{$field}}\n{{/if}}"
            val firstCurly = storyString.indexOf("{{").let { if (it == -1) 0 else it }
            val lastCurly = storyString.lastIndexOf("}}").let { if (it == -1) storyString.length else it + 2 }
            val lastTrim = storyString.lastIndexOf("{{trim}}").let { if (it == -1) storyString.length else it }
            val endPosition = minOf(lastTrim, lastCurly)
            storyString = if (position == "start") {
                storyString.substring(0, firstCurly) + fieldTemplate + storyString.substring(firstCurly)
            } else {
                storyString.substring(0, endPosition) + fieldTemplate + storyString.substring(endPosition)
            }
        }
        autoFixMissingField("anchorBefore", "start")
        autoFixMissingField("anchorAfter", "end")
        return JsonObject(context.toMutableMap().apply { put("story_string", JsonPrimitive(storyString)) })
    }

    /** 官方 context_presets change 的纯字段部分：context 与全局字段分开落。 */
    fun applyContextPresetJson(powerUser: JsonObject, preset: JsonObject): JsonObject {
        val fixed = autoFixStoryString(preset) ?: preset
        val context = powerUser["context"]?.jsonObject ?: JsonObject(emptyMap())
        val contextOut = context.toMutableMap()
        val globalOut = powerUser.toMutableMap()
        if (fixed.containsKey("name")) {
            contextOut["preset"] = fixed["name"] ?: JsonNull
        } else {
            contextOut.remove("preset")
        }

        for (control in contextControls) {
            val presetValue = fixed[control.property]
                ?.takeUnless { it is JsonNull }
                ?: control.defaultValue
            if (presetValue != null) {
                if (control.isGlobal) globalOut[control.property] = presetValue
                else contextOut[control.property] = presetValue
            }
        }
        globalOut["context"] = JsonObject(contextOut)
        return JsonObject(globalOut)
    }

    /** 官方 getContextSettings（编译当前 context + 全局字段，checkbox 强制布尔）。 */
    fun getContextSettingsCompiled(powerUser: JsonObject): JsonObject = buildJsonObject {
        val context = powerUser["context"]?.jsonObject ?: JsonObject(emptyMap())
        for (control in contextControls) {
            val value = if (control.isGlobal) powerUser[control.property] else context[control.property]
            if (control.isCheckbox) {
                put(control.property, JsonPrimitive(jsTruthy(value)))
            } else if (value != null) {
                put(control.property, value)
            }
        }
    }

    // ------------------------------------------------------------------
    // instruct（instruct-mode.js:23-53 controls + 55-105 migrate + 826-849 apply）
    // ------------------------------------------------------------------

    internal val instructControls = listOf(
        "enabled", "wrap", "macro", "story_string_prefix", "story_string_suffix", "input_sequence",
        "input_suffix", "output_sequence", "output_suffix", "system_sequence", "system_suffix",
        "last_system_sequence", "user_alignment_message", "stop_sequence", "first_output_sequence",
        "last_output_sequence", "first_input_sequence", "last_input_sequence", "activation_regex",
        "bind_to_context", "skip_examples", "names_behavior", "system_same_as_user",
        "sequences_as_stop_strings",
    )

    /** 官方 migrateInstructModeSettings（逐字；JSON 级原地迁移）。 */
    fun migrateInstructModeSettings(settings: JsonObject): JsonObject {
        val out = settings.toMutableMap()
        if (out.containsKey("separator_sequence")) {
            out["output_suffix"] = JsonPrimitive(orEmptyString(out["separator_sequence"]))
            out.remove("separator_sequence")
        }
        if (out.containsKey("names")) {
            out["names_behavior"] = JsonPrimitive(
                if (jsTruthy(out["names"])) "always"
                else if (jsTruthy(out["names_force_groups"])) "force"
                else "none",
            )
            out.remove("names")
            out.remove("names_force_groups")
        }
        val defaults = mapOf(
            "input_suffix" to "", "system_sequence" to "", "system_suffix" to "",
            "user_alignment_message" to "", "last_system_sequence" to "", "first_input_sequence" to "",
            "last_input_sequence" to "", "skip_examples" to false, "system_same_as_user" to false,
            "names_behavior" to "force", "sequences_as_stop_strings" to true,
            "story_string_prefix" to "", "story_string_suffix" to "",
        )
        for ((key, value) in defaults) {
            if (!out.containsKey(key)) {
                out[key] = if (value is Boolean) JsonPrimitive(value) else JsonPrimitive(value as String)
            }
        }
        for (field in listOf("names", "names_force_groups", "system_sequence_prefix", "system_sequence_suffix")) {
            out.remove(field)
        }
        return JsonObject(out)
    }

    /** 官方 instruct_presets change 的纯字段部分。 */
    fun applyInstructPresetJson(powerUser: JsonObject, preset: JsonObject): JsonObject {
        val migrated = migrateInstructModeSettings(preset)
        val instruct = powerUser["instruct"]?.jsonObject ?: JsonObject(emptyMap())
        val out = instruct.toMutableMap()
        // 官方 String(preset.name)：undefined → "undefined"、null → "null"、数字转字符串
        out["preset"] = when (val nameValue = migrated["name"]) {
            null -> JsonPrimitive("undefined")
            is JsonNull -> JsonPrimitive("null")
            is JsonPrimitive -> JsonPrimitive(nameValue.content)
            else -> JsonPrimitive("undefined")
        }
        for (property in instructControls) {
            if (migrated.containsKey(property)) {
                out[property] = migrated[property] ?: JsonNull
            }
        }
        return JsonObject(powerUser.toMutableMap().apply { put("instruct", JsonObject(out)) })
    }

    // ------------------------------------------------------------------
    // sysprompt / reasoning（select change 纯字段部分）
    // ------------------------------------------------------------------

    /** 官方 sysprompt $select.on('change')：enabled 自动置 true + name/content/post_history。 */
    fun applySyspromptPresetJson(powerUser: JsonObject, preset: JsonObject): JsonObject {
        val sys = powerUser["sysprompt"]?.jsonObject ?: JsonObject(emptyMap())
        val out = sys.toMutableMap()
        out["enabled"] = JsonPrimitive(true)
        if (preset.containsKey("name")) out["name"] = preset["name"] ?: JsonNull else out.remove("name")
        out["content"] = JsonPrimitive(orEmptyString(preset["content"]))
        out["post_history"] = JsonPrimitive(orEmptyString(preset["post_history"]))
        return JsonObject(powerUser.toMutableMap().apply { put("sysprompt", JsonObject(out)) })
    }

    /** 官方 reasoning $select.on('change') 纯字段部分。 */
    fun applyReasoningPresetJson(powerUser: JsonObject, template: JsonObject): JsonObject {
        val reasoning = powerUser["reasoning"]?.jsonObject ?: JsonObject(emptyMap())
        val out = reasoning.toMutableMap()
        if (template.containsKey("name")) out["name"] = template["name"] ?: JsonNull else out.remove("name")
        for (key in listOf("prefix", "suffix", "separator")) {
            if (template.containsKey(key)) out[key] = template[key] ?: JsonNull else out.remove(key)
        }
        return JsonObject(powerUser.toMutableMap().apply { put("reasoning", JsonObject(out)) })
    }

    // ------------------------------------------------------------------
    // openai chat completion（settingsToUpdate + onSettingsPresetChange 纯循环）
    // ------------------------------------------------------------------

    internal data class ChatCompletionKey(val presetKey: String, val settingKey: String, val isConnection: Boolean)

    internal val settingsToUpdate: List<ChatCompletionKey> = listOf(
        ChatCompletionKey("chat_completion_source", "chat_completion_source", true),
        ChatCompletionKey("temperature", "temp_openai", false),
        ChatCompletionKey("frequency_penalty", "freq_pen_openai", false),
        ChatCompletionKey("presence_penalty", "pres_pen_openai", false),
        ChatCompletionKey("top_p", "top_p_openai", false),
        ChatCompletionKey("top_k", "top_k_openai", false),
        ChatCompletionKey("top_a", "top_a_openai", false),
        ChatCompletionKey("min_p", "min_p_openai", false),
        ChatCompletionKey("repetition_penalty", "repetition_penalty_openai", false),
        ChatCompletionKey("max_context_unlocked", "max_context_unlocked", false),
        ChatCompletionKey("group_models", "group_models", true),
        ChatCompletionKey("sort_models", "sort_models", true),
        ChatCompletionKey("openai_model", "openai_model", true),
        ChatCompletionKey("claude_model", "claude_model", true),
        ChatCompletionKey("openrouter_model", "openrouter_model", true),
        ChatCompletionKey("openrouter_use_fallback", "openrouter_use_fallback", true),
        ChatCompletionKey("openrouter_providers", "openrouter_providers", true),
        ChatCompletionKey("openrouter_quantizations", "openrouter_quantizations", true),
        ChatCompletionKey("openrouter_allow_fallbacks", "openrouter_allow_fallbacks", true),
        ChatCompletionKey("openrouter_middleout", "openrouter_middleout", true),
        ChatCompletionKey("tool_reasoning_mode", "tool_reasoning_mode", false),
        ChatCompletionKey("ai21_model", "ai21_model", true),
        ChatCompletionKey("mistralai_model", "mistralai_model", true),
        ChatCompletionKey("cohere_model", "cohere_model", true),
        ChatCompletionKey("perplexity_model", "perplexity_model", true),
        ChatCompletionKey("groq_model", "groq_model", true),
        ChatCompletionKey("chutes_model", "chutes_model", true),
        ChatCompletionKey("siliconflow_model", "siliconflow_model", true),
        ChatCompletionKey("siliconflow_endpoint", "siliconflow_endpoint", true),
        ChatCompletionKey("minimax_model", "minimax_model", true),
        ChatCompletionKey("minimax_endpoint", "minimax_endpoint", true),
        ChatCompletionKey("electronhub_model", "electronhub_model", true),
        ChatCompletionKey("nanogpt_model", "nanogpt_model", true),
        ChatCompletionKey("nanogpt_provider", "nanogpt_provider", true),
        ChatCompletionKey("nanogpt_payg_override", "nanogpt_payg_override", true),
        ChatCompletionKey("deepseek_model", "deepseek_model", true),
        ChatCompletionKey("aimlapi_model", "aimlapi_model", true),
        ChatCompletionKey("xai_model", "xai_model", true),
        ChatCompletionKey("pollinations_model", "pollinations_model", true),
        ChatCompletionKey("moonshot_model", "moonshot_model", true),
        ChatCompletionKey("fireworks_model", "fireworks_model", true),
        ChatCompletionKey("cometapi_model", "cometapi_model", true),
        ChatCompletionKey("custom_model", "custom_model", true),
        ChatCompletionKey("custom_url", "custom_url", true),
        ChatCompletionKey("custom_include_body", "custom_include_body", true),
        ChatCompletionKey("custom_exclude_body", "custom_exclude_body", true),
        ChatCompletionKey("custom_include_headers", "custom_include_headers", true),
        ChatCompletionKey("custom_prompt_post_processing", "custom_prompt_post_processing", true),
        ChatCompletionKey("google_model", "google_model", true),
        ChatCompletionKey("vertexai_model", "vertexai_model", true),
        ChatCompletionKey("zai_model", "zai_model", true),
        ChatCompletionKey("zai_endpoint", "zai_endpoint", true),
        ChatCompletionKey("workers_ai_model", "workers_ai_model", true),
        ChatCompletionKey("workers_ai_account_id", "workers_ai_account_id", true),
        ChatCompletionKey("openai_max_context", "openai_max_context", false),
        ChatCompletionKey("openai_max_tokens", "openai_max_tokens", false),
        ChatCompletionKey("names_behavior", "names_behavior", false),
        ChatCompletionKey("send_if_empty", "send_if_empty", false),
        ChatCompletionKey("impersonation_prompt", "impersonation_prompt", false),
        ChatCompletionKey("new_chat_prompt", "new_chat_prompt", false),
        ChatCompletionKey("new_group_chat_prompt", "new_group_chat_prompt", false),
        ChatCompletionKey("new_example_chat_prompt", "new_example_chat_prompt", false),
        ChatCompletionKey("continue_nudge_prompt", "continue_nudge_prompt", false),
        ChatCompletionKey("bias_preset_selected", "bias_preset_selected", false),
        ChatCompletionKey("reverse_proxy", "reverse_proxy", true),
        ChatCompletionKey("wi_format", "wi_format", false),
        ChatCompletionKey("scenario_format", "scenario_format", false),
        ChatCompletionKey("personality_format", "personality_format", false),
        ChatCompletionKey("group_nudge_prompt", "group_nudge_prompt", false),
        ChatCompletionKey("stream_openai", "stream_openai", false),
        ChatCompletionKey("prompts", "prompts", false),
        ChatCompletionKey("prompt_order", "prompt_order", false),
        ChatCompletionKey("show_external_models", "show_external_models", true),
        ChatCompletionKey("proxy_password", "proxy_password", true),
        ChatCompletionKey("assistant_prefill", "assistant_prefill", false),
        ChatCompletionKey("assistant_impersonation", "assistant_impersonation", false),
        ChatCompletionKey("use_sysprompt", "use_sysprompt", false),
        ChatCompletionKey("vertexai_auth_mode", "vertexai_auth_mode", true),
        ChatCompletionKey("vertexai_region", "vertexai_region", true),
        ChatCompletionKey("vertexai_express_project_id", "vertexai_express_project_id", true),
        ChatCompletionKey("squash_system_messages", "squash_system_messages", false),
        ChatCompletionKey("media_inlining", "media_inlining", false),
        ChatCompletionKey("inline_image_quality", "inline_image_quality", false),
        ChatCompletionKey("continue_prefill", "continue_prefill", false),
        ChatCompletionKey("continue_postfix", "continue_postfix", false),
        ChatCompletionKey("function_calling", "function_calling", false),
        ChatCompletionKey("tool_call_recurse_limit", "tool_call_recurse_limit", false),
        ChatCompletionKey("show_thoughts", "show_thoughts", false),
        ChatCompletionKey("reasoning_effort", "reasoning_effort", false),
        ChatCompletionKey("verbosity", "verbosity", false),
        ChatCompletionKey("enable_web_search", "enable_web_search", false),
        ChatCompletionKey("seed", "seed", false),
        ChatCompletionKey("n", "n", false),
        ChatCompletionKey("bypass_status_check", "bypass_status_check", true),
        ChatCompletionKey("request_images", "request_images", false),
        ChatCompletionKey("request_image_aspect_ratio", "request_image_aspect_ratio", false),
        ChatCompletionKey("request_image_resolution", "request_image_resolution", false),
        ChatCompletionKey("azure_base_url", "azure_base_url", true),
        ChatCompletionKey("azure_deployment_name", "azure_deployment_name", true),
        ChatCompletionKey("azure_api_version", "azure_api_version", true),
        ChatCompletionKey("azure_openai_model", "azure_openai_model", true),
        ChatCompletionKey("extensions", "extensions", false),
    )

    /** 官方 migrateChatCompletionSettings（逐字；含正则迁移与同键替换）。 */
    fun migrateChatCompletionSettings(settings: JsonObject): JsonObject {
        data class Migration(
            val oldKey: String,
            val oldValue: Any?,
            val newKey: String,
            val newValue: Any?,
            val appendOld: Boolean = false,
        )

        val migrateMap = listOf(
            Migration("names_in_completion", true, "names_behavior", "completion"),
            Migration("chat_completion_source", "palm", "chat_completion_source", "makersuite"),
            Migration("custom_prompt_post_processing", "claude", "custom_prompt_post_processing", "merge"),
            Migration("ai21_model", Regex("^j2-"), "ai21_model", "jamba-large"),
            Migration("image_inlining", false, "media_inlining", false),
            Migration("image_inlining", true, "media_inlining", true),
            Migration("video_inlining", true, "media_inlining", true),
            Migration("audio_inlining", true, "media_inlining", true),
            Migration("claude_use_sysprompt", true, "use_sysprompt", true),
            Migration("use_makersuite_sysprompt", true, "use_sysprompt", true),
            Migration("mistralai_model", Regex("^(mistral-medium|mistral-small)$"), "mistralai_model", null, appendOld = true),
            Migration("deepseek_model", Regex("^deepseek-(chat|reasoner|coder)$"), "deepseek_model", "deepseek-v4-flash"),
            Migration("openrouter_sort_models", "alphabetically", "sort_models", "alphabetically"),
            Migration("openrouter_sort_models", "pricing.prompt", "sort_models", "pricing.prompt"),
            Migration("openrouter_sort_models", "context_length", "sort_models", "context_length"),
            Migration("openrouter_group_models", true, "group_models", true),
        )

        val out = settings.toMutableMap()
        for (migration in migrateMap) {
            if (!out.containsKey(migration.oldKey)) continue
            val old = out[migration.oldKey] ?: JsonNull
            val shouldMigrate = when (val ov = migration.oldValue) {
                is Regex -> ov.containsMatchIn(orEmptyString(old))
                else -> jsEquality(old, ov)
            }
            if (shouldMigrate) {
                val newValue = when {
                    migration.appendOld -> JsonPrimitive(orEmptyString(old) + "-latest")
                    migration.newValue == null -> JsonNull
                    migration.newValue is Boolean -> JsonPrimitive(migration.newValue)
                    else -> JsonPrimitive(migration.newValue.toString())
                }
                out[migration.newKey] = newValue
            }
            if (migration.oldKey != migration.newKey) {
                out.remove(migration.oldKey)
            }
        }
        return JsonObject(out)
    }

    private fun jsEquality(el: JsonElement, expected: Any?): Boolean = when (expected) {
        null -> el is JsonNull
        is Boolean -> (el as? JsonPrimitive)?.content == expected.toString()
        is String -> (el as? JsonPrimitive)?.content == expected
        else -> false
    }

    /** 官方 onSettingsPresetChange 纯循环（isConnection 未绑定跳过；extensions 特例；undefined 不覆盖）。 */
    fun applyChatCompletionPresetJson(settings: JsonObject, preset: JsonObject, bindPresetToConnection: Boolean): JsonObject {
        val out = settings.toMutableMap()
        for (key in settingsToUpdate) {
            if (key.isConnection && !bindPresetToConnection) continue
            if (key.presetKey == "extensions") {
                out["extensions"] = preset["extensions"] as? JsonObject ?: JsonObject(emptyMap())
                continue
            }
            if (preset.containsKey(key.presetKey)) {
                out[key.settingKey] = preset[key.presetKey] ?: JsonNull
            }
        }
        return JsonObject(out)
    }

    /** 官方 getChatCompletionPreset（当前设置 → 预设文件体）。 */
    fun getChatCompletionPresetBody(settings: JsonObject): JsonObject = buildJsonObject {
        for (key in settingsToUpdate) {
            if (settings.containsKey(key.settingKey)) {
                put(key.presetKey, settings[key.settingKey] ?: JsonNull)
            }
        }
    }

    // ------------------------------------------------------------------
    // 保存为预设（preset-manager.js getPresetSettings 的过滤语义）
    // ------------------------------------------------------------------

    private val filteredPresetKeys = setOf(
        "api_server", "preset", "streaming", "truncation_length", "n", "streaming_url", "stopping_strings",
        "can_use_tokenization", "can_use_streaming", "preset_settings_novel", "preset_settings",
        "streaming_novel", "nai_preamble", "model_novel", "streaming_kobold", "enabled", "bind_to_context",
        "seed", "legacy_api", "mancer_model", "togetherai_model", "ollama_model", "vllm_model",
        "aphrodite_model", "llamacpp_model", "server_urls", "type", "custom_model", "bypass_status_check",
        "infermaticai_model", "dreamgen_model", "openrouter_model", "featherless_model",
        "max_tokens_second", "openrouter_providers", "openrouter_quantizations",
        "openrouter_allow_fallbacks", "tabby_model", "derived", "generic_model", "include_reasoning",
        "global_banned_tokens", "send_banned_tokens", "auto_parse", "add_to_prompts", "auto_expand",
        "show_hidden", "max_additions",
    )

    fun filterPresetSettings(
        settings: JsonObject,
        apiId: String,
        name: String,
        currentName: String,
        isAdvancedFormatting: Boolean,
        genAmount: Int,
        maxLength: Int,
    ): JsonObject {
        val out = settings.toMutableMap()
        for (key in filteredPresetKeys) out.remove(key)
        if (isAdvancedFormatting) {
            out["name"] = JsonPrimitive(name.ifEmpty { currentName })
        } else if (apiId != "openai") {
            out["genamt"] = JsonPrimitive(genAmount)
            out["max_length"] = JsonPrimitive(maxLength)
        }
        return JsonObject(out)
    }

    // ------------------------------------------------------------------
    // 名称匹配与同名模板绑定（preset-manager.js /preset exact；instruct bind_to_context）
    // ------------------------------------------------------------------

    fun matchPresetNameExact(names: List<String>, name: String): String? {
        if (names.isEmpty()) return null
        return names.firstOrNull { it.lowercase().trim() == name.lowercase().trim() }
    }

    fun findMatchingTemplateName(name: String, candidateNames: List<String>): String? =
        candidateNames.firstOrNull { it == name }

    // ------------------------------------------------------------------
    // 类型化包装（App 接线用；经同一 JSON 级引擎，保证与官方单一路径）
    // ------------------------------------------------------------------

    @Serializable
    data class ContextGlobals(
        val alwaysForceName2: Boolean = true,
        val trimSentences: Boolean = false,
        val singleLine: Boolean = false,
    )

    data class ContextApplyResult(
        val context: ContextSettings,
        val globals: ContextGlobals,
        val presetName: String,
    )

    fun applyContextPreset(context: ContextSettings, globals: ContextGlobals, preset: JsonObject): ContextApplyResult {
        val powerUser = buildJsonObject {
            put("context", contextToJson(context))
            put("always_force_name2", JsonPrimitive(globals.alwaysForceName2))
            put("trim_sentences", JsonPrimitive(globals.trimSentences))
            put("single_line", JsonPrimitive(globals.singleLine))
        }
        val result = applyContextPresetJson(powerUser, preset)
        val resultContext = result["context"]?.jsonObject ?: JsonObject(emptyMap())
        return ContextApplyResult(
            context = context.copy(
                preset = (resultContext["preset"] as? JsonPrimitive)?.content ?: context.preset,
                storyString = orEmptyString(resultContext["story_string"]),
                exampleSeparator = orEmptyString(resultContext["example_separator"]),
                chatStart = orEmptyString(resultContext["chat_start"]),
                useStopStrings = jsTruthy(resultContext["use_stop_strings"]),
                namesAsStopStrings = jsTruthy(resultContext["names_as_stop_strings"]),
                storyStringPosition = (resultContext["story_string_position"] as? JsonPrimitive)?.content?.toIntOrNull() ?: context.storyStringPosition,
                storyStringRole = (resultContext["story_string_role"] as? JsonPrimitive)?.content?.toIntOrNull() ?: context.storyStringRole,
                storyStringDepth = (resultContext["story_string_depth"] as? JsonPrimitive)?.content?.toIntOrNull() ?: context.storyStringDepth,
            ),
            globals = ContextGlobals(
                alwaysForceName2 = jsTruthy(result["always_force_name2"]),
                trimSentences = jsTruthy(result["trim_sentences"]),
                singleLine = jsTruthy(result["single_line"]),
            ),
            presetName = (preset["name"] as? JsonPrimitive)?.content ?: context.preset,
        )
    }

    fun applyInstructPreset(instruct: InstructSettings, preset: JsonObject): InstructSettings {
        val migrated = migrateInstructModeSettings(preset)
        fun str(key: String, fallback: String): String = (migrated[key] as? JsonPrimitive)?.content ?: fallback
        fun bool(key: String, fallback: Boolean): Boolean =
            (migrated[key] as? JsonPrimitive)?.content?.let { it == "true" } ?: fallback
        return instruct.copy(
            preset = str("name", instruct.preset),
            enabled = bool("enabled", instruct.enabled),
            inputSequence = str("input_sequence", instruct.inputSequence),
            inputSuffix = str("input_suffix", instruct.inputSuffix),
            outputSequence = str("output_sequence", instruct.outputSequence),
            outputSuffix = str("output_suffix", instruct.outputSuffix),
            systemSequence = str("system_sequence", instruct.systemSequence),
            systemSuffix = str("system_suffix", instruct.systemSuffix),
            lastSystemSequence = str("last_system_sequence", instruct.lastSystemSequence),
            firstInputSequence = str("first_input_sequence", instruct.firstInputSequence),
            firstOutputSequence = str("first_output_sequence", instruct.firstOutputSequence),
            lastInputSequence = str("last_input_sequence", instruct.lastInputSequence),
            lastOutputSequence = str("last_output_sequence", instruct.lastOutputSequence),
            storyStringPrefix = str("story_string_prefix", instruct.storyStringPrefix),
            storyStringSuffix = str("story_string_suffix", instruct.storyStringSuffix),
            stopSequence = str("stop_sequence", instruct.stopSequence),
            wrap = bool("wrap", instruct.wrap),
            macro = bool("macro", instruct.macro),
            namesBehavior = NamesBehavior.fromValue(str("names_behavior", instruct.namesBehavior.value)),
            activationRegex = str("activation_regex", instruct.activationRegex),
            bindToContext = bool("bind_to_context", instruct.bindToContext),
            userAlignmentMessage = str("user_alignment_message", instruct.userAlignmentMessage),
            systemSameAsUser = bool("system_same_as_user", instruct.systemSameAsUser),
            sequencesAsStopStrings = bool("sequences_as_stop_strings", instruct.sequencesAsStopStrings),
            skipExamples = bool("skip_examples", instruct.skipExamples),
        )
    }

    fun applySyspromptPreset(sysprompt: SyspromptSettings, preset: JsonObject): SyspromptSettings =
        sysprompt.copy(
            enabled = true,
            name = (preset["name"] as? JsonPrimitive)?.content ?: sysprompt.name,
            content = orEmptyString(preset["content"]),
            postHistory = orEmptyString(preset["post_history"]),
        )

    fun applyReasoningPreset(reasoning: ReasoningSettings, preset: JsonObject): ReasoningSettings =
        reasoning.copy(
            name = (preset["name"] as? JsonPrimitive)?.content ?: reasoning.name,
            template = reasoning.template.copy(
                prefix = (preset["prefix"] as? JsonPrimitive)?.content ?: reasoning.template.prefix,
                suffix = (preset["suffix"] as? JsonPrimitive)?.content ?: reasoning.template.suffix,
                separator = (preset["separator"] as? JsonPrimitive)?.content ?: reasoning.template.separator,
            ),
        )

    private fun contextToJson(context: ContextSettings): JsonObject = buildJsonObject {
        put("story_string", JsonPrimitive(context.storyString))
        put("example_separator", JsonPrimitive(context.exampleSeparator))
        put("chat_start", JsonPrimitive(context.chatStart))
        put("use_stop_strings", JsonPrimitive(context.useStopStrings))
        put("names_as_stop_strings", JsonPrimitive(context.namesAsStopStrings))
        put("story_string_position", JsonPrimitive(context.storyStringPosition))
        put("story_string_role", JsonPrimitive(context.storyStringRole))
        put("story_string_depth", JsonPrimitive(context.storyStringDepth))
    }
}
