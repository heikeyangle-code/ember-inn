#!/usr/bin/env node
// 正则整体管线（regex/engine.js getRegexedString）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'regex-pipeline.json');
const engineSrc = readFileSync(join(officialRef, 'public', 'scripts', 'extensions', 'regex', 'engine.js'), 'utf8');
const utilsSrc = readFileSync(join(officialRef, 'public', 'scripts', 'utils.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false, prev='';
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i+1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '/' && source[i+1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i+1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; prev=ch; continue; }
        if (ch === '/' && !/[A-Za-z0-9_)\]}"']/.test(prev)) { inRegex = true; continue; }
        if (/\s/.test(ch)) continue;
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
        prev = ch;
    }
    throw new Error('unbalanced');
}

function extractFunction(source, name) {
    const start = source.indexOf(`function ${name}`);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = source.indexOf('(', start);
    let depth=0, bodyStart=-1, inString=null;
    for (let i=parenStart;i<source.length;i++){
        const ch=source[i];
        if(inString){if(ch==='\\'){i++;continue;}if(ch===inString)inString=null;continue;}
        if(ch==='"'||ch==="'"||ch==='`'){inString=ch;continue;}
        if(ch==='(')depth++;
        else if(ch===')'){depth--;if(depth===0){let j=i+1;while(/\s/.test(source[j]))j++;if(source[j]==='{')bodyStart=j;break;}}
    }
    if(bodyStart<0)throw new Error(`no body: ${name}`);
    return source.slice(start, scanBody(source, bodyStart)+1);
}

function extractClass(source, name) {
    const start = source.indexOf(`class ${name}`);
    const brace = source.indexOf('{', start);
    return source.slice(start, scanBody(source, brace)+1);
}

const runRegexScript = extractFunction(engineSrc, 'runRegexScript');
const sanitizeRegexMacro = extractFunction(engineSrc, 'sanitizeRegexMacro');
const filterString = extractFunction(engineSrc, 'filterString');
const getRegexedString = extractFunction(engineSrc, 'getRegexedString');
const regexProvider = extractClass(engineSrc, 'RegexProvider');
const regexFromString = extractFunction(utilsSrc, 'regexFromString');

const stub = `
let extension_settings = { disabledExtensions: [] };
let scripts = [];
const substitute_find_regex = { NONE: 0, RAW: 1, ESCAPED: 2 };
function substituteParams(text, options = {}) { if (!text) return ''; return String(text).replace(/\{\{char\}\}/gi, options.name2Override ?? ''); }
function substituteParamsExtended(text, options = {}, postProcess) { const out = substituteParams(text, options); return typeof postProcess === 'function' ? postProcess(out) : out; }
function getRegexScripts() { return scripts; }
function consoleWarn() {}
${regexFromString}
${regexProvider}
${sanitizeRegexMacro}
${filterString}
${runRegexScript}
${getRegexedString}
`;

const runner = new Function('module','exports','require', stub + `
const __cases = ${JSON.stringify([])};
`);
// We'll build runner differently with cases inline in string below
const runCase = new Function(stub + `
return async (request) => {
    extension_settings = { disabledExtensions: request.body.disabledExtensions ?? [] };
    scripts = request.body.scripts ?? [];
    return getRegexedString(request.body.raw, request.body.placement, {
        characterOverride: request.body.characterOverride ?? undefined,
        isMarkdown: request.body.isMarkdown ?? false,
        isPrompt: request.body.isPrompt ?? false,
        isEdit: request.body.isEdit ?? false,
        depth: request.body.depth ?? undefined,
    });
};
`);

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const baseScript = { findRegex: 'foo', replaceString: 'X', placement: [1], markdownOnly: false, promptOnly: false, runOnEdit: true };

await add('basic', { raw: 'foo foo', placement: 1, scripts: [{ ...baseScript, findRegex: '/foo/g' }] });
await add('wrong-placement', { raw: 'foo', placement: 2, scripts: [{ ...baseScript }] });
await add('markdown-only', { raw: 'foo', placement: 1, isMarkdown: true, scripts: [{ ...baseScript, markdownOnly: true }] });
await add('markdown-skip', { raw: 'foo', placement: 1, isMarkdown: false, scripts: [{ ...baseScript, markdownOnly: true }] });
await add('prompt-only', { raw: 'foo', placement: 1, isPrompt: true, scripts: [{ ...baseScript, promptOnly: true }] });
await add('edit-skip', { raw: 'foo', placement: 1, isEdit: true, scripts: [{ ...baseScript, runOnEdit: false }] });
await add('depth-min', { raw: 'foo', placement: 1, depth: 1, scripts: [{ ...baseScript, minDepth: 2 }] });
await add('depth-max', { raw: 'foo', placement: 1, depth: 5, scripts: [{ ...baseScript, maxDepth: 3 }] });
await add('disabled-extension', { raw: 'foo', placement: 1, disabledExtensions: ['regex'], scripts: [{ ...baseScript }] });
await add('trim-char-override', { raw: 'hello Alice', placement: 1, characterOverride: 'Alice', scripts: [{ ...baseScript, findRegex: '(Alice)', replaceString: '$1', trimStrings: ['{{char}}'] }] });

writeFileSync(outFile, JSON.stringify({ source: 'regex/engine.js getRegexedString', cases }, null, 2));
console.log('regex-pipeline:', cases.length, 'cases ->', outFile);
