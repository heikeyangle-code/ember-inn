#!/usr/bin/env node
// script.js Generate coreChat.map 的 append_title 标题追加纯逻辑 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd script.js:4448-4462。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'append-title.json');

const funcs = `
function appendTitles(mes, extra) {
    const titles = [];
    if (extra?.append_title && extra?.title) {
        titles.push(extra.title);
    }
    if (Array.isArray(extra?.media)) {
        for (const mediaItem of extra.media) {
            if (mediaItem?.title && mediaItem?.append_title) {
                titles.push(mediaItem.title);
            }
        }
    }
    if (titles.length > 0) {
        return \`\${mes}\\n\\n\${titles.join('\\n\\n')}\`;
    }
    return mes;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    return appendTitles(b.mes, b.extra);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('no-titles', { mes: 'hello', extra: {} });
await add('message-title', { mes: 'hello', extra: { append_title: true, title: '这是猫' } });
await add('media-title', { mes: 'hello', extra: { media: [{ title: '图1', append_title: true }, { title: '图2' }] } });
await add('combined', { mes: 'hello', extra: { append_title: true, title: '消息标题', media: [{ title: '图1', append_title: true }] } });
await add('no-append-flag', { mes: 'hello', extra: { title: '不追加' } });

writeFileSync(outFile, JSON.stringify({ source: 'append_title titles', cases }, null, 2));
console.log('append-title:', cases.length, 'cases ->', outFile);
