#!/usr/bin/env node
// 官方宏引擎 e2e 用例 → JSON fixture 生成器。
// 从 tests/frontend/MacroEngine.e2e.js 提取「字面输入 + 字面输出」的用例，
// 只保留与引擎环境无关（name1=User/name2=Character、空变量表）的 section。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'macros.json');

const src = readFileSync(join(officialRef, 'tests', 'frontend', 'MacroEngine.e2e.js'), 'utf8');
const lines = src.split('\n');

const WHITELIST = [
    'MacroEngine/Basic evaluation',
    'MacroEngine/Unnamed arguments',
    'MacroEngine/Nested macros',
    'MacroEngine/Unknown macros',
    'MacroEngine/Comment macro',
    'MacroEngine/Trim macro',
    'MacroEngine/Legacy compatibility',
    'MacroEngine/{{if}} conditional macro',
    'MacroEngine/Variable Shorthand Syntax',
    'MacroEngine/Variable Macros (hasvar, deletevar)',
    'MacroEngine/Bracket handling around macros',
];

function unescapeJs(str) {
    return str
        .replace(/\\n/g, '\n')
        .replace(/\\r/g, '\r')
        .replace(/\\t/g, '\t')
        .replace(/\\'/g, "'")
        .replace(/\\\\/g, '\\');
}

const stack = []; // { name, indent }
const cases = [];
let i = 0;
while (i < lines.length) {
    const line = lines[i];
    // test.beforeEach/beforeAll/afterEach/afterAll：整块跳过，避免闭合行干扰 describe 栈
    const hook = line.match(/^\s*test\.(beforeEach|beforeAll|afterEach|afterAll)\(/);
    if (hook) {
        const indent = line.match(/^(\s*)/)[1].length;
        let j = i + 1;
        while (j < lines.length) {
            const close = lines[j].match(/^(\s*)\}\);\s*$/);
            if (close && close[1].length <= indent) break;
            j++;
        }
        i = j + 1;
        continue;
    }
    const d = line.match(/^\s*test\.describe\('([^']+)'/);
    if (d) {
        stack.push({ name: d[1], indent: line.match(/^(\s*)/)[1].length });
        i++;
        continue;
    }
    const t = line.match(/^\s*test\('([^']+)', async \(\{ page \}\) => \{/);
    if (t) {
        const indent = line.match(/^(\s*)/)[1].length;
        let j = i + 1;
        let input = null;
        let expected = null;
        let localVars = null;
        let globalVars = null;
        while (j < lines.length) {
            const close = lines[j].match(/^(\s*)\}\);\s*$/);
            if (close && close[1].length <= indent) break;
            const im = lines[j].match(/const input = '((?:[^'\\]|\\.)*)';/);
            if (im) input = im[1];
            const em = lines[j].match(/expect\(output\)\.toBe\('((?:[^'\\]|\\.)*)'\);/);
            if (em) expected = em[1];
            const vm = lines[j].match(/evaluateWithEngineAndVariables\(page, '((?:[^'\\]|\\.)*)', \{ (local: \{[^}]*\})?(?:, )?(global: \{[^}]*\})? \}\)/);
            if (vm) {
                input = vm[1];
                const parseVars = (s) => {
                    if (!s) return null;
                    const eq = s.indexOf(':');
                    const obj = s.substring(eq + 1)
                        .replace(/'/g, '"')
                        .replace(/([{,]\s*)([A-Za-z0-9_-]+)\s*:/g, '$1"$2":');
                    return JSON.parse(obj);
                };
                if (vm[2]) localVars = parseVars(vm[2]);
                if (vm[3]) globalVars = parseVars(vm[3]);
            }
            j++;
        }
        const path = stack.map((s) => s.name).join('/');
        if (input !== null && expected !== null && WHITELIST.some((w) => path === w || path.startsWith(w + '/'))) {
            cases.push({
                id: `${path} :: ${t[1]}`,
                input: unescapeJs(input),
                expected: unescapeJs(expected),
                local: localVars,
                global: globalVars,
            });
        }
        i = j + 1;
        continue;
    }
    const closeLine = line.match(/^(\s*)\}\);\s*$/);
    if (closeLine) {
        const closeIndent = closeLine[1].length;
        while (stack.length > 0 && closeIndent <= stack[stack.length - 1].indent) {
            stack.pop();
        }
    }
    i++;
}

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref tests/frontend/MacroEngine.e2e.js', cases }, null, 2) + '\n');
console.log(`wrote ${outFile} (${cases.length} cases)`);
