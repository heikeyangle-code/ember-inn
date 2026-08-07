#!/usr/bin/env node
// 向量工具纯函数（utils.js splitRecursive/trimToEndSentence/trimToStartSentence）→ JSON fixture。
// 从官方 utils.js 逐字提取；trimToEndSentence 的 emoji 判定用官方 Unicode 属性正则（Node 原生支持）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'vector-utils.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'utils.js'), 'utf8');

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
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '/') {
            const next = source[i + 1];
            // 正则识别：前一非空白字符为 = > ( , : [ 时视为正则开始（如 `=> /re/`）
            let j = i - 1;
            while (j >= 0 && /\s/.test(source[j])) j--;
            const prevSig = source[j];
            if (prevSig === '=' || prevSig === '>' || prevSig === '(' || prevSig === ',' || prevSig === ':' || prevSig === '[') {
                inRegex = true;
                continue;
            }
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

const code = [
    extractFunction('splitRecursive'),
    extractFunction('trimToEndSentence'),
    extractFunction('trimToStartSentence'),
].join('\n\n') + '\n\n' + `
export const cases = [];

function add(id, fnName, fn, args) {
    cases.push({ id, fn: fnName, args, expected: fn(...args) });
}

add('split-3', 'splitRecursive', splitRecursive, ['Hello, world!', 3]);
add('split-5-multiline', 'splitRecursive', splitRecursive, ['a\\n\\nbb\\nccc dddd', 5]);
add('split-4-cjk', 'splitRecursive', splitRecursive, ['中文段落测试文本', 4]);
add('split-empty', 'splitRecursive', splitRecursive, ['', 3]);
add('split-zero-length', 'splitRecursive', splitRecursive, ['abc', 0]);
add('trim-end-basic', 'trimToEndSentence', trimToEndSentence, ['Hello, world! I am from']);
add('trim-end-cjk', 'trimToEndSentence', trimToEndSentence, ['你好，世界。继续后面的字']);
add('trim-end-emoji', 'trimToEndSentence', trimToEndSentence, ['前面的话😀后面还有字']);
add('trim-end-empty', 'trimToEndSentence', trimToEndSentence, ['']);
add('trim-end-no-punct', 'trimToEndSentence', trimToEndSentence, ['尾部没有标点']);
add('trim-start-basic', 'trimToStartSentence', trimToStartSentence, ['Hello. world']);
add('trim-start-newline', 'trimToStartSentence', trimToStartSentence, ['a!b?c\\nd']);
add('trim-start-empty', 'trimToStartSentence', trimToStartSentence, ['']);
add('trim-start-no-punct', 'trimToStartSentence', trimToStartSentence, ['无标点直接返回']);
`;

const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod = await import(dataUrl);
writeFileSync(outFile, JSON.stringify({ source: 'utils.js splitRecursive/trimToEndSentence/trimToStartSentence', cases: mod.cases }, null, 2));
console.log(`vector-utils: ${mod.cases.length} cases -> ${outFile}`);
