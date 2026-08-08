#!/usr/bin/env node
// BYAF 完整导入流程（characters.js importFromByaf）→ JSON fixture。
// importFromByaf/readFromV2 逐字提取；ByafParser/fs/write 打桩，输出导入计划。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'byaf-import.json');
const require = createRequire(import.meta.url);
const sanitize = require('./vendor/node_modules/sanitize-filename/index.js');

const charsSrc = readFileSync(join(officialRef, 'src', 'endpoints', 'characters.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && source[i + 1] !== '/' && source[i + 1] !== '*' &&
            (i === 0 || !/[A-Za-z0-9_$)]/.test(source[i - 1]))) { inRegex = true; continue; }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractFunction(signature, name) {
    const start = charsSrc.indexOf(signature);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = charsSrc.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < charsSrc.length; i++) {
        const ch = charsSrc[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(charsSrc[j])) j++; if (charsSrc[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return charsSrc.slice(start, scanBody(charsSrc, bodyStart) + 1);
}

const readFromV2 = extractFunction('function readFromV2(char)', 'readFromV2');
const importFromByaf = extractFunction('async function importFromByaf(uploadPath, { request }, preservedFileName)', 'importFromByaf');

const byafSrc = readFileSync(join(officialRef, 'src', 'byaf.js'), 'utf8');
function extractByafFunction(signature, name) {
    const start = byafSrc.indexOf(signature);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = byafSrc.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < byafSrc.length; i++) {
        const ch = byafSrc[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(byafSrc[j])) j++; if (byafSrc[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return byafSrc.slice(start, scanBody(byafSrc, bodyStart) + 1);
}
const byafReplaceMacros = extractByafFunction('static replaceMacros(str)', 'replaceMacros');
const byafFormatExampleMessages = extractByafFunction('static formatExampleMessages(examples)', 'formatExampleMessages');
const byafGetChatFromScenario = extractByafFunction('static getChatFromScenario(scenario, userName, characterName, chatBackgrounds)', 'getChatFromScenario');

const stubs = `
const console = { info: () => {}, warn: () => {}, error: () => {}, log: () => {}, debug: () => {} };
let writtenChats = [];
let writtenBackgrounds = [];
let writtenIcons = [];
let writtenPaths = [];
let writeCharacterResult = null;

const path = {
    join: (...parts) => parts.filter(x => x !== '' && x != null).join('/'),
    basename: (p, ext) => { const b = String(p).split('/').pop(); return ext ? b.slice(0, -ext.length) : b; },
    dirname: (p) => String(p).includes('/') ? String(p).split('/').slice(0, -1).join('/') : '.',
    extname: (p) => { const m = String(p).match(/(\\.[^./]+)$/); return m ? m[1] : ''; },
};

const fs = {
    existsSync: (p) => (request.body.exists?.includes(p) ?? false) || writtenPaths.includes(p),
    mkdirSync: () => {},
    unlinkSync: () => {},
    readdirSync: () => [],
};
const fsPromises = { readFile: async () => ({ buffer: Buffer.from(request.body.zipBase64 ?? '', 'base64') }), unlink: async () => {} };

const writeFileAtomicSync = (filePath, data) => {
    writtenPaths.push(filePath);
    if (String(filePath).includes('chats/')) writtenChats.push({ filePath, content: data.toString() });
    else if (String(filePath).includes('images/')) writtenBackgrounds.push({ filePath, data: Buffer.from(data).toString('base64') });
    else writtenIcons.push({ filePath, data: Buffer.from(data).toString('base64') });
};

const humanizedDateTime = () => '2026-08-08@00h00m00s000ms';
const getPngName = (name, directories) => name;
const clientRelativePath = (root, p) => p;
const getUniqueName = (baseName, exists) => { let name = baseName; let i = 1; while (exists(name)) name = \`\${baseName} (\${i++})\`; return name; };
const writeCharacterData = async (avatar, data, fileName, request) => { writeCharacterResult = { avatar: Buffer.isBuffer(avatar) ? 'buffer:' + avatar.toString('base64') : avatar, card: JSON.parse(data), fileName }; return true; };
const sanitizeSafeCharacterReplacements = () => '_';

class ByafParser {
    constructor() {}
    async parse() { return request.body.byafData; }

    static replaceMacros(str) { return String(str || '').replace(/#{user}:/gi, '{{user}}:').replace(/#{character}:/gi, '{{char}}:').replace(/{character}(?!})/gi, '{{char}}').replace(/{user}(?!})/gi, '{{user}}'); }
    static formatExampleMessages(examples) {
        if (!Array.isArray(examples)) return '';
        let formattedExamples = '';
        examples.forEach((example) => { if (!example?.text) return; formattedExamples += '<START>' + String.fromCharCode(10) + ByafParser.replaceMacros(example.text) + String.fromCharCode(10); });
        return formattedExamples.trimEnd();
    }
    static getChatFromScenario(scenario, userName, characterName, chatBackgrounds) {
        const chatStartDate = scenario?.messages?.length == 0 ? '2026-08-08T00:00:00.000Z' : scenario?.messages?.filter(m => 'createdAt' in m)[0]?.createdAt;
        const chatBackground = chatBackgrounds.find(bg => bg.paths.includes(scenario?.backgroundImage || ''))?.name || '';
        const chat = [{
            user_name: 'unused', character_name: 'unused',
            chat_metadata: {
                scenario: scenario?.narrative ?? '', mes_example: ByafParser.formatExampleMessages(scenario?.exampleMessages),
                system_prompt: ByafParser.replaceMacros(scenario?.formattingInstructions), mes_examples_optional: scenario?.canDeleteExampleMessages ?? false,
                byaf_model_settings: { model: scenario?.model ?? '', temperature: scenario?.temperature ?? 1.2, top_k: scenario?.topK ?? 40, top_p: scenario?.topP ?? 0.9, min_p: scenario?.minP ?? 0.1, min_p_enabled: scenario?.minPEnabled ?? true, repeat_penalty: scenario?.repeatPenalty ?? 1.05, repeat_penalty_tokens: scenario?.repeatLastN ?? 256, by_prompt_template: scenario?.promptTemplate ?? 'general', grammar: scenario?.grammar ?? null },
                chat_backgrounds: chatBackground ? [chatBackground] : [], custom_background: chatBackground ? 'url("' + encodeURI(chatBackground) + '")' : '',
            },
        }];
        if (scenario?.firstMessages?.length && scenario?.firstMessages?.length > 0 && scenario?.firstMessages?.[0]?.text) {
            chat.push({ name: characterName, is_user: false, send_date: chatStartDate, mes: scenario?.firstMessages?.[0]?.text || '' });
        }
        return chat.map(obj => JSON.stringify(obj)).join(String.fromCharCode(10));
    }
}
`;

const lodashStub = `
const _ = {
    isUndefined: v => v === undefined,
    get: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (const k of p) { if (cur == null) return undefined; cur = cur[k]; } return cur; },
    set: (obj, path, value) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur[p[i]] == null || typeof cur[p[i]] !== 'object') cur[p[i]] = {}; cur = cur[p[i]]; } cur[p[p.length - 1]] = value; },
    unset: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur == null) return; cur = cur[p[i]]; } if (cur != null) delete cur[p[p.length - 1]]; },
    forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); },
};
`;

const runCase = new Function('request', 'sanitize', [
    stubs,
    lodashStub,
    readFromV2,
    importFromByaf,
    'return (async () => {',
    '    writtenPaths = [];',
    '    request.user = { directories: { chats: "/chats", characters: "/chars", userImages: "/images", root: "/" } };',
    '    request.body.user_name = request.body.userName ?? "用户";',
    '    const fileName = await importFromByaf(\'upload.byaf\', { request }, request.body.preservedFileName ?? null);',
    '    return { fileName, writeCharacterResult, writtenChats, writtenBackgrounds, writtenIcons };',
    '})();',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase({ body }, sanitize);
    cases.push({ id, args: { body: JSON.parse(JSON.stringify(body)) }, expected });
}

const byafData = (scenarios, backgrounds = []) => {
    const first = scenarios[0] ?? {};
    const firstText = scenarios[0]?.firstMessages?.[0]?.text;
    const altGreetings = scenarios.slice(1)
        .filter(s => s.firstMessages?.[0]?.text && s.firstMessages[0].text !== firstText)
        .map(s => s.firstMessages[0].text);
    const card = {
        spec: 'chara_card_v2', spec_version: '2.0', create_date: '2026-08-08T00:00:00.000Z',
        data: {
            name: 'BYAF卡', description: '', personality: '', scenario: first.narrative ?? '', first_mes: first.firstMessages?.[0]?.text ?? '',
            mes_example: '', creator_notes: '', system_prompt: '', post_history_instructions: '', alternate_greetings: altGreetings,
            tags: [], creator: '', character_version: '', extensions: { display_name: '显示/名' },
        },
    };
    return {
        card,
        character: { displayName: '显示/名', name: 'BYAF卡' },
        scenarios,
        images: [{ filename: 'avatar.png', image: Buffer.from('QVZBVEFS', 'base64'), label: 'main' }],
        chatBackgrounds: backgrounds,
    };
};

const scenarioA = { title: '场景A', narrative: '故事', firstMessages: [{ text: '开场A' }], messages: [], backgroundImage: 'bg.png' };
const scenarioB = { title: '场景B', narrative: '备选', firstMessages: [{ text: '开场B' }], messages: [], backgroundImage: 'bg.png' };

await add('full', {
    userName: '用户',
    byafData: byafData([scenarioA, scenarioB], [{ name: 'bg.png', paths: ['bg.png'], data: Buffer.from('QkdB', 'base64') }]),
});
await add('preserved', {
    userName: '用户', preservedFileName: 'keep.json',
    byafData: byafData([scenarioA], [{ name: 'bg.png', paths: ['bg.png'], data: Buffer.from('QkdB', 'base64') }]),
});
await add('no-scenarios', {
    userName: '用户',
    byafData: byafData([], []),
});
await add('alt-icons', {
    userName: '用户',
    byafData: {
        ...byafData([scenarioA], []),
        images: [
            { filename: 'avatar.png', image: Buffer.from('QVZBVEFS', 'base64'), label: 'main' },
            { filename: 'alt1.png', image: Buffer.from('QUxUMQ==', 'base64'), label: '备选图标' },
        ],
    },
});


await add('duplicate-bg', {
    userName: '用户',
    byafData: byafData([scenarioA, { ...scenarioA, title: '场景A2' }], [{ name: 'bg.png', paths: ['bg.png'], data: Buffer.from('QkdB', 'base64') }]),
});
await add('bg-collision', {
    userName: '用户',
    exists: ['/images/显示_名/显示_名_bg.png'],
    byafData: byafData([scenarioA], [{ name: 'bg.png', paths: ['bg.png'], data: Buffer.from('QkdB', 'base64') }]),
});
await add('empty-title', {
    userName: '用户',
    byafData: byafData([{ ...scenarioA, title: '' }], []),
});
await add('duplicate-icons', {
    userName: '用户',
    byafData: {
        ...byafData([scenarioA], []),
        images: [
            { filename: 'avatar.png', image: Buffer.from('QVZBVEFS', 'base64'), label: 'main' },
            { filename: 'a.png', image: Buffer.from('QQ==', 'base64'), label: 'same' },
            { filename: 'b.png', image: Buffer.from('Qg==', 'base64'), label: 'same' },
        ],
    },
});

writeFileSync(outFile, JSON.stringify({ source: 'characters.js importFromByaf', cases }, null, 2));
console.log('byaf-import:', cases.length, 'cases ->', outFile);
