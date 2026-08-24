#!/usr/bin/env node
/**
 * 生成酒馆助手 API 契约清单：扫描 JS-Slash-Runner 源码导出面 → fixtures/th-api-surface.json
 *
 * 用法：node gen-contract.mjs [~/js-slash-runner-ref]
 * 基线升级流程：更新 TH 仓库 checkout → 重跑本脚本 → diff fixture → 对照补齐兼容层。
 */
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'fs';
import { join } from 'path';

const repo = process.argv[2] || `${process.env.HOME}/js-slash-runner-ref`;
const fnDir = join(repo, 'src', 'function');
const names = new Map(); // name -> file

for (const f of readdirSync(fnDir)) {
    if (!f.endsWith('.ts')) continue;
    const text = readFileSync(join(fnDir, f), 'utf8');
    const re = /^export (?:async )?(?:function|const) ([A-Za-z_][A-Za-z0-9_]*)/gm;
    for (const m of text.matchAll(re)) {
        if (!names.has(m[1])) names.set(m[1], `src/function/${f}`);
    }
}

const surface = [...names.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, source]) => ({ name, source }));

mkdirSync(join(import.meta.dirname, 'fixtures'), { recursive: true });
const target = join(import.meta.dirname, 'fixtures', 'th-api-surface.json');
writeFileSync(target, JSON.stringify({ baseline: 'JS-Slash-Runner src/function exports', total: surface.length, functions: surface }, null, 2) + '\n');
console.log(`✓ ${target} 已生成（${surface.length} 个导出）`);
