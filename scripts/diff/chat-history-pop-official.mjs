#!/usr/bin/env node
// populateChatHistory（openai.js）→ JSON fixture。
// 逐字提取官方函数；Message/PromptManager/ChatCompletion/ToolManager/设置 打桩，
// 输出重建后的最终集合结构（集合名 + 消息序列），与 Kotlin ChatCompletion.entries 对比。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'chat-history-pop.json');

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
        if (ch === '/') {
            let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--;
            const prevSig = source[j];
            if (prevSig === '=' || prevSig === '>' || prevSig === '(' || prevSig === ',' || prevSig === ':' || prevSig === '[') { inRegex = true; continue; }
        }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

const start = src.indexOf('async function populateChatHistory(');
if (start < 0) throw new Error('not found');
const parenStart = src.indexOf('(', start);
let depth = 0, bodyStart = -1, inString = null;
for (let i = parenStart; i < src.length; i++) {
    const ch = src[i];
    if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
    if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
    if (ch === '(') depth++;
    else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(src[j])) j++; if (src[j] === '{') bodyStart = j; break; } }
}
const fn = src.slice(start, scanBody(src, bodyStart) + 1);

const stub = [
    'const selected_group = request.body.selected_group ?? false;',
    'const oai_settings = {',
    '    new_chat_prompt: request.body.new_chat_prompt ?? \'\',',
    '    new_group_chat_prompt: request.body.new_group_chat_prompt ?? \'\',',
    '    send_if_empty: request.body.send_if_empty ?? \'\',',
    '    continue_prefill: request.body.continue_prefill ?? false,',
    '    continue_nudge_prompt: request.body.continue_nudge_prompt ?? \'[Continue your last message without repeating its original content.]\',',
    '};',
    'class Prompt { constructor(p) { Object.assign(this, p); } }',
    'const prompts = {',
    '    has: (id) => id === \'chatHistory\' || (request.body.selected_group && id === \'groupNudge\'),',
    '    get: (id) => id === \'groupNudge\' ? { role: \'system\', content: request.body.groupNudgeContent ?? \'\', identifier: \'groupNudge\' } : undefined,',
    '    index: () => 0,',
    '};',
    'const promptManager = {',
    '    preparePrompt: (p) => ({ role: p.role, content: String(p.content ?? \'\').replace(\'{{char}}\', request.body.char ?? \'\').replace(\'{{user}}\', request.body.user ?? \'\'), name: p.name, identifier: p.identifier }),',
    '    serviceSettings: { names_behavior: 0 },',
    '    isValidName: () => true,',
    '    sanitizeName: (n) => n,',
    '};',
    'const Message = {',
    '    async createAsync(role, content, identifier) { return { role, content: String(content ?? \'\'), identifier, tokens: String(content ?? \'\').length }; },',
    '    async fromPromptAsync(p) { return { role: p.role, content: String(p.content ?? \'\'), name: p.name, identifier: p.identifier ?? \'\', tokens: String(p.content ?? \'\').length }; },',
    '};',
    'const ToolManager = { isToolCallingSupported: () => false };',
    'const character_names_behavior = { NONE: -1, DEFAULT: 0, COMPLETION: 1, CONTENT: 2 };',
    'const tool_reasoning_modes = { DISABLED: \'disabled\' };',
    'const interleaved_reasoning_providers = [];',
    'const getEffectiveToolReasoningMode = () => \'disabled\';',
    'const isImageInliningSupported = () => false;',
    'const isVideoInliningSupported = () => false;',
    'const isAudioInliningSupported = () => false;',
    'const isReasoningSignatureSupported = () => false;',
    'const MEDIA_TYPE = { IMAGE: \'image\', VIDEO: \'video\', AUDIO: \'audio\' };',
    'const MEDIA_DISPLAY = { LIST: \'list\', GALLERY: \'gallery\' };',
    'const substituteParams = (t) => String(t);',
    'const substituteParamsExtended = (template, vars) => {',
    '    let out = String(template);',
    '    for (const [k, v] of Object.entries(vars)) out = out.split(\'{{\' + k + \'}}\').join(String(v));',
    '    return out;',
    '};',
    'class MsgCollection {',
    '    constructor(name) { this.name = name; this.msgs = []; }',
    '    add(m) { this.msgs.push({ role: m.role, content: m.content, identifier: m.identifier ?? \'\' }); }',
    '}',
    'const MessageCollection = MsgCollection;',
    'const chatCompletion = {',
    '    collections: [],',
    '    _find(name) { return this.collections.find(c => c.name === name); },',
    '    add(collection) { this.collections.push(collection); },',
    '    reserveBudget() {},',
    '    freeBudget() {},',
    '    canAfford() { return true; },',
    '    canAffordAll() { return true; },',
    '    insert(m, c) { this._find(c)?.msgs.push({ role: m.role, content: m.content, identifier: m.identifier ?? \'\' }); },',
    '    insertAtStart(m, c) { this._find(c)?.msgs.unshift({ role: m.role, content: m.content, identifier: m.identifier ?? \'\' }); },',
    '    insertAtEnd(m, c) { this._find(c)?.msgs.push({ role: m.role, content: m.content, identifier: m.identifier ?? \'\' }); },',
    '};',
].join('\n');

const runCase = new Function('request', stub + '\n' + fn + '\n' + [
    "return (async () => {",
    "    const b = request.body;",
    "    await populateChatHistory(b.messages ?? [], prompts, chatCompletion, b.type, b.cyclePrompt);",
    "    return chatCompletion.collections.map(c => ({ name: c.name, msgs: c.msgs }));",
    "})();",
].join('\n'));

const cases = [];
async function add(id, body) {
    // 官方函数会 splice 传入的 messages（continue 分支），用深拷贝跑，保留原始输入
    const result = await runCase({ body: structuredClone(body) });
    cases.push({ id, args: { body }, expected: result });
}

const base = {
    messages: [
        { role: 'user', content: '你好', name: 'User', injected: false },
        { role: 'assistant', content: '回应', name: 'Char', injected: false },
        { role: 'user', content: '{{char}}再说', name: 'User', injected: false },
    ],
    new_chat_prompt: '【新会话】',
};
await add('basic', base);
await add('empty-user-after-assistant', {
    ...base,
    messages: [...base.messages, { role: 'assistant', content: '最后助手', name: 'Char', injected: false }],
    send_if_empty: '（空）',
});
await add('continue-nudge', {
    ...base,
    type: 'continue',
    cyclePrompt: '上一条',
    continue_nudge_prompt: '[继续：{{lastChatMessage}}]',
});
await add('group-nudge', {
    ...base,
    selected_group: true,
    new_group_chat_prompt: '【群聊】',
    groupNudgeContent: '【群聊提示】',
    messages: [{ role: 'user', content: '群聊', name: 'A', injected: false }],
});
await add('injected-message-skipped-for-continue', {
    ...base,
    type: 'continue',
    cyclePrompt: 'C',
    messages: [
        { role: 'user', content: '注入的', name: 'X', injected: true },
        { role: 'assistant', content: '最后', name: 'Char', injected: false },
    ],
});

writeFileSync(outFile, JSON.stringify({ source: 'openai.js populateChatHistory', cases }, null, 2));
console.log('chat-history-pop:', cases.length, 'cases ->', outFile);
