#!/usr/bin/env node
// 官方 Text Completion 请求体差分：createTextGenGenerationData / getTextGenModel / getTextGenServer。
// 提取源（SillyTavern 1.18.0 / 8172dcd）：public/scripts/textgen-settings.js（函数逐字）。
// 打桩登记：
//   - textgenerationwebui_settings：由用例注入（server_urls 由用例提供）。
//   - max_context：由用例注入；power_user.request_token_probabilities 由用例注入。
//   - getStoppingStrings：由用例注入（默认 []）。
//   - getTextTokens(tokenizer, text)：恒为“字符码点数组”桩（tokenizer 相关行为不属本差分）。
//   - getTokenizerForTokenIds：恒 'llama'；getLogitBiasListResult 逐字（依赖上述 getTextTokens 桩）。
//   - textgenerationwebui_banned_in_macros：由用例注入（默认 []）；substituteParams：恒等（宏替换在调用方完成）。
//   - isDynamicTemperatureSupported 的 DYNATEMP_BLOCK.dataset.tgType：由用例注入。
//   - calculateLogitBias 逐字（BIAS_CACHE 恒 miss，走真实计算）。
//   - toastr（getTextGenModel 的 ollama 分支）：恒 no-op。
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const toastr = { error: () => {} };
const onlyUnique = (value, index, array) => array.indexOf(value) === index;
const substituteParams = (x) => x;
const BIAS_CACHE = { get: () => undefined, set: () => {} };
const BIAS_KEY = '#textgenerationwebui_api-settings';
const MANCER_SERVER = 'https://neuro.mancer.tech';
const TOGETHERAI_SERVER = 'https://api.together.xyz';
const INFERMATICAI_SERVER = 'https://api.totalgpt.ai';
const DREAMGEN_SERVER = 'https://dreamgen.com';
const OPENROUTER_SERVER = 'https://openrouter.ai/api';
const FEATHERLESS_SERVER = 'https://api.featherless.ai/v1';

const textgen_types = {
    OOBA: 'ooba',
    MANCER: 'mancer',
    VLLM: 'vllm',
    APHRODITE: 'aphrodite',
    TABBY: 'tabby',
    KOBOLDCPP: 'koboldcpp',
    TOGETHERAI: 'togetherai',
    LLAMACPP: 'llamacpp',
    OLLAMA: 'ollama',
    INFERMATICAI: 'infermaticai',
    DREAMGEN: 'dreamgen',
    OPENROUTER: 'openrouter',
    FEATHERLESS: 'featherless',
    HUGGINGFACE: 'huggingface',
    GENERIC: 'generic',
};

const {
    GENERIC, MANCER, VLLM, APHRODITE, TABBY, TOGETHERAI, OOBA, OLLAMA,
    LLAMACPP, INFERMATICAI, DREAMGEN, OPENROUTER, KOBOLDCPP, HUGGINGFACE, FEATHERLESS,
} = textgen_types;

const APHRODITE_DEFAULT_ORDER = [
    'dry', 'penalties', 'no_repeat_ngram', 'temperature', 'top_nsigma', 'top_p_top_k',
    'top_a', 'min_p', 'tfs', 'eta_cutoff', 'epsilon_cutoff', 'typical_p', 'quadratic', 'xtc',
];

let textgenerationwebui_settings = {};
let max_context = 4096;
let power_user = { request_token_probabilities: false };
let textgenerationwebui_banned_in_macros = [];
let DYNATEMP_BLOCK = { dataset: { tgType: '' } };
let __tokenize = (text) => Array.from(text).map((c) => c.codePointAt(0));
let __stoppingStrings = [];

function getTextTokens(tokenizer, text) { return __tokenize(text); }
function getTokenizerForTokenIds() { return 'llama'; }

// ---- 官方函数（逐字） ----
export function getTextGenModel(settings = null) {
    settings = settings ?? textgenerationwebui_settings;
    switch (settings.type) {
        case OOBA:
            if (settings.custom_model) return settings.custom_model;
            break;
        case GENERIC:
            if (settings.generic_model) return settings.generic_model;
            break;
        case MANCER:
            return settings.mancer_model;
        case TOGETHERAI:
            return settings.togetherai_model;
        case INFERMATICAI:
            return settings.infermaticai_model;
        case DREAMGEN:
            return settings.dreamgen_model;
        case OPENROUTER:
            return settings.openrouter_model;
        case VLLM:
            return settings.vllm_model;
        case APHRODITE:
            return settings.aphrodite_model;
        case OLLAMA:
            if (!settings.ollama_model) {
                toastr.error(`No Ollama model selected.`, 'Text Completion API');
                throw new Error('No Ollama model selected');
            }
            return settings.ollama_model;
        case FEATHERLESS:
            return settings.featherless_model;
        case HUGGINGFACE:
            return 'tgi';
        case TABBY:
            if (settings.tabby_model) return settings.tabby_model;
            break;
        case LLAMACPP:
            if (settings.llamacpp_model) return settings.llamacpp_model;
            break;
        default:
            return undefined;
    }
    return undefined;
}

function isDynamicTemperatureSupported(settings = null) {
    settings = settings ?? textgenerationwebui_settings;
    return settings.dynatemp && DYNATEMP_BLOCK?.dataset?.tgType?.includes(settings.type);
}

function getLogprobsNumber(type = null) {
    const selectedType = type ?? textgenerationwebui_settings.type;
    if (selectedType === VLLM || selectedType === INFERMATICAI) return 5;
    return 10;
}

function replaceMacrosInList(str) {
    if (!str || typeof str !== 'string') return str;
    try {
        const array = JSON.parse(str);
        if (!Array.isArray(array)) throw new Error('Not an array');
        for (let i = 0; i < array.length; i++) array[i] = substituteParams(array[i]);
        return JSON.stringify(array);
    } catch {
        const array = str.split(',');
        for (let i = 0; i < array.length; i++) array[i] = substituteParams(array[i]);
        return array.join(',');
    }
}

function getTextGenServer(type = null) {
    const selectedType = type ?? textgenerationwebui_settings.type;
    switch (selectedType) {
        case FEATHERLESS: return FEATHERLESS_SERVER;
        case MANCER: return MANCER_SERVER;
        case TOGETHERAI: return TOGETHERAI_SERVER;
        case INFERMATICAI: return INFERMATICAI_SERVER;
        case DREAMGEN: return DREAMGEN_SERVER;
        case OPENROUTER: return OPENROUTER_SERVER;
        default: return textgenerationwebui_settings.server_urls[selectedType] ?? '';
    }
}

function getCustomTokenBans(settings = null) {
    settings = settings ?? textgenerationwebui_settings;
    if (!settings.send_banned_tokens || (!settings.banned_tokens && !settings.global_banned_tokens && !textgenerationwebui_banned_in_macros.length)) {
        return { banned_tokens: '', banned_strings: [] };
    }
    const tokenizer = getTokenizerForTokenIds();
    const banned_tokens = [];
    const banned_strings = [];
    const sequences = []
        .concat(settings.banned_tokens.split('\n'))
        .concat(settings.global_banned_tokens.split('\n'))
        .concat(textgenerationwebui_banned_in_macros)
        .filter(x => x.length > 0)
        .filter(onlyUnique)
        .map(x => substituteParams(x));
    textgenerationwebui_banned_in_macros = [];
    for (const line of sequences) {
        if (line.startsWith('[') && line.endsWith(']')) {
            try {
                const tokens = JSON.parse(line);
                if (Array.isArray(tokens) && tokens.every(t => Number.isInteger(t))) banned_tokens.push(...tokens);
                else throw new Error('Not an array of integers');
            } catch (err) { console.log(`Failed to parse bad word token list: ${line}`, err); }
        } else if (line.startsWith('"') && line.endsWith('"')) {
            banned_strings.push(line.slice(1, -1));
        } else {
            try { banned_tokens.push(...getTextTokens(tokenizer, line)); }
            catch { console.log(`Could not tokenize raw text: ${line}`); }
        }
    }
    return {
        banned_tokens: banned_tokens.filter(onlyUnique).map(x => String(x)).join(','),
        banned_strings: banned_strings,
    };
}

function getLogitBiasListResult(biasPreset, tokenizerType, getBiasObject) {
    const result = [];
    for (const entry of biasPreset) {
        if (entry.text?.length > 0) {
            const text = entry.text.trim();
            if (text.length === 0) continue;
            if (text.startsWith('{') && text.endsWith('}')) {
                const tokens = getTextTokens(tokenizerType, text.slice(1, -1));
                result.push(getBiasObject(entry.value, tokens));
            } else if (text.startsWith('[') && text.endsWith(']')) {
                try {
                    const tokens = JSON.parse(text);
                    if (Array.isArray(tokens) && tokens.every(t => Number.isInteger(t))) result.push(getBiasObject(entry.value, tokens));
                    else throw new Error('Not an array of integers');
                } catch (err) { console.log(`Failed to parse logit bias token list: ${text}`, err); }
            } else {
                const biasText = ` ${text}`;
                const tokens = getTextTokens(tokenizerType, biasText);
                result.push(getBiasObject(entry.value, tokens));
            }
        }
    }
    return result;
}

function calculateLogitBias(settings = null) {
    settings = settings ?? textgenerationwebui_settings;
    if (!Array.isArray(settings.logit_bias) || settings.logit_bias.length === 0) return {};
    const tokenizer = getTokenizerForTokenIds();
    const result = {};
    function addBias(bias, sequence) {
        if (sequence.length === 0) return;
        for (const logit of sequence) {
            const key = String(logit);
            result[key] = bias;
        }
        return result;
    }
    getLogitBiasListResult(settings.logit_bias, tokenizer, addBias);
    return result;
}

function toIntArray(string) {
    if (!string) return [];
    return string.split(',').map(x => parseInt(x)).filter(x => !isNaN(x));
}

export function arraysEqual(a, b) {
    if (a === b) return true;
    if (a == null || b == null) return false;
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
        if (a[i] !== b[i]) return false;
    }
    return true;
}

export function createTextGenGenerationData(settings, model, finalPrompt = null, maxTokens = null, isImpersonate = false, isContinue = false, cfgValues = null, type = 'quiet') {
    settings = settings ?? textgenerationwebui_settings;
    model = model ?? getTextGenModel(settings);

    const canMultiSwipe = !isContinue && !isImpersonate && type !== 'quiet';
    const dynatemp = isDynamicTemperatureSupported(settings);
    const { banned_tokens, banned_strings } = getCustomTokenBans(settings);
    const jsonSchema = isObject(settings.json_schema)
        ? settings.json_schema_allow_empty
            ? settings.json_schema
            : Object.keys(settings.json_schema).length > 0 ? settings.json_schema : undefined
        : undefined;

    let params = {
        'prompt': finalPrompt,
        'model': model,
        'max_new_tokens': maxTokens,
        'max_tokens': maxTokens,
        'logprobs': power_user.request_token_probabilities ? getLogprobsNumber(settings.type) : undefined,
        'temperature': dynatemp ? (settings.min_temp + settings.max_temp) / 2 : settings.temp,
        'top_p': settings.top_p,
        'typical_p': settings.typical_p,
        'typical': settings.typical_p,
        'sampler_seed': settings.seed >= 0 ? settings.seed : undefined,
        'min_p': settings.min_p,
        'repetition_penalty': settings.rep_pen,
        'frequency_penalty': settings.freq_pen,
        'presence_penalty': settings.presence_pen,
        'top_k': settings.top_k,
        'skew': settings.skew,
        'min_length': settings.type === OOBA ? settings.min_length : undefined,
        'minimum_message_content_tokens': settings.type === DREAMGEN ? settings.min_length : undefined,
        'min_tokens': settings.min_length,
        'num_beams': settings.type === OOBA ? settings.num_beams : undefined,
        'length_penalty': settings.type === OOBA ? settings.length_penalty : undefined,
        'early_stopping': settings.type === OOBA ? settings.early_stopping : undefined,
        'add_bos_token': settings.add_bos_token,
        'dynamic_temperature': dynatemp ? true : undefined,
        'dynatemp_low': dynatemp ? settings.min_temp : undefined,
        'dynatemp_high': dynatemp ? settings.max_temp : undefined,
        'dynatemp_range': dynatemp ? (settings.max_temp - settings.min_temp) / 2 : undefined,
        'dynatemp_exponent': dynatemp ? settings.dynatemp_exponent : undefined,
        'smoothing_factor': settings.smoothing_factor,
        'smoothing_curve': settings.smoothing_curve,
        'dry_allowed_length': settings.dry_allowed_length,
        'dry_multiplier': settings.dry_multiplier,
        'dry_base': settings.dry_base,
        'dry_sequence_breakers': replaceMacrosInList(settings.dry_sequence_breakers),
        'dry_penalty_last_n': settings.dry_penalty_last_n,
        'max_tokens_second': settings.max_tokens_second,
        'sampler_priority': settings.type === OOBA ? settings.sampler_priority : undefined,
        'samplers': settings.type === LLAMACPP ? settings.samplers : undefined,
        'stopping_strings': getStoppingStrings(isImpersonate, isContinue),
        'stop': getStoppingStrings(isImpersonate, isContinue),
        'truncation_length': max_context,
        'ban_eos_token': settings.ban_eos_token,
        'skip_special_tokens': settings.skip_special_tokens,
        'include_reasoning': settings.include_reasoning,
        'top_a': settings.top_a,
        'tfs': settings.tfs,
        'epsilon_cutoff': [OOBA, MANCER].includes(settings.type) ? settings.epsilon_cutoff : undefined,
        'eta_cutoff': [OOBA, MANCER].includes(settings.type) ? settings.eta_cutoff : undefined,
        'mirostat_mode': settings.mirostat_mode,
        'mirostat_tau': settings.mirostat_tau,
        'mirostat_eta': settings.mirostat_eta,
        'custom_token_bans': [APHRODITE, MANCER].includes(settings.type) ? toIntArray(banned_tokens) : banned_tokens,
        'banned_strings': banned_strings,
        'api_type': settings.type,
        'api_server': getTextGenServer(settings.type),
        'sampler_order': settings.type === KOBOLDCPP ? settings.sampler_order : undefined,
        'xtc_threshold': settings.xtc_threshold,
        'xtc_probability': settings.xtc_probability,
        'nsigma': settings.nsigma,
        'top_n_sigma': settings.nsigma,
        'min_keep': settings.min_keep,
        'adaptive_target': settings.adaptive_target,
        'adaptive_decay': settings.adaptive_decay,
        parseSequenceBreakers: function () {
            try {
                return JSON.parse(this.dry_sequence_breakers);
            } catch {
                if (typeof this.dry_sequence_breakers === 'string') {
                    return this.dry_sequence_breakers.split(',');
                }
                return undefined;
            }
        },
    };
    const nonAphroditeParams = {
        'rep_pen': settings.rep_pen,
        'rep_pen_range': settings.rep_pen_range,
        'repetition_decay': settings.type === TABBY ? settings.rep_pen_decay : undefined,
        'repetition_penalty_range': settings.rep_pen_range,
        'encoder_repetition_penalty': settings.type === OOBA ? settings.encoder_rep_pen : undefined,
        'no_repeat_ngram_size': settings.type === OOBA ? settings.no_repeat_ngram_size : undefined,
        'penalty_alpha': settings.type === OOBA ? settings.penalty_alpha : undefined,
        'temperature_last': (settings.type === OOBA || settings.type === APHRODITE || settings.type == TABBY) ? settings.temperature_last : undefined,
        'speculative_ngram': settings.type === TABBY ? settings.speculative_ngram : undefined,
        'do_sample': settings.type === OOBA ? settings.do_sample : undefined,
        'seed': settings.seed >= 0 ? settings.seed : undefined,
        'guidance_scale': cfgValues?.guidanceScale?.value ?? settings.guidance_scale ?? 1,
        'negative_prompt': cfgValues?.negativePrompt ?? substituteParams(settings.negative_prompt) ?? '',
        'grammar_string': settings.grammar_string || undefined,
        'json_schema': [TABBY, LLAMACPP].includes(settings.type) ? jsonSchema : undefined,
        'repeat_penalty': settings.rep_pen,
        'repeat_last_n': settings.rep_pen_range,
        'n_predict': maxTokens,
        'num_predict': maxTokens,
        'num_ctx': max_context,
        'mirostat': settings.mirostat_mode,
        'ignore_eos': settings.ban_eos_token,
        'n_probs': power_user.request_token_probabilities ? 10 : undefined,
        'rep_pen_slope': settings.rep_pen_slope,
    };
    const vllmParams = {
        'n': canMultiSwipe ? settings.n : 1,
        'ignore_eos': settings.ignore_eos_token,
        'spaces_between_special_tokens': settings.spaces_between_special_tokens,
        'seed': settings.seed >= 0 ? settings.seed : undefined,
    };
    const aphroditeParams = {
        'n': canMultiSwipe ? settings.n : 1,
        'frequency_penalty': settings.freq_pen,
        'presence_penalty': settings.presence_pen,
        'repetition_penalty': settings.rep_pen,
        'seed': settings.seed >= 0 ? settings.seed : undefined,
        'stop': getStoppingStrings(isImpersonate, isContinue),
        'temperature': dynatemp ? (settings.min_temp + settings.max_temp) / 2 : settings.temp,
        'temperature_last': settings.temperature_last,
        'top_p': settings.top_p,
        'top_k': settings.top_k,
        'top_a': settings.top_a,
        'min_p': settings.min_p,
        'tfs': settings.tfs,
        'eta_cutoff': settings.eta_cutoff,
        'epsilon_cutoff': settings.epsilon_cutoff,
        'typical_p': settings.typical_p,
        'smoothing_factor': settings.smoothing_factor,
        'smoothing_curve': settings.smoothing_curve,
        'ignore_eos': settings.ignore_eos_token,
        'min_tokens': settings.min_length,
        'skip_special_tokens': settings.skip_special_tokens,
        'spaces_between_special_tokens': settings.spaces_between_special_tokens,
        'guided_grammar': settings.grammar_string || undefined,
        'guided_json': jsonSchema || undefined,
        'early_stopping': false,
        'include_stop_str_in_output': false,
        'dynatemp_min': dynatemp ? settings.min_temp : undefined,
        'dynatemp_max': dynatemp ? settings.max_temp : undefined,
        'dynatemp_exponent': dynatemp ? settings.dynatemp_exponent : undefined,
        'xtc_threshold': settings.xtc_threshold,
        'xtc_probability': settings.xtc_probability,
        'nsigma': settings.nsigma,
        'custom_token_bans': toIntArray(banned_tokens),
        'no_repeat_ngram_size': settings.no_repeat_ngram_size,
        'sampler_priority': settings.type === APHRODITE && !arraysEqual(
            settings.samplers_priorities,
            APHRODITE_DEFAULT_ORDER)
            ? settings.samplers_priorities
            : undefined,
    };

    if (settings.type === OPENROUTER) {
        params.provider = settings.openrouter_providers;
        params.quantizations = settings.openrouter_quantizations;
        params.allow_fallbacks = settings.openrouter_allow_fallbacks;
    }

    if (settings.type === KOBOLDCPP) {
        params.grammar = settings.grammar_string || undefined;
        params.grammar_retain_state = (settings.grammar_string && !!isContinue) ? true : undefined;
        params.trim_stop = true;
        params.dry_sequence_breakers = params.parseSequenceBreakers();
    }

    if (settings.type === HUGGINGFACE) {
        params.top_p = Math.min(Math.max(Number(params.top_p), 0.0), 0.999);
        params.stop = Array.isArray(params.stop) ? params.stop.slice(0, 4) : [];
        nonAphroditeParams.seed = settings.seed >= 0 ? settings.seed : Math.floor(Math.random() * Math.pow(2, 32));
    }

    if (settings.type === MANCER) {
        params.n = canMultiSwipe ? settings.n : 1;
        params.epsilon_cutoff /= 1000;
        params.eta_cutoff /= 1000;
        params.dynatemp_mode = params.dynamic_temperature ? 1 : 0;
        params.dynatemp_min = params.dynatemp_low;
        params.dynatemp_max = params.dynatemp_high;
        delete params.dynatemp_low;
        delete params.dynatemp_high;
        params.dry_sequence_breakers = params.parseSequenceBreakers();
    }

    if (settings.type === TABBY || settings.type === LLAMACPP) {
        params.n = canMultiSwipe ? settings.n : 1;
    }

    switch (settings.type) {
        case VLLM:
        case INFERMATICAI:
            params = Object.assign(params, vllmParams);
            break;
        case APHRODITE:
            params = Object.assign(params, aphroditeParams);
            break;
        default:
            params = Object.assign(params, nonAphroditeParams);
            break;
    }

    if (Array.isArray(settings.logit_bias) && settings.logit_bias.length) {
        const logitBias = BIAS_CACHE.get(BIAS_KEY) || calculateLogitBias(settings);
        BIAS_CACHE.set(BIAS_KEY, logitBias);
        params.logit_bias = logitBias;
    }

    if (settings.type === LLAMACPP || settings.type === OLLAMA) {
        const logitBiasArray = (params.logit_bias && typeof params.logit_bias === 'object' && Object.keys(params.logit_bias).length > 0)
            ? Object.entries(params.logit_bias).map(([key, value]) => [Number(key), value])
            : [];
        const tokenBans = toIntArray(banned_tokens);
        logitBiasArray.push(...tokenBans.map(x => [Number(x), false]));
        const sequenceBreakers = params.parseSequenceBreakers();
        const llamaCppParams = {
            'logit_bias': logitBiasArray,
            'grammar': settings.grammar_string,
            'cache_prompt': true,
            'dry_sequence_breakers': sequenceBreakers,
        };
        params = Object.assign(params, llamaCppParams);
        if (!Array.isArray(sequenceBreakers) || sequenceBreakers.length === 0) {
            delete params.dry_sequence_breakers;
        }
    }

    if ([LLAMACPP, APHRODITE].includes(settings.type)) {
        if (jsonSchema) {
            delete params.grammar_string;
            delete params.grammar;
            delete params.guided_grammar;
        } else {
            delete params.json_schema;
            delete params.guided_json;
        }
    }
    return params;
}

function isObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value);
}

let getStoppingStrings = () => [];

function runCase() {
    return (body) => {
        textgenerationwebui_settings = { server_urls: {}, ...(body.settings ?? {}) };
        max_context = body.maxContext ?? 4096;
        power_user = { request_token_probabilities: body.requestTokenProbabilities ?? false };
        textgenerationwebui_banned_in_macros = body.bannedInMacros ?? [];
        DYNATEMP_BLOCK = { dataset: { tgType: body.dynatempTypes ?? '' } };
        __stoppingStrings = body.stoppingStrings ?? [];
        getStoppingStrings = () => __stoppingStrings;
        if (body.tokenize) __tokenize = body.tokenize;
        const model = getTextGenModel();
        const params = createTextGenGenerationData(
            textgenerationwebui_settings,
            model,
            body.finalPrompt ?? 'hello',
            body.maxTokens ?? null,
            body.isImpersonate ?? false,
            body.isContinue ?? false,
            body.cfgValues ?? null,
            body.type ?? 'quiet',
        );
        return JSON.stringify(params);
    };
}

const cases = [];
async function add(id, settings, extra = {}) {
    let expected;
    try {
        expected = await runCase()({ settings, ...extra });
    } catch (e) {
        expected = 'ERROR: ' + e.message;
    }
    cases.push({ id, settings, extra: { ...extra, settings }, expected });
}

// 基础 ooba 全覆盖
const base = { type: 'ooba', temp: 0.7, send_banned_tokens: false, banned_tokens: '', global_banned_tokens: '', logit_bias: [], top_p: 0.5, top_k: 40, top_a: 0, tfs: 1, typical_p: 1, rep_pen: 1, rep_pen_range: 1024, rep_pen_slope: 0.7, freq_pen: 0, presence_pen: 0, min_p: 0, seed: -1, min_length: 0, num_beams: 1, length_penalty: 1, early_stopping: false, encoder_rep_pen: 1, no_repeat_ngram_size: 0, penalty_alpha: 0, do_sample: true, temperature_last: true, sampler_priority: ['temperature'], add_bos_token: true, ban_eos_token: false, skip_special_tokens: false, include_reasoning: false, epsilon_cutoff: 0, eta_cutoff: 0, mirostat_mode: 0, mirostat_tau: 5, mirostat_eta: 0.1, smoothing_factor: 0, smoothing_curve: 1, dry_allowed_length: 2, dry_multiplier: 0, dry_base: 1.75, dry_sequence_breakers: '["\\n",":"]', dry_penalty_last_n: 0, max_tokens_second: 0, skew: 0, min_keep: 0, adaptive_target: 0, adaptive_decay: 0, nsigma: 0, xtc_threshold: 0.1, xtc_probability: 0, custom_model: 'model-ooba', server_urls: { ooba: 'http://127.0.0.1:5000' } };

await add('ooba-min', { ...base });
await add('ooba-max-tokens', { ...base, seed: 42, min_length: 10 }, { maxTokens: 512, isImpersonate: true });
await add('ooba-continue', { ...base, rep_pen: 1.1, do_sample: false }, { maxTokens: 256, isContinue: true, stoppingStrings: ['\nUser:'] });
await add('ooba-request-logprobs', { ...base }, { requestTokenProbabilities: true });
await add('ooba-banned', { ...base, send_banned_tokens: true, banned_tokens: 'foo\n[1,2,3]\n"bar"' }, { tokenize: (t) => Array.from(t).map(c => c.codePointAt(0)) });
await add('ooba-logit-bias', { ...base, logit_bias: [{ text: 'hello', value: 1.5 }, { text: '[7,8]', value: -1 }] });
await add('ooba-dynatemp', { ...base, dynatemp: true, min_temp: 0.3, max_temp: 0.9, dynatemp_exponent: 1 }, { dynatempTypes: 'ooba vllm' });
await add('ooba-cfg', { ...base, guidance_scale: 3, negative_prompt: 'bad' }, { cfgValues: { guidanceScale: { value: 5 }, negativePrompt: 'worse' } });
// mancer
await add('mancer-min', { ...base, type: 'mancer', mancer_model: 'm-model', server_urls: {} }, { maxTokens: 200 });
await add('mancer-dynatemp', { ...base, type: 'mancer', mancer_model: 'm-model', dynatemp: true, min_temp: 0.2, max_temp: 0.8, dynatemp_exponent: 1, server_urls: {} }, { dynatempTypes: 'mancer', maxTokens: 200 });
// vllm / infermaticai
await add('vllm-min', { ...base, type: 'vllm', vllm_model: 'v-model', ignore_eos_token: false, spaces_between_special_tokens: true, n: 3, server_urls: {} }, { type: 'impersonate' });
await add('infermaticai-min', { ...base, type: 'infermaticai', infermaticai_model: 'i-model', ignore_eos_token: false, spaces_between_special_tokens: true, n: 2, server_urls: {} }, { requestTokenProbabilities: true });
// aphrodite
await add('aphrodite-min', { ...base, type: 'aphrodite', aphrodite_model: 'a-model', samplers_priorities: ['dry', 'penalties'], ignore_eos_token: true, spaces_between_special_tokens: true, n: 2, grammar_string: 'g', logit_bias: [], server_urls: {} });
await add('aphrodite-default-order', { ...base, type: 'aphrodite', aphrodite_model: 'a-model', samplers_priorities: ['dry', 'penalties', 'no_repeat_ngram', 'temperature', 'top_nsigma', 'top_p_top_k', 'top_a', 'min_p', 'tfs', 'eta_cutoff', 'epsilon_cutoff', 'typical_p', 'quadratic', 'xtc'], server_urls: {} });
// tabby
await add('tabby-min', { ...base, type: 'tabby', tabby_model: 't-model', rep_pen_decay: 0.5, speculative_ngram: 3, json_schema: { type: 'object' }, json_schema_allow_empty: true, server_urls: {} });
// llamacpp / ollama
await add('llamacpp-min', { ...base, type: 'llamacpp', llamacpp_model: 'l-model', samplers: ['temp'], ignore_eos_token: false, spaces_between_special_tokens: true, json_schema: { type: 'object' }, server_urls: {} }, { maxTokens: 128 });
await add('llamacpp-bias-banned', { ...base, type: 'llamacpp', llamacpp_model: 'l-model', samplers: [], send_banned_tokens: true, banned_tokens: '10,20', dry_sequence_breakers: 'x,y', server_urls: {} });
await add('ollama-min', { ...base, type: 'ollama', ollama_model: 'o-model', send_banned_tokens: true, banned_tokens: '1,2', server_urls: {} });
await add('ollama-no-model', { ...base, type: 'ollama', server_urls: {} });
// koboldcpp / huggingface / openrouter / togetherai / dreamgen / generic / featherless
await add('koboldcpp-min', { ...base, type: 'koboldcpp', sampler_order: [6, 0, 1], grammar_string: 'gram', server_urls: { koboldcpp: 'http://x:5001' } }, { maxTokens: 512 });
await add('huggingface-min', { ...base, type: 'huggingface', seed: 123, server_urls: {} }, { stoppingStrings: ['a', 'b', 'c', 'd', 'e'] });
await add('openrouter-min', { ...base, type: 'openrouter', openrouter_model: 'or-model', openrouter_providers: ['p1'], openrouter_quantizations: ['int4'], openrouter_allow_fallbacks: true, server_urls: {} });
await add('togetherai-min', { ...base, type: 'togetherai', togetherai_model: 'tg-model', server_urls: {} });
await add('dreamgen-min', { ...base, type: 'dreamgen', dreamgen_model: 'dg-model', min_length: 5, server_urls: {} });
await add('generic-min', { ...base, type: 'generic', generic_model: 'gen-model', server_urls: {} });
await add('featherless-min', { ...base, type: 'featherless', featherless_model: 'fl-model', server_urls: {} });
await add('model-null', { ...base, custom_model: '', server_urls: {} });

const out = join(__dirname, '..', '..', 'engine/src/test/resources/diff/textgen-body.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(cases, null, 1) + '\n');
console.log('textgen-body cases:', cases.length, '->', out);
