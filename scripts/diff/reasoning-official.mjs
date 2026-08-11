#!/usr/bin/env node
// parseReasoningFromString / removeReasoningFromString / formatReasoning（reasoning.js）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd reasoning.js:1389/1410/1450 + utils.js trimSpaces。
// 打桩：power_user.reasoning/trim_spaces 由用例设置；substituteParams=恒等。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'reasoning.json');

const funcs = `
let power_user = { reasoning: { auto_parse: false, prefix: '<think>', suffix: '</think>', separator: '\\n' }, trim_spaces: false };
const substituteParams = (text) => String(text ?? '');

function escapeRegex(string) {
    return string.replace(/[/\\-\\\\^$*+?.()|[\\]{}]/g, '\\\\$&');
}

function trimSpaces(input) {
    if (!input || typeof input !== 'string') {
        return input;
    }
    return power_user.trim_spaces ? input.trim() : input;
}

function removeReasoningFromString(str) {
    if (!power_user.reasoning.auto_parse) {
        return str;
    }
    const parsedReasoning = parseReasoningFromString(str);
    return parsedReasoning?.content ?? str;
}

function parseReasoningFromString(str, { strict = true } = {}, template = null) {
    template = template ?? power_user.reasoning;
    if (!template.prefix || !template.suffix) {
        return null;
    }
    try {
        const regex = new RegExp(\`\${(strict ? '^\\\\s*?' : '')}\${escapeRegex(template.prefix)}(.*?)\${escapeRegex(template.suffix)}\`, 's');
        let didReplace = false;
        let reasoning = '';
        let content = String(str).replace(regex, (_match, captureGroup) => {
            didReplace = true;
            reasoning = captureGroup;
            return '';
        });
        if (didReplace) {
            reasoning = trimSpaces(reasoning);
            content = trimSpaces(content);
        }
        return { reasoning, content };
    } catch (error) {
        return null;
    }
}

function formatReasoning(reasoning, content, template = null) {
    template = template ?? power_user.reasoning;
    if (!reasoning || !template.prefix || !template.suffix) {
        return { formatted: content, contentOnly: content };
    }
    const prefix = substituteParams(template.prefix || '');
    const suffix = substituteParams(template.suffix || '');
    const separator = substituteParams(template.separator || '');
    const formatted = \`\${prefix}\${reasoning}\${suffix}\${separator}\${content}\`;
    return { formatted, contentOnly: content };
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    power_user.reasoning = {',
    '        auto_parse: b.autoParse ?? false,',
    '        prefix: b.prefix ?? "<think>",',
    '        suffix: b.suffix ?? "</think>",',
    '        separator: b.separator ?? "\\n",',
    '    };',
    '    power_user.trim_spaces = b.trimSpaces ?? false;',
    '    if (b.method === "remove") return removeReasoningFromString(b.text);',
    '    if (b.method === "parse") return parseReasoningFromString(b.text, { strict: b.strict ?? true });',
    '    if (b.method === "format") return formatReasoning(b.reasoning, b.content);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('remove-disabled', { method: 'remove', text: 'x<think>r</think>y', autoParse: false });
await add('remove-enabled-match', { method: 'remove', text: 'x<think>r</think>y', autoParse: true });
await add('remove-enabled-no-match', { method: 'remove', text: 'no reasoning', autoParse: true });
await add('parse-strict-match', { method: 'parse', text: '<think>r</think>content', prefix: '<think>', suffix: '</think>' });
await add('parse-strict-leading-space', { method: 'parse', text: '  <think>r</think>content', prefix: '<think>', suffix: '</think>' });
await add('parse-non-strict-middle', { method: 'parse', text: 'lead <think>r</think> tail', strict: false, prefix: '<think>', suffix: '</think>' });
await add('parse-strict-middle-fails', { method: 'parse', text: 'lead <think>r</think> tail', strict: true, prefix: '<think>', suffix: '</think>' });
await add('parse-missing-template', { method: 'parse', text: 'x', prefix: '', suffix: '' });
await add('parse-trim-spaces', { method: 'parse', text: '<think>  r  </think>  content  ', trimSpaces: true, prefix: '<think>', suffix: '</think>' });
await add('parse-no-trim-spaces', { method: 'parse', text: '<think>  r  </think>  content  ', trimSpaces: false, prefix: '<think>', suffix: '</think>' });
await add('format-basic', { method: 'format', reasoning: 'r', content: 'c', prefix: '<think>', suffix: '</think>', separator: '\n' });
await add('format-empty-reasoning', { method: 'format', reasoning: '', content: 'c', prefix: '<think>', suffix: '</think>' });
await add('format-missing-prefix', { method: 'format', reasoning: 'r', content: 'c', prefix: '', suffix: '</think>' });

writeFileSync(outFile, JSON.stringify({ source: 'parseReasoningFromString/removeReasoningFromString/formatReasoning', cases }, null, 2));
console.log('reasoning:', cases.length, 'cases ->', outFile);
