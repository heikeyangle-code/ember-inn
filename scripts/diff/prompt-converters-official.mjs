#!/usr/bin/env node
// 官方 convertClaudeMessages 整链（逐字提取 src/prompt-converters.js）→ fixture。
// 官方函数会原地修改 messages，add() 用结构化克隆保留输入；PROMPT_PLACEHOLDER/names 打桩。

import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'claude-messages.json');

const src = readFileSync(join(officialRef, 'src', 'prompt-converters.js'), 'utf8');

function extractFn(name) {
    const start = src.indexOf('export function ' + name);
    if (start < 0) throw new Error(name + ' not found');
    // 参数列表可能有解构默认值（mergeMessages），先找到参数右括号
    let i = src.indexOf('(', start);
    let paren = 0;
    let inString = null;
    let inLineComment = false;
    let inBlockComment = false;
    for (; i < src.length; i++) {
        const ch = src[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && src[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (ch === '/' && src[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && src[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') paren++;
        else if (ch === ')') { paren--; if (paren === 0) break; }
    }
    let depth = 0;
    const bodyStart = src.indexOf('{', i);
    i = bodyStart;
    for (; i < src.length; i++) {
        const ch = src[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && src[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (ch === '/' && src[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && src[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return src.slice(start, i + 1); }
    }
    throw new Error(name + ' unbalanced');
}

const claudeFn = extractFn('convertClaudeMessages').replace(/^export /, '');
const googleFn = extractFn('convertGooglePrompt').replace(/^export /, '');

const body = [
    'const PROMPT_PLACEHOLDER = request.body.promptPlaceholder ?? "Let\'s get started.";',
    'const enableThoughtSignatures = request.body.enableThoughtSignatures ?? true;',
    'const GEMINI_MEDIA_RESOLUTION = { low: \'media_resolution_low\', high: \'media_resolution_high\' };',
    'const tryParse = (str) => { try { return JSON.parse(str); } catch { return undefined; } };',
    'const names = (() => {',
    '    const n = request.body.names ?? {};',
    '    return {',
    '        userName: String(n.userName ?? \'\'),',
    '        charName: String(n.charName ?? \'\'),',
    '        startsWithGroupName: (message) => Array.isArray(n.groupNames) && n.groupNames.some(name => message.startsWith(name + \': \')),',
    '    };',
    '})();',
].join('\n');

const cachingFn = extractFn('cachingAtDepthForClaude').replace(/^export /, '');
const runClaude = new Function('request', body + '\n' + claudeFn + '\n' +
    'return convertClaudeMessages(request.body.messages ?? [], request.body.assistantPrefill ?? "", request.body.useSysPrompt ?? false, request.body.useTools ?? false, names);');
const runGoogle = new Function('request', body + '\n' + googleFn + '\n' +
    'return convertGooglePrompt(request.body.messages ?? [], request.body.model ?? "", request.body.useSysPrompt ?? false, names);');
const runCaching = new Function('request', cachingFn + '\n' +
    'const msgs = request.body.messages ?? [];\n' +
    'cachingAtDepthForClaude(msgs, request.body.cachingAtDepth, request.body.ttl ?? "5m");\n' +
    'return msgs;');

// ---- 其余纯转换器（Cohere/AI21/Mistral/XAI/mergeMessages/postProcess/预算） ----
function extractConst(name, endMarker) {
    const st = src.indexOf('const ' + name + ' = {');
    if (st < 0) throw new Error('const ' + name + ' not found');
    const en = src.indexOf(endMarker, st);
    return src.slice(st, en + endMarker.length);
}
const cohereFn = extractFn('convertCohereMessages').replace(/^export /, '');
const ai21Fn = extractFn('convertAI21Messages').replace(/^export /, '');
const mistralFn = extractFn('convertMistralMessages').replace(/^export /, '');
const xaiFn = extractFn('convertXAIMessages').replace(/^export /, '');
const mergeFn = extractFn('mergeMessages').replace(/^export /, '');
const postFn = extractFn('postProcessPrompt').replace(/^export /, '');
const addPrefixFn = extractFn('addAssistantPrefix').replace(/^export /, '');
const tcFn = extractFn('convertTextCompletionPrompt').replace(/^export /, '');
const claudeBudgetFn = extractFn('calculateClaudeBudgetTokens').replace(/^export /, '');
const googleBudgetFn = extractFn('calculateGoogleBudgetTokens').replace(/^export /, '');
const processingType = extractConst('PROMPT_PROCESSING_TYPE', '};');
const reasoningEffort = extractConst('REASONING_EFFORT', '};');

const runMoreBody = body + '\n' + processingType + '\n' + reasoningEffort + '\n' + mergeFn + '\n' +
    'const crypto = {\n' +
    '    randomBytes: () => ({ toString: () => tokenFn() }),\n' +
    '    createHash: () => ({ update: (s) => ({ digest: () => hashFn(s) }) }),\n' +
    '};\n' +
    'const getConfigValue = (key, def) => key === "mistral.enablePrefix" ? (request.body.enablePrefix ?? def) : def;\n';
const runCohere = new Function('request', 'hashFn', 'tokenFn', runMoreBody + cohereFn + '\nreturn convertCohereMessages(request.body.messages ?? [], names);');
const runAI21 = new Function('request', 'hashFn', 'tokenFn', runMoreBody + ai21Fn + '\nreturn convertAI21Messages(request.body.messages ?? [], names);');
const runMistral = new Function('request', 'hashFn', 'tokenFn', runMoreBody + mistralFn + '\nreturn convertMistralMessages(request.body.messages ?? [], names);');
const runXAI = new Function('request', 'hashFn', 'tokenFn', runMoreBody + xaiFn + '\nreturn convertXAIMessages(request.body.messages ?? [], names);');
const runMerge = new Function('request', 'hashFn', 'tokenFn', runMoreBody + mergeFn + '\nreturn mergeMessages(request.body.messages ?? [], names, { strict: request.body.strict ?? false, placeholders: request.body.placeholders ?? false, single: request.body.single ?? false, tools: request.body.tools ?? false });');
const runPostProcess = new Function('request', 'hashFn', 'tokenFn', runMoreBody + postFn + '\nreturn postProcessPrompt(request.body.messages ?? [], request.body.type ?? "", names);');
const runAssistantPrefix = new Function('request', 'hashFn', 'tokenFn', runMoreBody + addPrefixFn + '\nreturn addAssistantPrefix(request.body.messages ?? [], request.body.tools ?? [], request.body.property ?? "prefix");');
const runTextCompletion = new Function('request', 'hashFn', 'tokenFn', runMoreBody + tcFn + '\nreturn convertTextCompletionPrompt(request.body.messages ?? []);');
const runClaudeBudget = new Function('request', 'hashFn', 'tokenFn', runMoreBody + claudeBudgetFn + '\nreturn calculateClaudeBudgetTokens(request.body.maxTokens ?? 512, request.body.reasoningEffort ?? "", request.body.stream ?? false, request.body.isAdaptiveModel ?? false);');
const runGoogleBudget = new Function('request', 'hashFn', 'tokenFn', runMoreBody + googleBudgetFn + '\nreturn calculateGoogleBudgetTokens(request.body.maxTokens ?? 512, request.body.reasoningEffort ?? "", request.body.model ?? "");');
const openRouterDepthFn = extractFn('cachingAtDepthForOpenRouterClaude').replace(/^export /, '');
const openRouterSysFn = extractFn('cachingSystemPromptForOpenRouter').replace(/^export /, '');
const embedMediaFn = extractFn('embedOpenRouterMedia').replace(/^export /, '');
const reasoningContentFn = extractFn('addReasoningContentToToolCalls').replace(/^export /, '');
const openRouterSignaturesFn = extractFn('addOpenRouterSignatures').replace(/^export /, '');
const runOpenRouterDepth = new Function('request', 'hashFn', 'tokenFn', runMoreBody + openRouterDepthFn + '\nconst msgs = request.body.messages ?? [];\n' +
    'cachingAtDepthForOpenRouterClaude(msgs, request.body.cachingAtDepth ?? 0, request.body.ttl ?? "5m");\nreturn msgs;');
const runOpenRouterSys = new Function('request', 'hashFn', 'tokenFn', runMoreBody + openRouterSysFn + '\nconst msgs = request.body.messages ?? [];\n' +
    'cachingSystemPromptForOpenRouter(msgs, request.body.ttl);\nreturn msgs;');
const runEmbedMedia = new Function('request', 'hashFn', 'tokenFn', runMoreBody + embedMediaFn + '\nconst msgs = request.body.messages ?? [];\n' +
    'embedOpenRouterMedia(msgs, { audio: request.body.audio ?? true, video: request.body.video ?? true });\nreturn msgs;');
const runReasoningContent = new Function('request', 'hashFn', 'tokenFn', runMoreBody + reasoningContentFn + '\nconst msgs = request.body.messages ?? [];\n' +
    'addReasoningContentToToolCalls(msgs);\nreturn msgs;');
const runOpenRouterSignatures = new Function('request', 'hashFn', 'tokenFn', runMoreBody + openRouterSignaturesFn + '\nconst msgs = request.body.messages ?? [];\n' +
    'addOpenRouterSignatures(msgs, request.body.model ?? "");\nreturn msgs;');

const cases = [];
function add(id, args, opts = {}) {
    // 官方两函数都会原地改消息对象，必须每次深拷贝输入、结果立即快照，否则互相污染。
    const original = JSON.parse(JSON.stringify(args));
    let claudeExpected = null;
    if (opts.claudeThrows) {
        // convertClaudeMessages 对非法 JSON 参数会抛错（官方语义），无期望输出。
        original.claudeThrows = true;
    } else {
        claudeExpected = JSON.parse(JSON.stringify(runClaude({ body: JSON.parse(JSON.stringify(original)) })));
    }
    const googleExpected = JSON.parse(JSON.stringify(runGoogle({ body: JSON.parse(JSON.stringify(original)) })));
    cases.push({ id, args: original, claude: claudeExpected, google: googleExpected });
}

const moreCases = [];
function addMore(id, target, args, opts = {}) {
    const argsSnapshot = JSON.parse(JSON.stringify(args));
    const input = JSON.parse(JSON.stringify(args));
    let tokenCounter = 0;
    const tokenFn = () => 'TOKEN' + (tokenCounter++);
    const hashFn = (s) => createHash('sha512').update(s).digest('hex');
    let expected;
    switch (target) {
        case 'cohere': expected = runCohere({ body: input }, hashFn, tokenFn); break;
        case 'ai21': expected = runAI21({ body: input }, hashFn, tokenFn); break;
        case 'mistral': expected = runMistral({ body: input }, hashFn, tokenFn); break;
        case 'xai': expected = runXAI({ body: input }, hashFn, tokenFn); break;
        case 'merge': expected = runMerge({ body: input }, hashFn, tokenFn); break;
        case 'postProcess': expected = runPostProcess({ body: input }, hashFn, tokenFn); break;
        case 'assistantPrefix': expected = runAssistantPrefix({ body: input }, hashFn, tokenFn); break;
        case 'textCompletion': expected = runTextCompletion({ body: input }, hashFn, tokenFn); break;
        case 'claudeBudget': expected = runClaudeBudget({ body: input }, hashFn, tokenFn); break;
        case 'googleBudget': expected = runGoogleBudget({ body: input }, hashFn, tokenFn); break;
        case 'openRouterDepth': expected = runOpenRouterDepth({ body: input }, hashFn, tokenFn); break;
        case 'openRouterSys': expected = runOpenRouterSys({ body: input }, hashFn, tokenFn); break;
        case 'embedMedia': expected = runEmbedMedia({ body: input }, hashFn, tokenFn); break;
        case 'reasoningContent': expected = runReasoningContent({ body: input }, hashFn, tokenFn); break;
        case 'openRouterSignatures': expected = runOpenRouterSignatures({ body: input }, hashFn, tokenFn); break;
        default: throw new Error('unknown target ' + target);
    }
    moreCases.push({ id, target, args: argsSnapshot, expected: JSON.parse(JSON.stringify(expected)) });
}


const names = { userName: 'User', charName: 'Bot', groupNames: ['GroupA', 'GroupB'] };
const noNames = { userName: '', charName: '', groupNames: [] };

// ---- 其余纯转换器用例 ----
const msg = (role, content, extra = {}) => ({ role, content, ...extra });
const tc = (id, fn) => ({ id, function: { name: fn.name, arguments: fn.arguments } });
addMore('cohere-empty', 'cohere', { messages: [], names });
addMore('cohere-tool-after-assistant', 'cohere', { messages: [msg('assistant', '准备调用'), msg('user', '', { tool_calls: [tc('t1', { name: 'f', arguments: '{}' })] })], names });
addMore('cohere-tool-first', 'cohere', { messages: [msg('user', '', { tool_calls: [tc('t1', { name: 'a' }), tc('t2', { name: 'b' })] })], names });
addMore('cohere-names', 'cohere', { messages: [msg('system', 'Greetings', { name: 'example_assistant' }), msg('user', 'hi', { name: 'NPC' })], names });

addMore('ai21-basic', 'ai21', { messages: [msg('user', 'A'), msg('user', 'B'), msg('assistant', 'C')], names });
addMore('ai21-system', 'ai21', { messages: [msg('system', 'S1'), msg('system', 'S2'), msg('user', 'A')], names });
addMore('ai21-empty', 'ai21', { messages: [], names });
addMore('ai21-names', 'ai21', { messages: [msg('system', 'Hello', { name: 'example_user' }), msg('assistant', 'hi', { name: 'NPC' })], names });

addMore('mistral-basic', 'mistral', { messages: [msg('user', 'hi'), msg('assistant', 'yo')], names });
addMore('mistral-prefix', 'mistral', { messages: [msg('user', 'hi'), msg('assistant', 'yo')], enablePrefix: true, names });
addMore('mistral-tool-hash', 'mistral', { messages: [msg('user', 'search'), msg('assistant', '', { tool_calls: [tc('original-id', { name: 'f', arguments: '{}' })] }), msg('tool', 'result', { tool_call_id: 'original-id' })], names });
addMore('mistral-fix-tool', 'mistral', { messages: [msg('user', 'u'), msg('tool', 't', { tool_call_id: 'x' }), msg('user', 'after')], names });
addMore('mistral-system-after-assistant', 'mistral', { messages: [msg('assistant', 'a'), msg('system', 's')], names });
addMore('mistral-names', 'mistral', { messages: [msg('system', 'Greetings', { name: 'example_assistant' }), msg('user', 'hi', { name: 'NPC' })], names });

addMore('xai-basic', 'xai', { messages: [msg('assistant', 'hi', { name: 'Bot' }), msg('user', 'yo')], names });
addMore('xai-example-user', 'xai', { messages: [msg('system', 'hello', { name: 'example_user' })], names });
addMore('xai-user-skip', 'xai', { messages: [msg('user', 'hi', { name: 'NPC' })], names });
addMore('xai-no-char-name', 'xai', { messages: [msg('assistant', 'hi', { name: 'Bot' })], names: noNames });

addMore('merge-basic', 'merge', { messages: [msg('user', 'A'), msg('user', 'B')], names });
addMore('merge-strict-placeholders', 'merge', { messages: [msg('assistant', 'A')], strict: true, placeholders: true, names });
addMore('merge-strict-system', 'merge', { messages: [msg('system', 'S'), msg('user', 'A'), msg('system', 'mid'), msg('user', 'B')], strict: true, names });
addMore('merge-single', 'merge', { messages: [msg('assistant', 'A'), msg('user', 'B')], single: true, names });
addMore('merge-tools-false', 'merge', { messages: [msg('user', 'u'), msg('assistant', '', { tool_calls: [tc('t1', { name: 'f', arguments: '{}' })] }), msg('tool', 'r', { tool_call_id: 't1' })], tools: false, names });
addMore('merge-media', 'merge', { messages: [msg('user', [{ type: 'text', text: 'x' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,AA' } }]), msg('user', 'y')], names });
addMore('merge-empty', 'merge', { messages: [], names });
addMore('merge-name-prefix', 'merge', { messages: [msg('user', 'hi', { name: 'NPC' })], names });

addMore('post-merge', 'postProcess', { messages: [msg('user', 'A'), msg('user', 'B')], type: 'merge', names });
addMore('post-semi', 'postProcess', { messages: [msg('assistant', 'A'), msg('user', 'B')], type: 'semi', names });
addMore('post-strict', 'postProcess', { messages: [msg('system', 'S'), msg('user', 'A')], type: 'strict', names });
addMore('post-unknown', 'postProcess', { messages: [msg('user', 'A')], type: 'nope', names });

addMore('prefix-basic', 'assistantPrefix', { messages: [msg('user', 'A'), msg('assistant', 'B')], tools: [], property: 'prefix' });
addMore('prefix-tools', 'assistantPrefix', { messages: [msg('user', 'A'), msg('assistant', 'B')], tools: [{ type: 'function' }], property: 'prefix' });
addMore('prefix-tool-role', 'assistantPrefix', { messages: [msg('user', 'A'), msg('tool', 'r'), msg('assistant', 'B')], tools: [], property: 'prefix' });
addMore('prefix-empty', 'assistantPrefix', { messages: [], tools: [], property: 'prefix' });

addMore('tc-basic', 'textCompletion', { messages: [msg('system', 's'), msg('user', 'u'), msg('assistant', 'a')] });
addMore('tc-named-system', 'textCompletion', { messages: [msg('system', 's', { name: 'NPC' }), msg('user', 'u')] });
addMore('tc-string', 'textCompletion', { messages: 'raw prompt' });

addMore('budget-claude-adaptive-min', 'claudeBudget', { maxTokens: 1000, reasoningEffort: 'min', stream: true, isAdaptiveModel: true });
addMore('budget-claude-adaptive-auto', 'claudeBudget', { maxTokens: 1000, reasoningEffort: 'auto', stream: true, isAdaptiveModel: true });
addMore('budget-claude-low-stream', 'claudeBudget', { maxTokens: 1000, reasoningEffort: 'low', stream: true, isAdaptiveModel: false });
addMore('budget-claude-max-nonstream', 'claudeBudget', { maxTokens: 100000, reasoningEffort: 'max', stream: false, isAdaptiveModel: false });
addMore('budget-claude-unknown', 'claudeBudget', { maxTokens: 1000, reasoningEffort: 'weird', stream: true, isAdaptiveModel: false });

addMore('budget-google-flash-low', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'low', model: 'gemini-2.5-flash' });
addMore('budget-google-flash-lite-low', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'low', model: 'gemini-2.5-flash-lite' });
addMore('budget-google-pro-low', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'low', model: 'gemini-2.5-pro' });
addMore('budget-google-flash-auto', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'auto', model: 'gemini-2.5-flash' });
addMore('budget-google-gemini3-flash-low', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'low', model: 'gemini-3-flash' });
addMore('budget-google-gemini3-pro-medium', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'medium', model: 'gemini-3-pro' });
addMore('budget-google-unknown', 'googleBudget', { maxTokens: 1000, reasoningEffort: 'low', model: 'foo' });

const txt = (text) => ({ type: 'text', text });

// OpenRouter 专项
addMore('or-depth-string', 'openRouterDepth', { messages: [msg('user', 'A'), msg('assistant', 'B'), msg('user', 'C')], cachingAtDepth: 0, ttl: '5m' });
addMore('or-depth-array', 'openRouterDepth', { messages: [msg('user', 'A'), msg('assistant', 'B'), msg('user', [txt('C')])], cachingAtDepth: 1, ttl: '1h' });
addMore('or-depth-skip-system', 'openRouterDepth', { messages: [msg('system', 'S'), msg('user', 'A'), msg('assistant', 'B')], cachingAtDepth: 0, ttl: '5m' });
addMore('or-sys-basic', 'openRouterSys', { messages: [msg('system', 'S'), msg('user', 'A')], ttl: '24h' });
addMore('or-sys-no-ttl', 'openRouterSys', { messages: [msg('system', 'S'), msg('user', 'A')] });
addMore('or-sys-array-text', 'openRouterSys', { messages: [msg('system', [txt('S'), { type: 'image_url', image_url: { url: 'data:image/png;base64,X' } }]), msg('user', 'A')], ttl: '5m' });
addMore('or-sys-cache-exists', 'openRouterSys', { messages: [{ role: 'system', content: 'S', cache_control: { type: 'ephemeral' } }, msg('user', 'A')], ttl: '5m' });
addMore('or-embed-audio', 'embedMedia', { messages: [msg('user', [{ type: 'audio_url', audio_url: { url: 'data:audio/mpeg;base64,AU' } }])] });
addMore('or-embed-video-off', 'embedMedia', { messages: [msg('user', [{ type: 'video_url', video_url: { url: 'data:video/mp4;base64,VI' } }])], video: false });
addMore('or-reasoning-content', 'reasoningContent', { messages: [msg('user', 'u'), msg('assistant', '', { tool_calls: [tc('t1', { name: 'f', arguments: '{}' })] }), { role: 'user', content: 'x', reasoning_content: 'keep' }] });
addMore('or-signatures', 'openRouterSignatures', { messages: [{ role: 'assistant', content: 'a', signature: 'sig-1' }, { role: 'assistant', tool_calls: [{ id: 't1', signature: 'sig-2', function: { name: 'f', arguments: '{}' } }] }], model: 'openai/gpt-5' });
addMore('or-signatures-anthropic', 'openRouterSignatures', { messages: [{ role: 'assistant', content: 'a', signature: 'sig-3' }], model: 'anthropic/claude-3.7' });

// ---- Claude / Gemini 共用消息用例 ----
const m = (role, content, extra = {}) => ({ role, content, ...extra });
const img = (url) => ({ type: 'image_url', image_url: { url } });


add('system-extract', { messages: [m('system', 'System instruction'), m('user', 'Hello')], useSysPrompt: true, names });
add('all-system-placeholder', { messages: [m('system', 'Only system')], useSysPrompt: true, names });
add('mid-system-to-user', { messages: [m('user', 'Hi'), m('system', 'Mid-prompt system'), m('assistant', 'Reply')], useSysPrompt: false, names });
add('merge-same-role', { messages: [m('user', 'A'), m('user', 'B'), m('assistant', 'C')], useSysPrompt: false, names });
add('string-to-array', { messages: [m('user', 'Hello')], useSysPrompt: false, names });
add('empty-messages-no-sys', { messages: [], useSysPrompt: false, names });
add('empty-messages-with-sys', { messages: [], useSysPrompt: true, names });

add('image-convert', { messages: [m('user', [txt('Look'), img('data:image/png;base64,abc123')])], useSysPrompt: false, names });
add('assistant-image-move', { messages: [m('assistant', [txt('Here is an image'), img('data:image/png;base64,abc')]), m('user', 'Thanks')], useSysPrompt: false, names });
add('assistant-image-last', { messages: [m('assistant', [txt('Here is an image'), img('data:image/png;base64,abc')])], useSysPrompt: false, names });
add('two-assistant-images', { messages: [m('assistant', [txt('a1'), img('data:image/png;base64,AAA')]), m('assistant', [txt('a2'), img('data:image/png;base64,BBB')]), m('user', 'ok')], useSysPrompt: false, names });
add('video-unchanged', { messages: [m('user', [{ type: 'video_url', video_url: { url: 'data:video/mp4;base64,DDDD' } }])], useSysPrompt: false, names });

add('prefill-trim', { messages: [m('user', 'Hello')], assistantPrefill: 'Sure, I will ', useSysPrompt: false, names });
add('prefill-all-space', { messages: [m('user', 'Hello')], assistantPrefill: '   ', useSysPrompt: false, names });
add('empty-text-zwsp', { messages: [m('user', [txt('')])], useSysPrompt: false, names });

add('example-assistant-name', { messages: [m('system', 'Greetings', { name: 'example_assistant' }), m('user', 'Hi')], useSysPrompt: true, names });
add('example-user-name', { messages: [m('system', 'Hello there', { name: 'example_user' }), m('user', 'Hi')], useSysPrompt: true, names });
add('example-already-prefixed', { messages: [m('system', 'Bot: Greetings', { name: 'example_assistant' }), m('user', 'Hi')], useSysPrompt: true, names });
add('name-prefix-string', { messages: [m('user', 'Hello', { name: 'Alice' })], useSysPrompt: false, names });
add('name-prefix-string-already', { messages: [m('user', 'Alice: hello', { name: 'Alice' })], useSysPrompt: false, names });
add('name-prefix-array', { messages: [m('user', [txt('hi'), img('data:image/png;base64,ZZZ')], { name: 'Alice' })], useSysPrompt: false, names });

const toolCall = { id: 'tc1', function: { name: 'search', arguments: '{"q":"cats"}' } };
const toolCallObj = { id: 'tc2', function: { name: 'weather', arguments: { city: 'Paris' } } };
add('tool-calls', { messages: [m('user', 'search for cats'), m('assistant', '', { tool_calls: [toolCall] })], useTools: true, useSysPrompt: false, names });
add('tool-calls-no-tools', { messages: [m('user', 'search for cats'), m('assistant', '', { tool_calls: [toolCall] })], useTools: false, useSysPrompt: false, names });
add('tool-result', { messages: [m('user', 'search'), m('assistant', '', { tool_calls: [toolCall] }), m('tool', 'Found results', { tool_call_id: 'tc1' })], useTools: true, useSysPrompt: false, names });
add('tool-result-no-tools', { messages: [m('user', 'search'), m('assistant', '', { tool_calls: [toolCall] }), m('tool', 'Found results', { tool_call_id: 'tc1' })], useTools: false, useSysPrompt: false, names });
add('tool-calls-args-object', { messages: [m('assistant', '', { tool_calls: [toolCallObj] })], useTools: true, useSysPrompt: false, names });

// ---- Gemini 特有用例 ----
add('google-empty-messages', { messages: [], model: 'gemini-2.0-flash', useSysPrompt: true, names });
add('google-video-inline', { messages: [m('user', [{ type: 'video_url', video_url: { url: 'data:video/mp4;base64,VVVV' } }])], model: 'gemini-2.0-flash', useSysPrompt: false, names });
add('google-audio-inline', { messages: [m('user', [{ type: 'audio_url', audio_url: { url: 'data:audio/mpeg;base64,AAAA' } }])], model: 'gemini-2.0-flash', useSysPrompt: false, names });
add('google-signature-gemini3', { messages: [m('assistant', 'Hello', { signature: 'sig-1' })], model: 'gemini-3-pro', useSysPrompt: false, names });
add('google-signature-gemini25', { messages: [m('assistant', 'Hello', { signature: 'sig-2' })], model: 'gemini-2.5-flash', useSysPrompt: false, names });
add('google-signature-disabled', { messages: [m('assistant', 'Hello', { signature: 'sig-3' })], model: 'gemini-3-pro', enableThoughtSignatures: false, useSysPrompt: false, names });
add('google-gemini3-functioncall-no-sig', { messages: [m('assistant', '', { tool_calls: [toolCall] })], model: 'gemini-3-pro', useSysPrompt: false, names });
add('google-gemini3-image-model-text', { messages: [m('assistant', 'Hello')], model: 'gemini-3-pro-image-preview', useSysPrompt: false, names });
add('google-gemini3-image-model-inline', { messages: [m('assistant', [img('data:image/png;base64,SIGIMG')])], model: 'gemini-3-pro-image-preview', useSysPrompt: false, names });
add('google-media-resolution-detail', { messages: [m('user', [{ type: 'image_url', image_url: { url: 'data:image/png;base64,LOWRES', detail: 'low' } }])], model: 'gemini-3-pro', useSysPrompt: false, names });
add('google-media-resolution-high-non3', { messages: [m('user', [{ type: 'image_url', image_url: { url: 'data:image/png;base64,HIGHRES', detail: 'high' } }])], model: 'gemini-2.5-flash', useSysPrompt: false, names });
add('google-merge-inline', { messages: [m('user', [txt('A'), img('data:image/png;base64,AA')]), m('user', [txt('B'), img('data:image/png;base64,BB')])], model: 'gemini-2.0-flash', useSysPrompt: false, names });
add('google-tool-call-args-invalid', { messages: [m('assistant', '', { tool_calls: [{ id: 'x1', function: { name: 'f', arguments: 'not json' } }] })], model: 'gemini-2.0-flash', useSysPrompt: false, names }, { claudeThrows: true });
add('google-name-prefix-user', { messages: [m('user', 'hi', { name: 'NPC' })], model: 'gemini-2.0-flash', useSysPrompt: false, names });
add('google-tool-call-id-unknown', { messages: [m('tool', 'result', { tool_call_id: 'nope' })], model: 'gemini-2.0-flash', useSysPrompt: false, names });

// cachingAtDepthForClaude 单独用例
const cachingCases = [];
function addCaching(id, messages, cachingAtDepth, ttl) {
    const expected = JSON.parse(JSON.stringify(runCaching({ body: { messages: JSON.parse(JSON.stringify(messages)), cachingAtDepth, ttl } })));
    cachingCases.push({ id, args: { messages, cachingAtDepth, ttl }, expected });
}
addCaching('depth-1', [
    { role: 'user', content: [{ type: 'text', text: 'A' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'B' }] },
    { role: 'user', content: [{ type: 'text', text: 'C' }] },
], 1, '5m');
addCaching('depth-0-prefill-skip', [
    { role: 'user', content: [{ type: 'text', text: 'A' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'B' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'prefill' }] },
], 0, '5m');
addCaching('depth-1-plus2-break', [
    { role: 'user', content: [{ type: 'text', text: 'A' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'B' }] },
    { role: 'user', content: [{ type: 'text', text: 'C' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'D' }] },
    { role: 'user', content: [{ type: 'text', text: 'E' }] },
], 1, '1h');
addCaching('depth-2-ttl-custom', [
    { role: 'user', content: [{ type: 'text', text: 'A' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'B' }] },
    { role: 'user', content: [{ type: 'text', text: 'C' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'D' }] },
    { role: 'user', content: [{ type: 'text', text: 'E' }] },
    { role: 'assistant', content: [{ type: 'text', text: 'F' }] },
    { role: 'user', content: [{ type: 'text', text: 'G' }] },
], 2, '24h');

writeFileSync(outFile, JSON.stringify({
    source: 'prompt-converters.js 全部纯转换函数（逐字提取）',
    cases,
    cachingCases,
    moreCases,
}, null, 2));
console.log('prompt-converters:', cases.length, 'cases +', cachingCases.length, 'caching +', moreCases.length, 'more ->', outFile);
