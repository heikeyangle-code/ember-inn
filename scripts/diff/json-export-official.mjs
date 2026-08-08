#!/usr/bin/env node
// JSON 角色卡导出（characters.js getCharaCardV2 + unsetPrivateFields）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'json-export.json');
const require = createRequire(import.meta.url);
const sanitize = require('./vendor/node_modules/sanitize-filename/index.js');
const src = readFileSync(join(officialRef, 'src', 'endpoints', 'characters.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && source[i + 1] !== '/' && source[i + 1] !== '*' &&
            (i === 0 || !/[A-Za-z0-9_$)]/.test(source[i - 1]))) { inRegex = true; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractFunction(signature, name) {
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

const charaFormatData = extractFunction('function charaFormatData(data, directories)', 'charaFormatData');
const convertToV2 = extractFunction('function convertToV2(char, directories)', 'convertToV2');
const readFromV2 = extractFunction('function readFromV2(char)', 'readFromV2');
const unsetPrivateFields = extractFunction('function unsetPrivateFields(char)', 'unsetPrivateFields');
const getCharaCardV2 = extractFunction('function getCharaCardV2(jsonObject, directories, hoistDate = true)', 'getCharaCardV2');

const lodashStub = `const _ = { isUndefined: v => v === undefined, get: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (const k of p) { if (cur == null) return undefined; cur = cur[k]; } return cur; }, set: (obj, path, value) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur[p[i]] == null || typeof cur[p[i]] !== 'object') cur[p[i]] = {}; cur = cur[p[i]]; } cur[p[p.length - 1]] = value; }, unset: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur == null) return; cur = cur[p[i]]; } if (cur != null) delete cur[p[p.length - 1]]; }, forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); } };`;
const miscStub = `const console = { info: () => {}, warn: () => {}, error: () => {}, log: () => {} }; const humanizedDateTime = () => '2026-08-08@00h00m00s000ms'; const tryParse = (s) => { try { return JSON.parse(s); } catch { return undefined; } }; const readWorldInfoFile = () => null; const deepMerge = (a, b) => { const out = structuredClone(a ?? {}); for (const [k, v] of Object.entries(b ?? {})) { if (v && typeof v === 'object' && !Array.isArray(v) && out[k] && typeof out[k] === 'object') { out[k] = deepMerge(out[k], v); } else { out[k] = v; } } return out; }; Date.prototype.toISOString = () => '2026-08-08T00:00:00.000Z';`;

const runCase = new Function([
    miscStub, lodashStub, charaFormatData, convertToV2, readFromV2, unsetPrivateFields, getCharaCardV2,
    'return async (request) => {',
    '    const obj = getCharaCardV2(JSON.parse(request.body.json), {});',
    '    unsetPrivateFields(obj);',
    '    return JSON.stringify(obj, null, 4);',
    '};',
].join('\n'));

const cases = [];
async function add(id, jsonData) {
    const expected = await runCase()({ body: { json: typeof jsonData === 'string' ? jsonData : JSON.stringify(jsonData) } });
    cases.push({ id, args: { body: { json: typeof jsonData === 'string' ? jsonData : JSON.stringify(jsonData) } }, expected });
}

await add('v3', { spec: 'chara_card_v3', spec_version: '3.0', data: { name: 'N', description: 'D', extensions: { fav: true, talkativeness: 0.7 } }, chat: ['x'], json_data: 'y' });
await add('v2', { spec: 'chara_card_v2', name: '旧', description: 'd', talkativeness: 0.8, fav: true, chat: 'c' });
await add('v1', { name: '旧版', description: '描述', personality: '性格', first_mes: '你好', scenario: '场景', mes_example: '示例', creator_notes: '备注', tags: 'a,b', talkativeness: 0.6 });
await add('v1-no-date', { name: '无日期', description: 'd' });

writeFileSync(outFile, JSON.stringify({ source: 'characters.js getCharaCardV2+unsetPrivateFields', cases }, null, 2));
console.log('json-export:', cases.length, 'cases ->', outFile);
