#!/usr/bin/env node
// 官方 src/endpoints/tokenizers.js getTokenizerModel → JSON fixture。
// 逐字提取函数体 + TEXT_COMPLETION_MODELS / sentencepieceTokenizers / webTokenizers 常量。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'tokenizer-model.json');
const src = readFileSync(join(officialRef, 'src', 'endpoints', 'tokenizers.js'), 'utf8');

function extractFn(name) {
    const start = src.indexOf(`function ${name}`);
    if (start < 0) throw new Error('not found: ' + name);
    const parenStart = src.indexOf('(', start);
    let depth = 0, bodyStart = -1;
    for (let i = parenStart; i < src.length; i++) {
        const ch = src[i];
        if (ch === '(') depth++;
        else if (ch === ')') {
            depth--;
            if (depth === 0) { bodyStart = i + 1; break; }
        }
    }
    while (bodyStart < src.length && /\s/.test(src[bodyStart])) bodyStart++;
    let d = 0, end = -1;
    for (let i = bodyStart; i < src.length; i++) {
        const ch = src[i];
        if (ch === '{') d++;
        else if (ch === '}') { d--; if (d === 0) { end = i + 1; break; } }
    }
    return src.slice(start, end);
}

function extractConst(name) {
    const re = new RegExp(`export const ${name} = \\[([^\\]]*)\\];`);
    const m = src.match(re);
    if (!m) throw new Error('const not found: ' + name);
    // eslint-disable-next-line no-eval
    return eval('[' + m[1] + ']');
}

const fn = extractFn('getTokenizerModel');
const textCompletion = extractConst('TEXT_COMPLETION_MODELS');
const sentencepiece = extractConst('sentencepieceTokenizers');
const web = extractConst('webTokenizers');

const stub = `
const TEXT_COMPLETION_MODELS = ${JSON.stringify(textCompletion)};
const sentencepieceTokenizers = ${JSON.stringify(sentencepiece)};
const webTokenizers = ${JSON.stringify(web)};
${fn}
`;

const cases = [
    'gpt-4o', 'chatgpt-4o-latest', 'gpt-4.1-mini', 'gpt-4.5-preview', 'gpt-4-32k', 'gpt-4-turbo',
    'gpt-3.5-turbo-0301', 'gpt-3.5-turbo', 'gpt-3.5-turbo-instruct', 'text-davinci-003',
    'o1', 'o1-preview', 'o1-mini-2024-09-12', 'o3-mini', 'o3', 'o4-mini', 'gpt-5', 'gpt-5.2',
    'claude-sonnet-4-5', 'claude-opus-4-1', 'llama-3.3-70b', 'llama3-8b', 'llama-2-7b',
    'mistral-large-latest', 'yi-large', 'deepseek-v4-flash', 'gemini-2.5-pro', 'gemma-3-27b',
    'learnlm-1.5', 'jamba-large', 'qwen2.5-72b', 'command-r-plus', 'command-a', 'nemo-12b',
    'random-model-xyz', '', 'gpt-3.5-turbo-16k',
];

const out = cases.map((m) => ({ model: m, key: eval(stub + 'getTokenizerModel(' + JSON.stringify(m) + ')') }));
writeFileSync(outFile, JSON.stringify({ source: 'sillytavern-ref tokenizers.js', cases: out }, null, 2) + '\n');
console.log('wrote', outFile, out.length, 'cases');
