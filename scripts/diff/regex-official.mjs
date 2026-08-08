#!/usr/bin/env node
// 官方正则脚本引擎 → JSON fixture 生成器。
// 提取 runRegexScript / RegexProvider / sanitizeRegexMacro / filterString / regexFromString。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'regex.json');

const engineSrc = readFileSync(join(officialRef, 'public', 'scripts', 'extensions', 'regex', 'engine.js'), 'utf8');
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

const runRegexScript = extractFunction(engineSrc, 'runRegexScript');
const sanitizeRegexMacro = extractFunction(engineSrc, 'sanitizeRegexMacro');
const filterString = extractFunction(engineSrc, 'filterString');
const regexProvider = extractClass(engineSrc, 'RegexProvider');
const regexFromString = extractFunction(utilsSrc, 'regexFromString');

const stub = `
let name1 = 'Alice';
let name2 = 'Bob';
const substitute_find_regex = { NONE: 0, RAW: 1, ESCAPED: 2 };

function substituteParams(text, options = {}) {
    if (!text) return '';
    return String(text)
        .replace(/\\{\\{user\\}\\}/gi, name1)
        .replace(/\\{\\{char\\}\\}/gi, name2);
}
function substituteParamsExtended(text, options = {}, postProcess) {
    const out = substituteParams(text, options);
    return typeof postProcess === 'function' ? postProcess(out) : out;
}

${regexFromString}
${regexProvider}
${sanitizeRegexMacro}
${filterString}
${runRegexScript}
`;

const cases = [
    { id: 'plain', args: { findRegex: 'foo', replaceString: 'bar', raw: 'a foo b' } },
    { id: 'groups', args: { findRegex: '(a)(b)', replaceString: '$2$1', raw: 'ab' } },
    { id: 'named', args: { findRegex: '(?<x>a)b', replaceString: '$<x>-b', raw: 'ab' } },
    { id: 'match_macro', args: { findRegex: '\\d+', replaceString: '[{{match}}]', raw: 'x12y' } },
    { id: 'trim', args: { findRegex: '(world)', replaceString: '$1', trimStrings: ['wor'], raw: 'hello world' } },
    { id: 'trim_macro', args: { findRegex: 'Alice', replaceString: '$0', trimStrings: ['{{user}}'], raw: 'Alice hi' } },
    { id: 'macro_replace', args: { findRegex: 'hi', replaceString: '{{user}} says', raw: 'hi' } },
    { id: 'substitute_raw', args: { findRegex: '{{user}}', replaceString: 'X', substituteRegex: 1, raw: 'Alice' } },
    { id: 'escaped_dot_hit', args: { findRegex: 'a.b', replaceString: 'X', substituteRegex: 2, raw: 'a.b' } },
    { id: 'escaped_dot_miss', args: { findRegex: 'a.b', replaceString: 'X', substituteRegex: 2, raw: 'axb' } },
    { id: 'disabled', args: { findRegex: 'foo', replaceString: 'X', raw: 'foo', disabled: true } },
    { id: 'empty_pattern', args: { findRegex: '', replaceString: 'X', raw: 'foo' } },

    { id: 'invalid_regex', args: { findRegex: '(', replaceString: 'X', raw: 'foo' } },
    { id: 'flags_global', args: { findRegex: '/foo/g', replaceString: 'X', raw: 'foo foo' } },
    { id: 'flags_first', args: { findRegex: '/foo/', replaceString: 'X', raw: 'foo foo' } },
    { id: 'flags_case_insensitive', args: { findRegex: '/FOO/i', replaceString: 'X', raw: 'foo FOO' } },
    { id: 'flags_multiline', args: { findRegex: '/^a/m', replaceString: 'X', raw: 'b\na' } },
    { id: 'flags_dotall', args: { findRegex: '/a.b/s', replaceString: 'X', raw: 'a\nb' } },
    { id: 'flags_invalid', args: { findRegex: '/foo/zz', replaceString: 'X', raw: 'foo' } },
    { id: 'no_flags_first_only', args: { findRegex: 'foo', replaceString: 'X', raw: 'foo foo' } },
];


const moduleText = stub + `
const __cases = ${JSON.stringify(cases)};
const __out = [];
for (const c of __cases) {
    const script = {
        findRegex: c.args.findRegex,
        replaceString: c.args.replaceString,
        trimStrings: c.args.trimStrings ?? [],
        disabled: c.args.disabled ?? false,
        substituteRegex: c.args.substituteRegex ?? 0,
    };
    __out.push({ id: c.id, args: c.args, expected: runRegexScript(script, c.args.raw) });
}
return __out;
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
