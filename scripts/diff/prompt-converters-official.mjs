#!/usr/bin/env node
// 官方 convertClaudeMessages 整链（逐字提取 src/prompt-converters.js）→ fixture。
// 官方函数会原地修改 messages，add() 用结构化克隆保留输入；PROMPT_PLACEHOLDER/names 打桩。

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
    let i = src.indexOf('{', start);
    let depth = 0;
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

// ---- Claude / Gemini 共用消息用例 ----
const m = (role, content, extra = {}) => ({ role, content, ...extra });
const img = (url) => ({ type: 'image_url', image_url: { url } });
const txt = (text) => ({ type: 'text', text });

const names = { userName: 'User', charName: 'Bot', groupNames: ['GroupA', 'GroupB'] };
const noNames = { userName: '', charName: '', groupNames: [] };

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
    source: 'prompt-converters.js convertClaudeMessages + convertGooglePrompt + cachingAtDepthForClaude（逐字提取）',
    cases,
    cachingCases,
}, null, 2));
console.log('claude-messages/gemini-prompt:', cases.length, 'cases +', cachingCases.length, 'caching ->', outFile);
