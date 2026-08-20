import { chromium } from 'playwright-core';

const CHROME = '/root/.cache/puppeteer/chrome/linux-151.0.7922.71/chrome-linux64/chrome';
const URL = 'http://127.0.0.1:8011/';

const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
});

const page = await browser.newPage();
page.on('console', m => { if (m.type() === 'error') console.log('PAGE ERROR:', m.text()); });
page.on('pageerror', e => console.log('PAGE EXCEPTION:', e.message));

try {
    await page.goto(URL, { waitUntil: 'load', timeout: 60000 });
    console.log('TITLE:', await page.title());
    await page.waitForTimeout(8000);
    // Try to import MacroEngine via dynamic import
    const r = await page.evaluate(async () => {
        try {
            const mod = await import('/scripts/macros/engine/MacroEngine.js');
            return { ok: true, keys: Object.keys(mod) };
        } catch (e) {
            return { ok: false, err: String(e && e.message || e) };
        }
    });
    console.log('MacroEngine import:', JSON.stringify(r));
} catch (e) {
    console.log('NAV ERROR:', e.message);
}
await browser.close();
