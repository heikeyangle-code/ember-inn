#!/usr/bin/env node
// getStoppingStrings（script.js:2966）+ getCustomStoppingStrings（power-user.js:3072）
// + getInstructStoppingSequences（instruct-mode.js:301）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩：substituteParams=恒等；EPHEMERAL_STOPPING_STRINGS 由用例设置。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'stopping-strings.json');

const funcs = `
let power_user = {
    context: { names_as_stop_strings: true, use_stop_strings: true, chat_start: '***', example_separator: '***' },
    instruct: {
        enabled: false, wrap: true, macro: true, stop_sequence: '',
        input_sequence: '', output_sequence: '', first_output_sequence: '',
        last_output_sequence: '', system_sequence: '', last_system_sequence: '',
        sequences_as_stop_strings: true,
    },
    single_line: false,
    custom_stopping_strings: '',
    custom_stopping_strings_macro: true,
};
let name1 = 'User';
let name2 = 'Char';
let main_api = 'openai';
let chat = [];
let selected_group = null;
let groups = [];
let characters = [];
let EPHEMERAL_STOPPING_STRINGS = [];
const substituteParams = (text) => String(text ?? '');

function onlyUnique(value, index, array) {
    return array.indexOf(value) === index;
}

function getCustomStoppingStrings(limit = undefined) {
    function getPermanent() {
        try {
            if (!power_user.custom_stopping_strings) {
                return [];
            }
            let strings = JSON.parse(power_user.custom_stopping_strings);
            if (!Array.isArray(strings)) {
                return [];
            }
            strings = strings.filter(s => typeof s === 'string' && s.length > 0);
            if (power_user.custom_stopping_strings_macro) {
                strings = strings.map(x => substituteParams(x));
            }
            return strings;
        } catch (error) {
            return [];
        }
    }
    const permanent = getPermanent();
    const ephemeral = EPHEMERAL_STOPPING_STRINGS;
    const strings = [...permanent, ...ephemeral];
    if (limit > 0) {
        return strings.slice(0, limit);
    }
    return strings;
}

function getInstructStoppingSequences({ customInstruct = null, useStopStrings = null } = {}) {
    const instruct = structuredClone(customInstruct ?? power_user.instruct);
    function addInstructSequence(sequence) {
        const wrap = (s) => instruct.wrap ? '\\n' + s : s;
        if (typeof sequence === 'string' && sequence.length > 0) {
            if (sequence.trim().length > 0) {
                const wrappedSequence = wrap(sequence);
                const stopString = instruct.macro ? substituteParams(wrappedSequence) : wrappedSequence;
                result.push(stopString);
            }
        }
    }
    const result = [];
    if (customInstruct ?? instruct.enabled) {
        const stop_sequence = instruct.stop_sequence || '';
        const input_sequence = instruct.input_sequence?.replace(/{{name}}/gi, name1) || '';
        const output_sequence = instruct.output_sequence?.replace(/{{name}}/gi, name2) || '';
        const first_output_sequence = instruct.first_output_sequence?.replace(/{{name}}/gi, name2) || '';
        const last_output_sequence = instruct.last_output_sequence?.replace(/{{name}}/gi, name2) || '';
        const system_sequence = instruct.system_sequence?.replace(/{{name}}/gi, 'System') || '';
        const last_system_sequence = instruct.last_system_sequence?.replace(/{{name}}/gi, 'System') || '';
        const combined_sequence = [stop_sequence];
        if (instruct.sequences_as_stop_strings) {
            combined_sequence.push(input_sequence, output_sequence, first_output_sequence, last_output_sequence, system_sequence, last_system_sequence);
        }
        combined_sequence.join('\\n').split('\\n').filter(onlyUnique).forEach(addInstructSequence);
    }
    if (useStopStrings ?? power_user.context.use_stop_strings) {
        if (power_user.context.chat_start) {
            result.push(\`\\n\${substituteParams(power_user.context.chat_start)}\`);
        }
        if (power_user.context.example_separator) {
            result.push(\`\\n\${substituteParams(power_user.context.example_separator)}\`);
        }
    }
    return result;
}

function getStoppingStrings(isImpersonate, isContinue, api = main_api) {
    if (api === 'openai') {
        return getCustomStoppingStrings();
    }
    const result = [];
    if (power_user.context.names_as_stop_strings) {
        const charString = \`\\n\${name2}:\`;
        const userString = \`\\n\${name1}:\`;
        result.push(isImpersonate ? charString : userString);
        result.push(userString);
        if (isContinue && Array.isArray(chat) && chat[chat.length - 1]?.is_user) {
            result.push(charString);
        }
        if (selected_group && (name2 || isImpersonate)) {
            const group = groups.find(x => x.id === selected_group);
            if (group && Array.isArray(group.members)) {
                const names = group.members
                    .map(x => characters.find(y => y.avatar == x))
                    .filter(x => x && x.name && x.name !== name2)
                    .map(x => \`\\n\${x.name}:\`);
                result.push(...names);
            }
        }
    }
    result.push(...getInstructStoppingSequences());
    result.push(...getCustomStoppingStrings());
    if (power_user.single_line) {
        result.unshift('\\n');
    }
    return result.filter(x => x).filter(onlyUnique);
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    power_user.context = {',
    '        names_as_stop_strings: b.namesAsStopStrings ?? true,',
    '        use_stop_strings: b.useStopStrings ?? true,',
    '        chat_start: b.chatStart ?? "***",',
    '        example_separator: b.exampleSeparator ?? "***",',
    '    };',
    '    power_user.instruct = {',
    '        enabled: b.instructEnabled ?? false, wrap: b.instructWrap ?? true, macro: b.instructMacro ?? true,',
    '        stop_sequence: b.instructStopSequence ?? "", input_sequence: b.instructInputSequence ?? "",',
    '        output_sequence: b.instructOutputSequence ?? "", first_output_sequence: b.instructFirstOutputSequence ?? "",',
    '        last_output_sequence: b.instructLastOutputSequence ?? "", system_sequence: b.instructSystemSequence ?? "",',
    '        last_system_sequence: b.instructLastSystemSequence ?? "", sequences_as_stop_strings: b.instructSequencesAsStopStrings ?? true,',
    '    };',
    '    power_user.single_line = b.singleLine ?? false;',
    '    power_user.custom_stopping_strings = b.customRaw ?? "";',
    '    power_user.custom_stopping_strings_macro = b.customMacro ?? true;',
    '    EPHEMERAL_STOPPING_STRINGS = b.ephemeral ?? [];',
    '    name1 = b.name1 ?? "User"; name2 = b.name2 ?? "Char"; main_api = b.mainApi ?? "openai";',
    '    chat = b.chatLastIsUser ? [{ is_user: true }] : [];',
    '    selected_group = b.groupId ?? null; groups = b.groups ?? []; characters = b.characters ?? [];',
    '    if (b.method === "custom") return getCustomStoppingStrings(b.limit);',
    '    if (b.method === "get") return getStoppingStrings(b.isImpersonate ?? false, b.isContinue ?? false, b.api ?? "openai");',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('custom-empty', { method: 'custom', customRaw: '' });
await add('custom-array', { method: 'custom', customRaw: '["a","b",""]' });
await add('custom-non-array', { method: 'custom', customRaw: '{"a":1}' });
await add('custom-invalid', { method: 'custom', customRaw: 'bad' });
await add('custom-ephemeral', { method: 'custom', customRaw: '["a"]', ephemeral: ['b', 'c'] });
await add('custom-limit', { method: 'custom', customRaw: '["a","b","c"]', limit: 2 });
await add('get-openai-only-custom', { method: 'get', api: 'openai', customRaw: '["x"]', namesAsStopStrings: true, instructEnabled: true });
await add('get-names', { method: 'get', api: 'kobold', name1: 'Alice', name2: 'Bob', namesAsStopStrings: true });
await add('get-impersonate', { method: 'get', api: 'kobold', isImpersonate: true, name1: 'Alice', name2: 'Bob' });
await add('get-continue-last-user', { method: 'get', api: 'kobold', isContinue: true, chatLastIsUser: true, name1: 'Alice', name2: 'Bob' });
await add('get-group-members', {
    method: 'get', api: 'kobold', name1: 'Alice', name2: 'Bob', groupId: 'g',
    groups: [{ id: 'g', members: ['a', 'b', 'c'] }],
    characters: [{ avatar: 'a', name: 'Alice' }, { avatar: 'b', name: 'Bob' }, { avatar: 'c', name: 'Carol' }],
});
await add('get-instruct-and-custom', {
    method: 'get', api: 'kobold', instructEnabled: true,
    instructStopSequence: '<|end|>', instructOutputSequence: '### Response:',
    customRaw: '["CUSTOM"]', singleLine: false,
});
await add('get-single-line-dedup', {
    method: 'get', api: 'kobold', singleLine: true,
    customRaw: '["\\n","A"]', chatStart: 'A',
});
await add('get-no-names', { method: 'get', api: 'kobold', namesAsStopStrings: false });

writeFileSync(outFile, JSON.stringify({ source: 'getStoppingStrings/getCustomStoppingStrings/getInstructStoppingSequences', cases }, null, 2));
console.log('stopping-strings:', cases.length, 'cases ->', outFile);
