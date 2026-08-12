#!/usr/bin/env node
// 官方后端 chat-completions.js 的 OpenAI 兼容 requestBody 构造 → fixture。
//
// 打桩登记（与官方行为差异仅限此处，均不影响 body 字段）：
// - URL / API Key / headers（含 OpenRouter Referer/X-Title、ZAI Accept-Language）不参与差分；
// - mergeObjectWithYaml（custom_include/exclude_body/headers）依赖 yaml 配置，未打桩（Ember 无此设置）；
// - getConfigValue/openrouter randomizeUserId 未打桩（Ember 无此配置）；
// - embedOpenRouterMedia / addOpenRouterSignatures / 缓存 / GEMINI_SAFETY 由消息层/extra 处理，不在此 body 构造；
// - pollinations seed 随机打桩为固定 42；
// - POST/转发/错误处理不打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'chat-request-body.json');

const funcs = `
const OPENAI_REASONING_EFFORT_MODELS = ['o1','o3-mini','o3-mini-2025-01-31','o4-mini','o4-mini-2025-04-16','o3','o3-2025-04-16','gpt-5','gpt-5-2025-08-07','gpt-5-mini','gpt-5-mini-2025-08-07','gpt-5-nano','gpt-5-nano-2025-08-07','gpt-5.1','gpt-5.1-2025-11-13','gpt-5.1-chat-latest','gpt-5.2','gpt-5.2-2025-12-11','gpt-5.2-chat-latest','gpt-5.3-chat-latest','gpt-5.4','gpt-5.4-2026-03-05','gpt-5.4-mini','gpt-5.4-mini-2026-03-17','gpt-5.4-nano','gpt-5.4-nano-2026-03-17','gpt-5.5','gpt-5.5-2026-04-23'];
const OPENAI_REASONING_EFFORT_MAP = { min: 'minimal' };
const OPENAI_FIXED_REASONING_EFFORT = { 'gpt-5.3-chat-latest': 'medium' };
const OPENAI_VERBOSITY_MODELS = /^gpt-5/;

function getOpenRouterTransforms(body) {
    switch (body.middleout) {
        case 'on': return ['middle-out'];
        case 'off': return [];
        case 'auto': return undefined;
    }
}

function getOpenRouterPlugins(body) {
    const plugins = [];
    if (body.enable_web_search) plugins.push({ id: 'web' });
    return plugins;
}

function buildOpenAiRequestBody(generate_data) {
    const isTextCompletion = !!generate_data.isTextCompletion;
    let bodyParams = {};
    if (generate_data.chat_completion_source === 'openai') {
        bodyParams.logprobs = generate_data.logprobs;
        bodyParams.top_logprobs = undefined;
        if (!isTextCompletion && bodyParams.logprobs > 0) {
            bodyParams.top_logprobs = bodyParams.logprobs;
            bodyParams.logprobs = true;
        }
    } else if (generate_data.chat_completion_source === 'openrouter') {
        const includeReasoning = Boolean(generate_data.include_reasoning);
        bodyParams = {
            transforms: getOpenRouterTransforms(generate_data),
            plugins: getOpenRouterPlugins(generate_data),
            reasoning: { exclude: !includeReasoning },
        };
        if (generate_data.min_p !== undefined) bodyParams.min_p = generate_data.min_p;
        if (generate_data.top_a !== undefined) bodyParams.top_a = generate_data.top_a;
        if (generate_data.repetition_penalty !== undefined) bodyParams.repetition_penalty = generate_data.repetition_penalty;
        if (Array.isArray(generate_data.provider) && generate_data.provider.length > 0) {
            bodyParams.provider = { allow_fallbacks: generate_data.allow_fallbacks ?? true, order: generate_data.provider };
        }
        if (Array.isArray(generate_data.quantizations) && generate_data.quantizations.length > 0) {
            bodyParams.provider ??= {};
            bodyParams.provider.quantizations = generate_data.quantizations;
        }
        if (generate_data.use_fallback) bodyParams.route = 'fallback';
        if (generate_data.reasoning_effort) bodyParams.reasoning.effort = generate_data.reasoning_effort;
        if (generate_data.verbosity) bodyParams.verbosity = generate_data.verbosity;
        if (generate_data.json_schema) {
            bodyParams.response_format = { type: 'json_schema', json_schema: { name: generate_data.json_schema.name, strict: generate_data.json_schema.strict ?? true, schema: generate_data.json_schema.value } };
        }
    } else if (generate_data.chat_completion_source === 'custom') {
        bodyParams.logprobs = generate_data.logprobs;
        bodyParams.top_logprobs = undefined;
        if (!isTextCompletion && bodyParams.logprobs > 0) {
            bodyParams.top_logprobs = bodyParams.logprobs;
            bodyParams.logprobs = true;
        }
    } else if (generate_data.chat_completion_source === 'perplexity') {
        bodyParams = { reasoning_effort: generate_data.reasoning_effort };
    } else if (generate_data.chat_completion_source === 'groq' || generate_data.chat_completion_source === 'fireworks' || generate_data.chat_completion_source === 'siliconflow') {
        bodyParams = {};
    } else if (generate_data.chat_completion_source === 'nanogpt') {
        bodyParams = {};
        if (generate_data.nanogpt_payg_override) bodyParams.billing_mode = 'paygo';
        if (generate_data.min_p !== undefined) bodyParams.min_p = generate_data.min_p;
        if (generate_data.top_a !== undefined) bodyParams.top_a = generate_data.top_a;
        if (generate_data.repetition_penalty !== undefined) bodyParams.repetition_penalty = generate_data.repetition_penalty;
        if (generate_data.reasoning_effort) {
            const NANOGPT_REASONING_EFFORT_MAP = { low: 'low', medium: 'medium', high: 'high', min: 'lowest', max: 'highest', auto: 'auto' };
            bodyParams.reasoning = { effort: NANOGPT_REASONING_EFFORT_MAP[generate_data.reasoning_effort] };
        }
    } else if (generate_data.chat_completion_source === 'pollinations') {
        bodyParams = { reasoning_effort: generate_data.reasoning_effort, seed: generate_data.seed ?? 42 };
    } else if (generate_data.chat_completion_source === 'moonshot') {
        bodyParams = { thinking: { type: generate_data.include_reasoning ? 'enabled' : 'disabled' } };
        if (generate_data.json_schema) {
            bodyParams.response_format = { type: 'json_object' };
            generate_data.messages.push({ role: 'user', content: 'JSON schema for the response:\\n' + JSON.stringify(generate_data.json_schema.value, null, 4) });
        } else {
            const msgs = generate_data.messages;
            if (msgs.length && !msgs.some(m => m.role === 'tool') && msgs[msgs.length - 1].role === 'assistant') {
                msgs[msgs.length - 1].partial = true;
            }
        }
    } else if (generate_data.chat_completion_source === 'zai') {
        bodyParams = { thinking: { type: generate_data.include_reasoning ? 'enabled' : 'disabled' } };
        if (generate_data.json_schema) {
            bodyParams.response_format = { type: 'json_object' };
            generate_data.messages.push({ role: 'user', content: 'JSON schema for the response:\\n' + JSON.stringify(generate_data.json_schema.value, null, 4) });
        }
    } else if (generate_data.chat_completion_source === 'workers_ai') {
        bodyParams = { repetition_penalty: generate_data.repetition_penalty };
    }

    if (generate_data.reasoning_effort && ['custom', 'openai'].includes(generate_data.chat_completion_source)) {
        if (OPENAI_REASONING_EFFORT_MODELS.includes(generate_data.model)) {
            bodyParams.reasoning_effort = OPENAI_FIXED_REASONING_EFFORT[generate_data.model] ?? OPENAI_REASONING_EFFORT_MAP[generate_data.reasoning_effort] ?? generate_data.reasoning_effort;
        }
    }
    if (generate_data.verbosity && ['custom', 'openai'].includes(generate_data.chat_completion_source)) {
        if (OPENAI_VERBOSITY_MODELS.test(generate_data.model)) bodyParams.verbosity = generate_data.verbosity;
    }
    if (Array.isArray(generate_data.stop) && generate_data.stop.length > 0) bodyParams.stop = generate_data.stop;
    if (!isTextCompletion && Array.isArray(generate_data.tools) && generate_data.tools.length > 0) {
        bodyParams.tools = generate_data.tools;
        bodyParams.tool_choice = generate_data.tool_choice;
    }
    if (generate_data.json_schema && !bodyParams.response_format) {
        bodyParams.response_format = { type: 'json_schema', json_schema: { name: generate_data.json_schema.name, strict: generate_data.json_schema.strict ?? true, schema: generate_data.json_schema.value } };
    }

    const requestBody = {
        messages: isTextCompletion ? undefined : generate_data.messages,
        prompt: isTextCompletion ? 'prompt' : undefined,
        model: generate_data.model,
        temperature: generate_data.temperature,
        max_tokens: generate_data.max_tokens,
        max_completion_tokens: generate_data.max_completion_tokens,
        stream: generate_data.stream,
        presence_penalty: generate_data.presence_penalty,
        frequency_penalty: generate_data.frequency_penalty,
        top_p: generate_data.top_p,
        top_k: generate_data.top_k,
        stop: isTextCompletion ? undefined : generate_data.stop,
        logit_bias: generate_data.logit_bias,
        seed: generate_data.seed,
        n: generate_data.n,
        ...bodyParams,
    };
    return JSON.parse(JSON.stringify(requestBody));
}
`;

// createGenerationParameters 提取段（与 openai-params-official.mjs 相同，完整全厂商）
import { funcs as paramsFuncs } from './openai-params-official.mjs';

const runParams = new Function([
    paramsFuncs,
    'return (request) => buildOpenAiParams(request.body.settings, request.body.model, request.body.type, request.body.messages);',
].join('\n'));
const runBody = new Function([funcs, 'return (generateData) => buildOpenAiRequestBody(generateData);'].join('\n'));

const cases = [];
async function add(id, body) {
    const generateData = await runParams()({ body });
    const requestBody = runBody()(generateData);
    cases.push({ id, args: body, expected: requestBody });
}

const baseSettings = { source: 'openai', temp: 1.0, freqPen: 0, presPen: 0, topP: 1, maxTokens: 512, stream: true, n: 1, userName: 'U', charName: 'C', groupNames: [], showThoughts: false, reasoningEffort: 'low', enableWebSearch: false, requestImages: false, requestImageResolution: 'auto', requestImageAspectRatio: '1:1', customPromptPostProcessing: 'NONE', verbosity: 'normal', seed: -1, requestTokenProbabilities: false, stopStrings: ['END'], topK: 40, minP: 0.1, repetitionPenalty: 1.05, topA: 0.5, useFallback: true, provider: ['a'], quantizations: ['q4'], allowFallbacks: true, middleout: 'on', nanogptProvider: 'p', nanogptPaygOverride: 'x', useSysprompt: true, zaiEndpoint: 'z', siliconflowEndpoint: 's', minimaxEndpoint: 'm', workersAiAccountId: 'w' };

await add('openai', { settings: baseSettings, model: 'gpt-4o', type: 'normal', messages: [{ role: 'user', content: 'hi' }] });
await add('openai-logprobs', { settings: { ...baseSettings, requestTokenProbabilities: true }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('azure', { settings: { ...baseSettings, source: 'azure_openai' }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('openrouter', { settings: { ...baseSettings, source: 'openrouter', seed: 7, showThoughts: true }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('openrouter-auto', { settings: { ...baseSettings, source: 'openrouter', middleout: 'auto', enableWebSearch: true, verbosity: undefined }, model: 'openai/gpt-5.5', type: 'normal', messages: [] });
await add('openrouter-fallback', { settings: { ...baseSettings, source: 'openrouter', useFallback: true, provider: [], quantizations: [] }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('custom', { settings: { ...baseSettings, source: 'custom', requestTokenProbabilities: true }, model: 'm', type: 'normal', messages: [] });
await add('perplexity', { settings: { ...baseSettings, source: 'perplexity' }, model: 'p', type: 'normal', messages: [] });
await add('groq', { settings: { ...baseSettings, source: 'groq' }, model: 'llama', type: 'normal', messages: [] });
await add('deepseek', { settings: { ...baseSettings, source: 'deepseek', topP: 0 }, model: 'deepseek', type: 'normal', messages: [] });
await add('moonshot', { settings: { ...baseSettings, source: 'moonshot', showThoughts: true }, model: 'kimi-k2.6', type: 'normal', messages: [{ role: 'assistant', content: 'x' }] });
await add('moonshot-k2', { settings: { ...baseSettings, source: 'moonshot' }, model: 'kimi-k2.5', type: 'normal', messages: [] });
await add('zai', { settings: { ...baseSettings, source: 'zai' }, model: 'glm-5', type: 'normal', messages: [] });
await add('siliconflow', { settings: { ...baseSettings, source: 'siliconflow' }, model: 's', type: 'normal', messages: [] });
await add('minimax', { settings: { ...baseSettings, source: 'minimax', temp: 0 }, model: 'm', type: 'normal', messages: [] });
await add('workers-ai', { settings: { ...baseSettings, source: 'workers_ai', topK: 80, seed: 3 }, model: 'llama', type: 'normal', messages: [] });
await add('o1', { settings: { ...baseSettings }, model: 'o1', type: 'normal', messages: [{ role: 'system', content: 'x' }] });
await add('gpt5', { settings: { ...baseSettings }, model: 'gpt-5.5', type: 'normal', messages: [{ role: 'user', content: 'hi' }] });
// ---- 2026-08-12 穷举复验补充 ----
await add('openrouter-providers-empty', { settings: { ...baseSettings, source: 'openrouter', provider: [], quantizations: [] }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('openrouter-topk-zero', { settings: { ...baseSettings, source: 'openrouter', topK: 0 }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('openrouter-no-verbosity', { settings: { ...baseSettings, source: 'openrouter', verbosity: undefined }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('minimax-temp-clamp-high', { settings: { ...baseSettings, source: 'minimax', temp: 2.5 }, model: 'm', type: 'normal', messages: [] });
await add('minimax-temp-clamp-low', { settings: { ...baseSettings, source: 'minimax', temp: 0 }, model: 'm', type: 'normal', messages: [] });
await add('workers-ai-seed-zero', { settings: { ...baseSettings, source: 'workers_ai', topK: 10, seed: 0 }, model: 'llama', type: 'normal', messages: [] });
await add('custom-no-stop', { settings: { ...baseSettings, source: 'custom', stopStrings: [] }, model: 'm', type: 'normal', messages: [] });
await add('nonstream', { settings: { ...baseSettings, stream: false }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('perplexity-reasoning-auto', { settings: { ...baseSettings, source: 'perplexity', reasoningEffort: 'auto' }, model: 'p', type: 'normal', messages: [] });
await add('moonshot-no-thinking', { settings: { ...baseSettings, source: 'moonshot', showThoughts: false }, model: 'kimi-k2.6', type: 'normal', messages: [{ role: 'assistant', content: 'x' }] });

writeFileSync(outFile, JSON.stringify({ source: 'chat-completions.js OpenAI 兼容 requestBody 构造', cases }, null, 2));
console.log('chat-request-body:', cases.length, 'cases ->', outFile);
