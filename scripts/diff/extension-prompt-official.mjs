#!/usr/bin/env node
// script.js setExtensionPrompt/getExtensionPrompt/getExtensionPromptByName +
// slash-commands.js injectCallback（3778-3824）纯逻辑 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩登记：filter 闭包恒无（App 不支持 /inject filter，见 HANDOFF 3.4）；ephemeral 生命周期不在此；
// 随机 id 固定为 'random-fixed'；isTrueBoolean 用官方布尔语义；substituteParams 注入为 substitute 桩。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'extension-prompt.json');

const funcs = `
const extension_prompt_types = { NONE: -1, IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };
const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
const SCRIPT_PROMPT_KEY = 'script_inject_';
const isTrueBoolean = (v) => ['true', 'on', '1', 'yes', 'y'].includes(String(v).toLowerCase());
function injectParse(idRaw, valueRaw, positionRaw, depthRaw, roleRaw, scanRaw) {
    const positions = {
        'before': extension_prompt_types.BEFORE_PROMPT,
        'after': extension_prompt_types.IN_PROMPT,
        'chat': extension_prompt_types.IN_CHAT,
        'none': extension_prompt_types.NONE,
    };
    const roles = {
        'system': extension_prompt_roles.SYSTEM,
        'user': extension_prompt_roles.USER,
        'assistant': extension_prompt_roles.ASSISTANT,
    };
    const id = String(idRaw ?? '') || 'random-fixed';
    const defaultPosition = 'after';
    const defaultDepth = 4;
    const positionValue = positionRaw ?? defaultPosition;
    const position = positions[positionValue] ?? positions[defaultPosition];
    const depthValue = Number(depthRaw ?? defaultDepth);
    const depth = isNaN(depthValue) ? defaultDepth : depthValue;
    const roleValue = typeof roleRaw === 'string' ? roleRaw.toLowerCase().trim() : Number(roleRaw ?? extension_prompt_roles.SYSTEM);
    const role = roles[roleValue] ?? extension_prompt_roles.SYSTEM;
    const scan = isTrueBoolean(String(scanRaw));
    const value = valueRaw || '';
    const prefixedId = SCRIPT_PROMPT_KEY + id;
    return { id, value, prefixedId, position, depth, scan, role };
}
function setExtensionPrompt(store, key, value, position, depth, scan = false, role = extension_prompt_roles.SYSTEM) {
    store[key] = {
        value: String(value),
        position: Number(position),
        depth: Number(depth),
        scan: !!scan,
        role: Number(role ?? extension_prompt_roles.SYSTEM),
    };
}
function getExtensionPrompt(extension_prompts, position = extension_prompt_types.IN_PROMPT, depth = undefined, separator = '\\n', role = undefined, wrap = true, substitute = (x) => x) {
    const prompts = Object.keys(extension_prompts)
        .sort()
        .map((x) => extension_prompts[x])
        .filter((x) => x.position == position && x.value)
        .filter((x) => depth === undefined || x.depth === undefined || x.depth === depth)
        .filter((x) => role === undefined || x.role === undefined || x.role === role);
    let values = prompts.map((x) => x.value.trim()).join(separator);
    if (wrap && values.length && !values.startsWith(separator)) values = separator + values;
    if (wrap && values.length && !values.endsWith(separator)) values = values + separator;
    if (values.length) values = substitute(values);
    return values;
}
function getExtensionPromptByName(extension_prompts, moduleName, substitute = (x) => x) {
    if (!moduleName) return '';
    const prompt = extension_prompts[moduleName];
    if (!prompt) return '';
    return substitute(prompt.value);
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    if (b.mode === "inject") {',
    '        return injectParse(b.id, b.value, b.position, b.depth, b.role, b.scan);',
    '    }',
    '    if (b.mode === "set-get") {',
    '        const store = {};',
    '        for (const item of b.entries) {',
    '            setExtensionPrompt(store, item.key, item.value, item.position, item.depth, item.scan, item.role);',
    '        }',
    '        return getExtensionPrompt(store, b.position, b.depth, b.separator, b.role, b.wrap, b.substitute);',
    '    }',
    '    if (b.mode === "by-name") {',
    '        const store = {};',
    '        for (const item of b.entries) {',
    '            setExtensionPrompt(store, item.key, item.value, item.position, item.depth, item.scan, item.role);',
    '        }',
    '        return getExtensionPromptByName(store, b.key, b.substitute);',
    '    }',
    '    return null;',
    '};',
].join('\n'));

const substitute = (x) => String(x).replaceAll('{{user}}', 'User').replaceAll('{{char}}', 'Char');

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

// injectCallback 映射
await add('inject-defaults', { mode: 'inject', id: 'abc', value: '注意脚下', position: undefined, depth: undefined, role: undefined, scan: undefined });
await add('inject-before-user', { mode: 'inject', id: 'b1', value: 'before text', position: 'before', depth: 2, role: 'user', scan: true });
await add('inject-after-assistant', { mode: 'inject', id: 'a1', value: 'after text', position: 'after', depth: '3', role: 'assistant', scan: 'on' });
await add('inject-chat-system', { mode: 'inject', id: 'c1', value: 'chat text', position: 'chat', depth: 0, role: 'system', scan: false });
await add('inject-none', { mode: 'inject', id: 'n1', value: 'none text', position: 'none', depth: 7, role: 'system', scan: '1' });
await add('inject-unknown-position', { mode: 'inject', id: 'u1', value: 'fallback', position: 'sideways', depth: 4, role: 'system', scan: false });
await add('inject-nan-depth', { mode: 'inject', id: 'd1', value: 'bad depth', position: 'after', depth: 'abc', role: 'system', scan: false });
await add('inject-empty-value', { mode: 'inject', id: 'e1', value: '', position: 'before', depth: 4, role: 'system', scan: false });
await add('inject-role-number', { mode: 'inject', id: 'r1', value: 'numeric role', position: 'chat', depth: 4, role: 1, scan: false });
await add('inject-case-role', { mode: 'inject', id: 'r2', value: 'case role', position: 'before', depth: 4, role: '  Assistant ', scan: false });

// setExtensionPrompt + getExtensionPrompt
const entriesA = [
    { key: 'z_last', value: 'Z', position: 1, depth: 2, scan: false, role: 0 },
    { key: 'a_first', value: 'A', position: 1, depth: 2, scan: false, role: 0 },
    { key: 'm_mid', value: 'M', position: 1, depth: 1, scan: false, role: 0 },
    { key: 'empty', value: '  ', position: 1, depth: 2, scan: false, role: 0 },
];
await add('get-in-chat-sorted', { mode: 'set-get', entries: entriesA, position: 1, depth: 2, separator: '\n', role: 0, wrap: true, substitute });
await add('get-in-chat-no-depth', { mode: 'set-get', entries: entriesA, position: 1, depth: undefined, separator: '\n', role: undefined, wrap: true, substitute });
await add('get-no-wrap', { mode: 'set-get', entries: entriesA, position: 1, depth: 2, separator: '\n', role: 0, wrap: false, substitute });

const entriesB = [
    { key: 'k1', value: ' one ', position: 0, depth: 4, scan: false, role: 0 },
    { key: 'k2', value: ' two ', position: 0, depth: 4, scan: true, role: 0 },
    { key: 'k3', value: ' three ', position: 2, depth: 4, scan: false, role: 0 },
];
await add('get-in-prompt', { mode: 'set-get', entries: entriesB, position: 0, depth: 4, separator: '\n', role: 0, wrap: true, substitute });
await add('get-before-prompt', { mode: 'set-get', entries: entriesB, position: 2, depth: undefined, separator: '\n', role: undefined, wrap: true, substitute });
await add('get-role-filter', { mode: 'set-get', entries: entriesB, position: 0, depth: 4, separator: '\n', role: 1, wrap: true, substitute });

// getExtensionPromptByName（scan 注入语义）
await add('by-name-found', { mode: 'by-name', entries: entriesB, key: 'k2', substitute });
await add('by-name-missing', { mode: 'by-name', entries: entriesB, key: 'nope', substitute });
await add('by-name-empty-key', { mode: 'by-name', entries: entriesB, key: '', substitute });

writeFileSync(outFile, JSON.stringify({ source: 'script.js setExtensionPrompt/getExtensionPrompt + slash-commands.js injectCallback', cases }, null, 2));
console.log('extension-prompt:', cases.length, 'cases ->', outFile);
