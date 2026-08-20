import { chromium } from 'playwright-core';

const CHROME = '/root/.cache/puppeteer/chrome/linux-151.0.7922.71/chrome-linux64/chrome';
const URL = 'http://127.0.0.1:8011/';

const browser = await chromium.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
});
const page = await browser.newPage();
page.on('pageerror', e => console.log('PAGE EXCEPTION:', e.message));
await page.goto(URL, { waitUntil: 'load', timeout: 60000 });

// 等待聊天界面出现（等待 mesText 或聊天容器）
try {
    await page.waitForSelector('#chat, #send_form, .mes', { timeout: 30000 });
    console.log('CHAT UI READY');
} catch (e) {
    console.log('CHAT UI NOT FOUND (continuing anyway)');
}
await page.waitForTimeout(3000);

const r = await page.evaluate(async () => {
    const out = {};
    try {
        const wi = await import('/scripts/world-info.js');
        out.wiKeys = Object.keys(wi).filter(k => /checkWorldInfo|getSortedEntries|WorldInfoBuffer|DEFAULT_DEPTH|MAX_SCAN_DEPTH|scan_state/.test(k));
        out.checkWorldInfoType = typeof wi.checkWorldInfo;
    } catch (e) {
        out.wiErr = String(e && e.message || e);
    }
    out.globals = {};
    for (const k of ['worldEntries','getContext','getCharaFilename','getTagKeyForEntity','substituteParams','getRegexedString','getTokenCountAsync','getSortedEntries','this_chid','world_info_depth','world_info_budget','chat']) {
        try { out.globals[k] = typeof eval(k); } catch (e) { out.globals[k] = 'ERR'; }
    }
    return out;
});
console.log(JSON.stringify(r, null, 2));
await browser.close();
