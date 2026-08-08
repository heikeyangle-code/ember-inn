#!/usr/bin/env node
// populateChatCompletion（openai.js）→ 操作序列 fixture。
// 函数体逐字提取；prompts/chatCompletion/Message/TokenHandler/populate* 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'chat-pipeline.json');
const src = readFileSync(join(officialRef, 'public', 'scripts', 'openai.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth=0, inString=null, inRegex=false, inLineComment=false, inBlockComment=false, prev='';
    for (let i=bodyStart;i<source.length;i++) {
        const ch=source[i];
        if (inLineComment) { if (ch==='\n') inLineComment=false; continue; }
        if (inBlockComment) { if (ch==='*'&&source[i+1]==='/') { inBlockComment=false; i++; } continue; }
        if (inString) { if (ch==='\\') { i++; continue; } if (ch===inString) inString=null; continue; }
        if (inRegex) { if (ch==='\\') { i++; continue; } if (ch==='[') { while (i<source.length&&source[i]!==']') i++; continue; } if (ch==='/') inRegex=false; continue; }
        if (ch==='/'&&source[i+1]==='/') { inLineComment=true; i++; continue; }
        if (ch==='/'&&source[i+1]==='*') { inBlockComment=true; i++; continue; }
        if (ch==='"'||ch==="'"||ch==='`') { inString=ch; prev=ch; continue; }
        if (ch==='/'&&!/[A-Za-z0-9_)\]}"']/.test(prev)) { inRegex=true; continue; }
        if (/\s/.test(ch)) continue;
        if (ch==='{') depth++;
        else if (ch==='}') { depth--; if (depth===0) return i; }
        prev=ch;
    }
    throw new Error('unbalanced');
}

function extractFunction(signature, name) {
    const start=src.indexOf(signature);
    if (start<0) throw new Error(`not found: ${name}`);
    const parenStart=src.indexOf('(', start);
    let depth=0, bodyStart=-1, inString=null;
    for (let i=parenStart;i<src.length;i++) {
        const ch=src[i];
        if (inString) { if (ch==='\\') { i++; continue; } if (ch===inString) inString=null; continue; }
        if (ch==='"'||ch==="'"||ch==='`') { inString=ch; continue; }
        if (ch==='(') depth++;
        else if (ch===')') { depth--; if (depth===0) { let j=i+1; while (/\s/.test(src[j])) j++; if (src[j]==='{') bodyStart=j; break; } }
    }
    if (bodyStart<0) throw new Error(`no body: ${name}`);
    return src.slice(start, scanBody(src, bodyStart)+1);
}

const populateChatCompletion = extractFunction('async function populateChatCompletion(prompts, chatCompletion, { bias, quietPrompt, quietImage, type, cyclePrompt, messages, messageExamples })', 'populateChatCompletion');

const stubs = `
const ops = [];
const chat_completion_sources = { CLAUDE: 'claude' };
const character_names_behavior = { COMPLETION: 1 };
const INJECTION_POSITION = { RELATIVE: 0, ABSOLUTE: 1 };
let power_user = { pin_examples: false };
let oai_settings = { continue_prefill: false, chat_completion_source: 'openai', names_behavior: 0, assistant_prefill: '' };
let promptManager = { isPromptDisabledForActiveCharacter: () => false, log: () => {} };
let ToolManager = { canPerformToolCalls: () => false, registerFunctionToolsOpenAI: async () => {} };
let tokenHandler = { countAsync: async () => 10 };
let substituteParams = (x) => String(x ?? '');
let isImageInliningSupported = () => false;

class MessageCollection {
    constructor(identifier) { this.identifier = identifier; this.collection = []; }
    add(message) { this.collection.push(message); }
}
class Message {
    static async fromPromptAsync(prompt) {
        if (!prompt) return null;
        return { role: prompt.role, content: prompt.content, name: prompt.name ?? null, identifier: prompt.identifier };
    }
    async addImage() {}
    async setName() {}
    static async createAsync(role, content, identifier) { return { role, content, identifier }; }
}
const chatCompletion = {
    reserveBudget: (n) => ops.push({ op: 'reserve', amount: n }),
    freeBudget: (n) => ops.push({ op: 'free', amount: n }),
    add: (collection, index) => ops.push({ op: 'add', collection: collection.identifier, index: index ?? null }),
    insert: (message, target, position) => ops.push({ op: 'insert', target, position, message }),
    insertAtStart: (message, target) => ops.push({ op: 'insert', target, position: 'start', message }),
    insertAtEnd: (message, target) => ops.push({ op: 'insert', target, position: 'end', message }),
    has: (id) => request.body.prompts.collection.some(p => p.identifier === id),
    setOverriddenPrompts: (ids) => ops.push({ op: 'overridden', ids }),
};
const populationInjectionPrompts = async (prompts, messages) => messages;
const populateDialogueExamples = async () => ops.push({ op: 'populate', name: 'dialogueExamples' });
const populateChatHistory = async () => ops.push({ op: 'populate', name: 'chatHistory' });
`;

const runCase = new Function([
    stubs,
    populateChatCompletion,
    'return async (request) => {',
    '    promptManager = { isPromptDisabledForActiveCharacter: (id) => request.body.disabledPromptIds?.includes(id) ?? false, log: () => {} };',
    '    ToolManager = { canPerformToolCalls: () => request.body.toolCallsEnabled ?? false, registerFunctionToolsOpenAI: async (data) => { request.body.registeredToolData = data; } };',
    '    tokenHandler = { countAsync: async () => request.body.toolTokenCount ?? 10 };',
    '    power_user = { pin_examples: request.body.pinExamples ?? false };',
    '    oai_settings = { continue_prefill: request.body.continuePrefill ?? false, chat_completion_source: request.body.chatCompletionSource ?? "openai", names_behavior: request.body.namesBehavior ?? 0, assistant_prefill: request.body.assistantPrefill ?? "" };',
    '    ops.length = 0;',
    '    const prompts = {',
    '        collection: request.body.prompts.collection,',
    '        overriddenPrompts: request.body.prompts.overriddenPrompts ?? [],',
    '        has: (id) => request.body.prompts.collection.some(p => p.identifier === id),',
    '        get: (id) => request.body.prompts.collection.find(p => p.identifier === id),',
    '        index: (id) => request.body.prompts.collection.findIndex(p => p.identifier === id),',
    '    };',
    '    await populateChatCompletion(prompts, chatCompletion, {',
    '        bias: request.body.bias ?? "",',
    '        quietPrompt: request.body.quietPrompt ?? "",',
    '        quietImage: false,',
    '        type: request.body.type ?? "normal",',
    '        cyclePrompt: request.body.cyclePrompt ?? "",',
    '        messages: request.body.messages ?? [],',
    '        messageExamples: request.body.messageExamples ?? [],',
    '    });',
    '    return ops;',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const argsBody = JSON.parse(JSON.stringify(body));
    const expected = await runCase()({ body });
    cases.push({ id, args: { body: argsBody }, expected });
}

const prompts = (ids) => ({ collection: ids.map((id, i) => ({ identifier: id, role: 'system', content: id, name: null, injection_position: 0 })), overriddenPrompts: ['main'] });

await add('basic', {
    prompts: prompts(['main', 'worldInfoBefore', 'worldInfoAfter', 'charDescription', 'charPersonality', 'scenario', 'personaDescription', 'nsfw', 'jailbreak', 'dialogueExamples', 'chatHistory']),
    bias: '', quietPrompt: '', type: 'normal', pinExamples: false,
});
await add('quiet-impersonate', {
    prompts: prompts(['main', 'impersonate', 'quietPrompt', 'nsfw', 'jailbreak', 'dialogueExamples', 'chatHistory']),
    bias: 'B', quietPrompt: 'Q', type: 'impersonate', pinExamples: true,
});
await add('absolute-and-tools', {
    prompts: prompts(['main', 'summary', 'nsfw', 'jailbreak', 'dialogueExamples', 'chatHistory']),
    bias: '', quietPrompt: '', type: 'normal', toolCallsEnabled: true, toolTokenCount: 33,
});
await add('continue-prefill', {
    prompts: prompts(['main', 'nsfw', 'jailbreak', 'dialogueExamples', 'chatHistory']),
    bias: '', quietPrompt: '', type: 'continue', continuePrefill: true, chatCompletionSource: 'claude', assistantPrefill: 'continue:',
    messages: [{ role: 'assistant', content: 'last', name: 'C', identifier: 'chatHistory-1' }],
});
await add('disabled-prompt', {
    prompts: prompts(['main', 'worldInfoBefore', 'nsfw', 'jailbreak', 'dialogueExamples', 'chatHistory']),
    disabledPromptIds: ['worldInfoBefore'], bias: '', quietPrompt: '', type: 'normal',
});

writeFileSync(outFile, JSON.stringify({ source: 'openai.js populateChatCompletion', cases }, null, 2));
console.log('chat-pipeline:', cases.length, 'cases ->', outFile);
