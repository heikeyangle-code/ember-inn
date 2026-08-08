#!/usr/bin/env node
// Gemini 请求体构造（sendMakerSuiteRequest getGeminiBody）→ JSON fixture。
// 逐字提取官方 getGeminiBody；convertGooglePrompt 用官方真函数（逐字提取），
// getPromptNames/calculateGoogleBudgetTokens/GEMINI_SAFETY/VERTEX_SAFETY/useVertexAi 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'gemini-body.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'backends', 'chat-completions.js'), 'utf8');
const pcSrc = readFileSync(join(officialRef, 'src', 'prompt-converters.js'), 'utf8');

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

const fnStart = src.indexOf('function getGeminiBody() {');
if (fnStart < 0) throw new Error('getGeminiBody not found');
const fnBodyStart = src.indexOf('{', fnStart);
const fnEnd = scanBody(src, fnBodyStart);
const start = src.indexOf('const model = String(request.body.model);');
if (start < 0 || start > fnStart) throw new Error('model start not found');
const fn = src.slice(start, fnEnd + 1);

// 逐字提取 convertGooglePrompt + GEMINI_MEDIA_RESOLUTION
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
const googleFn = extractFn('convertGooglePrompt').replace(/^export /, '');
const resStart = pcSrc.indexOf('const GEMINI_MEDIA_RESOLUTION = {');
const resEnd = pcSrc.indexOf('};', resStart) + 2;
const geminiRes = pcSrc.slice(resStart, resEnd);

const stub = [
    googleFn,
    geminiRes,
    'const PROMPT_PLACEHOLDER = request.body.promptPlaceholder ?? "Let\'s get started.";',
    'const enableThoughtSignatures = request.body.enableThoughtSignatures ?? true;',
    'const tryParse = (str) => { try { return JSON.parse(str); } catch { return undefined; } };',
    'const getPromptNames = () => {',
    '    const n = request.body.names ?? {};',
    '    return {',
    '        userName: String(n.userName ?? \'\'),',
    '        charName: String(n.charName ?? \'\'),',
    '        startsWithGroupName: (message) => Array.isArray(n.groupNames) && n.groupNames.some(name => message.startsWith(name + \': \')),',
    '    };',
    '};',
    'const calculateGoogleBudgetTokens = () => request.body.reasoningBudget ?? 0;',
    'const GEMINI_SAFETY = [];',
    'const VERTEX_SAFETY = [];',
    'const useVertexAi = false;',
].join('\n');

const runCase = new Function('request', stub + '\n' + fn + '\nreturn getGeminiBody();');

const cases = [];
function add(id, body) {
    const argsBody = JSON.parse(JSON.stringify(body));
    const result = runCase({ body });
    cases.push({ id, args: { body: argsBody }, expected: result });
}

const base = {
    model: 'gemini-3.6-flash',
    stream: false,
    enable_web_search: false,
    request_images: false,
    reasoning_effort: '',
    include_reasoning: false,
    request_image_aspect_ratio: '',
    request_image_resolution: '',
    use_sysprompt: true,
    max_tokens: 512,
    temperature: 1.0,
    top_p: 1.0,
    top_k: undefined,
    stop: [],
    seed: undefined,
    messages: [{ role: 'user', content: 'hi' }],
};
add('basic', base);
add('sysprompt', { ...base, messages: [{ role: 'system', content: '你是助手' }, { role: 'user', content: 'hi' }] });
add('stop-seed', { ...base, stop: ['END'], seed: 42 });
add('thinking-budget', { ...base, model: 'gemini-3-pro', reasoningBudget: 2048, include_reasoning: true, reasoning_effort: 'high' });
add('thinking-level', { ...base, model: 'gemini-2.5-flash', reasoningBudget: 'low', include_reasoning: true });
add('tools', {
    ...base,
    tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }],
    tool_choice: 'auto',
});
add('tool-choice-specific', {
    ...base,
    tools: [{ type: 'function', function: { name: 'f', description: 'd', parameters: undefined } }],
    tool_choice: { function: { name: 'f' } },
});
add('web-search', { ...base, enable_web_search: true });
add('image-modality', { ...base, model: 'gemini-3-pro-image-preview', request_images: true, request_image_resolution: '1024x1024', request_image_aspect_ratio: '1:1' });
add('no-sysprompt-gemma', { ...base, model: 'gemma-3-27b', messages: [{ role: 'system', content: 'x' }, { role: 'user', content: 'hi' }] });
add('json-schema', { ...base, json_schema: { value: { type: 'object' } } });
add('media-image', { ...base, messages: [{ role: 'user', content: [{ type: 'text', text: '看图' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,CCCC' } }] }] });
add('media-gemini3-resolution', { ...base, model: 'gemini-3-pro', messages: [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,DDDD', detail: 'high' } }] }] });
add('signature-gemini3', { ...base, model: 'gemini-3-pro', messages: [{ role: 'assistant', content: 'Hello', signature: 'sig-1' }] });
add('name-prefix', { ...base, messages: [{ role: 'user', name: 'NPC', content: 'hi' }] });
add('tool-calls-google', { ...base, messages: [{ role: 'user', content: 'search' }, { role: 'assistant', tool_calls: [{ id: 'tc1', function: { name: 'search', arguments: '{"q":"cats"}' } }] }, { role: 'tool', content: '结果', tool_call_id: 'tc1' }] });

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js sendMakerSuiteRequest getGeminiBody（真 convertGooglePrompt）', cases }, null, 2));
console.log('gemini-body:', cases.length, 'cases ->', outFile);
