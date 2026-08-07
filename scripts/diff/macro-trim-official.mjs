#!/usr/bin/env node
// 作用域宏内容裁剪 trimScopedContent（MacroEngine.js）→ JSON fixture。
// 逐字提取官方方法；该方法无外部依赖，可直接在 Node 运行。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'macro-trim.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'macros', 'engine', 'MacroEngine.js'), 'utf8');

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

const start = src.indexOf('trimScopedContent(content, { trimIndent = true } = {}) {');
if (start < 0) throw new Error('trimScopedContent not found');
const brace = src.indexOf(') {', start);
if (brace < 0) throw new Error('body brace not found');
const bodyStart = brace + 2;
const raw = src.slice(start, scanBody(src, bodyStart) + 1);
const fn = 'function ' + raw;

const code = fn + '\n' + `
export const cases = [];
function add(id, content, options) {
    cases.push({ id, args: { content, options: options ?? {} }, expected: trimScopedContent(content, options ?? {}) });
}
add('plain', '  你好  ', undefined);
add('empty', '', undefined);
add('multiline-dedent', '\\n    行一\\n    行二\\n', undefined);
add('mixed-indent', '  a\\n    b\\n  c', undefined);
add('no-indent', 'a\\nb', undefined);
add('no-dedent', '  你好  ', { trimIndent: false });
add('blank-lines', '\\n\\n  x\\n\\n', undefined);
`;

const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod = await import(dataUrl);
writeFileSync(outFile, JSON.stringify({ source: 'MacroEngine.js trimScopedContent', cases: mod.cases }, null, 2));
console.log(`macro-trim: ${mod.cases.length} cases -> ${outFile}`);
