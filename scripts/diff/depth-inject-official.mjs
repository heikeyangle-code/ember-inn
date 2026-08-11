#!/usr/bin/env node
// script.js generate 深度提示注入（4418-4430 群聊/角色卡 + 4609-4614 世界书 atDepth）纯逻辑 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩登记：getExtensionPromptRoleByName 数字分支等价直接返回；worldInfoDepth.entries.join 语义原样。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'depth-inject.json');

const funcs = `
const extension_prompt_types = { NONE: -1, IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };
const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
const inject_ids = {
    DEPTH_PROMPT: 'DEPTH_PROMPT',
    DEPTH_PROMPT_INDEX: (index) => 'DEPTH_PROMPT_' + index,
    CUSTOM_WI_DEPTH_ROLE: (depth, role) => 'customDepthWI_' + depth + '_' + role,
};
function getExtensionPromptRoleByName(roleName) {
    if (typeof roleName === 'number' && Object.values(extension_prompt_roles).includes(roleName)) {
        return roleName;
    }
    switch (roleName) {
        case 'system': return extension_prompt_roles.SYSTEM;
        case 'user': return extension_prompt_roles.USER;
        case 'assistant': return extension_prompt_roles.ASSISTANT;
    }
    return extension_prompt_roles.SYSTEM;
}
function characterDepthSpec(charDepthPrompt, depthRaw, roleRaw) {
    const depthPromptText = charDepthPrompt || '';
    const depthPromptDepth = depthRaw ?? 4;
    const depthPromptRole = getExtensionPromptRoleByName(roleRaw ?? 'system');
    return {
        key: inject_ids.DEPTH_PROMPT,
        value: depthPromptText,
        position: extension_prompt_types.IN_CHAT,
        depth: depthPromptDepth,
        role: depthPromptRole,
    };
}
function groupDepthSpecs(groupDepthPrompts) {
    return groupDepthPrompts.map((value, index) => ({
        key: inject_ids.DEPTH_PROMPT_INDEX(index),
        value: value.text,
        position: extension_prompt_types.IN_CHAT,
        depth: value.depth,
        role: getExtensionPromptRoleByName(value.role),
    }));
}
function worldDepthSpecs(worldInfoDepth) {
    return worldInfoDepth.map((e) => ({
        key: inject_ids.CUSTOM_WI_DEPTH_ROLE(e.depth, e.role),
        value: e.entries.join('\\n'),
        position: extension_prompt_types.IN_CHAT,
        depth: e.depth,
        role: e.role,
    }));
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    if (b.mode === "character") return characterDepthSpec(b.content, b.depth, b.role);',
    '    if (b.mode === "group") return groupDepthSpecs(b.prompts);',
    '    if (b.mode === "world") return worldDepthSpecs(b.depthEntries);',
    '    return null;',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('character-defaults', { mode: 'character', content: '角色深度提示', depth: undefined, role: undefined });
await add('character-user-depth0', { mode: 'character', content: '深度0用户', depth: 0, role: 'user' });
await add('character-assistant-depth10', { mode: 'character', content: '深度10助手', depth: 10, role: 'assistant' });
await add('character-empty', { mode: 'character', content: '', depth: 4, role: 'system' });
await add('group-two', { mode: 'group', prompts: [{ text: 'G1', depth: 1, role: 'system' }, { text: 'G2', depth: 2, role: 'assistant' }] });
await add('world-depth', { mode: 'world', depthEntries: [{ depth: 2, role: 0, entries: ['E1', 'E2'] }, { depth: 4, role: 2, entries: ['E3'] }, { depth: 1, role: 0, entries: ['E4'] }] });

writeFileSync(outFile, JSON.stringify({ source: 'script.js depth prompt injection specs', cases }, null, 2));
console.log('depth-inject:', cases.length, 'cases ->', outFile);
