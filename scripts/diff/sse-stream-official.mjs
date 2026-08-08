#!/usr/bin/env node
// SSE 流解析（sse-stream.js parseStreamData）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'sse-stream.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'sse-stream.js'), 'utf8');

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

const parseStreamData = extractFunction('async function* parseStreamData(json)', 'parseStreamData');

const runCase = new Function([
    "const NOT_PRIMARY = Symbol('not_primary_swipe');",
    parseStreamData,
    'return async (request) => {',
    '    const out = [];',
    '    try {',
    '        for await (const item of parseStreamData(request.body.json)) out.push(item);',
    '    } catch (e) {',
    '        return { error: String(e.message) };',
    '    }',
    '    return out;',
    '};',
].join('\n'));

const cases = [];
async function add(id, jsonData) {
    const expected = await runCase()({ body: { json: jsonData } });
    cases.push({ id, args: { body: { json: jsonData } }, expected });
}

await add('openai-delta', { choices: [{ index: 0, delta: { content: 'hi' } }] });
await add('openai-delta-null-content', { choices: [{ index: 0, delta: { content: null } }] });
await add('openai-delta-empty-content', { choices: [{ index: 0, delta: { content: '' } }] });
await add('openai-delta-null-delta', { choices: [{ index: 0, delta: null }] });
await add('openai-delta-missing-choices', { choices: [] });
await add('anthropic-text', { delta: { text: 'yo' } });
await add('anthropic-thinking', { delta: { thinking: 're' } });
await add('gemini-parts', { candidates: [{ index: 0, content: { parts: [{ text: 'ab' }, { text: 'cd' }] } }] });
await add('token', { token: 'ab' });
await add('content', { content: 'cd', object: 'completion' });
await add('choices-text', { choices: [{ index: 0, text: 'xy' }] });
await add('gemini-tool-call', { candidates: [{ index: 0, content: { parts: [{ functionCall: { name: 'x' } }] } }] });
await add('openai-delta-array-thinking', { choices: [{ index: 0, delta: { content: [{ thinking: [{ text: 're' }] }] } }] });
await add('openai-message-content', { choices: [{ index: 0, message: { content: 'hi' } }] });
await add('not-primary', { choices: [{ index: 1, delta: { content: 'x' } }] });

writeFileSync(outFile, JSON.stringify({ source: 'sse-stream.js parseStreamData', cases }, null, 2));
console.log('sse-stream:', cases.length, 'cases ->', outFile);
