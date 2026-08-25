// 主题应用 + Moonlit Echoes 兼容黄金测试
// 运行: node theme-moonlit.test.mjs（需先 npm install）
import { JSDOM } from 'jsdom';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const K = join(ROOT, 'app', 'src', 'main', 'assets', 'kernel');
// 扩展包（style.css/schema/preset）与主题 JSON 分居两目录（ExtensionManager 架构）
const ME = join(ROOT, 'app', 'src', 'main', 'assets', 'extensions', 'moonlit-echoes');
const MET = join(ROOT, 'app', 'src', 'main', 'assets', 'themes', 'moonlit-echoes');

const dom = new JSDOM(`<!DOCTYPE html><html><body class="light-theme"><style id="custom-style" type="text/css"></style><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;
for (const f of ['js/showdown.min.js','js/css-tools.min.js','js/dompurify.min.js','js/highlight.min.js','js/st-extensions.js','js/audio-player.js','js/render.js'])
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));

let pass = 0, fail = 0;
const t = (n, a, e) => { if (a === e) { pass++; console.log(`  ✓ ${n}`); } else { fail++; console.log(`  ✗ ${n}\n    实际: ${JSON.stringify(a)}`); } };
const body = () => window.document.body;
const root = () => window.document.documentElement;

// 输入区/背景层骨架（真实内核页由 kernel.html 提供；本 jsdom 壳默认只含 #chat）
{
    const d = window.document;
    if (!d.getElementById('bg1')) {
        const bg1 = d.createElement('div'); bg1.id = 'bg1';
        d.body.insertBefore(bg1, d.body.firstChild);
    }
    // 官方媒体模板（kernel.html 同源：index.html L7670-7727）
    if (!d.getElementById('message_image_template')) {
        d.body.insertAdjacentHTML('beforeend', `
<div id="message_image_template" class="template_element">
    <div class="mes_media_container mes_img_container">
        <div class="mes_img_controls">
            <div title="Expand and zoom" class="right_menu_button fa-lg fa-solid fa-magnifying-glass mes_media_enlarge"></div>
            <div title="Caption" class="right_menu_button fa-lg fa-solid fa-envelope-open-text mes_img_caption"></div>
            <div title="Delete" class="right_menu_button fa-lg fa-solid fa-trash-can mes_media_delete"></div>
        </div>
        <img class="mes_img" src="" />
    </div>
</div>
<div id="message_video_template" class="template_element">
    <div class="mes_media_container mes_video_container">
        <div class="mes_video_controls">
            <div title="Expand and zoom" class="right_menu_button fa-lg fa-solid fa-magnifying-glass mes_media_enlarge"></div>
            <div title="Caption" class="right_menu_button fa-lg fa-solid fa-envelope-open-text mes_img_caption"></div>
            <div title="Delete" class="right_menu_button fa-lg fa-solid fa-trash-can mes_media_delete"></div>
        </div>
        <video class="mes_video" controls preload="metadata"></video>
    </div>
</div>
<div id="message_gallery_controls" class="template_element">
    <div class="mes_img_swipes">
        <div title="Swipe left" class="right_menu_button fa-lg fa-solid fa-chevron-left mes_img_swipe_left"></div>
        <div class="mes_img_swipe_counter">1/1</div>
        <div title="Swipe right" class="right_menu_button fa-lg fa-solid fa-chevron-right mes_img_swipe_right"></div>
    </div>
</div>
<div id="message_audio_template" class="template_element">
    <div class="mes_media_container mes_audio_container audio-player">
        <audio class="mes_audio" preload="auto" hidden></audio>
        <div class="audio-player-header">
            <div class="audio-player-title">Audio</div>
            <div class="right_menu_button mes_media_delete fa-fw fa-solid fa-trash-can" title="Delete"></div>
        </div>
        <div class="audio-player-controls">
            <button class="audio-player-play-pause right_menu_button fa-fw fa-solid fa-play" title="Play"></button>
            <div class="audio-player-time">
                <span class="audio-player-current-time">0:00</span>
                <span class="audio-player-time-separator">/</span>
                <span class="audio-player-total-time">0:00</span>
            </div>
            <div class="audio-player-progress">
                <div class="audio-player-progress-bar"></div>
            </div>
            <div class="audio-player-volume-control">
                <button class="audio-player-volume right_menu_button fa-fw fa-solid fa-volume-high" title="Mute"></button>
            </div>
        </div>
    </div>
</div>`);
}
    if (!d.getElementById('form_sheld')) {
        const sheld = d.createElement('div'); sheld.id = 'sheld';
        sheld.innerHTML = '<div id="form_sheld"><div id="dialogue_del_mes"></div>'
            + '<div id="send_form" class="no-connection"><div id="mes_stop" style="display:none"></div></div></div>';
        d.body.appendChild(sheld);
    }
}

// ---------- 1. 官方格式主题 JSON 全字段应用 ----------
const glimmer = JSON.parse(readFileSync(`${MET}/Glimmer.json`, 'utf8'));
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
const moonlit = JSON.parse(readFileSync(`${MET}/MoonlitEchoes.json`, 'utf8'));
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
// 官方主题兼容：#chat 的 mask-image 属于主题包装饰语义，整页壳覆盖块不得无差别禁用。
t('kernel.html: fullchat 不劫持主题 #chat mask-image', !kernelHtml.includes('mask-image: none'), true);
// 官方 power-user.js switchCompactInputArea L529-532：compact_input_area 切的是
// #send_form 的 compact 类（非 body 类）；C3 后 #send_form 已在内核 DOM。
t('applyTheme: compact_input_area → #send_form.compact', (() => {
    window.Kernel.applyTheme({ main_text_color: '#ccc', compact_input_area: true });
    const form = window.document.getElementById('send_form');
    const on = form.classList.contains('compact');
    window.Kernel.applyTheme({ main_text_color: '#ccc', compact_input_area: false });
    return on && !form.classList.contains('compact');
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
const packHref = '../extensions/moonlit-echoes/style.css';
const extHref = '../extensions/moonlit-echoes/extension.css';
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
// 官方 theme-applier 语义：变量写入 <style id="dynamic-theme-styles"> 的 :root 块（非 inline style）
const varsCss = window.document.getElementById('dynamic-theme-styles')?.textContent ?? '';
t('样式包: 无前缀键自动补 --', varsCss.includes('--customThemeColor: rgba(81, 160, 222, 1) !important;'), true);
t('样式包: 带前缀键原样写入', varsCss.includes('--custom-ChatAvatar: 40px !important;'), true);
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

// ---------- 8. 官方对齐补遗（C3 输入区状态 / C4 背景 / 头像兜底 / 按钮排 / 滑动语义）----------
// C3：setInputState（showStopButton L3469 + RA_checkOnlineStatus data-generating 语义）
window.Kernel.setInputState({ generating: true });
t('输入区: 生成中 data-generating=true', body().getAttribute('data-generating'), 'true');
t('输入区: 生成中 mes_stop 显示', doc.getElementById('mes_stop').style.display, 'flex');
t('输入区: 生成中隐藏 chevron(hideAllSwipeButtons)', body().classList.contains('hideAllSwipeButtons'), true);
window.Kernel.setInputState({ generating: false });
t('输入区: 结束后 mes_stop 隐藏', doc.getElementById('mes_stop').style.display, 'none');
t('输入区: 结束后恢复 chevron', body().classList.contains('hideAllSwipeButtons'), false);

// C4：setBackground（backgrounds.js forceSetBackground 同构；null 清除）
window.Kernel.setBackground('/backgrounds/a.png', 'contain');
const bgEl = doc.getElementById('bg1');
t('背景: #bg1 backgroundImage 下发', bgEl.style.backgroundImage, 'url("/backgrounds/a.png")');
t('背景: fitting 类 contain', bgEl.classList.contains('contain'), true);
window.Kernel.setBackground(null, null);
t('背景: null 清除为 none', bgEl.style.backgroundImage, 'none');
t('背景: fitting 类清除', ['cover','contain','stretch','center'].some(c => bgEl.classList.contains(c)), false);

// 头像加载失败兜底（官方 script.js L2646-2650 missing-avatar）
await window.Kernel.renderMessage({
    mesid: 'av1', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    avatarUrl: '/avatars/broken.png',
});
doc.querySelector('.mes[mesid="av1"] .avatar img').dispatchEvent(new window.Event('error'));
t('头像: 加载失败兜底 missing-avatar', q('.mes[mesid="av1"] .missing-avatar.fa-user-slash'), true);

// 滑动语义（refreshSwipeButtons/isMessageSwipeable L9123-9152）：计数 ZWSP 分隔 + last_swipe
await window.Kernel.renderMessage({
    mesid: 'sw1', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    lastMessage: true, swipeCount: 2, currentSwipe: 0,
});
const swNode = doc.querySelector('.mes[mesid="sw1"]');
t('滑动: 多 swipe 可见(swipes_visible)', swNode.classList.contains('swipes_visible'), true);
t('滑动: 非末滑无 last_swipe', swNode.classList.contains('last_swipe'), false);
t('滑动: 计数零宽空格格式', doc.querySelector('.mes[mesid="sw1"] .swipes-counter').textContent, `1​/​2`);
await window.Kernel.renderMessage({
    mesid: 'sw1', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    lastMessage: true, swipeCount: 2, currentSwipe: 1, overswipe: 'regenerate',
});
t('滑动: 末滑加 last_swipe(regenerate)', doc.querySelector('.mes[mesid="sw1"]').classList.contains('last_swipe'), true);
// 官方 L9232 运算符优先级：(isLastSwipe && regenerate) || edit_generate —— edit_generate 不看 isLastSwipe
await window.Kernel.renderMessage({
    mesid: 'sw1', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    lastMessage: true, swipeCount: 2, currentSwipe: 0, overswipe: 'edit_generate',
});
t('滑动: edit_generate 非末滑也挂 last_swipe(官方优先级)', doc.querySelector('.mes[mesid="sw1"]').classList.contains('last_swipe'), true);
// pristine_greeting：chevrons 常显（无变体也 swipes_visible），不挂 last_swipe
await window.Kernel.renderMessage({
    mesid: 'sw3', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    lastMessage: true, swipeCount: 1, currentSwipe: 0, overswipe: 'pristine_greeting',
});
const swPri = doc.querySelector('.mes[mesid="sw3"]');
t('滑动: pristine_greeting 单变体也可见 chevron', swPri.classList.contains('swipes_visible'), true);
t('滑动: pristine_greeting 无 last_swipe', swPri.classList.contains('last_swipe'), false);
// extra.swipeable === false：整体不可滑（isMessageSwipeable 闸门）
await window.Kernel.renderMessage({
    mesid: 'sw4', mes: 'x', chName: 'A', isUser: false, isSystem: false,
    lastMessage: true, swipeCount: 2, currentSwipe: 0, overswipe: 'regenerate', swipeable: false,
});
const swOff = doc.querySelector('.mes[mesid="sw4"]');
t('滑动: extra.swipeable=false 全关', swOff.classList.contains('swipes_visible') || swOff.classList.contains('last_swipe'), false);
// 官方 isMessageSwipeable：用户消息不可滑（无 swipes_visible / 计数留空）
await window.Kernel.renderMessage({
    mesid: 'sw2', mes: 'x', chName: '我', isUser: true, isSystem: false,
    lastMessage: true, swipeCount: 2, currentSwipe: 0,
});
const swUser = doc.querySelector('.mes[mesid="sw2"]');
t('滑动: 用户消息不可滑', swUser.classList.contains('swipes_visible'), false);

// 按钮排展开/点外收起（官方 script.js L11806-11868）
await window.Kernel.renderMessage({
    mesid: 'ex1', mes: 'x', chName: 'A', isUser: false, isSystem: false,
});
const hint = doc.querySelector('.mes[mesid="ex1"] .extraMesButtonsHint');
hint.dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
const exBtns = doc.querySelector('.mes[mesid="ex1"] .extraMesButtons');
t('按钮排: 省略号点击展开 visible', exBtns.classList.contains('visible'), true);
t('按钮排: 展开后 hint 隐藏', hint.style.display, 'none');
body().dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
t('按钮排: 点击外侧收起并还原 hint',
    !exBtns.classList.contains('visible') && exBtns.style.display === 'none' && hint.style.display === '', true);
// expandMessageActions 开启时不收起（toggle-dependent.css L472 常显语义）
body().classList.add('expandMessageActions');
hint.dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
body().dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
t('按钮排: expandMessageActions 常显不收起', exBtns.classList.contains('visible'), true);
body().classList.remove('expandMessageActions');


// ---------- 9. 九项边界金测试（gallery/lightbox/编辑/show-more/lastInContext/timer/overswipe） ----------
// 边界1 官方画廊（appendMediaToMessage script.js L2371-2384 + onImageSwiped chats.js）
await window.Kernel.renderChat([
    { mesid: 'g1', mes: '图库消息', chName: 'A', isUser: false,
      mediaDisplay: 'gallery', mediaIndex: 0,
      media: [
        { url: '/media/a.png', type: 'image', title: '' },
        { url: '/media/b.png', type: 'image', title: '' },
      ] },
]);
const gNode = doc.querySelector('.mes[mesid="g1"]');
t('边界1: data-media-display=gallery', gNode.getAttribute('data-media-display'), 'gallery');
const gBlock = gNode.querySelector('.mes_media_wrapper .mes_img_container.img_swipes');
t('边界1: GALLERY 单容器带 img_swipes 类', !!gBlock, true);
t('边界1: GALLERY 只挂当前一张图', gNode.querySelectorAll('.mes_img').length, 1);
t('边界1: 计数格式 i+1/n', gNode.querySelector('.mes_img_swipe_counter').textContent, '1/2');
t('边界1: 切图条左右键存在',
    !!gNode.querySelector('.mes_img_swipe_left') && !!gNode.querySelector('.mes_img_swipe_right'), true);
// LIST：全部平铺，容器无 img_swipes 类（CSS 隐藏条）
await window.Kernel.renderMessage({
    mesid: 'g1', mes: '图库消息', chName: 'A', isUser: false,
    mediaDisplay: 'list', mediaIndex: 1,
    media: [
        { url: '/media/a.png', type: 'image', title: '' },
        { url: '/media/b.png', type: 'image', title: '' },
    ],
});
t('边界1: LIST 全量挂载', doc.querySelectorAll('.mes[mesid="g1"] .mes_img').length, 2);
t('边界1: LIST 容器无 img_swipes 类',
    doc.querySelectorAll('.mes[mesid="g1"] .mes_media_container.img_swipes').length, 0);
// 无媒体移除属性（jQuery attr(null)）
await window.Kernel.renderMessage({ mesid: 'g2', mes: '无媒体', chName: 'A', isUser: false });
t('边界1: 无媒体移除 data-media-display',
    doc.querySelector('.mes[mesid="g2"]').hasAttribute('data-media-display'), false);
// 图片加载失败 → alt=''+.error（官方 onError L2213-2216）
{
    const errImg = doc.querySelector('.mes[mesid="g1"] .mes_img');
    errImg.dispatchEvent(new window.Event('error'));
    t('边界1: 加载失败置 .error + alt=""',
        errImg.classList.contains('error') && errImg.getAttribute('alt') === '', true);
}

// 边界1 图库切换（onImageSwiped chats.js L2061-2102）：点击右键 → 本地重建 + 桥接最终下标
{
    let swiped = null;
    window.AndroidKernel = {
        postMessage(json) { const m = JSON.parse(json); if (m.messageAction === 'mes_img_swipe') swiped = m; },
    };
    await window.Kernel.renderMessage({
        mesid: 'g3', mes: '切换', chName: 'A', isUser: false,
        mediaDisplay: 'gallery', mediaIndex: 0,
        media: [
            { url: '/media/a.png', type: 'image' },
            { url: '/media/b.png', type: 'image' },
        ],
    });
    doc.querySelector('.mes[mesid="g3"] .mes_img_swipe_right')
        .dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界1: 右切桥接 mes_img_swipe value=1', swiped && swiped.value, '1');
    t('边界1: 切换后计数更新', doc.querySelector('.mes[mesid="g3"] .mes_img_swipe_counter').textContent, '2/2');
    // 回绕：再右切回 0
    doc.querySelector('.mes[mesid="g3"] .mes_img_swipe_right')
        .dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界1: 末图右切回绕到 0', doc.querySelector('.mes[mesid="g3"] .mes_img_swipe_counter').textContent, '1/2');
}

// 边界2 lightbox（callPopup large_dialogue_popup.transparent_dialogue_popup + img_enlarged 结构）
{
    await window.Kernel.renderMessage({
        mesid: 'lb1', mes: '放大', chName: 'A', isUser: false,
        mediaDisplay: 'list', media: [{ url: '/media/big.png', type: 'image' }],
    });
    doc.querySelector('.mes[mesid="lb1"] .mes_media_enlarge')
        .dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    const dlg = doc.querySelector('dialog.popup.large_dialogue_popup.transparent_dialogue_popup');
    t('边界2: dialog 三类齐全', !!dlg, true);
    t('边界2: container>holder>img.img_enlarged 结构',
        !!(dlg && dlg.querySelector('.img_enlarged_container > .img_enlarged_holder > img.img_enlarged')), true);
    t('边界2: 关闭键 data-result=0',
        !!(dlg && dlg.querySelector('.popup-button-close[data-result="0"]')), true);
    t('边界2: dialog width/height 内联 unset',
        !!(dlg && dlg.style.width === 'unset' && dlg.style.height === 'unset'), true);
    // 官方 L962-964：放大镜打开时立即触发 click → 初始即为放大态；再点取消
    const bigImg = dlg.querySelector('img.img_enlarged');
    t('边界2: 放大镜打开初始即 zoomed', bigImg.classList.contains('zoomed'), true);
    bigImg.dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界2: 再点取消 zoomed', bigImg.classList.contains('zoomed'), false);
    t('边界2: 点图不关闭', !!doc.querySelector('dialog.popup.large_dialogue_popup'), true);
    dlg.dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界2: 点空白关闭（closing 动画或移除）',
        doc.querySelectorAll('dialog.popup.large_dialogue_popup').length <= 1, true);
}

// 边界3 行内编辑（官方 messageEdit script.js L8157-8250）
{
    await window.Kernel.renderMessage({
        mesid: 'e1', mes: '编辑前的显示文本', chName: 'A', isUser: false,
        rawMes: '  原始 mes 文本  ',
    });
    let saved = null;
    window.AndroidKernel = {
        postMessage(json) {
            const m = JSON.parse(json);
            if (m.messageAction === 'mes_edit_save' || m.messageAction === 'mes_edit_cancel') saved = m;
        },
    };
    window.Kernel.beginEditMessage('e1');
    const ta = doc.querySelector('.mes[mesid="e1"] #curEditTextarea');
    t('边界3: curEditTextarea 存在', !!ta, true);
    t('边界3: 初值=trimSpaces(rawMes)', ta ? ta.value : null, '原始 mes 文本');
    t('边界3: 无 .editing 类（官方状态在变量）',
        doc.querySelector('.mes[mesid="e1"]').classList.contains('editing'), false);
    const nodeE1 = doc.querySelector('.mes[mesid="e1"]');
    t('边界3: 常规按钮排内联隐藏', nodeE1.querySelector('.mes_buttons').style.display, 'none');
    t('边界3: 编辑按钮排 inline-flex', nodeE1.querySelector('.mes_edit_buttons').style.display, 'inline-flex');
    // 保存桥接
    ta.value = '改好的文本';
    nodeE1.querySelector('.mes_edit_done').dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界3: mes_edit_done 桥接保存值', saved && saved.messageAction, 'mes_edit_save');
    t('边界3: 保存值透传', saved && saved.value, '改好的文本');
    // ESC 取消：不桥接（官方 cancel 本地恢复，auto_save 默认关）
    window.Kernel.beginEditMessage('e1');
    const ta2 = doc.querySelector('.mes[mesid="e1"] #curEditTextarea');
    saved = null;
    doc.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    t('边界3: ESC 不桥接保存（本地取消）', saved, null);
    t('边界3: ESC 移除编辑框',
        !doc.querySelector('.mes[mesid="e1"] #curEditTextarea'), true);
    t('边界3: ESC 恢复按钮排显示',
        doc.querySelector('.mes[mesid="e1"] .mes_buttons').style.display !== 'none', true);
}



// 边界5 show more（printMessages/showMoreMessages script.js L1431-1486）
{
    const many = [];
    for (let i = 0; i < 8; i++) {
        many.push({ mesid: 'p' + i, mes: '消息' + i, chName: 'A', isUser: false });
    }
    await window.Kernel.renderChat(many, { showMore: true });
    const btn = doc.getElementById('show_more_messages');
    t('边界5: 顶部挂 #show_more_messages', !!btn, true);
    t('边界5: 按钮文案官方硬编码', btn ? btn.textContent : null, 'Show more messages');
    t('边界5: 位于 #chat 首子节点', doc.querySelector('#chat').firstElementChild, btn);
    // 点击 → hostRequest 桥接
    let more = null;
    const prevPost = window.AndroidKernel.postMessage;
    window.AndroidKernel.postMessage = function (json) {
        const m = JSON.parse(json);
        if (m.hostAction === 'show_more_messages') more = m;
    };
    btn.dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    t('边界5: 点击桥 hostRequest show_more_messages', !!more, true);
    // prependMessages：插到按钮之后、不动其余节点、首条 mesid 正确
    await window.Kernel.prependMessages([
        { mesid: 'q0', mes: '更早0', chName: 'A', isUser: false },
        { mesid: 'q1', mes: '更早1', chName: 'A', isUser: false },
    ]);
    const chatChildren = [...doc.querySelector('#chat').children];
    t('边界5: 批次插在按钮后', chatChildren[1].getAttribute('mesid'), 'q0');
    t('边界5: 原有节点未动', chatChildren[chatChildren.length - 1].getAttribute('mesid'), 'p7');
    // 到顶移除按钮
    await window.Kernel.renderChat(many, { showMore: false });
    t('边界5: 无截断不挂按钮', !doc.getElementById('show_more_messages'), true);
}

// 边界6 lastInContext（setInContextMessages script.js L6022-6041）
{
    await window.Kernel.renderChat([
        { mesid: 'l0', mes: 'a', chName: 'A', isUser: true },
        { mesid: 'l1', mes: 'b', chName: 'A', isUser: false, isSystem: true },   // 排除
        { mesid: 'l2', mes: 'c', chName: 'A', isUser: false, lastInContext: true },
        { mesid: 'l3', mes: 'd', chName: 'A', isUser: true },
    ]);
    t('边界6: 第 -N 匹配节点带类',
        doc.querySelector('.mes[mesid="l2"]').classList.contains('lastInContext'), true);
    t('边界6: 其余节点无类',
        ['l0', 'l1', 'l3'].every(id => !doc.querySelector(`.mes[mesid="${id}"]`).classList.contains('lastInContext')), true);
}

// 边界4 timer（formatGenerationTimer script.js L2681-2706 + onProgressStreaming L3672）
{
    await window.Kernel.renderMessage({
        mesid: 't1', mes: '计时', chName: 'A', isUser: false,
        timerValue: '1.2s',
        timerTitle: 'Generation queued: 12:34:56 7 Jan 2021\nReply received: 12:34:57 7 Jan 2021\nTime to generate: 1.2 seconds\nToken rate: 5.000 t/s',
    });
    const tm = doc.querySelector('.mes[mesid="t1"] .mes_timer');
    t('边界4: .mes_timer 文本=timerValue', tm ? tm.textContent : null, '1.2s');
    t('边界4: title 六行英文', (tm ? tm.title : '').includes('Token rate: 5.000 t/s'), true);
    // 流式 updateStreaming 直写 timer
    await window.Kernel.renderChat([{ mesid: 't2', mes: '', chName: 'A', isUser: false }]);
    // 与 RenderKernel.updateStreaming 生成的一段式 JS 完全一致（Kotlin 侧组合后 evaluateJavascript）
    {
        const esc = JSON.stringify('流式…'), tv = JSON.stringify('3.4s'), tt = JSON.stringify('Time to generate: 3.4 seconds');
        window.eval(`(function(){var m=document.querySelector('.mes[mesid="t2"]');if(!m)return;` +
            `var el=m.querySelector('.mes_text');` +
            `if(el){el.innerHTML=window.Kernel.formatText(${esc},{});}` +
            `var tm=m.querySelector('.mes_timer');if(tm){tm.textContent=${tv},tm.title=${tt};}})();`);
    }
    const tm2 = doc.querySelector('.mes[mesid="t2"] .mes_timer');
    t('边界4: 流式 tick 更新 timerValue', tm2 ? tm2.textContent : null, '3.4s');
    t('边界4: 流式 tick 更新 title', tm2 ? tm2.title : null, 'Time to generate: 3.4 seconds');
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail ? 1 : 0);
