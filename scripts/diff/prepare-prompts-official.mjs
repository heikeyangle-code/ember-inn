#!/usr/bin/env node
// preparePromptsForChatCompletion（public/scripts/openai.js）→ JSON fixture。
// 函数体逐字提取；oai_settings/substituteParams/promptManager/扩展常量打桩
// （promptManager 逻辑照官方 PromptManager.js 的 getPromptCollection/preparePrompt 实现），
// 输出最终 PromptCollection（collection + overriddenPrompts）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'prepare-prompts.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'openai.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractFunction(name) {
    const start = src.indexOf(`async function ${name}(`);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = src.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < src.length; i++) {
        const ch = src[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(src[j])) j++; if (src[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return src.slice(start, scanBody(src, bodyStart) + 1);
}

const extension_prompt_types = { NONE: -1, IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };
const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
const persona_description_positions = { IN_PROMPT: 0, AFTER_CHAR: 1, TOP_AN: 2, BOTTOM_AN: 3, AT_DEPTH: 4, NONE: 9 };

const chatCompletionDefaultPrompts = [
    { name: 'Main Prompt', system_prompt: true, role: 'system', content: 'Write {{char}}\'s next reply in a fictional chat between {{charIfNotGroup}} and {{user}}.', identifier: 'main' },
    { name: 'Auxiliary Prompt', system_prompt: true, role: 'system', content: '', identifier: 'nsfw' },
    { identifier: 'dialogueExamples', name: 'Chat Examples', system_prompt: true, marker: true },
    { name: 'Post-History Instructions', system_prompt: true, role: 'system', content: '', identifier: 'jailbreak' },
    { identifier: 'chatHistory', name: 'Chat History', system_prompt: true, marker: true },
    { identifier: 'worldInfoAfter', name: 'World Info (after)', system_prompt: true, marker: true },
    { identifier: 'worldInfoBefore', name: 'World Info (before)', system_prompt: true, marker: true },
    { identifier: 'enhanceDefinitions', role: 'system', name: 'Enhance Definitions', content: 'If you have more knowledge of {{char}}, add to the character\'s lore and personality to enhance them but keep the Character Sheet\'s definitions absolute.', system_prompt: true, marker: false },
    { identifier: 'charDescription', name: 'Char Description', system_prompt: true, marker: true },
    { identifier: 'charPersonality', name: 'Char Personality', system_prompt: true, marker: true },
    { identifier: 'scenario', name: 'Scenario', system_prompt: true, marker: true },
    { identifier: 'personaDescription', name: 'Persona Description', system_prompt: true, marker: true },
];

const promptManagerDefaultPromptOrder = [
    { identifier: 'main', enabled: true },
    { identifier: 'worldInfoBefore', enabled: true },
    { identifier: 'personaDescription', enabled: true },
    { identifier: 'charDescription', enabled: true },
    { identifier: 'charPersonality', enabled: true },
    { identifier: 'scenario', enabled: true },
    { identifier: 'enhanceDefinitions', enabled: false },
    { identifier: 'nsfw', enabled: true },
    { identifier: 'worldInfoAfter', enabled: true },
    { identifier: 'dialogueExamples', enabled: true },
    { identifier: 'chatHistory', enabled: true },
    { identifier: 'jailbreak', enabled: true },
];

function makeStubs(request) {
    const body = request.body;
    const oai = body.oai ?? {};
    const env = body.env ?? { user: '', char: '' };

    const substituteParams = (text, extra = {}) => {
        const value = String(text ?? '');
        return value
            .replace(/\{\{scenario\}\}/g, body.scenario ?? '')
            .replace(/\{\{personality\}\}/g, body.charPersonality ?? '')
            .replace(/\{\{char\}\}/g, body.name2 ?? '')
            .replace(/\{\{charIfNotGroup\}\}/g, (env.group || body.name2 || ''))
            .replace(/\{\{user\}\}/g, env.user ?? '')
            .replace(/\{\{original\}\}/g, extra.original ?? '')
            .replace(/\{\{groupOverride\}\}/g, extra.groupOverride ?? '');
    };

    const oai_settings = {
        scenario_format: oai.scenario_format ?? null,
        personality_format: oai.personality_format ?? null,
        group_nudge_prompt: oai.group_nudge_prompt ?? null,
        impersonation_prompt: oai.impersonation_prompt ?? null,
        wi_format: oai.wi_format ?? null,
    };

    function stringFormat(format) {
        const args = Array.prototype.slice.call(arguments, 1);
        return format.replace(/{(\d+)}/g, function (match, number) {
            return typeof args[number] !== 'undefined' ? args[number] : match;
        });
    }

    const formatWorldInfo = (value) => {
        if (!value) return '';
        const format = oai_settings.wi_format ?? '{0}';
        if (!format.trim()) return value;
        return stringFormat(format, value);
    };

    function getPromptPosition(position) {
        if (position == extension_prompt_types.BEFORE_PROMPT) return 'start';
        if (position == extension_prompt_types.IN_PROMPT) return 'end';
        return false;
    }

    function getPromptRole(role) {
        switch (role) {
            case extension_prompt_roles.SYSTEM: return 'system';
            case extension_prompt_roles.USER: return 'user';
            case extension_prompt_roles.ASSISTANT: return 'assistant';
            default: return 'system';
        }
    }

    class Prompt {
        constructor({ identifier, role, content, name, system_prompt, position, injection_depth, injection_position, forbid_overrides, extension, injection_order, injection_trigger } = {}) {
            this.identifier = identifier;
            this.role = role;
            this.content = content;
            this.name = name;
            this.system_prompt = system_prompt;
            this.position = position;
            this.injection_depth = injection_depth;
            this.injection_position = injection_position;
            this.forbid_overrides = forbid_overrides;
            this.extension = extension ?? false;
            this.injection_order = injection_order ?? 100;
            this.injection_trigger = injection_trigger ?? [];
        }
    }

    class PromptCollection {
        constructor(...prompts) { this.collection = []; this.overriddenPrompts = []; this.add(...prompts); }
        add(...prompts) { this.collection.push(...prompts); }
        set(prompt, position) { this.collection[position] = prompt; }
        get(identifier) { return this.collection.find(prompt => prompt.identifier === identifier); }
        index(identifier) { return this.collection.findIndex(prompt => prompt.identifier === identifier); }
        has(identifier) { return this.index(identifier) !== -1; }
        override(prompt, position) { this.set(prompt, position); this.overriddenPrompts.push(prompt.identifier); }
    }

    const serviceSettings = {
        prompts: (body.userPrompts && body.userPrompts.length) ? structuredClone(body.userPrompts) : structuredClone(chatCompletionDefaultPrompts),
        prompt_order: body.userOrder && body.userOrder.length
            ? [{ character_id: 100000, order: structuredClone(body.userOrder) }]
            : [{ character_id: 100000, order: structuredClone(promptManagerDefaultPromptOrder) }],
    };

    const promptManager = {
        activeCharacter: { id: 100000 },
        serviceSettings,
        getPromptOrderForCharacter(character) {
            return !character ? [] : (this.serviceSettings.prompt_order.find(list => String(list.character_id) === String(character.id))?.order ?? []);
        },
        getPromptOrderEntry(character, identifier) {
            return this.getPromptOrderForCharacter(character).find(entry => entry.identifier === identifier) ?? null;
        },
        getPromptById(identifier) {
            return this.serviceSettings.prompts.find(item => item && item.identifier === identifier) ?? null;
        },
        getActiveGroupCharacters() {
            return (this.activeCharacter?.group?.members || []).map(member => member && member.substring(0, member.lastIndexOf('.')));
        },
        isPromptDisabledForActiveCharacter(identifier) {
            const promptOrderEntry = this.getPromptOrderEntry(this.activeCharacter, identifier);
            if (promptOrderEntry) return !promptOrderEntry.enabled;
            return false;
        },
        shouldTrigger(prompt, generationType) {
            if (!Array.isArray(prompt?.injection_trigger)) return true;
            if (!prompt.injection_trigger.length) return true;
            return prompt.injection_trigger.includes(generationType);
        },
        preparePrompt(prompt, original = null) {
            const groupMembers = this.getActiveGroupCharacters();
            const preparedPrompt = new Prompt(prompt);
            if (typeof original === 'string') {
                if (0 < groupMembers.length) preparedPrompt.content = substituteParams(prompt.content ?? '', { original, groupOverride: groupMembers.join(', ') });
                else preparedPrompt.content = substituteParams(prompt.content, { original });
            } else {
                if (0 < groupMembers.length) preparedPrompt.content = substituteParams(prompt.content ?? '', { groupOverride: groupMembers.join(', ') });
                else preparedPrompt.content = substituteParams(prompt.content);
            }
            return preparedPrompt;
        },
        getPromptCollection(generationType) {
            generationType = String(generationType || 'normal').toLowerCase().trim();
            const promptCollection = new PromptCollection();
            const promptOrder = this.getPromptOrderForCharacter(this.activeCharacter);
            promptOrder.forEach(entry => {
                const prompt = this.getPromptById(entry.identifier);
                const allowedTrigger = entry.enabled && this.shouldTrigger(prompt, generationType);
                if (!prompt) return;
                if (allowedTrigger) {
                    promptCollection.add(this.preparePrompt(prompt));
                } else if (entry.identifier === 'main') {
                    const replacementPrompt = structuredClone(prompt);
                    replacementPrompt.content = '';
                    promptCollection.add(this.preparePrompt(replacementPrompt));
                }
            });
            return promptCollection;
        },
    };

    const power_user = {
        persona_description: body.personaDescription ?? '',
        persona_description_position: body.personaInPrompt ? persona_description_positions.IN_PROMPT : persona_description_positions.NONE,
    };

    return { substituteParams, oai_settings, formatWorldInfo, getPromptPosition, getPromptRole, promptManager, power_user };
}

const fn = extractFunction('preparePromptsForChatCompletion');

const runCase = new Function('request', 'makeStubs', [
    'const extension_prompt_types = { NONE: -1, IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };',
    'const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };',
    'const persona_description_positions = { IN_PROMPT: 0, AFTER_CHAR: 1, TOP_AN: 2, BOTTOM_AN: 3, AT_DEPTH: 4, NONE: 9 };',
    'const { substituteParams, oai_settings, formatWorldInfo, getPromptPosition, getPromptRole, promptManager, power_user } = makeStubs(request);',
    'const promptManagerLocal = promptManager;',
    fn,
    'return (async () => {',
    '    const prompts = await preparePromptsForChatCompletion({',
    '        scenario: request.body.scenario,',
    '        charPersonality: request.body.charPersonality,',
    '        name2: request.body.name2,',
    '        worldInfoBefore: request.body.worldInfoBefore,',
    '        worldInfoAfter: request.body.worldInfoAfter,',
    '        charDescription: request.body.charDescription,',
    '        quietPrompt: request.body.quietPrompt,',
    '        bias: request.body.bias,',
    '        extensionPrompts: request.body.extensionPrompts ?? {},',
    '        systemPromptOverride: request.body.systemPromptOverride,',
    '        jailbreakPromptOverride: request.body.jailbreakPromptOverride,',
    '        type: request.body.type,',
    '    });',
    '    return {',
    '        collection: prompts.collection.map(p => ({',
    '            identifier: p.identifier ?? null,',
    '            name: p.name ?? null,',
    '            role: p.role ?? null,',
    '            content: p.content ?? null,',
    '            system_prompt: p.system_prompt ?? null,',
    '            marker: p.marker ?? null,',
    '            enabled: p.enabled ?? null,',
    '            injection_position: p.injection_position ?? null,',
    '            injection_depth: p.injection_depth ?? null,',
    '            injection_order: p.injection_order ?? null,',
    '            injection_trigger: p.injection_trigger ?? [],',
    '            forbid_overrides: p.forbid_overrides ?? null,',
    '            position: p.position ?? null,',
    '            extension: p.extension ?? null,',
    '        })),',
    '        overriddenPrompts: prompts.overriddenPrompts,',
    '    };',
    '})();',
].join('\n'));

const cases = [];
async function add(id, body) {
    const result = await runCase({ body }, makeStubs);
    cases.push({ id, args: { body }, expected: result });
}

const base = {
    scenario: '场景',
    charPersonality: '性格',
    name2: '角色名',
    worldInfoBefore: 'WI前',
    worldInfoAfter: 'WI后',
    charDescription: '描述',
    quietPrompt: '静默',
    bias: '偏向',
    extensionPrompts: {},
    systemPromptOverride: '',
    jailbreakPromptOverride: '',
    type: 'normal',
    env: { user: '用户', char: '角色名' },
    oai: {},
};

await add('defaults', { ...structuredClone(base) });

await add('known-extensions', {
    ...structuredClone(base),
    extensionPrompts: {
        '1_memory': { value: '记忆摘要', role: 1, position: 2 },
        '2_floating_prompt': { value: '作者注', role: 2, position: 0 },
        '3_vectors': { value: '向量记忆', role: 0, position: 2 },
        '4_vectors_data_bank': { value: '资料库', role: 1, position: 0 },
        chromadb: { value: '智能上下文', role: 0, position: 2 },
    },
    personaDescription: '人设描述',
    personaInPrompt: true,
    impersonationPrompt: '扮演提示',
    quietPrompt: '静默提示',
    oai: {
        scenario_format: '场景格式:{{scenario}}',
        personality_format: '性格格式:{{personality}}',
        group_nudge_prompt: '群聊提示:{{char}}',
        impersonation_prompt: '扮演:{{user}}',
        wi_format: '[{0}]',
    },
});

await add('unknown-extensions', {
    ...structuredClone(base),
    extensionPrompts: {
        my_ext: { value: '扩展A', role: 2, position: 0 },
        'other!key': { value: '扩展B', role: 0, position: 2 },
        skipped_chat: { value: '不应出现', role: 0, position: 1 },
        empty_ext: { value: '', role: 0, position: 2 },
    },
});

await add('role-depth-overrides', {
    ...structuredClone(base),
    userOrder: [
        { identifier: 'main', enabled: true, injection_position: 1, injection_depth: 6, injection_order: 42 },
        { identifier: 'worldInfoBefore', enabled: true, injection_position: 0, injection_depth: 3, injection_order: 7, role: 'user' },
        { identifier: 'worldInfoAfter', enabled: true },
        { identifier: 'jailbreak', enabled: true },
    ],
    userPrompts: [
        { identifier: 'main', name: '主提示', role: 'system', content: '主内容{{user}}', system_prompt: true, enabled: true },
        { identifier: 'worldInfoBefore', name: 'WI前', role: 'system', content: '', system_prompt: true, marker: true, enabled: true },
        { identifier: 'worldInfoAfter', name: 'WI后', role: 'system', content: '', system_prompt: true, marker: true, enabled: true },
        { identifier: 'jailbreak', name: '结语', role: 'system', content: '', system_prompt: true, enabled: true },
    ],
    extensionPrompts: {
        '1_memory': { value: '摘要{{char}}', role: 1, position: 2 },
    },
});

await add('overrides', {
    ...structuredClone(base),
    systemPromptOverride: '覆盖主提示:{{original}}',
    jailbreakPromptOverride: '覆盖结语:{{user}}',
    userOrder: [
        { identifier: 'main', enabled: true },
        { identifier: 'jailbreak', enabled: true },
    ],
    userPrompts: [
        { identifier: 'main', name: '主提示', role: 'system', content: '原主内容', system_prompt: true, enabled: true },
        { identifier: 'jailbreak', name: '结语', role: 'system', content: '原结语', system_prompt: true, enabled: true },
    ],
});

await add('forbid-and-disabled', {
    ...structuredClone(base),
    systemPromptOverride: '不应覆盖',
    jailbreakPromptOverride: '可覆盖',
    userOrder: [
        { identifier: 'main', enabled: false },
        { identifier: 'jailbreak', enabled: true },
    ],
    userPrompts: [
        { identifier: 'main', name: '主提示', role: 'system', content: '原主内容', system_prompt: true, enabled: true, forbid_overrides: true },
        { identifier: 'jailbreak', name: '结语', role: 'system', content: '原结语', system_prompt: true, enabled: true },
    ],
});

await add('trigger-filter', {
    ...structuredClone(base),
    type: 'quiet',
    userOrder: [
        { identifier: 'main', enabled: true },
        { identifier: 'nsfw', enabled: true },
        { identifier: 'jailbreak', enabled: true },
    ],
    userPrompts: [
        { identifier: 'main', name: '主提示', role: 'system', content: '主内容', system_prompt: true, enabled: true },
        { identifier: 'nsfw', name: '附加', role: 'system', content: '附加内容', system_prompt: true, enabled: true, injection_trigger: ['normal'] },
        { identifier: 'jailbreak', name: '结语', role: 'system', content: '结语内容', system_prompt: true, enabled: true },
    ],
});

writeFileSync(outFile, JSON.stringify({ source: 'public/scripts/openai.js preparePromptsForChatCompletion', cases }, null, 2));
console.log('prepare-prompts:', cases.length, 'cases ->', outFile);
