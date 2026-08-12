#!/usr/bin/env node
// 官方世界书纯逻辑 → JSON fixture 生成器。
// 提取 world-info.js 的 parseDecorators / parseRegexFromString / WorldInfoBuffer（matchKeys/getScore）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'worldinfo.json');

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

const parseDecorators = extractFunction(wiSrc, 'parseDecorators');
const parseRegexFromString = extractFunction(wiSrc, 'parseRegexFromString');
const escapeRegex = extractFunction(utilsSrc, 'escapeRegex');
const worldInfoBuffer = extractClass(wiSrc, 'WorldInfoBuffer');

const stub = `
const MAX_SCAN_DEPTH = 1000;
let world_info_depth = 4;
let world_info_case_sensitive = false;
let world_info_match_whole_words = false;
const scan_state = { NONE: 0, INITIAL: 1, RECURSION: 2, MIN_ACTIVATIONS: 3 };
const world_info_logic = { AND_ANY: 0, NOT_ALL: 1, NOT_ANY: 2, AND_ALL: 3 };
const KNOWN_DECORATORS = ['@@activate', '@@dont_activate'];

${parseRegexFromString}
${escapeRegex}
${parseDecorators}
${worldInfoBuffer}
`;

const cases = [
    // matchKeys
    { id: 'mk_plain_ci', fn: 'matchKeys', args: { haystack: 'Hello World', needle: 'world', messages: ['Hello World'] } },
    { id: 'mk_case_sensitive', fn: 'matchKeys', args: { haystack: 'Hello World', needle: 'world', messages: ['Hello World'], caseSensitive: true } },
    { id: 'mk_whole_word_single', fn: 'matchKeys', args: { haystack: 'x,y', needle: 'y', messages: ['x,y'], matchWholeWords: true } },
    { id: 'mk_whole_word_no_match', fn: 'matchKeys', args: { haystack: 'x,yz', needle: 'y', messages: ['x,yz'], matchWholeWords: true } },
    { id: 'mk_whole_word_multi', fn: 'matchKeys', args: { haystack: 'xx hello world yy', needle: 'hello world', messages: ['xx hello world yy'], matchWholeWords: true } },
    { id: 'mk_regex_i', fn: 'matchKeys', args: { haystack: 'FOO bar', needle: '/foo/i', messages: ['FOO bar'] } },
    { id: 'mk_regex_escaped_slash', fn: 'matchKeys', args: { haystack: 'a/b', needle: '/a\\/b/', messages: ['a/b'] } },
    { id: 'mk_multiword_plain', fn: 'matchKeys', args: { haystack: 'hello world', needle: 'hello', messages: ['hello world'] } },
    // getScore
    { id: 'score_primary_count', fn: 'getScore', args: { messages: ['a b'], key: ['a', 'b'] } },
    { id: 'score_and_all_full', fn: 'getScore', args: { messages: ['a b c'], key: ['a'], keysecondary: ['b', 'c'], selectiveLogic: 3 } },
    { id: 'score_and_all_partial', fn: 'getScore', args: { messages: ['a b'], key: ['a'], keysecondary: ['b', 'c'], selectiveLogic: 3 } },
    { id: 'score_and_any', fn: 'getScore', args: { messages: ['a c'], key: ['a'], keysecondary: ['b', 'c'], selectiveLogic: 0 } },
    { id: 'score_no_keys', fn: 'getScore', args: { messages: ['a'], key: [] } },
    // parseDecorators
    { id: 'dec_plain', fn: 'parseDecorators', args: { content: 'hello' } },
    { id: 'dec_activate', fn: 'parseDecorators', args: { content: '@@activate\nbody' } },
    { id: 'dec_dont', fn: 'parseDecorators', args: { content: '@@dont_activate\nx' } },
    { id: 'dec_triple_skipped', fn: 'parseDecorators', args: { content: '@@@activate\nbody' } },
    { id: 'dec_unknown_then_known', fn: 'parseDecorators', args: { content: '@@bogus\n@@activate\nbody' } },
    { id: 'dec_blank_line', fn: 'parseDecorators', args: { content: '@@activate\n\nbody' } },
    // 追加穷举：Unicode / 空输入 / 多个已知装饰器 / @@@ 跳过 / 无正文 / 正文内再次出现 @@
    { id: 'mk_unicode_cn', fn: 'matchKeys', args: { haystack: '你好世界', needle: '世界', messages: ['你好世界'] } },
    { id: 'mk_unicode_cs', fn: 'matchKeys', args: { haystack: '你好世界', needle: '世界', messages: ['你好世界'], caseSensitive: true } },
    { id: 'mk_emoji', fn: 'matchKeys', args: { haystack: 'a😀b', needle: '😀', messages: ['a😀b'] } },
    { id: 'mk_empty_haystack', fn: 'matchKeys', args: { haystack: '', needle: 'x', messages: [''] } },
    { id: 'mk_empty_needle', fn: 'matchKeys', args: { haystack: 'abc', needle: '', messages: ['abc'] } },
    { id: 'mk_regex_multiline', fn: 'matchKeys', args: { haystack: 'line1\nline2', needle: '/^line2$/m', messages: ['line1\nline2'] } },
    { id: 'mk_regex_unicode', fn: 'matchKeys', args: { haystack: '你好世界', needle: '/世界/u', messages: ['你好世界'] } },
    { id: 'mk_whole_word_hyphen', fn: 'matchKeys', args: { haystack: 'well-known', needle: 'well', messages: ['well-known'], matchWholeWords: true } },
    { id: 'mk_whole_word_unicode', fn: 'matchKeys', args: { haystack: '你 好 世界', needle: '好', messages: ['你 好 世界'], matchWholeWords: true } },
    { id: 'mk_multiline_messages', fn: 'matchKeys', args: { haystack: 'a\nb', needle: 'b', messages: ['a\nb'] } },
    { id: 'score_or_any', fn: 'getScore', args: { messages: ['a'], key: ['a'], keysecondary: ['b'], selectiveLogic: 1 } },
    { id: 'score_or_all', fn: 'getScore', args: { messages: ['a b'], key: ['a'], keysecondary: ['b'], selectiveLogic: 1 } },
    { id: 'score_and_any_missing', fn: 'getScore', args: { messages: ['a'], key: ['a'], keysecondary: ['b'], selectiveLogic: 2 } },
    { id: 'score_secondary_only', fn: 'getScore', args: { messages: ['b'], key: [], keysecondary: ['b'] } },
    { id: 'score_empty_messages', fn: 'getScore', args: { messages: [], key: ['a'] } },
    { id: 'score_unicode', fn: 'getScore', args: { messages: ['你好'], key: ['你好'] } },
    { id: 'dec_activate_only', fn: 'parseDecorators', args: { content: '@@activate' } },
    { id: 'dec_two_known', fn: 'parseDecorators', args: { content: '@@activate\n@@dont_activate\nbody' } },
    { id: 'dec_triple_then_known', fn: 'parseDecorators', args: { content: '@@@activate\n@@dont_activate\nbody' } },
    { id: 'dec_prefix_match', fn: 'parseDecorators', args: { content: '@@activate_extra\nbody' } },
    { id: 'dec_body_contains_at', fn: 'parseDecorators', args: { content: '@@activate\nline @@activate inside' } },
];

const moduleText = stub + `
const __cases = ${JSON.stringify(cases)};
const __out = [];
for (const c of __cases) {
    let value;
    const entry = {
        key: c.args.key ?? [],
        keysecondary: c.args.keysecondary ?? [],
        selectiveLogic: c.args.selectiveLogic,
        caseSensitive: c.args.caseSensitive,
        matchWholeWords: c.args.matchWholeWords,
        scanDepth: c.args.scanDepth,
    };
    switch (c.fn) {
        case 'matchKeys':
            value = new WorldInfoBuffer(c.args.messages ?? [], {}).matchKeys(c.args.haystack, c.args.needle, entry);
            break;
        case 'getScore':
            value = new WorldInfoBuffer(c.args.messages ?? [], {}).getScore(entry, scan_state.INITIAL);
            break;
        case 'parseDecorators':
            value = parseDecorators(c.args.content);
            break;
        default:
            throw new Error('unknown fn ' + c.fn);
    }
    __out.push({ id: c.id, fn: c.fn, args: c.args, expected: value });
}
return __out;
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
