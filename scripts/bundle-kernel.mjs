#!/usr/bin/env node
/** 把 kernel.html 内联为单文件 kernel-bundle.html：
 *  - 4 个 <link> 样式（css-bundle/mobile-styles/toggle-dependent/popup）+ 2 个 webfont
 *    stylesheet + 3 个 fontawesome css 全部内联，css 内相对 url() 重写为相对内核根路径
 *  - 9 个 <script src> 全部内联（showdown/dompurify/highlight/render.js 等）
 *  - message-template.html 直接注入为 <template> 节点（消除运行时 fetch）
 *  冷启动从 17+ 次资源请求（每次走 shouldInterceptRequest + assets IO）降为 1 次主文档。
 *  重跑本脚本再生成；kernel.html 保持源文件不动。 */
import { readFileSync, writeFileSync } from 'fs';
import { dirname, join, relative } from 'path';
import { fileURLToPath } from 'url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'app/src/main/assets/kernel');
const html = readFileSync(join(root, 'kernel.html'), 'utf8');

/** 内联 CSS：把 css 文本里相对 url() 重写为相对 kernel-bundle.html 的路径。
 *  页面位于 /assets/kernel/kernel-bundle.html——内联后基准变为内核根目录，
 *  故 resolved 直接作为相对路径（此前误加 assets/kernel/ 前缀 → 双重前缀 404）。 */
function inlineCss(href) {
    let css = readFileSync(join(root, href), 'utf8');
    const base = join(root, dirname(href));
    css = css.replace(/url\(\s*['"]?([^'")]+)['"]?\s*\)/g, (m, url) => {
        if (/^(data:|https?:|\/|#)/.test(url)) return m; // 绝对/data/协议/根相对不动
        const resolved = relative(root, join(base, url)).replace(/\\/g, '/');
        return `url(${resolved})`;
    });
    return `/* ===== ${href} ===== */\n${css}`;
}

/** 内联 JS：保留原文（含 //# sourceMappingURL 注释无害） */
function inlineJs(src) {
    const js = readFileSync(join(root, src), 'utf8');
    return `// ===== ${src} =====\n${js}`;
}

let out = html;

// 1) <link rel="stylesheet" href="..."> → <style> 内联（保持原 DOM 顺序与层叠）
out = out.replace(/<link rel="stylesheet" href="([^"]+)">\n?/g, (m, href) =>
    `<style>\n${inlineCss(href)}\n</style>\n`,
);

// 2) <script src="..."></script> → 内联（保持执行顺序）
out = out.replace(/<script src="([^"]+)"><\/script>\n?/g, (m, src) =>
    `<script>\n${inlineJs(src)}\n</script>\n`,
);

// 3) message-template.html 注入 <body> 尾部 <template id="message_template_html">：
//    render.js loadTemplate 优先读该节点，消除运行时 fetch 往返。
//    必须锚定 HTML 结构性的最后一个 </body>——内联的 showdown.min.js 源码字符串里
//    也有 </body>（它生成 HTML 文档的代码），replace 首个匹配会把模板注进 JS 字符串
//    中间 → 语法崩溃 → showdown 未定义 → render.js 启动即抛 → kernelReady 永不触发
//    → 宿主 30s 超时整页空白（9.2c 事故根因，勿再用 str.replace）。
const tpl = readFileSync(join(root, 'official/message-template.html'), 'utf8');
const bodyClose = out.lastIndexOf('</body>');
if (bodyClose < 0) throw new Error('kernel.html 缺少 </body>');
out = out.slice(0, bodyClose) +
    `    <template id="message_template_html">\n${tpl.trim()}\n    </template>\n` +
    out.slice(bodyClose);

// 4) webfonts 只保留 woff2 源（Android WebView 全版本支持 woff2；woff fallback 是浏览器
//    兼容产物，App 无第二引擎）。内联的 webfont stylesheet 顺带瘦身。
out = out.replace(/src:\s*url\('([^']+\.woff2)'\)\s*format\('woff2'\),\s*url\('([^']+\.woff)'\)\s*format\('woff'\)/g,
    "src: url('$1') format('woff2')");

const banner = `<!-- 自动生成:scripts/bundle-kernel.mjs——勿手改。单文件 = kernel.html + 全部 CSS/JS 内联 -->\n`;
writeFileSync(join(root, 'kernel-bundle.html'), banner + out);
const kb = (readFileSync(join(root, 'kernel-bundle.html')).length / 1024).toFixed(0);
console.log(`✓ kernel-bundle.html 已生成（${kb}KB 单请求）`);

// 5) 自校验（防 9.2c 重演）：逐 <script> 块语法检查 + 模板标签不得落在脚本块内 +
//    字体 url 不得双重前缀。任一失败非零退出，让 CI/构建当场拦下坏包。
const produced = banner + out;
{
    const ranges = []; // 每个 <script>…</script> 块的 [start, end)
    const blocks = [];
    const re = /<script>([\s\S]*?)<\/script>/g;
    for (let m; (m = re.exec(produced)) !== null;) {
        ranges.push([m.index, m.index + m[0].length]);
        blocks.push(m[1]);
    }
    let fail = 0;
    blocks.forEach((code, i) => {
        try { new Function(code); } catch (e) {
            fail++;
            console.error(`✗ script 块 ${i + 1} 语法错误: ${e.message}`);
        }
    });
    // 模板标签必须是 HTML 结构节点（不在任何 script 块内）。
    // render.js 源码注释/代码里也合法出现该 id 字符串（块内），故取最后一次出现判定。
    const tplPos = produced.lastIndexOf('<template id="message_template_html">');
    if (tplPos < 0) {
        fail++;
        console.error('✗ 消息模板缺失');
    } else if (ranges.some(([s, e]) => tplPos >= s && tplPos < e)) {
        fail++;
        console.error('✗ 消息模板被注进 script 块内（</body> 锚定错误）');
    }
    if (produced.includes('url(assets/kernel/assets/')) {
        fail++;
        console.error('✗ 字体 url 双重前缀（inlineCss 重写错误）');
    }
    if (fail > 0) {
        console.error(`✗ kernel-bundle 自校验失败（${fail} 处），已生成的文件不可用`);
        process.exit(1);
    }
    console.log(`✓ 自校验通过：${blocks.length} 个内联脚本块语法完好，模板注入位置正确`);
}
