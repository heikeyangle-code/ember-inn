#!/usr/bin/env node
// OpenAI 文本补全请求体（sendChatCompletionRequest 的 isTextCompletion 分支）→ JSON fixture。
// 逐字提取官方 requestBody 构造段；convertTextCompletionPrompt 用官方真函数。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'text-completion-body.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'backends', 'chat-completions.js'), 'utf8');
const pcSrc = readFileSync(join(officialRef, 'src', 'prompt-converters.js'), 'utf8');

const start = src.indexOf("const textPrompt = isTextCompletion ? convertTextCompletionPrompt(request.body.messages) : '';");
const end = src.indexOf('const config = {', start);
if (start < 0 || end < 0 || end <= start) throw new Error('markers not found');
const segment = src.slice(start, end);

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
const convertFn = extractFn('convertTextCompletionPrompt').replace(/^export /, '');

const stub = [
    convertFn,
    'const isTextCompletion = request.body.isTextCompletion;',
    "const apiUrl = request.body.apiUrl ?? 'https://api.openai.com/v1';",
    'const bodyParams = request.body.bodyParams ?? {};',
    'const color = { yellow: s => s, blue: s => s, red: s => s };',
    "const CHAT_COMPLETION_SOURCES = { OPENAI: 'openai', OPENROUTER: 'openrouter', CUSTOM: 'custom' };",
    'const excludeKeysByYaml = () => {};',
    'request.socket = { removeAllListeners() {}, on() {} };',
].join('\n');

const fnBody = stub + '\n' + segment + '\nreturn requestBody;\n';
const runCase = new Function('request', fnBody);

const cases = [];
function add(id, body) {
    const argsBody = JSON.parse(JSON.stringify(body));
    const expected = runCase({ body: argsBody });
    cases.push({ id, args: { body: argsBody }, expected });
}

const base = {
    isTextCompletion: true,
    model: 'gpt-3.5-turbo-instruct',
    messages: [
        { role: 'system', content: '你是助手' },
        { role: 'user', content: 'hi' },
        { role: 'assistant', content: 'hello' },
    ],
    temperature: 1.0,
    max_tokens: 512,
    stream: false,
    presence_penalty: 0,
    frequency_penalty: 0,
    top_p: 1.0,
};
add('basic', base);
add('single-user', { ...base, messages: [{ role: 'user', content: '你好' }] });
add('names-example', {
    ...base,
    names: { userName: 'User', charName: 'Char' },
    messages: [
        { role: 'system', name: 'example_user', content: 'hello' },
        { role: 'system', name: 'example_assistant', content: 'world' },
        { role: 'user', content: 'hi' },
    ],
});
add('tool-messages', {
    ...base,
    messages: [
        { role: 'user', content: 'q' },
        { role: 'assistant', tool_calls: [{ id: 'c1', function: { name: 'f', arguments: '{}' } }] },
        { role: 'tool', content: 'r', tool_call_id: 'c1' },
        { role: 'user', content: 'next' },
    ],
});
add('empty-messages', { ...base, messages: [] });
add('sampler-values', { ...base, temperature: 0.7, top_p: 0.9, max_tokens: 2048, stream: true, presence_penalty: 0.5, frequency_penalty: 1.5 });

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js isTextCompletion requestBody（真 convertTextCompletionPrompt）', cases }, null, 2));
console.log('text-completion-body:', cases.length, 'cases ->', outFile);
