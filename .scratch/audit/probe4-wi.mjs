#!/usr/bin/env node
// 真实行为比对 · 世界书扫描
// 在官方 SillyTavern 真实浏览器环境中运行真正的 checkWorldInfo（真实 getTokenCountAsync / getRegexedString / substituteParams），
// 注入完全相同的 world 条目与聊天记录，采集真实输出；再与 EmberInn 引擎逐字符比对。
import { chromium } from 'playwright-core';
import { writeFileSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const CHROME = '/root/.cache/puppeteer/chrome/linux-151.0.7922.71/chrome-linux64/chrome';
const URL = 'http://127.0.0.1:8011/';
const OUT = join(here, 'wi-official-real.json');

// ---- 场景：每个都是同一输入喂给官方 ---- 
// settings 用官方 updateWorldInfoSettings 的字段名
const scenarios = [
    {
        id: 'recursion_cycle_ab',
        desc: 'A 触发 B，B 触发 A（环路），recursive=true，验证官方如何终止、最终哪些进 prompt',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: true },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, key: ['线索'], content: '暗门', order: 10, position: 0 },
            { uid: 2, key: ['暗门'], content: '宝藏', order: 20, position: 0 },
            { uid: 3, key: ['宝藏'], content: '钥匙线索', order: 30, position: 0 },
        ],
    },
    {
        id: 'recursion_max_steps_1',
        desc: '长链 A→B→C→D，maxRecursionSteps=1，验证官方在哪一步截断',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: true, world_info_max_recursion_steps: 1 },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, key: ['线索'], content: 'A文本', order: 10, position: 0 },
            { uid: 2, key: ['A文本'], content: 'B文本', order: 20, position: 0 },
            { uid: 3, key: ['B文本'], content: 'C文本', order: 30, position: 0 },
            { uid: 4, key: ['C文本'], content: 'D文本', order: 40, position: 0 },
        ],
    },
    {
        id: 'recursion_max_steps_2',
        desc: '同上 maxRecursionSteps=2，验证第二层之后截断',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: true, world_info_max_recursion_steps: 2 },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, key: ['线索'], content: 'A文本', order: 10, position: 0 },
            { uid: 2, key: ['A文本'], content: 'B文本', order: 20, position: 0 },
            { uid: 3, key: ['B文本'], content: 'C文本', order: 30, position: 0 },
            { uid: 4, key: ['C文本'], content: 'D文本', order: 40, position: 0 },
        ],
    },
    {
        id: 'budget_priority_order',
        desc: '预算超限截断：处理顺序 order 降序(丙→乙→甲)，预算只够 2 条，验证哪条被砍及最终字符串',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: false },
        chat: ['a b'],
        maxContext: 15,
        entries: [
            { uid: 1, key: ['a'], content: '甲甲甲甲甲', order: 10, position: 0 },
            { uid: 2, key: ['b'], content: '乙乙乙乙乙', order: 20, position: 0 },
            { uid: 3, key: ['b'], content: '丙丙丙丙丙', order: 30, position: 0 },
        ],
    },
    {
        id: 'budget_priority_sticky',
        desc: '预算超限时 sticky 条目优先进入，普通条目被砍',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: false },
        chat: ['a b'],
        maxContext: 20,
        entries: [
            { uid: 1, key: ['a'], content: '普通A普通A普通A普通A普通A', order: 10, position: 0 },
            { uid: 2, key: ['b'], content: '粘住B粘住B粘住B粘住B粘住B', order: 20, position: 0, sticky: 2 },
        ],
    },
    {
        id: 'budget_ignorebudget',
        desc: '预算超限后 ignoreBudget 条目（order 最低=最后处理）仍能进入，验证 continue 而非 break',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: false },
        chat: ['a b c'],
        maxContext: 12,
        entries: [
            { uid: 1, key: ['a'], content: 'AAAAAAAAAAAAAAAAAAAA', order: 30, position: 0 },
            { uid: 2, key: ['b'], content: 'BBBBBBBBBBBBBBBBBBBB', order: 20, position: 0 },
            { uid: 3, key: ['c'], content: '忽略预算C', order: 10, position: 0, ignoreBudget: true },
        ],
    },
    {
        id: 'depth_positions',
        desc: 'before/after/atDepth 三种位置拼装顺序（含 depth 排序），验证最终字符串',
        settings: { world_info_depth: 3, world_info_budget: 100, world_info_recursive: false },
        chat: ['钥匙 门'],
        maxContext: 100,
        entries: [
            { uid: 1, key: ['钥匙'], content: '前置X', order: 10, position: 0 },
            { uid: 2, key: ['门'], content: '后置Y', order: 20, position: 1 },
            { uid: 3, key: ['门'], content: '深处Z', order: 30, position: 4, depth: 1 },
        ],
    },
];

const browser = await chromium.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
});
const page = await browser.newPage();
page.on('pageerror', e => console.log('PAGE EXCEPTION:', e.message));
await page.goto(URL, { waitUntil: 'load', timeout: 60000 });
try {
    await page.waitForSelector('#chat, #send_form, .mes', { timeout: 30000 });
} catch (e) { console.log('chat UI not ready, continuing'); }
await page.waitForTimeout(3000);

const results = await page.evaluate(async (scenarios) => {
    const out = { env: {}, cases: [] };
    try {
        const wi = await import('/scripts/world-info.js');
        const tok = await import('/scripts/tokenizers.js');
        const pu = await import('/scripts/power-user.js');
        // 强制确定性 token 计数：NONE = guesstimate = ceil(utf8Bytes/3.35)
        pu.power_user.tokenizer = tok.tokenizers.NONE;
        out.env.tokenizersNONE = tok.tokenizers.NONE;
        out.env.guessTest = await tok.getTokenCountAsync('test test test');
        out.env.mainApi = (() => { try { return typeof main_api !== 'undefined' ? main_api : 'undefined'; } catch { return 'ERR'; } })();
        out.env.wiExports = ['checkWorldInfo', 'updateWorldInfoSettings', 'worldInfoCache', 'getTokenCountAsync', 'getRegexedString'].map(k => ({ k, v: typeof wi[k] }));

        for (const sc of scenarios) {
            try {
                const { settings, chat, maxContext, entries } = sc;
                // 注入设置与激活世界
                wi.updateWorldInfoSettings(settings, ['probe_world']);
                // 注入 world 条目（官方 loadWorldInfo 命中 worldInfoCache）
                const entryMap = {};
                for (const e of entries) {
                    entryMap[String(e.uid)] = {
                        uid: e.uid, key: e.key, keysecondary: e.keysecondary ?? [],
                        comment: '', constant: e.constant ?? false, selective: true, order: e.order ?? 100,
                        position: e.position ?? 0, disable: false, displayIndex: 0, addMemo: true,
                        group: e.group ?? '', groupOverride: false, groupWeight: 100,
                        sticky: e.sticky ?? 0, cooldown: e.cooldown ?? 0, delay: e.delay ?? 0,
                        probability: e.probability ?? 100, depth: e.depth ?? 4, useProbability: e.useProbability ?? false,
                        selectiveLogic: e.selectiveLogic ?? 0, content: e.content,
                        ignoreBudget: e.ignoreBudget ?? false,
                    };
                }
                wi.worldInfoCache.set('probe_world', { entries: entryMap });
                // 清理可能污染的事件副作用：dry run
                const res = await wi.checkWorldInfo(chat, maxContext, true);
                out.cases.push({
                    id: sc.id,
                    input: {
                        settings: sc.settings, chat: sc.chat, maxContext: sc.maxContext, entries: sc.entries,
                    },
                    before: res.worldInfoBefore,
                    after: res.worldInfoAfter,
                    uids: Array.from(res.allActivatedEntries.values()).map(e => `${e.world}.${e.uid}`),
                    em: (res.EMEntries ?? []).map(e => ({ position: e.position, content: e.content })),
                    anBefore: res.ANBeforeEntries ?? [],
                    anAfter: res.ANAfterEntries ?? [],
                    depth: (res.WIDepthEntries ?? []).map(e => ({ depth: e.depth, role: e.role, entries: e.entries })),
                });
            } catch (e) {
                out.cases.push({ id: sc.id, error: String(e && e.stack || e) });
            }
        }
    } catch (e) {
        out.fatal = String(e && e.stack || e);
    }
    return out;
}, scenarios);

console.log('ENV:', JSON.stringify(results.env, null, 2));
writeFileSync(OUT, JSON.stringify({ generated: new Date().toISOString(), source: 'official-sillytavern-real-browser', cases: results.cases }, null, 2) + '\n');
for (const c of results.cases) {
    if (c.error) { console.log(`[${c.id}] ERROR ${c.error}`); continue; }
    console.log(`[${c.id}] before=${JSON.stringify(c.before)} uids=${c.uids.join(',')} depth=${JSON.stringify(c.depth)}`);
}
await browser.close();
