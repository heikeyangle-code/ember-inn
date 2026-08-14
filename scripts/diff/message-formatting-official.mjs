#!/usr/bin/env node
// 官方 messageFormatting 纯文本子集 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   script.js:1753 messageFormatting（只移植不依赖 DOM 的步骤 1-7 + 名字前缀剥离）
//   power-user.js:429 fixMarkdown（verbatim）、utils.js:1378 escapeRegex、utils.js:154 escapeHtml
// 打桩（脚本头部登记）：
//   substituteParams = {{user}}→Alice（确定性；真实宏由 MacroEngine 单独差分/单测）
//   getRegexedString = text + |r{placement}:{depth}（可观测位点/深度；真实管线由 regex-pipeline 差分）
// 边界（不移植，登记）：
//   - 引号对转换 / Showdown makeHtml / DOMPurify / encodeStyleTags：渲染器边界
//   - 官方 name2 前缀剥离在 makeHtml 之后；本子集在纯文本上执行（语义等价近似）
//   - messageId==0 的 chat.mes 写回：结果经 firstMessageSubstituted 返回，App 按官方写回
//   - 官方 mes.trim() 在 makeHtml 之后；本子集在纯文本上执行（差分同边界）

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'message-formatting.json');

const substituteParams = (text) => String(text ?? '').replaceAll('{{user}}', 'Alice');
const getRegexedString = (text, placement, opts) =>
    String(text ?? '') + `|r${placement}:${opts && typeof opts.depth === 'number' ? opts.depth : '-'}`;

function escapeHtml(str) {
    return String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function escapeRegex(string) {
    return string.replace(/[/\-\\^$*+?.()|[\]{}]/g, '\\$&');
}

function countOccurrences(str, char) {
    return str.split(char).length - 1;
}

function isOdd(num) {
    return num % 2 !== 0;
}

function fixMarkdown(text, forDisplay) {
    const format = /([*_]{1,2})([\s\S]*?)\1/gm;
    let matches = [];
    let match;
    while ((match = format.exec(text)) !== null) {
        matches.push(match);
    }
    let newText = text;
    for (let i = matches.length - 1; i >= 0; i--) {
        let matchText = matches[i][0];
        let replacementText = matchText.replace(/(\*|_)([\t \u00a0\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]+)|([\t \u00a0\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]+)(\*|_)/g, '$1$4');
        newText = newText.slice(0, matches[i].index) + replacementText + newText.slice(matches[i].index + matchText.length);
    }
    if (!forDisplay) {
        return newText;
    }
    const splitText = newText.split('\n');
    for (let index = 0; index < splitText.length; index++) {
        const line = splitText[index];
        const charsToCheck = ['*', '"'];
        for (const char of charsToCheck) {
            if (line.includes(char) && isOdd(countOccurrences(line, char))) {
                splitText[index] = line.trimEnd() + char;
            }
        }
    }
    newText = splitText.join('\n');
    return newText;
}

// 官方 messageFormatting 纯文本子集（顺序与 Kotlin MessageFormattingEngine.format 一一对应）
function messageFormattingPure(mes, chName, isSystem, isUser, isNarrator, messageId, isReasoning, opts) {
    if (!mes) return '';
    const pu = opts.powerUser;

    if (Number(messageId) === 0 && !isSystem && !isUser && !isReasoning) {
        mes = substituteParams(mes);
    }

    if (chName === 'Note' && isSystem && !isUser) isSystem = false;
    if (isSystem && chName !== 'SillyTavern System') isSystem = false;

    const replacedPromptBias = pu.user_prompt_bias && substituteParams(pu.user_prompt_bias);
    if (!pu.show_user_prompt_bias && chName && !isUser && !isSystem && replacedPromptBias && mes.startsWith(replacedPromptBias)) {
        mes = mes.slice(replacedPromptBias.length);
    }

    if (!isSystem) {
        const placement = isReasoning ? 6 : isUser ? 1 : isNarrator ? 3 : 2;
        mes = getRegexedString(mes, placement, { characterOverride: chName, isMarkdown: true, depth: opts.depth });
    }

    if (pu.auto_fix_generated_markdown) mes = fixMarkdown(mes, true);

    if (!isSystem && pu.encode_tags) {
        mes = mes.replaceAll('<', '&lt;').replace(new RegExp('(?<!^|\\n\\s*)>', 'g'), '&gt;');
    }

    [pu.reasoning_prefix, pu.reasoning_suffix].forEach((reasoningString) => {
        if (!reasoningString || !reasoningString.trim().length) return;
        if (mes.includes(reasoningString)) {
            mes = mes.replace(reasoningString, escapeHtml(reasoningString));
        }
    });

    // 官方：非系统消息 makeHtml 后 trim（本子集在纯文本上执行）
    if (!isSystem) mes = mes.trim();

    if (!pu.allow_name2_display && chName && !isUser && !isSystem) {
        mes = mes.replace(new RegExp(`(^|\n)${escapeRegex(chName)}:`, 'g'), '$1');
    }

    return mes;
}

const cases = [];
let seq = 0;
function add(mes, chName, isSystem, isUser, isNarrator, messageId, isReasoning, opts) {
    const id = 'mf-' + String(++seq).padStart(4, '0');
    cases.push({
        id,
        args: {
            mes,
            chName,
            isSystem,
            isUser,
            isNarrator,
            messageId,
            isReasoning,
            depth: opts.depth ?? null,
            powerUser: {
                user_prompt_bias: opts.powerUser.user_prompt_bias ?? '',
                show_user_prompt_bias: opts.powerUser.show_user_prompt_bias ?? true,
                auto_fix_generated_markdown: opts.powerUser.auto_fix_generated_markdown ?? false,
                encode_tags: opts.powerUser.encode_tags ?? false,
                reasoning_prefix: opts.powerUser.reasoning_prefix ?? '',
                reasoning_suffix: opts.powerUser.reasoning_suffix ?? '',
                allow_name2_display: opts.powerUser.allow_name2_display ?? false,
            },
        },
        expected: messageFormattingPure(mes, chName, isSystem, isUser, isNarrator, messageId, isReasoning, opts),
    });
}

const DEFAULT_PU = () => ({ user_prompt_bias: '', show_user_prompt_bias: true, auto_fix_generated_markdown: false, encode_tags: false, reasoning_prefix: '', reasoning_suffix: '', allow_name2_display: false });
const clone = (pu) => ({ ...pu });

// 1) 空输入 + 基础维度全组合（分支穷举：messageId × isSystem × chName × isUser × isReasoning × isNarrator × depth）
for (const messageId of [0, 1, -1]) {
    for (const isSystem of [false, true]) {
        for (const chName of ['', 'Char', 'Note', 'SillyTavern System']) {
            for (const isUser of [false, true]) {
                for (const isReasoning of [false, true]) {
                    for (const isNarrator of [false, true]) {
                        for (const depth of [null, 0, 3]) {
                            const opts = { depth, powerUser: clone(DEFAULT_PU()) };
                            add('plain', chName, isSystem, isUser, isNarrator, messageId, isReasoning, opts);
                            if (seq < 3) add('', chName, isSystem, isUser, isNarrator, messageId, isReasoning, opts);
                        }
                    }
                }
            }
        }
    }
}

// 2) 首条消息宏替换可观测（{{user}} → Alice）
for (const messageId of [0, 1]) {
    for (const isSystem of [false, true]) {
        for (const isUser of [false, true]) {
            for (const isReasoning of [false, true]) {
                add('{{user}} 你好', 'Char', isSystem, isUser, false, messageId, isReasoning, { depth: 0, powerUser: clone(DEFAULT_PU()) });
            }
        }
    }
}

// 3) bias 前缀剥离
for (const show of [false, true]) {
    for (const isSystem of [false, true]) {
        for (const isUser of [false, true]) {
            for (const mes of ['Bias: 内容', 'Bias: 内容\n第二行', '前缀Bias: 内容']) {
                const pu = clone(DEFAULT_PU());
                pu.user_prompt_bias = 'Bias: ';
                pu.show_user_prompt_bias = show;
                add(mes, 'Char', isSystem, isUser, false, 1, false, { depth: 0, powerUser: pu });
            }
        }
    }
}
// 3b) bias 空串/纯空白边界
for (const bias of ['', '   ']) {
    for (const show of [false, true]) {
        const pu = clone(DEFAULT_PU());
        pu.user_prompt_bias = bias;
        pu.show_user_prompt_bias = show;
        add(bias + '开头', 'Char', false, false, false, 1, false, { depth: 0, powerUser: pu });
        add('前缀' + bias + '开头', 'Char', false, false, false, 1, false, { depth: 0, powerUser: pu });
    }
}

// 4) autoFixMarkdown
for (const autoFix of [false, true]) {
    for (const isSystem of [false, true]) {
        for (const mes of ['*odd', '他说"你好', 'a * b * c', '**ok**']) {
            const pu = clone(DEFAULT_PU());
            pu.auto_fix_generated_markdown = autoFix;
            add(mes, 'Char', isSystem, false, false, 1, false, { depth: 0, powerUser: pu });
        }
    }
}

// 5) encode_tags
for (const encode of [false, true]) {
    for (const isSystem of [false, true]) {
        for (const mes of ['a < b > c', '> quote', '第1行\n> quote', '<b>hi</b>', 'x<10,y=20']) {
            const pu = clone(DEFAULT_PU());
            pu.encode_tags = encode;
            add(mes, 'Char', isSystem, false, false, 1, false, { depth: 0, powerUser: pu });
        }
    }
}

// 6) reasoning prefix/suffix 转义
for (const prefix of ['', 'Reasoning:']) {
    for (const suffix of ['', '[/think]']) {
        for (const mes of ['Reasoning: x', '前置[/think]后置', 'Reasoning:[/think]', '无标记文本']) {
            const pu = clone(DEFAULT_PU());
            pu.reasoning_prefix = prefix;
            pu.reasoning_suffix = suffix;
            add(mes, 'Char', false, false, false, 1, false, { depth: 0, powerUser: pu });
        }
    }
}

// 7) allow_name2_display 名字前缀剥离
for (const allow of [false, true]) {
    for (const chName of ['Char', 'Note', 'SillyTavern System', 'C.har']) {
        for (const isSystem of [false, true]) {
            for (const isUser of [false, true]) {
                for (const mes of ['Char: 开头', 'Char: 开头\n第二行', '前缀 Char: 中间', 'Note: 评论']) {
                    const pu = clone(DEFAULT_PU());
                    pu.allow_name2_display = allow;
                    add(mes, chName, isSystem, isUser, false, 1, false, { depth: 0, powerUser: pu });
                }
            }
        }
    }
}

writeFileSync(outFile, JSON.stringify({ generator: 'message-formatting-official.mjs', cases }, null, 2));
console.log(`wrote ${cases.length} cases -> ${outFile}`);
