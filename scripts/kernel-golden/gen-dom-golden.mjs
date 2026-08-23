#!/usr/bin/env node
/**
 * 生成 golden/dom-format.json：用 jsdom 跑同一 render.js 管线对语料库输出快照。
 * CI 的 Puppeteer harness（puppeteer-dom.test.mjs）断言真 Chromium 输出与此文件逐字一致——
 * 同时验证「渲染管线未回归」与「jsdom/真浏览器两运行时一致」。
 * 语料或管线有意变更时重跑本脚本，人工复核 diff 后提交。
 */
import { JSDOM } from 'jsdom';
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const K = join(here, '..', '..', 'app', 'src', 'main', 'assets', 'kernel');
const dom = new JSDOM(`<!DOCTYPE html><html><body><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;
for (const f of ['js/showdown.min.js', 'js/css-tools.min.js', 'js/dompurify.min.js', 'js/highlight.min.js', 'js/st-extensions.js', 'js/render.js'])
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));

if (!window.Kernel?.ready) { console.error('✗ 内核未就绪'); process.exit(1); }

const corpus = JSON.parse(readFileSync(join(here, 'fixtures', 'dom-corpus.json'), 'utf8'));
const out = {};
for (const item of corpus) out[item.id] = window.Kernel.formatText(item.mes, {});

mkdirSync(join(here, 'golden'), { recursive: true });
const target = join(here, 'golden', 'dom-format.json');
writeFileSync(target, JSON.stringify(out, null, 2) + '\n');
console.log(`✓ ${target} 已生成（${corpus.length} 条）`);
for (const [id, html] of Object.entries(out)) console.log(`  ${id}: ${html.length} chars`);
