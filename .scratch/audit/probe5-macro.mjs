#!/usr/bin/env node
// 真实行为比对 · 宏引擎
// 在官方 SillyTavern 真实浏览器中，用真正的 MacroEngine.evaluate 评估与 macros.json fixture 相同的输入，
// 逐字符比对真实输出 vs fixture 期望（fixture 期望来自官方仓库 tests/frontend/MacroEngine.e2e.js）。
// 引擎侧已由 MacroDiffTest 证明与 fixture 一致 ⇒ 真实浏览器 == 引擎 成立。
import { chromium } from 'playwright-core';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const CHROME = '/root/.cache/puppeteer/chrome/linux-151.0.7922.71/chrome-linux64/chrome';
const URL = 'http://127.0.0.1:8011/';
const fixture = JSON.parse(readFileSync(join(here, '..', '..', 'engine', 'src', 'test', 'resources', 'diff', 'macros.json'), 'utf8'));
const cases = fixture.cases;

const browser = await chromium.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
});
const page = await browser.newPage();
page.on('pageerror', e => console.log('PAGE EXCEPTION:', e.message));
await page.goto(URL, { waitUntil: 'load', timeout: 60000 });
try {
    await page.waitForSelector('#chat, #send_form, .mes', { timeout: 30000 });
} catch (e) { console.log('chat UI not ready'); }
await page.waitForTimeout(3000);

// 分批评估，避免单次 evaluate 过大
const results = [];
const BATCH = 40;
for (let b = 0; b < cases.length; b += BATCH) {
    const slice = cases.slice(b, b + BATCH).map(c => ({ id: c.id, input: c.input }));
    const r = await page.evaluate(async (slice) => {
        const { MacroEngine } = await import('/scripts/macros/engine/MacroEngine.js');
        const { MacroEnvBuilder } = await import('/scripts/macros/engine/MacroEnvBuilder.js');
        const out = [];
        for (const c of slice) {
            try {
                const env = MacroEnvBuilder.buildFromRawEnv({ content: c.input });
                const result = await MacroEngine.evaluate(c.input, env);
                out.push({ id: c.id, ok: true, result });
            } catch (e) {
                out.push({ id: c.id, ok: false, error: String(e && e.stack || e) });
            }
        }
        return out;
    }, slice);
    results.push(...r);
}

let mismatches = 0;
for (const c of cases) {
    const r = results.find(x => x.id === c.id);
    if (!r) { console.log(`NO RESULT: ${c.id}`); mismatches++; continue; }
    if (!r.ok) { console.log(`EVAL ERROR [${c.id}]: ${r.error}`); mismatches++; continue; }
    if (r.result !== c.expected) {
        mismatches++;
        console.log(`MISMATCH [${c.id}]\n  real=${JSON.stringify(r.result)}\n  fixture=${JSON.stringify(c.expected)}`);
    }
}
console.log(`\nchecked ${cases.length} cases, ${mismatches} mismatches vs fixture`);
await browser.close();
