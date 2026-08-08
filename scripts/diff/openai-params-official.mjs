#!/usr/bin/env node
// OpenAI createGenerationParameters 全厂商分支 → fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'openai-params.json');

const funcs = `
const chat_completion_sources = { OPENAI: 'openai', AZURE_OPENAI: 'azure_openai', OPENROUTER: 'openrouter', NANOGPT: 'nanogpt', MAKERSUITE: 'makersuite', VERTEXAI: 'vertexai', MISTRALAI: 'mistralai', CUSTOM: 'custom', COHERE: 'cohere', PERPLEXITY: 'perplexity', GROQ: 'groq', DEEPSEEK: 'deepseek', XAI: 'xai', ELECTRONHUB: 'electronhub', CHUTES: 'chutes', ZAI: 'zai', SILICONFLOW: 'siliconflow', MINIMAX: 'minimax', WORKERS_AI: 'workers_ai', MOONSHOT: 'moonshot' };
const ZAI_ENDPOINT = { COMMON: 'common' };
const SILICONFLOW_ENDPOINT = { GLOBAL: 'global' };
const MINIMAX_ENDPOINT = { GLOBAL: 'global' };

function buildOpenAiParams(settings, model, type, messages) {
    const isO1 = ['openai', 'azure_openai'].includes(settings.source) && ['o1-2024-12-17', 'o1'].includes(model);
    const stream = settings.stream && type !== 'quiet' && !isO1;
    const noMultiSwipeTypes = ['quiet', 'impersonate', 'continue'];
    const multiswipeSources = ['openai','azure_openai','custom','xai','aimlapi','moonshot'];
    const canMultiSwipe = settings.n > 1 && !noMultiSwipeTypes.includes(type) && multiswipeSources.includes(settings.source);
    const logitBiasSources = ['openai','azure_openai','openrouter','electronhub','chutes','custom'];
    let logit_bias = (settings.logitBias && Object.keys(settings.logitBias).length && logitBiasSources.includes(settings.source)) ? settings.logitBias : undefined;
    const generate_data = {
        'type': type, 'messages': messages, 'model': model,
        'temperature': Number(settings.temp), 'frequency_penalty': Number(settings.freqPen),
        'presence_penalty': Number(settings.presPen), 'top_p': Number(settings.topP),
        'max_tokens': settings.maxTokens, 'stream': stream, 'logit_bias': logit_bias,
        'stop': settings.stopStrings, 'chat_completion_source': settings.source,
        'n': canMultiSwipe ? settings.n : undefined,
        'user_name': settings.userName, 'char_name': settings.charName, 'group_names': settings.groupNames,
        'include_reasoning': Boolean(settings.showThoughts), 'reasoning_effort': settings.reasoningEffort,
        'enable_web_search': Boolean(settings.enableWebSearch),
        'request_images': Boolean(settings.requestImages),
        'request_image_resolution': String(settings.requestImageResolution),
        'request_image_aspect_ratio': String(settings.requestImageAspectRatio),
        'custom_prompt_post_processing': settings.customPromptPostProcessing,
        'verbosity': settings.verbosity,
    };
    if (settings.source === 'azure_openai') {
        generate_data.azure_base_url = settings.azureBaseUrl;
        generate_data.azure_deployment_name = settings.azureDeploymentName;
        generate_data.azure_api_version = settings.azureApiVersion;
        if (/^gpt-[34]/.test(model)) delete generate_data.reasoning_effort;
    }
    if (settings.source === 'openrouter') {
        generate_data.top_k = Number(settings.topK); generate_data.min_p = Number(settings.minP);
        generate_data.repetition_penalty = Number(settings.repetitionPenalty); generate_data.top_a = Number(settings.topA);
        generate_data.use_fallback = settings.useFallback; generate_data.provider = settings.provider;
        generate_data.quantizations = settings.quantizations; generate_data.allow_fallbacks = settings.allowFallbacks;
        generate_data.middleout = settings.middleout;
    }
    if (settings.source === 'nanogpt') {
        generate_data.nanogpt_provider = settings.nanogptProvider; generate_data.nanogpt_payg_override = settings.nanogptPaygOverride;
        generate_data.top_k = Number(settings.topK); generate_data.min_p = Number(settings.minP);
        generate_data.repetition_penalty = Number(settings.repetitionPenalty); generate_data.top_a = Number(settings.topA);
    }
    if (['makersuite','vertexai'].includes(settings.source)) {
        const stopStringsLimit = 5;
        generate_data.top_k = Number(settings.topK);
        generate_data.stop = (settings.stopStrings || []).slice(0, stopStringsLimit).filter(x => x.length >= 1 && x.length <= 16);
        generate_data.use_sysprompt = settings.useSysprompt;
        if (settings.source === 'vertexai') {
            generate_data.vertexai_auth_mode = settings.vertexaiAuthMode;
            generate_data.vertexai_region = settings.vertexaiRegion;
            generate_data.vertexai_express_project_id = settings.vertexaiExpressProjectId;
        }
    }
    if (settings.source === 'mistralai') {
        generate_data.safe_prompt = false; generate_data.stop = settings.stopStrings;
    }
    if (settings.source === 'custom') {
        generate_data.custom_url = settings.customUrl; generate_data.custom_include_body = settings.customIncludeBody;
        generate_data.custom_exclude_body = settings.customExcludeBody; generate_data.custom_include_headers = settings.customIncludeHeaders;
    }
    if (settings.source === 'cohere') {
        generate_data.top_p = Math.min(Math.max(Number(settings.topP), 0.01), 0.99);
        generate_data.top_k = Number(settings.topK);
        generate_data.frequency_penalty = Math.min(Math.max(Number(settings.freqPen), 0), 1);
        generate_data.presence_penalty = Math.min(Math.max(Number(settings.presPen), 0), 1);
        generate_data.stop = (settings.stopStrings || []).slice(0, 5);
    }
    if (settings.source === 'perplexity') {
        generate_data.top_k = Number(settings.topK); generate_data.frequency_penalty = Number(settings.freqPen);
        generate_data.presence_penalty = Number(settings.presPen); delete generate_data.stop;
    }
    if (settings.source === 'groq') { delete generate_data.logprobs; delete generate_data.logit_bias; delete generate_data.top_logprobs; delete generate_data.n; }
    if (settings.source === 'deepseek') { generate_data.top_p = generate_data.top_p || Number.EPSILON; }
    if (settings.source === 'xai') {
        if (model.includes('grok-3-mini')) { delete generate_data.presence_penalty; delete generate_data.frequency_penalty; delete generate_data.stop; }
        else { delete generate_data.reasoning_effort; }
        if (model.includes('grok-4') || model.includes('grok-code')) {
            delete generate_data.presence_penalty; delete generate_data.frequency_penalty;
            if (!model.includes('grok-4-fast-non-reasoning')) delete generate_data.stop;
        }
    }
    if (settings.source === 'electronhub') { generate_data.top_k = Number(settings.topK); }
    if (settings.source === 'chutes') {
        generate_data.min_p = Number(settings.minP);
        generate_data.top_k = settings.topK > 0 ? Number(settings.topK) : undefined;
        generate_data.repetition_penalty = Number(settings.repetitionPenalty);
        generate_data.stop = settings.stopStrings;
    }
    if (settings.source === 'zai') {
        generate_data.top_p = generate_data.top_p || 0.01; generate_data.stop = (settings.stopStrings || []).slice(0, 1);
        generate_data.zai_endpoint = settings.zaiEndpoint || 'common'; delete generate_data.presence_penalty; delete generate_data.frequency_penalty;
    }
    if (settings.source === 'siliconflow') { generate_data.siliconflow_endpoint = settings.siliconflowEndpoint || 'global'; }
    if (settings.source === 'minimax') {
        generate_data.minimax_endpoint = settings.minimaxEndpoint || 'global';
        if (Number.isFinite(generate_data.temperature)) generate_data.temperature = Math.min(Math.max(generate_data.temperature, Number.EPSILON), 1.0);
    }
    if (settings.source === 'workers_ai') {
        generate_data.workers_ai_account_id = settings.workersAiAccountId;
        generate_data.top_k = settings.topK > 0 ? Math.min(Number(settings.topK), 50) : undefined;
        generate_data.repetition_penalty = Number(settings.repetitionPenalty);
        generate_data.seed = settings.seed >= 1 ? Number(settings.seed) : undefined;
        generate_data.top_p = Math.max(Number(settings.topP), 0.001);
        delete generate_data.n; delete generate_data.logit_bias;
    }
    if (settings.source === 'moonshot') {
        if (/kimi-k2.5/.test(model)) { delete generate_data.temperature; delete generate_data.top_p; delete generate_data.frequency_penalty; delete generate_data.presence_penalty; }
    }
    const seedSupportedSources = ['openai','azure_openai','openrouter','mistralai','custom','cohere','groq','electronhub','nanogpt','xai','pollinations','aimlapi','vertexai','makersuite','chutes'];
    if (seedSupportedSources.includes(settings.source) && settings.seed >= 0) generate_data.seed = settings.seed;
    if (['openai','azure_openai'].includes(settings.source) && /^(o1|o3|o4)/.test(model) || (settings.source === 'openrouter' && /^openai\\/(o1|o3|o4)/.test(model))) {
        generate_data.max_completion_tokens = generate_data.max_tokens;
        delete generate_data.max_tokens; delete generate_data.logprobs; delete generate_data.stop; delete generate_data.logit_bias;
        delete generate_data.temperature; delete generate_data.top_p; delete generate_data.frequency_penalty; delete generate_data.presence_penalty;
        if (/^(openai\\/)?(o1)/.test(model)) { generate_data.messages.forEach(msg => { if (msg.role === 'system') msg.role = 'user'; }); delete generate_data.n; }
    }
    if (['openai','azure_openai','openrouter'].includes(settings.source) && /gpt-5/.test(model)) {
        generate_data.max_completion_tokens = generate_data.max_tokens;
        delete generate_data.max_tokens; delete generate_data.logprobs; delete generate_data.top_logprobs;
        if (/gpt-5-chat-latest/.test(model)) {
            delete generate_data.tools; delete generate_data.tool_choice;
        } else if (/gpt-5\.(1|2|3|4)/.test(model) && !/chat-latest/.test(model) && !generate_data.reasoning_effort) {
            delete generate_data.frequency_penalty; delete generate_data.presence_penalty; delete generate_data.logit_bias; delete generate_data.stop;
        } else {
            delete generate_data.temperature; delete generate_data.top_p; delete generate_data.frequency_penalty; delete generate_data.presence_penalty; delete generate_data.logit_bias; delete generate_data.stop;
        }
    }
    return generate_data;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => buildOpenAiParams(request.body.settings, request.body.model, request.body.type, request.body.messages);',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const baseSettings = { source: 'openai', temp: 1.0, freqPen: 0, presPen: 0, topP: 1, maxTokens: 512, stream: true, n: 1, userName: 'U', charName: 'C', groupNames: [], showThoughts: false, reasoningEffort: 'low', enableWebSearch: false, requestImages: false, requestImageResolution: 'auto', requestImageAspectRatio: '1:1', customPromptPostProcessing: 'NONE', verbosity: 'normal', seed: -1, requestTokenProbabilities: false, stopStrings: ['END'], topK: 40, minP: 0.1, repetitionPenalty: 1.05, topA: 0.5, useFallback: true, provider: { order: ['a'] }, quantizations: ['q4'], allowFallbacks: true, middleout: false, nanogptProvider: 'p', nanogptPaygOverride: 'x', useSysprompt: true, vertexaiAuthMode: 'oauth', vertexaiRegion: 'us', vertexaiExpressProjectId: 'p', customUrl: 'http://x', customIncludeBody: true, customExcludeBody: false, customIncludeHeaders: true, zaiEndpoint: 'z', siliconflowEndpoint: 's', minimaxEndpoint: 'm', workersAiAccountId: 'w' };

await add('basic', { settings: baseSettings, model: 'gpt-4o', type: 'normal', messages: [{ role: 'user', content: 'hi' }] });
await add('azure', { settings: { ...baseSettings, source: 'azure_openai', azureBaseUrl: 'https://x', azureDeploymentName: 'dep', azureApiVersion: '2024-12-01' }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('openrouter', { settings: { ...baseSettings, source: 'openrouter', seed: 7 }, model: 'openai/gpt-4o', type: 'normal', messages: [] });
await add('groq', { settings: { ...baseSettings, source: 'groq' }, model: 'llama', type: 'normal', messages: [] });
await add('xai-grok3', { settings: { ...baseSettings, source: 'xai' }, model: 'grok-3-mini', type: 'normal', messages: [] });
await add('xai-grok4', { settings: { ...baseSettings, source: 'xai' }, model: 'grok-4', type: 'normal', messages: [] });
await add('cohere', { settings: { ...baseSettings, source: 'cohere', topP: 0.5, freqPen: 0.5, presPen: 0.5 }, model: 'command', type: 'normal', messages: [] });
await add('deepseek', { settings: { ...baseSettings, source: 'deepseek', topP: 0 }, model: 'deepseek', type: 'normal', messages: [] });
await add('workers-ai', { settings: { ...baseSettings, source: 'workers_ai', topK: 80, seed: 3 }, model: 'llama', type: 'normal', messages: [] });
await add('moonshot-k2', { settings: { ...baseSettings, source: 'moonshot' }, model: 'kimi-k2.5', type: 'normal', messages: [] });
await add('custom', { settings: { ...baseSettings, source: 'custom' }, model: 'm', type: 'normal', messages: [] });
await add('perplexity', { settings: { ...baseSettings, source: 'perplexity' }, model: 'p', type: 'normal', messages: [] });
await add('mistral', { settings: { ...baseSettings, source: 'mistralai' }, model: 'm', type: 'normal', messages: [] });
await add('chutes', { settings: { ...baseSettings, source: 'chutes', topK: 20 }, model: 'c', type: 'normal', messages: [] });
await add('zai', { settings: { ...baseSettings, source: 'zai' }, model: 'z', type: 'normal', messages: [] });
await add('minimax', { settings: { ...baseSettings, source: 'minimax', temp: 0 }, model: 'm', type: 'normal', messages: [] });
await add('nanogpt', { settings: { ...baseSettings, source: 'nanogpt' }, model: 'n', type: 'normal', messages: [] });
await add('vertex', { settings: { ...baseSettings, source: 'vertexai' }, model: 'v', type: 'normal', messages: [] });
await add('electronhub', { settings: { ...baseSettings, source: 'electronhub' }, model: 'e', type: 'normal', messages: [] });
await add('siliconflow', { settings: { ...baseSettings, source: 'siliconflow' }, model: 's', type: 'normal', messages: [] });
await add('o1', { settings: { ...baseSettings }, model: 'o1', type: 'normal', messages: [{ role: 'system', content: 'x' }] });
await add('gpt5', { settings: { ...baseSettings }, model: 'gpt-5.5', type: 'normal', messages: [{ role: 'user', content: 'hi' }] });
await add('gpt5-chat-latest', { settings: { ...baseSettings }, model: 'gpt-5-chat-latest', type: 'normal', messages: [] });
await add('gpt5-no-reasoning', { settings: { ...baseSettings, reasoningEffort: undefined }, model: 'gpt-5.4', type: 'normal', messages: [] });
await add('gpt5-reasoning', { settings: { ...baseSettings, reasoningEffort: 'high' }, model: 'gpt-5.4', type: 'normal', messages: [] });
await add('azure-gpt5', { settings: { ...baseSettings, source: 'azure_openai' }, model: 'gpt-5.5', type: 'normal', messages: [] });
await add('openrouter-gpt5', { settings: { ...baseSettings, source: 'openrouter' }, model: 'openai/gpt-5.5', type: 'normal', messages: [] });

writeFileSync(outFile, JSON.stringify({ source: 'openai.js createGenerationParameters 全厂商', cases }, null, 2));
console.log('openai-params:', cases.length, 'cases ->', outFile);
