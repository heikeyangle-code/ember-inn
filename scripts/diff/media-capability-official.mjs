#!/usr/bin/env node
// 媒体内联能力判定（openai.js isImageInliningSupported/isVideoInliningSupported/isAudioInliningSupported）
// 逐字提取 + 打桩（main_api/oai_settings/chat_completion_sources/model_list）→ JSON fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'media-capability.json');

const src = readFileSync(join(officialRef, 'public/scripts/openai.js'), 'utf8');

function extractFunction(signature, name) {
    const start = src.indexOf(signature);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = src.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < src.length; i++) {
        const ch = src[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(src[j])) j++; if (src[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    let d = 0, k = bodyStart, q = null;
    for (; k < src.length; k++) {
        const ch = src[k];
        if (q) { if (ch === '\\') { k++; continue; } if (ch === q) q = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { q = ch; continue; }
        if (ch === '/' && src[k + 1] === '/') { while (k < src.length && src[k] !== '\n') k++; continue; }
        if (ch === '/' && src[k + 1] === '*') { d++; k++; continue; }
        if (ch === '*' && src[k + 1] === '/') { d--; k++; continue; }
        if (ch === '{') d++;
        else if (ch === '}') { d--; if (d === 0) return src.slice(start, k + 1); }
    }
    throw new Error(`unbalanced: ${name}`);
}

const fnImage = extractFunction('export function isImageInliningSupported()', 'isImageInliningSupported').replace(/^export /, '');
const fnVideo = extractFunction('export function isVideoInliningSupported()', 'isVideoInliningSupported').replace(/^export /, '');
const fnAudio = extractFunction('export function isAudioInliningSupported()', 'isAudioInliningSupported').replace(/^export /, '');

const stub = `
const main_api = 'openai';
const chat_completion_sources = {
    OPENAI: 'openai',
    AZURE_OPENAI: 'azure_openai',
    MAKERSUITE: 'makersuite',
    VERTEXAI: 'vertexai',
    CLAUDE: 'claude',
    OPENROUTER: 'openrouter',
    CUSTOM: 'custom',
    MISTRALAI: 'mistralai',
    COHERE: 'cohere',
    XAI: 'xai',
    MOONSHOT: 'moonshot',
    ZAI: 'zai',
    SILICONFLOW: 'siliconflow',
    WORKERS_AI: 'workers',
};
const model = request.body.model ?? 'gpt-4o';
const modalities = request.body.modalities ?? {};
const model_list = [
    {
        id: model,
        architecture: { input_modalities: modalities.vision ? ['image'] : (modalities.video ? ['video'] : (modalities.audio ? ['audio'] : [])) },
        capabilities: { vision: !!modalities.vision },
        supports_image_in: !!modalities.vision,
        properties: [{ property_id: 'vision', value: modalities.vision ? 'true' : 'false' }],
    },
];
const oai_settings = {
    media_inlining: true,
    chat_completion_source: request.body.source,
    openai_model: model,
    azure_openai_model: model,
    google_model: model,
    vertexai_model: model,
    claude_model: model,
    openrouter_model: model,
    mistralai_model: model,
    cohere_model: model,
    xai_model: model,
    moonshot_model: model,
    zai_model: model,
    siliconflow_model: model,
    workers_ai_model: model,
};
`;

const fn = [stub, fnImage, fnVideo, fnAudio].join('\n');
const runCase = new Function('request', [
    fn,
    'return (() => ({',
    '    image: isImageInliningSupported(),',
    '    video: isVideoInliningSupported(),',
    '    audio: isAudioInliningSupported(),',
    '}))();',
].join('\n'));

const cases = [];
async function add(id, source, model, modalities = {}) {
    const expected = await runCase({ body: { source, model, modalities } });
    cases.push({ id, args: { body: { source, model, modalities } }, expected });
}

await add('openai-gpt4o', 'openai', 'gpt-4o');
await add('openai-gpt4turbo-preview', 'openai', 'gpt-4-turbo-preview');
await add('openai-o3-mini', 'openai', 'o3-mini');
await add('openai-gpt5', 'openai', 'gpt-5');
await add('openai-unknown', 'openai', 'deepseek-v4');
await add('azure-claude', 'azure_openai', 'claude-sonnet-4');
await add('claude-opus', 'claude', 'claude-opus-4');
await add('makersuite-gemini3', 'makersuite', 'gemini-3');
await add('makersuite-gemma', 'makersuite', 'gemma-3-27b');
await add('vertex-gemini25', 'vertexai', 'gemini-2.5-flash');
await add('openrouter-metadata-image', 'openrouter', 'custom-vl', { vision: true });
await add('openrouter-no-metadata', 'openrouter', 'custom-vl');
await add('custom-always', 'custom', 'anything');
await add('mistral-pixtral', 'mistralai', 'pixtral-12b');
await add('mistral-metadata', 'mistralai', 'custom-vl', { vision: true });
await add('cohere-aya', 'cohere', 'c4ai-aya-vision');
await add('xai-grok4', 'xai', 'grok-4');
await add('moonshot-kimi', 'moonshot', 'kimi-k2.5');
await add('moonshot-metadata', 'moonshot', 'custom-vl', { vision: true });
await add('zai-glm5v', 'zai', 'glm-5v-turbo');
await add('siliconflow-qwen3vl', 'siliconflow', 'Qwen/Qwen3-VL-8B-Instruct');
await add('workers-metadata', 'workers', 'cf-vl', { vision: true });
await add('workers-no-metadata', 'workers', 'cf-vl');
await add('unknown-source', 'kobold', 'x');

writeFileSync(outFile, JSON.stringify({ source: 'openai.js media inlining capability', cases }, null, 2));
console.log('media-capability:', cases.length, 'cases ->', outFile);
