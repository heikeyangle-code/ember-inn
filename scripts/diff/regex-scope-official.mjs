#!/usr/bin/env node
// 正则脚本分桶纯逻辑（regex/engine.js getRegexScripts + getScriptsByType）→ JSON fixture。
// 打桩：extension_settings / characters / presetManager / getCurrentPresetAPI/Name；
// 脚本用 scriptName 标识（官方 RegexScriptData 字段）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'regex-scope.json');

const src = readFileSync(join(officialRef, 'public/scripts/extensions/regex/engine.js'), 'utf8');

function extractFunction(signature, name) {
    const start = src.indexOf(signature);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = src.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < src.length; i++) {
        const ch = src[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(src[j])) j++; if (src[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    let d = 0, k = bodyStart, q = null;
    for (; k < src.length; k++) {
        const ch = src[k];
        if (q) { if (ch === '\\') { k++; continue; } if (ch === q) q = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { q = ch; continue; }
        if (ch === '/' && src[k + 1] === '/') { while (k < src.length && src[k] !== '\n') k++; continue; }
        if (ch === '/' && src[k + 1] === '*') { d++; k++; continue; }
        if (ch === '*' && src[k + 1] === '/') { d--; k++; continue; }
        if (ch === '{') d++;
        else if (ch === '}') { d--; if (d === 0) return src.slice(start, k + 1); }
    }
    throw new Error(`unbalanced: ${name}`);
}

const getScriptsByType = extractFunction('export function getScriptsByType(scriptType, { allowedOnly } = DEFAULT_GET_REGEX_SCRIPTS_OPTIONS)', 'getScriptsByType').replace(/^export /, '');
const getRegexScripts = extractFunction('export function getRegexScripts(options = DEFAULT_GET_REGEX_SCRIPTS_OPTIONS)', 'getRegexScripts').replace(/^export /, '');

const stub = `
const SCRIPT_TYPES = { GLOBAL: 0, PRESET: 2, SCOPED: 1 };
const SCRIPT_TYPE_UNKNOWN = -1;
const DEFAULT_GET_REGEX_SCRIPTS_OPTIONS = Object.freeze({ allowedOnly: false });
const console = { warn: () => {} };
const extension_settings = {
    regex: request.body.global ?? [],
    character_allowed_regex: request.body.scopedAllowed ?? [],
    preset_allowed_regex: { openai: request.body.presetAllowed ?? [] },
};
const characters = [{ avatar: request.body.avatar ?? 'chara', data: { extensions: { regex_scripts: request.body.scoped ?? [] } } }];
const this_chid = 0;
const getCurrentPresetAPI = () => 'openai';
const getCurrentPresetName = () => 'preset';
const presetManager = { readPresetExtensionField: ({ path }) => request.body.preset ?? [] };
const getPresetManager = () => presetManager;
`;

const fn = [stub, getScriptsByType, getRegexScripts].join('\n');
const runCase = new Function('request', [
    fn,
    'return (() => {',
    '    const list = getRegexScripts({ allowedOnly: request.body.allowedOnly ?? false });',
    '    return list.map(x => x.scriptName ?? null);',
    '})();',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase({ body });
    cases.push({ id, args: { body }, expected });
}

await add('all-allowed', { global: [{ scriptName: 'g1' }, { scriptName: 'g2' }], preset: [{ scriptName: 'p1' }], scoped: [{ scriptName: 's1' }] });
await add('allowed-only-scoped-and-preset-allowed', { global: [{ scriptName: 'g1' }, { scriptName: 'g2' }], preset: [{ scriptName: 'p1' }], scoped: [{ scriptName: 's1' }], allowedOnly: true, scopedAllowed: ['chara'], presetAllowed: ['preset'] });
await add('allowed-only-scoped-blocked', { global: [{ scriptName: 'g1' }], preset: [{ scriptName: 'p1' }], scoped: [{ scriptName: 's1' }], allowedOnly: true, scopedAllowed: [] });
await add('allowed-only-preset-blocked', { global: [{ scriptName: 'g1' }], preset: [{ scriptName: 'p1' }], scoped: [{ scriptName: 's1' }], allowedOnly: true, presetAllowed: [] });
await add('global-only', { global: [{ scriptName: 'g1' }] });
await add('empty-all', {});
await add('other-avatar', { global: [{ scriptName: 'g1' }], scoped: [{ scriptName: 's1' }], allowedOnly: true, scopedAllowed: ['other'], presetAllowed: [] });

writeFileSync(outFile, JSON.stringify({ source: 'regex/engine.js getRegexScripts+getScriptsByType', cases }, null, 2));
console.log('regex-scope:', cases.length, 'cases ->', outFile);
