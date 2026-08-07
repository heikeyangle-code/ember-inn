#!/usr/bin/env node
// Anthropic 请求体构造（sendClaudeRequest）→ JSON fixture。
// 逐字提取官方 requestBody 构造段；依赖（convertClaudeMessages/getPromptNames/calculateClaudeBudgetTokens/
// flattenSchema/cachingAtDepthForClaude/cacheTTL/color）打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'anthropic-body.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'backends', 'chat-completions.js'), 'utf8');

const start = src.indexOf('const additionalHeaders = {};');
const end = src.indexOf('const generateResponse = await fetch(apiUrl + \'/messages\', {');
if (start < 0 || end < 0 || end <= start) throw new Error('markers not found');
const segment = src.slice(start, end);

const stub = [
    'const convertClaudeMessages = (messages, prefill, useSys, useTools, names) => ({',
    '    messages: structuredClone(request.body.messages ?? []),',
    '    systemPrompt: request.body.systemPrompt ?? [],',
    '});',
    'const getPromptNames = () => [];',
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
    const result = runCase({ body });
    cases.push({ id, args: { body }, expected: result });
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
add('sysprompt', { ...base, use_sysprompt: true, systemPrompt: ['你是助手'] });
add('thinking-adaptive', { ...base, model: 'claude-sonnet-4-6', reasoningBudget: 'low', include_reasoning: true });
add('thinking-budget', { ...base, model: 'claude-sonnet-4-6', reasoningBudget: 1024 });
add('no-sampling', { ...base, model: 'claude-opus-4-7', reasoningBudget: 'low', include_reasoning: true });
add('limited-sampling', { ...base, model: 'claude-sonnet-4-5', top_p: 0.9 });
add('tools', { ...base, tools: [{ type: 'function', function: { name: 'get_weather', description: 'd', parameters: { type: 'object' } } }], tool_choice: 'auto' });
add('web-search', { ...base, model: 'claude-sonnet-4-6', enable_web_search: true });
add('verbosity', { ...base, model: 'claude-opus-4-6', verbosity: 'low' });
add('no-prefill-assistant-last', { ...base, model: 'claude-opus-4-6', messages: [{ role: 'assistant', content: '最后是助手' }] });
add('cache-enabled', { ...base, use_sysprompt: true, systemPrompt: ['缓存'], enableSystemPromptCache: true, cachingAtDepth: 1 });
add('json-schema', { ...base, json_schema: { name: 'j', description: 'json', value: { type: 'object' } } });

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js sendClaudeRequest requestBody', cases }, null, 2));
console.log('anthropic-body:', cases.length, 'cases ->', outFile);
