#!/usr/bin/env node
// 群聊完整循环纯逻辑（shouldAutoContinue + generateGroupWrapper 计划）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'group-loop.json');

const funcs = `
function shouldAutoContinue(messageChunk, isImpersonate, settings, userInputEmpty, lastMessageTokens, isOpenAi) {
    if (!settings.enabled) return false;
    if (typeof messageChunk !== 'string') return false;
    if (isImpersonate) return false;
    if (settings.target_length <= 0) return false;
    if (isOpenAi && !settings.allow_chat_completions) return false;
    if (!userInputEmpty) return false;
    const USABLE_LENGTH = 5;
    if (messageChunk.trim().length > USABLE_LENGTH && lastMessageTokens !== null) {
        const shouldAutoContinue = lastMessageTokens < settings.target_length;
        return shouldAutoContinue;
    }
    return false;
}

function planGeneration(type, activatedMembers, showQueue) {
    const plan = activatedMembers.map((avatar, i) => ({
        avatar,
        generateType: ['swipe', 'impersonate', 'quiet', 'continue'].includes(type) ? type : 'normal',
        queue: showQueue ? i + 1 : null,
    }));
    return { plan, queueOrder: showQueue ? activatedMembers.map((a, i) => [a, i + 1]) : [] };
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    if (b.method === "continue") return shouldAutoContinue(b.messageChunk ?? null, b.isImpersonate ?? false, b.settings, b.userInputEmpty ?? true, b.lastMessageTokens ?? null, b.isOpenAi ?? false);',
    '    if (b.method === "plan") return planGeneration(b.type ?? "normal", b.activatedMembers ?? [], b.showQueue ?? false);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('continue-disabled', { method: 'continue', messageChunk: 'hello', settings: { enabled: false, target_length: 100, allow_chat_completions: true } });
await add('continue-short', { method: 'continue', messageChunk: 'hi', settings: { enabled: true, target_length: 100, allow_chat_completions: true } });
await add('continue-true', { method: 'continue', messageChunk: 'hello world', settings: { enabled: true, target_length: 100, allow_chat_completions: true }, lastMessageTokens: 50 });
await add('continue-target-hit', { method: 'continue', messageChunk: 'hello world', settings: { enabled: true, target_length: 100, allow_chat_completions: true }, lastMessageTokens: 120 });
await add('continue-impersonate', { method: 'continue', messageChunk: 'hello world', isImpersonate: true, settings: { enabled: true, target_length: 100, allow_chat_completions: true }, lastMessageTokens: 50 });
await add('continue-user-input', { method: 'continue', messageChunk: 'hello world', settings: { enabled: true, target_length: 100, allow_chat_completions: true }, userInputEmpty: false, lastMessageTokens: 50 });
await add('continue-openai-off', { method: 'continue', messageChunk: 'hello world', settings: { enabled: true, target_length: 100, allow_chat_completions: false }, lastMessageTokens: 50, isOpenAi: true });
await add('plan-normal', { method: 'plan', type: 'normal', activatedMembers: ['a', 'b', 'c'], showQueue: true });
await add('plan-continue', { method: 'plan', type: 'continue', activatedMembers: ['a', 'b'], showQueue: false });
await add('plan-impersonate', { method: 'plan', type: 'impersonate', activatedMembers: ['b'], showQueue: true });
await add('plan-empty', { method: 'plan', type: 'quiet', activatedMembers: [], showQueue: true });

writeFileSync(outFile, JSON.stringify({ source: 'group-chats.js + script.js 群聊循环纯逻辑', cases }, null, 2));
console.log('group-loop:', cases.length, 'cases ->', outFile);
