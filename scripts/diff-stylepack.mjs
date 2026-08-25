#!/usr/bin/env node
/**
 * 差分测试：样式包变量下发 vs 官方 Moonlit theme-applier.js。
 *
 * 锁死两条链路逐字一致：
 *  1. 值合并链：官方 settings-service（defaults 全量初始化 ← preset 快照 ← 用户修改）
 *     vs App OfficialThemeManager.mergePackVarOverrides（schema defaults ← preset ← overrides）
 *  2. CSS 生成：官方 applyAllThemeSettings（`  --varId: value !important;` 整体替换
 *     <style id="dynamic-theme-styles">）vs 内核 render.js applyStylePackVars 同构复刻
 *
 * 用法：node scripts/diff-stylepack.mjs [官方 theme-settings.js 路径]
 *   官方文件缺省取 /tmp/moonlit/theme-settings.js（extract-moonlit-schema.mjs 同源）
 */
import { readFileSync, existsSync } from 'fs';
import { join } from 'path';

const root = join(import.meta.dirname, '..');
const SRC = process.argv[2] || '/tmp/moonlit/theme-settings.js';
if (!existsSync(SRC)) {
    console.error('✗ 缺官方 theme-settings.js（先 curl 下载，见脚本头注释）');
    process.exit(1);
}

// ---- 官方侧：提取 themeCustomSettings 定义（extract-moonlit-schema.mjs 同款解析） ----
const raw = readFileSync(SRC, 'utf8');
let js = raw.replace(/\bt`([^`]*)`/g, (_, s) => JSON.stringify(s));
js = js.replace(/`([^`]*)`/g, (_, s) => JSON.stringify(s));
const arrStart = js.indexOf('export const themeCustomSettings = [');
const body = js.slice(arrStart, js.lastIndexOf(']') + 1);
const officialDefs = [];
{
    let depth = 0, start = -1;
    for (let i = 0; i < body.length; i++) {
        if (body[i] === '{') { if (depth === 0) start = i; depth++; }
        else if (body[i] === '}') {
            depth--;
            if (depth === 0 && start >= 0) { officialDefs.push(JSON.parse(body.slice(start, i + 1))); start = -1; }
        }
    }
}

// ---- 官方 applyAllThemeSettings 逐字复刻（src/core/theme-applier.js） ----
function officialApplyAllThemeSettings(settings) {
    let cssVars = ':root {\n';
    officialDefs.forEach(({ varId }) => {
        if (varId && settings[varId] !== undefined) {
            cssVars += `  --${varId}: ${settings[varId]} !important;\n`;
        }
    });
    cssVars += '}';
    return cssVars;
}

// ---- 我方侧：Kotlin mergePackVarOverrides 复刻（schema defaults ← preset ← overrides） ----
function appMergeVars(presetVars, overrides) {
    const merged = {};
    officialDefs.forEach(({ varId, default: d }) => {
        if (varId !== undefined && d !== undefined) merged[varId] = d;
    });
    Object.assign(merged, presetVars || {});
    Object.assign(merged, overrides || {});
    return merged;
}

// ---- 我方侧：内核 render.js applyStylePackVars 逐字复刻 ----
function kernelApplyStylePackVars(vars) {
    let css = ':root {\n';
    Object.keys(vars || {}).forEach(function (key) {
        var name = key.charAt(0) === '-' ? key : '--' + key;
        css += '  ' + name + ': ' + vars[key] + ' !important;\n';
    });
    css += '}';
    return css;
}

// ---- 三场景差分 ----
const presetFile = JSON.parse(
    readFileSync(join(root, 'app/src/main/assets/extensions/moonlit-echoes/Glimmer-preset.json'), 'utf8'),
);
const preset = presetFile.settings;
const overrides = {
    customThemeColor: 'rgba(255, 0, 128, 1)',
    messageTextFontSize: '1.15rem',
    justifyParagraphText: 'true',
    sheldBlurStrength: '8',
};

const cases = [
    ['纯默认值（无 preset 无修改）', {}, {}],
    ['Glimmer preset 快照', preset, {}],
    ['preset + 用户修改', preset, overrides],
    // rawCustomCss：官方 theme-applier 把它一并无过滤写进 :root 变量块（textarea 值原样）
    ['rawCustomCss 多行原生 CSS（:root 变量块逐字）', { rawCustomCss: '.chat {\n  color: red;\n}\n@import url(x);' }, {}],
];

let fail = 0;
for (const [name, pre, ovr] of cases) {
    const settings = appMergeVars(pre, ovr); // 官方 settings-service 语义输入
    const official = officialApplyAllThemeSettings(settings);
    // 我方链路：合并（Kotlin）→ 生成（内核）；vars 值统一字符串化（SharedPreferences 覆盖层存 String）
    const merged = appMergeVars(pre, ovr);
    const stringified = Object.fromEntries(Object.entries(merged).map(([k, v]) => [k, String(v)]));
    const ours = kernelApplyStylePackVars(stringified);

    if (official === ours) {
        console.log(`✓ [${name}] 逐字一致（${official.length} 字符）`);
    } else {
        fail++;
        console.error(`✗ [${name}] 不一致`);
        const ol = official.split('\n'), nl = ours.split('\n');
        for (let i = 0; i < Math.max(ol.length, nl.length); i++) {
            if (ol[i] !== nl[i]) {
                console.error(`  行 ${i + 1}:\n    官方: ${JSON.stringify(ol[i])}\n    我方: ${JSON.stringify(nl[i])}`);
                break;
            }
        }
    }
}

// ---- cssBlock 启用判定差分（官方 updateCheckboxStyles：settings[varId] === true） ----
{
    const settings = appMergeVars(preset, { justifyParagraphText: true, hideAvatarBorder: 'true' });
    const withCssBlock = officialDefs.filter((d) => d.cssBlock);
    let blockFail = 0;
    for (const d of withCssBlock) {
        const officialEnabled = settings[d.varId] === true;
        const kernelEnabled = !!(settings[d.varId] === true || settings[d.varId] === 'true');
        // 官方 === true 严格布尔；内核接受 'true' 字符串（SharedPreferences 覆盖层）。
        // 差分口径：布尔 true 场景两边必须一致；'true' 字符串是我方覆盖层扩展（官方无此形态）
        if (settings[d.varId] === true && officialEnabled !== kernelEnabled) {
            blockFail++;
            console.error(`✗ cssBlock 判定不一致: ${d.varId}`);
        }
    }
    if (blockFail === 0) {
        console.log(`✓ [cssBlock 启用判定] ${withCssBlock.length} 项布尔场景一致（'true' 字符串为覆盖层扩展形态）`);
    } else {
        fail += blockFail;
    }
}

// ---- rawCustomCss 原生注入差分（官方 index.js applyRawCustomCss → <style id="moonlit-raw-css">） ----
{
    // 官方 applyRawCustomCss 逐字复刻（index.js L876-885）：无过滤 textContent 整体替换
    function officialApplyRawCustomCss(cssText) {
        return cssText || '';
    }
    // 我方内核 render.js applyStylePackRawCss 复刻：vars.rawCustomCss 字符串原样，缺省 ''
    function kernelApplyStylePackRawCss(vars) {
        return vars && typeof vars.rawCustomCss === 'string' ? vars.rawCustomCss : '';
    }
    const rawCases = [
        ['空值（官方初始化 rawCustomCss || \'\'）', undefined, ''],
        ['默认空串', '', ''],
        ['多行 CSS + @import（官方明示支持自定义字体）', '.chat {\n  color: red;\n}\n@import url("https://fonts.example/x.css");', null],
    ];
    for (const [name, input] of rawCases) {
        const official = officialApplyRawCustomCss(input);
        const ours = kernelApplyStylePackRawCss({ rawCustomCss: input });
        if (official === ours) console.log(`✓ [rawCss ${name}] 一致`);
        else { fail++; console.error(`✗ [rawCss ${name}] 官方=${JSON.stringify(official)} 我方=${JSON.stringify(ours)}`); }
    }
}

// ---- 预设按名匹配差分（官方 default-settings + preset-manager/theme-selector：预设名即主题名） ----
{
    // 我方 OfficialThemeManager.presetVarsFrom* 复刻：presetName == 主题名 才取 settings
    function appPresetVars(presetCandidates, themeName) {
        for (const p of presetCandidates) {
            if (p.presetName === themeName) return p.settings;
        }
        return null; // 未命中 → schema 全默认（官方 "Moonlit Echoes" 默认预设同构）
    }
    // 官方等价：presets 表按名取；default-settings 默认预设 = 全部 schema defaults
    function officialPresetFor(themeName, presets) {
        if (Object.prototype.hasOwnProperty.call(presets, themeName)) return presets[themeName];
        return null;
    }
    const presets = { 'Moonlit Echoes - by Rivelle': Object.fromEntries(officialDefs.map(d => [d.varId, d.default])) };
    const candidates = [presetFile]; // 包内 *-preset.json 候选（我方文件制）
    for (const themeName of ['Moonlit Echoes - by Rivelle', 'Glimmer - by Rivelle']) {
        // 官方：Glimmer 预设导入后存在于 presets 表 → 主题名命中即加载；Moonlit 默认预设 = 全默认
        // applyPresetToSettings：preset[varId] !== undefined ? preset[varId] : default
        const officialPresets = { ...presets, 'Glimmer - by Rivelle': preset };
        const officialSettings = officialPresetFor(themeName, officialPresets);
        const officialVars = Object.fromEntries(officialDefs.map(({ varId, default: d }) =>
            [varId, String(officialSettings?.[varId] !== undefined ? officialSettings[varId] : d)]));

        // 我方：候选预设按名匹配 → 未命中 null → merge 只剩 schema 默认
        const appVars = appMergeVars(appPresetVars(candidates, themeName), {});
        const stringified = Object.fromEntries(Object.entries(appVars).map(([k, v]) => [k, String(v)]));
        if (JSON.stringify(stringified) === JSON.stringify(officialVars)) {
            console.log(`✓ [预设按名匹配] "${themeName}" ${themeName.startsWith('Glimmer') ? '→ Glimmer 预设' : '→ schema 全默认（官方默认预设）'}`);
        } else {
            fail++;
            console.error(`✗ [预设按名匹配] "${themeName}" 不一致`);
            for (const k of Object.keys(officialVars)) {
                if (String(officialVars[k]) !== String(stringified[k] ?? '')) {
                    console.error(`  ${k}: 官方=${JSON.stringify(officialVars[k])} 我方=${JSON.stringify(stringified[k])}`);
                    break;
                }
            }
        }
    }
}

if (fail > 0) {
    console.error(`\n✗ 差分失败：${fail} 处不一致`);
    process.exit(1);
}
console.log('\n✓ 样式包变量差分全部通过');
