#!/usr/bin/env node
// BYAF 纯逻辑（src/byaf.js：replaceMacros/formatExampleMessages/formatAlternateGreetings/convertCharacterBook）→ JSON fixture。
// 方法体逐字提取，类壳仅用于承载 static/实例方法，无其它依赖。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'byaf-macros.json');

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
const formatAlternateGreetings = extractMethod('formatAlternateGreetings(scenarios)', 'formatAlternateGreetings');
const convertCharacterBook = extractMethod('convertCharacterBook(items)', 'convertCharacterBook');

const runCase = new Function([
    'class ByafParser {',
    replaceMacros,
    formatExampleMessages,
    formatAlternateGreetings,
    convertCharacterBook,
    '}',
    'return async (request) => {',
    '    const parser = new ByafParser();',
    '    const method = request.body.method;',
    '    const args = request.body.args;',
    '    if (method === "replaceMacros") return ByafParser.replaceMacros(args[0]);',
    '    if (method === "formatExampleMessages") return ByafParser.formatExampleMessages(args[0]);',
    '    if (method === "formatAlternateGreetings") return parser.formatAlternateGreetings(args[0]);',
    '    if (method === "convertCharacterBook") return parser.convertCharacterBook(args[0]) ?? null;',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(method, args) {
    const expected = await runCase()({ body: { method, args } });
    cases.push({ id: `${method}-${cases.length + 1}`, args: { body: { method, args } }, expected });
}

await add('replaceMacros', ['#{user}: #{character}: {character} {user}']);
await add('replaceMacros', ['UPPER #{USER}: #{CHARACTER}: {CHARACTER} {USER}']);
await add('replaceMacros', ['{characteristic} {userland} #{user}x']);
await add('replaceMacros', [null]);
await add('replaceMacros', ['']);
await add('formatExampleMessages', [
    [{ text: '你好 #{user}' }, { text: '回复 {character}' }, { text: '' }, { text: '   ' }, { text: '正常' }],
]);
await add('formatExampleMessages', [[]]);
await add('formatExampleMessages', [null]);
await add('formatAlternateGreetings', [
    [
        { firstMessages: [{ text: '开场A' }] },
        { firstMessages: [{ text: '开场B' }] },
        { firstMessages: [{ text: '开场A' }] },
        { firstMessages: [] },
        { firstMessages: [{ text: '开场C {user}' }] },
    ],
]);
await add('formatAlternateGreetings', [[]]);
await add('formatAlternateGreetings', [null]);
await add('convertCharacterBook', [
    [
        { key: '地点, 人物', value: '#{user}:在{character}旁' },
        { key: '事件', value: '' },
        { key: '', value: '无名' },
        null,
    ],
]);
await add('convertCharacterBook', [[]]);
await add('convertCharacterBook', [null]);

writeFileSync(outFile, JSON.stringify({ source: 'src/byaf.js 纯逻辑', cases }, null, 2));
console.log('byaf-macros:', cases.length, 'cases ->', outFile);
