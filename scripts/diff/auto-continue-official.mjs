#!/usr/bin/env node
// script.js shouldAutoContinue → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd script.js:5657。
// 打桩：getTokenCount 由用例提供；$('#send_textarea').val() 由 textareaText 提供；
// is_send_press / abortController / chat / main_api / power_user.auto_continue 由用例设置。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'auto-continue.json');

const funcs = `
let power_user = { auto_continue: { enabled: false, target_length: 0, allow_chat_completions: true } };
let is_send_press = false;
let abortController = null;
let main_api = 'openai';
let chat = [];
let getTokenCount = (text) => 0;

function shouldAutoContinue(messageChunk, isImpersonate) {
    if (!power_user.auto_continue.enabled) {
        return false;
    }
    if (typeof messageChunk !== 'string') {
        return false;
    }
    if (isImpersonate) {
        return false;
    }
    if (is_send_press) {
        return false;
    }
    if (abortController && abortController.signal.aborted) {
        return false;
    }
    if (power_user.auto_continue.target_length <= 0) {
        return false;
    }
    if (main_api === 'openai' && !power_user.auto_continue.allow_chat_completions) {
        return false;
    }
    const textareaText = String(globalThis.__textareaText);
    const USABLE_LENGTH = 5;
    if (textareaText.length > 0) {
        return false;
    }
    if (messageChunk.trim().length > USABLE_LENGTH && chat.length) {
        const lastMessage = chat[chat.length - 1];
        const messageLength = getTokenCount(lastMessage.mes);
        const shouldAutoContinue = messageLength < power_user.auto_continue.target_length;
        if (shouldAutoContinue) {
            return true;
        } else {
            return false;
        }
    } else {
        return false;
    }
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    power_user.auto_continue = {',
    '        enabled: b.enabled ?? false,',
    '        target_length: b.targetLength ?? 0,',
    '        allow_chat_completions: b.allowChatCompletions ?? true,',
    '    };',
    '    is_send_press = b.isSendPress ?? false;',
    '    abortController = b.generationStopped ? { signal: { aborted: true } } : null;',
    '    main_api = b.mainApi ?? "openai";',
    '    chat = b.lastMessageText !== undefined ? [{ mes: b.lastMessageText }] : [];',
    '    globalThis.__textareaText = b.textareaText ?? "";',
    '    getTokenCount = (text) => b.tokenCounts?.[text] ?? 0;',
    '    return shouldAutoContinue(b.messageChunk, b.isImpersonate ?? false);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('disabled', { messageChunk: 'long enough text', enabled: false });
await add('impersonate', { messageChunk: 'long enough text', enabled: true, targetLength: 100, isImpersonate: true, lastMessageText: 'x' });
await add('send-press', { messageChunk: 'long enough text', enabled: true, targetLength: 100, isSendPress: true, lastMessageText: 'x' });
await add('stopped', { messageChunk: 'long enough text', enabled: true, targetLength: 100, generationStopped: true, lastMessageText: 'x' });
await add('target-zero', { messageChunk: 'long enough text', enabled: true, targetLength: 0, lastMessageText: 'x' });
await add('openai-disallowed', { messageChunk: 'long enough text', enabled: true, targetLength: 100, allowChatCompletions: false, mainApi: 'openai', lastMessageText: 'x' });
await add('textarea-not-empty', { messageChunk: 'long enough text', enabled: true, targetLength: 100, textareaText: 'draft', lastMessageText: 'x' });
await add('short-chunk', { messageChunk: 'short', enabled: true, targetLength: 100, lastMessageText: 'x' });
await add('no-last-message', { messageChunk: 'long enough text', enabled: true, targetLength: 100 });
await add('already-long', { messageChunk: 'long enough text', enabled: true, targetLength: 10, lastMessageText: 'hello world', tokenCounts: { 'hello world': 20 } });
await add('should-continue', { messageChunk: 'long enough text', enabled: true, targetLength: 100, lastMessageText: 'hello world', tokenCounts: { 'hello world': 40 } });

writeFileSync(outFile, JSON.stringify({ source: 'shouldAutoContinue', cases }, null, 2));
console.log('auto-continue:', cases.length, 'cases ->', outFile);
