#!/usr/bin/env node
// BYAF getCharacterCard（src/byaf.js）→ JSON fixture。
// 方法体 + 依赖的 replaceMacros/formatExampleMessages/formatAlternateGreetings/convertCharacterBook 逐字提取。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'byaf-card.json');
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

const methods = [
    extractMethod('static replaceMacros(str)', 'replaceMacros'),
    extractMethod('static formatExampleMessages(examples)', 'formatExampleMessages'),
    extractMethod('formatAlternateGreetings(scenarios)', 'formatAlternateGreetings'),
    extractMethod('convertCharacterBook(items)', 'convertCharacterBook'),
    extractMethod('getCharacterCard(manifest, character, scenarios)', 'getCharacterCard'),
];

const runCase = new Function([
    'class ByafParser {',
    ...methods,
    '}',
    'return async (request) => {',
    "    Date.prototype.toISOString = () => request.body.fixedISO ?? '2026-08-08T00:00:00.000Z';",
    '    return new ByafParser().getCharacterCard(',
    '        request.body.manifest ?? null,',
    '        request.body.character ?? null,',
    '        request.body.scenarios ?? [],',
    '    );',
    '};',
].join('\n'));

const cases = [];
async function add(id, manifest, character, scenarios) {
    const expected = await runCase()({ body: { manifest, character, scenarios, fixedISO: '2026-08-08T00:00:00.000Z' } });
    cases.push({ id, args: { body: { manifest, character, scenarios } }, expected });
}

await add('full', {
    author: { name: '作者', backyardURL: 'https://by' },
}, {
    name: '角色A',
    displayName: '显示名',
    persona: '人设#{user}',
    isNSFW: true,
    loreItems: [{ key: '地点', value: '内容{character}' }],
}, [
    { narrative: '故事#{user}', firstMessages: [{ text: '开场' }], exampleMessages: [{ text: '示例' }], formattingInstructions: '指令{character}' },
    { narrative: '备选', firstMessages: [{ text: '备选开场' }] },
]);

await add('string-nsfw', {
    author: {},
}, {
    name: 'N',
    isNSFW: 'false',
}, [
    { narrative: 'S' },
]);

await add('empty', {
    author: {},
}, {
    name: '',
}, []);

await add('nulls', null, null, null);

writeFileSync(outFile, JSON.stringify({ source: 'src/byaf.js getCharacterCard', cases }, null, 2));
console.log('byaf-card:', cases.length, 'cases ->', outFile);
