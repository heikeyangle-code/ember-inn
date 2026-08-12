#!/usr/bin/env node
// 预设应用全链纯逻辑（对照官方 release 8172dcd）→ fixture。
// 逐字提取：preset-manager.js（isPossibly*/masterSections/performMasterImport legacy/getPresetSettings filteredKeys）、
// power-user.js（contextControls + context_presets change 应用循环 + getContextSettings + autoFixStoryString）、
// instruct-mode.js（controls + migrateInstructModeSettings + instruct_presets change 应用循环）、
// sysprompt.js（sysprompt select change 纯字段部分）、reasoning.js（reasoning select change 纯字段部分）、
// openai.js（settingsToUpdate + onSettingsPresetChange 纯循环 + migrateChatCompletionSettings + getChatCompletionPreset）。
// 打桩登记（脚本头部，规则 0.1）：
//   - DOM/selector 更新与 saveSettings/事件触发全部剥除，只保留设置对象的纯字段变换；
//   - sysprompt 应用按官方 $select.on('change')：enabled 自动置 true（打桩为字段级等价）；
//   - 各 apply 的“预设名匹配到对象”步骤用 fixtures 直接给 preset 对象（官方 find(x => x.name === name) 语义）；
//   - Fuse 模糊匹配未移植（/preset 命令与 selectSystemPrompt 的 fuzzy 回退），App 用子串近似，登记于 HANDOFF 8.6。
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'preset-apply.json');

const funcs = `
// ---- preset-manager.js:209-233（isPossibly* 逐字）----
function isPossiblyInstructData(data) {
    const instructProps = ['name', 'input_sequence', 'output_sequence'];
    return data && instructProps.every(prop => Object.keys(data).includes(prop));
}
function isPossiblyContextData(data) {
    const contextProps = ['name', 'story_string'];
    return data && contextProps.every(prop => Object.keys(data).includes(prop));
}
function isPossiblySystemPromptData(data) {
    const sysPromptProps = ['name', 'content'];
    return data && sysPromptProps.every(prop => Object.keys(data).includes(prop));
}
function isPossiblyTextCompletionData(data) {
    const textCompletionProps = ['temp', 'top_k', 'top_p', 'rep_pen'];
    return data && textCompletionProps.every(prop => Object.keys(data).includes(prop));
}
function isPossiblyReasoningData(data) {
    const reasoningProps = ['name', 'prefix', 'suffix', 'separator'];
    return data && reasoningProps.every(prop => Object.keys(data).includes(prop));
}
function isPossiblyStartReplyWithData(data) {
    return data && 'value' in data && 'show' in data;
}

// ---- preset-manager.js performMasterImport legacy 顺序（先单个类型识别）----
function detectLegacyImportType(data) {
    if (!data || typeof data !== 'object') return null;
    if (isPossiblyInstructData(data)) return 'instruct';
    if (isPossiblyContextData(data)) return 'context';
    if (isPossiblySystemPromptData(data)) return 'sysprompt';
    if (isPossiblyTextCompletionData(data)) return 'preset';
    if (isPossiblyReasoningData(data)) return 'reasoning';
    return null;
}

// ---- preset-manager.js masterSections isValid ----
const masterSections = {
    instruct: { key: 'instruct', isValid: isPossiblyInstructData },
    context: { key: 'context', isValid: isPossiblyContextData },
    sysprompt: { key: 'sysprompt', isValid: isPossiblySystemPromptData },
    preset: { key: 'preset', isValid: isPossiblyTextCompletionData },
    reasoning: { key: 'reasoning', isValid: isPossiblyReasoningData },
    srw: { key: 'srw', isValid: isPossiblyStartReplyWithData },
};

// ---- power-user.js:354-369 contextControls（逐字数据）----
const extension_prompt_types = { IN_PROMPT: 0, IN_CHAT: 1 };
const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
const contextControls = [
    { id: 'context_story_string', property: 'story_string', isCheckbox: false, isGlobalSetting: false },
    { id: 'context_example_separator', property: 'example_separator', isCheckbox: false, isGlobalSetting: false },
    { id: 'context_chat_start', property: 'chat_start', isCheckbox: false, isGlobalSetting: false },
    { id: 'context_use_stop_strings', property: 'use_stop_strings', isCheckbox: true, isGlobalSetting: false, defaultValue: false },
    { id: 'context_names_as_stop_strings', property: 'names_as_stop_strings', isCheckbox: true, isGlobalSetting: false, defaultValue: true },
    { id: 'context_story_string_position', property: 'story_string_position', isCheckbox: false, isGlobalSetting: false, defaultValue: extension_prompt_types.IN_PROMPT, trigger: true },
    { id: 'context_story_string_depth', property: 'story_string_depth', isCheckbox: false, isGlobalSetting: false, defaultValue: 1 },
    { id: 'context_story_string_role', property: 'story_string_role', isCheckbox: false, isGlobalSetting: false, defaultValue: extension_prompt_roles.SYSTEM },
    { id: 'always-force-name2-checkbox', property: 'always_force_name2', isCheckbox: true, isGlobalSetting: true, defaultValue: true },
    { id: 'trim_sentences_checkbox', property: 'trim_sentences', isCheckbox: true, isGlobalSetting: true, defaultValue: false },
    { id: 'single_line', property: 'single_line', isCheckbox: true, isGlobalSetting: true, defaultValue: false },
];

// ---- power-user.js loadContextSettings.autoFixStoryString（逐字）----
function autoFixStoryString(contextSettings) {
    if (!contextSettings || Object.hasOwn(contextSettings, 'story_string_position')) {
        return;
    }
    let storyString = contextSettings.story_string || '';
    function autoFixMissingField(field, position) {
        if (storyString.includes(\`{{\${field}}}\`)) {
            return;
        }
        const fieldTemplate = \`{{#if \${field}}}{{\${field}}}\\n{{/if}}\`;
        const firstCurlyPosition = storyString.includes('{{') ? storyString.indexOf('{{') : 0;
        const lastCurlyPosition = storyString.includes('}}') ? storyString.lastIndexOf('}}') + '}}'.length : storyString.length;
        const lastTrimPosition = storyString.includes('{{trim}}') ? storyString.lastIndexOf('{{trim}}') : storyString.length;
        const endPosition = Math.min(lastTrimPosition, lastCurlyPosition);
        storyString = position === 'start'
            ? storyString.substring(0, firstCurlyPosition) + fieldTemplate + storyString.substring(firstCurlyPosition)
            : storyString.substring(0, endPosition) + fieldTemplate + storyString.substring(endPosition);
    }
    autoFixMissingField('anchorBefore', 'start');
    autoFixMissingField('anchorAfter', 'end');
    contextSettings.story_string = storyString;
}

// ---- power-user.js:2037-2074 context_presets change（纯字段部分，逐字）----
function applyContextPreset(powerUser, preset) {
    autoFixStoryString(preset);
    powerUser.context.preset = preset.name;
    contextControls.forEach(control => {
        const presetValue = preset[control.property] ?? control.defaultValue;
        if (presetValue !== undefined) {
            if (control.isGlobalSetting) {
                powerUser[control.property] = presetValue;
            } else {
                powerUser.context[control.property] = presetValue;
            }
        }
    });
    return powerUser;
}

// ---- power-user.js getContextSettings（逐字）----
function getContextSettingsCompiled(powerUser) {
    let compiledSettings = {};
    contextControls.forEach((control) => {
        let value = control.isGlobalSetting ? powerUser[control.property] : powerUser.context[control.property];
        if (control.isCheckbox) {
            value = !!value;
        }
        compiledSettings[control.property] = value;
    });
    return compiledSettings;
}

// ---- instruct-mode.js:23-53 controls + 55-105 migrateInstructModeSettings（逐字）----
const names_behavior_types = { NONE: 'none', FORCE: 'force', ALWAYS: 'always' };
const instructControls = [
    { id: 'instruct_enabled', property: 'enabled', isCheckbox: true },
    { id: 'instruct_wrap', property: 'wrap', isCheckbox: true },
    { id: 'instruct_macro', property: 'macro', isCheckbox: true },
    { id: 'instruct_story_string_prefix', property: 'story_string_prefix', isCheckbox: false },
    { id: 'instruct_story_string_suffix', property: 'story_string_suffix', isCheckbox: false },
    { id: 'instruct_input_sequence', property: 'input_sequence', isCheckbox: false },
    { id: 'instruct_input_suffix', property: 'input_suffix', isCheckbox: false },
    { id: 'instruct_output_sequence', property: 'output_sequence', isCheckbox: false },
    { id: 'instruct_output_suffix', property: 'output_suffix', isCheckbox: false },
    { id: 'instruct_system_sequence', property: 'system_sequence', isCheckbox: false },
    { id: 'instruct_system_suffix', property: 'system_suffix', isCheckbox: false },
    { id: 'instruct_last_system_sequence', property: 'last_system_sequence', isCheckbox: false },
    { id: 'instruct_user_alignment_message', property: 'user_alignment_message', isCheckbox: false },
    { id: 'instruct_stop_sequence', property: 'stop_sequence', isCheckbox: false },
    { id: 'instruct_first_output_sequence', property: 'first_output_sequence', isCheckbox: false },
    { id: 'instruct_last_output_sequence', property: 'last_output_sequence', isCheckbox: false },
    { id: 'instruct_first_input_sequence', property: 'first_input_sequence', isCheckbox: false },
    { id: 'instruct_last_input_sequence', property: 'last_input_sequence', isCheckbox: false },
    { id: 'instruct_activation_regex', property: 'activation_regex', isCheckbox: false },
    { id: 'instruct_bind_to_context', property: 'bind_to_context', isCheckbox: true },
    { id: 'instruct_skip_examples', property: 'skip_examples', isCheckbox: true },
    { id: 'instruct_names_behavior', property: 'names_behavior', isCheckbox: false },
    { id: 'instruct_system_same_as_user', property: 'system_same_as_user', isCheckbox: true, trigger: true },
    { id: 'instruct_sequences_as_stop_strings', property: 'sequences_as_stop_strings', isCheckbox: true },
];
function migrateInstructModeSettings(settings) {
    if (settings.separator_sequence !== undefined) {
        settings.output_suffix = settings.separator_sequence || '';
        delete settings.separator_sequence;
    }
    if (settings.names !== undefined) {
        settings.names_behavior = settings.names
            ? names_behavior_types.ALWAYS
            : (settings.names_force_groups ? names_behavior_types.FORCE : names_behavior_types.NONE);
        delete settings.names;
        delete settings.names_force_groups;
    }
    const defaults = {
        input_suffix: '',
        system_sequence: '',
        system_suffix: '',
        user_alignment_message: '',
        last_system_sequence: '',
        first_input_sequence: '',
        last_input_sequence: '',
        skip_examples: false,
        system_same_as_user: false,
        names_behavior: names_behavior_types.FORCE,
        sequences_as_stop_strings: true,
        story_string_prefix: '',
        story_string_suffix: '',
    };
    for (let key in defaults) {
        if (settings[key] === undefined) {
            settings[key] = defaults[key];
        }
    }
    const obsoleteFields = ['names', 'names_force_groups', 'system_sequence_prefix', 'system_sequence_suffix'];
    for (const field of obsoleteFields) {
        if (Object.hasOwn(settings, field)) {
            delete settings[field];
        }
    }
}

// ---- instruct-mode.js:826-849 instruct_presets change（纯字段部分，逐字）----
function applyInstructPreset(powerUser, preset) {
    migrateInstructModeSettings(preset);
    powerUser.instruct.preset = String(preset.name);
    instructControls.forEach(control => {
        if (preset[control.property] !== undefined) {
            powerUser.instruct[control.property] = preset[control.property];
        }
    });
    return powerUser;
}

// ---- sysprompt.js:143-161 $select.on('change') 纯字段部分 ----
function applySyspromptPreset(powerUser, preset) {
    powerUser.sysprompt.enabled = true;
    powerUser.sysprompt.name = preset.name;
    powerUser.sysprompt.content = preset.content || '';
    powerUser.sysprompt.post_history = preset.post_history || '';
    return powerUser;
}

// ---- reasoning.js:830-843 UI.$select.on('change') 纯字段部分 ----
function applyReasoningPreset(powerUser, template) {
    powerUser.reasoning.name = template.name;
    powerUser.reasoning.prefix = template.prefix;
    powerUser.reasoning.suffix = template.suffix;
    powerUser.reasoning.separator = template.separator;
    return powerUser;
}

// ---- openai.js:295-401 settingsToUpdate（逐字数据，selector 省略保留字段语义）----
const settingsToUpdate = {
    chat_completion_source: ['#chat_completion_source', 'chat_completion_source', false, true],
    temperature: ['#temp_openai', 'temp_openai', false, false],
    frequency_penalty: ['#freq_pen_openai', 'freq_pen_openai', false, false],
    presence_penalty: ['#pres_pen_openai', 'pres_pen_openai', false, false],
    top_p: ['#top_p_openai', 'top_p_openai', false, false],
    top_k: ['#top_k_openai', 'top_k_openai', false, false],
    top_a: ['#top_a_openai', 'top_a_openai', false, false],
    min_p: ['#min_p_openai', 'min_p_openai', false, false],
    repetition_penalty: ['#repetition_penalty_openai', 'repetition_penalty_openai', false, false],
    max_context_unlocked: ['#oai_max_context_unlocked', 'max_context_unlocked', true, false],
    group_models: ['#cc_group_models', 'group_models', true, true],
    sort_models: ['#cc_sort_models', 'sort_models', false, true],
    openai_model: ['#model_openai_select', 'openai_model', false, true],
    claude_model: ['#model_claude_select', 'claude_model', false, true],
    openrouter_model: ['#model_openrouter_select', 'openrouter_model', false, true],
    openrouter_use_fallback: ['#openrouter_use_fallback', 'openrouter_use_fallback', true, true],
    openrouter_providers: ['#openrouter_providers_chat', 'openrouter_providers', false, true],
    openrouter_quantizations: ['#openrouter_quantizations_chat', 'openrouter_quantizations', false, true],
    openrouter_allow_fallbacks: ['#openrouter_allow_fallbacks', 'openrouter_allow_fallbacks', true, true],
    openrouter_middleout: ['#openrouter_middleout', 'openrouter_middleout', false, true],
    tool_reasoning_mode: ['#tool_reasoning_mode', 'tool_reasoning_mode', false, false],
    ai21_model: ['#model_ai21_select', 'ai21_model', false, true],
    mistralai_model: ['#model_mistralai_select', 'mistralai_model', false, true],
    cohere_model: ['#model_cohere_select', 'cohere_model', false, true],
    perplexity_model: ['#model_perplexity_select', 'perplexity_model', false, true],
    groq_model: ['#model_groq_select', 'groq_model', false, true],
    chutes_model: ['#model_chutes_select', 'chutes_model', false, true],
    siliconflow_model: ['#model_siliconflow_select', 'siliconflow_model', false, true],
    siliconflow_endpoint: ['#siliconflow_endpoint', 'siliconflow_endpoint', false, true],
    minimax_model: ['#model_minimax_select', 'minimax_model', false, true],
    minimax_endpoint: ['#minimax_endpoint', 'minimax_endpoint', false, true],
    electronhub_model: ['#model_electronhub_select', 'electronhub_model', false, true],
    nanogpt_model: ['#model_nanogpt_select', 'nanogpt_model', false, true],
    nanogpt_provider: ['#nanogpt_provider', 'nanogpt_provider', false, true],
    nanogpt_payg_override: ['#nanogpt_payg_override', 'nanogpt_payg_override', true, true],
    deepseek_model: ['#model_deepseek_select', 'deepseek_model', false, true],
    aimlapi_model: ['#model_aimlapi_select', 'aimlapi_model', false, true],
    xai_model: ['#model_xai_select', 'xai_model', false, true],
    pollinations_model: ['#model_pollinations_select', 'pollinations_model', false, true],
    moonshot_model: ['#model_moonshot_select', 'moonshot_model', false, true],
    fireworks_model: ['#model_fireworks_select', 'fireworks_model', false, true],
    cometapi_model: ['#model_cometapi_select', 'cometapi_model', false, true],
    custom_model: ['#custom_model_id', 'custom_model', false, true],
    custom_url: ['#custom_api_url_text', 'custom_url', false, true],
    custom_include_body: ['#custom_include_body', 'custom_include_body', false, true],
    custom_exclude_body: ['#custom_exclude_body', 'custom_exclude_body', false, true],
    custom_include_headers: ['#custom_include_headers', 'custom_include_headers', false, true],
    custom_prompt_post_processing: ['#custom_prompt_post_processing', 'custom_prompt_post_processing', false, true],
    google_model: ['#model_google_select', 'google_model', false, true],
    vertexai_model: ['#model_vertexai_select', 'vertexai_model', false, true],
    zai_model: ['#model_zai_select', 'zai_model', false, true],
    zai_endpoint: ['#zai_endpoint', 'zai_endpoint', false, true],
    workers_ai_model: ['#model_workers_ai_select', 'workers_ai_model', false, true],
    workers_ai_account_id: ['#workers_ai_account_id', 'workers_ai_account_id', false, true],
    openai_max_context: ['#openai_max_context', 'openai_max_context', false, false],
    openai_max_tokens: ['#openai_max_tokens', 'openai_max_tokens', false, false],
    names_behavior: ['#names_behavior', 'names_behavior', false, false],
    send_if_empty: ['#send_if_empty_textarea', 'send_if_empty', false, false],
    impersonation_prompt: ['#impersonation_prompt_textarea', 'impersonation_prompt', false, false],
    new_chat_prompt: ['#newchat_prompt_textarea', 'new_chat_prompt', false, false],
    new_group_chat_prompt: ['#newgroupchat_prompt_textarea', 'new_group_chat_prompt', false, false],
    new_example_chat_prompt: ['#newexamplechat_prompt_textarea', 'new_example_chat_prompt', false, false],
    continue_nudge_prompt: ['#continue_nudge_prompt_textarea', 'continue_nudge_prompt', false, false],
    bias_preset_selected: ['#openai_logit_bias_preset', 'bias_preset_selected', false, false],
    reverse_proxy: ['#openai_reverse_proxy', 'reverse_proxy', false, true],
    wi_format: ['#wi_format_textarea', 'wi_format', false, false],
    scenario_format: ['#scenario_format_textarea', 'scenario_format', false, false],
    personality_format: ['#personality_format_textarea', 'personality_format', false, false],
    group_nudge_prompt: ['#group_nudge_prompt_textarea', 'group_nudge_prompt', false, false],
    stream_openai: ['#stream_toggle', 'stream_openai', true, false],
    prompts: ['', 'prompts', false, false],
    prompt_order: ['', 'prompt_order', false, false],
    show_external_models: ['#openai_show_external_models', 'show_external_models', true, true],
    proxy_password: ['#openai_proxy_password', 'proxy_password', false, true],
    assistant_prefill: ['#claude_assistant_prefill', 'assistant_prefill', false, false],
    assistant_impersonation: ['#claude_assistant_impersonation', 'assistant_impersonation', false, false],
    use_sysprompt: ['#use_sysprompt', 'use_sysprompt', true, false],
    vertexai_auth_mode: ['#vertexai_auth_mode', 'vertexai_auth_mode', false, true],
    vertexai_region: ['#vertexai_region', 'vertexai_region', false, true],
    vertexai_express_project_id: ['#vertexai_express_project_id', 'vertexai_express_project_id', false, true],
    squash_system_messages: ['#squash_system_messages', 'squash_system_messages', true, false],
    media_inlining: ['#openai_media_inlining', 'media_inlining', true, false],
    inline_image_quality: ['#openai_inline_image_quality', 'inline_image_quality', false, false],
    continue_prefill: ['#continue_prefill', 'continue_prefill', true, false],
    continue_postfix: ['#continue_postfix', 'continue_postfix', false, false],
    function_calling: ['#openai_function_calling', 'function_calling', true, false],
    tool_call_recurse_limit: ['#tool_call_recurse_limit', 'tool_call_recurse_limit', false, false],
    show_thoughts: ['#openai_show_thoughts', 'show_thoughts', true, false],
    reasoning_effort: ['#openai_reasoning_effort', 'reasoning_effort', false, false],
    verbosity: ['#openai_verbosity', 'verbosity', false, false],
    enable_web_search: ['#openai_enable_web_search', 'enable_web_search', true, false],
    seed: ['#seed_openai', 'seed', false, false],
    n: ['#n_openai', 'n', false, false],
    bypass_status_check: ['#openai_bypass_status_check', 'bypass_status_check', true, true],
    request_images: ['#openai_request_images', 'request_images', true, false],
    request_image_aspect_ratio: ['#request_image_aspect_ratio', 'request_image_aspect_ratio', false, false],
    request_image_resolution: ['#request_image_resolution', 'request_image_resolution', false, false],
    azure_base_url: ['#azure_base_url', 'azure_base_url', false, true],
    azure_deployment_name: ['#azure_deployment_name', 'azure_deployment_name', false, true],
    azure_api_version: ['#azure_api_version', 'azure_api_version', false, true],
    azure_openai_model: ['#azure_openai_model', 'azure_openai_model', false, true],
    extensions: ['#NULL_SELECTOR', 'extensions', false, false],
};

// ---- openai.js:4179-4212 migrateChatCompletionSettings（逐字）----
function migrateChatCompletionSettings(settings) {
    const migrateMap = [
        { oldKey: 'names_in_completion', oldValue: true, newKey: 'names_behavior', newValue: 'completion' },
        { oldKey: 'chat_completion_source', oldValue: 'palm', newKey: 'chat_completion_source', newValue: 'makersuite' },
        { oldKey: 'custom_prompt_post_processing', oldValue: 'claude', newKey: 'custom_prompt_post_processing', newValue: 'merge' },
        { oldKey: 'ai21_model', oldValue: /^j2-/, newKey: 'ai21_model', newValue: 'jamba-large' },
        { oldKey: 'image_inlining', oldValue: false, newKey: 'media_inlining', newValue: false },
        { oldKey: 'image_inlining', oldValue: true, newKey: 'media_inlining', newValue: true },
        { oldKey: 'video_inlining', oldValue: true, newKey: 'media_inlining', newValue: true },
        { oldKey: 'audio_inlining', oldValue: true, newKey: 'media_inlining', newValue: true },
        { oldKey: 'claude_use_sysprompt', oldValue: true, newKey: 'use_sysprompt', newValue: true },
        { oldKey: 'use_makersuite_sysprompt', oldValue: true, newKey: 'use_sysprompt', newValue: true },
        { oldKey: 'mistralai_model', oldValue: /^(mistral-medium|mistral-small)$/, newKey: 'mistralai_model', newValue: 'mistralai_model', newValueFn: (s) => (s.mistralai_model + '-latest') },
        { oldKey: 'deepseek_model', oldValue: /^deepseek-(chat|reasoner|coder)$/, newKey: 'deepseek_model', newValue: 'deepseek-v4-flash' },
        { oldKey: 'openrouter_sort_models', oldValue: 'alphabetically', newKey: 'sort_models', newValue: 'alphabetically' },
        { oldKey: 'openrouter_sort_models', oldValue: 'pricing.prompt', newKey: 'sort_models', newValue: 'pricing.prompt' },
        { oldKey: 'openrouter_sort_models', oldValue: 'context_length', newKey: 'sort_models', newValue: 'context_length' },
        { oldKey: 'openrouter_group_models', oldValue: true, newKey: 'group_models', newValue: true },
    ];
    for (const migration of migrateMap) {
        if (Object.hasOwn(settings, migration.oldKey)) {
            const shouldMigrate = migration.oldValue instanceof RegExp
                ? migration.oldValue.test(settings[migration.oldKey])
                : settings[migration.oldKey] === migration.oldValue;
            if (shouldMigrate) {
                settings[migration.newKey] = migration.newValueFn ? migration.newValueFn(settings) : migration.newValue;
            }
            if (migration.oldKey !== migration.newKey) {
                delete settings[migration.oldKey];
            }
        }
    }
}

// ---- openai.js:4898-4945 onSettingsPresetChange 纯循环（逐字；事件/UI/save 剥除）----
function applyChatCompletionPreset(settings, preset, bindPresetToConnection) {
    for (const [key, [selector, setting, isCheckbox, isConnection]] of Object.entries(settingsToUpdate)) {
        if (isConnection && !bindPresetToConnection) {
            continue;
        }
        if (key === 'extensions') {
            settings.extensions = preset.extensions || {};
            continue;
        }
        if (preset[key] !== undefined) {
            settings[setting] = preset[key];
        }
    }
    return settings;
}

// ---- openai.js:4479-4486 getChatCompletionPreset（逐字）----
function getChatCompletionPresetBody(settings) {
    const presetBody = {};
    for (const [presetKey, [, settingsKey]] of Object.entries(settingsToUpdate)) {
        presetBody[presetKey] = settings[settingsKey];
    }
    return presetBody;
}

// ---- preset-manager.js:640-726 getPresetSettings filteredKeys + genamt/max_length ----
function filterPresetSettings(settings, apiId, name, currentName, isAdvancedFormatting, extraGen) {
    const filteredKeys = [
        'api_server', 'preset', 'streaming', 'truncation_length', 'n', 'streaming_url', 'stopping_strings',
        'can_use_tokenization', 'can_use_streaming', 'preset_settings_novel', 'preset_settings',
        'streaming_novel', 'nai_preamble', 'model_novel', 'streaming_kobold', 'enabled', 'bind_to_context',
        'seed', 'legacy_api', 'mancer_model', 'togetherai_model', 'ollama_model', 'vllm_model',
        'aphrodite_model', 'llamacpp_model', 'server_urls', 'type', 'custom_model', 'bypass_status_check',
        'infermaticai_model', 'dreamgen_model', 'openrouter_model', 'featherless_model',
        'max_tokens_second', 'openrouter_providers', 'openrouter_quantizations',
        'openrouter_allow_fallbacks', 'tabby_model', 'derived', 'generic_model', 'include_reasoning',
        'global_banned_tokens', 'send_banned_tokens', 'auto_parse', 'add_to_prompts', 'auto_expand',
        'show_hidden', 'max_additions',
    ];
    const out = Object.assign({}, settings);
    for (const key of filteredKeys) {
        if (Object.hasOwn(out, key)) {
            delete out[key];
        }
    }
    if (isAdvancedFormatting) {
        out.name = name || currentName;
    } else if (apiId !== 'openai') {
        out.genamt = extraGen.genamt;
        out.max_length = extraGen.max_length;
    }
    return out;
}

// ---- 预设名匹配（/preset 命令 exact 部分；preset-manager.js:914-943）----
function matchPresetNameExact(allPresets, name) {
    if (!Array.isArray(allPresets) || allPresets.length === 0) return null;
    const exactMatch = allPresets.find(p => p.toLowerCase().trim() === name.toLowerCase().trim());
    return exactMatch || null;
}

// ---- 绑定同名模板（instruct-mode.js:657-665 selectMatchingContextTemplate；power-user.js:2076-2084 反向）----
function findMatchingTemplateName(name, candidateNames) {
    for (const candidate of candidateNames) {
        if (candidate === name) {
            return candidate;
        }
    }
    return null;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    const method = b.method;',
    '    if (method === "detectLegacy") return detectLegacyImportType(b.data ?? null);',
    '    if (method === "isPossibly") { const f = { instruct: isPossiblyInstructData, context: isPossiblyContextData, sysprompt: isPossiblySystemPromptData, preset: isPossiblyTextCompletionData, reasoning: isPossiblyReasoningData, srw: isPossiblyStartReplyWithData }; return f[b.type](b.data ?? null); }',
    '    if (method === "masterSectionsValid") { const out = {}; for (const [k, sec] of Object.entries(masterSections)) { if (k in (b.data ?? {})) out[k] = sec.isValid(b.data[k]); } return out; }',
    '    if (method === "applyContext") { const pu = JSON.parse(JSON.stringify(b.powerUser)); return applyContextPreset(pu, b.preset); }',
    '    if (method === "compileContext") return getContextSettingsCompiled(JSON.parse(JSON.stringify(b.powerUser)));',
    '    if (method === "autoFixStory") { const ctx = JSON.parse(JSON.stringify(b.context)); autoFixStoryString(ctx); return ctx; }',
    '    if (method === "applyInstruct") { const pu = JSON.parse(JSON.stringify(b.powerUser)); return applyInstructPreset(pu, b.preset); }',
    '    if (method === "migrateInstruct") { const s = JSON.parse(JSON.stringify(b.settings)); migrateInstructModeSettings(s); return s; }',
    '    if (method === "applySysprompt") { const pu = JSON.parse(JSON.stringify(b.powerUser)); return applySyspromptPreset(pu, b.preset); }',
    '    if (method === "applyReasoning") { const pu = JSON.parse(JSON.stringify(b.powerUser)); return applyReasoningPreset(pu, b.template); }',
    '    if (method === "migrateChatCompletion") { const s = JSON.parse(JSON.stringify(b.settings)); migrateChatCompletionSettings(s); return s; }',
    '    if (method === "applyChatCompletion") return applyChatCompletionPreset(JSON.parse(JSON.stringify(b.settings)), b.preset, b.bindPresetToConnection);',
    '    if (method === "chatCompletionBody") return getChatCompletionPresetBody(JSON.parse(JSON.stringify(b.settings)));',
    '    if (method === "filterPresetSettings") return filterPresetSettings(JSON.parse(JSON.stringify(b.settings)), b.apiId, b.name, b.currentName, b.isAdvancedFormatting, b.extraGen);',
    '    if (method === "matchExact") return matchPresetNameExact(b.names ?? [], b.name ?? "");',
    '    if (method === "findMatching") return findMatchingTemplateName(b.name ?? "", b.candidateNames ?? []);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const powerUserBase = {
    always_force_name2: true, trim_sentences: false, single_line: false,
    context: {
        preset: 'Default', story_string: '{{#if system}}{{system}}\n{{/if}}{{trim}}', chat_start: '***',
        example_separator: '***', use_stop_strings: true, names_as_stop_strings: true,
        story_string_position: 0, story_string_role: 0, story_string_depth: 1,
    },
    instruct: {
        enabled: false, preset: 'Alpaca', input_sequence: '### Instruction:', input_suffix: '',
        output_sequence: '### Response:', output_suffix: '', system_sequence: '', system_suffix: '',
        last_system_sequence: '', first_input_sequence: '', first_output_sequence: '', last_input_sequence: '',
        last_output_sequence: '', story_string_prefix: '', story_string_suffix: '', stop_sequence: '',
        wrap: true, macro: true, names_behavior: 'force', activation_regex: '', bind_to_context: false,
        user_alignment_message: '', system_same_as_user: false, separator_sequence: '', sequences_as_stop_strings: true,
    },
    sysprompt: { enabled: true, name: 'Neutral - Chat', content: 'Write {{char}}\'s next reply', post_history: '' },
    reasoning: { name: 'Think XML', auto_parse: false, add_to_prompts: false, auto_expand: false, show_hidden: false, prefix: '<think>', suffix: '</think>', separator: '\\n', max_additions: 1 },
};

// 1. 类型识别：空/缺失字段/各类型/多区段对象/null/非对象
await add('detect-null', { method: 'detectLegacy', data: null });
await add('detect-empty', { method: 'detectLegacy', data: {} });
await add('detect-instruct', { method: 'detectLegacy', data: { name: 'A', input_sequence: '<in>', output_sequence: '<out>' } });
await add('detect-context', { method: 'detectLegacy', data: { name: 'A', story_string: 'x' } });
await add('detect-sysprompt', { method: 'detectLegacy', data: { name: 'A', content: 'x' } });
await add('detect-textgen', { method: 'detectLegacy', data: { temp: 1, top_k: 0, top_p: 1, rep_pen: 1 } });
await add('detect-reasoning', { method: 'detectLegacy', data: { name: 'A', prefix: '<t>', suffix: '</t>', separator: '' } });
await add('detect-master-multi', { method: 'detectLegacy', data: { instruct: { name: 'A', input_sequence: 'x', output_sequence: 'y' }, context: { name: 'B', story_string: 'z' } } });
// 优先顺序：同时满足 instruct+context 结构 → instruct
await add('detect-order-instruct-wins', { method: 'detectLegacy', data: { name: 'A', input_sequence: 'x', output_sequence: 'y', story_string: 'z' } });

for (const type of ['instruct', 'context', 'sysprompt', 'preset', 'reasoning', 'srw']) {
    await add(`possible-${type}-ok`, { method: 'isPossibly', type, data: {
        instruct: { name: 'A', input_sequence: 'x', output_sequence: 'y' },
        context: { name: 'A', story_string: 'x' },
        sysprompt: { name: 'A', content: 'x' },
        preset: { temp: 1, top_k: 0, top_p: 1, rep_pen: 1 },
        reasoning: { name: 'A', prefix: 'p', suffix: 's', separator: '' },
        srw: { value: 'x', show: true },
    }[type] });
    await add(`possible-${type}-bad`, { method: 'isPossibly', type, data: { name: 'A' } });
    await add(`possible-${type}-null`, { method: 'isPossibly', type, data: null });
}
// srw：value 缺 / show 缺 / 空对象 / 字符串值
await add('possible-srw-missing-show', { method: 'isPossibly', type: 'srw', data: { value: 'x' } });
await add('possible-srw-empty', { method: 'isPossibly', type: 'srw', data: {} });

// 2. master 多区段校验
await add('master-sections-mixed', { method: 'masterSectionsValid', data: {
    instruct: { name: 'A', input_sequence: 'x', output_sequence: 'y' },
    context: { story_string: 'x' },
    sysprompt: { name: 'B', content: 'c' },
    preset: { temp: 1, top_k: 0, top_p: 1, rep_pen: 1 },
    reasoning: { name: 'C', prefix: 'p', suffix: 's', separator: '' },
    srw: { value: 'v', show: false },
} });
await add('master-sections-empty', { method: 'masterSectionsValid', data: {} });
await add('master-sections-unknown', { method: 'masterSectionsValid', data: { other: {} } });

// 3. context 应用：完整/局部字段/全局字段/checkbox 缺省回退/autoFix
await add('apply-context-full', { method: 'applyContext', powerUser: powerUserBase, preset: {
    name: 'X', story_string: '{{system}}', example_separator: '---', chat_start: '---', use_stop_strings: false,
    names_as_stop_strings: false, story_string_position: 1, story_string_depth: 2, story_string_role: 1,
    always_force_name2: false, trim_sentences: true, single_line: true,
} });
await add('apply-context-partial', { method: 'applyContext', powerUser: powerUserBase, preset: { name: 'Y', example_separator: '***' } });
await add('apply-context-empty-preset', { method: 'applyContext', powerUser: powerUserBase, preset: { name: 'Z' } });
await add('apply-context-autofix', { method: 'applyContext', powerUser: powerUserBase, preset: { name: 'A', story_string: 'plain text', example_separator: '***' } });
await add('compile-context', { method: 'compileContext', powerUser: powerUserBase });
await add('autofix-already-migrated', { method: 'autoFixStory', context: { story_string_position: 0, story_string: 'x' } });
await add('autofix-null', { method: 'autoFixStory', context: null });
await add('autofix-has-both', { method: 'autoFixStory', context: { story_string: '{{anchorBefore}}\n{{system}}{{trim}}{{anchorAfter}}' } });

// 4. instruct 应用：完整/旧字段迁移/names 迁移/局部
await add('migrate-instruct-legacy-names', { method: 'migrateInstruct', settings: { names: true, names_force_groups: false, input_sequence: '<in>', output_sequence: '<out>' } });
await add('migrate-instruct-names-groups', { method: 'migrateInstruct', settings: { names: false, names_force_groups: true } });
await add('migrate-instruct-separator', { method: 'migrateInstruct', settings: { separator_sequence: '###' } });
await add('migrate-instruct-obsolete', { method: 'migrateInstruct', settings: { system_sequence_prefix: 'A', system_sequence_suffix: 'B' } });
await add('migrate-instruct-empty', { method: 'migrateInstruct', settings: {} });
await add('apply-instruct-full', { method: 'applyInstruct', powerUser: powerUserBase, preset: {
    name: 'I', input_sequence: 'I:', output_sequence: 'O:', input_suffix: 'is', output_suffix: 'os', wrap: false, macro: false,
    names_behavior: 'always', system_sequence: 'S:', system_suffix: 'ss', stop_sequence: '<stop>', bind_to_context: true,
    skip_examples: true, system_same_as_user: true, sequences_as_stop_strings: false, activation_regex: '^x', first_input_sequence: 'FI',
} });
await add('apply-instruct-legacy', { method: 'applyInstruct', powerUser: powerUserBase, preset: { name: 'L', names: true, separator_sequence: '###' } });
await add('apply-instruct-empty', { method: 'applyInstruct', powerUser: powerUserBase, preset: { name: 'E' } });
await add('apply-instruct-no-name', { method: 'applyInstruct', powerUser: powerUserBase, preset: { input_sequence: 'X' } });
await add('compile-context-missing', { method: 'compileContext', powerUser: { context: { story_string: 's' }, always_force_name2: true } });

// 5. sysprompt / reasoning 应用
await add('apply-sysprompt-full', { method: 'applySysprompt', powerUser: powerUserBase, preset: { name: 'S', content: 'C', post_history: 'P' } });
await add('apply-sysprompt-empty-content', { method: 'applySysprompt', powerUser: powerUserBase, preset: { name: 'S' } });
await add('apply-sysprompt-null-content', { method: 'applySysprompt', powerUser: powerUserBase, preset: { name: 'S', content: null, post_history: null } });
await add('apply-sysprompt-numeric-content', { method: 'applySysprompt', powerUser: powerUserBase, preset: { name: 'S', content: 0 } });
await add('apply-reasoning-full', { method: 'applyReasoning', powerUser: powerUserBase, template: { name: 'R', prefix: '<r>', suffix: '</r>', separator: '\\n\\n' } });
await add('apply-reasoning-empty', { method: 'applyReasoning', powerUser: powerUserBase, template: { name: 'R' } });

// 6. chat completion 迁移 + 应用 + 导出 body
await add('migrate-cc-old-names', { method: 'migrateChatCompletion', settings: { names_in_completion: true, temperature: 1 } });
await add('migrate-cc-palm', { method: 'migrateChatCompletion', settings: { chat_completion_source: 'palm' } });
await add('migrate-cc-image-inlining-off', { method: 'migrateChatCompletion', settings: { image_inlining: false } });
await add('migrate-cc-ai21', { method: 'migrateChatCompletion', settings: { ai21_model: 'j2-light' } });
await add('migrate-cc-mistral-fn', { method: 'migrateChatCompletion', settings: { mistralai_model: 'mistral-small' } });
await add('migrate-cc-deepseek', { method: 'migrateChatCompletion', settings: { deepseek_model: 'deepseek-coder' } });
await add('migrate-cc-noop', { method: 'migrateChatCompletion', settings: { temperature: 1, openai_model: 'gpt-4o' } });
await add('apply-cc-bound', { method: 'applyChatCompletion', settings: { temperature: 0.5, openai_model: 'old', claude_model: 'old-c' }, preset: { temperature: 1, openai_model: 'new', claude_model: 'new-c', extensions: { x: 1 } }, bindPresetToConnection: true });
await add('apply-cc-unbound', { method: 'applyChatCompletion', settings: { temperature: 0.5, openai_model: 'old', claude_model: 'old-c' }, preset: { temperature: 1, openai_model: 'new', claude_model: 'new-c' }, bindPresetToConnection: false });
await add('apply-cc-partial', { method: 'applyChatCompletion', settings: { temperature: 0.5 }, preset: { temperature: 0.9, n: 2 }, bindPresetToConnection: true });
await add('apply-cc-extensions-undefined', { method: 'applyChatCompletion', settings: {}, preset: {}, bindPresetToConnection: true });
await add('cc-body', { method: 'chatCompletionBody', settings: { temperature: 1, openai_max_tokens: 300, openai_model: 'm', claude_model: null, use_sysprompt: false } });
await add('cc-body-empty', { method: 'chatCompletionBody', settings: {} });

// 7. filterPresetSettings（保存为预设时的过滤）
await add('filter-context', { method: 'filterPresetSettings', settings: { story_string: 's', example_separator: '***', name: 'Old', preset: 'Old', enabled: false, add_to_prompts: true, show_hidden: false, max_additions: 1 }, apiId: 'context', name: 'New', currentName: 'Old', isAdvancedFormatting: true, extraGen: { genamt: 80, max_length: 4000 } });
await add('filter-context-empty-name', { method: 'filterPresetSettings', settings: { story_string: 's', name: 'Old' }, apiId: 'context', name: '', currentName: 'Old', isAdvancedFormatting: true, extraGen: { genamt: 80, max_length: 4000 } });
await add('filter-sysprompt', { method: 'filterPresetSettings', settings: { name: 'Old', content: 'c', post_history: 'p', enabled: true }, apiId: 'sysprompt', name: 'New', currentName: 'Old', isAdvancedFormatting: true, extraGen: { genamt: 80, max_length: 4000 } });
await add('filter-openai', { method: 'filterPresetSettings', settings: {}, apiId: 'openai', name: 'New', currentName: 'Old', isAdvancedFormatting: false, extraGen: { genamt: 80, max_length: 4000 } });
await add('filter-textgen', { method: 'filterPresetSettings', settings: { temp: 1, n: 2, seed: 5, streaming: true }, apiId: 'textgenerationwebui', name: 'New', currentName: 'Old', isAdvancedFormatting: false, extraGen: { genamt: 80, max_length: 4000 } });

// 8. 名字匹配 + 同名模板绑定
await add('match-exact', { method: 'matchExact', names: ['Default', 'Alpaca', '  Space  '], name: 'alpaca' });
await add('match-exact-space', { method: 'matchExact', names: ['Default', ' Space '], name: ' space ' });
await add('match-exact-none', { method: 'matchExact', names: ['Default'], name: 'Alpaca' });
await add('match-exact-empty-list', { method: 'matchExact', names: [], name: 'Alpaca' });
await add('match-exact-empty-query', { method: 'matchExact', names: ['Default'], name: '' });
await add('find-matching-hit', { method: 'findMatching', name: 'Alpaca', candidateNames: ['Default', 'Alpaca', 'ChatML'] });
await add('find-matching-miss', { method: 'findMatching', name: 'Alpaca', candidateNames: ['Default', 'ChatML'] });
await add('find-matching-empty', { method: 'findMatching', name: 'Alpaca', candidateNames: [] });

writeFileSync(outFile, JSON.stringify({ source: 'preset-manager/power-user/instruct/sysprompt/reasoning/openai 预设纯逻辑（release 8172dcd）', cases }, null, 2));
console.log('preset-apply:', cases.length, 'cases ->', outFile);
