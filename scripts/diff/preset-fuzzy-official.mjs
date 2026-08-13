#!/usr/bin/env node
// preset-manager.js presetCommandCallback → fixture（选择名逻辑；连接/等待副作用剥除）。
// 官方 1.18.0 release 8172dcd public/scripts/preset-manager.js:910-976：
// 1) exact：allPresets.find(p => p.toLowerCase().trim() === name.toLowerCase().trim())
// 2) fuzzy：new Fuse(allPresets)（fuse.js ^7.1.0，默认 options）→ fuse.search(name) → 取 [0].item
// 打桩：presetManager.selectPreset / waitForConnection / online_status 剥除（与选择名无关）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import Fuse from './vendor/node_modules/fuse.js/dist/fuse.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'preset-fuzzy.json');

/** 官方 presetCommandCallback 选择名部分（逐字：exact → Fuse → 第一个结果）。 */
function selectPresetName(allPresets, name) {
    if (!name) return null; // 官方无参返回 currentPreset，这里统一 null 由调用方处理
    if (!Array.isArray(allPresets) || allPresets.length === 0) return null;
    const exact = allPresets.find(p => p.toLowerCase().trim() === name.toLowerCase().trim());
    if (exact) return exact;
    const fuse = new Fuse(allPresets);
    const fuzzy = fuse.search(name);
    if (!fuzzy.length) return null;
    return fuzzy[0].item;
}

const cases = [];
async function add(id, list, query) {
    const expected = selectPresetName(list, query);
    cases.push({ id, args: { list, query }, expected });
}

const baseList = [
    'Default',
    'Claude Sonnet',
    'Creative Writing',
    'RP Instruct',
    'Narrator',
    'Story Telling',
    'Lorebook Heavy',
    'Gemini 2.5 Pro',
    'DeepSeek Chat',
    'Novel',
    'Char Card',
    'Tavern RP',
    'Test Preset',
    '自定义预设',
    '深夜电台',
    'Long Pattern Name With Several Words To Test Chunking Over Thirty Two Characters Long',
];

// 精确匹配
await add('exact', baseList, 'Default');
await add('exact-case', baseList, 'default');
await add('exact-case2', baseList, 'CLAUDE SONNET');
await add('exact-trim', baseList, '  DeepSeek Chat  ');
await add('exact-later', ['A', 'B', 'C'], 'c');
await add('exact-first-of-dups', ['Same', 'Same'], 'same');
// 模糊
await add('fuzzy-prefix', baseList, 'creat');
await add('fuzzy-typo', baseList, 'cluade');
await add('fuzzy-partial', baseList, 'sonnet');
await add('fuzzy-word', baseList, 'deep');
await add('fuzzy-multiword', baseList, 'rp instruct');
await add('fuzzy-single-char', baseList, 'n');
await add('fuzzy-longer-than-item', ['ab'], 'abcd');
await add('fuzzy-no-match', baseList, 'zzzzzz');
await add('fuzzy-empty-query', baseList, '');
await add('fuzzy-chinese', baseList, '深夜');
await add('fuzzy-chinese-typo', baseList, '深夜电');
await add('fuzzy-tie-stable', ['apple', 'apple pie'], 'appl');
await add('fuzzy-case', baseList, 'GEMINI');
// 33+ 字符分块
const longPattern = 'Long Pattern Name With Several Words To Test Chunking Over Thirty Two Characters Long';
await add('chunk-exact', [longPattern], longPattern);
await add('chunk-typo', [longPattern], 'Long Pattern Name With Several Words To Test Chunking Over Thirty Two Characterz Long');
await add('chunk-no-match', [longPattern], 'this pattern is totally unrelated to the preset name here');
// 空串/空白条目被 Fuse 索引跳过
await add('blank-entries', ['', '   ', 'RP'], 'rp');
// 空列表
await add('empty-list', [], 'anything');
// 全空白 query
await add('blank-query', baseList, '   ');
// 单 token vs 多 token norm 影响排序
await add('norm-order', ['short', 'longer name here', 'long'], 'lon');
// 与文本完全相等但大小写不同（Fuse 内部 exact 分支 score=0）
await add('fuse-internal-exact', ['Hello World'], 'hello world');

writeFileSync(outFile, JSON.stringify({ source: 'presetCommandCallback (select name)', cases }, null, 2));
console.log('preset-fuzzy:', cases.length, 'cases ->', outFile);
