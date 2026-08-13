/**
 * 官方 NovelAI 聊天请求体差分：getNovelGenerationData / selectPrefix / getTokenizerTypeForModel。
 * 提取源（SillyTavern 1.18.0 / 8172dcd）：public/scripts/nai-settings.js:515-610（含函数逐字）。
 * 打桩登记：
 *   - nai_settings：由用例参数注入（model/temperature/min_length/tail_free_sampling/repetition 系/
 *     top_a/top_p/top_k/min_p/math1 系/typical_p/mirostat 系/phrase_rep_pen/order/logit_bias/
 *     banned_tokens/prefix）。
 *   - getStoppingStrings → 用例注入的 stoppingStrings（默认 []）。
 *   - getBadWordIds → 恒 []；calculateLogitBias → 恒 []；BIAS_CACHE 不用（logit_bias 空时不走）。
 *   - getNovelMaxResponseTokens → 512；power_user.console_log_prompts=false、request_token_probabilities=false。
 *   - tokenizers：{NONE:0, NERD:1, NERD2:2, LLAMA3:3}；default_order 用例注入。
 *   - getTextTokens：恒 []（stop_sequences 只验证“是否 tokenizerType!==NONE 决定字段存在”）。
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const tokenizers = { NONE: 0, NERD: 1, NERD2: 2, LLAMA3: 3 };
const default_order = ['temperature', 'tail_free_sampling', 'repetition_penalty', 'top_p', 'top_k'];

// ---- 官方函数（逐字） ----
function getTokenizerTypeForModel(model) {
    if (model.includes('clio')) return tokenizers.NERD;
    if (model.includes('kayra')) return tokenizers.NERD2;
    if (model.includes('erato')) return tokenizers.LLAMA3;
    return tokenizers.NONE;
}

function selectPrefix(selected_prefix, finalPrompt) {
    let useInstruct = false;
    const clio = nai_settings.model_novel.includes('clio');
    const kayra = nai_settings.model_novel.includes('kayra');
    const erato = nai_settings.model_novel.includes('erato');
    const isNewModel = clio || kayra || erato;
    if (isNewModel) {
        const tail = finalPrompt.slice(-1500);
        useInstruct = tail.includes('}');
        return useInstruct ? 'special_instruct' : selected_prefix;
    }
    return 'vanilla';
}

function getNovelGenerationData(finalPrompt, settings, maxLength, isImpersonate, isContinue, _cfgValues, type) {
    const isKayra = nai_settings.model_novel.includes('kayra');
    const isErato = nai_settings.model_novel.includes('erato');
    const tokenizerType = getTokenizerTypeForModel(nai_settings.model_novel);
    const stoppingStrings = getStoppingStrings(isImpersonate, isContinue);
    if (isErato) {
        const additionalStopStrings = [];
        for (const stoppingString of stoppingStrings) {
            if (stoppingString.startsWith('\n')) {
                additionalStopStrings.push('.' + stoppingString);
                additionalStopStrings.push('!' + stoppingString);
                additionalStopStrings.push('?' + stoppingString);
                additionalStopStrings.push('*' + stoppingString);
                additionalStopStrings.push('"' + stoppingString);
                additionalStopStrings.push('_' + stoppingString);
                additionalStopStrings.push('...' + stoppingString);
                additionalStopStrings.push('."' + stoppingString);
                additionalStopStrings.push('?"' + stoppingString);
                additionalStopStrings.push('!"' + stoppingString);
                additionalStopStrings.push('.*' + stoppingString);
                additionalStopStrings.push(')' + stoppingString);
            }
        }
        stoppingStrings.push(...additionalStopStrings);
    }
    const MAX_STOP_SEQUENCES = 1024;
    const stopSequences = (tokenizerType !== tokenizers.NONE)
        ? stoppingStrings.slice(0, MAX_STOP_SEQUENCES).map(t => getTextTokens(tokenizerType, t))
        : undefined;
    const badWordIds = (tokenizerType !== tokenizers.NONE)
        ? getBadWordIds(nai_settings.banned_tokens, tokenizerType)
        : undefined;
    const prefix = selectPrefix(nai_settings.prefix, finalPrompt);
    let logitBias = [];
    if (tokenizerType !== tokenizers.NONE && Array.isArray(nai_settings.logit_bias) && nai_settings.logit_bias.length) {
        logitBias = calculateLogitBias();
    }
    if (power_user.console_log_prompts) { console.log(finalPrompt); }
    if (isErato) {
        finalPrompt = '<|startoftext|><|reserved_special_token81|>' + finalPrompt;
    }
    const adjustedMaxLength = (isKayra || isErato) ? getNovelMaxResponseTokens() : maximum_output_length;
    return {
        'input': finalPrompt,
        'model': nai_settings.model_novel,
        'use_string': true,
        'temperature': Number(nai_settings.temperature),
        'max_length': maxLength < adjustedMaxLength ? maxLength : adjustedMaxLength,
        'min_length': Number(nai_settings.min_length),
        'tail_free_sampling': Number(nai_settings.tail_free_sampling),
        'repetition_penalty': Number(nai_settings.repetition_penalty),
        'repetition_penalty_range': Number(nai_settings.repetition_penalty_range),
        'repetition_penalty_slope': Number(nai_settings.repetition_penalty_slope),
        'repetition_penalty_frequency': Number(nai_settings.repetition_penalty_frequency),
        'repetition_penalty_presence': Number(nai_settings.repetition_penalty_presence),
        'top_a': Number(nai_settings.top_a),
        'top_p': Number(nai_settings.top_p),
        'top_k': Number(nai_settings.top_k),
        'min_p': Number(nai_settings.min_p),
        'math1_temp': Number(nai_settings.math1_temp),
        'math1_quad': Number(nai_settings.math1_quad),
        'math1_quad_entropy_scale': Number(nai_settings.math1_quad_entropy_scale),
        'typical_p': Number(nai_settings.typical_p),
        'mirostat_lr': Number(nai_settings.mirostat_lr),
        'mirostat_tau': Number(nai_settings.mirostat_tau),
        'phrase_rep_pen': nai_settings.phrase_rep_pen,
        'stop_sequences': stopSequences,
        'bad_words_ids': badWordIds,
        'logit_bias_exp': logitBias,
        'generate_until_sentence': true,
        'use_cache': false,
        'return_full_text': false,
        'prefix': prefix,
        'order': nai_settings.order || settings.order || default_order,
        'num_logprobs': power_user.request_token_probabilities ? 10 : undefined,
    };
}

// ---- 桩 ----
let nai_settings = {};
let power_user = { console_log_prompts: false, request_token_probabilities: false };
let maximum_output_length = 600;
// 返回副本：官方函数会 push 扩充该数组，避免污染 fixture 里注入的原始列表
const getStoppingStrings = () => [...(nai_settings._stoppingStrings || [])];
const getBadWordIds = () => [];
const getTextTokens = () => [];
const calculateLogitBias = () => [{ bias: 1, sequence: [] }];
const getNovelMaxResponseTokens = () => 512;

const cases = [];
function add(id, settings, extra = {}) {
    nai_settings = { ...settings, _stoppingStrings: extra.stoppingStrings || [] };
    power_user = { console_log_prompts: false, request_token_probabilities: extra.requestTokenProbabilities || false };
    maximum_output_length = extra.maximumOutputLength ?? 600;
    const result = getNovelGenerationData(extra.finalPrompt ?? 'hello', { order: extra.order || null }, extra.maxLength ?? 200, extra.isImpersonate ?? false, extra.isContinue ?? false, null, extra.type ?? 'normal');
    // 复现官方 JSON.stringify 行为：undefined 字段省略
    cases.push({ id, settings, extra: { ...extra, order: undefined }, expected: JSON.stringify(result) });
}

// Kayra / Clio / Erato / 旧模型；prefix 选择；erato 停用词扩充；token 字段存在性
add('kayra-min', { model_novel: 'kayra-v1', temperature: 1, min_length: 0, tail_free_sampling: 1, repetition_penalty: 1, repetition_penalty_range: 1024, repetition_penalty_slope: 0.9, repetition_penalty_frequency: 0, repetition_penalty_presence: 0, top_a: 0, top_p: 0.9, top_k: 0, min_p: 0, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 1, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'off', order: null, logit_bias: [], banned_tokens: [], prefix: 'vanilla' });
add('clio-full', { model_novel: 'clio-v1', temperature: 0.8, min_length: 1, tail_free_sampling: 0.9, repetition_penalty: 1.1, repetition_penalty_range: 2048, repetition_penalty_slope: 0.7, repetition_penalty_frequency: 0.05, repetition_penalty_presence: 0.05, top_a: 0.2, top_p: 0.95, top_k: 40, min_p: 0.1, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 0.9, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'aggressive', order: ['temperature', 'top_k'], logit_bias: [{ bias: 1 }], banned_tokens: ['x'], prefix: 'special_instruct' }, { finalPrompt: 'hello } world' });
add('erato-stops', { model_novel: 'erato-v1', temperature: 1, min_length: 0, tail_free_sampling: 1, repetition_penalty: 1, repetition_penalty_range: 1024, repetition_penalty_slope: 0.9, repetition_penalty_frequency: 0, repetition_penalty_presence: 0, top_a: 0, top_p: 1, top_k: 0, min_p: 0, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 1, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'off', order: null, logit_bias: [], banned_tokens: [], prefix: 'vanilla' }, { stoppingStrings: ['\nChar:', 'x'], finalPrompt: '...' });
add('old-model', { model_novel: 'euterpe-v2', temperature: 1, min_length: 0, tail_free_sampling: 1, repetition_penalty: 1, repetition_penalty_range: 1024, repetition_penalty_slope: 0.9, repetition_penalty_frequency: 0, repetition_penalty_presence: 0, top_a: 0, top_p: 1, top_k: 0, min_p: 0, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 1, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'off', order: null, logit_bias: [], banned_tokens: [], prefix: 'vanilla' }, { maxLength: 999, maximumOutputLength: 600 });
add('kayra-prefix-instruct', { model_novel: 'kayra-v1', temperature: 0.5, min_length: 0, tail_free_sampling: 0.95, repetition_penalty: 1.05, repetition_penalty_range: 1024, repetition_penalty_slope: 0.9, repetition_penalty_frequency: 0, repetition_penalty_presence: 0, top_a: 0, top_p: 0.9, top_k: 0, min_p: 0, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 1, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'off', order: null, logit_bias: [], banned_tokens: [], prefix: 'vanilla' }, { finalPrompt: 'a'.repeat(1600) + '}' });
add('kayra-logprobs', { model_novel: 'kayra-v1', temperature: 1, min_length: 0, tail_free_sampling: 1, repetition_penalty: 1, repetition_penalty_range: 1024, repetition_penalty_slope: 0.9, repetition_penalty_frequency: 0, repetition_penalty_presence: 0, top_a: 0, top_p: 1, top_k: 0, min_p: 0, math1_temp: 1, math1_quad: 0, math1_quad_entropy_scale: 0, typical_p: 1, mirostat_lr: 0.1, mirostat_tau: 5, phrase_rep_pen: 'off', order: null, logit_bias: [], banned_tokens: [], prefix: 'vanilla' }, { requestTokenProbabilities: true });

const out = join(__dirname, '..', '..', 'engine/src/test/resources/diff/novel-body.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(cases, null, 1) + '\n');
console.log('novel-body cases:', cases.length, '->', out);
