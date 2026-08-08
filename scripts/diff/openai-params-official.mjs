#!/usr/bin/env node
// OpenAI createGenerationParameters 核心（openai.js 公共字段）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'openai-params.json');

const funcs = `
const chat_completion_sources = { OPENAI: 'openai', AZURE_OPENAI: 'azure_openai', OPENROUTER: 'openrouter' };
function buildOpenAiParams(settings, model, type, messages) {
    const isO1 = ['openai', 'azure_openai'].includes(settings.source) && ['o1-2024-12-17', 'o1'].includes(model);
    const stream = settings.stream && type !== 'quiet' && !isO1;
    const noMultiSwipeTypes = ['quiet', 'impersonate', 'continue'];
    const canMultiSwipe = settings.n > 1 && !noMultiSwipeTypes.includes(type) && ['openai', 'azure_openai', 'custom', 'xai', 'aimlapi', 'moonshot'].includes(settings.source);
    let logit_bias = settings.logitBias && Object.keys(settings.logitBias).length ? settings.logitBias : undefined;
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
    if (settings.requestTokenProbabilities && ['openai','azure_openai','custom','deepseek','xai','aimlapi','chutes'].includes(settings.source)) generate_data.logprobs = 5;
    const isVision = (m) => ['gpt', 'vision'].every(x => typeof m === 'string' && m.includes(x));
    if (['openai','azure_openai'].includes(settings.source) && isVision(model)) { delete generate_data.logit_bias; delete generate_data.stop; delete generate_data.logprobs; }
    if (['openai','azure_openai'].includes(settings.source) && /gpt-4.5/.test(model)) delete generate_data.logprobs;
    if (['openai','azure_openai'].includes(settings.source) && settings.seed >= 0) generate_data.seed = settings.seed;
    if (['openai','azure_openai'].includes(settings.source) && /^(o1|o3|o4)/.test(model)) {
        generate_data.max_completion_tokens = generate_data.max_tokens;
        delete generate_data.max_tokens; delete generate_data.logprobs; delete generate_data.stop; delete generate_data.logit_bias;
        delete generate_data.temperature; delete generate_data.top_p; delete generate_data.frequency_penalty; delete generate_data.presence_penalty;
        if (/^(openai\\/)?(o1)/.test(model)) { delete generate_data.n; }
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

const baseSettings = { source: 'openai', temp: 1.0, freqPen: 0, presPen: 0, topP: 1, maxTokens: 512, stream: true, n: 1, userName: 'U', charName: 'C', groupNames: [], showThoughts: false, reasoningEffort: 'low', enableWebSearch: false, requestImages: false, requestImageResolution: 'auto', requestImageAspectRatio: '1:1', customPromptPostProcessing: 'NONE', verbosity: 'normal', seed: -1, requestTokenProbabilities: false, stopStrings: ['END'] };

await add('basic', { settings: baseSettings, model: 'gpt-4o', type: 'normal', messages: [{ role: 'user', content: 'hi' }] });
await add('quiet', { settings: { ...baseSettings, stream: true }, model: 'gpt-4o', type: 'quiet', messages: [] });
await add('azure', { settings: { ...baseSettings, source: 'azure_openai', azureBaseUrl: 'https://x', azureDeploymentName: 'dep', azureApiVersion: '2024-12-01' }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('seed', { settings: { ...baseSettings, seed: 42 }, model: 'gpt-4o', type: 'normal', messages: [] });
await add('vision', { settings: { ...baseSettings, requestTokenProbabilities: true }, model: 'gpt-4o-vision', type: 'normal', messages: [] });
await add('o1', { settings: { ...baseSettings, stream: true }, model: 'o1', type: 'normal', messages: [] });

writeFileSync(outFile, JSON.stringify({ source: 'openai.js createGenerationParameters 核心', cases }, null, 2));
console.log('openai-params:', cases.length, 'cases ->', outFile);
