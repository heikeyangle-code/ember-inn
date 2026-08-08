#!/usr/bin/env node
// 特殊协议请求体（sendMistralAIRequest / sendXAIRequest / sendAI21Request / sendCohereRequest）→ JSON fixture。
// 逐字提取官方 requestBody 构造段；convert*Messages 用官方真函数（逐字提取），
// getPromptNames/getConfigValue/crypto 打桩（crypto 用 Node 原生 sha512，与官方一致）。

import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'special-bodies.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'backends', 'chat-completions.js'), 'utf8');
const pcSrc = readFileSync(join(officialRef, 'src', 'prompt-converters.js'), 'utf8');

function extractFn(name) {
    const s = pcSrc.indexOf('export function ' + name);
    if (s < 0) throw new Error(name + ' not found');
    let i = pcSrc.indexOf('{', s);
    let depth = 0, inString = null, inLineComment = false, inBlockComment = false;
    for (; i < pcSrc.length; i++) {
        const ch = pcSrc[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && pcSrc[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && pcSrc[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && pcSrc[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return pcSrc.slice(s, i + 1); }
    }
    throw new Error(name + ' unbalanced');
}

function extractBetween(fnName, startMarker, endMarker, afterFn) {
    const fnStart = src.indexOf('async function ' + fnName);
    if (fnStart < 0) throw new Error(fnName + ' not found');
    const start = src.indexOf(startMarker, fnStart);
    const end = src.indexOf(endMarker, start);
    if (start < 0 || end < 0 || end <= start) throw new Error(fnName + ' markers not found');
    return src.slice(start, end);
}

const stub = [
    'const getPromptNames = () => {',
    '    const n = request.body.names ?? {};',
    '    return {',
    "        userName: String(n.userName ?? ''),",
    "        charName: String(n.charName ?? ''),",
    "        startsWithGroupName: (message) => Array.isArray(n.groupNames) && n.groupNames.some(name => message.startsWith(name + ': ')),",
    '    };',
    '};',
    'const getConfigValue = () => false;',
    'const crypto = { createHash };',
    'request.socket = { removeAllListeners() {}, on() {} };',
    "const PROMPT_PLACEHOLDER = request.body.promptPlaceholder ?? 'Let\\'s get started.';",
].join('\n');

function makeRunner(fnCode, prelude, returnVar = 'requestBody') {
    const code = fnCode + '\n' + stub + '\n' + prelude + '\nreturn ' + returnVar + ';\n';
    return new Function('request', 'createHash', code);
}

const mistralFn = extractFn('convertMistralMessages').replace(/^export /, '');
const mistralSeg = extractBetween('sendMistralAIRequest', 'const requestBody = {', 'const config = {');
const runMistral = makeRunner(mistralFn, 'const messages = convertMistralMessages(request.body.messages, getPromptNames(request));\n' + mistralSeg);

const xaiFn = extractFn('convertXAIMessages').replace(/^export /, '');
const xaiSeg = extractBetween('sendXaiRequest', 'let bodyParams = {};', 'const config = {');
const runXai = makeRunner(xaiFn, xaiSeg);

const ai21Fn = extractFn('convertAI21Messages').replace(/^export /, '');
const ai21Seg = extractBetween('sendAI21Request', 'const bodyParams = {};', 'const options = {');
const runAi21 = makeRunner(ai21Fn, ai21Seg, 'body');

const cohereFn = extractFn('convertCohereMessages').replace(/^export /, '');
const cohereSeg = extractBetween('sendCohereRequest', 'const convertedHistory = convertCohereMessages(request.body.messages, getPromptNames(request));', 'const config = {');
const runCohere = makeRunner(cohereFn, cohereSeg);

const cases = [];
function add(provider, id, body) {
    const argsBody = JSON.parse(JSON.stringify(body));
    const runner = { mistral: runMistral, xai: runXai, ai21: runAi21, cohere: runCohere }[provider];
    let expected;
    try {
        expected = runner({ body: JSON.parse(JSON.stringify(body)) }, createHash);
    } catch (e) {
        console.error('FAIL case:', provider, id, e.message);
        throw e;
    }
    cases.push({ id: provider + '-' + id, args: { provider, body: argsBody }, expected });
}

const base = {
    model: 'm',
    messages: [{ role: 'user', content: 'hi' }],
    temperature: 1.0,
    top_p: 1.0,
    max_tokens: 512,
    stream: false,
    frequency_penalty: 0,
    presence_penalty: 0,
};

// ---- Mistral ----
add('mistral', 'basic', { ...base });
add('mistral', 'names-example', {
    ...base,
    names: { userName: 'User', charName: 'Char' },
    messages: [
        { role: 'system', name: 'example_user', content: 'hello' },
        { role: 'system', name: 'example_assistant', content: 'world' },
        { role: 'user', content: 'hi' },
    ],
});
add('mistral', 'tools', {
    ...base,
    tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }],
    tool_choice: 'auto',
});
add('mistral', 'json-schema', {
    ...base,
    json_schema: { name: 'j', description: 'json', value: { type: 'object' } },
});
add('mistral', 'tool-messages', {
    ...base,
    messages: [
        { role: 'user', content: 'q' },
        { role: 'assistant', tool_calls: [{ id: 'call_1', type: 'function', function: { name: 'f', arguments: '{}' } }] },
        { role: 'tool', content: 'r', tool_call_id: 'call_1' },
        { role: 'user', content: 'next' },
    ],
});
add('mistral', 'assistant-prefix', {
    ...base,
    messages: [{ role: 'assistant', content: '继续' }],
});

// ---- xAI ----
add('xai', 'basic', { ...base });
add('xai', 'effort-auto', { ...base, reasoning_effort: 'auto' });
add('xai', 'effort-high', { ...base, reasoning_effort: 'high' });
add('xai', 'effort-medium', { ...base, reasoning_effort: 'medium' });
add('xai', 'tools', {
    ...base,
    tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }],
    tool_choice: 'required',
});
add('xai', 'json-schema', {
    ...base,
    json_schema: { name: 'j', strict: false, value: { type: 'object' } },
});
add('xai', 'names-example', {
    ...base,
    names: { userName: 'User', charName: 'Char' },
    messages: [
        { role: 'system', name: 'example_user', content: 'hello' },
        { role: 'system', name: 'example_assistant', content: 'world' },
        { role: 'user', content: 'hi' },
    ],
});

// ---- AI21 ----
add('ai21', 'basic', { ...base });
add('ai21', 'json-schema', {
    ...base,
    json_schema: { name: 'j', value: { type: 'object' } },
});
add('ai21', 'tools', {
    ...base,
    tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }],
});
add('ai21', 'names-system', {
    ...base,
    names: { userName: 'User', charName: 'Char' },
    messages: [
        { role: 'system', name: 'example_user', content: 'hello' },
        { role: 'system', name: 'example_assistant', content: 'world' },
        { role: 'user', content: 'hi' },
    ],
});

// ---- Cohere ----
add('cohere', 'basic', { ...base });
add('cohere', 'empty-messages', { ...base, messages: [] });
add('cohere', 'safety-model', { ...base, model: 'command-r-08-2024' });
add('cohere', 'tools-schema', {
    ...base,
    tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object', properties: {}, required: [], '\u0024schema': 'https://json-schema.org/draft/2020-12/schema' } } }],
});
add('cohere', 'json-schema', {
    ...base,
    json_schema: { value: { type: 'object' } },
});
add('cohere', 'tool-calls-primer', {
    ...base,
    messages: [
        { role: 'user', content: 'q' },
        { role: 'assistant', tool_calls: [{ id: 'c1', function: { name: 'f', arguments: '{}' } }] },
    ],
});

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js 特殊协议 requestBody（真 convert*Messages）', cases }, null, 2));
console.log('special-bodies:', cases.length, 'cases ->', outFile);
