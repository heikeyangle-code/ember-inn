#!/usr/bin/env node
// 真实行为比对 · 正则脚本执行时机
// 在官方 SillyTavern 真实浏览器环境中验证：
//   A. WORLD_INFO 正则：条目内容在「BUILDING PROMPT 阶段（扫描完成后组装时）」被替换
//   B. Chat 消息 prompt 正则（isPrompt=true 只跑 promptOnly 脚本）先于世界书扫描
//   C. 默认脚本（非 promptOnly）在 prompt 阶段不运行（发送/显示阶段才运行）
// 全部使用官方 getRegexedString / checkWorldInfo / regexFromString 真实实现。
import { chromium } from 'playwright-core';
import { writeFileSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const CHROME = '/root/.cache/puppeteer/chrome/linux-151.0.7922.71/chrome-linux64/chrome';
const URL = 'http://127.0.0.1:8011/';
const OUT = join(here, 'regex-official-real.json');

const scenarios = [
    {
        id: 'A_wi_content_regex_at_building_prompt',
        desc: 'WORLD_INFO 正则(promptOnly)：条目内容「宝藏密码」→ 替换「珍宝」，验证 WI before 输出用替换后文本',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: false },
        chat: ['密道'],
        maxContext: 100,
        regexScripts: [
            { findRegex: '/宝藏/g', replaceString: '珍宝', placement: [5], markdownOnly: false, promptOnly: true, runOnEdit: true, disabled: false, substituteRegex: 0 },
        ],
        entries: [
            { uid: 1, key: ['密道'], content: '宝藏密码', order: 10, position: 0 },
        ],
    },
    {
        id: 'B_promptonly_chat_regex_before_wi_scan',
        desc: 'promptOnly USER_INPUT 正则把「密语」→「开锁」；checkWorldInfo 前先过该正则则命中「开锁」条目',
        settings: { world_info_depth: 2, world_info_budget: 100, world_info_recursive: false },
        chat: ['请说密语'],
        maxContext: 100,
        regexScripts: [
            { findRegex: '/密语/g', replaceString: '开锁', placement: [1], markdownOnly: false, promptOnly: true, runOnEdit: true, disabled: false, substituteRegex: 0 },
        ],
        entries: [
            { uid: 1, key: ['开锁'], content: '门开了', order: 10, position: 0 },
        ],
    },
    {
        id: 'C_default_script_not_run_in_prompt_phase',
        desc: '默认脚本（非 promptOnly）在 isPrompt=true 时不运行；对照：send 阶段(isPrompt 未设)运行',
        chat: ['甲'],
        maxContext: 100,
        regexScripts: [
            { findRegex: '/甲/g', replaceString: '乙', placement: [1], markdownOnly: false, promptOnly: false, runOnEdit: true, disabled: false, substituteRegex: 0 },
        ],
        entries: [
            { uid: 1, key: ['乙'], content: '条目乙', order: 10, position: 0 },
        ],
        skipWi: true,
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
        const re = await import('/scripts/extensions/regex/engine.js');
        const tok = await import('/scripts/tokenizers.js');
        const pu = await import('/scripts/power-user.js');
        pu.power_user.tokenizer = tok.tokenizers.NONE;
        out.env.wiExports = ['checkWorldInfo', 'updateWorldInfoSettings'].map(k => ({ k, v: typeof wi[k] }));
        out.env.reExports = ['getRegexedString', 'regex_placement', 'runRegexScript'].map(k => ({ k, v: typeof re[k] }));

        for (const sc of scenarios) {
            try {
                const { settings, chat, maxContext, entries, regexScripts, skipWi } = sc;
                // 覆盖全局正则脚本（GLOBAL 恒 allowed）；extension_settings 为 extensions.js 导出的 const 对象
                const ext = await import('/scripts/extensions.js');
                ext.extension_settings.regex = regexScripts;
                wi.updateWorldInfoSettings(settings ?? {}, ['probe_regex']);
                const entryMap = {};
                for (const e of entries) {
                    entryMap[String(e.uid)] = {
                        uid: e.uid, key: e.key, keysecondary: [],
                        comment: '', constant: false, selective: true, order: e.order ?? 100,
                        position: e.position ?? 0, disable: false, displayIndex: 0, addMemo: true,
                        group: '', groupOverride: false, groupWeight: 100,
                        sticky: 0, cooldown: 0, delay: 0,
                        probability: 100, depth: 4, useProbability: false,
                        selectiveLogic: 0, content: e.content,
                        ignoreBudget: false,
                    };
                }
                wi.worldInfoCache.set('probe_regex', { entries: entryMap });

                // 发送/显示阶段（isPrompt 未设）：默认脚本 + promptOnly 脚本都跑
                const sendPhaseText = re.getRegexedString(chat[0], re.regex_placement.USER_INPUT);
                // prompt 阶段（isPrompt=true）：只跑 promptOnly 脚本
                const promptPhaseText = re.getRegexedString(chat[0], re.regex_placement.USER_INPUT, { isPrompt: true });
                const caseOut = {
                    id: sc.id,
                    input: { chat: sc.chat, entries: sc.entries, regexScripts: sc.regexScripts },
                    sendPhaseText,
                    promptPhaseText,
                };
                if (!skipWi) {
                    // 直接扫原始 chat（checkWorldInfo 自己不做 chat 正则）
                    const resRaw = await wi.checkWorldInfo(chat, maxContext, true);
                    // 官方 getChat 先过 prompt 正则再喂 WI
                    const resPrompt = await wi.checkWorldInfo([promptPhaseText], maxContext, true);
                    caseOut.raw = {
                        before: resRaw.worldInfoBefore,
                        uids: Array.from(resRaw.allActivatedEntries.values()).map(e => `${e.world}.${e.uid}`),
                    };
                    caseOut.prompt = {
                        before: resPrompt.worldInfoBefore,
                        uids: Array.from(resPrompt.allActivatedEntries.values()).map(e => `${e.world}.${e.uid}`),
                    };
                }
                out.cases.push(caseOut);
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
    console.log(`[${c.id}] send=${JSON.stringify(c.sendPhaseText)} prompt=${JSON.stringify(c.promptPhaseText)}` + (c.raw ? ` raw.before=${JSON.stringify(c.raw.before)} raw.uids=${c.raw.uids.join(',')} prompt.before=${JSON.stringify(c.prompt.before)} prompt.uids=${c.prompt.uids.join(',')}` : ''));
}
await browser.close();
