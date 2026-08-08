#!/usr/bin/env node
// 群聊深度提示（group-chats.js getGroupDepthPrompts）→ JSON fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'group-depth.json');

const funcs = `
const group_generation_mode = { SWAP: 0, APPEND: 1, APPEND_DISABLED: 2 };
const depth_prompt_depth_default = 4;
const depth_prompt_role_default = 'system';
let characters = [];
let groups = [];
const console = { debug: () => {}, warn: () => {}, info: () => {}, error: () => {} };

function baseChatReplace(value, name1Override = null, name2Override = null) {
    if (typeof value === 'string' && value.length > 0) {
        return String(value).replace(/\\{\\{char\\}\\}/gi, name2Override ?? '').replace(/\\r/g, '');
    }
    return value;
}

function getGroupDepthPrompts(groupId, characterId) {
    if (!groupId) return [];
    const group = groups.find(x => x.id === groupId);
    if (!group || !Array.isArray(group.members) || !group.members.length) return [];
    if (group.generation_mode === group_generation_mode.SWAP) return [];
    const depthPrompts = [];
    for (const member of group.members) {
        const index = characters.findIndex(x => x.avatar === member);
        const character = characters[index];
        if (index === -1 || !character) continue;
        if (group.disabled_members.includes(member) && characterId !== index) continue;
        const depthPromptText = baseChatReplace(character.data?.extensions?.depth_prompt?.prompt?.trim(), null, character.name) || '';
        const depthPromptDepth = character.data?.extensions?.depth_prompt?.depth ?? depth_prompt_depth_default;
        const depthPromptRole = character.data?.extensions?.depth_prompt?.role ?? depth_prompt_role_default;
        if (depthPromptText) depthPrompts.push({ text: depthPromptText, depth: depthPromptDepth, role: depthPromptRole });
    }
    return depthPrompts;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    characters = request.body.characters ?? [];',
    '    groups = request.body.groups ?? [];',
    '    return getGroupDepthPrompts(request.body.groupId, request.body.characterId ?? 0);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const chars = [
    { avatar: 'a', name: 'Alice', data: { extensions: { depth_prompt: { prompt: 'Alice深度 {{char}}', depth: 6, role: 'user' } } } },
    { avatar: 'b', name: 'Bob', data: { extensions: { depth_prompt: { prompt: 'Bob深度', depth: 8, role: 'assistant' } } } },
    { avatar: 'c', name: 'Carol', data: { extensions: {} } },
];

await add('swap', { groupId: 'g', characterId: 0, groups: [{ id: 'g', generation_mode: 0, members: ['a', 'b'], disabled_members: [] }], characters: chars });
await add('append-basic', { groupId: 'g', characterId: 0, groups: [{ id: 'g', generation_mode: 1, members: ['a', 'b', 'c'], disabled_members: [] }], characters: chars });
await add('disabled-current', { groupId: 'g', characterId: 0, groups: [{ id: 'g', generation_mode: 1, members: ['a', 'b'], disabled_members: ['a'] }], characters: chars });
await add('disabled-other', { groupId: 'g', characterId: 1, groups: [{ id: 'g', generation_mode: 1, members: ['a', 'b'], disabled_members: ['a'] }], characters: chars });
await add('missing', { groupId: 'g', characterId: 0, groups: [{ id: 'g', generation_mode: 1, members: ['a', 'missing'], disabled_members: [] }], characters: chars });


await add('no-group', { groupId: '', characterId: 0, groups: [], characters: chars });
await add('empty-members', { groupId: 'g', characterId: 0, groups: [{ id: 'g', generation_mode: 1, members: [], disabled_members: [] }], characters: chars });
writeFileSync(outFile, JSON.stringify({ source: 'group-chats.js getGroupDepthPrompts', cases }, null, 2));
console.log('group-depth:', cases.length, 'cases ->', outFile);
