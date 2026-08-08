#!/usr/bin/env node
// 导演备注（authors-note.js 默认值解析 + world-info.js ANWithWI）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'authors-note.json');

const runCase = new Function([
    'const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };',
    'return async (request) => {',
    '    const method = request.body.method;',
    '    if (method === "resolve") {',
    '        const DEFAULT_DEPTH = 4; const DEFAULT_POSITION = 1; const DEFAULT_INTERVAL = 1; const DEFAULT_ROLE = extension_prompt_roles.SYSTEM;',
    '        const defaults = request.body.defaults ?? {};',
    '        const meta = request.body.meta ?? {};',
    '        return {',
    '            prompt: meta.prompt ?? defaults.default ?? "",',
    '            interval: meta.interval ?? defaults.defaultInterval ?? DEFAULT_INTERVAL,',
    '            position: meta.position ?? defaults.defaultPosition ?? DEFAULT_POSITION,',
    '            depth: meta.depth ?? defaults.defaultDepth ?? DEFAULT_DEPTH,',
    '            role: meta.role ?? defaults.defaultRole ?? DEFAULT_ROLE,',
    '        };',
    '    }',
    '    if (method === "compose") {',
    '        const top = request.body.top ?? [];',
    '        const bottom = request.body.bottom ?? [];',
    '        const original = request.body.original ?? "";',
    '        return `${top.join("\\n")}\\n${original}\\n${bottom.join("\\n")}`.replace(/(^\\n)|(\\n$)/g, "");',
    '    }',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('resolve-defaults', { method: 'resolve', meta: {}, defaults: {} });
await add('resolve-overrides', {
    method: 'resolve',
    meta: { prompt: '备注', interval: 3, position: 2, depth: 7, role: 2 },
    defaults: { default: '默认', defaultInterval: 1, defaultPosition: 1, defaultDepth: 4, defaultRole: 0 },
});
await add('resolve-partial-defaults', {
    method: 'resolve',
    meta: { prompt: 'P' },
    defaults: { defaultInterval: 5, defaultPosition: 0, defaultDepth: 9, defaultRole: 1 },
});
await add('compose-empty', { method: 'compose', top: [], bottom: [], original: 'note' });
await add('compose-top', { method: 'compose', top: ['a', 'b'], bottom: [], original: 'note' });
await add('compose-both', { method: 'compose', top: ['a'], bottom: ['c', 'd'], original: 'note' });
await add('compose-empty-original', { method: 'compose', top: ['a'], bottom: ['c'], original: '' });

writeFileSync(outFile, JSON.stringify({ source: 'authors-note.js + world-info.js ANWithWI', cases }, null, 2));
console.log('authors-note:', cases.length, 'cases ->', outFile);
