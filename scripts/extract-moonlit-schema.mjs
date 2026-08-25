#!/usr/bin/env node
/**
 * 从官方 Moonlit Echoes theme-settings.js 提取设置定义 → settings-schema.json。
 *
 * 用途：官方扩展的设置面板由 theme-settings.js 的定义数组驱动（类型/标签/默认值/
 * 选项/滑条范围/cssBlock）。本脚本把这份定义原样提取为 JSON schema，随样式包内置——
 * App 侧 UI 由 schema 驱动渲染类型化控件（颜色/滑条/开关/下拉/文本），不硬编码。
 *
 * 提取规则：
 *   - t`X` 模板标签 → "X"（i18n 原文；App 侧另配中文标签表覆盖）
 *   - cssBlock 模板字符串 → 普通字符串（已验证全文件无 ${} 插值，静态安全）
 *   - 字段透传：type/varId/displayText/default/min/max/step/options/category/description/cssBlock
 *
 * 官方源：src/config/theme-settings.js（release v3.1.0）
 */
import { readFileSync, writeFileSync } from 'fs';
import { join } from 'path';

const SRC = process.argv[2] || '/tmp/moonlit/theme-settings.js';
const OUT = process.argv[3]
    || join(process.cwd(), 'app/src/main/assets/themes/moonlit-echoes/settings-schema.json');

const raw = readFileSync(SRC, 'utf8');

// 1) 两步模板字符串 → JSON 字符串（已验证全文件无 ${} 插值与嵌套反引号）：
//    先 t`X`（i18n 标签），再裸 `X`（cssBlock 值；不能合并一个正则——\b 在
//    空格↔反引号两个非 word 字符间不成立，合并写法会跳过裸模板起点导致跨界配对）
let js = raw.replace(/\bt`([^`]*)`/g, (_, s) => JSON.stringify(s));
js = js.replace(/`([^`]*)`/g, (_, s) => JSON.stringify(s));

// 2) 数组切片：themeCustomSettings = [ ... ];
const arrStart = js.indexOf('export const themeCustomSettings = [');
if (arrStart < 0) throw new Error('找不到 themeCustomSettings 数组');
const body = js.slice(arrStart, js.lastIndexOf(']') + 1);

// 3) 逐对象块提取（大括号配平），转 JSON
const items = [];
let depth = 0, start = -1;
for (let i = 0; i < body.length; i++) {
    if (body[i] === '{') { if (depth === 0) start = i; depth++; }
    else if (body[i] === '}') {
        depth--;
        if (depth === 0 && start >= 0) {
            const objSrc = body.slice(start, i + 1);
            try { items.push(JSON.parse(objSrc)); }
            catch (e) { throw new Error(`对象解析失败（${e.message}）: ${objSrc.slice(0, 120)}...`); }
            start = -1;
        }
    }
}

// 4) 校验：必须有 varId+type；统计类型分布
const typeCount = {};
for (const it of items) {
    if (!it.varId || !it.type) throw new Error(`缺 varId/type: ${JSON.stringify(it).slice(0, 80)}`);
    typeCount[it.type] = (typeCount[it.type] || 0) + 1;
}

// 5) 输出（保留官方顺序与全字段）
const schema = { source: 'Moonlit Echoes v3.1.0 theme-settings.js', settings: items };
writeFileSync(OUT, JSON.stringify(schema, null, 2) + '\n');

console.log(`✓ 提取 ${items.length} 项设置定义 → ${OUT}`);
console.log(`  类型分布: ${JSON.stringify(typeCount)}`);
const withCssBlock = items.filter(i => i.cssBlock).length;
console.log(`  含 cssBlock（启用注入的内嵌 CSS）: ${withCssBlock} 项`);
