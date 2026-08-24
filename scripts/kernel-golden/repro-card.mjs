import { JSDOM } from 'jsdom';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
const K = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'assets', 'kernel');
const dom = new JSDOM(`<!DOCTYPE html><html><body><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;
for (const f of ['js/showdown.min.js','js/css-tools.min.js','js/dompurify.min.js','js/highlight.min.js','js/st-extensions.js','js/render.js'])
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));

const card = `*店主微笑着擦了杯子。*

<style>
.panel { border: 1px solid #8549cc; border-radius: 8px; padding: 10px; background: rgba(30,20,40,.7); }
.panel h3 { color: #c9a6ff; margin: 0 0 6px; }
.menu_button { cursor: pointer; }
</style>
<div class="panel">
<h3>🌙 月下酒馆 — 状态</h3>
<table><tr><td>体力</td><td>82/100</td></tr><tr><td>金币</td><td>1200</td></tr></table>
<div class="menu_button">查看任务</div>
</div>`;

const out = window.Kernel.formatText(card, {});
console.log("==== 关键检查 ====");
console.log("div.panel(custom-panel) 存在:", out.includes('custom-panel'));
console.log("<table> 存在:", out.includes('<table>'));
console.log("style 解码+作用域存在:", out.includes('<style>') && out.includes('.mes_text .custom-panel'));
console.log("menu_button 类保留:", out.toLowerCase().includes('menu_button'));
console.log("==== 输出 HTML ====");
console.log(out);
