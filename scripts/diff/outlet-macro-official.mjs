#!/usr/bin/env node
// 官方 {{outlet::key}} 宏（core-macros.js）→ JSON fixture。
// 逐字提取 handler；打桩 extension_prompts 与 inject_ids.CUSTOM_WI_OUTLET。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'outlet-macro.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'macros', 'definitions', 'core-macros.js'), 'utf8');
const marker = "// Outlet macro: {{outlet::key}}";
const handlerStart = src.indexOf("handler: ({ unnamedArgs: [outlet] }) => {", src.indexOf(marker));
if (handlerStart < 0) throw new Error('outlet handler not found');

// 提取箭头函数体：handler 是对象属性，箭头体闭合是第一个 "}," 行
const arrowAt = src.indexOf('=> {', handlerStart);
if (arrowAt < 0) throw new Error('arrow body not found');
const bodyStart = src.indexOf('{', arrowAt);
const closeMatch = src.slice(bodyStart).match(/^\s*\},\s*$/m);
if (!closeMatch) throw new Error('handler close not found');
const end = bodyStart + closeMatch.index + closeMatch[0].length;
const handler = src.slice(handlerStart, end);

const stub = `
const extension_prompts = request.body.prompts;
const inject_ids = { CUSTOM_WI_OUTLET: (key) => 'customWIOutlet_' + key };
`;

const runCase = new Function('request', [
    stub,
    'const fn = ' + handler.replace(/^handler:\s*/, '').replace(/,\s*$/, ''),
    'return fn({ unnamedArgs: [request.body.key] });',
].join('\n'));

const cases = [];
async function add(id, key, prompts) {
    const expected = await runCase({ body: { key, prompts } });
    cases.push({ id, args: { body: { key, prompts } }, expected });
}

await add('exists', 'castle', { customWIOutlet_castle: { value: '王城情报' } });
await add('missing-key', 'dungeon', { customWIOutlet_castle: { value: '王城情报' } });
await add('empty-key', '', { customWIOutlet_: { value: 'x' } });
await add('null-value', 'castle', { customWIOutlet_castle: { value: null } });
await add('empty-value', 'castle', { customWIOutlet_castle: { value: '' } });

writeFileSync(outFile, JSON.stringify({ source: 'core-macros.js outlet macro', cases }, null, 2));
console.log('outlet-macro:', cases.length, 'cases ->', outFile);
