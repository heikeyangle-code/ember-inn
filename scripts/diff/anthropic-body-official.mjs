#!/usr/bin/env node
// Anthropic 请求体构造（sendClaudeRequest）→ JSON fixture。
// 逐字提取官方 requestBody 构造段；convertClaudeMessages 用官方真函数（逐字提取），
// getPromptNames/calculateClaudeBudgetTokens/cachingAtDepthForClaude/flattenSchema/cacheTTL/color 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'anthropic-body.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'backends', 'chat-completions.js'), 'utf8');
const pcSrc = readFileSync(join(officialRef, 'src', 'prompt-converters.js'), 'utf8');

const start = src.indexOf('const additionalHeaders = {};');
const end = src.indexOf("const generateResponse = await fetch(apiUrl + '/messages', {");
if (start < 0 || end < 0 || end <= start) throw new Error('markers not found');
const segment = src.slice(start, end);

// 逐字提取 convertClaudeMessages
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
const claudeFn = extractFn('convertClaudeMessages').replace(/^export /, '');

const stub = [
    claudeFn,
    'const PROMPT_PLACEHOLDER = request.body.promptPlaceholder ?? "Let\'s get started.";',
    'const getPromptNames = () => {',
    '    const n = request.body.names ?? {};',
    '    return {',
    '        userName: String(n.userName ?? \'\'),',
    '        charName: String(n.charName ?? \'\'),',
    '        startsWithGroupName: (message) => Array.isArray(n.groupNames) && n.groupNames.some(name => message.startsWith(name + \': \')),',
    '    };',
    '};',
    'const calculateClaudeBudgetTokens = () => request.body.reasoningBudget ?? 1024;',
    'const cachingAtDepthForClaude = () => {};',
    'const flattenSchema = (p) => p;',
    'const enableSystemPromptCache = request.body.enableSystemPromptCache ?? false;',
    'const cachingAtDepth = request.body.cachingAtDepth ?? -1;',
    'const enableAdaptiveThinking = request.body.enableAdaptiveThinking ?? true;',
    'const cacheTTL = request.body.cacheTTL ?? \'5m\';',
    'const color = { yellow: s => s, blue: s => s, red: s => s };',
].join('\n');

const fnBody = stub + '\n' + segment + '\nreturn { body: requestBody, betaHeaders };\n';
const runCase = new Function('request', fnBody);

const cases = [];
function add(id, body) {
    // 官方 convertClaudeMessages 会改消息对象，args 保留克隆
    const argsBody = JSON.parse(JSON.stringify(body));
    const result = runCase({ body });
    cases.push({ id, args: { body: argsBody }, expected: result });
}

const base = {
    model: 'claude-sonnet-5',
    max_tokens: 512,
    temperature: 1.0,
    top_p: 1.0,
    top_k: 0,
    stream: false,
    stop: [],
    use_sysprompt: false,
    messages: [{ role: 'user', content: 'hi' }],
};
add('basic', base);
add('sysprompt', { ...base, use_sysprompt: true, messages: [{ role: 'system', content: '你是助手' }, { role: 'user', content: 'hi' }] });
add('thinking-adaptive', { ...base, model: 'claude-sonnet-4-6', reasoningBudget: 'low', include_reasoning: true });
add('thinking-budget', { ...base, model: 'claude-sonnet-4-6', reasoningBudget: 1024 });
add('no-sampling', { ...base, model: 'claude-opus-4-7', reasoningBudget: 'low', include_reasoning: true });
add('limited-sampling', { ...base, model: 'claude-sonnet-4-5', top_p: 0.9 });
add('tools', { ...base, tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }], tool_choice: 'auto' });
add('web-search', { ...base, model: 'claude-sonnet-4-6', enable_web_search: true });
add('verbosity', { ...base, model: 'claude-opus-4-6', verbosity: 'low' });
add('no-prefill-assistant-last', { ...base, model: 'claude-opus-4-6', messages: [{ role: 'assistant', content: '最后是助手' }] });
add('cache-enabled', { ...base, use_sysprompt: true, messages: [{ role: 'system', content: '缓存' }, { role: 'user', content: 'hi' }], enableSystemPromptCache: true, cachingAtDepth: 1 });
add('json-schema', { ...base, json_schema: { name: 'j', description: 'json', value: { type: 'object' } } });
add('prefill', { ...base, assistant_prefill: 'Sure, I will ', messages: [{ role: 'user', content: 'hi' }] });
add('media', { ...base, messages: [{ role: 'user', content: [{ type: 'text', text: '看图' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,AAAA' } }] }] });
add('name-prefix', { ...base, messages: [{ role: 'user', name: 'Alice', content: 'hello' }] });
add('assistant-image-last', { ...base, messages: [{ role: 'assistant', content: [{ type: 'text', text: '图' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,BBBB' } }] }] });
add('tools-no-tools-conversion', { ...base, messages: [{ role: 'user', content: 'search' }, { role: 'assistant', tool_calls: [{ id: 'tc1', function: { name: 'f', arguments: '{"q":1}' } }] }, { role: 'tool', content: '结果', tool_call_id: 'tc1' }] });

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js sendClaudeRequest requestBody（真 convertClaudeMessages）', cases }, null, 2));
console.log('anthropic-body:', cases.length, 'cases ->', outFile);
