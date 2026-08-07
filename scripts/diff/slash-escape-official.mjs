#!/usr/bin/env node
// SlashCommandParser testSymbol/testSymbolLooseyGoosey（转义判定）→ JSON fixture。
// 方法体照官方实现；仅依赖 text/index/flags/jumpedEscapeSequence。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'slash-escape.json');

const funcs = `
class Parser {
    constructor(text, index, strict, jumped = false) {
        this.text = text; this.index = index; this.jumpedEscapeSequence = jumped;
        this.flags = { 1: strict };
    }
    testSymbol(sequence, offset = 0) {
        if (!this.flags[1]) return this.testSymbolLooseyGoosey(sequence, offset);
        const escapeOffset = this.jumpedEscapeSequence ? -1 : 0;
        const escapes = this.text.slice(this.index + offset + escapeOffset).replace(/^(\\\\*).*$/s, '$1').length;
        const test = (sequence instanceof RegExp) ? (text) => new RegExp('^' + sequence.source).test(text) : (text) => text.startsWith(sequence);
        if (test(this.text.slice(this.index + offset + escapeOffset + escapes))) {
            if (escapes == 0) return true;
            if (!this.jumpedEscapeSequence && offset == 0) { this.index++; this.jumpedEscapeSequence = true; }
            return false;
        }
        return false;
    }
    testSymbolLooseyGoosey(sequence, offset = 0) {
        const escapeOffset = this.jumpedEscapeSequence ? -1 : 0;
        const escapes = this.text[this.index + offset + escapeOffset] == '\\\\' ? 1 : 0;
        const test = (sequence instanceof RegExp) ? (text) => new RegExp('^' + sequence.source).test(text) : (text) => text.startsWith(sequence);
        if (test(this.text.slice(this.index + offset + escapeOffset + escapes))) {
            if (escapes == 0) return true;
            if (!this.jumpedEscapeSequence && offset == 0) { this.index++; this.jumpedEscapeSequence = true; }
            return false;
        }
        return false;
    }
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const p = new Parser(request.body.text, request.body.index ?? 0, request.body.strict ?? false, request.body.jumped ?? false);',
    '    const found = p.testSymbol(request.body.sequence, request.body.offset ?? 0);',
    '    return { found, index: p.index, jumped: p.jumpedEscapeSequence };',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('loose-pipe', { text: 'abc | def', index: 4, sequence: '|', strict: false });
await add('loose-escaped', { text: 'abc \\| def', index: 4, sequence: '|', strict: false });
await add('strict-plain', { text: 'abc | def', index: 4, sequence: '|', strict: true });
await add('strict-one-escape', { text: 'abc \\| def', index: 4, sequence: '|', strict: true });
await add('strict-two-escape', { text: 'abc \\\\| def', index: 4, sequence: '|', strict: true });
await add('strict-three-escape', { text: 'abc \\\\\\| def', index: 4, sequence: '|', strict: true });
await add('strict-jumped', { text: 'abc \\\\| def', index: 5, sequence: '|', strict: true, jumped: true });
await add('closure-start', { text: 'abc {:', index: 4, sequence: '{:', strict: true });
await add('closure-escaped', { text: 'abc \\{:', index: 4, sequence: '{:', strict: true });
await add('offset-pipe', { text: 'a | b', index: 2, sequence: '|', strict: true, offset: 1 });

writeFileSync(outFile, JSON.stringify({ source: 'SlashCommandParser testSymbol', cases }, null, 2));
console.log('slash-escape:', cases.length, 'cases ->', outFile);
