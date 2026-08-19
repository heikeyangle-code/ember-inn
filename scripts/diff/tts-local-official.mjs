#!/usr/bin/env node
// 官方 TTS 扩展本地后端 fetchTtsGeneration / generateTts 内联请求体/URL 纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   public/scripts/extensions/tts/{backend}.js 的 fetchTtsGeneration 函数体内 body/URL 构造段
//   （无 fetchTtsGeneration 者对照 generateTts 内联构造段）。
//
// 打桩（脚本头部登记）：
// - this.settings = 入参，由 cases 注入（用官方 defaultSettings 默认值）
// - fetch / response / console / toastr = 不调用（脚本只跑 body/URL 构造）
// - getApiUrl / getCharacters / getPreviewString = 打桩常量
// - Math.random = monkey-patch 返回 0（保证 fixture 确定性）
//
// 边界（不差分，登记）：
// - kokoro.js / kokoro-worker.js：官方为浏览器 WebWorker（postMessage，无 HTTP 请求体），
//   Android 无 Worker，App 简化为 POST /tts JSON {text, voice} — 与官方不同源，登记不差分。
// - openai-compatible.js：官方走 ST 代理 /api/openai/custom/generate-voice，body 含 provider_endpoint
//   字段（ST 转发用）；App 直连厂商 /v1/audio/speech，body 不含 provider_endpoint — 不同源，登记不差分。
// - speecht5.js：官方 speaker 字段取自 getVoice(voiceId).data（运行时拉取的 speaker 嵌入），
//   App 用 voiceId 直接传入 — 值源不同但 body 字段集合一致；此处差分 body 构造（speaker 作不透明入参）。
// - 各后端的 getVoices / fetchTtsVoiceObjects / previewTtsVoice / changeTTSSettings / fetchTtsFromHistory
//   等非生成行为不差分。
//
// 重要语义：
// - JS `URLSearchParams.toString()` → application/x-www-form-urlencoded（space=+，与 Java URLEncoder.encode 一致）。
// - JS `parseInt('none')` = NaN → JSON.stringify(NaN) = null。Kotlin 用 toIntOrNull() ?: JSONObject.NULL 等价。
// - JS `Math.floor(Math.random() * 2147483648)` 在 fixture 生成中打桩为常量（Math.random=()=>0 → 0）。
// - JS `this.settings.seed >= 0 ? this.settings.seed : Math.floor(...)` 短路：seed=0 也传 0。
// - JS `replaceAll` 与 Kotlin String.replace(Regex, "") 语义对齐（/g 全局）。
// - JS `voiceId.split('&')` 与 `split('-', n)` 行为：JS 不传 limit，取前 N 个；Kotlin 用 limit=2 等价。
// - JS `Object.fromEntries(filter)` (tts-webui chatterboxParams) → 仅保留指定键的子对象，Kotlin 逐字段 put 等价。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'tts-local.json');

// ========== 官方函数逐字摘（仅保留 body/URL 构造段，去除 fetch/response/console） ==========

// ---------- alltalk.js fetchTtsGeneration L990-L1015 逐字摘 ----------
// requestBody = new URLSearchParams({ 14 字段 }); V2 + RVC 条件 append 4 字段。
// 返回 { url: ".../api/tts-generate", form: "k1=v1&k2=v2..." }（form 用 URLSearchParams.toString 语义）。
function allTalkBody(settings, inputText, voiceId) {
    const params = new URLSearchParams({
        'text_input': inputText,
        'text_filtering': 'standard',
        'character_voice_gen': voiceId,
        'narrator_enabled': settings.narrator_enabled,
        'narrator_voice_gen': settings.narrator_voice_gen,
        'text_not_inside': settings.at_narrator_text_not_inside,
        'language': settings.language,
        'output_file_name': 'st_output',
        'output_file_timestamp': 'true',
        'autoplay': 'false',
        'autoplay_volume': '0.8',
    });
    if (settings.server_version === 'v2') {
        if (settings.rvc_character_voice !== 'Disabled') {
            params.append('rvccharacter_voice_gen', settings.rvc_character_voice);
            params.append('rvccharacter_pitch', settings.rvc_character_pitch || '0');
        }
        if (settings.rvc_narrator_voice !== 'Disabled') {
            params.append('rvcnarrator_voice_gen', settings.rvc_narrator_voice);
            params.append('rvcnarrator_pitch', settings.rvc_narrator_pitch || '0');
        }
    }
    return { form: params.toString() };
}

// ---------- chatterbox.js generateTts L572-L605 逐字摘 ----------
// requestBody = { text, voice_mode, temperature, exaggeration, cfg_weight, seed, speed_factor, language,
//   split_text, chunk_size, output_format, [reference_audio_filename | predefined_voice_id] }
// seed: settings.seed >= 0 ? settings.seed : Math.floor(Math.random() * 2147483648)
// voiceId 'ref_' 前缀 → clone 模式（reference_audio_filename）；否则 predefined（predefined_voice_id = actualVoiceId || settings.predefined_voice）
function chatterboxBody(settings, inputText, voiceId) {
    let isReferenceVoice = false;
    let actualVoiceId = voiceId;
    if (voiceId && voiceId.startsWith('ref_')) {
        isReferenceVoice = true;
        actualVoiceId = voiceId.substring(4);
    }
    const requestBody = {
        text: inputText,
        voice_mode: isReferenceVoice ? 'clone' : 'predefined',
        temperature: settings.temperature,
        exaggeration: settings.exaggeration,
        cfg_weight: settings.cfg_weight,
        seed: settings.seed >= 0 ? settings.seed : Math.floor(Math.random() * 2147483648),
        speed_factor: settings.speed_factor,
        language: settings.language,
        split_text: settings.split_text,
        chunk_size: settings.chunk_size,
        output_format: settings.output_format,
    };
    if (isReferenceVoice) {
        requestBody.reference_audio_filename = actualVoiceId;
    } else {
        requestBody.predefined_voice_id = actualVoiceId || settings.predefined_voice;
    }
    return requestBody;
}

// ---------- coqui.js generateTts L674-L714 逐字摘 ----------
// voiceId = settings.customVoices[voiceId]（先映射；App 无 customVoices → 恒等映射，打桩）
// tokens = voiceId.replaceAll(']','').replaceAll('"','').split('[')
// model_id = tokens[0]; language='none'; speaker='none'
// tokens.length>1: model_id.includes('multilingual') ? language=tokens[1] : speaker=tokens[1]
// tokens.length>2: speaker=tokens[2]
// body = { text, model_id, language_id: parseInt(language), speaker_id: parseInt(speaker) }
// parseInt('none')=NaN → JSON null
function coquiBody(settings, inputText, voiceId) {
    // 打桩：customVoices 恒等映射（App 无此字段）
    const mapped = (settings.customVoices && settings.customVoices[voiceId]) ? settings.customVoices[voiceId] : voiceId;
    const tokens = mapped.replaceAll(']', '').replaceAll('"', '').split('[');
    const model_id = tokens[0];
    let language = 'none';
    let speaker = 'none';
    if (tokens.length > 1) {
        const option1 = tokens[1];
        if (model_id.includes('multilingual')) language = option1;
        else speaker = option1;
    }
    if (tokens.length > 2) speaker = tokens[2];
    return {
        text: inputText,
        model_id: model_id,
        language_id: parseInt(language),
        speaker_id: parseInt(speaker),
    };
}

// ---------- cosyvoice.js fetchTtsGeneration L162-L187 逐字摘 ----------
// params = { text, speaker }; if (settings.streaming) params.streaming = 1; → body
function cosyVoiceBody(settings, inputText, voiceId) {
    const params = { text: inputText, speaker: voiceId };
    if (settings.streaming) {
        params.streaming = 1;
    }
    return params;
}

// ---------- gpt-sovits-adapter.js fetchTtsGeneration L195-L220 逐字摘 ----------
// params = { text, card_name: getCharacters(false), use_st_adapter: true, target_voice: voiceId,
//   text_lang: settings.text_lang, text_split_method: 'cut5', batch_size: 1,
//   media_type: settings.media_type, streaming_mode: 'true' }
// 打桩：getCharacters(false) = ''（Android 无角色上下文）
function gptSoVitsAdapterBody(settings, inputText, voiceId) {
    return {
        text: inputText,
        card_name: '',
        use_st_adapter: true,
        target_voice: voiceId,
        text_lang: settings.text_lang,
        text_split_method: 'cut5',
        batch_size: 1,
        media_type: settings.media_type,
        streaming_mode: 'true',
    };
}

// ---------- gpt-sovits-v2.js fetchTtsGeneration L169-L202 逐字摘 ----------
// replaceSpeaker(text) = text.replace(/\[.*?\]/gu, '')
// params = { text, prompt_text: replaceSpeaker(voiceId), ref_audio_path: './参考音频/'+voiceId+'.wav',
//   text_lang: settings.text_lang, prompt_lang: settings.prompt_lang, text_split_method: 'cut5',
//   batch_size: 1, media_type: 'ogg', streaming_mode: 'true' }
function gptSoVitsV2Body(settings, inputText, voiceId) {
    function replaceSpeaker(text) {
        return text.replace(/\[.*?\]/gu, '');
    }
    const prompt_text = replaceSpeaker(voiceId);
    return {
        text: inputText,
        prompt_text: prompt_text,
        ref_audio_path: './参考音频/' + voiceId + '.wav',
        text_lang: settings.text_lang,
        prompt_lang: settings.prompt_lang,
        text_split_method: 'cut5',
        batch_size: 1,
        media_type: 'ogg',
        streaming_mode: 'true',
    };
}

// ---------- gsvi.js fetchTtsGeneration L235-L251 逐字摘 ----------
// params = new URLSearchParams(); append 8 字段；返回 url = endpoint + "/tts?" + params.toString()
// 字段：text, cha_name, text_language, batch_size(toString), speed(toString), top_k(toString),
//      top_p(toString), temperature(toString), stream(toString)
function gsviUrl(settings, inputText, voiceId) {
    const params = new URLSearchParams();
    params.append('text', inputText);
    params.append('cha_name', voiceId);
    params.append('text_language', settings.language);
    params.append('batch_size', settings.batch_size.toString());
    params.append('speed', settings.speed.toString());
    params.append('top_k', settings.top_k.toString());
    params.append('top_p', settings.top_p.toString());
    params.append('temperature', settings.temperature.toString());
    params.append('stream', settings.stream.toString());
    return { query: params.toString() };
}

// ---------- sbvits2.js fetchTtsGeneration L276-L318 逐字摘 ----------
// [model_id, speaker_id, ...rest] = voiceId.split('-'); style = rest.join('-')
// inputText = inputText.replaceAll('<br>', '\n')
// params = new URLSearchParams(); append text, model_id, speaker_id, sdp_ratio, noise, noisew, length,
//   language, auto_split, split_interval
// if (settings.assist_text) append assist_text, assist_text_weight
// append style, style_weight
// if (settings.reference_audio_path) append reference_audio_path
// 返回 url = endpoint + "/voice?" + params.toString()
function sbvits2Url(settings, inputText, voiceId) {
    const [model_id, speaker_id, ...rest] = voiceId.split('-');
    const style = rest.join('-');
    const params = new URLSearchParams();
    inputText = inputText.replaceAll('<br>', '\n');
    params.append('text', inputText);
    params.append('model_id', model_id);
    params.append('speaker_id', speaker_id);
    params.append('sdp_ratio', settings.sdp_ratio);
    params.append('noise', settings.noise);
    params.append('noisew', settings.noisew);
    params.append('length', settings.length);
    params.append('language', settings.language);
    params.append('auto_split', settings.auto_split);
    params.append('split_interval', settings.split_interval);
    if (settings.assist_text) {
        params.append('assist_text', settings.assist_text);
        params.append('assist_text_weight', settings.assist_text_weight);
    }
    params.append('style', style);
    params.append('style_weight', settings.style_weight);
    if (settings.reference_audio_path) {
        params.append('reference_audio_path', settings.reference_audio_path);
    }
    return { query: params.toString() };
}

// ---------- silerotts.js fetchTtsGeneration L122-L144 逐字摘 ----------
// body = { text, speaker: voiceId, session: 'sillytavern' }
function sileroBody(settings, inputText, voiceId) {
    return {
        text: inputText,
        speaker: voiceId,
        session: 'sillytavern',
    };
}

// ---------- speecht5.js fetchTtsGeneration L166-L186 逐字摘 ----------
// speaker = await this.getVoice(voiceId) → speaker.data（运行时拉取，打桩为入参 speakerData）
// body = { text, speaker: speakerData, model: 'Xenova/speecht5_tts' }
function speechT5Body(settings, inputText, voiceId, speakerData) {
    return {
        text: inputText,
        speaker: speakerData,
        model: 'Xenova/speecht5_tts',
    };
}

// ---------- tts-webui.js fetchTtsGeneration L489-L527 逐字摘 ----------
// chatterboxParams = [15 字段名]；getParams = Object.fromEntries(filter settings by chatterboxParams)
// requestBody = { model: settings.model, voice: voiceId, input: inputText, response_format: 'wav',
//   speed: settings.speed, stream: settings.streaming, params: getParams(settings) }
function ttsWebuiBody(settings, inputText, voiceId) {
    const chatterboxParams = [
        'desired_length', 'max_length', 'halve_first_chunk', 'exaggeration', 'cfg_weight',
        'temperature', 'device', 'dtype', 'cpu_offload', 'chunked', 'cache_voice',
        'tokens_per_slice', 'remove_milliseconds', 'remove_milliseconds_start',
        'chunk_overlap_method', 'seed',
    ];
    const params = {};
    for (const key of chatterboxParams) {
        if (key in settings) {
            params[key] = settings[key];
        }
    }
    return {
        model: settings.model,
        voice: voiceId,
        input: inputText,
        response_format: 'wav',
        speed: settings.speed,
        stream: settings.streaming,
        params: params,
    };
}

// ---------- vits.js fetchTtsGeneration L317-L355 逐字摘 ----------
// streaming = !forceNoStreaming && settings.streaming
// [model_type, speaker_id] = voiceId.split('&')
// params = new URLSearchParams(); append text, id=speaker_id
// if (streaming) append streaming; else append format=settings.format
// append lang=lang ?? settings.lang, length, noise, noisew, segment_size
// if (model_type == W2V2_VITS) append emotion=settings.dim_emotion
// else if (model_type == BERT_VITS2) append sdp_ratio, emotion; if text_prompt append; if style_text append + style_weight
// 非流式：POST /voice/{model_type.toLowerCase()} body=params（form-urlencoded）
// 流式：return url + "?"+params（URL）— 本差分锁非流式分支（forceNoStreaming=true）
// modelTypes 常量：VITS='VITS', W2V2_VITS='W2V2-VITS', BERT_VITS2='BERT-VITS2'
function vitsForm(settings, inputText, voiceId, forceNoStreaming) {
    const streaming = !forceNoStreaming && settings.streaming;
    const split = voiceId.split('&');
    const model_type = split[0];
    const speaker_id = split.length > 1 ? split[1] : undefined;
    const params = new URLSearchParams();
    params.append('text', inputText);
    params.append('id', speaker_id);
    if (streaming) {
        params.append('streaming', streaming);
    } else {
        params.append('format', settings.format);
    }
    params.append('lang', settings.lang);
    params.append('length', settings.length);
    params.append('noise', settings.noise);
    params.append('noisew', settings.noisew);
    params.append('segment_size', settings.segment_size);
    if (model_type === 'W2V2-VITS') {
        params.append('emotion', settings.dim_emotion);
    } else if (model_type === 'BERT-VITS2') {
        params.append('sdp_ratio', settings.sdp_ratio);
        params.append('emotion', settings.emotion);
        if (settings.text_prompt) {
            params.append('text_prompt', settings.text_prompt);
        }
        if (settings.style_text) {
            params.append('style_text', settings.style_text);
            params.append('style_weight', settings.style_weight);
        }
    }
    return { form: params.toString(), model_type: model_type.toLowerCase() };
}

// ---------- xtts.js fetchTtsGeneration L289-L320 逐字摘 ----------
// 流式分支：return url（GET），登记不差分
// 非流式：body = { text: inputText, speaker_wav: voiceId, language: settings.language }
// 注：processText(text) 在官方定义但 fetchTtsGeneration 不调用 — 不差分 processText（死代码）
function xttsBody(settings, inputText, voiceId) {
    return {
        text: inputText,
        speaker_wav: voiceId,
        language: settings.language,
    };
}

// ============ cases ============
const cases = [];
let id = 0;
function add(name, kind, input, expected) {
    cases.push({ id: String(++id).padStart(3, '0') + '-' + name, kind, args: input, expected });
}

// 固定随机数打桩：Math.random = 0（chatterbox seed 回退 → Math.floor(0 * 2147483648) = 0）
const originalRandom = Math.random;
Math.random = () => 0;

// ---------- alltalk defaultSettings ----------
const ALLTALK_DEFAULTS = {
    server_version: 'v2', language: 'en', narrator_enabled: 'false',
    at_narrator_text_not_inside: 'narrator', narrator_voice_gen: 'Please set a voice',
    rvc_character_voice: 'Disabled', rvc_character_pitch: '0',
    rvc_narrator_voice: 'Disabled', rvc_narrator_pitch: '0',
};
add('alltalk-defaults', 'alltalk',
    { settings: ALLTALK_DEFAULTS, inputText: 'hello world', voiceId: 'en_0' },
    allTalkBody(ALLTALK_DEFAULTS, 'hello world', 'en_0'));
add('alltalk-v1-no-rvc', 'alltalk',
    { settings: { ...ALLTALK_DEFAULTS, server_version: 'v1' }, inputText: 'hi', voiceId: 'v1voice' },
    allTalkBody({ ...ALLTALK_DEFAULTS, server_version: 'v1' }, 'hi', 'v1voice'));
const alltalkRvcChar = { ...ALLTALK_DEFAULTS, rvc_character_voice: 'myrvc.wav', rvc_character_pitch: '-5' };
add('alltalk-v2-rvc-char', 'alltalk',
    { settings: alltalkRvcChar, inputText: 'space & special=+', voiceId: 'en_0' },
    allTalkBody(alltalkRvcChar, 'space & special=+', 'en_0'));
const alltalkRvcBoth = { ...ALLTALK_DEFAULTS, rvc_character_voice: 'char.wav', rvc_character_pitch: '3',
    rvc_narrator_voice: 'nar.wav', rvc_narrator_pitch: '-2' };
add('alltalk-v2-rvc-both', 'alltalk',
    { settings: alltalkRvcBoth, inputText: 'narrator test', voiceId: 'voice1' },
    allTalkBody(alltalkRvcBoth, 'narrator test', 'voice1'));

// ---------- chatterbox defaultSettings ----------
const CHATTERBOX_DEFAULTS = {
    temperature: 0.8, exaggeration: 0.5, cfg_weight: 0.5, seed: -1,
    speed_factor: 1.0, language: 'en', split_text: true, chunk_size: 120,
    output_format: 'wav', predefined_voice: 'S1',
};
add('chatterbox-predefined-defaults', 'chatterbox',
    { settings: CHATTERBOX_DEFAULTS, inputText: 'hello', voiceId: 'S1' },
    chatterboxBody(CHATTERBOX_DEFAULTS, 'hello', 'S1'));
add('chatterbox-predefined-empty-fallback', 'chatterbox',
    { settings: CHATTERBOX_DEFAULTS, inputText: 'hi', voiceId: '' },
    chatterboxBody(CHATTERBOX_DEFAULTS, 'hi', ''));
add('chatterbox-clone-ref', 'chatterbox',
    { settings: CHATTERBOX_DEFAULTS, inputText: 'clone this', voiceId: 'ref_myfile.wav' },
    chatterboxBody(CHATTERBOX_DEFAULTS, 'clone this', 'ref_myfile.wav'));
add('chatterbox-seed-positive', 'chatterbox',
    { settings: { ...CHATTERBOX_DEFAULTS, seed: 42 }, inputText: 'seeded', voiceId: 'S2' },
    chatterboxBody({ ...CHATTERBOX_DEFAULTS, seed: 42 }, 'seeded', 'S2'));
add('chatterbox-seed-zero-passes', 'chatterbox',
    { settings: { ...CHATTERBOX_DEFAULTS, seed: 0 }, inputText: 'zero', voiceId: 'S1' },
    chatterboxBody({ ...CHATTERBOX_DEFAULTS, seed: 0 }, 'zero', 'S1'));

// ---------- coqui ----------
add('coqui-simple-model', 'coqui',
    { settings: {}, inputText: 'hello', voiceId: 'tts_models/en/ljspeech/glow-tts' },
    coquiBody({}, 'hello', 'tts_models/en/ljspeech/glow-tts'));
add('coqui-multilingual-language', 'coqui',
    { settings: {}, inputText: 'hola', voiceId: 'tts_models/multilingual/multi-dataset/your_tts[2]' },
    coquiBody({}, 'hola', 'tts_models/multilingual/multi-dataset/your_tts[2]'));
add('coqui-three-tokens-speaker', 'coqui',
    { settings: {}, inputText: 'hi', voiceId: 'tts_models/en/ljspeech/tacotron2-DDC[1][5]' },
    coquiBody({}, 'hi', 'tts_models/en/ljspeech/tacotron2-DDC[1][5]'));
add('coqui-quotes-stripped', 'coqui',
    { settings: {}, inputText: 'q', voiceId: 'model"with"quotes[1]' },
    coquiBody({}, 'q', 'model"with"quotes[1]'));

// ---------- cosyvoice ----------
const COSY_DEFAULTS = { streaming: false };
add('cosyvoice-defaults', 'cosyvoice',
    { settings: COSY_DEFAULTS, inputText: 'hello', voiceId: 'spk1' },
    cosyVoiceBody(COSY_DEFAULTS, 'hello', 'spk1'));
add('cosyvoice-streaming-true', 'cosyvoice',
    { settings: { streaming: true }, inputText: 'stream', voiceId: 'spk2' },
    cosyVoiceBody({ streaming: true }, 'stream', 'spk2'));

// ---------- gpt-sovits-adapter ----------
const GSV_ADAPTER_DEFAULTS = { text_lang: 'zh', media_type: 'auto' };
add('gsv-adapter-defaults', 'gpt-sovits-adapter',
    { settings: GSV_ADAPTER_DEFAULTS, inputText: '你好', voiceId: 'spk1' },
    gptSoVitsAdapterBody(GSV_ADAPTER_DEFAULTS, '你好', 'spk1'));
add('gsv-adapter-en', 'gpt-sovits-adapter',
    { settings: { text_lang: 'en', media_type: 'wav' }, inputText: 'hello', voiceId: 'spk2' },
    gptSoVitsAdapterBody({ text_lang: 'en', media_type: 'wav' }, 'hello', 'spk2'));

// ---------- gpt-sovits-v2 ----------
const GSV_V2_DEFAULTS = { text_lang: 'zh', prompt_lang: 'zh' };
add('gsv-v2-defaults', 'gpt-sovits-v2',
    { settings: GSV_V2_DEFAULTS, inputText: '你好', voiceId: 'speaker1' },
    gptSoVitsV2Body(GSV_V2_DEFAULTS, '你好', 'speaker1'));
add('gsv-v2-bracket-stripped', 'gpt-sovits-v2',
    { settings: GSV_V2_DEFAULTS, inputText: 'hi', voiceId: 'Alice[protagonist]' },
    gptSoVitsV2Body(GSV_V2_DEFAULTS, 'hi', 'Alice[protagonist]'));
add('gsv-v2-en', 'gpt-sovits-v2',
    { settings: { text_lang: 'en', prompt_lang: 'en' }, inputText: 'hello', voiceId: 'Bob' },
    gptSoVitsV2Body({ text_lang: 'en', prompt_lang: 'en' }, 'hello', 'Bob'));

// ---------- gsvi ----------
const GSVI_DEFAULTS = { language: '多语种混合', batch_size: 10, speed: 1, top_k: 6, top_p: 0.85, temperature: 0.75, stream: false };
add('gsvi-defaults', 'gsvi',
    { settings: GSVI_DEFAULTS, inputText: '你好', voiceId: 'char1' },
    gsviUrl(GSVI_DEFAULTS, '你好', 'char1'));
add('gsvi-space-encoded', 'gsvi',
    { settings: GSVI_DEFAULTS, inputText: 'hello world & special=+', voiceId: 'char2' },
    gsviUrl(GSVI_DEFAULTS, 'hello world & special=+', 'char2'));

// ---------- sbvits2 ----------
const SBVITS2_DEFAULTS = { sdp_ratio: 0.2, noise: 0.6, noisew: 0.8, length: 1, language: 'JP',
    auto_split: true, split_interval: 0.5, style_weight: 1, assist_text: '', reference_audio_path: '' };
add('sbvits2-defaults', 'sbvits2',
    { settings: SBVITS2_DEFAULTS, inputText: 'hello', voiceId: '0-0-Neutral' },
    sbvits2Url(SBVITS2_DEFAULTS, 'hello', '0-0-Neutral'));
add('sbvits2-br-replaced', 'sbvits2',
    { settings: SBVITS2_DEFAULTS, inputText: 'line1<br>line2', voiceId: '1-2-Happy' },
    sbvits2Url(SBVITS2_DEFAULTS, 'line1<br>line2', '1-2-Happy'));
const sbvAssist = { ...SBVITS2_DEFAULTS, assist_text: 'context', assist_text_weight: 0.5, reference_audio_path: '/path/ref.wav' };
add('sbvits2-assist-and-ref', 'sbvits2',
    { settings: sbvAssist, inputText: 'with assist', voiceId: '2-3-Angry' },
    sbvits2Url(sbvAssist, 'with assist', '2-3-Angry'));
add('sbvits2-style-multi-dash', 'sbvits2',
    { settings: SBVITS2_DEFAULTS, inputText: 'multi', voiceId: '0-0-My-Style-Name' },
    sbvits2Url(SBVITS2_DEFAULTS, 'multi', '0-0-My-Style-Name'));

// ---------- silerotts ----------
add('silero-defaults', 'silerotts',
    { settings: {}, inputText: 'привет', voiceId: 'kseniya' },
    sileroBody({}, 'привет', 'kseniya'));
add('silero-special-chars', 'silerotts',
    { settings: {}, inputText: 'a & b = c', voiceId: 'aidar' },
    sileroBody({}, 'a & b = c', 'aidar'));

// ---------- speecht5 ----------
add('speecht5-defaults', 'speecht5',
    { settings: {}, inputText: 'hello', voiceId: 'p226', speakerData: '<2048-byte-base64-embedding>' },
    speechT5Body({}, 'hello', 'p226', '<2048-byte-base64-embedding>'));

// ---------- tts-webui ----------
const TTSWEBUI_DEFAULTS = {
    model: 'chatterbox', speed: 1, streaming: false,
    desired_length: 80, max_length: 200, halve_first_chunk: true,
    exaggeration: 0.5, cfg_weight: 0.5, temperature: 0.8, device: 'auto', dtype: 'float32',
    cpu_offload: false, chunked: true, cache_voice: false, tokens_per_slice: 1000,
    remove_milliseconds: 45, remove_milliseconds_start: 25, chunk_overlap_method: 'zero', seed: -1,
};
add('tts-webui-defaults', 'tts-webui',
    { settings: TTSWEBUI_DEFAULTS, inputText: 'hello', voiceId: 'voice1' },
    ttsWebuiBody(TTSWEBUI_DEFAULTS, 'hello', 'voice1'));
add('tts-webui-streaming-true', 'tts-webui',
    { settings: { ...TTSWEBUI_DEFAULTS, streaming: true }, inputText: 'stream', voiceId: 'voice2' },
    ttsWebuiBody({ ...TTSWEBUI_DEFAULTS, streaming: true }, 'stream', 'voice2'));
const webuiSubset = { ...TTSWEBUI_DEFAULTS, model: 'other-model', speed: 2, extra_field: 'ignored' };
add('tts-webui-filters-unknown-keys', 'tts-webui',
    { settings: webuiSubset, inputText: 'filter', voiceId: 'v3' },
    ttsWebuiBody(webuiSubset, 'filter', 'v3'));

// ---------- vits ----------
const VITS_DEFAULTS = { streaming: false, format: 'wav', lang: 'auto', length: 1.0,
    noise: 0.33, noisew: 0.4, segment_size: 50, dim_emotion: 0, sdp_ratio: 0.2, emotion: 0,
    text_prompt: '', style_text: '', style_weight: 1 };
add('vits-vits-defaults', 'vits',
    { settings: VITS_DEFAULTS, inputText: 'hello', voiceId: 'VITS&0', forceNoStreaming: true },
    vitsForm(VITS_DEFAULTS, 'hello', 'VITS&0', true));
add('vits-w2v2-emotion', 'vits',
    { settings: { ...VITS_DEFAULTS, dim_emotion: 5 }, inputText: 'hi', voiceId: 'W2V2-VITS&1', forceNoStreaming: true },
    vitsForm({ ...VITS_DEFAULTS, dim_emotion: 5 }, 'hi', 'W2V2-VITS&1', true));
const vitsBERT = { ...VITS_DEFAULTS, sdp_ratio: 0.3, emotion: 2, text_prompt: 'happy', style_text: 'sad', style_weight: 0.8 };
add('vits-bert-full', 'vits',
    { settings: vitsBERT, inputText: 'bert', voiceId: 'BERT-VITS2&3', forceNoStreaming: true },
    vitsForm(vitsBERT, 'bert', 'BERT-VITS2&3', true));
add('vits-bert-no-style-text', 'vits',
    { settings: { ...VITS_DEFAULTS, text_prompt: 'p', style_text: '' }, inputText: 'x', voiceId: 'BERT-VITS2&0', forceNoStreaming: true },
    vitsForm({ ...VITS_DEFAULTS, text_prompt: 'p', style_text: '' }, 'x', 'BERT-VITS2&0', true));

// ---------- xtts ----------
const XTTS_DEFAULTS = { language: 'en', streaming: false };
add('xtts-defaults', 'xtts',
    { settings: XTTS_DEFAULTS, inputText: 'hello', voiceId: 'speaker1' },
    xttsBody(XTTS_DEFAULTS, 'hello', 'speaker1'));
add('xtts-other-language', 'xtts',
    { settings: { ...XTTS_DEFAULTS, language: 'zh-cn' }, inputText: '你好', voiceId: 'spk2' },
    xttsBody({ ...XTTS_DEFAULTS, language: 'zh-cn' }, '你好', 'spk2'));

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`tts-local fixtures: ${cases.length} cases -> ${outFile}`);
