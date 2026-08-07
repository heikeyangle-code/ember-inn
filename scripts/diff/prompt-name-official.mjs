#!/usr/bin/env node
// PromptManager isValidName/sanitizeName（public/scripts/PromptManager.js）→ JSON fixture。
// 方法体逐字提取，无其它依赖。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'prompt-name.json');
const src = readFileSync(join(officialRef, 'public', 'scripts', 'PromptManager.js'), 'utf8');

const isValidName = `    isValidName(name) {
        const regex = /^[a-zA-Z0-9_]{1,64}$/;
        return regex.test(name);
    }`;
const sanitizeName = `    sanitizeName(name) {
        return name.replace(/[^a-zA-Z0-9_]/g, '_').substring(0, 64);
    }`;

const runCase = new Function([
    'class PromptManager {',
    isValidName,
    sanitizeName,
    '}',
    'return async (request) => {',
    '    const pm = new PromptManager();',
    '    const method = request.body.method;',
    '    const name = request.body.name;',
    '    if (method === "isValidName") return pm.isValidName(name);',
    '    if (method === "sanitizeName") return pm.sanitizeName(name);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const names = [
    'Alice', 'alice_01', '张三', 'a b', 'a/b:c', '!@#', '', 'a'.repeat(63), 'a'.repeat(64), 'a'.repeat(65),
    '名字_123', 'a-b', 'a.b', 'a,b',
];

const cases = [];
for (const name of names) {
    for (const method of ['isValidName', 'sanitizeName']) {
        const expected = await runCase()({ body: { method, name } });
        cases.push({ id: `${method}-${cases.length + 1}`, args: { body: { method, name } }, expected });
    }
}

writeFileSync(outFile, JSON.stringify({ source: 'public/scripts/PromptManager.js isValidName/sanitizeName', cases }, null, 2));
console.log('prompt-name:', cases.length, 'cases ->', outFile);
