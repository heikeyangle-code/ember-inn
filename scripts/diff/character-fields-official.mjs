#!/usr/bin/env node
// 角色卡字段聚合（script.js getCharacterCardFieldsLazy）→ JSON fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'character-fields.json');

const funcs = `
let characters = [];
let selected_group = null;
let this_chid = 0;
let chat_metadata = {};
let power_user = { persona_description: '', prefer_character_prompt: true, prefer_character_jailbreak: true, collapse_newlines: false };
let groupCardsLazy = null;

function baseChatReplace(value, name1Override = null, name2Override = null) {
    if (name2Override === null) name2Override = characters[this_chid]?.name ?? '';
    if (typeof value === 'string' && value.length > 0) {
        return String(value).replace(/\\{\\{char\\}\\}/gi, name2Override ?? '').replace(/\\r/g, '');
    }
    return value;
}

function createLazyFields(resolvers) {
    const result = {};
    for (const [key, resolver] of Object.entries(resolvers)) {
        let cached; let resolved = false;
        Object.defineProperty(result, key, { get() { if (!resolved) { cached = resolver(); resolved = true; } return cached; }, enumerable: true, configurable: true });
    }
    return result;
}

function getCharacterCardFieldsLazy({ chid = undefined } = {}) {
    const currentChid = chid ?? this_chid;
    const character = characters[currentChid];
    const useGroupCards = selected_group && character;
    const groupCards = useGroupCards ? groupCardsLazy : null;
    const resolvers = {
        persona: () => baseChatReplace(power_user.persona_description?.trim()),
        system: () => {
            if (!character) return '';
            const systemPrompt = chat_metadata.system_prompt || character.data?.system_prompt || '';
            return power_user.prefer_character_prompt ? baseChatReplace(systemPrompt.trim()) : '';
        },
        jailbreak: () => {
            if (!character) return '';
            return power_user.prefer_character_jailbreak ? baseChatReplace(character.data?.post_history_instructions?.trim()) : '';
        },
        version: () => character?.data?.character_version ?? '',
        charDepthPrompt: () => { if (!character) return ''; return baseChatReplace(character.data?.extensions?.depth_prompt?.prompt?.trim()); },
        creatorNotes: () => { if (!character) return ''; return baseChatReplace(character.data?.creator_notes?.trim()); },
        description: () => { if (groupCards) return groupCards.description; if (!character) return ''; return baseChatReplace(character.description?.trim()); },
        personality: () => { if (groupCards) return groupCards.personality; if (!character) return ''; return baseChatReplace(character.personality?.trim()); },
        scenario: () => { if (groupCards) return groupCards.scenario; if (!character) return ''; const scenarioText = chat_metadata.scenario || character.scenario || ''; return baseChatReplace(scenarioText.trim()); },
        mesExamples: () => { if (groupCards) return groupCards.mesExamples; if (!character) return ''; const exampleDialog = chat_metadata.mes_example || character.mes_example || ''; return baseChatReplace(exampleDialog.trim()); },
        firstMessage: () => { if (!character) return ''; const firstMes = character.first_mes?.trim() || ''; return baseChatReplace(firstMes); },
        alternateGreetings: () => { if (!character) return []; const altGreetings = character.data?.alternate_greetings; if (!Array.isArray(altGreetings)) return []; return altGreetings.map(greeting => baseChatReplace(greeting?.trim())); },
    };
    return createLazyFields(resolvers);
}

function getCharacterCardFields({ chid = undefined } = {}) {
    const lazy = getCharacterCardFieldsLazy({ chid });
    return {
        system: lazy.system, mesExamples: lazy.mesExamples, description: lazy.description, personality: lazy.personality,
        persona: lazy.persona, scenario: lazy.scenario, jailbreak: lazy.jailbreak, version: lazy.version,
        charDepthPrompt: lazy.charDepthPrompt, creatorNotes: lazy.creatorNotes, firstMessage: lazy.firstMessage,
        alternateGreetings: lazy.alternateGreetings,
    };
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    characters = request.body.characters ?? [];',
    '    selected_group = request.body.selected_group ?? null;',
    '    this_chid = request.body.chid ?? 0;',
    '    chat_metadata = request.body.chat_metadata ?? {};',
    '    power_user = request.body.power_user ?? { persona_description: "", prefer_character_prompt: true, prefer_character_jailbreak: true };',
    '    groupCardsLazy = request.body.groupCards ?? null;',
    '    return getCharacterCardFields({ chid: request.body.chid ?? 0 });',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const fullChar = {
    name: 'Alice', description: '描述 {{char}}', personality: '开朗', scenario: '森林', mes_example: '示例',
    first_mes: '你好',
    data: {
        system_prompt: '系统提示', post_history_instructions: '结语', character_version: '1.2',
        creator_notes: '备注', alternate_greetings: ['备选A', '备选B'],
        extensions: { depth_prompt: { prompt: '深度提示' } },
    },
};

await add('full', { characters: [fullChar], chid: 0, power_user: { persona_description: '人设', prefer_character_prompt: true, prefer_character_jailbreak: true } });
await add('missing', { characters: [], chid: 0, power_user: { persona_description: '', prefer_character_prompt: true, prefer_character_jailbreak: true } });
await add('metadata-overrides', {
    characters: [fullChar], chid: 0,
    chat_metadata: { system_prompt: '元系统', scenario: '元场景', mes_example: '元示例' },
    power_user: { persona_description: '人设', prefer_character_prompt: true, prefer_character_jailbreak: true },
});
await add('prefs-off', { characters: [fullChar], chid: 0, power_user: { persona_description: '', prefer_character_prompt: false, prefer_character_jailbreak: false } });
await add('group-override', {
    characters: [fullChar], chid: 0, selected_group: 'g1',
    groupCards: { description: '群描述', personality: '群性格', scenario: '群场景', mesExamples: '群示例' },
    power_user: { persona_description: '', prefer_character_prompt: true, prefer_character_jailbreak: true },
});
await add('empty-greetings', {
    characters: [{ ...fullChar, data: { ...fullChar.data, alternate_greetings: [] } }], chid: 0,
    power_user: { persona_description: '', prefer_character_prompt: true, prefer_character_jailbreak: true },
});

writeFileSync(outFile, JSON.stringify({ source: 'script.js getCharacterCardFields', cases }, null, 2));
console.log('character-fields:', cases.length, 'cases ->', outFile);
