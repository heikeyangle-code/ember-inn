#!/usr/bin/env node
// 官方 WorldInfoTimedEffects（world-info.js:479-800 类）→ JSON fixture。
// 类方法体逐字摘自 SillyTavern 1.18.0 release 8172dcd；
// 打桩登记：chat_metadata 用可序列化全局桩（结构同官方 timedWorldInfo），console 打桩为空。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outFile = join(here, '..', '..', 'engine', 'src', 'test', 'resources', 'diff', 'worldinfo-timed-effects.json');

// 官方类（逐字提取 world-info.js 479-800；私有字段与 # 方法保留）
const timedEffectsClass = `
let chat_metadata = {};
const console = { log() {}, debug() {}, error() {}, warn() {} };

class WorldInfoTimedEffects {
    #chat = [];
    #entries = [];
    #isDryRun = false;
    #buffer = { 'sticky': [], 'cooldown': [], 'delay': [] };
    #onEnded = {
        'sticky': (entry) => {
            if (!entry.cooldown) return;
            const key = this.#getEntryKey(entry);
            const effect = this.#getEntryTimedEffect('cooldown', entry, true);
            chat_metadata.timedWorldInfo.cooldown[key] = effect;
            this.#buffer.cooldown.push(entry);
        },
        'cooldown': (entry) => {},
        'delay': () => {},
    };

    constructor(chat, entries, isDryRun = false) {
        this.#chat = chat;
        this.#entries = entries;
        this.#isDryRun = isDryRun;
        this.#ensureChatMetadata();
    }

    #ensureChatMetadata() {
        if (!chat_metadata.timedWorldInfo) chat_metadata.timedWorldInfo = {};
        ['sticky', 'cooldown'].forEach(type => {
            if (!chat_metadata.timedWorldInfo[type] || typeof chat_metadata.timedWorldInfo[type] !== 'object') {
                chat_metadata.timedWorldInfo[type] = {};
            }
            Object.entries(chat_metadata.timedWorldInfo[type]).forEach(([key, value]) => {
                if (!value || typeof value !== 'object') delete chat_metadata.timedWorldInfo[type][key];
            });
        });
    }

    #getEntryHash(entry) { return entry.hash; }

    #getEntryKey(entry) { return \`\${entry.world}.\${entry.uid}\`; }

    #getEntryTimedEffect(type, entry, isProtected) {
        return { hash: this.#getEntryHash(entry), start: this.#chat.length, end: this.#chat.length + Number(entry[type]), protected: !!isProtected };
    }

    #checkTimedEffectOfType(type, buffer, onEnded) {
        const effects = Object.entries(chat_metadata.timedWorldInfo[type]);
        for (const [key, value] of effects) {
            const entry = this.#entries.find(x => String(this.#getEntryHash(x)) === String(value.hash));

            if (this.#chat.length <= Number(value.start) && !value.protected) {
                delete chat_metadata.timedWorldInfo[type][key];
                continue;
            }

            if (!entry) {
                if (this.#chat.length >= Number(value.end)) {
                    delete chat_metadata.timedWorldInfo[type][key];
                }
                continue;
            }

            if (!entry[type]) {
                delete chat_metadata.timedWorldInfo[type][key];
                continue;
            }

            if (this.#chat.length >= Number(value.end)) {
                delete chat_metadata.timedWorldInfo[type][key];
                if (typeof onEnded === 'function') onEnded(entry);
                continue;
            }

            buffer.push(entry);
        }
    }

    #checkDelayEffect(buffer) {
        for (const entry of this.#entries) {
            if (!entry.delay) continue;
            if (this.#chat.length < entry.delay) buffer.push(entry);
        }
    }

    checkTimedEffects() {
        if (!this.#isDryRun) {
            this.#checkTimedEffectOfType('sticky', this.#buffer.sticky, this.#onEnded.sticky.bind(this));
            this.#checkTimedEffectOfType('cooldown', this.#buffer.cooldown, this.#onEnded.cooldown.bind(this));
        }
        this.#checkDelayEffect(this.#buffer.delay);
    }

    getEffectMetadata(type, entry) {
        if (!this.isValidEffectType(type)) return null;
        const key = this.#getEntryKey(entry);
        return chat_metadata.timedWorldInfo[type][key];
    }

    #setTimedEffectOfType(type, entry) {
        if (!entry[type]) return;
        const key = this.#getEntryKey(entry);
        if (!chat_metadata.timedWorldInfo[type][key]) {
            const effect = this.#getEntryTimedEffect(type, entry, false);
            chat_metadata.timedWorldInfo[type][key] = effect;
        }
    }

    setTimedEffects(activatedEntries) {
        if (this.#isDryRun) return;
        for (const entry of activatedEntries) {
            this.#setTimedEffectOfType('sticky', entry);
            this.#setTimedEffectOfType('cooldown', entry);
        }
    }

    setTimedEffect(type, entry, newState) {
        if (!this.isValidEffectType(type)) return;
        if (this.#isDryRun && type !== 'delay') return;
        const key = this.#getEntryKey(entry);
        delete chat_metadata.timedWorldInfo[type][key];
        if (newState) {
            const effect = this.#getEntryTimedEffect(type, entry, false);
            chat_metadata.timedWorldInfo[type][key] = effect;
        }
    }

    isValidEffectType(type) {
        return typeof type === 'string' && ['sticky', 'cooldown', 'delay'].includes(type.trim().toLowerCase());
    }

    isEffectActive(type, entry) {
        if (!this.isValidEffectType(type)) return false;
        return this.#buffer[type]?.some(x => this.#getEntryHash(x) === this.#getEntryHash(entry)) ?? false;
    }

    cleanUp() {
        for (const buffer of Object.values(this.#buffer)) buffer.splice(0, buffer.length);
    }
}
`;

// 场景 runner：跑操作后输出缓冲 hash 列表（sticky/cooldown 从元数据推导 + delay 直接扫描）
const runCase = new Function([
    timedEffectsClass,
    `return (c) => {
        chat_metadata = JSON.parse(JSON.stringify(c.metadata || {}));
        const te = new WorldInfoTimedEffects(c.chat, c.entries, c.isDryRun || false);
        if (c.op === 'check') te.checkTimedEffects();
        else if (c.op === 'setTimedEffects') te.setTimedEffects(c.entries);
        else if (c.op === 'setTimedEffect') te.setTimedEffect(c.type, c.entry, c.newState);
        else if (c.op === 'cleanUp') te.cleanUp();
        // 官方 #buffer 私有；用公开 isEffectActive 逐 entry 枚举，准确反映真实缓冲
        const buffered = (type) => c.entries.filter(e => te.isEffectActive(type, e)).map(e => e?.hash);
        return {
            buffers: {
                sticky: buffered('sticky'),
                cooldown: buffered('cooldown'),
                delay: buffered('delay'),
            },
            metadata: chat_metadata,
        };
    };`,
].join('\n'));

const cases = [];
function add(id, body) {
    const expected = runCase()(body);
    cases.push({ id, args: body, expected });
}

const entryA = { uid: 1, world: 'w', hash: 11, sticky: 3, cooldown: 2, delay: 5 };
const entryB = { uid: 2, world: 'w', hash: 22, sticky: 0, cooldown: 0, delay: 2 };
const entryC = { uid: 3, world: 'w', hash: 33, sticky: 2, cooldown: 0 };

add('empty', { op: 'check', chat: [], entries: [], metadata: {}, isDryRun: false });
add('sticky-active', {
    op: 'check',
    chat: ['a', 'b'],
    entries: [entryA],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 0, end: 3, protected: false } }, cooldown: {} } },
});
add('sticky-expired-to-cooldown', {
    op: 'check',
    chat: ['a', 'b', 'c', 'd'],
    entries: [entryA],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 0, end: 3, protected: false } }, cooldown: {} } },
});
add('sticky-expired-no-cooldown', {
    op: 'check',
    chat: ['a', 'b', 'c', 'd'],
    entries: [entryC],
    metadata: { timedWorldInfo: { sticky: { 'w.3': { hash: 33, start: 0, end: 2, protected: false } }, cooldown: {} } },
});
add('chat-not-advanced-removes', {
    op: 'check',
    chat: ['a'],
    entries: [entryA],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 1, end: 4, protected: false } }, cooldown: {} } },
});
add('entry-missing-kept-until-end', {
    op: 'check',
    chat: ['a', 'b'],
    entries: [],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 0, end: 3, protected: false } }, cooldown: {} } },
});
add('entry-missing-expired-removed', {
    op: 'check',
    chat: ['a', 'b', 'c', 'd'],
    entries: [],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 0, end: 3, protected: false } }, cooldown: {} } },
});
add('cooldown-active', {
    op: 'check',
    chat: ['a', 'b'],
    entries: [entryA],
    metadata: { timedWorldInfo: { sticky: {}, cooldown: { 'w.1': { hash: 11, start: 1, end: 3, protected: true } } } },
});
add('delay-applies', { op: 'check', chat: ['a'], entries: [entryA, entryB], metadata: {} });
add('set-timed-effects-new', { op: 'setTimedEffects', chat: ['a'], entries: [entryA, entryC], metadata: {} });
add('set-timed-effect-force', {
    op: 'setTimedEffect',
    chat: ['a', 'b'],
    entries: [entryA],
    type: 'sticky',
    entry: entryA,
    newState: true,
    metadata: { timedWorldInfo: { sticky: {}, cooldown: {} } },
});
add('set-timed-effect-clear', {
    op: 'setTimedEffect',
    chat: ['a', 'b'],
    entries: [entryA],
    type: 'cooldown',
    entry: entryA,
    newState: false,
    metadata: { timedWorldInfo: { sticky: {}, cooldown: { 'w.1': { hash: 11, start: 0, end: 2, protected: false } } } },
});
add('dry-run-check', {
    op: 'check',
    chat: ['a'],
    entries: [entryA],
    metadata: { timedWorldInfo: { sticky: { 'w.1': { hash: 11, start: 0, end: 3, protected: false } }, cooldown: {} } },
    isDryRun: true,
});
add('clean-up', { op: 'cleanUp', chat: [], entries: [entryA], metadata: {} });

writeFileSync(outFile, JSON.stringify({ source: 'world-info.js WorldInfoTimedEffects 类', cases }, null, 2));
console.log('worldinfo-timed-effects:', cases.length, 'cases ->', outFile);
