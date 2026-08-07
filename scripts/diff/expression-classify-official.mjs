#!/usr/bin/env node
// expressions sampleClassifyText（+ utils.js trimToEndSentence/trimToStartSentence）→ JSON fixture。
// 函数体逐字提取；substituteParams/extension_settings 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'expression-classify.json');

const trimEnd = `function trimToEndSentence(input) {
    if (!input) {
        return '';
    }

    const isEmoji = x => /(\\p{Emoji_Presentation}|\\p{Extended_Pictographic})/gu.test(x);
    const punctuation = new Set(['.', '!', '?', '*', '\"', ')', '}', '\`', ']', '$', '。', '！', '？', '”', '）', '】', '’', '」', '_']);
    let last = -1;

    const characters = Array.from(input);
    for (let i = characters.length - 1; i >= 0; i--) {
        const char = characters[i];
        const emoji = isEmoji(char);

        if (punctuation.has(char) || emoji) {
            if (!emoji && i > 0 && /[\\s\\n]/.test(characters[i - 1])) {
                last = i - 1;
            } else {
                last = i;
            }
            break;
        }
    }

    if (last === -1) {
        return input.trimEnd();
    }

    return characters.slice(0, last + 1).join('').trimEnd();
}`;
const trimStart = `function trimToStartSentence(input) {
    if (!input) {
        return '';
    }

    let p1 = input.indexOf('.');
    let p2 = input.indexOf('!');
    let p3 = input.indexOf('?');
    let p4 = input.indexOf('\\n');
    let first = p1;
    let skip1 = false;
    if (p2 > 0 && p2 < first) { first = p2; }
    if (p3 > 0 && p3 < first) { first = p3; }
    if (p4 > 0 && p4 < first) { first = p4; skip1 = true; }
    if (first > 0) {
        if (skip1) {
            return input.substring(first + 1);
        } else {
            return input.substring(first + 2);
        }
    }
    return input;
}`;
const sampleClassifyText = `function sampleClassifyText(text) {
    if (!text) {
        return text;
    }

    let result = substituteParams(text).replace(/[*"]/g, '');

    if (extension_settings.expressions.api === EXPRESSION_API.llm) {
        return result.trim();
    }

    const SAMPLE_THRESHOLD = 500;
    const HALF_SAMPLE_THRESHOLD = SAMPLE_THRESHOLD / 2;

    if (text.length < SAMPLE_THRESHOLD) {
        result = trimToEndSentence(result);
    } else {
        result = trimToEndSentence(result.slice(0, HALF_SAMPLE_THRESHOLD)) + ' ' + trimToStartSentence(result.slice(-HALF_SAMPLE_THRESHOLD));
    }

    return result.trim();
}`;
const runCase = new Function([
    "const EXPRESSION_API = { local: 'local', llm: 'llm', extras: 'extras', webllm: 'webllm' };",
    'let extension_settings = { expressions: { api: \'local\' } };',
    "const substituteParams = (text) => String(text ?? '');",
    trimEnd,
    trimStart,
    sampleClassifyText,
    'return async (request) => {',
    '    extension_settings = request.body.settings ?? { expressions: { api: \'local\' } };',
    '    return sampleClassifyText(request.body.text);',
    '};',
].join('\n'));

const cases = [];
async function add(id, text, settings) {
    const expected = await runCase()({ body: { text, settings } });
    cases.push({ id, args: { body: { text, settings } }, expected });
}

await add('plain', 'Hello world! This is a test.', undefined);
await add('quotes-asterisks', '*"Hello"* world!', undefined);
await add('short-no-punct', 'just some words without punctuation', undefined);
await add('chinese', '你好！今天天气真好。', undefined);
await add('empty', '', undefined);
const long = 'A'.repeat(600);
await add('long', long, undefined);
const longWithSentence = 'First sentence. ' + 'Middle words without punctuation '.repeat(30) + 'Last sentence.';
await add('long-sentences', longWithSentence, undefined);
await add('llm', '*"raw"* text with quotes', { expressions: { api: 'llm' } });

writeFileSync(outFile, JSON.stringify({ source: 'expressions sampleClassifyText', cases }, null, 2));
console.log('expression-classify:', cases.length, 'cases ->', outFile);
