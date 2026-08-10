#!/usr/bin/env node
// 官方 PromptReasoning.addToMessage（reasoning.js）→ JSON fixture。
// 打桩：power_user.reasoning 由 request.body.settings 注入；substituteParams 恒等（宏替换由 App 侧负责）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'prompt-reasoning.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'reasoning.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0;
    let inString = null;
    let inRegex = false;
    let inLineComment = false;
    let inBlockComment = false;
    let prevSignificant = '';
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
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; prevSignificant = ch; continue; }
        if (ch === '/' && !/[A-Za-z0-9_)\\]}"']/.test(prevSignificant)) { inRegex = true; continue; }
        if (/\s/.test(ch)) continue;
        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) return i;
        }
        prevSignificant = ch;
    }
    throw new Error('unbalanced body');
}

const start = src.indexOf('export class PromptReasoning');
if (start < 0) throw new Error('class not found');
const brace = src.indexOf('{', start);
const klass = src.slice(start, scanBody(src, brace) + 1).replace(/^export /, '');

const stub = `
const power_user = { reasoning: request.body.settings };
function substituteParams(text, options = {}) {
    return text == null ? '' : String(text);
}
`;

const fn = stub + '\n' + klass + '\n';
const runCase = new Function('request', [
    fn,
    'return (() => {',
    '    const pr = new PromptReasoning();',
    '    const b = request.body;',
    '    const first = pr.addToMessage(b.content, b.reasoning, b.isPrefix, b.duration ?? null);',
    '    const second = b.secondContent === undefined',
    '        ? null',
    '        : pr.addToMessage(b.secondContent, b.secondReasoning, b.secondIsPrefix, b.secondDuration ?? null);',
    '    return { first, second, counter: pr.counter, prefixLength: pr.prefixLength, prefixIncomplete: pr.prefixIncomplete };',
    '})();',
].join('\n'));

const settings = { add_to_prompts: false, max_additions: 1, prefix: '<think>', suffix: '</think>', separator: '\n' };
const enabled = { add_to_prompts: true, max_additions: 1, prefix: '<think>', suffix: '</think>', separator: '\n' };

const cases = [];
async function add(id, body) {
    const expected = await runCase({ body });
    cases.push({ id, args: { body }, expected });
}

await add('disabled-nonprefix', { settings, content: '正文', reasoning: '思考', isPrefix: false, duration: null });
await add('disabled-prefix-still-injects', { settings, content: '正文', reasoning: '思考', isPrefix: true, duration: 12 });
await add('enabled-content', { settings: enabled, content: '正文', reasoning: '思考', isPrefix: false, duration: null });
await add('enabled-prefix-only', { settings: enabled, content: '', reasoning: '思考', isPrefix: true, duration: 7 });
await add('max-additions-limit', { settings: enabled, content: '一', reasoning: 'r1', isPrefix: false, duration: null, secondContent: '二', secondReasoning: 'r2', secondIsPrefix: false });
await add('placeholder-skipped', { settings: enabled, content: '正文', reasoning: '\u200B', isPrefix: false, duration: null });
await add('empty-reasoning-skipped', { settings: enabled, content: '正文', reasoning: '', isPrefix: false, duration: null });

writeFileSync(outFile, JSON.stringify({ source: 'reasoning.js PromptReasoning.addToMessage', cases }, null, 2));
console.log('prompt-reasoning:', cases.length, 'cases ->', outFile);
