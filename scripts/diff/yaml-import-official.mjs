#!/usr/bin/env node
// YAML 角色卡导入（characters.js importFromYaml）→ JSON fixture。
// 逐字提取 importFromYaml + convertToV2 + charaFormatData；fs/写盘/文件名/时间 打桩，
// 输出最终 char JSON（写 PNG 前）。依赖 vendor/yaml + vendor/sanitize-filename（npm 已装）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'yaml-import.json');
const require = createRequire(import.meta.url);
const yaml = require('./vendor/node_modules/yaml/dist/index.js');
const sanitize = require('./vendor/node_modules/sanitize-filename/index.js');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'characters.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
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

function extractFunction(name, sig = null) {
    const pattern = sig ? `${name}(${sig})` : `${name}(`;
    let start = src.indexOf(`function ${name}(`);
    if (start < 0) throw new Error(`not found: ${name}`);
    // 若为 async function，把 async 前缀一并提取
    const asyncIdx = src.lastIndexOf('async ', start);
    if (asyncIdx >= 0 && src.slice(asyncIdx, start).trim() === 'async') {
        start = asyncIdx;
    }
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

const fsStub = [
    'const fs = {',
    '    readFileSync: () => request.body.yaml ?? \'\',',
    '    unlinkSync: () => {},',
    '};',
].join('\n');

const lodashStub = [
    'const _ = {',
    '    isUndefined: v => v === undefined,',
    '    unset: (obj, path) => { const p = String(path).split(\'.\'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur == null) return; cur = cur[p[i]]; } if (cur != null) delete cur[p[p.length - 1]]; },',
    '    get: (obj, path) => { const p = String(path).split(\'.\'); let cur = obj; for (const k of p) { if (cur == null) return undefined; cur = cur[k]; } return cur; },',
    '    set: (obj, path, value) => { const p = String(path).split(\'.\'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur[p[i]] == null || typeof cur[p[i]] !== \'object\') cur[p[i]] = {}; cur = cur[p[i]]; } cur[p[p.length - 1]] = value; },',
    '    forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); },',
    '};',
].join('\n');

const miscStub = [
    'const humanizedDateTime = () => request.body.fixedTime ?? \'2026-08-08@00h00m00s000ms\';',
    'const tryParse = (s) => { try { return JSON.parse(s); } catch { return undefined; } };',
    'const readWorldInfoFile = () => null;',
    'const deepMerge = (a, b) => { const out = structuredClone(a ?? {}); for (const [k, v] of Object.entries(b ?? {})) { if (v && typeof v === \'object\' && !Array.isArray(v) && out[k] && typeof out[k] === \'object\') { out[k] = deepMerge(out[k], v); } else { out[k] = v; } } return out; };',
    'const getPngName = (name) => name;',
    'const writeCharacterData = async (avatarPath, data) => { request.body.resultChar = JSON.parse(data); return true; };',
    'const DEFAULT_AVATAR_PATH = \'avatar\';',
    'const console = { info: () => {}, warn: () => {}, error: () => {} };',
    'Date.prototype.toISOString = () => request.body.fixedISO ?? \'2026-08-08T00:00:00.000Z\';',
].join('\n');

const fn = [
    extractFunction('charaFormatData'),
    extractFunction('convertToV2'),
    extractFunction('importFromYaml'),
].join('\n');

const runCase = new Function('request', 'yaml', 'sanitize', [
    fsStub, lodashStub, miscStub, fn,
    'return (async () => {',
    '    await importFromYaml(\'upload.yml\', { request: { user: { directories: {} } }, response: {} });',
    '    return request.body.resultChar;',
    '})();',
].join('\n'));

const cases = [];
async function add(id, yamlText, extra = {}) {
    const body = { yaml: yamlText, ...extra };
    const result = await runCase({ body }, yaml, sanitize);
    cases.push({ id, args: { body: { yaml: yamlText, ...extra } }, expected: result });
}

await add('minimal', 'name: 测试角色\n');
await add('full', [
    'name: 完整/角色:名',
    'context: 一段描述',
    'greeting: 你好呀',
    'first_mes: 备用开场',
    'scenario: 场景',
    'personality: 性格',
    'mes_example: 示例',
    'creator_notes: 备注',
    'tags: a, b',
    'depth_prompt_prompt: 深度提示',
    'depth_prompt_depth: 6',
    'depth_prompt_role: assistant',
].join('\n'));
await add('creators-and-fav', [
    'name: Alice',
    'context: 背景',
    'greeting: Hi',
    'creator: 作者',
    'character_version: 1.0',
    'fav: true',
    'talkativeness: 0.8',
].join('\n'));


await add('trailing-dots', 'name: Alice...\n');
await add('multiline-context', 'name: 多行\ncontext: |\n  第一行\n  第二行\n');
writeFileSync(outFile, JSON.stringify({ source: 'characters.js importFromYaml', cases }, null, 2));
console.log('yaml-import:', cases.length, 'cases ->', outFile);
