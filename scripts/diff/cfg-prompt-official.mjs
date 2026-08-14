#!/usr/bin/env node
// 官方 CFG Scale 扩展纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   scripts/cfg-scale.js getGuidanceScale / getCustomSeparator / getCfgPrompt（仅纯逻辑，无 DOM/事件）
// 打桩（脚本头部登记）：
//   substituteParams = {{user}}→Alice（确定性；真实宏由 MacroEngine 单独差分/单测）
//   getCharaFilename = 直接传入 charaName（官方从当前角色文件名解析）
// 边界（不移植，登记）：
//   - DOM 事件/initCfg/onCfgMenuItemClick/loadSettings：UI 边界
//   - extension_settings.cfg 为空对象时 getGuidanceScale 返回 undefined（Kotlin null）
//   - charaCfg 不存在但 groupchatCharOverride=true 时官方返回 {type:chara, value:undefined}（保留）
//   - prompt_separator 非 JSON 字符串时官方回退 '\n'（JSON.parse throw 分支）

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'cfg-prompt.json');

const substituteParams = (text) => String(text ?? '').replaceAll('{{user}}', 'Alice');

const cfgType = { chat: 0, chara: 1, global: 2 };
const metadataKeys = {
    guidance_scale: 'cfg_guidance_scale',
    negative_prompt: 'cfg_negative_prompt',
    positive_prompt: 'cfg_positive_prompt',
    prompt_combine: 'cfg_prompt_combine',
    groupchat_individual_chars: 'cfg_groupchat_individual_chars',
    prompt_insertion_depth: 'cfg_prompt_insertion_depth',
    prompt_separator: 'cfg_prompt_separator',
};

function getGuidanceScale(extensionSettings, chatMetadata, selectedGroup, charaName) {
    if (!extensionSettings.cfg) {
        return;
    }

    const charaCfg = extensionSettings.cfg.chara?.find((e) => e.name === charaName);
    const chatGuidanceScale = chatMetadata[metadataKeys.guidance_scale];
    const groupchatCharOverride = chatMetadata[metadataKeys.groupchat_individual_chars] ?? false;

    if (chatGuidanceScale && chatGuidanceScale !== 1 && !groupchatCharOverride) {
        return {
            type: cfgType.chat,
            value: chatGuidanceScale,
        };
    }

    if ((!selectedGroup && charaCfg || groupchatCharOverride) && charaCfg?.guidance_scale !== 1) {
        return {
            type: cfgType.chara,
            value: charaCfg.guidance_scale,
        };
    }

    if (extensionSettings.cfg.global && extensionSettings.cfg.global?.guidance_scale !== 1) {
        return {
            type: cfgType.global,
            value: extensionSettings.cfg.global.guidance_scale,
        };
    }
}

function getCustomSeparator(chatMetadata) {
    const defaultSeparator = '\n';

    try {
        if (chatMetadata[metadataKeys.prompt_separator]) {
            return JSON.parse(chatMetadata[metadataKeys.prompt_separator]);
        }

        return defaultSeparator;
    } catch {
        return defaultSeparator;
    }
}

function getCfgPrompt(guidanceScale, isNegative, quiet, extensionSettings, chatMetadata, charaName) {
    let splitCfgPrompt = [];

    const cfgPromptCombine = chatMetadata[metadataKeys.prompt_combine] ?? [];
    if (guidanceScale.type === cfgType.chat || cfgPromptCombine.includes(cfgType.chat)) {
        splitCfgPrompt.unshift(
            substituteParams(chatMetadata[isNegative ? metadataKeys.negative_prompt : metadataKeys.positive_prompt]),
        );
    }

    const charaCfg = extensionSettings.cfg.chara?.find((e) => e.name === charaName);
    if (guidanceScale.type === cfgType.chara || cfgPromptCombine.includes(cfgType.chara)) {
        splitCfgPrompt.unshift(
            substituteParams(isNegative ? charaCfg.negative_prompt : charaCfg.positive_prompt),
        );
    }

    if (guidanceScale.type === cfgType.global || cfgPromptCombine.includes(cfgType.global)) {
        splitCfgPrompt.unshift(
            substituteParams(isNegative ? extensionSettings.cfg.global.negative_prompt : extensionSettings.cfg.global.positive_prompt),
        );
    }

    const customSeparator = getCustomSeparator(chatMetadata);
    const combinedCfgPrompt = splitCfgPrompt.filter((e) => e.length > 0).join(customSeparator);
    const insertionDepth = chatMetadata[metadataKeys.prompt_insertion_depth] ?? 1;

    return {
        value: combinedCfgPrompt,
        depth: insertionDepth,
    };
}

function jv(v) {
    return v === undefined ? null : v;
}

const cases = [];
let id = 0;

function add(name, extensionSettings, chatMetadata, selectedGroup, charaName, isNegative) {
    const guidance = getGuidanceScale(extensionSettings, chatMetadata, selectedGroup, charaName);
    const prompt = guidance ? getCfgPrompt(guidance, isNegative, true, extensionSettings, chatMetadata, charaName) : null;
    cases.push({
        id: String(++id).padStart(3, '0') + '-' + name,
        args: {
            extensionSettings,
            chatMetadata,
            selectedGroup,
            charaName,
            isNegative,
        },
        expected: {
            guidance: guidance ? { type: guidance.type, value: jv(guidance.value) } : null,
            prompt: prompt ? { value: prompt.value, depth: prompt.depth } : null,
        },
    });
}

const emptyMeta = {};
const noCfg = {};
const globalCfg = { cfg: { global: { guidance_scale: 2.5, negative_prompt: 'global neg {{user}}', positive_prompt: 'global pos' } } };
const charaCfg = {
    cfg: {
        global: { guidance_scale: 1, negative_prompt: '', positive_prompt: '' },
        chara: [{ name: 'Alice', guidance_scale: 3, negative_prompt: 'chara neg', positive_prompt: 'chara pos' }],
    },
};
const chatCfg = {
    cfg: {
        global: { guidance_scale: 1, negative_prompt: '', positive_prompt: '' },
    },
};
const mixedCfg = {
    cfg: {
        global: { guidance_scale: 2.5, negative_prompt: 'global neg', positive_prompt: 'global pos' },
        chara: [{ name: 'Alice', guidance_scale: 3, negative_prompt: 'chara neg', positive_prompt: 'chara pos' }],
    },
};

// getGuidanceScale 优先级
add('no-cfg', noCfg, emptyMeta, false, 'Alice', false);
add('global-only', globalCfg, emptyMeta, false, 'Alice', false);
add('chara-priority', charaCfg, emptyMeta, false, 'Alice', false);
add('chat-priority', chatCfg, { cfg_guidance_scale: 4, cfg_negative_prompt: 'chat neg' }, false, 'Alice', true);
add('chat-one-ignored', chatCfg, { cfg_guidance_scale: 1, cfg_negative_prompt: 'chat neg' }, false, 'Alice', true);
// 官方边界：groupchatCharOverride=true 且 charaCfg 不存在时官方 getGuidanceScale 会抛 TypeError（访问 undefined），不生成差分用例
add('group-override-chara', charaCfg, { cfg_groupchat_individual_chars: true }, true, 'Alice', false);
add('selected-group-no-override', charaCfg, {}, true, 'Alice', false);
add('chara-scale-one', { cfg: { global: { guidance_scale: 1 }, chara: [{ name: 'Alice', guidance_scale: 1 }] } }, {}, false, 'Alice', false);
add('chara-absent-name', charaCfg, {}, false, 'Bob', false);

// getCfgPrompt：类型 × 合并 × 空值 × 分隔符 × 深度
add('chat-prompt-only', chatCfg, { cfg_guidance_scale: 2, cfg_negative_prompt: 'chat neg' }, false, 'Alice', true);
add('chat-positive', chatCfg, { cfg_guidance_scale: 2, cfg_positive_prompt: 'chat pos {{user}}' }, false, 'Alice', false);
add('chara-prompt-only', charaCfg, {}, false, 'Alice', true);
add('global-prompt-only', globalCfg, {}, false, 'Alice', true);
add('combine-all', charaCfg, {
    cfg_guidance_scale: 2,
    cfg_negative_prompt: 'chat neg',
    cfg_positive_prompt: 'chat pos',
    cfg_prompt_combine: [0, 1, 2],
    cfg_prompt_insertion_depth: 5,
}, false, 'Alice', false);
add('combine-chat-only', globalCfg, {
    cfg_negative_prompt: 'chat neg',
    cfg_prompt_combine: [0],
}, false, 'Alice', true);
add('combine-chara-only', mixedCfg, {
    cfg_prompt_combine: [1],
}, false, 'Alice', true);
add('combine-global-only', chatCfg, {
    cfg_prompt_combine: [2],
}, false, 'Alice', true);
add('empty-prompts-filtered', globalCfg, {
    cfg_negative_prompt: '',
    cfg_prompt_combine: [0, 2],
}, false, 'Alice', true);
add('separator-json-newline', globalCfg, {
    cfg_prompt_separator: '"\\n"',
    cfg_prompt_combine: [0, 2],
}, false, 'Alice', true);
add('separator-json-tab', globalCfg, {
    cfg_prompt_separator: '"\\t"',
    cfg_prompt_combine: [0, 2],
}, false, 'Alice', true);
add('separator-invalid-json', globalCfg, {
    cfg_prompt_separator: 'not-json',
    cfg_prompt_combine: [0, 2],
}, false, 'Alice', true);
add('separator-json-sentence', globalCfg, {
    cfg_prompt_separator: '" and then "',
    cfg_prompt_combine: [0, 2],
}, false, 'Alice', true);
add('depth-zero', globalCfg, {
    cfg_prompt_insertion_depth: 0,
}, false, 'Alice', true);
add('depth-default', globalCfg, {}, false, 'Alice', true);
// 官方 UI 恒写 Number，字符串 depth 不生成差分用例
add('chara-undefined-prompt', { cfg: { global: { guidance_scale: 1 }, chara: [{ name: 'Alice', guidance_scale: 3 }] } }, {
    cfg_groupchat_individual_chars: true,
}, true, 'Alice', true);

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`cfg-prompt fixtures: ${cases.length} cases -> ${outFile}`);
