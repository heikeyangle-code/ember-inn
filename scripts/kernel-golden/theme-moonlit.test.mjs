// 主题应用 + Moonlit Echoes 兼容黄金测试
// 运行: node theme-moonlit.test.mjs（需先 npm install）
import { JSDOM } from 'jsdom';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const K = join(ROOT, 'app', 'src', 'main', 'assets', 'kernel');
const ME = join(ROOT, 'app', 'src', 'main', 'assets', 'themes', 'moonlit-echoes');

const dom = new JSDOM(`<!DOCTYPE html><html><body class="light-theme"><style id="custom-style" type="text/css"></style><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;
for (const f of ['js/showdown.min.js','js/css-tools.min.js','js/dompurify.min.js','js/highlight.min.js','js/st-extensions.js','js/render.js'])
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));

let pass = 0, fail = 0;
const t = (n, a, e) => { if (a === e) { pass++; console.log(`  ✓ ${n}`); } else { fail++; console.log(`  ✗ ${n}\n    实际: ${JSON.stringify(a)}`); } };
const body = () => window.document.body;
const root = () => window.document.documentElement;

// ---------- 1. 官方格式主题 JSON 全字段应用 ----------
const glimmer = JSON.parse(readFileSync(`${ME}/Glimmer.json`, 'utf8'));
window.Kernel.applyTheme(glimmer);

t('Glimmer: 主文字色→CSS变量', root().style.getPropertyValue('--SmartThemeBodyColor'), glimmer.main_text_color);
t('Glimmer: 引号色→CSS变量', root().style.getPropertyValue('--SmartThemeQuoteColor'), glimmer.quote_text_color);
t('Glimmer: 模糊强度→CSS变量', root().style.getPropertyValue('--blurStrength'), String(glimmer.blur_strength));
t('Glimmer: 字号缩放→CSS变量', root().style.getPropertyValue('--fontScale'), String(glimmer.font_scale));
t('Glimmer: custom_css 注入', window.document.getElementById('custom-style').textContent, glimmer.custom_css);
t('Glimmer: CheckboxBg RGBA 分解', !!root().style.getPropertyValue('--SmartThemeCheckboxBgColorR'), true);

// 开关型 body 类（chat_display=1 → 气泡；timestamps 等按值同步）
t('Glimmer: chat_display=1 → bubblechat', body().classList.contains('bubblechat'), glimmer.chat_display === 1);
t('Glimmer: timestamps 开→无 no-timestamps', body().classList.contains('no-timestamps'), !glimmer.timestamps_enabled);

// ---------- 2. 全量同步语义：换主题后旧类被清除 ----------
const moonlit = JSON.parse(readFileSync(`${ME}/MoonlitEchoes.json`, 'utf8'));
const flipped = { ...moonlit, chat_display: 2, timestamps_enabled: false, avatar_style: 2 };
window.Kernel.applyTheme(flipped);
t('换主题: bubblechat 被清除', body().classList.contains('bubblechat'), false);
t('换主题: chat_display=2 → documentstyle', body().classList.contains('documentstyle'), true);
t('换主题: timestamps 关→no-timestamps', body().classList.contains('no-timestamps'), true);
t('换主题: avatar_style=2 → square-avatars', body().classList.contains('square-avatars'), true);
t('换主题: custom_css 替换', window.document.getElementById('custom-style').textContent, moonlit.custom_css);

// ---------- 3. Moonlit style.css 选择器与内核 DOM 同构性 ----------
// jsdom 无 fetch：预置官方消息模板（与 kernel.html 运行时同源）
const templateHtml = readFileSync(`${K}/official/message-template.html`, 'utf8');
window.fetch = (url) => {
    if (String(url).includes('message-template')) {
        return Promise.resolve({ text: () => Promise.resolve(templateHtml) });
    }
    return Promise.reject(new Error('no fetch: ' + url));
};

// 内核渲染一条消息，然后验证 Moonlit CSS 的核心选择器都能命中
await window.Kernel.renderMessage({
    mesid: '1', mes: 'Hello **world**', chName: 'Alice', isUser: false,
    isSystem: false, avatarUrl: null, timestamp: '12:00', tokenCount: 10,
});
const doc = window.document;
const q = (sel) => !!doc.querySelector(sel);
t('内核 DOM: .mes 存在', q('.mes[mesid="1"]'), true);
t('内核 DOM: .mes_text 存在', q('.mes[mesid="1"] .mes_text'), true);
t('内核 DOM: .mesAvatarWrapper 存在', q('.mes[mesid="1"] .mesAvatarWrapper'), true);
t('内核 DOM: .mes_block 存在', q('.mes[mesid="1"] .mes_block'), true);
t('内核 DOM: .name_text 存在', q('.mes[mesid="1"] .name_text'), true);

// Moonlit style.css 中引用的官方选择器逐一在内核 DOM 中存在
const css = readFileSync(`${ME}/style.css`, 'utf8');
const criticalSelectors = ['.mes_text', '.mes', '.mesAvatarWrapper', '.mes_block', '#chat'];
for (const sel of criticalSelectors) {
    const used = css.includes(sel);
    t(`Moonlit CSS 引用 ${sel} 且内核 DOM 可命中`, used && q(sel), true);
}

// 8 种消息布局的 body 类在 style.css 中都有对应规则块
const layoutClasses = ['echostyle', 'whisperstyle', 'hushstyle', 'tidestyle', 'ripplestyle', 'bubblechat', 'documentstyle', 'flatchat'];
for (const cls of layoutClasses) {
    t(`Moonlit 布局类 body.${cls} 有 CSS 规则`, css.includes(cls), true);
}
// 官方布局类切换（内核 API 与官方 power-user 同构）
t('body 类切换: echostyle 可加', (() => { body().classList.add('echostyle'); return body().classList.contains('echostyle'); })(), true);

// ---------- 4. 扩展预设格式识别 ----------
const preset = JSON.parse(readFileSync(`${ME}/Glimmer-preset.json`, 'utf8'));
t('预设格式: moonlitEchoesPreset 标记', preset.moonlitEchoesPreset === true, true);
t('预设格式: settings.customThemeColor 存在', typeof preset.settings?.customThemeColor === 'string', true);

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail ? 1 : 0);
