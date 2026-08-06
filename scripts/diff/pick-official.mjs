#!/usr/bin/env node
// 官方 {{pick}} 确定性基准生成器。
// 基准公式（官方 MacroEngine.e2e.js + pick 宏实现）：
//   combinedSeed = [chatIdHash, contentHash, offset, rerollSeed].filter(v => v !== null).join('-')
//   finalSeed = getStringHash(combinedSeed); rng = seedrandom(String(finalSeed))
//   randomIndex = floor(rng() * list.length)

import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'pick.json');

const require = createRequire(import.meta.url);
const seedrandom = require('./vendor/seedrandom-3.0.5.js');

// 官方 utils.js getStringHash（xmur3 风格 64 位）
function getStringHash(str, seed = 0) {
    if (typeof str !== 'string') return 0;
    let h1 = 0xdeadbeef ^ seed;
    let h2 = 0x41c6ce57 ^ seed;
    for (let i = 0; i < str.length; i++) {
        const ch = str.charCodeAt(i);
        h1 = Math.imul(h1 ^ ch, 2654435761);
        h2 = Math.imul(h2 ^ ch, 1597334677);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
    h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
    return 4294967296 * (2097151 & h2) + (h1 >>> 0);
}

const CHAT_ID_HASH = 123456;

function computeOutput(input) {
    const contentHash = getStringHash(input);
    const out = input.replace(/\{\{pick::([^}]+)\}\}/g, (raw, listStr, offset) => {
        const list = listStr.split('::');
        const combined = [CHAT_ID_HASH, contentHash, offset].join('-');
        const finalSeed = getStringHash(combined);
        const rng = seedrandom(String(finalSeed));
        const randomIndex = Math.floor(rng() * list.length);
        return list[randomIndex];
    });
    return out;
}

const cases = [
    { id: 'two_picks_different_offsets', input: 'Choices: {{pick::red::green::blue}}, {{pick::red::green::blue}}.' },
    { id: 'chinese_pick', input: '选择：{{pick::甲::乙::丙}}' },
    { id: 'two_options', input: '{{pick::a::b}}' },
    { id: 'mixed_pick_lists', input: '{{pick::x::y::z}} {{pick::1::2::3}}' },
    { id: 'single_option', input: '{{pick::only}}' },
];

const results = cases.map((c) => ({
    id: c.id,
    chatIdHash: CHAT_ID_HASH,
    input: c.input,
    expected: computeOutput(c.input),
}));

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref seedrandom@3.0.5', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
console.log(results.map((r) => `${r.id}: ${r.expected}`).join('\n'));
