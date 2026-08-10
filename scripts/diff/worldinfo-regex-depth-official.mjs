#!/usr/bin/env node
// 官方 world-info.js BUILDING PROMPT 的 regexDepth 计算（逐字提取）→ JSON fixture。
// 打桩：world_info_position.atDepth=4、DEFAULT_DEPTH=4、getRegexedString 由 App 侧差分覆盖（regex-pipeline）。
// 本组只锁“哪个条目带什么深度进正则”。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'worldinfo-regex-depth.json');

const src = readFileSync(join(officialRef, 'public', 'scripts', 'world-info.js'), 'utf8');
const LINE = 'const regexDepth = entry.position === world_info_position.atDepth ? (entry.depth ?? DEFAULT_DEPTH) : null;';
if (!src.includes(LINE)) {
    throw new Error('official regexDepth line not found (官方源码已变？)');
}

const stub = `
const DEFAULT_DEPTH = 4;
const world_info_position = { atDepth: 4 };
const entry = { position: request.body.position, depth: request.body.depth ?? null };
${LINE}
`;

const runCase = new Function('request', [stub, 'return regexDepth;'].join('\n'));

const cases = [];
async function add(id, position, depth) {
    const expected = await runCase({ body: { position, depth } });
    cases.push({ id, args: { body: { position, depth } }, expected });
}

for (let position = 0; position <= 7; position++) {
    await add(`pos-${position}-depth-null`, position, null);
    await add(`pos-${position}-depth-2`, position, 2);
    await add(`pos-${position}-depth-0`, position, 0);
    await add(`pos-${position}-depth-neg1`, position, -1);
    await add(`pos-${position}-depth-100`, position, 100);
}

writeFileSync(outFile, JSON.stringify({ source: 'world-info.js BUILDING PROMPT regexDepth', cases }, null, 2));
console.log('worldinfo-regex-depth:', cases.length, 'cases ->', outFile);
