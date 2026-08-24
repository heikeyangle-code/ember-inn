#!/usr/bin/env node
/** 把 official/style.css 的 @import 链内联为单文件 css-bundle.css(逐字保留各文件内容,
 *  仅去掉 @import 行并按原顺序拼接)——消除内核页冷启动的 17 次串行样式请求。 */
import { readFileSync, writeFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'app/src/main/assets/kernel/official');
const stylePath = join(root, 'style.css');
let css = readFileSync(stylePath, 'utf8');
const imported = [];
css = css.replace(/^@import url\(css\/([\w.-]+\.css)\);\n/gm, (_, f) => {
    imported.push(f);
    return `/* ===== css/${f} ===== */\n` + readFileSync(join(root, 'css', f), 'utf8') + '\n';
});
if (!imported.length) throw new Error('no @import resolved');
const banner = `/* 自动生成:scripts/bundle-official-css.mjs——勿手改。基线 style.css + ${imported.length} 个导入,单请求加载 */\n`;
writeFileSync(join(root, 'css-bundle.css'), banner + css);
console.log(`✓ css-bundle.css 已生成(${imported.length} 个导入内联)`);
