#!/usr/bin/env node
// 快捷回复自动执行选择逻辑 handleWIActivation → JSON fixture 生成器。
// 从官方 extensions/quick-reply/src/AutoExecuteHandler.js 逐字提取方法体；
// 只做最小测试替身：checkExecute 恒真、performAutoExecute 改为返回 qrList（执行语义由引擎 Handler 接）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'auto-execute.json');

const src = readFileSync(
    join(officialRef, 'public', 'scripts', 'extensions', 'quick-reply', 'src', 'AutoExecuteHandler.js'),
    'utf8',
);

function scanBody(source, bodyStart) {
    let depth = 0;
    let inString = null;
    let inLineComment = false;
    let inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) {
            if (ch === '\n') inLineComment = false;
            continue;
        }
        if (inBlockComment) {
            if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; }
            continue;
        }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) return i;
        }
    }
    throw new Error(`unbalanced body: start=${bodyStart}`);
}

const marker = 'async handleWIActivation(entries) {';
const start = src.indexOf(marker);
if (start < 0) throw new Error('handleWIActivation not found');
const bodyStart = src.indexOf('{', start);
const end = scanBody(src, bodyStart);

let fn = src.slice(start, end + 1);
// 最小测试替身（不改选择逻辑）：
fn = fn.replace(
    'if (!this.checkExecute() || !Array.isArray(entries) || entries.length === 0) return;',
    'if (!Array.isArray(entries) || entries.length === 0) return [];',
);
fn = fn.replaceAll('this.settings.config', 'config');
fn = fn.replaceAll('this.settings.chatConfig', 'chatConfig');
fn = fn.replaceAll('this.settings.charConfig', 'charConfig');
fn = fn.replace('await this.performAutoExecute(qrList);', 'return qrList;');
fn = fn.replace('async handleWIActivation(entries) {', 'async function handleWIActivation(entries, config, chatConfig, charConfig) {');

const runnable = fn + '\n' + `
export const cases = [];

async function makeCase(id, entries, config, chatConfig, charConfig) {
    const qrList = await handleWIActivation(entries, config, chatConfig, charConfig);
    cases.push({
        id,
        args: { entries, config, chatConfig, charConfig, presets: toPresets([config, chatConfig, charConfig]) },
        expected: qrList.map(qr => ({ automationId: qr.automationId, label: qr.label })),
    });
}

function toPresets(configs) {
    const out = [];
    configs.forEach((config, ci) => {
        (config?.setList ?? []).forEach((link, si) => {
            if (!link.set) return;
            out.push({ name: 'c' + ci + '-s' + si, slots: link.set.qrList.map(qr => ({ label: qr.label, automationId: qr.automationId })) });
        });
    });
    return out;
}

const qr = (label, automationId) => ({ automationId, label });
const set = (...qrs) => ({ setList: [{ set: { qrList: qrs } }] });

await makeCase(
    'match-by-automation-id',
    [{ uid: 1, automationId: 'a1' }, { uid: 2, automationId: 'a2' }, { uid: 3, automationId: null }],
    set(qr('x1', 'a1'), qr('x2', 'nope')),
    set(qr('y2', 'a2')),
    set(qr('z1', 'a1'), qr('z3', '')),
);

await makeCase(
    'duplicate-across-configs',
    [{ uid: 1, automationId: 'a1' }],
    set(qr('g1', 'a1')),
    set(qr('c1', 'a1')),
    set(qr('h1', 'a1')),
);

await makeCase(
    'broken-link-skipped',
    [{ uid: 1, automationId: 'a1' }],
    { setList: [{ set: null }, { set: { qrList: [qr('ok', 'a1')] } }] },
    null,
    null,
);

await makeCase(
    'no-match-returns-empty',
    [{ uid: 1, automationId: 'a9' }],
    set(qr('x1', 'a1')),
    null,
    null,
);
`;

const code = runnable;
const dataUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const mod = await import(dataUrl);
writeFileSync(outFile, JSON.stringify({ source: 'AutoExecuteHandler.js handleWIActivation', cases: mod.cases }, null, 2));
console.log(`auto-execute: ${mod.cases.length} cases -> ${outFile}`);
