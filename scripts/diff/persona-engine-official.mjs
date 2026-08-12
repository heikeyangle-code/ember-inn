#!/usr/bin/env node
// 人设引擎纯逻辑（personas.js states/temporary/connections/resolve）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'persona-engine.json');

const funcs = `
function getPersonaStates(avatarId, powerUser, chatPersona, selectedGroup, charAvatar) {
    const isDefaultPersona = powerUser.default_persona === avatarId;
    const hasChatLock = chatPersona == avatarId;
    const connections = powerUser.persona_descriptions[avatarId]?.connections;
    const hasCharLock = !!connections?.some(c =>
        (!selectedGroup && c.type === 'character' && c.id === charAvatar)
        || (selectedGroup && c.type === 'group' && c.id === selectedGroup));
    return { avatarId, default: isDefaultPersona, locked: { chat: hasChatLock, character: hasCharLock } };
}

function getPersonaTemporaryLockInfo(userAvatar, chatPersona, defaultPersona, personas) {
    const hasDifferentChatLock = !!chatPersona && chatPersona !== userAvatar;
    const hasDifferentDefaultLock = !!defaultPersona && defaultPersona !== userAvatar;
    const isTemporary = hasDifferentChatLock || (!chatPersona && hasDifferentDefaultLock);
    const info = isTemporary ? 'Current: ' + (personas[userAvatar] || '') +
        (hasDifferentChatLock ? ' Chat: ' + (personas[chatPersona] || '') : '') +
        (hasDifferentDefaultLock ? ' Default: ' + (personas[defaultPersona] || '') : '') : '';
    return { isTemporary, hasDifferentChatLock, hasDifferentDefaultLock, info };
}

function getConnectedPersonas(personaDescriptions, characterKey) {
    return Object.entries(personaDescriptions)
        .filter(([_, { connections }]) => connections?.some(conn => conn.id === characterKey))
        .map(([key, _]) => key);
}

function getCurrentConnectionObj(selectedGroup, charAvatar) {
    if (selectedGroup) return { type: 'group', id: selectedGroup };
    if (charAvatar) return { type: 'character', id: charAvatar };
    return null;
}

function getOrCreatePersonaDescriptor(userAvatar, powerUser) {
    let object = powerUser.persona_descriptions[userAvatar];
    if (!object) {
        object = {
            description: powerUser.persona_description,
            position: powerUser.persona_description_position,
            depth: powerUser.persona_description_depth,
            role: powerUser.persona_description_role,
            lorebook: powerUser.persona_description_lorebook,
            connections: [],
            title: '',
        };
        powerUser.persona_descriptions[userAvatar] = object;
    }
    return object;
}

function resolvePersonaForChat(chatMetaPersona, userAvatars, connectedPersonas, defaultPersona, allowMultiConnections, userAvatar, personaAutoLock) {
    let chatPersona = '';
    let connectType = null;
    let unlockChat = false;
    let clearDefault = false;

    if (chatMetaPersona) {
        chatPersona = chatMetaPersona;
        if (!userAvatars.includes(chatPersona)) {
            unlockChat = true;
            chatPersona = '';
        }
        if (chatPersona) connectType = 'chat';
    }

    if (!chatPersona && connectedPersonas.length > 0) {
        if (connectedPersonas.length === 1 || !allowMultiConnections) {
            chatPersona = connectedPersonas[0];
        }
        if (chatPersona) connectType = 'character';
    }

    if (!chatPersona && defaultPersona) {
        chatPersona = defaultPersona;
        if (chatPersona) connectType = 'default';
    }

    if (chatMetaPersona && !userAvatars.includes(chatMetaPersona)) unlockChat = true;
    if (defaultPersona && !userAvatars.includes(defaultPersona)) { clearDefault = true; defaultPersona = null; }

    const autoLock = !!chatPersona && personaAutoLock && (userAvatar !== chatPersona ? userAvatar !== chatMetaPersona : !chatMetaPersona);
    return { chatPersona, connectType, unlockChat, clearDefault, willSwitch: !!chatPersona && userAvatar !== chatPersona, autoLock };
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    const method = b.method;',
    '    if (method === "states") return getPersonaStates(b.avatarId, b.powerUser, b.chatPersona, b.selectedGroup ?? null, b.charAvatar ?? null);',
    '    if (method === "temporary") return getPersonaTemporaryLockInfo(b.userAvatar, b.chatPersona, b.defaultPersona, b.personas ?? {});',
    '    if (method === "connected") return getConnectedPersonas(b.personaDescriptions, b.characterKey);',
    '    if (method === "connectionObj") return getCurrentConnectionObj(b.selectedGroup ?? null, b.charAvatar ?? null);',
    '    if (method === "descriptor") return getOrCreatePersonaDescriptor(b.userAvatar, b.powerUser);',
    '    if (method === "resolve") return resolvePersonaForChat(b.chatMetaPersona ?? null, b.userAvatars ?? [], b.connectedPersonas ?? [], b.defaultPersona ?? null, b.allowMultiConnections ?? false, b.userAvatar, b.personaAutoLock ?? false);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const powerUser = {
    default_persona: 'd',
    persona_descriptions: {
        a: { connections: [{ type: 'character', id: 'char1' }], description: 'A' },
        b: { connections: [{ type: 'group', id: 'g1' }] },
        c: { connections: [] },
    },
    persona_description: '默认描述', persona_description_position: 0, persona_description_depth: 4,
    persona_description_role: 0, persona_description_lorebook: '',
};

await add('states-default-chat', { method: 'states', avatarId: 'a', powerUser, chatPersona: 'a', selectedGroup: null, charAvatar: 'char1' });
await add('states-char-lock', { method: 'states', avatarId: 'b', powerUser, chatPersona: null, selectedGroup: 'g1', charAvatar: 'char1' });
await add('temporary-chat', { method: 'temporary', userAvatar: 'a', chatPersona: 'b', defaultPersona: 'd', personas: { a: 'A', b: 'B', d: 'D' } });
await add('temporary-default', { method: 'temporary', userAvatar: 'a', chatPersona: null, defaultPersona: 'd', personas: { a: 'A', d: 'D' } });
await add('temporary-none', { method: 'temporary', userAvatar: 'a', chatPersona: 'a', defaultPersona: 'a', personas: { a: 'A' } });
await add('connected-char', { method: 'connected', personaDescriptions: powerUser.persona_descriptions, characterKey: 'char1' });
await add('connected-group', { method: 'connected', personaDescriptions: powerUser.persona_descriptions, characterKey: 'g1' });
await add('connection-obj-char', { method: 'connectionObj', selectedGroup: null, charAvatar: 'char1' });
await add('connection-obj-group', { method: 'connectionObj', selectedGroup: 'g1', charAvatar: 'char1' });
await add('descriptor-create', { method: 'descriptor', userAvatar: 'new', powerUser: { ...powerUser, persona_descriptions: {} } });
await add('resolve-chat-lock', { method: 'resolve', chatMetaPersona: 'a', userAvatars: ['a', 'b'], connectedPersonas: [], defaultPersona: null, allowMultiConnections: false, userAvatar: 'b' });
await add('resolve-connected', { method: 'resolve', chatMetaPersona: null, userAvatars: ['a', 'b'], connectedPersonas: ['a'], defaultPersona: null, allowMultiConnections: false, userAvatar: 'b' });
await add('resolve-default', { method: 'resolve', chatMetaPersona: null, userAvatars: ['a', 'd'], connectedPersonas: [], defaultPersona: 'd', allowMultiConnections: false, userAvatar: 'a' });
await add('resolve-invalid', { method: 'resolve', chatMetaPersona: 'x', userAvatars: ['a'], connectedPersonas: [], defaultPersona: 'd', allowMultiConnections: false, userAvatar: 'a' });
await add('resolve-auto-lock-switch', { method: 'resolve', chatMetaPersona: 'a', userAvatars: ['a', 'b'], connectedPersonas: [], defaultPersona: null, allowMultiConnections: false, userAvatar: 'b', personaAutoLock: true });
await add('resolve-auto-lock-same', { method: 'resolve', chatMetaPersona: null, userAvatars: ['a', 'b'], connectedPersonas: ['a'], defaultPersona: null, allowMultiConnections: false, userAvatar: 'a', personaAutoLock: true });
// ---- 2026-08-12 穷举复验补充：全空/多连接/连接冲突/描述已存在/无群聊状态 ----
await add('states-no-persona-no-group', { method: 'states', avatarId: 'a', powerUser, chatPersona: null, selectedGroup: null, charAvatar: null });
await add('states-no-description', { method: 'states', avatarId: 'x', powerUser, chatPersona: 'x', selectedGroup: null, charAvatar: 'char1' });
await add('temporary-empty-personas', { method: 'temporary', userAvatar: 'a', chatPersona: 'b', defaultPersona: 'd', personas: {} });
await add('connected-multi', { method: 'connected', personaDescriptions: {
    a: { connections: [{ type: 'character', id: 'char1' }, { type: 'group', id: 'g1' }], description: 'A' },
    b: { connections: [{ type: 'character', id: 'char1' }], description: 'B' },
}, characterKey: 'char1' });
await add('connection-obj-none', { method: 'connectionObj', selectedGroup: null, charAvatar: null });
await add('descriptor-existing', { method: 'descriptor', userAvatar: 'a', powerUser });
await add('resolve-all-empty', { method: 'resolve', chatMetaPersona: null, userAvatars: [], connectedPersonas: [], defaultPersona: null, allowMultiConnections: false, userAvatar: 'a' });
await add('resolve-multi-connections-allow', { method: 'resolve', chatMetaPersona: null, userAvatars: ['a', 'b'], connectedPersonas: ['a', 'b'], defaultPersona: null, allowMultiConnections: true, userAvatar: 'a' });
await add('resolve-connected-plus-chatmeta', { method: 'resolve', chatMetaPersona: 'a', userAvatars: ['a', 'b'], connectedPersonas: ['b'], defaultPersona: 'd', allowMultiConnections: false, userAvatar: 'b' });
await add('resolve-autolock-multi', { method: 'resolve', chatMetaPersona: null, userAvatars: ['a', 'b'], connectedPersonas: ['a', 'b'], defaultPersona: null, allowMultiConnections: true, userAvatar: 'b', personaAutoLock: true });


writeFileSync(outFile, JSON.stringify({ source: 'personas.js 纯逻辑', cases }, null, 2));
console.log('persona-engine:', cases.length, 'cases ->', outFile);
