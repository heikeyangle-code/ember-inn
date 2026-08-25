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

/** 内联 CSS：把 css 文本里相对 url() 重写为相对 kernel.html 的路径（css 原位置 → 内联后基准变了） */
function inlineCss(href) {
    let css = readFileSync(join(root, href), 'utf8');
    const base = join(root, dirname(href));
    css = css.replace(/url\(\s*['"]?([^'")]+)['"]?\s*\)/g, (m, url) => {
        if (/^(data:|https?:|\/|#)/.test(url)) return m; // 绝对/data/协议/根相对不动
        const resolved = relative(root, join(base, url)).replace(/\\/g, '/');
        return `url(assets/kernel/${resolved})`;
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
//    render.js loadTemplate 优先读该节点，消除运行时 fetch 往返
const tpl = readFileSync(join(root, 'official/message-template.html'), 'utf8');
out = out.replace('</body>', `    <template id="message_template_html">\n${tpl.trim()}\n    </template>\n</body>`);

// 4) webfonts 只保留 woff2 源（Android WebView 全版本支持 woff2；woff fallback 是浏览器
//    兼容产物，App 无第二引擎）。内联的 webfont stylesheet 顺带瘦身。
out = out.replace(/src:\s*url\('([^']+\.woff2)'\)\s*format\('woff2'\),\s*url\('([^']+\.woff)'\)\s*format\('woff'\)/g,
    "src: url('$1') format('woff2')");

const banner = `<!-- 自动生成:scripts/bundle-kernel.mjs——勿手改。单文件 = kernel.html + 全部 CSS/JS 内联 -->\n`;
writeFileSync(join(root, 'kernel-bundle.html'), banner + out);
const kb = (readFileSync(join(root, 'kernel-bundle.html')).length / 1024).toFixed(0);
console.log(`✓ kernel-bundle.html 已生成（${kb}KB 单请求）`);
