#!/usr/bin/env node
// 角色卡 V3→V2 归一 readFromV2（src/endpoints/characters.js）→ JSON fixture。
// 逐字提取函数；lodash(_) / humanizedDateTime / console 打桩，固定时间保证可复现。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'char-v2.json');

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
        if (ch === '/') {
            let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--;
            const prevSig = source[j];
            if (prevSig === '=' || prevSig === '>' || prevSig === '(' || prevSig === ',' || prevSig === ':' || prevSig === '[') { inRegex = true; continue; }
        }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractFunction(name) {
    const start = src.indexOf(`function ${name}(`);
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

const lodashStub = `
const _ = {
    isUndefined: v => v === undefined,
    unset: (obj, path) => {
        const parts = String(path).split('.');
        let cur = obj;
        for (let i = 0; i < parts.length - 1; i++) { if (cur == null) return; cur = cur[parts[i]]; }
        if (cur != null) delete cur[parts[parts.length - 1]];
    },
    get: (obj, path) => {
        const parts = String(path).split('.');
        let cur = obj;
        for (const p of parts) { if (cur == null) return undefined; cur = cur[p]; }
        return cur;
    },
    forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); },
};
const humanizedDateTime = () => '2026-08-08@00h00m00s000ms';
const console = { warn: () => {} };
`;

const code = lodashStub + '\n' + extractFunction('readFromV2') + '\n' + `
export const cases = [];
function add(id, char) {
    const input = structuredClone(char);
    const output = readFromV2(input);
    cases.push({ id, args: { char }, expected: output });
}

add('v3-full', {
    name: 'OldName',
    chat: 'OldChat',
    json_data: '{"foreign":1}',
    data: {
        name: '新名字', description: '描述', personality: '性格', scenario: '场景',
        first_mes: '开场', mes_example: '示例', tags: ['t1', 't2'],
        extensions: { talkativeness: 0.7, fav: true },
    },
});
add('v3-no-ext', {
    name: 'A', chat: 'C',
    data: { name: 'B', description: 'D' },
});
add('v1-no-data', {
    name: 'Legacy', description: '旧卡', chat: 'C1',
});
add('with-json-data', {
    name: 'X', chat: 'C', json_data: '{"x":1}',
    data: { name: 'Y' },
});
add('no-chat', {
    name: 'N', json_data: '{}',
    data: { name: 'N2', description: 'D' },
});
`;

const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod = await import(dataUrl);
writeFileSync(outFile, JSON.stringify({ source: 'characters.js readFromV2', cases: mod.cases }, null, 2));
console.log(`char-v2: ${mod.cases.length} cases -> ${outFile}`);
