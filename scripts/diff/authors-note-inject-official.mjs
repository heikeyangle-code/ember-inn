#!/usr/bin/env node
// authors-note.js 注入判定纯逻辑 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd authors-note.js:333-362。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'authors-note-inject.json');

const funcs = `
function shouldInject(lastUserMessageNumber, interval) {
    let lastMessageNumber = lastUserMessageNumber;
    if (interval === 1) {
        lastMessageNumber = 1;
    }
    if (lastMessageNumber <= 0 || interval <= 0) {
        return false;
    }
    const messagesTillInsertion = lastMessageNumber >= interval
        ? (lastMessageNumber % interval)
        : (interval - lastMessageNumber);
    return messagesTillInsertion == 0;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    return shouldInject(b.userMessages, b.interval);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('interval-one', { userMessages: 0, interval: 1 });
await add('interval-one-many', { userMessages: 5, interval: 1 });
await add('zero-user', { userMessages: 0, interval: 4 });
await add('below-interval', { userMessages: 3, interval: 4 });
await add('equal-interval', { userMessages: 4, interval: 4 });
await add('multiple-interval', { userMessages: 8, interval: 4 });
await add('remainder', { userMessages: 6, interval: 4 });
await add('zero-interval', { userMessages: 2, interval: 0 });

writeFileSync(outFile, JSON.stringify({ source: 'authors-note shouldInject', cases }, null, 2));
console.log('authors-note-inject:', cases.length, 'cases ->', outFile);
