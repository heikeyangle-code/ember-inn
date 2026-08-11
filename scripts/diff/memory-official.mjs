#!/usr/bin/env node
// extensions/memory 纯逻辑：getLatestMemoryFromChat / getIndexOfLatestChatSummary /
// getSummaryPromptForNow / getRawSummaryPrompt / formatMemoryValue（index.js:353/374/559/756/240）→ fixture。
// 打桩：substituteParamsExtended=恒等、countSourceTokens=len+padding、extractAllWords 官方实现。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'memory.json');

const funcs = `
let extension_settings = { memory: { promptInterval: 10, promptForceWords: 0, promptWords: 200, prompt: 'Summarize.' } };
const substituteParamsExtended = (text) => text;
const countSourceTokens = async (str, padding) => str.length + padding;
let getSourceContextSize = async () => 4096;
const chatBuffer = [];
let latestSummary = '';

const defaultTemplate = '[Summary: {{summary}}]';
function formatMemoryValue(value, template, substitute) {
    if (!value) return '';
    value = value.trim();
    if (template) {
        return substitute(template);
    } else {
        return 'Summary: ' + value;
    }
}

function extractAllWords(value) {
    const words = [];
    if (!value) return words;
    const matches = value.matchAll(/\\b\\w+\\b/gim);
    for (let match of matches) {
        words.push(match[0]);
    }
    return words;
}

function getLatestMemoryFromChat(chat) {
    if (!Array.isArray(chat) || !chat.length) return '';
    const reversedChat = chat.slice().reverse();
    reversedChat.shift();
    for (let mes of reversedChat) {
        if (mes.extra && mes.extra.memory) return mes.extra.memory;
    }
    return '';
}

function getIndexOfLatestChatSummary(chat) {
    if (!Array.isArray(chat) || !chat.length) return -1;
    const reversedChat = chat.slice().reverse();
    reversedChat.shift();
    for (let mes of reversedChat) {
        if (mes.extra && mes.extra.memory) return chat.indexOf(mes);
    }
    return -1;
}

async function getSummaryPromptForNow(context, force) {
    if (extension_settings.memory.promptInterval === 0 && !force) return '';
    if (!context.chat.length) return '';
    if (context.chat.length < extension_settings.memory.promptInterval && !force) return '';
    let messagesSinceLastSummary = 0;
    let wordsSinceLastSummary = 0;
    let conditionSatisfied = false;
    for (let i = context.chat.length - 1; i >= 0; i--) {
        if (context.chat[i].extra && context.chat[i].extra.memory) break;
        messagesSinceLastSummary++;
        wordsSinceLastSummary += extractAllWords(context.chat[i].mes).length;
    }
    if (messagesSinceLastSummary >= extension_settings.memory.promptInterval) conditionSatisfied = true;
    if (extension_settings.memory.promptForceWords && wordsSinceLastSummary >= extension_settings.memory.promptForceWords) conditionSatisfied = true;
    if (!conditionSatisfied && !force) return '';
    const prompt = substituteParamsExtended(extension_settings.memory.prompt);
    if (!prompt) return '';
    return prompt;
}

async function getRawSummaryPrompt(context, prompt) {
    function getMemoryString(includeSystem) {
        const delimiter = '\\n\\n';
        const stringBuilder = [];
        const bufferString = chatBuffer.slice().join(delimiter);
        if (includeSystem) stringBuilder.push(prompt);
        if (latestSummary) stringBuilder.push(latestSummary);
        stringBuilder.push(bufferString);
        return stringBuilder.join(delimiter).trim();
    }
    const chat = context.chat.slice();
    latestSummary = getLatestMemoryFromChat(chat);
    const latestSummaryIndex = getIndexOfLatestChatSummary(chat);
    chat.pop();
    const PADDING = 64;
    const PROMPT_SIZE = await getSourceContextSize();
    let latestUsedMessage = null;
    for (let index = latestSummaryIndex + 1; index < chat.length; index++) {
        const message = chat[index];
        if (!message) break;
        if (message.is_system || !message.mes) continue;
        const entry = \`\${message.name}:\\n\${message.mes}\`;
        chatBuffer.push(entry);
        const tokens = await countSourceTokens(getMemoryString(true), PADDING);
        if (tokens > PROMPT_SIZE) {
            chatBuffer.pop();
            break;
        }
        latestUsedMessage = message;
        if (extension_settings.memory.maxMessagesPerRequest > 0 && chatBuffer.length >= extension_settings.memory.maxMessagesPerRequest) break;
    }
    const lastUsedIndex = context.chat.indexOf(latestUsedMessage);
    const rawPrompt = getMemoryString(false);
    return { rawPrompt, lastUsedIndex };
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    extension_settings.memory = {',
    '        promptInterval: b.promptInterval ?? 10,',
    '        promptForceWords: b.promptForceWords ?? 0,',
    '        promptWords: b.promptWords ?? 200,',
    '        prompt: b.prompt ?? "Summarize.",',
    '        maxMessagesPerRequest: b.maxMessagesPerRequest ?? 0,',
    '    };',
    '    chatBuffer.length = 0;',
    '    latestSummary = "";',
    '    if (b.method === "latest") return getLatestMemoryFromChat(b.chat);',
    '    if (b.method === "index") return getIndexOfLatestChatSummary(b.chat);',
    '    if (b.method === "prompt") return await getSummaryPromptForNow({ chat: b.chat }, b.force ?? false);',
    '    if (b.method === "raw") {',
    '        getSourceContextSize = async () => b.promptSize ?? 4096;',
    '        return await getRawSummaryPrompt({ chat: b.chat }, b.prompt ?? "Summarize.");',
    '    }',
    '    if (b.method === "format") {',
    '        const substitute = (t) => String(t).replaceAll("{{summary}}", b.value.trim()).replaceAll("{{user}}", "User");',
    '        return formatMemoryValue(b.value, b.template, substitute);',
    '    }',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const chat = (items) => items.map(m => ({ name: m.name ?? 'User', mes: m.mes ?? '', is_system: m.is_system ?? false, extra: m.memory ? { memory: m.memory } : {} }));

await add('latest-empty', { method: 'latest', chat: [] });
await add('latest-skip-last', { method: 'latest', chat: chat([{ name: 'A', mes: 'x', memory: 'M' }, { name: 'B', mes: 'y' }]) });
await add('latest-none', { method: 'latest', chat: chat([{ name: 'A', mes: 'x' }]) });
await add('index-found', { method: 'index', chat: chat([{ name: 'A', mes: 'x', memory: 'M' }, { name: 'B', mes: 'y' }]) });
await add('index-not-found', { method: 'index', chat: chat([{ name: 'A', mes: 'x' }]) });
await add('prompt-interval-zero', { method: 'prompt', promptInterval: 0, chat: chat([{ name: 'A', mes: 'x' }]) });
await add('prompt-too-short', { method: 'prompt', promptInterval: 5, chat: chat([{ name: 'A', mes: 'x' }]) });
await add('prompt-satisfied', { method: 'prompt', promptInterval: 2, chat: chat([{ name: 'A', mes: 'one two' }, { name: 'B', mes: 'three four' }]) });
await add('prompt-force-words', { method: 'prompt', promptInterval: 100, promptForceWords: 3, chat: chat([{ name: 'A', mes: 'one two three four' }]) });
await add('prompt-force', { method: 'prompt', promptInterval: 100, force: true, chat: chat([{ name: 'A', mes: 'x' }]) });
await add('raw-basic', {
    method: 'raw', promptSize: 1000, maxMessagesPerRequest: 0,
    chat: chat([{ name: 'A', mes: 'aaaa' }, { name: 'B', mes: 'bbbb' }, { name: 'C', mes: 'cccc' }]),
});
await add('raw-with-memory', {
    method: 'raw', promptSize: 1000, maxMessagesPerRequest: 0,
    chat: chat([{ name: 'A', mes: 'old', memory: 'SUMMARY' }, { name: 'B', mes: 'bbbb' }, { name: 'C', mes: 'cccc' }]),
});
await add('raw-max-messages', {
    method: 'raw', promptSize: 1000, maxMessagesPerRequest: 1,
    chat: chat([{ name: 'A', mes: 'aaaa' }, { name: 'B', mes: 'bbbb' }, { name: 'C', mes: 'cccc' }]),
});
await add('raw-token-limit', {
    method: 'raw', promptSize: 40, maxMessagesPerRequest: 0,
    chat: chat([{ name: 'A', mes: 'aaaa' }, { name: 'B', mes: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbb' }, { name: 'C', mes: 'cccc' }]),
});
await add('format-empty', { method: 'format', value: '', template: '[Summary: {{summary}}]' });
await add('format-default-template', { method: 'format', value: '  故事摘要  ', template: '[Summary: {{summary}}]' });
await add('format-custom-template', { method: 'format', value: '摘要', template: '记忆：{{summary}}（{{user}}）' });
await add('format-no-template', { method: 'format', value: '摘要', template: '' });

writeFileSync(outFile, JSON.stringify({ source: 'extensions/memory pure logic', cases }, null, 2));
console.log('memory:', cases.length, 'cases ->', outFile);
