#!/usr/bin/env node
// SlashCommandParser 参数解析核心（parseCommand/parseNamedArgument/parseUnnamedArgument/
// parseQuotedValue/parseListValue/parseValue/testSymbol/testCommandEnd）→ JSON fixture。
// 方法体照官方 release 8172dcd 逐字提取；打桩：commands（echo rawQuotes / let·setvar split1 /
// qr-arg split2）、scope（根闭包）、闭包（testClosure=false）、宏索引（no-op）、REPLACE_GETVAR（新宏引擎 no-op）。
// 覆盖 loose/strict、rawQuotes、split+count、list、宏括号内管道、转义定界符。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'slash-parser.json');

const funcs = `
class SlashCommandNamedArgumentAssignment { constructor() { this.name = ''; this.value = undefined; } }
class SlashCommandUnnamedArgumentAssignment { constructor() { this.value = undefined; this.start = 0; this.end = 0; } }
class SlashCommandExecutor {
    constructor(start) {
        this.start = start; this.end = start;
        this.name = ''; this.command = null;
        this.namedArgumentList = []; this.unnamedArgumentList = [];
        this.startNamedArgs = start; this.endNamedArgs = start;
        this.startUnnamedArgs = start; this.endUnnamedArgs = start;
        this.parserFlags = {}; this.injectPipe = true;
    }
}
function isFalseBoolean(value) { return ['false', 'off', 'no', '0'].includes(String(value).toLowerCase()); }

const COMMANDS = {
    echo:   { rawQuotes: true,  unnamedArgumentList: [{}], splitUnnamedArgument: false },
    sys:    { rawQuotes: true,  unnamedArgumentList: [{}], splitUnnamedArgument: false },
    sendas: { rawQuotes: true,  unnamedArgumentList: [{}], splitUnnamedArgument: false },
    send:   { rawQuotes: true,  unnamedArgumentList: [{}], splitUnnamedArgument: false },
    comment:{ rawQuotes: true,  unnamedArgumentList: [{}], splitUnnamedArgument: false },
    sysname:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    persona:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    pass:   { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    let:    { rawQuotes: false, unnamedArgumentList: [{}, {}], splitUnnamedArgument: true, splitUnnamedArgumentCount: 1 },
    setvar: { rawQuotes: false, unnamedArgumentList: [{}, {}], splitUnnamedArgument: true, splitUnnamedArgumentCount: 1 },
    'qr-arg': { rawQuotes: false, unnamedArgumentList: [{}, {}], splitUnnamedArgument: true, splitUnnamedArgumentCount: 2 },
    'message-role': { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    'message-name': { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    hide:   { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    unhide: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    delname:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    addswipe:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    delswipe:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    getvar: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    addvar: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    incvar: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    decvar: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    if:     { rawQuotes: false, unnamedArgumentList: [{}, {}], splitUnnamedArgument: false },
    upper:  { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    lower:  { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    substr: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    replace:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    trimstart:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    trimend:{ rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    tokens: { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: false },
    add:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    sub:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    mul:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    div:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    mod:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    pow:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    max:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    min:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    abs:    { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    sqrt:   { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
    round:  { rawQuotes: false, unnamedArgumentList: [{}], splitUnnamedArgument: true },
};

class Parser {
    constructor(text, strict = false) {
        this.text = text;
        this.index = 0;
        this.jumpedEscapeSequence = false;
        this.flags = { 1: strict, 2: false };
        this.verifyCommandNames = true;
        this.commands = COMMANDS;
        this.scope = { parent: null, variableNames: [] };
        this.scope.getCopy = function () { return { parent: this.parent, variableNames: [...this.variableNames] }; };
        this.commandIndex = [];
        this.scopeIndex = [];
    }
    get char() { return this.text[this.index]; }
    get ahead() { return this.text.slice(this.index + 1); }
    get behind() { return this.text.slice(0, this.index); }
    get endOfText() { return this.index >= this.text.length || (/\\s/.test(this.char) && /^\\s+$/.test(this.ahead)); }
    take(length = 1) {
        this.jumpedEscapeSequence = false;
        let content = this.char;
        this.index++;
        if (length > 1) { content = this.take(length - 1); }
        return content;
    }
    discardWhitespace() {
        while (/\\s/.test(this.char)) {
            this.take();
            this.jumpedEscapeSequence = false;
        }
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
    testClosure() { return false; }
    testClosureEnd() {
        if (!this.scope.parent) {
            if (this.index >= this.text.length) return true;
            return false;
        }
        return this.testSymbol(':}');
    }
    testCommandEnd() {
        if (this.testClosureEnd()) return true;
        if (this.testSymbol('|') && !this.isInsideMacroBraces()) return true;
        return false;
    }
    isInsideMacroBraces() {
        const textBehind = this.behind;
        let depth = 0;
        for (let i = 0; i < textBehind.length; i++) {
            if (textBehind[i] === '{' && textBehind[i + 1] === '{') { depth++; i++; }
            else if (textBehind[i] === '}' && textBehind[i + 1] === '}') { depth = Math.max(0, depth - 1); i++; }
        }
        return depth > 0;
    }
    indexMacros() {}
    replaceGetvar(value) { return value; }
    testNamedArgument() { return /^(\\w+)=/.test(\`\${this.char}\${this.ahead}\`); }
    parseNamedArgument() {
        let assignment = new SlashCommandNamedArgumentAssignment();
        assignment.start = this.index;
        let key = '';
        while (/\\w/.test(this.char)) key += this.take();
        this.take();
        assignment.name = key;
        if (this.testClosure()) { assignment.value = null; }
        else if (this.testQuotedValue()) { assignment.value = this.parseQuotedValue(); }
        else if (this.testListValue()) { assignment.value = this.parseListValue(); }
        else if (this.testValue()) { assignment.value = this.parseValue(); }
        assignment.end = this.index;
        return assignment;
    }
    testUnnamedArgument() { return !this.testCommandEnd(); }
    testUnnamedArgumentEnd() { return this.testCommandEnd(); }
    parseUnnamedArgument(split, splitCount = null, rawQuotes = false) {
        const wasSplit = split;
        let value = this.jumpedEscapeSequence ? this.take() : '';
        let isList = split;
        let listValues = [];
        let listQuoted = [];
        let assignment = new SlashCommandUnnamedArgumentAssignment();
        assignment.start = this.index;
        if (!split && !rawQuotes && this.testQuotedValue()) {
            assignment.value = this.parseQuotedValue();
            assignment.end = this.index;
            isList = true;
            listValues.push(assignment);
            listQuoted.push(true);
            assignment = new SlashCommandUnnamedArgumentAssignment();
            assignment.start = this.index;
        }
        while (!this.testUnnamedArgumentEnd()) {
            if (split && splitCount && listValues.length >= splitCount) {
                split = false;
                if (this.testQuotedValue()) {
                    assignment.value = this.parseQuotedValue();
                    assignment.end = this.index;
                    listValues.push(assignment);
                    listQuoted.push(true);
                    assignment = new SlashCommandUnnamedArgumentAssignment();
                    assignment.start = this.index;
                }
            }
            if (this.testClosure()) {
                isList = true;
                if (value.length > 0) {
                    assignment.value = value;
                    listValues.push(assignment);
                    listQuoted.push(false);
                    assignment = new SlashCommandUnnamedArgumentAssignment();
                    assignment.start = this.index;
                }
                assignment.start = this.index;
                assignment.value = 'CLOSURE';
                assignment.end = this.index;
                listValues.push(assignment);
                assignment = new SlashCommandUnnamedArgumentAssignment();
                assignment.start = this.index;
                if (split) this.discardWhitespace();
            } else if (split) {
                if (this.testQuotedValue()) {
                    assignment.start = this.index;
                    assignment.value = this.parseQuotedValue();
                    assignment.end = this.index;
                    listValues.push(assignment);
                    listQuoted.push(true);
                    assignment = new SlashCommandUnnamedArgumentAssignment();
                } else if (this.testListValue()) {
                    assignment.start = this.index;
                    assignment.value = this.parseListValue();
                    assignment.end = this.index;
                    listValues.push(assignment);
                    listQuoted.push(false);
                    assignment = new SlashCommandUnnamedArgumentAssignment();
                } else if (this.testValue()) {
                    assignment.start = this.index;
                    assignment.value = this.parseValue();
                    assignment.end = this.index;
                    listValues.push(assignment);
                    listQuoted.push(false);
                    assignment = new SlashCommandUnnamedArgumentAssignment();
                } else {
                    throw new Error('Unexpected end of unnamed argument');
                }
                this.discardWhitespace();
            } else {
                value += this.take();
                assignment.end = this.index;
            }
        }
        if (isList && value.length > 0) {
            assignment.value = value;
            listValues.push(assignment);
            listQuoted.push(false);
        }
        if (isList) {
            const firstVal = listValues[0];
            if (typeof firstVal?.value == 'string') {
                if (!listQuoted[0]) { firstVal.value = firstVal.value.trimStart(); }
                if (firstVal.value.length == 0) { listValues.shift(); listQuoted.shift(); }
            }
            const lastVal = listValues.slice(-1)[0];
            if (typeof lastVal?.value == 'string') {
                if (!listQuoted.slice(-1)[0]) { lastVal.value = lastVal.value.trimEnd(); }
                if (lastVal.value.length == 0) { listValues.pop(); listQuoted.pop(); }
            }
            if (wasSplit && splitCount && splitCount + 1 < listValues.length) {
                const joined = new SlashCommandUnnamedArgumentAssignment();
                joined.start = listValues[splitCount].start;
                joined.end = listValues.slice(-1)[0].end;
                joined.value = '';
                for (let i = splitCount; i < listValues.length; i++) {
                    if (listQuoted[i]) joined.value += '"' + listValues[i].value + '"';
                    else joined.value += listValues[i].value;
                }
                listValues = [...listValues.slice(0, splitCount), joined];
            }
            return listValues;
        }
        this.indexMacros(this.index - value.length, value);
        value = value.trim();
        if (this.flags[2]) { value = this.replaceGetvar(value); }
        assignment.value = value;
        return [assignment];
    }
    testQuotedValue() { return this.testSymbol('"'); }
    testQuotedValueEnd() {
        if (this.endOfText) {
            if (this.verifyCommandNames) throw new Error('Unexpected end of quoted value');
            else return true;
        }
        if (!this.verifyCommandNames && this.testClosureEnd()) return true;
        if (this.verifyCommandNames && !this.flags[1] && this.testCommandEnd()) {
            throw new Error('Unexpected end of quoted value');
        }
        return this.testSymbol('"') || (!this.flags[1] && this.testCommandEnd());
    }
    parseQuotedValue() {
        this.take();
        let value = '';
        while (!this.testQuotedValueEnd()) value += this.take();
        this.take();
        if (this.flags[2]) { value = this.replaceGetvar(value); }
        this.indexMacros(this.index - value.length, value);
        return value;
    }
    testListValue() { return this.testSymbol('['); }
    testListValueEnd() {
        if (this.endOfText) throw new Error('Unexpected end of list value');
        return this.testSymbol(']');
    }
    parseListValue() {
        let value = this.take();
        while (!this.testListValueEnd()) value += this.take();
        value += this.take();
        if (this.flags[2]) { value = this.replaceGetvar(value); }
        this.indexMacros(this.index - value.length, value);
        return value;
    }
    testValue() { return !this.testSymbol(/\\s/); }
    testValueEnd() {
        if (this.testSymbol(/\\s/)) return true;
        return this.testCommandEnd();
    }
    parseValue() {
        let value = this.jumpedEscapeSequence ? this.take() : '';
        while (!this.testValueEnd()) value += this.take();
        if (this.flags[2]) { value = this.replaceGetvar(value); }
        this.indexMacros(this.index - value.length, value);
        return value;
    }
    parseCommand() {
        const start = this.index + 1;
        const cmd = new SlashCommandExecutor(start);
        cmd.parserFlags = Object.assign({}, this.flags);
        this.commandIndex.push(cmd);
        this.scopeIndex.push(this.scope.getCopy());
        this.take();
        while (!/\\s/.test(this.char) && !this.testCommandEnd()) cmd.name += this.take();
        this.discardWhitespace();
        if (this.verifyCommandNames && !this.commands[cmd.name]) throw new Error('Unknown command: /' + cmd.name);
        cmd.command = this.commands[cmd.name];
        cmd.startNamedArgs = this.index;
        cmd.endNamedArgs = this.index;
        while (this.testNamedArgument()) {
            const arg = this.parseNamedArgument();
            cmd.namedArgumentList.push(arg);
            cmd.endNamedArgs = this.index;
            this.discardWhitespace();
        }
        this.discardWhitespace();
        cmd.startUnnamedArgs = this.index - (/\\s(\\s*)$/s.exec(this.behind)?.[1]?.length ?? 0);
        cmd.endUnnamedArgs = this.index;
        if (this.testUnnamedArgument()) {
            const rawQuotesArg = cmd?.namedArgumentList?.find(a => a.name === 'raw');
            const rawQuotes = cmd?.command?.rawQuotes && rawQuotesArg ? !isFalseBoolean(rawQuotesArg?.value?.toString()) : cmd?.command?.rawQuotes;
            cmd.unnamedArgumentList = this.parseUnnamedArgument(cmd.command?.unnamedArgumentList?.length && cmd?.command?.splitUnnamedArgument, cmd?.command?.splitUnnamedArgumentCount, rawQuotes);
            cmd.endUnnamedArgs = this.index;
            if (cmd.name == 'let') {
                const keyArg = cmd.namedArgumentList.find(it => it.name == 'key');
                if (keyArg) {
                    this.scope.variableNames.push(keyArg.value.toString());
                } else if (typeof cmd.unnamedArgumentList[0]?.value == 'string') {
                    this.scope.variableNames.push(cmd.unnamedArgumentList[0].value);
                }
            }
        }
        if (this.testCommandEnd()) {
            cmd.end = this.index;
            return cmd;
        } else {
            throw new Error('Unexpected end of command');
        }
    }
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const p = new Parser(request.body.text, request.body.strict ?? false);',
    '    const cmd = p.parseCommand();',
    '    return {',
    '        name: cmd.name,',
    '        named: Object.fromEntries(cmd.namedArgumentList.map(a => [a.name, String(a.value)])),',
    '        unnamed: cmd.unnamedArgumentList.map(a => String(a.value)),',
    '        index: cmd.end,',
    '    };',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('basic-named-unnamed', { text: '/sendas name=柳春娘 at=1 你好啊' });
await add('quoted-unnamed', { text: '/sys "hello world"' });
await add('quoted-named', { text: '/persona name="柳 春 娘"' });
await add('echo-rawQuotes', { text: '/echo "hello world"' });
await add('echo-rawQuotes-one-escape-pipe', { text: '/echo a \\| b' });
await add('echo-rawQuotes-two-escape-pipe-loose', { text: '/echo a \\\\| b' });
await add('echo-rawQuotes-two-escape-pipe-strict', { text: '/echo a \\\\| b', strict: true });
await add('let-split-merge', { text: '/let key=greeting Hello World' });
await add('let-named-list', { text: '/let key=[a|b|c]' });
await add('qr-arg-split-two', { text: '/qr-arg hello world' });
await add('setvar-split-merge', { text: '/setvar key=x a b c' });
await add('sys-list-unnamed', { text: '/sys [a|b|c]' });
await add('macro-braces-pipe-not-split', { text: '/sys {{foo|bar}}' });
await add('quoted-escaped-quote-loose', { text: '/persona name="a\\"b"' });
await add('quoted-escaped-quote-strict', { text: '/persona name="a\\"b"', strict: true });
await add('quoted-two-escape-strict', { text: '/persona name="a\\\\"b"', strict: true });
await add('named-empty-value', { text: '/persona name=' });
await add('echo-escaped-closure-text', { text: '/echo \\{:' });
await add('sendas-rawquotes-quoted', { text: '/sendas name=小红 "你好 呀"' });
await add('sendas-raw-false-override', { text: '/sendas raw=false name=小红 "你好 呀"' });
await add('sys-rawquotes-name', { text: '/sys name=旁白 雪很大。' });
await add('comment-rawquotes', { text: '/comment 这条是评论消息' });
await add('send-rawquotes', { text: '/send "我 想 说"' });
await add('message-role-negative-at', { text: '/message-role at=-1 assistant' });
await add('message-name-at', { text: '/message-name at=0 小红' });
await add('hide-range', { text: '/hide 2-4' });
await add('hide-name', { text: '/hide name=小炭 3' });
await add('unhide-last', { text: '/unhide' });
await add('delname', { text: '/delname 小明' });
await add('addswipe-switch', { text: '/addswipe switch=true 新回复' });
await add('delswipe-id', { text: '/delswipe 2' });
await add('getvar-named', { text: '/getvar key=height' });
await add('addvar-named', { text: '/addvar key=score 10' });
await add('incvar', { text: '/incvar score' });
await add('upper', { text: '/upper 你好' });
await add('lower', { text: '/lower HELLO' });
await add('substr', { text: '/substr start=1 end=3 abcdef' });
await add('replace', { text: '/replace pattern=abc replacer=x abcabc' });
await add('trimstart', { text: '/trimstart 这是第一句。第二句。' });
await add('tokens', { text: '/tokens 你好世界' });
await add('add-split', { text: '/add 1 2 3' });
await add('mul-split', { text: '/mul 2 3 4' });
await add('if-then-else', { text: '/if left=a right=b rule=eq {:x:} {:y:}' });


writeFileSync(outFile, JSON.stringify({ source: 'SlashCommandParser parseCommand core', cases }, null, 2));
console.log('slash-parser:', cases.length, 'cases ->', outFile);
