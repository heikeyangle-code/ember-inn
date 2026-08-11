#!/usr/bin/env node
// script.js generate “Add message example WI” + baseChatReplace + parseMesExamples 纯逻辑 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd script.js:4591-4604 / 3442 / 3298。
// 打桩登记：substituteParams 注入为 substitute 桩；power_user.collapse_newlines 由参数注入；
// example_separator 传入已宏替换后的文本（官方 substituteParams(separator)）；wi_anchor_position.before=0/after=1。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'em-examples.json');

const funcs = `
function collapseNewlines(text) { return text.replace(/\\n+/g, '\\n'); }
function parseMesExamples(examplesStr, isInstruct, exampleSeparator, mainApiIsOpenAi) {
    if (!examplesStr || examplesStr.length === 0 || examplesStr === '<START>') {
        return [];
    }
    if (!examplesStr.startsWith('<START>')) {
        examplesStr = '<START>\\n' + examplesStr.trim();
    }
    const separator = exampleSeparator ? exampleSeparator + '\\n' : '';
    const blockHeading = (mainApiIsOpenAi || isInstruct) ? '<START>\\n' : separator;
    const splitExamples = examplesStr.split(/<START>/gi).slice(1).map(block => blockHeading + block.trim() + '\\n');
    return splitExamples;
}
function baseChatReplace(value, substitute, collapse) {
    if (typeof value === 'string' && value.length > 0) {
        value = substitute(value);
        if (collapse) value = collapseNewlines(value);
        value = value.replace(/\\r/g, '');
    }
    return value;
}
function assembleWithWorldExamples(base, emEntries, substitute, collapse, isInstruct, exampleSeparator, mainApiIsOpenAi) {
    let mesExamplesArray = parseMesExamples(base, isInstruct, exampleSeparator, mainApiIsOpenAi);
    for (const example of emEntries) {
        const exampleMessage = example.content;
        if (exampleMessage.length === 0) continue;
        const formattedExample = baseChatReplace(exampleMessage, substitute, collapse);
        const cleanedExample = parseMesExamples(formattedExample, isInstruct, exampleSeparator, mainApiIsOpenAi);
        if (example.position === 0) {
            mesExamplesArray.unshift(...cleanedExample);
        } else {
            mesExamplesArray.push(...cleanedExample);
        }
    }
    return mesExamplesArray;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    const substitute = (x) => String(x).replaceAll("{{user}}", b.user ?? "User").replaceAll("{{char}}", b.char ?? "Char");',
    '    return assembleWithWorldExamples(b.base, b.emEntries, substitute, b.collapse, b.isInstruct, b.exampleSeparator, b.mainApiIsOpenAi);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('base-only', { base: '<START>\nUser: hi\nChar: hello', emEntries: [], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-before', { base: '<START>\nUser: hi\nChar: hello', emEntries: [{ position: 0, content: '<START>\nUser: em\nChar: em1' }], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-after', { base: '<START>\nUser: hi\nChar: hello', emEntries: [{ position: 1, content: '<START>\nUser: em\nChar: em2' }], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-before-after-multi', { base: '<START>\nUser: hi', emEntries: [{ position: 0, content: '<START>\nUser: A1\nChar: A2' }, { position: 1, content: '<START>\nUser: B1' }, { position: 0, content: '<START>\nUser: C1' }], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-macros-collapse', { base: '<START>\n{{user}}: hi', emEntries: [{ position: 1, content: '<START>\n{{user}}: macro\n\n\n\n{{char}}: x' }], collapse: true, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-empty-skipped', { base: '<START>\nUser: hi', emEntries: [{ position: 0, content: '' }, { position: 1, content: '<START>\nUser: ok' }], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-just-start', { base: '<START>\nUser: hi', emEntries: [{ position: 0, content: '<START>' }], collapse: false, isInstruct: false, exampleSeparator: '', mainApiIsOpenAi: true });
await add('em-instruct-separator', { base: 'plain base', emEntries: [{ position: 1, content: 'plain em' }], collapse: false, isInstruct: true, exampleSeparator: '[Example]', mainApiIsOpenAi: false });
await add('em-non-openai-separator', { base: 'plain base', emEntries: [{ position: 0, content: 'plain em' }], collapse: false, isInstruct: false, exampleSeparator: '[Example]', mainApiIsOpenAi: false });

writeFileSync(outFile, JSON.stringify({ source: 'script.js Generate WI message examples + baseChatReplace', cases }, null, 2));
console.log('em-examples:', cases.length, 'cases ->', outFile);
