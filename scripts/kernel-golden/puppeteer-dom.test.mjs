#!/usr/bin/env node
/**
 * P7 DOM 黄金对比 harness（docs/REFACTOR_V2_PLAN.md §六.2）：
 * 真浏览器（headless Chromium）加载 kernel.html，对语料库逐条断言——
 *   1) Kernel.formatText 输出与 golden/dom-format.json 快照逐字一致（渲染管线防回归）
 *   2) applyTheme 注入官方主题后 CSS 变量逐值读回一致（主题变量逐值一致）
 *   3) renderMessage 后 .mes/.mes_text 挂载且非空（DOM 结构）
 *
 * 运行：npm run test:dom（需 puppeteer；`npm test` 纯 node 四件套不含此项）。
 * 更新快照：UPDATE_GOLDEN=1 npm run test:dom，提交 diff 人工复核。
 */
import { createServer } from 'node:http';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const KERNEL_DIR = join(here, '..', '..', 'app/src/main/assets/kernel');
const THEMES_DIR = join(here, '..', '..', 'app/src/main/assets/themes/moonlit-echoes');
const GOLDEN = join(here, 'golden', 'dom-format.json');

let pass = 0, fail = 0;
const ok = (cond, msg) => {
    if (cond) { pass++; console.log(`  ✓ ${msg}`); }
    else { fail++; console.log(`  ✗ ${msg}`); }
};

// ---- 静态服务器（免依赖 serve kernel 目录）----
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = createServer((req, res) => {
    const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/^\/+/, '');
    const file = join(KERNEL_DIR, rel);
    if (!file.startsWith(KERNEL_DIR) || !existsSync(file)) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream' });
    res.end(readFileSync(file));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

let puppeteer;
try {
    puppeteer = (await import('puppeteer')).default;
} catch {
    console.error('✗ 未安装 puppeteer（npm ci 应已装）；CI 缺此依赖即红');
    process.exit(1);
}

const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-setuid-sandbox'] });
try {
    const page = await browser.newPage();
    page.on('pageerror', (e) => { fail++; console.log('  ✗ 页面 JS 异常:', e.message); });
    await page.goto(`http://127.0.0.1:${port}/kernel.html`, { waitUntil: 'load' });
    await page.waitForFunction('window.Kernel && window.Kernel.ready === true', { timeout: 15000 });

    // ---- 1) formatText 语料黄金对比 ----
    const corpus = JSON.parse(readFileSync(join(here, 'fixtures', 'dom-corpus.json'), 'utf8'));
    const actual = {};
    for (const item of corpus) {
        actual[item.id] = await page.evaluate(
            (mes) => window.Kernel.formatText(mes, {}),
            item.mes,
        );
    }
    if (process.env.UPDATE_GOLDEN === '1') {
        writeFileSync(GOLDEN, JSON.stringify(actual, null, 2) + '\n');
        console.log(`  ↻ golden 已重写：${GOLDEN}（${corpus.length} 条，请人工复核 diff）`);
        pass++;
    } else if (!existsSync(GOLDEN)) {
        writeFileSync(GOLDEN, JSON.stringify(actual, null, 2) + '\n');
        ok(false, `golden 不存在，已生成初版 ${GOLDEN}——复核后提交再跑`);
    } else {
        const expected = JSON.parse(readFileSync(GOLDEN, 'utf8'));
        for (const item of corpus) {
            const exp = expected[item.id], act = actual[item.id];
            if (exp === undefined) { ok(false, `${item.id}: golden 缺条目（UPDATE_GOLDEN=1 重生成）`); continue; }
            if (exp === act) { ok(true, `${item.id}: 输出逐字一致 (${act.length} chars)`); continue; }
            let d = 0; while (d < Math.min(exp.length, act.length) && exp[d] === act[d]) d++;
            ok(false, `${item.id}: 首个差异@${d}\n      golden: …${JSON.stringify(exp.slice(Math.max(0, d - 30), d + 30))}…\n      actual: …${JSON.stringify(act.slice(Math.max(0, d - 30), d + 30))}…`);
        }
    }

    // ---- 2) 官方主题变量逐值一致（applyTheme 注入 → computedStyle 读回）----
    const theme = JSON.parse(readFileSync(join(THEMES_DIR, 'MoonlitEchoes.json'), 'utf8'));
    await page.evaluate((t) => window.Kernel.applyTheme(t), theme);
    const probeFields = [
        ['main_text_color', '--SmartThemeBodyColor'],
        ['italics_text_color', '--SmartThemeEmColor'],
        ['underline_text_color', '--SmartThemeUnderlineColor'],
        ['quote_text_color', '--SmartThemeQuoteColor'],
        ['border_color', '--SmartThemeBorderColor'],
    ];
    for (const [key, cssVar] of probeFields) {
        if (!(key in theme)) { ok(true, `${cssVar}: 主题未含字段，跳过`); continue; }
        const readBack = await page.evaluate(
            (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim(),
            cssVar,
        );
        ok(readBack === String(theme[key]), `${cssVar} = ${theme[key]}（读回 ${readBack || '空'}）`);
    }

    // ---- 3) renderMessage DOM 结构 ----
    await page.evaluate(() => window.Kernel.renderMessage({ mesid: 'm-0', mes: '<b>黄金</b>样本', chName: '测试角色' }));
    await new Promise((r) => setTimeout(r, 120));
    const dom = await page.evaluate(() => {
        const mes = document.querySelector('.mes');
        const text = document.querySelector('.mes .mes_text');
        return { hasMes: !!mes, textLen: text ? text.innerHTML.length : 0, bold: !!document.querySelector('.mes strong, .mes b') };
    });
    ok(dom.hasMes && dom.textLen > 0, `.mes/.mes_text 挂载且非空 (${dom.textLen} chars)`);

    console.log(`\nPuppeteer DOM 黄金对比: ${pass} 通过, ${fail} 失败`);
    process.exit(fail > 0 ? 1 : 0);
} finally {
    await browser.close().catch(() => {});
    server.close();
}
