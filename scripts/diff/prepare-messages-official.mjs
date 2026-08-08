#!/usr/bin/env node
// 提示词总装整链（prepareOpenAIMessages + populateChatCompletion）→ JSON fixture。
// 逐字提取官方函数：prepareOpenAIMessages / populateChatCompletion / setOpenAIMessageExamples /
// parseExampleIntoIndividual / parseMesExamples / populateChatHistory / populateDialogueExamples。
// 打桩：Message/MessageCollection/ChatCompletion（与 Kotlin 移植一致的顺序语义）、promptManager、
// tokenHandler、ToolManager、populationInjectionPrompts（原样透传，absolute 分支边界）、
// preparePromptsForChatCompletion（用 fixture 注入的 promptCollection，该函数本身另有 7 例差分）。
// dryRun=false + squash_system_messages=true，输出官方 getChat 展平结果。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'prepare-messages.json');

const openaiSrc = readFileSync(join(officialRef, 'public', 'scripts', 'openai.js'), 'utf8');
const scriptSrc = readFileSync(join(officialRef, 'public', 'script.js'), 'utf8');

function extractFn(source, marker) {
    const s = source.indexOf(marker);
    if (s < 0) throw new Error(marker + ' not found');
    // 跳过参数列表（含解构 {}），到匹配的 ) 结束
    let paren = 1, brace = 0, i = s + marker.length; // marker 已含开括号
    let inString = null, inLineComment = false, inBlockComment = false, inRegex = false;
    for (; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '/') { let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--; const prev = source[j]; if (['=', '>', '(', ',', ':', '['].includes(prev)) { inRegex = true; continue; } }
        if (ch === '(') paren++;
        else if (ch === ')') { paren--; if (paren === 0) { i++; break; } }
        else if (ch === '{') brace++;
        else if (ch === '}') brace--;
    }
    while (/\s/.test(source[i])) i++;
    if (source[i] !== '{') throw new Error(marker + ' body not found');
    const bodyStart = i;
    let depth = 0;
    inString = null; inLineComment = false; inBlockComment = false; inRegex = false;
    for (; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '/') { let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--; const prev = source[j]; if (['=', '>', '(', ',', ':', '['].includes(prev)) { inRegex = true; continue; } }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return source.slice(s, i + 1); }
    }
    throw new Error(marker + ' unbalanced');
}

const fns = [
    extractFn(openaiSrc, 'async function prepareOpenAIMessages('),
    extractFn(openaiSrc, 'async function populateChatCompletion('),
    extractFn(openaiSrc, 'function setOpenAIMessageExamples('),
    extractFn(openaiSrc, 'export function parseExampleIntoIndividual('),
    extractFn(openaiSrc, 'async function populateChatHistory('),
    extractFn(openaiSrc, 'async function populateDialogueExamples('),
    extractFn(scriptSrc, 'export function parseMesExamples('),
].map(fn => fn.replace(/^export /, '')).join('\n');

const stub = [
    // ---- 与 Kotlin 移植一致的集合/消息语义 ----
    'class MessageCollection {',
    '    constructor(identifier) { this.identifier = identifier; this.collection = []; }',
    '    add(m) { this.collection.push(m); }',
    '    getTokens() { return this.collection.reduce((s, m) => s + (m.tokens ?? 0), 0); }',
    '}',
    'class Prompt { constructor(p) { Object.assign(this, p); } }',
    'class Message {',
    '    constructor(role, content, name = null, identifier = null) {',
    '        this.role = role; this.content = content; this.name = name; this.identifier = identifier; this.tokens = 0;',
    '    }',
    '    static async fromPromptAsync(p) { if (!p) return null;',
    "        const m = new Message(p.role, p.content ?? '', p.name ?? null, p.identifier ?? null);",
    "        m.tokens = tokenHandler.countAsync(m.content, 'prompt');",
    '        return m;',
    '    }',
    '    async setName(n) { this.name = n; }',
    '    static async createAsync(role, content, identifier) {',
    '        const m = new Message(role, content, null, identifier ?? null);',
    "        m.tokens = tokenHandler.countAsync(content, 'conversation');",
    '        return m;',
    '    }',
    '}',
    'class ChatCompletion {',
    '    constructor() { this.entries = []; this.tokenBudget = 0; this.overriddenPrompts = []; }',
    '    setTokenBudget(context, response) { this.tokenBudget = context - response; }',
    '    enableLogging() {}',
    "    log(...a) { globalThis.console.error(\"CC_LOG\", ...a.map(x => (x instanceof Error ? x.stack : x))); }",
    '    validateMessageCollection() {}',
    '    validateMessage() {}',
    '    checkTokenBudget() {}',
    '    add(collection, position = null) {',
    '        if (position !== null && position !== -1) {',
    '            while (this.entries.length <= position) this.entries.push(null);',
    '            this.entries[position] = collection;',
    '        } else { this.entries.push(collection); }',
    '        this.tokenBudget -= collection.getTokens();',
    '    }',
    "    insert(message, identifier, position = 'end') {",
    '        const idx = this.findMessageIndex(identifier);',
    '        if (idx < 0) return;',
    '        const c = this.entries[idx]?.collection;',
    '        if (!c) return;',
    '        if (message.content || message.tool_calls) {',
    "            if (position === 'start') c.unshift(message);",
    '            else c.push(message);',
    '            this.tokenBudget -= (message.tokens ?? 0);',
    '        }',
    '    }',
    "    insertAtStart(message, identifier) { this.insert(message, identifier, 'start'); }",
    "    insertAtEnd(message, identifier) { this.insert(message, identifier, 'end'); }",
    '    has(identifier) { return this.findMessageIndex(identifier) !== -1; }',
    '    findMessageIndex(identifier) { return this.entries.findIndex(e => e?.identifier === identifier); }',
    "    reserveBudget(x) { this.tokenBudget -= (typeof x === 'number' ? x : (x?.getTokens?.() ?? x?.tokens ?? 0)); }",
    "    freeBudget(x) { this.tokenBudget += (typeof x === 'number' ? x : (x?.getTokens?.() ?? x?.tokens ?? 0)); }",
    '    setOverriddenPrompts(list) { this.overriddenPrompts = list; }',
    '    canAfford() { return true; }',
    '    canAffordAll() { return true; }',
    '    async squashSystemMessages() {',
    "        const exclude = ['newMainChat', 'newChat', 'groupNudge'];",
    '        const flat = this.entries.filter(Boolean).flatMap(e => e.collection);',
    '        const out = [];',
    '        let last = null;',
    '        for (const m of flat) {',
    "            if (m.role === 'system' && !m.content) continue;",
    "            const canSquash = m.role === 'system' && !m.name && !exclude.includes(m.identifier);",
    "            if (canSquash && last && last.role === 'system' && !last.name && !exclude.includes(last.identifier)) {",
    "                last.content += '\\n' + m.content;",
    "                last.tokens = tokenHandler.countAsync(last.content, 'prompt');",
    '            } else { out.push(m); last = m; }',
    '        }',
    "        this.entries = out.map(m => { const c = new MessageCollection(m.identifier ?? \'squashed\'); c.add(m); return c; });",
    '    }',
    '    getChat() {',
    '        return this.entries.filter(Boolean).flatMap(e => e.collection)',
    '            .filter(m => m.content || m.tool_calls)',
    '            .map(m => ({ role: m.role, content: m.content, name: m.name ?? null }));',
    '    }',
    '}',
    // ---- 环境打桩 ----
    'const tokenHandler = {',
    '    counts: {},',
    '    countAsync(text, type) {',
    "        const t = typeof text === 'string' ? text : (text?.content ?? '');",
    '        const n = Math.max(1, Math.ceil(t.length / 4));',
    '        return n;',
    '    },',
    '};',
    'const promptManager = {',
    '    serviceSettings: { openai_max_context: request.body.maxContextTokens ?? 8192, openai_max_tokens: request.body.maxTokens ?? 256 },',
    '    isPromptDisabledForActiveCharacter: () => false,',
    '    sanitizeName: (n) => n,',
    '    log: (...a) => globalThis.console.error("CC_LOG", ...a),',
    "    preparePrompt: (p) => ({ ...p, content: String(p.content ?? '') }),",
    '    setChatCompletion: () => {},',
    '    render: () => {},',
    '    error: null,',
    '    tokenHandler,',
    '};',
    "const power_user = { pin_examples: request.body.pinExamples ?? false, console_log_prompts: false, always_force_name2: false, context: { example_separator: request.body.exampleSeparator ?? '' } };",
    "const main_api = request.body.mainApi ?? 'openai';",
    'const oai_settings = {',
    '    continue_prefill: request.body.continuePrefill ?? false,',
    "    chat_completion_source: request.body.chatCompletionSource ?? 'openai',",
    "    assistant_prefill: request.body.assistantPrefill ?? '',",
    '    names_behavior: request.body.namesBehavior ?? 0,',
    '    squash_system_messages: request.body.squashSystemMessages ?? true,',
    "    new_chat_prompt: request.body.newChatPrompt ?? 'New chat:',",
    "    new_example_chat_prompt: request.body.newExampleChatPrompt ?? 'New chat:',",
    "    send_if_empty: request.body.sendIfEmpty ?? '',",
    "    continue_nudge_prompt: '[Continue your last message without repeating its original content.]',",
    '};',
    'const character_names_behavior = { NONE: -1, DEFAULT: 0, COMPLETION: 1, CONTENT: 2 };',
    'const INJECTION_POSITION = { RELATIVE: 0, ABSOLUTE: 1 };',
    'const ToolManager = { canPerformToolCalls: () => false, isToolCallingSupported: () => false, registerFunctionToolsOpenAI: async () => {} };',
    'const chat_completion_sources = { CLAUDE: \'claude\' };',
    'const populationInjectionPrompts = async (absolutePrompts, messages) => messages;',
    'const isImageInliningSupported = () => false;',
    'const substituteParams = (t) => String(t);',
    'const substituteParamsExtended = (template, vars) => {',
    '    let out = String(template);',
    "    for (const [k, v] of Object.entries(vars || {})) out = out.split('{{' + k + '}}').join(String(v));",
    '    return out;',
    '};',
    'const getGroupNames = () => request.body.groupNames ?? [];',
    "const name1 = request.body.name1 ?? 'User';",
    "const name2 = request.body.name2 ?? 'Char';",
    'const selected_group = request.body.selectedGroup ?? false;',
    'const event_types = {};',
    'const eventSource = { emit: async () => {} };',
    'const t = (s) => String(s);',
    'const toastr = { error: (...a) => globalThis.console.error(...a), warning: () => {} };',
    'const console = { log: () => {}, warn: () => {}, error: (...a) => globalThis.console.error(...a), debug: () => {} };',
    'const TokenBudgetExceededError = class extends Error {};',
    'const InvalidCharacterNameError = class extends Error {};',
    "const tool_reasoning_modes = { DISABLED: 'disabled' };",
    'const interleaved_reasoning_providers = [];',
    "const getEffectiveToolReasoningMode = () => 'disabled';",
    'const isVideoInliningSupported = () => false;',
    'const isAudioInliningSupported = () => false;',
    'const isReasoningSignatureSupported = () => false;',
    "const MEDIA_TYPE = { IMAGE: 'image', VIDEO: 'video', AUDIO: 'audio' };",
    "const MEDIA_DISPLAY = { LIST: 'list', GALLERY: 'gallery' };",
    // ---- preparePromptsForChatCompletion 打桩（注入相同集合；该函数本身另有差分） ----
    'const preparePromptsForChatCompletion = async () => {',
    '    const collection = (request.body.promptCollection ?? []).map(p => ({ ...p }));',
    '    return {',
    '        collection,',
    '        overriddenPrompts: [],',
    '        has: (id) => collection.some(p => p.identifier === id),',
    '        get: (id) => collection.find(p => p.identifier === id),',
    '        index: (id) => collection.findIndex(p => p.identifier === id),',
    '    };',
    '};',
].join('\n');

const runCode = fns + '\n' + stub + '\n' + [
    'return (async () => {',
    '    const b = request.body;',
    "    const mesExamplesArray = parseMesExamples(b.mesExamples ?? '', false);",
    '    const oaiMessageExamples = setOpenAIMessageExamples(mesExamplesArray);',
    "    const messages = (b.messages ?? []).map(m => ({ role: m.role, content: m.content, name: m.name ?? null, identifier: m.identifier ?? 'chatHistory' }));",
    '    const [chat] = await prepareOpenAIMessages({',
    '        name2,',
    "        charDescription: b.charDescription ?? '',",
    "        charPersonality: b.charPersonality ?? '',",
    "        scenario: b.scenario ?? '',",
    "        worldInfoBefore: b.worldInfoBefore ?? '',",
    "        worldInfoAfter: b.worldInfoAfter ?? '',",
    "        bias: b.bias ?? '',",
    "        type: b.type ?? 'generate',",
    "        quietPrompt: b.quietPrompt ?? '',",
    '        quietImage: null,',
    '        extensionPrompts: b.extensionPrompts ?? {},',
    "        cyclePrompt: b.cyclePrompt ?? '',",
    "        systemPromptOverride: b.systemPromptOverride ?? '',",
    "        jailbreakPromptOverride: b.jailbreakPromptOverride ?? '',",
    '        messages,',
    '        messageExamples: oaiMessageExamples,',
    '    }, false);',
    '    return chat;',
    '})();',
].join('\n');
const runCase = new Function('request', runCode);

const cases = [];
async function add(id, body) {
    delete globalThis.__ccEntries; delete globalThis.__ccInserts; delete globalThis.__ccCreate; delete globalThis.__ccGetChat;
    const expected = await runCase({ body: structuredClone(body) });
    cases.push({ id, args: { body }, expected });
}

const base = {
    name2: 'Char',
    name1: 'User',
    charDescription: '描述',
    charPersonality: '性格',
    scenario: '场景',
    worldInfoBefore: '世界书前',
    worldInfoAfter: '世界书后',
    maxContextTokens: 10000,
    maxTokens: 256,
    mesExamples: '<START>\nUser: 你好\nChar: 你好呀',
    messages: [
        { role: 'user', content: '第一条' },
        { role: 'assistant', content: '回复一', name: 'Char' },
        { role: 'user', content: '第二条' },
    ],
    promptCollection: [
        { identifier: 'worldInfoBefore', role: 'system', content: '', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'main', role: 'system', content: 'Write {{char}}\'s next reply in a fictional chat between {{charIfNotGroup}} and {{user}}.', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'worldInfoAfter', role: 'system', content: '', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'charDescription', role: 'system', content: '描述', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'charPersonality', role: 'system', content: '性格', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'scenario', role: 'system', content: '场景', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'personaDescription', role: 'system', content: '', name: null, system_prompt: true, injection_position: 0 },
        { identifier: 'chatHistory', role: 'user', content: '', name: null, system_prompt: false, injection_position: 0 },
        { identifier: 'dialogueExamples', role: 'user', content: '', name: null, system_prompt: false, injection_position: 0 },
    ],
};

await add('basic', { ...base });
await add('persona-an-extension', {
    ...base,
    personaDescription: '人设描述',
    extensionPrompts: { '2_floating_prompt': { identifier: '2_floating_prompt', role: 'system', content: '作者注释', position: 'end' } },
    promptCollection: [...base.promptCollection.map(p => p.identifier === 'personaDescription' ? { ...p, content: '人设描述' } : p),
        { identifier: 'authorsNote', role: 'system', content: '作者注释', name: null, system_prompt: true, injection_position: 0, position: 'end', extension: true }],
});
await add('impersonate-quiet', {
    ...base,
    type: 'impersonate',
    quietPrompt: '安静提示',
    promptCollection: [...base.promptCollection,
        { identifier: 'impersonate', role: 'assistant', content: '冒充内容', name: null, system_prompt: false, injection_position: 0 },
        { identifier: 'quietPrompt', role: 'user', content: '安静提示', name: null, system_prompt: false, injection_position: 0 }],
});
await add('pin-examples', { ...base, pinExamples: true });
await add('continue-prefill-claude', {
    ...base,
    type: 'continue',
    continuePrefill: true,
    assistantPrefill: 'Sure.',
    chatCompletionSource: 'claude',
    messages: [{ role: 'assistant', content: '继续这段', name: 'Char' }],
});

writeFileSync(outFile, JSON.stringify({ source: 'openai.js prepareOpenAIMessages + populateChatCompletion 整链', cases }, null, 2));
console.log('prepare-messages:', cases.length, 'cases ->', outFile);
