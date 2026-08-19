#!/usr/bin/env node
// QuickReply 官方迁移/过滤纯函数（摘自 quick-reply/index.js L55-L104 loadSets 迁移段 + AutoExecuteHandler +
// QuickReplySettings visible 过滤）。函数体逐字摘自官方 1.18.0 release 8172dcd。
//
// 打桩：fetch/toastr/getRequestHeaders/settings 都不调用；cases 注入原始 set/settings/slot，
// 仅测 migrateSet/visibleSetNames/shouldAutoExecute 三个纯函数（差分用）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '../../engine/src/test/resources/diff/quickreply.json');

// ---------- 官方代码块 START（逐字摘自 quick-reply/index.js）----------
const defaultConfig = {
    setList: [{
        set: 'Default',
        isVisible: true,
    }],
};

function migrateSet(set) {
    if (set.version !== 2) {
        set.version = 2;
        set.disableSend = set.quickActionEnabled ?? false;
        set.placeBeforeInput = set.placeBeforeInputEnabled ?? false;
        set.injectInput = set.AutoInputInject ?? false;
        set.qrList = set.quickReplySlots.map((slot, idx) => {
            const qr = {};
            qr.id = idx + 1;
            qr.label = slot.label ?? '';
            qr.title = slot.title ?? '';
            qr.message = slot.mes ?? '';
            qr.isHidden = slot.hidden ?? false;
            qr.executeOnStartup = slot.autoExecute_appStartup ?? false;
            qr.executeOnUser = slot.autoExecute_userMessage ?? false;
            qr.executeOnAi = slot.autoExecute_botMessage ?? false;
            qr.preventAutoExecute = slot.preventAutoExecute ?? false;
            qr.automationId = slot.automationId ?? '';
            qr.placeBeforeInput = slot.placeBeforeInputEnabled ?? false;
            qr.injectInput = slot.AutoInputInject ?? false;
            qr.disableSend = slot.quickActionEnabled ?? false;
            return qr;
        });
        delete set.quickReplySlots;
    }
    return set;
}

function visibleSetNames(settings) {
    return settings.config.setList.filter(it => it.isVisible).map(it => it.set);
}

function shouldAutoExecute(slot, phase, settings, setName) {
    const vis = new Set(visibleSetNames(settings));
    if (settings.isEnabled === false) return false;
    if (setName && !settings.isCombined && !vis.has(setName)) return false;
    if (!slot) return false;
    if (slot.isHidden) return false;
    if (slot.preventAutoExecute) return false;
    if (phase === 'startup') return !!slot.executeOnStartup;
    if (phase === 'user')    return !!slot.executeOnUser;
    if (phase === 'ai')      return !!slot.executeOnAi;
    return false;
}
// ---------- 官方代码块 END ----------

function clone(o){ return JSON.parse(JSON.stringify(o)); }

function cases(){
    const out = [];
    let id = 0;

    // --- migrateSet 7 例 ---
    const v1 = {
        quickReplySlots: [
            { mes: '/hello', label: 'hi', title: 'greet', hidden: true,
              autoExecute_appStartup: true, autoExecute_userMessage: false, autoExecute_botMessage: true,
              preventAutoExecute: true, automationId: 'a1',
              placeBeforeInputEnabled: true, AutoInputInject: true, quickActionEnabled: true },
            { mes: '/bye' },
        ],
        quickActionEnabled: true,
        placeBeforeInputEnabled: false,
        AutoInputInject: true,
    };
    const r1 = migrateSet(clone(v1));
    out.push({ id: id++, name: 'qr-migrate-v1-version',     _tag: 'migrateSet', input: v1, expected: r1.version });
    out.push({ id: id++, name: 'qr-migrate-v1-set-fields',  _tag: 'migrateSet', input: v1,
        expected: { disableSend: r1.disableSend, placeBeforeInput: r1.placeBeforeInput, injectInput: r1.injectInput } });
    out.push({ id: id++, name: 'qr-migrate-v1-slot0-id',    _tag: 'migrateSet', input: v1, expected: r1.qrList[0].id });
    out.push({ id: id++, name: 'qr-migrate-v1-slot0-fields',_tag: 'migrateSet', input: v1, expected: r1.qrList[0] });
    out.push({ id: id++, name: 'qr-migrate-v1-slot1-defaults', _tag: 'migrateSet', input: v1, expected: r1.qrList[1] });
    out.push({ id: id++, name: 'qr-migrate-v1-quickReplySlots-deleted', _tag: 'migrateSet', input: v1,
        expected: 'quickReplySlots' in r1 ? r1.quickReplySlots : null });
    const v2 = { version: 2, quickReplySlots: [{ mes: '/x', label: 'x' }], disableSend: true,
        qrList: [{ id: 5, label: 'keep', message: '/keep' }] };
    const r6 = migrateSet(clone(v2));
    out.push({ id: id++, name: 'qr-migrate-v2-skip', _tag: 'migrateSet', input: v2,
        expected: { version: r6.version, qrList0: r6.qrList[0], disableSend: r6.disableSend,
            quickReplySlots: 'quickReplySlots' in r6 ? r6.quickReplySlots : null } });

    // --- visibleSetNames 2 例 ---
    const s7 = { isEnabled: true, isCombined: false, config: { setList: [
        { set: 'A', isVisible: true }, { set: 'B', isVisible: false }, { set: 'C', isVisible: true },
    ]}};
    out.push({ id: id++, name: 'qr-visible-set-names',   _tag: 'visibleSetNames', input: s7, expected: visibleSetNames(s7) });
    const s8 = { isEnabled: false, config: defaultConfig };
    out.push({ id: id++, name: 'qr-visible-default-set', _tag: 'visibleSetNames', input: s8, expected: visibleSetNames(s8) });

    // --- shouldAutoExecute 7 例 ---
    const settingsA = { isEnabled: true, isCombined: false, config: { setList: [
        { set: 'A', isVisible: true }, { set: 'B', isVisible: false },
    ]}};
    const slotGo = { isHidden: false, preventAutoExecute: false, executeOnStartup: true, executeOnUser: true, executeOnAi: true };
    const slotHidden = { ...slotGo, isHidden: true };
    const slotPrevent = { ...slotGo, preventAutoExecute: true };
    out.push({ id: id++, name: 'qr-autoexec-visible-set',    _tag: 'shouldAutoExecute',
        input: { slot: slotGo, phase: 'user', settings: settingsA, setName: 'A' },
        expected: shouldAutoExecute(slotGo, 'user', settingsA, 'A') });
    out.push({ id: id++, name: 'qr-autoexec-hidden-set',    _tag: 'shouldAutoExecute',
        input: { slot: slotGo, phase: 'user', settings: settingsA, setName: 'B' },
        expected: shouldAutoExecute(slotGo, 'user', settingsA, 'B') });
    out.push({ id: id++, name: 'qr-autoexec-hidden-slot',   _tag: 'shouldAutoExecute',
        input: { slot: slotHidden, phase: 'startup', settings: settingsA, setName: 'A' },
        expected: shouldAutoExecute(slotHidden, 'startup', settingsA, 'A') });
    out.push({ id: id++, name: 'qr-autoexec-prevent',       _tag: 'shouldAutoExecute',
        input: { slot: slotPrevent, phase: 'ai', settings: settingsA, setName: 'A' },
        expected: shouldAutoExecute(slotPrevent, 'ai', settingsA, 'A') });
    out.push({ id: id++, name: 'qr-autoexec-disabled',      _tag: 'shouldAutoExecute',
        input: { slot: slotGo, phase: 'user', settings: { ...settingsA, isEnabled: false }, setName: 'A' },
        expected: shouldAutoExecute(slotGo, 'user', { ...settingsA, isEnabled: false }, 'A') });
    out.push({ id: id++, name: 'qr-autoexec-combined',      _tag: 'shouldAutoExecute',
        input: { slot: slotGo, phase: 'user', settings: { ...settingsA, isCombined: true }, setName: 'B' },
        expected: shouldAutoExecute(slotGo, 'user', { ...settingsA, isCombined: true }, 'B') });
    out.push({ id: id++, name: 'qr-autoexec-no-phase',      _tag: 'shouldAutoExecute',
        input: { slot: slotGo, phase: 'unknown', settings: settingsA, setName: 'A' },
        expected: shouldAutoExecute(slotGo, 'unknown', settingsA, 'A') });

    return out;
}

function main(){
    const fixture = { generatedAt: new Date().toISOString(),
        source: 'quick-reply/index.js L55-L104 (migrateSet) + AutoExecuteHandler + QuickReplySettings visibleSetNames',
        cases: cases() };
    writeFileSync(OUT, JSON.stringify(fixture, null, 2));
    console.log('quickreply fixtures:', fixture.cases.length, '→', OUT);
}
main();
