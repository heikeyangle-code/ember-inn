#!/usr/bin/env node
// JSON 角色卡导入（characters.js importFromJson）→ JSON fixture。
// 函数体逐字提取；fs/写盘/时间/sanitize/Risu 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'json-import.json');
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
const unsetPrivateFields = extractFunction('function unsetPrivateFields(char)', 'unsetPrivateFields');
const readFromV2 = extractFunction('function readFromV2(char)', 'readFromV2');
const importFromJson = extractFunction('async function importFromJson(uploadPath, { request }, preservedFileName)', 'importFromJson');

const lodashStub = `
const _ = {
    isUndefined: v => v === undefined,
    get: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (const k of p) { if (cur == null) return undefined; cur = cur[k]; } return cur; },
    set: (obj, path, value) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur[p[i]] == null || typeof cur[p[i]] !== 'object') cur[p[i]] = {}; cur = cur[p[i]]; } cur[p[p.length - 1]] = value; },
    unset: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur == null) return; cur = cur[p[i]]; } if (cur != null) delete cur[p[p.length - 1]]; },
    forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); },
};
`;

const miscStub = `
const console = { info: () => {}, warn: () => {}, error: () => {}, log: () => {}, trace: () => {} };
const fs = { readFileSync: () => request.body.json, unlinkSync: () => {} };
const fsPromises = { readFile: async () => ({}) };
const DEFAULT_AVATAR_PATH = 'avatar';
const humanizedDateTime = () => '2026-08-08@00h00m00s000ms';
const tryParse = (s) => { try { return JSON.parse(s); } catch { return undefined; } };
const readWorldInfoFile = () => null;
const deepMerge = (a, b) => { const out = structuredClone(a ?? {}); for (const [k, v] of Object.entries(b ?? {})) { if (v && typeof v === 'object' && !Array.isArray(v) && out[k] && typeof out[k] === 'object') { out[k] = deepMerge(out[k], v); } else { out[k] = v; } } return out; };
const getPngName = (name, directories) => name;
const writeCharacterData = async (avatarPath, data, fileName, request) => { request.body.resultChar = JSON.parse(data); request.body.resultAvatar = avatarPath; request.body.resultFileName = fileName; return true; };
const importRisuSprites = (directories, data) => { request.body.risuRun = true; };
const sanitizeSafeCharacterReplacements = () => '_';
Date.prototype.toISOString = () => request.body.fixedISO ?? '2026-08-08T00:00:00.000Z';
`;

const fn = [
    lodashStub,
    miscStub,
    charaFormatData,
    convertToV2,
    unsetPrivateFields,
    readFromV2,
    importFromJson,
].join('\n');

const runCase = new Function('request', 'sanitize', [
    fn,
    'return (async () => {',
    '    request.user = { directories: {} };',
    '    const fileName = await importFromJson(\'upload.json\', { request }, request.body.preservedFileName ?? null);',
    '    return { resultChar: request.body.resultChar, resultAvatar: request.body.resultAvatar, resultFileName: fileName, risuRun: request.body.risuRun ?? false };',
    '})();',
].join('\n'));

const cases = [];
async function add(id, jsonData, extra = {}) {
    const expected = await runCase({ body: { json: typeof jsonData === 'string' ? jsonData : JSON.stringify(jsonData), fixedISO: '2026-08-08T00:00:00.000Z', ...extra } }, sanitize);
    cases.push({ id, args: { body: { json: typeof jsonData === 'string' ? jsonData : JSON.stringify(jsonData), ...extra } }, expected });
}

await add('v3', {
    spec: 'chara_card_v3', spec_version: '3.0',
    data: { name: '测试/角色:名', description: '描述', extensions: { fav: true, talkativeness: 0.7 } },
    chat: ['旧'], json_data: 'x',
});
await add('v2', {
    spec: 'chara_card_v2', name: '旧卡', description: '描述', personality: '性格', scenario: '场景',
    first_mes: '你好', mes_example: '示例', talkativeness: 0.8, fav: true, chat: '会话',
});
await add('v1', {
    name: '旧版/角色', description: '旧描述', personality: '旧性格', first_mes: '开场', scenario: '旧场景',
    mes_example: '旧示例', creator_notes: 'Creator\'s notes go here.备注', talkativeness: 0.6, fav: true, tags: 'a, b',
});
await add('gradio', {
    char_name: '渐变角色', char_persona: '人设', char_greeting: '问候', example_dialogue: '示例对话',
    creator_notes: 'Creator\'s notes go here.', world_scenario: '场景', personality: '性格',
});
await add('preserved', {
    spec: 'chara_card_v3', data: { name: 'Preserved卡', description: 'd' },
}, { preservedFileName: 'keep.json' });

writeFileSync(outFile, JSON.stringify({ source: 'characters.js importFromJson', cases }, null, 2));
console.log('json-import:', cases.length, 'cases ->', outFile);
