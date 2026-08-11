#!/usr/bin/env node
// extractMessageBias / getBiasStrings / removeMacros（script.js:3081/5735/5801）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd；Handlebars 用官方同版本 vendor。
// 打桩：substituteParams=恒等；chat/power_user 由用例设置。

import { createRequire } from 'node:module';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'bias.json');
const require = createRequire(import.meta.url);
const Handlebars = require('./vendor/node_modules/handlebars');

const funcs = `
const system_message_types = { NARRATOR: 'narrator' };
let power_user = { user_prompt_bias: '' };
let chat = [];
const substituteParams = (text) => String(text ?? '');

function extractMessageBias(message) {
    if (!message) {
        return '';
    }
    try {
        const biasHandlebars = Handlebars.create();
        const biasMatches = [];
        biasHandlebars.registerHelper('bias', function (text) {
            biasMatches.push(text);
            return '';
        });
        const template = biasHandlebars.compile(message);
        template({});
        if (biasMatches && biasMatches.length > 0) {
            return \` \${biasMatches.join(' ')}\`;
        }
        return '';
    } catch {
        return '';
    }
}

function getBiasStrings(textareaText, type) {
    if (type == 'impersonate' || type == 'continue') {
        return { messageBias: '', promptBias: '', isUserPromptBias: false };
    }
    let promptBias = '';
    let messageBias = extractMessageBias(textareaText);
    if (!textareaText) {
        for (let i = chat.length - 1; i >= 0; i--) {
            const mes = chat[i];
            if (type === 'swipe' && chat.length - 1 === i) {
                continue;
            }
            if (mes && (mes.is_user || mes.is_system || mes.extra?.type === system_message_types.NARRATOR)) {
                if (mes.extra?.bias?.trim()?.length > 0) {
                    promptBias = mes.extra.bias;
                }
                break;
            }
        }
    }
    promptBias = messageBias || promptBias || power_user.user_prompt_bias || '';
    const isUserPromptBias = promptBias === power_user.user_prompt_bias;
    messageBias = substituteParams(messageBias);
    promptBias = substituteParams(promptBias);
    return { messageBias, promptBias, isUserPromptBias };
}

function removeMacros(str) {
    return (str ?? '').replace(/\\{\\{[\\s\\S]*?\\}\\}/gm, '').trim();
}
`;

const runCase = new Function('Handlebars', [
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    power_user.user_prompt_bias = b.userPromptBias ?? "";',
    '    chat = b.chat ?? [];',
    '    if (b.method === "bias") return extractMessageBias(b.text);',
    '    if (b.method === "get") return getBiasStrings(b.textareaText ?? "", b.type ?? "normal");',
    '    if (b.method === "removeMacros") return removeMacros(b.text);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase(Handlebars)({ body });
    cases.push({ id, args: { body }, expected });
}

await add('bias-none', { method: 'bias', text: 'hello world' });
await add('bias-simple', { method: 'bias', text: 'before {{bias hello}} after' });
await add('bias-quoted', { method: 'bias', text: '{{bias "hello world"}}' });
await add('bias-single-quoted', { method: 'bias', text: "{{bias 'single quoted'}}" });
await add('bias-multiple', { method: 'bias', text: '{{bias a}} and {{bias b}}' });
await add('bias-unclosed', { method: 'bias', text: '{{bias broken' });
await add('bias-empty-tag', { method: 'bias', text: '{{bias}}' });
await add('remove-macros-basic', { method: 'removeMacros', text: 'a {{foo}} b' });
await add('remove-macros-multi', { method: 'removeMacros', text: '{{a}}{{b}} tail' });
await add('remove-macros-null', { method: 'removeMacros', text: null });
await add('get-impersonate', { method: 'get', textareaText: 'x', type: 'impersonate', userPromptBias: 'U' });
await add('get-continue', { method: 'get', textareaText: 'x', type: 'continue', userPromptBias: 'U' });
await add('get-message-bias', { method: 'get', textareaText: '{{bias B}} text', type: 'normal', userPromptBias: 'U' });
await add('get-user-prompt-bias', { method: 'get', textareaText: 'text', type: 'normal', userPromptBias: 'U' });
await add('get-chat-bias', {
    method: 'get', textareaText: '', type: 'normal', userPromptBias: 'U',
    chat: [{ is_user: false, extra: { type: 'narrator', bias: 'N' } }, { is_user: true, extra: { bias: 'C' } }],
});
await add('get-swipe-skip-last', {
    method: 'get', textareaText: '', type: 'swipe', userPromptBias: 'U',
    chat: [{ is_user: true, extra: { bias: 'LAST' } }, { is_user: true, extra: { bias: 'PREV' } }],
});
await add('get-empty-bias-chat', { method: 'get', textareaText: '', type: 'normal', userPromptBias: 'U', chat: [{ is_user: true, extra: { bias: '   ' } }] });

writeFileSync(outFile, JSON.stringify({ source: 'extractMessageBias/getBiasStrings/removeMacros', cases }, null, 2));
console.log('bias:', cases.length, 'cases ->', outFile);
