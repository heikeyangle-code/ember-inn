#!/usr/bin/env node
// 官方 TTS 扩展各后端 fetchTtsGeneration / fetchNativeTtsGeneration 构造的 request body → fixture。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   public/scripts/extensions/tts/{backend}.js 的 fetchTtsGeneration 函数体内 body 字面量构造段。
//
// 打桩（脚本头部登记）：
// - this.settings = 入参（由 cases 注入），默认值取自各 .js defaultSettings
// - substituteParams = 恒等（OpenAI instructions 的 macro 替换另开差分）
// - splitRecursive = 官方 utils.js splitRecursive 逐字复制（novel/pollinations/google-translate 分块）
// - fetch / getRequestHeaders / toastr / secret_state = 不调用（脚本只跑 body 构造）
//
// 边界（不差分，登记）：
// - 服务端 src/endpoints/{speech,openai,google,azure,volcengine,minimax,novelai}.js 把 .js body 转发到厂商
//   真实端点时可能做字段映射（如 OpenAI text→input）；该映射属服务端实现，App 直连时按厂商 API
//   字段名另做适配（见 TtsBackendsCloud 注释），不在本差分覆盖。
// - history 复用（ElevenLabs findTtsGenerationInHistory）、voice cloning、recognize 等非生成行为不差分。
// - alltalk/coqui/cosyvoice/gpt-sovits/gsvi/kokoro/openai-compatible/sbvits2/silerotts/speecht5/
//   system/tts-webui/vits/xtts/chatterbox/electronhub 等本地或其余后端 App 未接，登记未做（见 HANDOFF 3.7）。
//
// 差分目标（11 后端，首批全覆盖已接后端）：
// - elevenlabs: request={model_id, text, voice_settings:{stability, similarity_boost, speed, [style, use_speaker_boost]}}
//   分支：shouldInvolveExtendedSettings()=model∈[eleven_v3,eleven_ttv_v3,eleven_multilingual_v2,eleven_multilingual_ttv_v2]
// - openai: requestBody={text, voice, model, speed, [instructions]}
//   分支：model='gpt-4o-mini-tts' && characterName && instructions.trim() 才加 instructions=substituteParams(instructions)
// - edge: body={text, voice, rate}
// - azure: body={text, voice, region}
// - novel: body={text, voice}，generator 用 splitRecursive(text,1000) 分块
// - minimax: requestBody={text, voiceId, apiHost, model, speed, volume, pitch, audioSampleRate, bitrate, format, language}
//   含 clamp 函数与 defaultSettings 兜底
// - volcengine: body={provider_endpoint, resource_id, text, voice_speaker, speed}
// - chutes: body={input, voice: voiceId||'af_heart', speed: settings.speed||1}
// - pollinations: body={model, text:'Say exactly this and nothing else:'+'\n'+chunk, voice}，splitRecursive(text,1000)
// - google-native: body={text, voice, model, api, reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id}
// - google-translate: body={text: splitRecursive(text,200), voice}

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'tts-services.json');

// ---------- 官方 utils.js splitRecursive（L1157-L1189 逐字摘） ----------
function splitRecursive(input, length, delimiters = ['\n\n', '\n', ' ', '']) {
    if (length <= 0) {
        return [input];
    }
    const delim = delimiters[0] ?? '';
    const parts = input.split(delim);
    const flatParts = parts.flatMap(p => {
        if (p.length < length) return p;
        return splitRecursive(p, length, delimiters.slice(1));
    });
    const result = [];
    let currentChunk = '';
    for (let i = 0; i < flatParts.length;) {
        currentChunk = flatParts[i];
        let j = i + 1;
        while (j < flatParts.length) {
            const nextChunk = flatParts[j];
            if (currentChunk.length + nextChunk.length + delim.length <= length) {
                currentChunk += delim + nextChunk;
            } else {
                break;
            }
            j++;
        }
        i = j;
        result.push(currentChunk);
    }
    return result;
}

// substituteParams 打桩：恒等（OpenAI instructions 的 macro 替换由 macros-official.mjs 覆盖）
function substituteParams(s) { return s; }

// ---------- ElevenLabs（elevenlabs.js fetchTtsGeneration L332-L361 逐字摘 body 构造） ----------
// request = { model_id, text, voice_settings:{ stability, similarity_boost, speed, [style, use_speaker_boost] } }
// shouldInvolveExtendedSettings = model ∈ ['eleven_v3','eleven_ttv_v3','eleven_multilingual_v2','eleven_multilingual_ttv_v2']
const ELEVENLABS_EXTENDED_MODELS = ['eleven_v3', 'eleven_ttv_v3', 'eleven_multilingual_v2', 'eleven_multilingual_ttv_v2'];
function elevenLabsShouldInvolveExtendedSettings(model) {
    return ELEVENLABS_EXTENDED_MODELS.includes(model);
}
function elevenLabsRequest(settings, text, voiceId) {
    const model = settings.model ?? 'eleven_monolingual_v1';
    const request = {
        model_id: model,
        text: text,
        voice_settings: {
            stability: Number(settings.stability),
            similarity_boost: Number(settings.similarity_boost),
            speed: Number(settings.speed),
        },
    };
    if (elevenLabsShouldInvolveExtendedSettings(model)) {
        request.voice_settings.style = Number(settings.style_exaggeration);
        request.voice_settings.use_speaker_boost = Boolean(settings.speaker_boost);
    }
    return { voiceId: voiceId, request: request };
}

// ---------- OpenAI（openai.js fetchTtsGeneration L222-L251 逐字摘 body 构造） ----------
// requestBody = { text, voice, model, speed, [instructions] }
// 分支：model='gpt-4o-mini-tts' && characterName && instructions.trim() → requestBody.instructions = substituteParams(instructions)
function openAiRequest(settings, inputText, voiceId, characterName, characterInstructions) {
    const requestBody = {
        'text': inputText,
        'voice': voiceId,
        'model': settings.model,
        'speed': settings.speed,
    };
    if (settings.model === 'gpt-4o-mini-tts' && characterName) {
        const instructions = characterInstructions;
        if (instructions && instructions.trim()) {
            requestBody.instructions = substituteParams(instructions);
        }
    }
    return requestBody;
}

// ---------- Edge（edge.js fetchTtsGeneration L167-L188 逐字摘 body 构造） ----------
// body = { text, voice, rate }
function edgeRequest(settings, inputText, voiceId) {
    return {
        text: inputText,
        voice: voiceId,
        rate: Number(settings.rate),
    };
}

// ---------- Azure（azure.js fetchTtsGeneration L182-L207 逐字摘 body 构造） ----------
// body = { text, voice, region }
function azureRequest(settings, text, voiceId) {
    return {
        text: text,
        voice: voiceId,
        region: settings.region,
    };
}

// ---------- NovelAI（novel.js fetchTtsGeneration L193-L214 逐字摘 body 构造） ----------
// generator：splitRecursive(inputText,1000) 分块，每块 body = { text: chunk, voice }
// fixture 只输出第一块 body（chunking 行为是分块调度，body 字段一致）
function novelRequest(settings, inputText, voiceId) {
    const MAX_LENGTH = 1000;
    const chunks = splitRecursive(inputText, MAX_LENGTH);
    const chunk = chunks[0] ?? '';
    return { text: chunk, voice: voiceId };
}

// ---------- MiniMax（minimax.js fetchTtsGeneration L775-L799 逐字摘 body 构造） ----------
// 含 clamp 函数与 defaultSettings 兜底（settings 字段空时取 defaultSettings.*.default 等）
const MINIMAX_DEFAULTS = {
    model: 'speech-02-hd',
    speed: { default: 1.0, min: 0.5, max: 2.0 },
    volume: { default: 50, min: 1, max: 100 },
    pitch: { default: 0, min: -100, max: 100 },
    audioSampleRate: 32000,
    bitrate: 128000,
    format: 'mp3',
};
function minimaxRequest(settings, inputText, voiceId, language) {
    const clamp = (number, lower, upper) => Math.min(Math.max(number, lower), upper);
    return {
        text: inputText,
        voiceId: voiceId,
        apiHost: settings.apiHost,
        model: settings.model || MINIMAX_DEFAULTS.model,
        speed: clamp(Number(settings.speed) || MINIMAX_DEFAULTS.speed.default, MINIMAX_DEFAULTS.speed.min, MINIMAX_DEFAULTS.speed.max),
        volume: clamp(Number(settings.volume) || MINIMAX_DEFAULTS.volume.default, MINIMAX_DEFAULTS.volume.min, MINIMAX_DEFAULTS.volume.max),
        pitch: clamp(Math.round(Number(settings.pitch)) || MINIMAX_DEFAULTS.pitch.default, MINIMAX_DEFAULTS.pitch.min, MINIMAX_DEFAULTS.pitch.max),
        audioSampleRate: Number(settings.audioSampleRate) || MINIMAX_DEFAULTS.audioSampleRate,
        bitrate: Number(settings.bitrate) || MINIMAX_DEFAULTS.bitrate,
        format: settings.format || MINIMAX_DEFAULTS.format,
        language: language,
    };
}

// ---------- Volcengine（volcengine.js fetchTtsGeneration L295-L315 逐字摘 body 构造） ----------
// body = { provider_endpoint, resource_id, text, voice_speaker, speed }
function volcengineRequest(settings, text, voiceSpeaker) {
    return {
        provider_endpoint: settings.provider_endpoint,
        resource_id: settings.resource_id,
        text: text,
        voice_speaker: voiceSpeaker,
        speed: settings.speed,
    };
}

// ---------- Chutes（chutes.js fetchTtsGeneration L194-L217 逐字摘 body 构造） ----------
// body = { input, voice: voiceId || 'af_heart', speed: settings.speed || 1 }
function chutesRequest(settings, text, voiceId) {
    return {
        input: text,
        voice: voiceId || 'af_heart',
        speed: settings.speed || 1,
    };
}

// ---------- Pollinations（pollinations.js fetchTtsGeneration L128-L149 逐字摘 body 构造） ----------
// generator：splitRecursive(text,1000) 分块，每块 body = { model, text:'Say exactly this and nothing else:'+'\n'+chunk, voice }
function pollinationsRequest(settings, text, voiceId) {
    const MAX_LENGTH = 1000;
    const chunks = splitRecursive(text, MAX_LENGTH);
    const chunk = chunks[0] ?? '';
    return {
        model: settings.model,
        text: 'Say exactly this and nothing else:' + '\n' + chunk,
        voice: voiceId,
    };
}

// ---------- Google Native（google-native.js fetchNativeTtsGeneration L160-L178 逐字摘 body 构造） ----------
// body = { text, voice, model, api, reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
// useReverseProxy = oai_settings.reverse_proxy && isValidUrl(oai_settings.reverse_proxy)
//   反代有效 → reverse_proxy = oai_settings.reverse_proxy / proxy_password = oai_settings.proxy_password
//   否则 → 两字段都 = ''
function googleNativeRequest(settings, text, voiceId, oaiSettings) {
    const useReverseProxy = oaiSettings.reverse_proxy && isValidUrl(oaiSettings.reverse_proxy);
    return {
        text: text,
        voice: voiceId,
        model: settings.model,
        api: settings.apiType,
        reverse_proxy: useReverseProxy ? oaiSettings.reverse_proxy : '',
        proxy_password: useReverseProxy ? oaiSettings.proxy_password : '',
        vertexai_auth_mode: oaiSettings.vertexai_auth_mode,
        vertexai_region: oaiSettings.vertexai_region,
        vertexai_express_project_id: oaiSettings.vertexai_express_project_id,
    };
}
// isValidUrl 打桩：与官方 isValidUrl 一致（try new URL(url) 成功即 true）
function isValidUrl(str) {
    try { return Boolean(new URL(str)); } catch { return false; }
}

// ---------- Google Translate（google-translate.js fetchTtsGeneration L123-L139 逐字摘 body 构造） ----------
// body = { text: splitRecursive(text, 200), voice }
function googleTranslateRequest(settings, text, voiceId) {
    return {
        text: splitRecursive(text, 200),
        voice: voiceId,
    };
}

// ---------- cases ----------
const cases = [];
let id = 0;
function add(name, kind, input, expected) {
    cases.push({ id: String(++id).padStart(3, '0') + '-' + name, kind, args: input, expected });
}

// ============ ElevenLabs ============
// defaultSettings: stability=0.75, similarity_boost=0.75, style_exaggeration=0.0, speaker_boost=true, speed=1.0, model=eleven_turbo_v2_5
const EL_DEFAULTS = { stability: 0.75, similarity_boost: 0.75, style_exaggeration: 0.0, speaker_boost: true, speed: 1.0, model: 'eleven_turbo_v2_5' };
add('elevenlabs-defaults-turbo', 'elevenlabs',
    { settings: EL_DEFAULTS, text: 'hello world', voiceId: '21m00Tcm4TlvDq8ikWAM' },
    elevenLabsRequest(EL_DEFAULTS, 'hello world', '21m00Tcm4TlvDq8ikWAM'));
add('elevenlabs-v3-extended-settings', 'elevenlabs',
    // v3 模型应加 style + use_speaker_boost
    { settings: { ...EL_DEFAULTS, model: 'eleven_v3', style_exaggeration: 0.5, speaker_boost: false }, text: 'hi', voiceId: 'vID' },
    elevenLabsRequest({ ...EL_DEFAULTS, model: 'eleven_v3', style_exaggeration: 0.5, speaker_boost: false }, 'hi', 'vID'));
add('elevenlabs-multilingual-v2-extended', 'elevenlabs',
    { settings: { ...EL_DEFAULTS, model: 'eleven_multilingual_v2' }, text: 'bonjour', voiceId: 'v' },
    elevenLabsRequest({ ...EL_DEFAULTS, model: 'eleven_multilingual_v2' }, 'bonjour', 'v'));
add('elevenlabs-turbo-no-extended', 'elevenlabs',
    // turbo v2.5 不应加 style/use_speaker_boost
    { settings: EL_DEFAULTS, text: 'x', voiceId: 'y' },
    elevenLabsRequest(EL_DEFAULTS, 'x', 'y'));
add('elevenlabs-unicode-text', 'elevenlabs',
    { settings: EL_DEFAULTS, text: '你好，世界！', voiceId: 'vid' },
    elevenLabsRequest(EL_DEFAULTS, '你好，世界！', 'vid'));

// ============ OpenAI ============
// defaultSettings: model='tts-1', speed=1
const OAI_DEFAULTS = { model: 'tts-1', speed: 1 };
add('openai-defaults', 'openai',
    { settings: OAI_DEFAULTS, inputText: 'hello', voiceId: 'alloy', characterName: null, characterInstructions: '' },
    openAiRequest(OAI_DEFAULTS, 'hello', 'alloy', null, ''));
add('openai-gpt4o-mini-with-instructions', 'openai',
    // gpt-4o-mini-tts + characterName + 非空 instructions → 加 instructions
    { settings: { model: 'gpt-4o-mini-tts', speed: 1 }, inputText: 'hi', voiceId: 'nova', characterName: 'Alice', characterInstructions: 'Speak cheerfully' },
    openAiRequest({ model: 'gpt-4o-mini-tts', speed: 1 }, 'hi', 'nova', 'Alice', 'Speak cheerfully'));
add('openai-gpt4o-mini-no-character', 'openai',
    // gpt-4o-mini-tts 但无 characterName → 不加 instructions
    { settings: { model: 'gpt-4o-mini-tts', speed: 1 }, inputText: 'hi', voiceId: 'nova', characterName: null, characterInstructions: '' },
    openAiRequest({ model: 'gpt-4o-mini-tts', speed: 1 }, 'hi', 'nova', null, ''));
add('openai-gpt4o-mini-blank-instructions', 'openai',
    // gpt-4o-mini-tts + characterName 但 instructions 为空 → 不加
    { settings: { model: 'gpt-4o-mini-tts', speed: 1 }, inputText: 'hi', voiceId: 'nova', characterName: 'Alice', characterInstructions: '   ' },
    openAiRequest({ model: 'gpt-4o-mini-tts', speed: 1 }, 'hi', 'nova', 'Alice', '   '));
add('openai-tts-1hd-never-instructions', 'openai',
    // tts-1-hd 永远不加 instructions
    { settings: { model: 'tts-1-hd', speed: 2 }, inputText: 'x', voiceId: 'echo', characterName: 'Alice', characterInstructions: 'should not appear' },
    openAiRequest({ model: 'tts-1-hd', speed: 2 }, 'x', 'echo', 'Alice', 'should not appear'));

// ============ Edge ============
add('edge-defaults', 'edge',
    { settings: { rate: 0 }, inputText: 'hello', voiceId: 'en-US-AriaNeural' },
    edgeRequest({ rate: 0 }, 'hello', 'en-US-AriaNeural'));
add('edge-rate-positive', 'edge',
    { settings: { rate: 1.5 }, inputText: 'faster', voiceId: 'zh-CN-XiaoxiaoNeural' },
    edgeRequest({ rate: 1.5 }, 'faster', 'zh-CN-XiaoxiaoNeural'));

// ============ Azure ============
add('azure-defaults', 'azure',
    { settings: { region: 'westus' }, text: 'hello', voiceId: 'en-US-AriaNeural' },
    azureRequest({ region: 'westus' }, 'hello', 'en-US-AriaNeural'));
add('azure-region-asia', 'azure',
    { settings: { region: 'eastasia' }, text: '你好', voiceId: 'zh-CN-XiaoxiaoNeural' },
    azureRequest({ region: 'eastasia' }, '你好', 'zh-CN-XiaoxiaoNeural'));

// ============ Novel ============
add('novel-short', 'novel',
    { settings: {}, inputText: 'hello world', voiceId: 'Ligeia' },
    novelRequest({}, 'hello world', 'Ligeia'));
add('novel-long-split', 'novel',
    // 超过 MAX_LENGTH=1000 应分块，body.text 取首块
    { settings: {}, inputText: 'a'.repeat(1500), voiceId: 'Orea' },
    novelRequest({}, 'a'.repeat(1500), 'Orea'));
add('novel-multichunk-delimiters', 'novel',
    // 含换行+空格的分块边界
    { settings: {}, inputText: 'word1 word2 word3\nword4 word5', voiceId: 'Lim' },
    novelRequest({}, 'word1 word2 word3\nword4 word5', 'Lim'));

// ============ Minimax ============
const MM_DEFAULTS = { apiHost: 'https://api.minimax.io', model: '', speed: '', volume: '', pitch: '', audioSampleRate: '', bitrate: '', format: '' };
add('minimax-defaults-fallback', 'minimax',
    // 全空 → 全部取 defaultSettings 兜底
    { settings: MM_DEFAULTS, inputText: 'hello', voiceId: 'voice1', language: 'en_US' },
    minimaxRequest(MM_DEFAULTS, 'hello', 'voice1', 'en_US'));
add('minimax-clamp-speed', 'minimax',
    // speed=10 → clamp 到 2.0（max）
    { settings: { ...MM_DEFAULTS, speed: 10 }, inputText: 'x', voiceId: 'v', language: null },
    minimaxRequest({ ...MM_DEFAULTS, speed: 10 }, 'x', 'v', null));
add('minimax-clamp-pitch-negative', 'minimax',
    // pitch=-500 → round 后 clamp 到 -100
    { settings: { ...MM_DEFAULTS, pitch: -500 }, inputText: 'x', voiceId: 'v', language: 'zh_CN' },
    minimaxRequest({ ...MM_DEFAULTS, pitch: -500 }, 'x', 'v', 'zh_CN'));
add('minimax-explicit-model', 'minimax',
    { settings: { ...MM_DEFAULTS, model: 'speech-02-turbo' }, inputText: 'hi', voiceId: 'v', language: 'ja_JP' },
    minimaxRequest({ ...MM_DEFAULTS, model: 'speech-02-turbo' }, 'hi', 'v', 'ja_JP'));

// ============ Volcengine ============
add('volcengine-defaults', 'volcengine',
    { settings: { provider_endpoint: 'https://openspeech.bytedance.com', resource_id: 'volcservice_tts', speed: 1.0 }, text: 'hello', voiceSpeaker: 'zh_female_xiaohe' },
    volcengineRequest({ provider_endpoint: 'https://openspeech.bytedance.com', resource_id: 'volcservice_tts', speed: 1.0 }, 'hello', 'zh_female_xiaohe'));
add('volcengine-unicode', 'volcengine',
    { settings: { provider_endpoint: 'https://x.com', resource_id: 'rid', speed: 0.8 }, text: '你好', voiceSpeaker: 'saturn_zh_female_keainvsheng_tob' },
    volcengineRequest({ provider_endpoint: 'https://x.com', resource_id: 'rid', speed: 0.8 }, '你好', 'saturn_zh_female_keainvsheng_tob'));

// ============ Chutes ============
add('chutes-defaults', 'chutes',
    // voiceId='' → 用 'af_heart' 默认；speed=0 → 用 1
    { settings: { speed: 0 }, text: 'hello', voiceId: '' },
    chutesRequest({ speed: 0 }, 'hello', ''));
add('chutes-explicit-voice', 'chutes',
    { settings: { speed: 1.5 }, text: 'hi', voiceId: 'am_adam' },
    chutesRequest({ speed: 1.5 }, 'hi', 'am_adam'));
add('chutes-speed-zero-fallback', 'chutes',
    // speed=0 → 用 1（JS || 短路）
    { settings: { speed: 0 }, text: 'x', voiceId: 'af_heart' },
    chutesRequest({ speed: 0 }, 'x', 'af_heart'));

// ============ Pollinations ============
add('pollinations-defaults', 'pollinations',
    { settings: { model: 'openai-audio' }, text: 'hello', voiceId: 'alloy' },
    pollinationsRequest({ model: 'openai-audio' }, 'hello', 'alloy'));
add('pollinations-long-split', 'pollinations',
    // 超过 1000 分块，body.text 取首块并加前缀
    { settings: { model: 'openai-audio' }, text: 'y'.repeat(1500), voiceId: 'echo' },
    pollinationsRequest({ model: 'openai-audio' }, 'y'.repeat(1500), 'echo'));
add('pollinations-prefix-semantic', 'pollinations',
    // 验证 'Say exactly this and nothing else:' + '\n' + chunk 前缀逻辑
    { settings: { model: 'm' }, text: 'say hi', voiceId: 'v' },
    pollinationsRequest({ model: 'm' }, 'say hi', 'v'));

// ============ Google Native ============
const GN_DEFAULTS = { model: 'en-US-Standard-A', apiType: 'generate' };
add('google-native-no-reverse-proxy', 'google-native',
    // 无 reverse_proxy → reverse_proxy='' / proxy_password=''
    { settings: GN_DEFAULTS, text: 'hello', voiceId: 'en-US-Standard-A', oaiSettings: { reverse_proxy: '', proxy_password: '', vertexai_auth_mode: 'auto', vertexai_region: 'us-central1', vertexai_express_project_id: '' } },
    googleNativeRequest(GN_DEFAULTS, 'hello', 'en-US-Standard-A', { reverse_proxy: '', proxy_password: '', vertexai_auth_mode: 'auto', vertexai_region: 'us-central1', vertexai_express_project_id: '' }));
add('google-native-with-reverse-proxy', 'google-native',
    // 有效 reverse_proxy → 透传
    { settings: GN_DEFAULTS, text: 'hi', voiceId: 'v', oaiSettings: { reverse_proxy: 'https://proxy.example.com/v1', proxy_password: 'secret', vertexai_auth_mode: 'express', vertexai_region: 'global', vertexai_express_project_id: 'my-proj' } },
    googleNativeRequest(GN_DEFAULTS, 'hi', 'v', { reverse_proxy: 'https://proxy.example.com/v1', proxy_password: 'secret', vertexai_auth_mode: 'express', vertexai_region: 'global', vertexai_express_project_id: 'my-proj' }));
add('google-native-invalid-reverse-proxy', 'google-native',
    // 无效 reverse_proxy（非 URL）→ 视同无
    { settings: GN_DEFAULTS, text: 'x', voiceId: 'v', oaiSettings: { reverse_proxy: 'not-a-url', proxy_password: 'pw', vertexai_auth_mode: 'auto', vertexai_region: 'r', vertexai_express_project_id: 'p' } },
    googleNativeRequest(GN_DEFAULTS, 'x', 'v', { reverse_proxy: 'not-a-url', proxy_password: 'pw', vertexai_auth_mode: 'auto', vertexai_region: 'r', vertexai_express_project_id: 'p' }));

// ============ Google Translate ============
add('google-translate-short', 'google-translate',
    { settings: {}, text: 'hello world', voiceId: 'en-US' },
    googleTranslateRequest({}, 'hello world', 'en-US'));
add('google-translate-split-200', 'google-translate',
    // 超过 200 → body.text 是数组（多块）
    { settings: {}, text: 'a'.repeat(500), voiceId: 'zh-CN' },
    googleTranslateRequest({}, 'a'.repeat(500), 'zh-CN'));
add('google-translate-multiline-split', 'google-translate',
    { settings: {}, text: 'line1\n\nline2\nline3', voiceId: 'ja-JP' },
    googleTranslateRequest({}, 'line1\n\nline2\nline3', 'ja-JP'));

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`tts-services fixtures: ${cases.length} cases -> ${outFile}`);
