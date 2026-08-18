#!/usr/bin/env node
// 官方 instruct 相关纯函数 → JSON fixture 生成器。
// 函数体逐字取自官方源码，仅替换全局依赖为桩；输出 engine/src/test/resources/diff/instruct.json。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'instruct.json');

const instructSrc = readFileSync(join(officialRef, 'public', 'scripts', 'instruct-mode.js'), 'utf8');
const openaiSrc = readFileSync(join(officialRef, 'public', 'scripts', 'openai.js'), 'utf8');
const scriptSrc = readFileSync(join(officialRef, 'public', 'script.js'), 'utf8');
const naiSrc = readFileSync(join(officialRef, 'public', 'scripts', 'nai-settings.js'), 'utf8');

function extractFunction(source, name) {
    const start = source.indexOf(`function ${name}`);
    if (start < 0) throw new Error(`not found: ${name}`);

    // 跳过参数列表（可能含解构花括号），定位函数体起始 '{'
    const parenStart = source.indexOf('(', start);
    let parenDepth = 0;
    let bodyStart = -1;
    let paramString = null;
    for (let i = parenStart; i < source.length; i++) {
        const ch = source[i];
        if (paramString) {
            if (ch === '\\') { i++; continue; }
            if (ch === paramString) paramString = null;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { paramString = ch; continue; }
        if (ch === '(') parenDepth++;
        else if (ch === ')') {
            parenDepth--;
            if (parenDepth === 0) {
                let j = i + 1;
                while (j < source.length && /\s/.test(source[j])) j++;
                if (source[j] === '{') bodyStart = j;
                break;
            }
        }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);

    let depth = 0;
    let inString = null;
    let inRegex = false;
    let inLineComment = false;
    let inBlockComment = false;
    let prevSignificant = '';
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) {
            if (ch === '\n') inLineComment = false;
            continue;
        }
        if (inBlockComment) {
            if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; }
            continue;
        }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; prevSignificant = ch; continue; }
        if (ch === '/' && !/[A-Za-z0-9_)\]}"']/.test(prevSignificant)) { inRegex = true; continue; }
        if (/\s/.test(ch)) continue;
        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) return source.slice(start, i + 1);
        }
        prevSignificant = ch;
    }
    throw new Error(`unbalanced: ${name}`);
}

const funcs = [
    'formatInstructModeChat',
    'formatInstructModeStoryString',
    'formatInstructModeExamples',
    'formatInstructModePrompt',
    'getInstructStoppingSequences',
].map((n) => extractFunction(instructSrc, n)).join('\n');

const createRawPrompt = extractFunction(scriptSrc, 'createRawPrompt');
const parseExample = extractFunction(openaiSrc, 'parseExampleIntoIndividual');
const adjustNovel = extractFunction(naiSrc, 'adjustNovelInstructionPrompt');

const stub = `
let name1 = 'User';
let name2 = 'Char';
let selected_group = null;
const online_status = 'no_connection';
const extension_prompt_types = { IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };
const names_behavior_types = { NONE: 'none', FORCE: 'force', ALWAYS: 'always' };
const force_output_sequence = { FIRST: 1, LAST: 2 };
const saveSettingsDebounced = () => {};
const resetScrollHeight = async () => {};

const power_user = {
    instruct: {},
    context: {},
};

function setSettings(instruct, context) {
    power_user.instruct = { ...instruct };
    power_user.context = { ...context };
}

function substituteParams(text, options = {}) {
    if (!text) return '';
    const name1Override = options.name1Override ?? name1;
    const name2Override = options.name2Override ?? name2;
    return String(text)
        .replace(/\\{\\{user\\}\\}/gi, name1Override)
        .replace(/\\{\\{char\\}\\}/gi, name2Override);
}

function getGroupNames() {
    return [];
}

function onlyUnique(value, index, self) {
    return self.indexOf(value) === index;
}

function regexFromString(regexString) {
    const match = /^\\/(.*)\\/([dgimsuvy]*)$/.exec(regexString);
    if (!match) return null;
    return new RegExp(match[1], match[2]);
}

// 真实官方实现（nai-settings.js）：去 []、trim；无 "{ " 前缀则包裹 "{ ... }"
${adjustNovel}

${parseExample}
`;

// ---- 用例定义 ----
const alpaca = {
    enabled: true,
    preset: 'Alpaca',
    input_sequence: '### Instruction:',
    input_suffix: '\n\n',
    output_sequence: '### Response:',
    output_suffix: '\n\n',
    system_sequence: '### Input:',
    system_suffix: '',
    last_system_sequence: '',
    first_input_sequence: '',
    first_output_sequence: '',
    last_input_sequence: '',
    last_output_sequence: '',
    story_string_prefix: '',
    story_string_suffix: '',
    stop_sequence: '',
    wrap: true,
    macro: true,
    names_behavior: 'force',
    activation_regex: '',
    bind_to_context: false,
    user_alignment_message: '',
    system_same_as_user: false,
    sequences_as_stop_strings: true,
    skip_examples: false,
};

const chatml = {
    ...alpaca,
    preset: 'ChatML',
    input_sequence: '<|im_start|>user',
    output_sequence: '<|im_start|>assistant',
    input_suffix: '<|im_end|>\n',
    output_suffix: '<|im_end|>\n',
    system_sequence: '<|im_start|>system',
    stop_sequence: '<|im_end|>',
    story_string_prefix: '<|im_start|>system',
    story_string_suffix: '<|im_end|>\n',
};

const context = {
    preset: 'Default',
    story_string: 'template',
    chat_start: '***',
    example_separator: '***',
    use_stop_strings: true,
    names_as_stop_strings: true,
    story_string_position: 0,
    story_string_role: 0,
    story_string_depth: 1,
};

const sampleExamples = [
    'This is how Char should talk\n<START>\nUser: hello\nChar: hi\nUser: how are you?\nChar: fine',
];

const cases = [
    { id: 'chat_user', fn: 'formatChat', instruct: alpaca, context, args: { name: 'User', mes: 'Hello', isUser: true, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_char', fn: 'formatChat', instruct: alpaca, context, args: { name: 'Char', mes: 'Hi there', isUser: false, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_narrator', fn: 'formatChat', instruct: alpaca, context, args: { name: '', mes: 'A quiet voice.', isUser: false, isNarrator: true, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_force_first', fn: 'formatChat', instruct: alpaca, context, args: { name: 'Char', mes: 'First!', isUser: false, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 1, selectedGroup: false } },
    { id: 'chat_force_last_input', fn: 'formatChat', instruct: alpaca, context, args: { name: 'User', mes: 'Last!', isUser: true, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 2, selectedGroup: false } },
    { id: 'chat_names_always', fn: 'formatChat', instruct: { ...alpaca, names_behavior: 'always' }, context, args: { name: 'Char', mes: 'With name', isUser: false, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_force_avatar', fn: 'formatChat', instruct: alpaca, context, args: { name: 'Char', mes: 'Avatar name', isUser: false, isNarrator: false, forceAvatar: 'Avatar', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_group_force', fn: 'formatChat', instruct: alpaca, context, args: { name: 'Alice', mes: 'Group msg', isUser: false, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: true } },
    { id: 'chat_macro_sequence', fn: 'formatChat', instruct: { ...alpaca, input_sequence: '{{name1}}: {{user}} says', input_suffix: '' }, context, args: { name: 'User', mes: 'Hi', isUser: true, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },
    { id: 'chat_nowrap', fn: 'formatChat', instruct: { ...alpaca, wrap: false, input_suffix: '', output_suffix: '' }, context, args: { name: 'Char', mes: 'Tight', isUser: false, isNarrator: false, forceAvatar: '', name1: 'User', name2: 'Char', forceOutputSequence: 0, selectedGroup: false } },

    { id: 'story_default', fn: 'formatStoryString', instruct: chatml, context, args: { storyString: 'You are a cat.' } },
    { id: 'story_in_chat', fn: 'formatStoryString', instruct: chatml, context: { ...context, story_string_position: 1 }, args: { storyString: 'You are a cat.' } },
    { id: 'story_empty', fn: 'formatStoryString', instruct: chatml, context, args: { storyString: '' } },
    { id: 'story_prefix_only', fn: 'formatStoryString', instruct: { ...chatml, story_string_suffix: '' }, context, args: { storyString: 'You are a cat.' } },

    { id: 'examples_alpaca', fn: 'formatExamples', instruct: alpaca, context, args: { mesExamplesArray: sampleExamples, name1: 'User', name2: 'Char', selectedGroup: false, groupBotNames: [] } },
    { id: 'examples_skip', fn: 'formatExamples', instruct: { ...alpaca, skip_examples: true }, context, args: { mesExamplesArray: sampleExamples, name1: 'User', name2: 'Char', selectedGroup: false, groupBotNames: [] } },
    { id: 'examples_chatml', fn: 'formatExamples', instruct: chatml, context, args: { mesExamplesArray: sampleExamples, name1: 'User', name2: 'Char', selectedGroup: false, groupBotNames: [] } },
    { id: 'examples_empty', fn: 'formatExamples', instruct: alpaca, context, args: { mesExamplesArray: [], name1: 'User', name2: 'Char', selectedGroup: false, groupBotNames: [] } },

    { id: 'prompt_default', fn: 'formatPrompt', instruct: alpaca, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: false, selectedGroup: false } },
    { id: 'prompt_quiet_to_loud', fn: 'formatPrompt', instruct: alpaca, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: true, selectedGroup: false } },
    { id: 'prompt_impersonate', fn: 'formatPrompt', instruct: alpaca, context, args: { name: 'User', isImpersonate: true, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: false, selectedGroup: false } },
    { id: 'prompt_bias', fn: 'formatPrompt', instruct: alpaca, context, args: { name: 'Char', isImpersonate: false, promptBias: 'prefill text', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: false, selectedGroup: false } },
    { id: 'prompt_names_always', fn: 'formatPrompt', instruct: { ...alpaca, names_behavior: 'always' }, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: true, selectedGroup: false } },
    { id: 'prompt_group_names', fn: 'formatPrompt', instruct: alpaca, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: true, selectedGroup: true } },
    { id: 'prompt_mistral_hack', fn: 'formatPrompt', instruct: { ...alpaca, output_sequence: '[/INST] ', last_output_sequence: '[/INST]' }, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: true, selectedGroup: true } },
    { id: 'prompt_nowrap', fn: 'formatPrompt', instruct: { ...alpaca, wrap: false }, context, args: { name: 'Char', isImpersonate: false, promptBias: '', name1: 'User', name2: 'Char', isQuiet: true, isQuietToLoud: false, selectedGroup: false } },

    { id: 'stops_alpaca', fn: 'stoppingSequences', instruct: alpaca, context, args: { name1: 'User', name2: 'Char' } },
    { id: 'stops_nowrap', fn: 'stoppingSequences', instruct: { ...alpaca, wrap: false }, context, args: { name1: 'User', name2: 'Char' } },
    { id: 'stops_no_chat_start', fn: 'stoppingSequences', instruct: alpaca, context: { ...context, use_stop_strings: false }, args: { name1: 'User', name2: 'Char' } },
    { id: 'stops_no_sequences', fn: 'stoppingSequences', instruct: { ...alpaca, sequences_as_stop_strings: false }, context, args: { name1: 'User', name2: 'Char' } },
    { id: 'stops_disabled', fn: 'stoppingSequences', instruct: { ...alpaca, enabled: false }, context, args: { name1: 'User', name2: 'Char' } },

    { id: 'raw_tc_alpaca', fn: 'createRawPrompt', instruct: alpaca, context, args: { prompt: [{ role: 'user', content: 'Hello' }, { role: 'assistant', content: 'Hi' }], api: 'textgenerationwebui', instructOverride: false, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: '' } },
    { id: 'raw_tc_chatml_prefill', fn: 'createRawPrompt', instruct: alpaca, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'textgenerationwebui', instructOverride: false, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: 'Sure' } },
    { id: 'raw_tc_chatml', fn: 'createRawPrompt', instruct: chatml, context, args: { prompt: [{ role: 'user', content: 'Hello' }, { role: 'assistant', content: 'Hi' }], api: 'textgenerationwebui', instructOverride: false, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: '' } },
    { id: 'raw_cc', fn: 'createRawPrompt', instruct: alpaca, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'openai', instructOverride: false, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: 'Sure' } },
    { id: 'raw_tc_instruct_override', fn: 'createRawPrompt', instruct: alpaca, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'textgenerationwebui', instructOverride: true, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: '' } },
    // novel 被 isInstruct 排除：名字前缀 + '\n' 拼接 + '\n'+prefill（adjustNovel stub 为恒等）
    { id: 'raw_tc_novel', fn: 'createRawPrompt', instruct: alpaca, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'novel', instructOverride: false, quietToLoud: false, systemPrompt: 'You are a cat.', prefill: 'Sure' } },
    // quietToLoud=true → last_output_sequence；quietToLoud=false → last_system_sequence（二者非空且不同才有区分度）
    { id: 'raw_tc_quiet_to_loud', fn: 'createRawPrompt', instruct: { ...alpaca, last_system_sequence: '[SYS_LAST]', last_output_sequence: '[OUT_LAST]' }, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'textgenerationwebui', instructOverride: false, quietToLoud: true, systemPrompt: '', prefill: '' } },
    { id: 'raw_tc_quiet_system', fn: 'createRawPrompt', instruct: { ...alpaca, last_system_sequence: '[SYS_LAST]', last_output_sequence: '[OUT_LAST]' }, context, args: { prompt: [{ role: 'user', content: 'Hello' }], api: 'textgenerationwebui', instructOverride: false, quietToLoud: false, systemPrompt: '', prefill: '' } },
];

const moduleText = stub + funcs + createRawPrompt + `
const __cases = ${JSON.stringify(cases)};
const __out = [];
for (const c of __cases) {
    const a = JSON.parse(JSON.stringify(c.args)); // 深拷贝：官方 createRawPrompt 会原地改输入
    name1 = a.name1 ?? 'User';
    name2 = a.name2 ?? 'Char';
    selected_group = a.selectedGroup ? 'group' : null;
    setSettings(c.instruct, c.context);
    let value;
    switch (c.fn) {
        case 'formatChat':
            value = formatInstructModeChat(a.name, a.mes, a.isUser, a.isNarrator, a.forceAvatar, a.name1, a.name2, a.forceOutputSequence);
            break;
        case 'formatStoryString':
            value = formatInstructModeStoryString(a.storyString);
            break;
        case 'formatExamples':
            value = formatInstructModeExamples(a.mesExamplesArray, a.name1, a.name2);
            break;
        case 'formatPrompt':
            value = formatInstructModePrompt(a.name, a.isImpersonate, a.promptBias, a.name1, a.name2, a.isQuiet, a.isQuietToLoud);
            break;
        case 'stoppingSequences':
            value = getInstructStoppingSequences();
            break;
        case 'createRawPrompt':
            value = createRawPrompt(a.prompt, a.api, a.instructOverride, a.quietToLoud, a.systemPrompt, a.prefill);
            break;
        default:
            throw new Error('unknown fn ' + c.fn);
    }
    __out.push({
        id: c.id,
        fn: c.fn,
        instruct: c.instruct,
        context: c.context,
        args: c.fn === 'createRawPrompt' ? { ...c.args, name1, name2 } : c.args,
        expected: value,
    });
}
return __out;
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
