#!/usr/bin/env node
// populateDialogueExamples（openai.js）→ JSON fixture。
// 逐字提取官方函数；Message/PromptManager/预算 打桩，输出重建后的集合结构。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'dialogue-examples-pop.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'openai.js'), 'utf8');

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

const start = src.indexOf('async function populateDialogueExamples(');
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

const stub = [
    'const prompts = {',
    '    has: (id) => id === \'dialogueExamples\',',
    '    index: () => 0,',
    '};',
    'const oai_settings = { new_example_chat_prompt: request.body.new_example_chat_prompt ?? \'\' };',
    'const substituteParams = (t) => String(t);',
    'const Message = {',
    '    async createAsync(role, content, identifier) { return { role, content: String(content ?? \'\'), identifier, name: null, tokens: String(content ?? \'\').length, async setName(n) { this.name = n; } }; },',
    '};',
    'class MsgCollection {',
    '    constructor(name) { this.name = name; this.msgs = []; }',
    '    add(m) { this.msgs.push(m); }',
    '}',
    'const MessageCollection = MsgCollection;',
    'const chatCompletion = {',
    '    collections: [],',
    '    _remaining: request.body.budget ?? 100000,',
    '    add(collection) { this.collections.push(collection); this._remaining -= collection.msgs.reduce((a, m) => a + m.tokens, 0); },',
    '    canAffordAll(ms) { const total = ms.reduce((a, m) => a + m.tokens, 0); return total <= this._remaining; },',
    '    insert(m, c) { const col = this.collections.find(x => x.name === c); col.msgs.push({ role: m.role, content: m.content, identifier: m.identifier ?? \'\', name: m.name ?? null }); this._remaining -= m.tokens; },',
    '};',
].join('\n');

const runCase = new Function('request', stub + '\n' + fn + '\n' + [
    "return (async () => {",
    "    await populateDialogueExamples(prompts, chatCompletion, request.body.messageExamples ?? []);",
    "    return chatCompletion.collections.map(c => ({ name: c.name, msgs: c.msgs }));",
    "})();",
].join('\n'));

const cases = [];
async function add(id, body) {
    const result = await runCase(structuredClone({ body }));
    cases.push({ id, args: { body }, expected: result });
}

const examples = [
    [{ name: 'User', content: '示例A' }, { name: 'Char', content: '示例B' }],
    [{ name: 'User', content: '示例C' }],
];
await add('basic', { new_example_chat_prompt: '【示例】', messageExamples: examples });
await add('empty-examples', { new_example_chat_prompt: '【示例】', messageExamples: [] });
await add('empty-dialogue', { new_example_chat_prompt: '【示例】', messageExamples: [[]] });
await add('budget-cuts-second-group', { new_example_chat_prompt: '【示例】', budget: 12, messageExamples: examples });

writeFileSync(outFile, JSON.stringify({ source: 'openai.js populateDialogueExamples', cases }, null, 2));
console.log('dialogue-examples-pop:', cases.length, 'cases ->', outFile);
