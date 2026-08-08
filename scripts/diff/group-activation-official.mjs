#!/usr/bin/env node
// 群聊激活策略（group-chats.js activate* + utils.js shuffle/extractAllWords）→ JSON fixture。
// 函数体照官方逐字实现；characters/chat/Math.random 打桩，输出按 characters 映射回 avatar。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'group-activation.json');

const funcs = `
let characters = [];
let chat = [];
const system_message_types = { NARRATOR: 'narrator' };
const talkativeness_default = 0.5;

function shuffle(array) {
    let currentIndex = array.length, randomIndex;
    while (currentIndex != 0) {
        randomIndex = Math.floor(Math.random() * currentIndex);
        currentIndex--;
        [array[currentIndex], array[randomIndex]] = [array[randomIndex], array[currentIndex]];
    }
    return array;
}

function extractAllWords(value) {
    const words = [];
    if (!value) return words;
    const matches = value.matchAll(/\\b\\w+\\b/gim);
    for (const match of matches) words.push(match[0].toLowerCase());
    return words;
}

function onlyUnique(value, index, array) {
    return array.indexOf(value) === index;
}

function activateImpersonate(members) {
    const randomIndex = Math.floor(Math.random() * members.length);
    const activatedMembers = [members[randomIndex]];
    return activatedMembers
        .map((x) => characters.findIndex((y) => y.avatar === x))
        .filter((x) => x !== -1);
}

function activateSwipe(members, { allowSystem = false } = {}) {
    let activatedNames = [];
    const lastMessage = chat[chat.length - 1];
    if (!lastMessage) return [];
    if (lastMessage.is_user || (!allowSystem && lastMessage.is_system) || lastMessage.extra?.type === system_message_types.NARRATOR) {
        for (const message of chat.slice().reverse()) {
            if (message.is_user || (!allowSystem && message.is_system) || message.extra?.type === system_message_types.NARRATOR) continue;
            if (message.original_avatar) { activatedNames.push(message.original_avatar); break; }
        }
        if (activatedNames.length === 0) activatedNames.push(shuffle(members.slice())[0]);
    }
    if (!lastMessage.original_avatar) {
        const matches = characters.filter(x => x.name == lastMessage.name);
        for (const match of matches) {
            if (members.includes(match.avatar)) { activatedNames.push(match.avatar); break; }
        }
    } else {
        activatedNames.push(lastMessage.original_avatar);
    }
    return activatedNames
        .map((x) => characters.findIndex((y) => y.avatar === x))
        .filter((x) => x !== -1);
}

function activateListOrder(members) {
    const activatedMembers = members.filter(onlyUnique);
    return activatedMembers
        .map((x) => characters.findIndex((y) => y.avatar === x))
        .filter((x) => x !== -1);
}

function activatePooledOrder(members, lastMessage, isUserInput) {
    let activatedMember = null;
    const spokenSinceUser = [];
    for (const message of chat.slice().reverse()) {
        if (message.is_user || isUserInput) break;
        if (message.is_system || message.extra?.type === system_message_types.NARRATOR) continue;
        if (message.original_avatar) spokenSinceUser.push(message.original_avatar);
    }
    const haveNotSpoken = members.filter(x => !spokenSinceUser.includes(x));
    if (haveNotSpoken.length) activatedMember = haveNotSpoken[Math.floor(Math.random() * haveNotSpoken.length)];
    if (activatedMember === null) {
        const lastMessageAvatar = members.length > 1 && lastMessage && !lastMessage.is_user && lastMessage.original_avatar;
        const randomPool = lastMessageAvatar ? members.filter(x => x !== lastMessage.original_avatar) : members;
        activatedMember = randomPool[Math.floor(Math.random() * randomPool.length)];
    }
    const memberId = characters.findIndex(y => y.avatar === activatedMember);
    return memberId !== -1 ? [memberId] : [];
}

function activateNaturalOrder(members, input, lastMessage, allowSelfResponses, isUserInput) {
    let activatedMembers = [];
    let bannedUser = !isUserInput && lastMessage && !lastMessage.is_user && lastMessage.name;
    if (allowSelfResponses) bannedUser = undefined;
    if (input && input.length) {
        for (const inputWord of extractAllWords(input)) {
            for (const member of members) {
                const character = characters.find(x => x.avatar === member);
                if (!character || character.name === bannedUser) continue;
                if (extractAllWords(character.name).includes(inputWord)) { activatedMembers.push(member); break; }
            }
        }
    }
    const chattyMembers = [];
    const shuffledMembers = shuffle([...members]);
    for (const member of shuffledMembers) {
        const character = characters.find((x) => x.avatar === member);
        if (!character || character.name === bannedUser) continue;
        const rollValue = Math.random();
        const talkativeness = isNaN(character.talkativeness) ? talkativeness_default : Number(character.talkativeness);
        if (talkativeness >= rollValue) activatedMembers.push(member);
        if (talkativeness > 0) chattyMembers.push(member);
    }
    let retries = 0;
    const randomPool = chattyMembers.length > 0 ? chattyMembers : members;
    while (activatedMembers.length === 0 && ++retries <= randomPool.length) {
        const randomIndex = Math.floor(Math.random() * randomPool.length);
        const character = characters.find((x) => x.avatar === randomPool[randomIndex]);
        if (!character) continue;
        activatedMembers.push(randomPool[randomIndex]);
    }
    activatedMembers = activatedMembers.filter(onlyUnique);
    return activatedMembers
        .map((x) => characters.findIndex((y) => y.avatar === x))
        .filter((x) => x !== -1);
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    characters = request.body.characters ?? [];',
    '    chat = request.body.chat ?? [];',
    '    let randomIndex = 0;',
    '    const oldRandom = Math.random;',
    '    Math.random = () => { const r = request.body.randoms?.[randomIndex++]; return r === undefined ? 0.5 : r; };',
    '    try {',
    '        const method = request.body.method;',
    '        const members = request.body.members ?? [];',
    '        const toAvatars = ids => ids.map(id => characters[id]?.avatar).filter(Boolean);',
    '        if (method === "list") return toAvatars(activateListOrder(members));',
    '        if (method === "impersonate") return toAvatars(activateImpersonate(members));',
    '        if (method === "swipe") return toAvatars(activateSwipe(members, { allowSystem: request.body.allowSystem ?? false }));',
    '        if (method === "pooled") return toAvatars(activatePooledOrder(members, request.body.lastMessage ?? null, request.body.isUserInput ?? false));',
    '        if (method === "natural") return toAvatars(activateNaturalOrder(members, request.body.input ?? "", request.body.lastMessage ?? null, request.body.allowSelfResponses ?? false, request.body.isUserInput ?? false));',
    '        throw new Error("unknown method");',
    '    } finally { Math.random = oldRandom; }',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const chars = [
    { avatar: 'a', name: 'Alice', talkativeness: 0.8 },
    { avatar: 'b', name: 'Bob', talkativeness: 0.2 },
    { avatar: 'c', name: 'Carol', talkativeness: 0.5 },
];

await add('list', { method: 'list', members: ['b', 'a', 'b', 'c'], characters: chars, randoms: [] });
await add('impersonate', { method: 'impersonate', members: ['a', 'b', 'c'], characters: chars, randoms: [0.9] });
await add('swipe-user-last', {
    method: 'swipe', members: ['a', 'b', 'c'], characters: chars,
    chat: [{ is_user: true, name: 'User', original_avatar: null }, { is_user: false, name: 'Alice', original_avatar: 'a' }],
    randoms: [],
});
await add('swipe-no-last', { method: 'swipe', members: ['a', 'b', 'c'], characters: chars, chat: [], randoms: [0.2] });
await add('swipe-system', {
    method: 'swipe', members: ['a', 'b', 'c'], characters: chars,
    chat: [{ is_user: false, is_system: true, name: 'Sys', original_avatar: null }, { is_user: false, name: 'Bob', original_avatar: 'b' }],
    randoms: [],
});
await add('pooled-have-not-spoken', {
    method: 'pooled', members: ['a', 'b', 'c'], characters: chars,
    chat: [{ is_user: false, name: 'Alice', original_avatar: 'a' }],
    lastMessage: { is_user: false, name: 'Alice', original_avatar: 'a' },
    isUserInput: false,
    randoms: [0.8],
});
await add('pooled-all-spoken', {
    method: 'pooled', members: ['a', 'b', 'c'], characters: chars,
    chat: [{ is_user: false, name: 'Alice', original_avatar: 'a' }, { is_user: false, name: 'Bob', original_avatar: 'b' }, { is_user: false, name: 'Carol', original_avatar: 'c' }],
    lastMessage: { is_user: false, name: 'Carol', original_avatar: 'c' },
    isUserInput: false,
    randoms: [0.1],
});
await add('natural-mention', {
    method: 'natural', members: ['a', 'b', 'c'], characters: chars,
    input: 'Hey Bob!', lastMessage: { is_user: true, name: 'User', original_avatar: null },
    allowSelfResponses: false, isUserInput: true,
    randoms: [0.1, 0.2, 0.3, 0.9],
});
await add('natural-banned', {
    method: 'natural', members: ['a', 'b', 'c'], characters: chars,
    input: '', lastMessage: { is_user: false, name: 'Alice', original_avatar: 'a' },
    allowSelfResponses: false, isUserInput: false,
    randoms: [0.1, 0.9, 0.9, 0.5],
});
await add('natural-allow-self', {
    method: 'natural', members: ['a', 'b', 'c'], characters: chars,
    input: '', lastMessage: { is_user: false, name: 'Alice', original_avatar: 'a' },
    allowSelfResponses: true, isUserInput: false,
    randoms: [0.1, 0.1, 0.1, 0.9],
});


await add('list-empty', { method: 'list', members: [], characters: chars, randoms: [] });
await add('natural-empty', { method: 'natural', members: [], characters: chars, input: '', lastMessage: null, allowSelfResponses: false, isUserInput: false, randoms: [] });
await add('pooled-empty', { method: 'pooled', members: [], characters: chars, chat: [], lastMessage: null, isUserInput: false, randoms: [0.5] });
await add('swipe-all-system-allow', {
    method: 'swipe', members: ['a', 'b', 'c'], characters: chars, allowSystem: true,
    chat: [{ is_user: false, is_system: true, name: 'Sys', original_avatar: null }],
    randoms: [0.9],
});
await add('natural-zero-random', {
    method: 'natural', members: ['a', 'b', 'c'], characters: chars,
    input: '', lastMessage: null, allowSelfResponses: false, isUserInput: true,
    randoms: [0, 0, 0, 0, 0],
});

writeFileSync(outFile, JSON.stringify({ source: 'group-chats.js activate*', cases }, null, 2));
console.log('group-activation:', cases.length, 'cases ->', outFile);
