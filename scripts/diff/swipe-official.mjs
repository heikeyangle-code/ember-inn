#!/usr/bin/env node
// isSwipingAllowed / isMessageSwipeable / getOverswipeBehavior / ensureSwipes（script.js）
// + generatedTextFiltered（power-user.js）+ extractMultiSwipes（script.js）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩：cleanUpMessage=恒等（已单独差分）；syncMesToSwipe=no-op；swipe 全局状态由用例设置。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'swipe.json');

const funcs = `
const SWIPE_STATE = { NONE: 'none', EDITING: 'editing' };
const OVERSWIPE_BEHAVIOR = { NONE: 'none', PRISTINE_GREETING: 'pristine_greeting', REGENERATE: 'regenerate', LOOP: 'loop' };
const textgen_types = { LLAMACPP: 'llamacpp', MANCER: 'mancer', VLLM: 'vllm', APHRODITE: 'aphrodite', TABBY: 'tabby', INFERMATICAI: 'infermaticai' };
let chat = [];
let swipes = true;
let swipesHidden = false;
let is_generating = false;
let swipeState = SWIPE_STATE.NONE;
let this_edit_mes_id = null;
let chat_metadata = { tainted: false };
let power_user = { auto_swipe_minimum_length: 0, auto_swipe_blacklist: [], auto_swipe_blacklist_threshold: 0 };
let main_api = 'openai';
let textgen_settings = { type: '' };
const cleanUpMessage = ({ getMessage }) => getMessage;
const syncMesToSwipe = () => false;

function isSwipingAllowed() {
    return (
        chat.length !== 0 &&
        swipes && !swipesHidden &&
        !is_generating() &&
        swipeState === SWIPE_STATE.NONE
    );
}

function isMessageSwipeable(messageId, message = undefined) {
    message ??= chat[messageId];
    if (ensureSwipes(message)) {
        syncMesToSwipe(messageId);
    }
    if (
        ((messageId > (this_edit_mes_id ?? -1)) && (swipeState != SWIPE_STATE.EDITING)) &&
        (messageId == chat.length - 1) &&
        (message &&
            !(message?.extra?.isSmallSys) &&
            !(message?.extra?.swipeable === false) &&
            !message.is_user
        )
    ) {
        return true;
    } else {
        return false;
    }
}

function getOverswipeBehavior(messageId, message = undefined) {
    message ??= chat[messageId];
    const isPristine = !chat_metadata?.tainted;
    const isGreeting = messageId === 0;
    if (typeof message?.extra?.overswipe_behavior == 'string') return message.extra.overswipe_behavior;
    else if (message?.extra?.swipeable === false) return OVERSWIPE_BEHAVIOR.NONE;
    else if (message?.extra?.isSmallSys) return OVERSWIPE_BEHAVIOR.NONE;
    else if (isGreeting && isPristine) return OVERSWIPE_BEHAVIOR.PRISTINE_GREETING;
    else if (!message?.is_user && !message?.is_system) return OVERSWIPE_BEHAVIOR.REGENERATE;
    else { return OVERSWIPE_BEHAVIOR.LOOP; }
}

function ensureSwipes(message) {
    let updated = false;
    if (!message || typeof message !== 'object') {
        return updated;
    }
    if (message?.is_user || message?.extra?.isSmallSys) {
        return updated;
    }
    if (!Array.isArray(message.swipes)) {
        message.swipes = [message.mes ?? ''];
        updated = true;
    }
    if (typeof message.swipe_id !== 'number') {
        message.swipe_id = 0;
        updated = true;
    }
    const createSwipeInfo = () => ({ send_date: message.send_date, gen_started: message.gen_started, gen_finished: message.gen_finished, extra: {} });
    if (!Array.isArray(message.swipe_info)) {
        message.swipe_info = message.swipes.map(_ => createSwipeInfo());
        updated = true;
    }
    for (let i = 0; i < message.swipes.length; i++) {
        if (typeof message.swipes[i] !== 'string') {
            updated = true;
            message.swipes[i] = '';
        }
        if (!message.swipe_info[i] || typeof message.swipe_info[i] !== 'object') {
            updated = true;
            message.swipe_info[i] = createSwipeInfo();
        }
    }
    return updated;
}

function generatedTextFiltered(text) {
    function containsBlacklistedWords(text, blacklist, threshold) {
        const regex = new RegExp(\`\\\\b(\${blacklist.join('|')})\\\\b\`, 'gi');
        const matches = text.match(regex) || [];
        return matches.length >= threshold;
    }
    text = text.trim();
    if (text.length > 0) {
        if (power_user.auto_swipe_minimum_length) {
            if (text.length < power_user.auto_swipe_minimum_length) {
                return true;
            }
        }
        if (power_user.auto_swipe_blacklist.length && power_user.auto_swipe_blacklist_threshold) {
            if (containsBlacklistedWords(text, power_user.auto_swipe_blacklist, power_user.auto_swipe_blacklist_threshold)) {
                return true;
            }
        }
    }
    return false;
}

function extractMultiSwipes(data, type) {
    const swipes = [];
    if (!data) {
        return swipes;
    }
    if (type === 'continue' || type === 'impersonate' || type === 'quiet') {
        return swipes;
    }
    if (main_api === 'textgenerationwebui' && textgen_settings.type === textgen_types.LLAMACPP) {
        if (!Array.isArray(data)) {
            return swipes;
        }
        const multiSwipeCount = data.length - 1;
        if (multiSwipeCount <= 0) {
            return swipes;
        }
        for (let i = 1; i < data.length; i++) {
            const text = data?.[i]?.content ?? '';
            swipes.push(text);
        }
    }
    if (main_api === 'openai' || (main_api === 'textgenerationwebui' && [textgen_types.MANCER, textgen_types.VLLM, textgen_types.APHRODITE, textgen_types.TABBY, textgen_types.INFERMATICAI].includes(textgen_settings.type))) {
        if (!Array.isArray(data.choices)) {
            return swipes;
        }
        const multiSwipeCount = data.choices.length - 1;
        if (multiSwipeCount <= 0) {
            return swipes;
        }
        for (let i = 1; i < data.choices.length; i++) {
            const text = data.choices[i]?.message?.content ?? data.choices[i]?.text ?? '';
            swipes.push(text);
        }
    }
    const cleanedSwipes = swipes.map(text => cleanUpMessage({
        getMessage: text,
        isImpersonate: false,
        isContinue: false,
        displayIncompleteSentences: false,
    }));
    return cleanedSwipes;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    chat = b.chat ?? [];',
    '    swipes = b.swipesEnabled ?? true;',
    '    swipesHidden = b.swipesHidden ?? false;',
    '    is_generating = () => b.isGenerating ?? false;',
    '    swipeState = b.midSwipe ? SWIPE_STATE.EDITING : SWIPE_STATE.NONE;',
    '    this_edit_mes_id = b.thisEditMesId ?? null;',
    '    chat_metadata = { tainted: b.chatTainted ?? false };',
    '    power_user.auto_swipe_minimum_length = b.minimumLength ?? 0;',
    '    power_user.auto_swipe_blacklist = b.blacklist ?? [];',
    '    power_user.auto_swipe_blacklist_threshold = b.threshold ?? 0;',
    '    main_api = b.mainApi ?? "openai";',
    '    textgen_settings.type = b.textgenType ?? "";',
    '    if (b.method === "allowed") return isSwipingAllowed();',
    '    if (b.method === "swipeable") return isMessageSwipeable(b.messageId, b.message);',
    '    if (b.method === "overswipe") return getOverswipeBehavior(b.messageId, b.message);',
    '    if (b.method === "ensure") { const m = structuredClone(b.message); const updated = ensureSwipes(m); return { updated, message: m }; }',
    '    if (b.method === "filtered") return generatedTextFiltered(b.text);',
    '    if (b.method === "multi") return extractMultiSwipes(b.data, b.type ?? "normal");',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('allowed-basic', { method: 'allowed', chat: [{}] });
await add('allowed-empty-chat', { method: 'allowed', chat: [] });
await add('allowed-disabled', { method: 'allowed', chat: [{}], swipesEnabled: false });
await add('allowed-hidden', { method: 'allowed', chat: [{}], swipesHidden: true });
await add('allowed-generating', { method: 'allowed', chat: [{}], isGenerating: true });
await add('allowed-mid-swipe', { method: 'allowed', chat: [{}], midSwipe: true });
await add('swipeable-ai-last', { method: 'swipeable', messageId: 0, chat: [{ is_user: false }], message: { is_user: false } });
await add('swipeable-user', { method: 'swipeable', messageId: 0, chat: [{ is_user: true }], message: { is_user: true } });
await add('swipeable-small-sys', { method: 'swipeable', messageId: 0, chat: [{ extra: { isSmallSys: true } }], message: { extra: { isSmallSys: true } } });
await add('swipeable-not-last', { method: 'swipeable', messageId: 0, chat: [{}, {}], message: { is_user: false } });
await add('swipeable-disabled', { method: 'swipeable', messageId: 0, chat: [{ extra: { swipeable: false } }], message: { extra: { swipeable: false } } });
await add('overswipe-explicit', { method: 'overswipe', messageId: 0, message: { extra: { overswipe_behavior: 'custom' } } });
await add('overswipe-disabled', { method: 'overswipe', messageId: 0, message: { extra: { swipeable: false } } });
await add('overswipe-small-sys', { method: 'overswipe', messageId: 0, message: { extra: { isSmallSys: true } } });
await add('overswipe-pristine-greeting', { method: 'overswipe', messageId: 0, message: { is_user: false } });
await add('overswipe-regenerate', { method: 'overswipe', messageId: 3, message: { is_user: false } });
await add('overswipe-loop-user', { method: 'overswipe', messageId: 2, message: { is_user: true } });
await add('ensure-initializes', { method: 'ensure', message: { mes: 'hi' } });
await add('ensure-user-skip', { method: 'ensure', message: { mes: 'hi', is_user: true } });
await add('ensure-valid-noop', { method: 'ensure', message: { mes: 'hi', swipes: ['hi'], swipe_id: 0, swipe_info: [{}] } });
await add('filtered-empty', { method: 'filtered', text: '  ' });
await add('filtered-too-short', { method: 'filtered', text: 'abc', minimumLength: 5 });
await add('filtered-blacklist', { method: 'filtered', text: 'bad word here', blacklist: ['bad'], threshold: 1 });
await add('filtered-blacklist-threshold', { method: 'filtered', text: 'bad bad', blacklist: ['bad'], threshold: 2 });
await add('filtered-clean', { method: 'filtered', text: 'good text', minimumLength: 3, blacklist: ['bad'], threshold: 1 });
await add('multi-openai', { method: 'multi', mainApi: 'openai', data: { choices: [{ message: { content: 'a' } }, { message: { content: 'b' } }, { text: 'c' }] } });
await add('multi-openai-single', { method: 'multi', mainApi: 'openai', data: { choices: [{ message: { content: 'a' } }] } });
await add('multi-llamacpp', { method: 'multi', mainApi: 'textgenerationwebui', textgenType: 'llamacpp', data: [{ content: 'a' }, { content: 'b' }] });
await add('multi-skip-type', { method: 'multi', type: 'continue', mainApi: 'openai', data: { choices: [{}, {}] } });

writeFileSync(outFile, JSON.stringify({ source: 'swipe/generatedTextFiltered/extractMultiSwipes', cases }, null, 2));
console.log('swipe:', cases.length, 'cases ->', outFile);
