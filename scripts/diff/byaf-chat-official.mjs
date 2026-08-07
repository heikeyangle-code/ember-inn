#!/usr/bin/env node
// BYAF getChatFromScenario（src/byaf.js）→ JSON fixture。
// 方法体 + replaceMacros/formatExampleMessages 逐字提取，Date/encodeURI/console 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'byaf-chat.json');
const src = readFileSync(join(officialRef, 'src', 'byaf.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && source[i + 1] !== '/' && source[i + 1] !== '*' &&
            (i === 0 || !/[A-Za-z0-9_$)]/.test(source[i - 1]))) {
            inRegex = true;
            continue;
        }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractMethod(signature, name) {
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
    return src.slice(start, scanBody(src, bodyStart) + 1);
}

const replaceMacros = extractMethod('static replaceMacros(str)', 'replaceMacros');
const formatExampleMessages = extractMethod('static formatExampleMessages(examples)', 'formatExampleMessages');
const getChatFromScenario = extractMethod('static getChatFromScenario(scenario, userName, characterName, chatBackgrounds)', 'getChatFromScenario');

const runCase = new Function([
    'const console = { info: () => {}, warn: () => {}, error: () => {} };',
    'class ByafParser {',
    replaceMacros,
    formatExampleMessages,
    getChatFromScenario,
    '}',
    'return async (request) => {',
    "    Date.prototype.toISOString = () => request.body.fixedISO ?? '2026-08-08T00:00:00.000Z';",
    '    return ByafParser.getChatFromScenario(',
    '        request.body.scenario ?? null,',
    '        request.body.userName ?? "",',
    '        request.body.characterName ?? "",',
    '        request.body.chatBackgrounds ?? [],',
    '    );',
    '};',
].join('\n'));

const cases = [];
async function add(id, scenario, chatBackgrounds = [], extra = {}) {
    const expected = await runCase()({ body: { scenario, userName: '玩家', characterName: '角色', chatBackgrounds, fixedISO: '2026-08-08T00:00:00.000Z', ...extra } });
    cases.push({ id, args: { body: { scenario, userName: '玩家', characterName: '角色', chatBackgrounds, ...extra } }, expected });
}

await add('basic', {
    narrative: '场景#{user}',
    exampleMessages: [{ text: '示例' }],
    formattingInstructions: '指令{character}',
    canDeleteExampleMessages: true,
    model: 'by-1',
    temperature: 1.2,
    topK: 40,
    topP: 0.9,
    minP: 0.1,
    minPEnabled: true,
    repeatPenalty: 1.05,
    repeatLastN: 256,
    promptTemplate: 'general',
    grammar: null,
    backgroundImage: 'bg.png',
    firstMessages: [{ text: '开场' }],
    messages: [],
}, [{ name: '森林', paths: ['bg.png', 'other.png'] }]);

await add('interleaved', {
    narrative: '故事',
    firstMessages: [{ text: '你好' }],
    messages: [
        { type: 'ai', createdAt: '1000', outputs: [{ text: '回复A', activeTimestamp: '1000' }, { text: '回复B', activeTimestamp: '2000' }] },
        { type: 'human', createdAt: '3000', text: '玩家说' },
    ],
});

await add('unequal-order', {
    narrative: '顺序',
    messages: [
        { type: 'human', createdAt: '100', text: '先' },
        { type: 'human', createdAt: '200', text: '后' },
        { type: 'ai', createdAt: '300', outputs: [{ text: 'AI', activeTimestamp: '300' }] },
    ],
});

await add('no-messages', {
    narrative: '无消息',
    firstMessages: [{ text: '开场白' }],
});

await add('empty-first-and-raw-types', {
    narrative: '原始类型',
    canDeleteExampleMessages: 'true',
    temperature: '0.8',
    topK: '30',
    topP: '0.7',
    minP: '0.2',
    minPEnabled: 'false',
    repeatPenalty: '1.1',
    repeatLastN: '128',
    promptTemplate: 'custom',
    grammar: 'EBNF',
    backgroundImage: 'missing.png',
    firstMessages: [{ text: '' }],
    messages: null,
});

writeFileSync(outFile, JSON.stringify({ source: 'src/byaf.js getChatFromScenario', cases }, null, 2));
console.log('byaf-chat:', cases.length, 'cases ->', outFile);
