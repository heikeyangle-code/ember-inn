#!/usr/bin/env node
// 世界书 ↔ 角色书互转 → JSON fixture 生成器。
// convertWorldInfoToCharacterBook（characters.js）与 convertCharacterBook（world-info.js）
// 逐字提取，newWorldInfoEntryTemplate 由官方定义计算。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'worldinfo-file.json');

const wiSrc = readFileSync(join(officialRef, 'public', 'scripts', 'world-info.js'), 'utf8');
const charSrc = readFileSync(join(officialRef, 'src', 'endpoints', 'characters.js'), 'utf8');

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

function extractConst(source, name) {
    const start = source.indexOf(`export const ${name} = {`);
    if (start < 0) throw new Error(`not found: const ${name}`);
    const bodyStart = source.indexOf('{', start);
    return source.slice(start, scanBody(source, bodyStart) + 1);
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

const definition = extractConst(wiSrc, 'newWorldInfoEntryDefinition').replace('export ', '');
const convertWorldInfoToCharacterBook = extractFunction(charSrc, 'convertWorldInfoToCharacterBook');
const convertCharacterBook = extractFunction(wiSrc, 'convertCharacterBook');

const stub = `
const DEFAULT_DEPTH = 4;
const DEFAULT_WEIGHT = 100;
const world_info_position = { before: 0, after: 1, ANTop: 2, ANBottom: 3, atDepth: 4, EMTop: 5, EMBottom: 6, outlet: 7 };
const world_info_logic = { AND_ANY: 0, NOT_ALL: 1, NOT_ANY: 2, AND_ALL: 3 };
const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
const GENERATION_TYPE_TRIGGERS = ['normal', 'continue', 'impersonate', 'swipe', 'regenerate', 'quiet'];

${definition}
const newWorldInfoEntryTemplate = Object.fromEntries(
    Object.entries(newWorldInfoEntryDefinition).filter(([_, value]) => !value.excludeFromTemplate).map(([key, value]) => [key, value.default]),
);

${convertWorldInfoToCharacterBook}
${convertCharacterBook}
`;

const worldEntries = [
    {
        uid: 0,
        key: ['钥匙', 'door'],
        keysecondary: ['锁'],
        comment: '大门口',
        content: '一扇木门。',
        constant: false,
        selective: true,
        selectiveLogic: 3,
        order: 100,
        position: 0,
        disable: false,
        excludeRecursion: false,
        probability: 50,
        useProbability: true,
        depth: 2,
        outletName: '',
        group: 'g',
        groupOverride: true,
        groupWeight: 200,
        preventRecursion: true,
        delayUntilRecursion: true,
        scanDepth: 3,
        matchWholeWords: true,
        useGroupScoring: true,
        caseSensitive: false,
        role: 0,
        sticky: 2,
        cooldown: 3,
        delay: 1,
        matchPersonaDescription: true,
        matchScenario: false,
        triggers: ['normal'],
        ignoreBudget: true,
        extensions: { my_custom: 'x' },
    },
    {
        uid: 1,
        key: ['暗门'],
        content: '隐藏通道',
        order: 50,
    },
];

const characterBook = {
    entries: [
        {
            id: 7,
            keys: ['巷子'],
            secondary_keys: ['夜里'],
            comment: '地点',
            content: '潮湿的小巷。',
            constant: true,
            selective: true,
            insertion_order: 10,
            enabled: true,
            position: 'after_char',
            use_regex: true,
            extensions: {
                position: 1,
                exclude_recursion: true,
                probability: 30,
                useProbability: true,
                depth: 3,
                selectiveLogic: 0,
                group: 'scene',
                group_override: false,
                group_weight: 150,
                prevent_recursion: false,
                delay_until_recursion: true,
                scan_depth: 2,
                match_whole_words: true,
                use_group_scoring: true,
                case_sensitive: false,
                role: 1,
                vectorized: false,
                sticky: 5,
                cooldown: null,
                delay: null,
                match_persona_description: true,
                triggers: ['normal'],
                ignore_budget: true,
                custom: 'keep',
            },
        },
        {
            id: 8,
            keys: ['深处'],
            content: '尽头',
        },
    ],
    extensions: {},
};

const cases = [
    {
        id: 'world_to_character_book',
        fn: 'toCharacterBook',
        args: { name: '测试世界', entries: worldEntries },
    },
    {
        id: 'character_book_to_world',
        fn: 'toWorldEntries',
        args: { characterBook },
    },
];

const moduleText = stub + `
const __cases = ${JSON.stringify(cases)};
const __out = [];
for (const c of __cases) {
    if (c.fn === 'toCharacterBook') {
        __out.push({ id: c.id, fn: c.fn, args: c.args, expected: convertWorldInfoToCharacterBook(c.args.name, c.args.entries) });
    } else {
        __out.push({ id: c.id, fn: c.fn, args: c.args, expected: convertCharacterBook(c.args.characterBook) });
    }
}
return __out;
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
