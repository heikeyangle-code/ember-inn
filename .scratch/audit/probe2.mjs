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
await page.waitForTimeout(8000);

const r = await page.evaluate(async () => {
    const out = {};
    // MacroEngine
    try { const m = await import('/scripts/macros/engine/MacroEngine.js'); out.macro = Object.keys(m); } catch (e) { out.macro = 'ERR ' + e.message; }
    // world-info
    try { const w = await import('/scripts/world-info.js'); out.wi = Object.keys(w).filter(k => /check|scan|buffer/i.test(k)); } catch (e) { out.wi = 'ERR ' + e.message; }
    // macro-system (core macros registry)
    try { const s = await import('/scripts/macros.js'); out.macrosjs = Object.keys(s); } catch (e) { out.macrosjs = 'ERR ' + e.message; }
    // slash commands parser
    try { const s = await import('/scripts/slash-commands/SlashCommandParser.js'); out.slash = Object.keys(s); } catch (e) { out.slash = 'ERR ' + e.message; }
    // check global state availability
    out.globals = {
        worldEntries: typeof worldEntries,
        world_info_recursive: typeof world_info_recursive,
        world_info_depth: typeof world_info_depth,
        world_info_budget: typeof world_info_budget,
        chat: typeof chat,
        getTokenCountAsync: typeof getTokenCountAsync,
        substituteParams: typeof substituteParams,
        power_user: typeof power_user,
        oai_settings: typeof oai_settings,
        eventSource: typeof eventSource,
        main_api: typeof main_api,
    };
    return out;
});
console.log(JSON.stringify(r, null, 2));
await browser.close();
