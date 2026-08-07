#!/usr/bin/env node
// 提示词工具（script.js collapseNewlines/parseMesExamples）→ JSON fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'prompt-utils.json');

const funcs = `
let power_user = { context: { example_separator: '' } };
let main_api = 'openai';
const substituteParams = (text) => String(text ?? '');

function collapseNewlines(x) {
    return x.replaceAll(/\\n+/g, '\\n');
}

function parseMesExamples(examplesStr, isInstruct) {
    if (!examplesStr || examplesStr.length === 0 || examplesStr === '<START>') {
        return [];
    }
    if (!examplesStr.startsWith('<START>')) {
        examplesStr = '<START>\\n' + examplesStr.trim();
    }
    const exampleSeparator = power_user.context.example_separator ? \`\${substituteParams(power_user.context.example_separator)}\\n\` : '';
    const blockHeading = (main_api === 'openai' || isInstruct) ? '<START>\\n' : exampleSeparator;
    const splitExamples = examplesStr.split(/<START>/gi).slice(1).map(block => \`\${blockHeading}\${block.trim()}\\n\`);
    return splitExamples;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    power_user = request.body.power_user ?? { context: { example_separator: "" } };',
    '    main_api = request.body.main_api ?? "openai";',
    '    if (request.body.method === "collapse") return collapseNewlines(request.body.text);',
    '    if (request.body.method === "parseExamples") return parseMesExamples(request.body.text, request.body.isInstruct ?? false);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('collapse-basic', { method: 'collapse', text: 'a\n\n\nb\nc' });
await add('collapse-empty', { method: 'collapse', text: '' });
await add('collapse-mixed', { method: 'collapse', text: 'a\r\n\r\nb' });
await add('examples-empty', { method: 'parseExamples', text: '' });
await add('examples-start-only', { method: 'parseExamples', text: '<START>' });
await add('examples-plain', { method: 'parseExamples', text: 'hello\nworld' });
await add('examples-multi', { method: 'parseExamples', text: '<START>first\n<START>second' });
await add('examples-separator', {
    method: 'parseExamples', text: '<START>first\n<START>second',
    power_user: { context: { example_separator: '=== Example ===' } }, main_api: 'kobold',
});
await add('examples-instruct', {
    method: 'parseExamples', text: '<START>first\n<START>second',
    power_user: { context: { example_separator: '=== Example ===' } }, main_api: 'kobold', isInstruct: true,
});

writeFileSync(outFile, JSON.stringify({ source: 'script.js prompt utils', cases }, null, 2));
console.log('prompt-utils:', cases.length, 'cases ->', outFile);
