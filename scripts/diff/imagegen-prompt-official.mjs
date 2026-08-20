#!/usr/bin/env node
// 官方 stable-diffusion 扩展 prompt 纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   extensions/stable-diffusion/index.js：generationMode/triggerWords/messageTrigger/promptTemplates/
//     getGenerationType/getQuietPrompt/processTriggers（纯逻辑）/processReply
//   scripts/utils.js：stringFormat
// 打桩（脚本头部登记）：processTriggers 的 abort/setTimeout/generatePicture 网络段不移植（App 接线）。
// 覆盖：getGenerationType（triggerWords/multimodal/free_extend 全分支）+ getQuietPrompt + stringFormat
//       + parseInteractiveTrigger（activationRegex 匹配 + specialCases 替换 + mode 解析）
//       + processReply（minimal 与常规清洗两分支）+ promptTemplates 全量逐字。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'imagegen-prompt.json');

// ---------- 官方 utils.js stringFormat L757-764 ----------
function stringFormat(format, ...args) {
    return format.replace(/{(\d+)}/g, function (match, number) {
        return typeof args[number] != 'undefined'
            ? args[number]
            : match;
    });
}

// ---------- 官方 index.js L113-160 ----------
const generationMode = {
    TOOL: -2, MESSAGE: -1, CHARACTER: 0, USER: 1, SCENARIO: 2,
    RAW_LAST: 3, NOW: 4, FACE: 5, FREE: 6, BACKGROUND: 7,
    CHARACTER_MULTIMODAL: 8, USER_MULTIMODAL: 9, FACE_MULTIMODAL: 10, FREE_EXTENDED: 11,
};

const modeLabels = {
    [generationMode.TOOL]: 'Function Tool Prompt Description',
    [generationMode.MESSAGE]: 'Chat Message Template',
    [generationMode.CHARACTER]: 'Character ("Yourself")',
    [generationMode.FACE]: 'Portrait ("Your Face")',
    [generationMode.USER]: 'User ("Me")',
    [generationMode.SCENARIO]: 'Scenario ("The Whole Story")',
    [generationMode.NOW]: 'Last Message',
    [generationMode.RAW_LAST]: 'Raw Last Message',
    [generationMode.BACKGROUND]: 'Background',
    [generationMode.CHARACTER_MULTIMODAL]: 'Character (Multimodal Mode)',
    [generationMode.FACE_MULTIMODAL]: 'Portrait (Multimodal Mode)',
    [generationMode.USER_MULTIMODAL]: 'User (Multimodal Mode)',
    [generationMode.FREE_EXTENDED]: 'Free Mode (LLM-Extended)',
};

const triggerWords = {
    [generationMode.CHARACTER]: ['you'],
    [generationMode.USER]: ['me'],
    [generationMode.SCENARIO]: ['scene'],
    [generationMode.RAW_LAST]: ['raw_last'],
    [generationMode.NOW]: ['last'],
    [generationMode.FACE]: ['face'],
    [generationMode.BACKGROUND]: ['background'],
};

const messageTrigger = {
    activationRegex: /\b(send|mail|imagine|generate|make|create|draw|paint|render|show)\b.{0,10}\b(pic|picture|image|drawing|painting|photo|photograph)\b(?:\s+of)?(?:\s+(?:a|an|the|this|that|those|your)?\s+)?(.+)/i,
    specialCases: {
        [generationMode.CHARACTER]: ['you', 'yourself'],
        [generationMode.USER]: ['me', 'myself'],
        [generationMode.SCENARIO]: ['story', 'scenario', 'whole story'],
        [generationMode.NOW]: ['last message'],
        [generationMode.FACE]: ['face', 'portrait', 'selfie'],
        [generationMode.BACKGROUND]: ['background', 'scene background', 'scene', 'scenery', 'surroundings', 'environment'],
    },
};

// ---------- 官方 index.js L174-218 promptTemplates ----------
const promptTemplates = {
    [generationMode.MESSAGE]: '[{{char}} sends a picture that contains: {{prompt}}].',
    [generationMode.TOOL]: [
        'The text prompt used to generate the image.',
        'Must represent an exhaustive description of the desired image that will allow an artist or a photographer to perfectly recreate it.',
    ].join(' '),
    [generationMode.CHARACTER]: 'In the next response I want you to provide only a detailed comma-delimited list of keywords and phrases which describe {{char}}. The list must include all of the following items in this order: name, species and race, gender, age, clothing, occupation, physical features and appearances. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase \'full body portrait,\'',
    [generationMode.FACE]: 'In the next response I want you to provide only a detailed comma-delimited list of keywords and phrases which describe {{char}}. The list must include all of the following items in this order: name, species and race, gender, age, facial features and expressions, occupation, hair and hair accessories (if any), what they are wearing on their upper body (if anything). Do not describe anything below their neck. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase \'close up facial portrait,\'',
    [generationMode.USER]: 'Ignore previous instructions and provide a detailed description of {{user}}\'s physical appearance from the perspective of {{char}} in the form of a comma-delimited list of keywords and phrases. The list must include all of the following items in this order: name, species and race, gender, age, clothing, occupation, physical features and appearances. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase \'full body portrait,\'. Ignore the rest of the story when crafting this description. Do not reply as {{char}} when writing this description, and do not attempt to continue the story.',
    [generationMode.SCENARIO]: 'Ignore previous instructions and provide a detailed description for all of the following: a brief recap of recent events in the story, {{char}}\'s appearance, and {{char}}\'s surroundings. Do not reply as {{char}} while writing this description.',
    [generationMode.NOW]: `Ignore previous instructions. Your next response must be formatted as a single comma-delimited list of concise keywords.  The list will describe of the visual details included in the last chat message.

    Only mention characters by using pronouns ('he','his','she','her','it','its') or neutral nouns ('male', 'the man', 'female', 'the woman').

    Ignore non-visible things such as feelings, personality traits, thoughts, and spoken dialog.

    Add keywords in this precise order:
    a keyword to describe the location of the scene,
    a keyword to mention how many characters of each gender or type are present in the scene (minimum of two characters:
    {{user}} and {{char}}, example: '2 men ' or '1 man 1 woman ', '1 man 3 robots'),

    keywords to describe the relative physical positioning of the characters to each other (if a commonly known term for the positioning is known use it instead of describing the positioning in detail) + 'POV',

    a single keyword or phrase to describe the primary act taking place in the last chat message,

    keywords to describe {{char}}'s physical appearance and facial expression,
    keywords to describe {{char}}'s actions,
    keywords to describe {{user}}'s physical appearance and actions.

    If character actions involve direct physical interaction with another character, mention specifically which body parts interacting and how.

    A correctly formatted example response would be:
    '(location),(character list by gender),(primary action), (relative character position) POV, (character 1's description and actions), (character 2's description and actions)'`,
    [generationMode.RAW_LAST]: 'Ignore previous instructions and provide ONLY the last chat message string back to me verbatim. Do not write anything after the string. Do not reply as {{char}} when writing this description, and do not attempt to continue the story.',
    [generationMode.BACKGROUND]: 'Ignore previous instructions and provide a detailed description of {{char}}\'s surroundings in the form of a comma-delimited list of keywords and phrases. The list must include all of the following items in this order: location, time of day, weather, lighting, and any other relevant details. Do not include descriptions of characters and non-visual qualities such as names, personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase \'background,\'. Ignore the rest of the story when crafting this description. Do not reply as {{char}} when writing this description, and do not attempt to continue the story.',
    [generationMode.FACE_MULTIMODAL]: 'Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with "close-up portrait".',
    [generationMode.CHARACTER_MULTIMODAL]: 'Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with "full body portrait".',
    [generationMode.USER_MULTIMODAL]: 'Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with "full body portrait".',
    [generationMode.FREE_EXTENDED]: 'Ignore previous instructions and provide an exhaustive comma-separated list of tags describing the appearance of "{0}" in great detail. Start with {{charPrefix}} (sic) if the subject is associated with {{char}}.',
};

// ---------- 官方 index.js L130-134 multimodalMap ----------
const multimodalMap = {
    [generationMode.CHARACTER]: generationMode.CHARACTER_MULTIMODAL,
    [generationMode.USER]: generationMode.USER_MULTIMODAL,
    [generationMode.FACE]: generationMode.FACE_MULTIMODAL,
};

// ---------- 官方 index.js L2860-2889 getGenerationType / getQuietPrompt ----------
function getGenerationType(prompt, multimodal_captioning, free_extend) {
    let mode = generationMode.FREE;
    for (const [key, values] of Object.entries(triggerWords)) {
        for (const value of values) {
            if (value.toLowerCase() === prompt.toLowerCase().trim()) {
                mode = Number(key);
                break;
            }
        }
    }
    if (multimodal_captioning && multimodalMap[mode] !== undefined) {
        mode = multimodalMap[mode];
    }
    if (mode === generationMode.FREE && free_extend) {
        mode = generationMode.FREE_EXTENDED;
    }
    return mode;
}

function getQuietPrompt(mode, trigger, prompts) {
    if (mode === generationMode.FREE) {
        return trigger;
    }
    return stringFormat(prompts[mode], trigger);
}

// ---------- 官方 index.js L375-434 processTriggers（纯逻辑段） ----------
function processTriggersCore(message) {
    if (!message) {
        return null;
    }
    const messageLower = message.toLowerCase();
    const activationRegex = new RegExp(messageTrigger.activationRegex, 'i');
    const activationMatch = messageLower.match(activationRegex);
    if (!activationMatch) {
        return null;
    }
    let subject = activationMatch[3].trim();
    if (!subject) {
        return null;
    }
    outer: for (const [specialMode, triggers] of Object.entries(messageTrigger.specialCases)) {
        for (const trigger of triggers) {
            if (subject === trigger) {
                subject = triggerWords[specialMode][0];
                break outer;
            }
        }
    }
    return { mode: getGenerationType(subject, false, false), subject };
}

// ---------- 官方 index.js L2891-2928 processReply ----------
function processReply(str, minimal) {
    if (!str) {
        return '';
    }
    if (minimal) {
        str = str.normalize('NFD');
        str = str.replace(/\s+/g, ' ');
        str = str.trim();
        return str;
    }
    str = str.replaceAll('"', '');
    str = str.replaceAll('“', '');
    str = str.replaceAll('\n', ', ');
    str = str.normalize('NFD');
    str = str.replace(/[^a-zA-Z0-9.,:_(){}<>[\]/\-'|#]+/g, ' ');
    str = str.replace(/\s+/g, ' ');
    str = str.trim();
    str = str
        .split(',')
        .map(x => x.trim())
        .filter(x => x)
        .join(', ');
    return str;
}

// ---------- 用例 ----------
const cases = [];
let id = 0;
function add(name, kind, args, expected) {
    cases.push({ id: String(++id).padStart(3, '0') + '-' + name, kind, args, expected });
}

// getGenerationType
for (const p of ['you', 'me', 'scene', 'raw_last', 'last', 'face', 'background', 'a cat', '  you  ', 'You']) {
    add('gen-type-' + p.replace(/\W/g, '_'), 'genType', { prompt: p, multimodal: false, freeExtend: false }, getGenerationType(p, false, false));
}
add('gen-type-multimodal-char', 'genType', { prompt: 'you', multimodal: true, freeExtend: false }, getGenerationType('you', true, false));
add('gen-type-multimodal-me', 'genType', { prompt: 'me', multimodal: true, freeExtend: false }, getGenerationType('me', true, false));
add('gen-type-multimodal-face', 'genType', { prompt: 'face', multimodal: true, freeExtend: false }, getGenerationType('face', true, false));
add('gen-type-multimodal-cat', 'genType', { prompt: 'a cat', multimodal: true, freeExtend: false }, getGenerationType('a cat', true, false));
add('gen-type-free-extend', 'genType', { prompt: 'a cat', multimodal: false, freeExtend: true }, getGenerationType('a cat', false, true));

// promptTemplates 逐字（13 个模板全量，含 \' 转义与多行 NOW）
for (const [k, v] of Object.entries(promptTemplates)) {
    add('template-' + k, 'template', { key: k }, v);
}

// getQuietPrompt
add('quiet-free', 'quiet', { mode: generationMode.FREE, trigger: 'a cat' }, getQuietPrompt(generationMode.FREE, 'a cat', promptTemplates));
add('quiet-char', 'quiet', { mode: generationMode.CHARACTER, trigger: 'x' }, getQuietPrompt(generationMode.CHARACTER, 'x', promptTemplates));
add('quiet-free-extended', 'quiet', { mode: generationMode.FREE_EXTENDED, trigger: 'dragon' }, getQuietPrompt(generationMode.FREE_EXTENDED, 'dragon', promptTemplates));

// stringFormat
add('fmt-basic', 'fmt', { format: 'Hello, {0}!', args: ['world'] }, stringFormat('Hello, {0}!', 'world'));
add('fmt-missing', 'fmt', { format: '{0} and {1}', args: ['a'] }, stringFormat('{0} and {1}', 'a'));
add('fmt-multi', 'fmt', { format: '{2} {0} {1}', args: ['a', 'b', 'c'] }, stringFormat('{2} {0} {1}', 'a', 'b', 'c'));

// parseInteractiveTrigger
for (const msg of [
    'send me a picture of cat',
    'imagine a picture of a dragon',
    'draw me a picture of you',
    'show a photo of your face',
    'generate a picture of the whole story',
    'make me an image of myself',
    'send me a picture of',
    'hello there',
    'Send Me A Picture Of You',
    'paint a photograph of the scene background',
    'create an image of last message',
]) {
    const r = processTriggersCore(msg);
    add('interactive-' + msg.replace(/\W/g, '_').slice(0, 24), 'interactive', { message: msg }, r);
}

// processReply（minimal 与常规两分支）
const replyCases = [
    { name: 'minimal-empty', str: '', minimal: true },
    { name: 'minimal-json', str: '{"prompt": "a   cat  with\n hat", "seed": 42}', minimal: true },
    { name: 'minimal-multiws', str: '  a   very    long\n\n   prompt with   tabs\t here  ', minimal: true },
    { name: 'minimal-accents', str: 'café déjà vu naïve', minimal: true },
    { name: 'reg-empty', str: '', minimal: false },
    { name: 'reg-quotes', str: 'He said "hi" and then “hello” to her', minimal: false },
    { name: 'reg-newlines', str: 'line one\nline two\nline three', minimal: false },
    { name: 'reg-realistic', str: 'A woman, wearing a "red dress", standing in front of a castle.\n\nShe looks elegant, with long hair.', minimal: false },
    { name: 'reg-marks', str: 'hero - warrior! (1girl), [masterpiece], best quality??', minimal: false },
    { name: 'reg-commas', str: '  one  ,, two ,, three ,  ', minimal: false },
    { name: 'reg-accents', str: 'café noir, déjà vu', minimal: false },
    { name: 'reg-curly', str: 'full body portrait, {masterpiece:1.2}, <lora:x:0.8>', minimal: false },
    { name: 'reg-only-spaces', str: '   \n\n   ', minimal: false },
    { name: 'reg-mixed', str: 'a  cat with a "bowtie"  sitting on  a chair\n\nwarm lighting', minimal: false },
];
for (const c of replyCases) {
    add('reply-' + c.name, 'reply', { str: c.str, minimal: c.minimal }, processReply(c.str, c.minimal));
}

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`imagegen-prompt fixtures: ${cases.length} cases -> ${outFile}`);
