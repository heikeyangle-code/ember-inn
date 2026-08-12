#!/usr/bin/env node
// cleanUpMessage / cleanGroupMessage（script.js）+ fixMarkdown（power-user.js）→ fixture。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   script.js:3112 cleanGroupMessage、script.js:6383 cleanUpMessage
//   power-user.js:408 collapseNewlines、power-user.js:429 fixMarkdown
//   utils.js:883 trimToEndSentence、utils.js:1378 escapeRegex
// 打桩：substituteParams=恒等（prompt bias 由用例给已替换值）、getRegexedString=恒等
// （正则位点由 RegexPipelineEngine 单独差分）、getStoppingStrings=参数传入。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'cleanup.json');

const funcs = `
let power_user = {
    user_prompt_bias: '',
    collapse_newlines: false,
    allow_name1_display: true,
    allow_name2_display: true,
    auto_fix_generated_markdown: false,
    trim_sentences: false,
    trim_spaces: false,
    disable_group_trimming: false,
    instruct: {
        enabled: false,
        stop_sequence: '',
        input_sequence: '',
        output_sequence: '',
        last_output_sequence: '',
        sequences_as_stop_strings: false,
    },
};
let name1 = 'User';
let name2 = 'Char';
let main_api = 'openai';
let selected_group = null;
let groups = [];
let characters = [];
const substituteParams = (text) => String(text ?? '');
const getRegexedString = (text) => text;
const getStoppingStrings = () => [];
const PromptReasoning = { getLatestPrefix: () => '' };

function collapseNewlines(x) {
    return x.replaceAll(/\\n+/g, '\\n');
}

function escapeRegex(string) {
    return string.replace(/[/\\-\\\\^$*+?.()|[\\]{}]/g, '\\\\$&');
}

function fixMarkdown(text, forDisplay) {
    const format = /([*_]{1,2})([\\s\\S]*?)\\1/gm;
    let matches = [];
    let match;
    while ((match = format.exec(text)) !== null) {
        matches.push(match);
    }
    let newText = text;
    for (let i = matches.length - 1; i >= 0; i--) {
        let matchText = matches[i][0];
        let replacementText = matchText.replace(/(\\*|_)([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)|([\\t \\u00a0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]+)(\\*|_)/g, '$1$4');
        newText = newText.slice(0, matches[i].index) + replacementText + newText.slice(matches[i].index + matchText.length);
    }
    if (!forDisplay) {
        return newText;
    }
    const splitText = newText.split('\\n');
    for (let index = 0; index < splitText.length; index++) {
        const line = splitText[index];
        const charsToCheck = ['*', '"'];
        for (const char of charsToCheck) {
            if (line.includes(char) && isOdd(countOccurrences(line, char))) {
                splitText[index] = line.trimEnd() + char;
            }
        }
    }
    newText = splitText.join('\\n');
    return newText;
}

function countOccurrences(str, char) {
    return str.split(char).length - 1;
}

function isOdd(num) {
    return num % 2 !== 0;
}

function trimToEndSentence(input) {
    if (!input) {
        return '';
    }
    const isEmoji = x => /(\\p{Emoji_Presentation}|\\p{Extended_Pictographic})/gu.test(x);
    const punctuation = new Set(['.', '!', '?', '*', '"', ')', '}', '\`', ']', '$', '。', '！', '？', '”', '）', '】', '’', '」', '_']);
    let last = -1;
    const characters = Array.from(input);
    for (let i = characters.length - 1; i >= 0; i--) {
        const char = characters[i];
        const emoji = isEmoji(char);
        if (punctuation.has(char) || emoji) {
            if (!emoji && i > 0 && /[\\s\\n]/.test(characters[i - 1])) {
                last = i - 1;
            } else {
                last = i;
            }
            break;
        }
    }
    if (last === -1) {
        return input.trimEnd();
    }
    return characters.slice(0, last + 1).join('').trimEnd();
}

function cleanGroupMessage(getMessage) {
    if (power_user.disable_group_trimming) {
        return getMessage;
    }
    const group = groups.find((x) => x.id == selected_group);
    if (group && Array.isArray(group.members) && group.members) {
        for (let member of group.members) {
            const character = characters.find(x => x.avatar == member);
            if (!character) {
                continue;
            }
            const name = character.name;
            if (name === name2) {
                continue;
            }
            const regex = new RegExp(\`(^|\\n)\${escapeRegex(name)}:\`);
            const nameMatch = getMessage.match(regex);
            if (nameMatch) {
                getMessage = getMessage.substring(0, nameMatch.index);
            }
        }
    }
    return getMessage;
}

function cleanUpMessage({ getMessage, isImpersonate, isContinue, displayIncompleteSentences = false, stoppingStrings = null, includeUserPromptBias = true, trimNames = true, trimWrongNames = true } = {}) {
    if (!getMessage) {
        return '';
    }
    if (includeUserPromptBias && power_user.user_prompt_bias && !isImpersonate && !isContinue && power_user.user_prompt_bias.length !== 0) {
        getMessage = substituteParams(power_user.user_prompt_bias) + getMessage;
    }
    if (!stoppingStrings) {
        stoppingStrings = getStoppingStrings(isImpersonate, isContinue, main_api);
    }
    for (const stoppingString of stoppingStrings) {
        if (stoppingString.length) {
            for (let j = stoppingString.length; j > 0; j--) {
                if (getMessage.slice(-j) === stoppingString.slice(0, j)) {
                    getMessage = getMessage.slice(0, -j);
                    break;
                }
            }
        }
    }
    getMessage = getRegexedString(getMessage, isImpersonate ? 1 : 2);
    if (power_user.collapse_newlines) {
        getMessage = collapseNewlines(getMessage);
    }
    getMessage = getMessage.replace(/[^\\S\\r\\n]+$/gm, '');
    if (trimWrongNames) {
        let wrongName = isImpersonate
            ? (!power_user.allow_name2_display ? name2 : '')
            : (!power_user.allow_name1_display ? name1 : '');
        if (wrongName) {
            let startIndex = getMessage.indexOf(\`\${wrongName}:\`);
            if (startIndex === 0) {
                getMessage = '';
            }
            startIndex = getMessage.indexOf(\`\\n\${wrongName}:\`);
            if (startIndex >= 0) {
                getMessage = getMessage.substring(0, startIndex);
            }
        }
    }
    if (getMessage.indexOf('<|endoftext|>') != -1) {
        getMessage = getMessage.substring(0, getMessage.indexOf('<|endoftext|>'));
    }
    const isInstruct = power_user.instruct.enabled && main_api !== 'openai';
    const isNotEmpty = (str) => str && str.trim() !== '';
    if (isInstruct && power_user.instruct.stop_sequence) {
        if (getMessage.indexOf(power_user.instruct.stop_sequence) != -1) {
            getMessage = getMessage.substring(0, getMessage.indexOf(power_user.instruct.stop_sequence));
        }
    }
    if (isInstruct && isNotEmpty(power_user.instruct.input_sequence)) {
        if (getMessage.indexOf(power_user.instruct.input_sequence) != -1) {
            getMessage = getMessage.substring(0, getMessage.indexOf(power_user.instruct.input_sequence));
        }
    }
    if (isInstruct && power_user.instruct.sequences_as_stop_strings) {
        const sequences = [
            { value: power_user.instruct.input_sequence, apply: isImpersonate && isNotEmpty(power_user.instruct.input_sequence) },
            { value: power_user.instruct.output_sequence, apply: !isImpersonate && isNotEmpty(power_user.instruct.output_sequence) },
            { value: power_user.instruct.last_output_sequence, apply: !isImpersonate && isNotEmpty(power_user.instruct.last_output_sequence) },
        ];
        for (const seq of sequences.filter(s => s.apply)) {
            seq.value.split('\\n').filter(line => line.trim() !== '').forEach(line => { getMessage = getMessage.replaceAll(line, ''); });
        }
    }
    if (selected_group) {
        getMessage = cleanGroupMessage(getMessage);
    }
    if (!power_user.allow_name2_display) {
        const name2Escaped = escapeRegex(name2);
        getMessage = getMessage.replace(new RegExp(\`(^|\\n)\${name2Escaped}:\\\\s*\`, 'g'), '$1');
    }
    if (isImpersonate) {
        getMessage = getMessage.trim();
    }
    if (power_user.auto_fix_generated_markdown) {
        getMessage = fixMarkdown(getMessage, false);
    }
    if (trimNames) {
        const nameToTrim2 = isImpersonate
            ? (!power_user.allow_name1_display ? name1 : '')
            : (!power_user.allow_name2_display ? name2 : '');
        if (nameToTrim2 && getMessage.startsWith(nameToTrim2 + ':')) {
            getMessage = getMessage.replace(nameToTrim2 + ':', '');
            getMessage = getMessage.trimStart();
        }
    }
    if (isImpersonate) {
        getMessage = getMessage.trim();
    }
    if (!displayIncompleteSentences && power_user.trim_sentences) {
        getMessage = trimToEndSentence(getMessage);
    }
    if (power_user.trim_spaces && !PromptReasoning.getLatestPrefix()) {
        getMessage = getMessage.trim();
    }
    return getMessage;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    power_user = {',
    '        user_prompt_bias: b.userPromptBias ?? "",',
    '        collapse_newlines: b.collapseNewlines ?? false,',
    '        allow_name1_display: b.allowName1Display ?? true,',
    '        allow_name2_display: b.allowName2Display ?? true,',
    '        auto_fix_generated_markdown: b.autoFixMarkdown ?? false,',
    '        trim_sentences: b.trimSentences ?? false,',
    '        trim_spaces: b.trimSpaces ?? false,',
    '        disable_group_trimming: b.disableGroupTrimming ?? false,',
    '        instruct: {',
    '            enabled: b.instructEnabled ?? false,',
    '            stop_sequence: b.instructStopSequence ?? "",',
    '            input_sequence: b.instructInputSequence ?? "",',
    '            output_sequence: b.instructOutputSequence ?? "",',
    '            last_output_sequence: b.instructLastOutputSequence ?? "",',
    '            sequences_as_stop_strings: b.instructSequencesAsStopStrings ?? false,',
    '        },',
    '    };',
    '    name1 = b.name1 ?? "User";',
    '    name2 = b.name2 ?? "Char";',
    '    main_api = b.mainApi ?? "openai";',
    '    selected_group = b.groupId ?? null;',
    '    groups = b.groups ?? [];',
    '    characters = b.characters ?? [];',
    '    PromptReasoning.getLatestPrefix = () => b.hasReasoningPrefix ? "x" : "";',
    '    if (b.method === "fixMarkdown") return fixMarkdown(b.text, b.forDisplay ?? false);',
    '    if (b.method === "cleanGroup") return cleanGroupMessage(b.text);',
    '    if (b.method === "clean") return cleanUpMessage({',
    '        getMessage: b.text,',
    '        isImpersonate: b.isImpersonate ?? false,',
    '        isContinue: b.isContinue ?? false,',
    '        displayIncompleteSentences: b.displayIncompleteSentences ?? false,',
    '        stoppingStrings: b.stoppingStrings ?? null,',
    '        includeUserPromptBias: b.includeUserPromptBias ?? true,',
    '        trimNames: b.trimNames ?? true,',
    '        trimWrongNames: b.trimWrongNames ?? true,',
    '    });',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

// fixMarkdown
await add('fix-spaces', { method: 'fixMarkdown', text: 'a * spaced * b', forDisplay: false });
await add('fix-underscore', { method: 'fixMarkdown', text: 'x _ spaced _ y', forDisplay: false });
await add('fix-mixed-pairs', { method: 'fixMarkdown', text: 'one * a * two ** b ** three', forDisplay: false });
await add('fix-display-odd-star', { method: 'fixMarkdown', text: 'line *', forDisplay: true });
await add('fix-display-odd-quote', { method: 'fixMarkdown', text: 'say "hello', forDisplay: true });
await add('fix-display-noop', { method: 'fixMarkdown', text: '**bold** *em*', forDisplay: true });

// cleanGroupMessage
await add('group-disabled', {
    method: 'cleanGroup', text: 'Alice: hi\\nBob: hello', disableGroupTrimming: true,
    groupId: 'g', groups: [{ id: 'g', members: ['a', 'b'] }],
    characters: [{ avatar: 'a', name: 'Alice' }, { avatar: 'b', name: 'Bob' }], name2: 'Bob',
});
await add('group-cuts-earlier-member', {
    method: 'cleanGroup', text: 'Alice: hi\\nBob: hello', disableGroupTrimming: false,
    groupId: 'g', groups: [{ id: 'g', members: ['a', 'b'] }],
    characters: [{ avatar: 'a', name: 'Alice' }, { avatar: 'b', name: 'Bob' }], name2: 'Bob',
});
await add('group-keeps-current-speaker', {
    method: 'cleanGroup', text: 'Bob: hello\\nAlice: hi', disableGroupTrimming: false,
    groupId: 'g', groups: [{ id: 'g', members: ['a', 'b'] }],
    characters: [{ avatar: 'a', name: 'Alice' }, { avatar: 'b', name: 'Bob' }], name2: 'Bob',
});
await add('group-no-match', {
    method: 'cleanGroup', text: 'Just text', disableGroupTrimming: false,
    groupId: 'g', groups: [{ id: 'g', members: ['a'] }],
    characters: [{ avatar: 'a', name: 'Alice' }], name2: 'Bob',
});

// cleanUpMessage
// 追加穷举：endoftext / 名字裁剪 / instruct / 停用词 / 不完整句 / 折叠换行 / Unicode
await add('fix-display-odd-star-trailing', { method: 'fixMarkdown', text: 'line *  ', forDisplay: true });
await add('fix-display-odd-quote-trailing', { method: 'fixMarkdown', text: 'say "hello  ', forDisplay: true });
await add('fix-display-double-star-spaced', { method: 'fixMarkdown', text: 'a ** b ** c', forDisplay: false });
await add('fix-display-unicode', { method: 'fixMarkdown', text: '你 *好* 世界', forDisplay: false });
await add('clean-endoftext', { method: 'clean', text: 'reply<|endoftext|>more' });
await add('clean-trim-name', { method: 'clean', text: 'Char: hello', name2: 'Char' });
await add('clean-trim-name-off', { method: 'clean', text: 'Char: hello', name2: 'Char', trimNames: false });
await add('clean-wrong-name', { method: 'clean', text: 'Wrong: hi', name2: 'Char' });
await add('clean-unicode-name', { method: 'clean', text: '小炭: 你好', name2: '小炭' });
await add('clean-instruct-output', {
    method: 'clean', text: '### hello', instructEnabled: true, instructOutputSequence: '###',
});
await add('clean-stop-suffix', { method: 'clean', text: 'hello STOP', stoppingStrings: ['STOP'] });
await add('clean-bias-not-impersonate', {
    method: 'clean', text: 'reply', userPromptBias: 'Bias: ', isImpersonate: true, includeUserPromptBias: true,
});
await add('clean-incomplete-off', { method: 'clean', text: 'hello. unfinished sen', displayIncompleteSentences: false });
await add('clean-incomplete-on', { method: 'clean', text: 'hello. unfinished sen', displayIncompleteSentences: true });
await add('clean-collapse-newlines', { method: 'clean', text: 'a\n\n\nb', collapseNewlines: true });
await add('clean-trim-spaces', { method: 'clean', text: '  hello world  ', trimSpaces: true });
await add('clean-prompt-bias', {
    method: 'clean', text: 'reply', userPromptBias: 'Bias: ', includeUserPromptBias: true,
});
await add('clean-prompt-bias-impersonate', {
    method: 'clean', text: 'reply', userPromptBias: 'Bias: ', isImpersonate: true, includeUserPromptBias: true,
});
await add('clean-stopping-partial', {
    method: 'clean', text: 'hello world', stoppingStrings: ['world'],
});
await add('clean-stopping-prefix-only', {
    method: 'clean', text: 'hello wor', stoppingStrings: ['world'],
});
await add('clean-stopping-multiple', {
    method: 'clean', text: 'abc END', stoppingStrings: ['X', 'END'],
});
await add('clean-collapse-newlines', {
    method: 'clean', text: 'a\\n\\n\\nb', collapseNewlines: true,
});
await add('clean-trailing-space-newline', {
    method: 'clean', text: 'a   \\nb\\t\\n',
});
await add('clean-wrong-name-start', {
    method: 'clean', text: 'User: oops\\nActual', name1: 'User', allowName1Display: false,
});
await add('clean-wrong-name-inline', {
    method: 'clean', text: 'Good text\\nUser: bad tail', name1: 'User', allowName1Display: false,
});
await add('clean-wrong-name-impersonate', {
    method: 'clean', text: 'Char: wrong', isImpersonate: true, name2: 'Char', allowName2Display: false,
});
await add('clean-endoftext', {
    method: 'clean', text: 'keep this<|endoftext|>drop',
});
await add('clean-instruct-stop', {
    method: 'clean', text: 'body<|stop|>tail', instructEnabled: true, mainApi: 'kobold', instructStopSequence: '<|stop|>',
});
await add('clean-instruct-input-sequence', {
    method: 'clean', text: 'body\\n### Instruction:\\ntail', instructEnabled: true, mainApi: 'kobold', instructInputSequence: '### Instruction:',
});
await add('clean-instruct-sequences-as-stop', {
    method: 'clean', text: 'hello\\n### Response:\\nworld', instructEnabled: true, mainApi: 'kobold',
    instructOutputSequence: '### Response:\\n', instructSequencesAsStopStrings: true,
});
await add('clean-group-integration', {
    method: 'clean', text: 'Alice: hi\\nBob: hello', groupId: 'g',
    groups: [{ id: 'g', members: ['a', 'b'] }],
    characters: [{ avatar: 'a', name: 'Alice' }, { avatar: 'b', name: 'Bob' }],
    name2: 'Bob',
});
await add('clean-remove-char-name', {
    method: 'clean', text: 'Char: Hello there', name2: 'Char', allowName2Display: false,
});
await add('clean-trim-name-prefix', {
    method: 'clean', text: 'Char:Hello', name2: 'Char', allowName2Display: false, trimNames: true,
});
await add('clean-impersonate-trim', {
    method: 'clean', text: '  padded  ', isImpersonate: true,
});
await add('clean-fix-markdown', {
    method: 'clean', text: 'a * spaced * b', autoFixMarkdown: true,
});
await add('clean-trim-sentence', {
    method: 'clean', text: 'Hello, world! I am from', trimSentences: true,
});
await add('clean-trim-spaces', {
    method: 'clean', text: '  padded  ', trimSpaces: true,
});
await add('clean-trim-spaces-reasoning', {
    method: 'clean', text: '  padded  ', trimSpaces: true, hasReasoningPrefix: true,
});
await add('clean-combo', {
    method: 'clean', text: '  Alice: bad\\nUser: final  ', name1: 'User', allowName1Display: false,
    name2: 'Char', allowName2Display: false, collapseNewlines: true, trimSpaces: true,
});

writeFileSync(outFile, JSON.stringify({ source: 'cleanUpMessage/cleanGroupMessage/fixMarkdown', cases }, null, 2));
console.log('cleanup:', cases.length, 'cases ->', outFile);
