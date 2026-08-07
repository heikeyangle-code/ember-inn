#!/usr/bin/env node
// 群聊角色卡合并（group-chats.js getGroupCharacterCardsLazy）→ JSON fixture。
// 函数体照官方实现；characters/chat_metadata/baseChatReplace/createLazyFields 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'group-cards.json');

const funcs = `
const group_generation_mode = { SWAP: 0, APPEND: 1, APPEND_DISABLED: 2 };
let characters = [];
let chat_metadata = {};
let groups = [];
let power_user = { collapse_newlines: false };

function baseChatReplace(value, name1Override = null, name2Override = null) {
    if (typeof value === 'string' && value.length > 0) {
        value = String(value).replace(/\\{\\{char\\}\\}/gi, name2Override ?? '').replace(/\\r/g, '');
    }
    return value;
}

function createLazyFields(resolvers) {
    const result = {};
    for (const [key, resolver] of Object.entries(resolvers)) {
        let cached;
        let resolved = false;
        Object.defineProperty(result, key, {
            get() {
                if (!resolved) { cached = resolver(); resolved = true; }
                return cached;
            },
            enumerable: true,
            configurable: true,
        });
    }
    return result;
}

function getGroupCharacterCardsLazy(groupId, characterId) {
    const group = groups.find(x => x.id === groupId);
    if (!group || !group?.generation_mode || !Array.isArray(group.members) || !group.members.length) {
        return null;
    }

    function customTransform(value, fieldName, characterName, trim) {
        if (!value) return '';
        value = value.replace(/<FIELDNAME>/gi, fieldName);
        value = trim ? value.trim() : value;
        return baseChatReplace(value, null, characterName);
    }

    function replaceAndPrepareForJoin(value, characterName, fieldName, preprocess = null) {
        value = value?.trim() ?? '';
        if (!value) return '';
        if (typeof preprocess === 'function') value = preprocess(value);
        const prefix = customTransform(group.generation_mode_join_prefix, fieldName, characterName, false);
        const suffix = customTransform(group.generation_mode_join_suffix, fieldName, characterName, false);
        value = customTransform(value, fieldName, characterName, true);
        return \`\${prefix}\${value}\${suffix}\`;
    }

    function collectField(fieldName, getter, preprocess = null) {
        const values = [];
        for (const member of group.members) {
            const index = characters.findIndex(x => x.avatar === member);
            const character = characters[index];
            if (index === -1 || !character) continue;
            if (group.disabled_members.includes(member) && characterId !== index && group.generation_mode !== group_generation_mode.APPEND_DISABLED) continue;
            values.push(replaceAndPrepareForJoin(getter(character), character.name, fieldName, preprocess));
        }
        return values.filter(x => x.length).join('\\n');
    }

    const scenarioOverride = String(chat_metadata.scenario || '');
    const mesExamplesOverride = String(chat_metadata.mes_example || '');

    return createLazyFields({
        description: () => collectField('Description', c => c.description),
        personality: () => collectField('Personality', c => c.personality),
        scenario: () => baseChatReplace(scenarioOverride?.trim()) || collectField('Scenario', c => c.scenario),
        mesExamples: () => baseChatReplace(mesExamplesOverride?.trim()) ||
            collectField('Example Messages', c => c.mes_example, x => !x.startsWith('<START>') ? \`<START>\\n\${x}\` : x),
    });
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    characters = request.body.characters ?? [];',
    '    chat_metadata = request.body.chat_metadata ?? {};',
    '    groups = request.body.groups ?? [];',
    '    const lazy = getGroupCharacterCardsLazy(request.body.groupId, request.body.characterId ?? 0);',
    '    if (!lazy) return null;',
    '    return JSON.parse(JSON.stringify(lazy));',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const baseGroup = {
    id: 'g1', generation_mode: 1, members: ['a', 'b'], disabled_members: [],
    generation_mode_join_prefix: '', generation_mode_join_suffix: '',
};
const chars = [
    { avatar: 'a', name: 'Alice', description: '爱丽丝描述', personality: '开朗', scenario: '森林', mes_example: '示例A' },
    { avatar: 'b', name: 'Bob', description: '鲍勃描述 {{char}}', personality: '沉稳', scenario: '海边', mes_example: '<START>\\n示例B' },
];

await add('swap-null', { groupId: 'g1', characterId: 0, groups: [{ ...baseGroup, generation_mode: 0 }], characters: chars, chat_metadata: {} });
await add('append-basic', { groupId: 'g1', characterId: 0, groups: [{ ...baseGroup }], characters: chars, chat_metadata: {} });
await add('append-prefix-suffix', {
    groupId: 'g1', characterId: 0,
    groups: [{ ...baseGroup, generation_mode_join_prefix: '<FIELDNAME>: ', generation_mode_join_suffix: ';' }],
    characters: chars, chat_metadata: {},
});
await add('append-disabled', {
    groupId: 'g1', characterId: 1,
    groups: [{ ...baseGroup, generation_mode: 2, disabled_members: ['a'] }],
    characters: chars, chat_metadata: {},
});
await add('overrides', {
    groupId: 'g1', characterId: 0, groups: [{ ...baseGroup }], characters: chars,
    chat_metadata: { scenario: '覆盖场景 {{char}}', mes_example: '覆盖示例' },
});
await add('missing-member', {
    groupId: 'g1', characterId: 0, groups: [{ ...baseGroup, members: ['a', 'missing', 'b'] }],
    characters: chars, chat_metadata: {},
});

writeFileSync(outFile, JSON.stringify({ source: 'group-chats.js getGroupCharacterCardsLazy', cases }, null, 2));
console.log('group-cards:', cases.length, 'cases ->', outFile);
