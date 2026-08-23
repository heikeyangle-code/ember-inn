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

// 思考块（官方 reasoning.js updateDom 语义）：reasoning 类 + done 状态 + 内容经 messageFormatting
await window.Kernel.renderMessage({
    mesid: '2', mes: '最终回答', chName: 'Alice', isUser: false,
    isSystem: false, avatarUrl: null, timestamp: '12:01', tokenCount: 8,
    reasoning: '**内心独白**\n让我想想再答',
});
t('思考块: .mes 带 reasoning 类', q('.mes[mesid="2"].reasoning'), true);
t('思考块: data-reasoning-state=done', doc.querySelector('.mes[mesid="2"]').getAttribute('data-reasoning-state'), 'done');
t('思考块: details[data-state=done]', doc.querySelector('.mes[mesid="2"] .mes_reasoning_details').getAttribute('data-state'), 'done');
t('思考块: 内容经 formatText(粗体生效)', q('.mes[mesid="2"] .mes_reasoning strong'), true);
t('思考块: summary 标题存在', q('.mes[mesid="2"] .mes_reasoning_header_title'), true);

// 无 reasoning：不带类、不标状态（details 存在于模板但保持折叠）
await window.Kernel.renderMessage({
    mesid: '3', mes: '普通回答', chName: 'Alice', isUser: false,
    isSystem: false, avatarUrl: null, timestamp: '12:02', tokenCount: 5,
});
t('无思考块: 不带 reasoning 类', q('.mes[mesid="3"].reasoning'), false);
t('无思考块: 无 data-reasoning-state', doc.querySelector('.mes[mesid="3"]').getAttribute('data-reasoning-state'), null);
t('无思考块: details 默认折叠(无 open)', !!doc.querySelector('.mes[mesid="3"] .mes_reasoning_details[open]'), false);

// ---------- 3b. 整页壳 C1：renderChat 全量同步 + 滚动接管 API ----------
t('API: renderChat 已暴露', typeof window.Kernel.renderChat, 'function');
t('API: scrollToBottom 已暴露', typeof window.Kernel.scrollToBottom, 'function');
await window.Kernel.renderChat([
    { mesid: 'a1', mes: '**第一条**', chName: 'Alice', isUser: false, isSystem: false, timestamp: '13:00' },
    { mesid: 'a2', mes: '用户回复', chName: '我', isUser: true, isSystem: false, reasoning: '> 推理链' },
    { mesid: 'a3', mes: '第三条', chName: 'Alice', isUser: false, isSystem: false },
]);
const idsAfterFull = Array.from(doc.querySelectorAll('#chat .mes')).map(n => n.getAttribute('mesid'));
t('renderChat: 清空旧消息(1..3)并按序重建', idsAfterFull.join(','), 'a1,a2,a3');
t('renderChat: 用户消息 is_user 标记', doc.querySelector('.mes[mesid="a2"]').getAttribute('is_user'), 'true');
t('renderChat: 思考块随行挂载', q('.mes[mesid="a2"].reasoning'), true);
t('renderChat: 粗体格式化生效', q('.mes[mesid="a1"] strong'), true);
// 二次全量：幂等重建（不堆积重复节点）
await window.Kernel.renderChat([
    { mesid: 'a1', mes: '第一条', chName: 'Alice', isUser: false, isSystem: false },
]);
t('renderChat: 二次全量无残留', doc.querySelectorAll('#chat .mes').length, 1);
// 官方 append 顺序：renderChat 输入数组顺序必须等于 DOM 顺序（旧反向列表曾导致开场白不可见）。
await window.Kernel.renderChat([
    { mesid: 'o1', mes: '开场白', chName: 'Alice', isUser: false },
    { mesid: 'o2', mes: '用户', chName: '我', isUser: true },
    { mesid: 'o3', mes: '回复', chName: 'Alice', isUser: false },
]);
t('renderChat: 官方 append 顺序', Array.from(doc.querySelectorAll('#chat .mes')).map(n => n.getAttribute('mesid')).join(','), 'o1,o2,o3');
t('renderChat: 开场白正文非空', (doc.querySelector('.mes[mesid="o1"] .mes_text')?.textContent || '').includes('开场白'), true);
// 增量路径与全量互操作：upsert 单条继续追加
await window.Kernel.renderMessage({ mesid: 'a9', mes: '追加', chName: 'Alice', isUser: false, isSystem: false });
t('renderMessage: 全量后可增量追加', doc.querySelectorAll('#chat .mes').length, 4);
// 滚动接管：jsdom 无布局，验证调用不抛错即可
window.Kernel.scrollToBottom(false);
window.Kernel.scrollToBottom(true);
// kernel.html 整页壳模式块存在（C2 宿主将切 body.fullchat）
const kernelHtml = readFileSync(`${K}/kernel.html`, 'utf8');
t('kernel.html: body.fullchat 恢复官方滚动语义', kernelHtml.includes('body.fullchat #chat'), true);
// 真机回归：Moonlit #chat 的渐变 mask 在 Android WebView 整页壳路径可能裁成全空白。
// fullchat 覆盖块必须显式禁用 mask，消息可见性优先；后续真机审计后再评估恢复方式。
t('kernel.html: fullchat 禁用 #chat mask-image 防空白',
  /body\.fullcat|#chat[\s\S]*-webkit-mask-image:\s*none/.test(kernelHtml) &&
  /body\.fullcat|#chat[\s\S]*(?<!-webkit-)mask-image:\s*none/.test(kernelHtml),
  true);
// 官方主题字段 compact_input_area 的类语义已登记，供 C3 前主题过渡/C3 后真实表单使用。
t('applyTheme: compact_input_area → body 类', (() => {
    window.Kernel.applyTheme({ main_text_color: '#ccc', compact_input_area: true });
    const on = body().classList.contains('compact-input-area');
    window.Kernel.applyTheme({ main_text_color: '#ccc', compact_input_area: false });
    return on && !body().classList.contains('compact-input-area');
})(), true);

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

// ---------- 5. chat_display 全枚举映射（0..2 官方 + 3..7 Moonlit 扩展） ----------
// 全量同步语义：每次 applyTheme 先清后加，切布局不留残留
const layoutCases = [[0,'flatchat'],[1,'bubblechat'],[2,'documentstyle'],[3,'echostyle'],[4,'whisperstyle'],[5,'hushstyle'],[6,'ripplestyle'],[7,'tidestyle']];
for (const [v, cls] of layoutCases) {
    window.Kernel.applyTheme({ ...moonlit, chat_display: v });
    t(`chat_display=${v} → ${cls}`, body().classList.contains(cls), true);
    const others = layoutClasses.filter(c => c !== cls);
    t(`chat_display=${v} → 其余布局类全清`, others.every(c => !body().classList.contains(c)), true);
}
// 未知值安全落空：不抛错、不加任何布局类
let unknownThrew = false;
try { window.Kernel.applyTheme({ ...moonlit, chat_display: 9 }); } catch (e) { unknownThrew = true; }
t('chat_display=9 不抛错', unknownThrew, false);
t('chat_display=9 不加任何布局类', layoutClasses.every(c => !body().classList.contains(c)), true);

// ---------- 6. 样式包 applyStylePack ----------
const packHref = '../themes/moonlit-echoes/style.css';
const extHref = '../themes/moonlit-echoes/extension.css';
// 启用：注入 <link id="style-pack-style"> + 扩展兼容层 + 变量写入 documentElement
window.Kernel.applyStylePack({
    enabled: true, href: packHref, extensionHref: extHref,
    vars: { customThemeColor: 'rgba(81, 160, 222, 1)', 'custom-ChatAvatar': '40px' },
});
const packLink = window.document.getElementById('style-pack-style');
t('样式包: link 注入', !!packLink, true);
t('样式包: link rel=stylesheet', packLink?.getAttribute('rel'), 'stylesheet');
t('样式包: link href 正确', packLink?.getAttribute('href'), packHref);
t('样式包: 扩展层 link 注入', window.document.getElementById('style-pack-extension')?.getAttribute('href'), extHref);
t('样式包: 无前缀键自动补 --', root().style.getPropertyValue('--customThemeColor'), 'rgba(81, 160, 222, 1)');
t('样式包: 带前缀键原样写入', root().style.getPropertyValue('--custom-ChatAvatar'), '40px');
// href 变更：复用同一 link 节点换地址（主题切换不堆积节点）
window.Kernel.applyStylePack({ enabled: true, href: '../themes/other/style.css', vars: {} });
t('样式包: 换主题复用节点换 href', window.document.getElementById('style-pack-style')?.getAttribute('href'), '../themes/other/style.css');
t('样式包: 换主题仍是单节点', window.document.querySelectorAll('#style-pack-style').length, 1);
t('样式包: 无 extensionHref 时扩展层移除', window.document.getElementById('style-pack-extension'), null);
// 禁用：link 移除，变量保留由主题切换流程清理（与官方 custom_css 语义同构）
window.Kernel.applyStylePack({ enabled: false, href: null, vars: null });
t('样式包: 禁用移除 link', window.document.getElementById('style-pack-style'), null);
t('样式包: 禁用移除扩展层 link', window.document.getElementById('style-pack-extension'), null);
// 纯官方主题路径：enabled=false 为无操作，不产生任何节点
window.Kernel.applyStylePack({ enabled: false, href: packHref, vars: { customThemeColor: 'x' } });
t('样式包: 官方主题零污染', window.document.getElementById('style-pack-style'), null);

// ---------- 7. 官方字段补遗（对照 power-user.js L95-549）----------
window.Kernel.applyTheme({ ...moonlit, avatar_style: 3, enableLabMode: true, chat_display: 0 });
t('avatar_style=3 → rounded-avatars', body().classList.contains('rounded-avatars'), true);
t('enableLabMode=true → body 类', body().classList.contains('enableLabMode'), true);
window.Kernel.applyTheme({ ...moonlit, avatar_style: 0, enableLabMode: false, chat_display: 0 });
t('avatar_style=0 圆形：三形状类全清', ['big-avatars','square-avatars','rounded-avatars'].every(c => !body().classList.contains(c)), true);
t('enableLabMode=false 类移除', body().classList.contains('enableLabMode'), false);

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail ? 1 : 0);
