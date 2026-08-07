#!/usr/bin/env node
// 世界书编辑器排序 sortWorldInfoEntries → JSON fixture 生成器。
// 从 world-info.js 逐字提取函数；DOM(option.data) 与 search 评分(worldInfoFilter) 桩掉，
// 只测 custom/priority/default/length 纯排序语义（search 依赖 UI 搜索评分，App 层）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'editor-sort.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'world-info.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0;
    let inString = null;
    let inRegex = false;
    let inLineComment = false;
    let inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) {
            if (ch === '\n') inLineComment = false;
            continue;
        }
        if (inBlockComment) {
            if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; }
            continue;
        }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '/') {
            let prev = source[i - 1];
            let next = source[i + 1];
            if (!/[\w\s)]/.test(prev) && !/[\w\s(]/.test(next)) { inRegex = true; continue; }
        }
        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) return i;
        }
    }
    throw new Error(`unbalanced body: start=${bodyStart}`);
}

function extractFunction(name) {
    const start = src.indexOf(`export function ${name}`);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = src.indexOf('(', start);
    let depth = 0;
    let bodyStart = -1;
    let inString = null;
    for (let i = parenStart; i < src.length; i++) {
        const ch = src[i];
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') {
            depth--;
            if (depth === 0) {
                let j = i + 1;
                while (j < src.length && /\s/.test(src[j])) j++;
                if (src[j] === '{') bodyStart = j;
                break;
            }
        }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return src.slice(start, scanBody(src, bodyStart) + 1);
}

const fnSource = extractFunction('sortWorldInfoEntries');

// 桩：DOM 下拉（customSort 给全时不触发）与 search 评分（不生成 search 用例）
const stub = `
globalThis.$ = () => ({ find: () => ({ data: () => undefined }) });
`;
const runnable = stub + '\n' + fnSource + '\n' + `
export const cases = [];` + '\n' + `
function makeCase(id, entries, customSort) {
    const input = entries.map(e => ({ ...e }));
    const output = sortWorldInfoEntries(input, { customSort });
    cases.push({ id, args: { entries, customSort }, expected: output.map(e => e.uid) });
}

const base = [
    { uid: 1, order: 5, displayIndex: 2, constant: false, disable: false, name: 'b', content: 'x' },
    { uid: 2, order: 5, displayIndex: 2, constant: false, disable: false, name: 'a', content: 'y' },
    { uid: 3, order: 9, displayIndex: 1, constant: false, disable: false, name: 'c', content: 'z' },
    { uid: 4, order: 1, displayIndex: 4, constant: true, disable: false, name: 'd', content: 'w' },
    { uid: 5, order: 2, displayIndex: 3, constant: false, disable: true, name: 'e', content: 'v' },
];
makeCase('custom-basic', base, { sortField: 'uid', sortOrder: 'asc', sortRule: 'custom' });
makeCase('priority-basic', base, { sortField: 'uid', sortOrder: 'asc', sortRule: 'priority' });
makeCase('default-order-asc', base, { sortField: 'order', sortOrder: 'asc', sortRule: 'default' });
makeCase('default-order-desc', base, { sortField: 'order', sortOrder: 'desc', sortRule: 'default' });
makeCase('default-uid-asc', base, { sortField: 'uid', sortOrder: 'asc', sortRule: 'default' });
makeCase('length-desc', [
    { uid: 1, order: 1, name: 'abc', content: '' },
    { uid: 2, order: 2, name: 'abcdef', content: '' },
    { uid: 3, order: 3, name: 'a', content: '' },
], { sortField: 'name', sortOrder: 'desc', sortRule: 'length' });
`

const code = runnable;
const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod2 = await import(dataUrl);

writeFileSync(outFile, JSON.stringify({ source: 'world-info.js sortWorldInfoEntries', cases: mod2.cases }, null, 2));
console.log(`editor-sort: ${mod2.cases.length} cases -> ${outFile}`);
