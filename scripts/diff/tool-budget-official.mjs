#!/usr/bin/env node
// 工具 token 预分配（openai.js populateChatCompletion 片段）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'tool-budget.json');

const runCase = new Function([
    'return async (request) => {',
    '    const b = request.body;',
    '    if (!b.canPerform) return { reserve: 0, toolMessage: null };',
    '    const toolData = b.toolData ?? {};',
    '    const toolMessage = [{ role: "user", content: JSON.stringify(toolData) }];',
    '    const toolTokens = b.tokenCount ?? JSON.stringify(toolMessage).length;',
    '    return { reserve: toolTokens, toolMessage };',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('disabled', { canPerform: false });
await add('enabled-basic', { canPerform: true, toolData: { functions: [{ name: 'getWeather' }] }, tokenCount: 42 });
await add('enabled-empty', { canPerform: true, toolData: {}, tokenCount: 5 });
await add('enabled-custom-count', { canPerform: true, toolData: { functions: [] }, tokenCount: 99 });

writeFileSync(outFile, JSON.stringify({ source: 'openai.js populateChatCompletion ToolManager 片段', cases }, null, 2));
console.log('tool-budget:', cases.length, 'cases ->', outFile);
