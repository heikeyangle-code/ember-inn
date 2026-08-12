#!/usr/bin/env node
// 官方 checkWorldInfo 全流程 → JSON fixture 生成器。
// 覆盖：关键词/常驻/递归/预算/min activations/分组覆盖/分组评分/角色与标签过滤/
// sticky/cooldown/delay/delayUntilRecursion/probability。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'worldinfo-scan.json');

const wiSrc = readFileSync(join(officialRef, 'public', 'scripts', 'world-info.js'), 'utf8');
const utilsSrc = readFileSync(join(officialRef, 'public', 'scripts', 'utils.js'), 'utf8');

function extractFunction(source, name) {
    const start = source.indexOf(`function ${name}`);
    if (start < 0) throw new Error(`not found: ${name}`);
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
    return source.slice(start, scanBody(source, bodyStart) + 1);
}

function extractClass(source, name) {
    const start = source.indexOf(`class ${name}`);
    if (start < 0) throw new Error(`not found: class ${name}`);
    const brace = source.indexOf('{', start);
    if (brace < 0) throw new Error(`no body: class ${name}`);
    return source.slice(start, scanBody(source, brace) + 1);
}

function scanBody(source, bodyStart) {
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
            if (depth === 0) return i;
        }
        prevSignificant = ch;
    }
    throw new Error('unbalanced body');
}

const pieces = [
    'parseRegexFromString',
    'parseDecorators',
    'filterGroupsByScoring',
    'filterGroupsByTimedEffects',
    'filterByInclusionGroups',
].map((n) => extractFunction(wiSrc, n)).join('\n');

const checkWorldInfo = 'async ' + extractFunction(wiSrc, 'checkWorldInfo');
const worldInfoBuffer = extractClass(wiSrc, 'WorldInfoBuffer');
const timedEffects = extractClass(wiSrc, 'WorldInfoTimedEffects');
const getStringHash = extractFunction(utilsSrc, 'getStringHash');

const stub = `
const MAX_SCAN_DEPTH = 1000;
const DEFAULT_WEIGHT = 100;
const DEFAULT_DEPTH = 4;
const KNOWN_DECORATORS = ['@@activate', '@@dont_activate'];
const scan_state = { NONE: 0, INITIAL: 1, RECURSION: 2, MIN_ACTIVATIONS: 3 };
const world_info_logic = { AND_ANY: 0, NOT_ALL: 1, NOT_ANY: 2, AND_ALL: 3 };
const world_info_position = { before: 0, after: 1, ANTop: 2, ANBottom: 3, atDepth: 4, EMTop: 5, EMBottom: 6, outlet: 7 };
const sortFn = (a, b) => b.order - a.order;
const event_types = { WORLDINFO_SCAN_DONE: 'scan_done' };

let world_info_depth = 4;
let world_info_budget = 25;
let world_info_budget_cap = 0;
let world_info_recursive = false;
let world_info_overflow_alert = false;
let world_info_min_activations = 0;
let world_info_min_activations_depth_max = 0;
let world_info_max_recursion_steps = 0;
let world_info_use_group_scoring = false;
let world_info_case_sensitive = false;
let world_info_match_whole_words = false;
let shouldWIAddPrompt = false;

let worldEntries = [];
let charaName = '';
let tagKey = null;
let this_chid = 'char1';
let tagMapGlobal = {};
const defaultGlobalScanData = {};
const chat_metadata = { timedWorldInfo: { sticky: {}, cooldown: {} } };

function setSettings(s) {
    world_info_depth = s.depth ?? 4;
    world_info_budget = s.budget ?? 25;
    world_info_budget_cap = s.budgetCap ?? 0;
    world_info_recursive = s.recursive ?? false;
    world_info_overflow_alert = false;
    world_info_min_activations = s.minActivations ?? 0;
    world_info_min_activations_depth_max = s.minActivationsDepthMax ?? 0;
    world_info_max_recursion_steps = s.maxRecursionSteps ?? 0;
    world_info_use_group_scoring = s.useGroupScoring ?? false;
    world_info_case_sensitive = s.caseSensitive ?? false;
    world_info_match_whole_words = s.matchWholeWords ?? false;
}

function resetMetadata() {
    chat_metadata.timedWorldInfo = { sticky: {}, cooldown: {} };
}

const toastr = { warning() {}, error() {} };
const eventSource = { emit() {} };

function getContext() {
    return { extensionPrompts: {}, tagMap: tagMapGlobal };
}
function getExtensionPromptByName() { return null; }
function getCharaFilename() { return charaName; }
function getTagKeyForEntity() { return tagKey; }
function substituteParams(text) { return String(text); }
function getRegexedString(content) { return content; }
const regex_placement = { WORLD_INFO: 0 };
async function getTokenCountAsync(text) { return typeof text === 'string' ? text.length : 0; }
// 官方 utils.js escapeRegex（world-info.js matchWholeWords 路径依赖；此前用例未触发，2026-08-12 补）
function escapeRegex(string) {
    return string.replace(/[/\-\\^$*+?.()|[\]{}]/g, '\\$&');
}
async function getSortedEntries() {
    return structuredClone(worldEntries).map((entry) => {
        const [decorators, content] = parseDecorators(entry.content || '');
        const withDec = { ...entry, decorators, content };
        return { ...withDec, hash: getStringHash(JSON.stringify(withDec)) };
    }).sort(sortFn);
}

${getStringHash}
${pieces}
${checkWorldInfo}
${worldInfoBuffer}
${timedEffects}
`;

const cases = [
    {
        id: 'constant_and_keyword',
        settings: { depth: 2, budget: 100, recursive: false },
        chat: ['这里有钥匙'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, content: '常驻', constant: true, position: 0 },
            { uid: 2, world: 'w', order: 2, key: ['钥匙'], content: '触发', position: 1 },
        ],
    },
    {
        id: 'recursion',
        settings: { depth: 2, budget: 100, recursive: true },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['线索'], content: '暗门' },
            { uid: 2, world: 'w', order: 2, key: ['暗门'], content: '宝藏' },
        ],
    },
    {
        id: 'and_all_partial',
        settings: { depth: 2, budget: 100 },
        chat: ['门和钥匙'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], keysecondary: ['钥匙', '锁'], selectiveLogic: 3, selective: true, content: 'X' },
        ],
    },
    {
        id: 'and_all_full',
        settings: { depth: 2, budget: 100 },
        chat: ['门 钥匙 锁'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], keysecondary: ['钥匙', '锁'], selectiveLogic: 3, selective: true, content: 'X' },
        ],
    },
    {
        id: 'budget_stops',
        settings: { depth: 2, budget: 100 },
        chat: ['a b'],
        maxContext: 10,
        entries: [
            { uid: 1, world: 'w', order: 2, key: ['a'], content: 'aaaaaa' },
            { uid: 2, world: 'w', order: 1, key: ['b'], content: 'bbbbbb' },
        ],
    },
    {
        id: 'min_activations',
        settings: { depth: 1, budget: 100, minActivations: 1 },
        chat: ['第一句', '钥匙'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['钥匙'], content: '找到了' }],
    },
    {
        id: 'group_override',
        settings: { depth: 2, budget: 100 },
        chat: ['门'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], content: 'A', group: 'g', groupOverride: true },
            { uid: 2, world: 'w', order: 5, key: ['门'], content: 'B', group: 'g', groupOverride: true },
        ],
    },
    {
        id: 'group_scoring',
        settings: { depth: 2, budget: 100, useGroupScoring: true },
        chat: ['a b'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['a', 'b'], content: '高', group: 'g', useGroupScoring: true },
            { uid: 2, world: 'w', order: 2, key: ['a'], content: '低', group: 'g', useGroupScoring: true },
        ],
    },
    {
        id: 'char_filter_excluded',
        settings: { depth: 2, budget: 100 },
        chat: ['门'],
        maxContext: 100,
        charaName: '柳春娘',
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], content: 'X', characterFilter: { names: ['柳春娘'], isExclude: true } },
        ],
    },
    {
        id: 'char_filter_included',
        settings: { depth: 2, budget: 100 },
        chat: ['门'],
        maxContext: 100,
        charaName: '关东',
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], content: 'X', characterFilter: { names: ['柳春娘'], isExclude: true } },
        ],
    },
    {
        id: 'tag_filter_excluded',
        settings: { depth: 2, budget: 100 },
        chat: ['门'],
        maxContext: 100,
        tagKey: 'c1',
        tagMap: { c1: ['nsfw'] },
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['门'], content: 'X', characterFilter: { tags: ['nsfw'], isExclude: true } },
        ],
    },
    {
        id: 'sticky_second_scan',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: '粘住', sticky: 2 }],
        second: { chat: ['x', 'y'] },
    },
    {
        id: 'cooldown_second_scan',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: '冷', cooldown: 3 }],
        second: { chat: ['a', 'b'] },
    },
    {
        id: 'delay_suppressed',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: 'X', delay: 3 }],
    },
    {
        id: 'delay_until_recursion',
        settings: { depth: 2, budget: 100, recursive: true },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['线索'], content: '暗门' },
            { uid: 2, world: 'w', order: 2, key: ['暗门'], content: '宝藏', delayUntilRecursion: true },
        ],
    },
    {
        id: 'probability_fail',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: 'X', useProbability: true, probability: 1 }],
        random: [99.0],
    },
    {
        id: 'probability_sticky_second',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: '粘住', sticky: 2, useProbability: true, probability: 1 }],
        second: {
            chat: ['x', 'y'],
            entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: '粘住', sticky: 2, useProbability: true, probability: 1 }],
        },
        random: [0.005, 99.0],
    },
    // ---- 2026-08-12 穷举复验补充：深度/大小写/整词/概率/常驻禁用/分组/递归上限 ----
    {
        id: 'depth_zero',
        settings: { depth: 0, budget: 100 },
        chat: ['钥匙'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['钥匙'], content: 'X' }],
    },
    {
        id: 'case_sensitive_match',
        settings: { depth: 2, budget: 100, caseSensitive: true },
        chat: ['KEY'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['KEY'], content: 'X' }],
    },
    {
        id: 'case_sensitive_miss',
        settings: { depth: 2, budget: 100, caseSensitive: true },
        chat: ['KEY'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['key'], content: 'X' }],
    },
    {
        id: 'whole_word_match',
        settings: { depth: 2, budget: 100, matchWholeWords: true },
        chat: ['猫'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['猫'], content: 'X' }],
    },
    {
        id: 'whole_word_miss',
        settings: { depth: 2, budget: 100, matchWholeWords: true },
        chat: ['猫粮'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['猫'], content: 'X' }],
    },
    {
        id: 'probability_pass',
        settings: { depth: 2, budget: 100 },
        chat: ['a'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['a'], content: 'X', useProbability: true, probability: 100 }],
        random: [0.0],
    },
    {
        id: 'constant_disable',
        settings: { depth: 2, budget: 100 },
        chat: ['钥匙'],
        maxContext: 100,
        entries: [{ uid: 1, world: 'w', order: 1, key: ['钥匙'], content: 'X', constant: true, disable: true }],
    },
    {
        id: 'group_no_scoring_all',
        settings: { depth: 2, budget: 100, useGroupScoring: false },
        chat: ['a b'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['a'], content: 'A', group: 'g' },
            { uid: 2, world: 'w', order: 2, key: ['b'], content: 'B', group: 'g' },
        ],
    },
    {
        id: 'recursion_steps_limit',
        settings: { depth: 2, budget: 100, recursive: true, maxRecursionSteps: 1 },
        chat: ['线索'],
        maxContext: 100,
        entries: [
            { uid: 1, world: 'w', order: 1, key: ['线索'], content: '暗门' },
            { uid: 2, world: 'w', order: 2, key: ['暗门'], content: '更深' },
            { uid: 3, world: 'w', order: 3, key: ['更深'], content: '宝藏' },
        ],
    },
];

const moduleText = stub + `
const __cases = ${JSON.stringify(cases)};
const __out = [];

async function runScan(c) {
    Math.random = () => (c.__random ?? 99.0);
    setSettings(c.settings);
    worldEntries = c.entries.map((e) => ({ position: 0, ...e }));
    charaName = c.charaName ?? '';
    tagKey = c.tagKey ?? null;
    tagMapGlobal = c.tagMap ?? {};
    const result = await checkWorldInfo(c.chat, c.maxContext, false);
    return {
        before: result.worldInfoBefore,
        after: result.worldInfoAfter,
        uids: Array.from(result.allActivatedEntries.values()).map((e) => e.uid),
        em: (result.EMEntries ?? []).map((e) => ({ position: e.position, content: e.content })),
        anBefore: result.ANBeforeEntries ?? [],
        anAfter: result.ANAfterEntries ?? [],
        depth: (result.WIDepthEntries ?? []).map((e) => ({ depth: e.depth, role: e.role, entries: e.entries })),
    };
}

const __run = async () => {
for (const c of __cases) {
    resetMetadata();
    const first = await runScan({ ...c, __random: c.random ? c.random[0] : 99.0 });
    let second = null;
    if (c.second) {
        second = await runScan({ ...c, chat: c.second.chat, entries: c.second.entries ?? c.entries, __random: c.random ? c.random[1] : 99.0 });
        }
        __out.push({
            id: c.id,
            settings: c.settings,
            chat: c.second ? c.second.chat : c.chat,
            maxContext: c.maxContext,
            entries: c.second ? c.second.entries ?? c.entries : c.entries,
            charaName: c.charaName ?? '',
            tagKey: c.tagKey ?? null,
            tagMap: c.tagMap ?? {},
            random: c.random ?? [99.0],
            first: { chat: c.chat, entries: c.entries, expected: first },
            expected: second ?? first,
        });
    }
    return __out;
};
return __run();
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = await runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
