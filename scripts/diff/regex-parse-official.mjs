#!/usr/bin/env node
// 世界书正则解析 parseRegexFromString（world-info.js）→ JSON fixture。
// 逐字提取；输出 RegExp 的 source/flags（Kotlin Regex 无法原样序列化对象）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'regex-parse.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'world-info.js'), 'utf8');

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

const start = src.indexOf('export function parseRegexFromString');
if (start < 0) throw new Error('not found');
const parenStart = src.indexOf('(', start);
let depth = 0, bodyStart = -1, inString = null;
for (let i = parenStart; i < src.length; i++) {
    const ch = src[i];
    if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
    if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
    if (ch === '(') depth++;
    else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(src[j])) j++; if (src[j] === '{') bodyStart = j; break; } }
}
const fn = src.slice(start, scanBody(src, bodyStart) + 1);

const code = fn + '\n' + `
export const cases = [];
function add(id, input) {
    const regex = parseRegexFromString(input);
    cases.push({ id, args: { input }, expected: regex ? { source: regex.source, flags: regex.flags } : null });
}
add('basic-i', '/abc/i');
add('basic-m', '/a b/m');
add('basic-s', '/x.y/s');
add('combined', '/foo/ims');
add('no-flags', '/plain/');
add('invalid-no-close', '/abc');
add('unescaped-slash', '/a/b/i');
add('escaped-slash', '/a\\\\/b/i');
add('empty-pattern', '//');
`;

const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod = await import(dataUrl);
writeFileSync(outFile, JSON.stringify({ source: 'world-info.js parseRegexFromString', cases: mod.cases }, null, 2));
console.log(`regex-parse: ${mod.cases.length} cases -> ${outFile}`);
