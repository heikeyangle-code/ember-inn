/**
 * 官方 PromptManager.js 纯逻辑差分：Prompt 构造 / shouldTrigger / preparePrompt /
 * getPromptCollection / getPromptOrderForCharacter。
 * 提取源（SillyTavern 1.18.0 / 8172dcd）：public/scripts/PromptManager.js
 *   - class Prompt 构造（80-208 行字段语义）
 *   - preparePrompt / getPromptCollection / shouldTrigger / getPromptOrderForCharacter（1277/1516/1549/1196 附近）
 * 打桩登记：
 *   - substituteParams：只替换 {{user}}/{{char}}/{{original}}（{{groupOverride}} 依赖官方全局宏表，
 *     完整宏语义由 macros 差分覆盖，prepare 用例不覆盖该键）。
 *   - getActiveGroupCharacters → []（prepare 的 groupOverride 分支不参与本差分）。
 *   - structuredClone 用 Node 原生。
 *   - Prompt 序列化投影 12 字段（identifier/role/content/name/system_prompt/position/
 *     injection_depth/injection_position/forbid_overrides/extension/injection_order/injection_trigger），
 *     enabled/marker 官方 new Prompt() 不复制（undefined），两端一致剔除。
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 官方 Prompt 构造（逐字） ----
class Prompt {
    constructor({ identifier, role, content, name, system_prompt, position, injection_depth, injection_position, forbid_overrides, extension, injection_order, injection_trigger } = {}) {
        this.identifier = identifier;
        this.role = role;
        this.content = content;
        this.name = name;
        this.system_prompt = system_prompt;
        this.position = position;
        this.injection_depth = injection_depth;
        this.injection_position = injection_position;
        this.forbid_overrides = forbid_overrides;
        this.extension = extension ?? false;
        this.injection_order = injection_order ?? DEFAULT_ORDER;
        this.injection_trigger = injection_trigger ?? [];
    }
}

// ---- 官方 PromptCollection 行为桩 ----
class PromptCollection {
    collection = [];
    overriddenPrompts = [];
    add(p) { this.collection.push(p); }
}

// ---- 官方宏桩（只替换四个键，见头部登记） ----
function substituteParams(text, overrides = {}) {
    let out = String(text ?? '');
    const map = { user: 'User', char: 'Char', original: '', groupOverride: '' };
    for (const k of Object.keys(map)) {
        const v = k in overrides ? String(overrides[k] ?? '') : map[k];
        out = out.replaceAll('{{' + k + '}}', v);
    }
    return out;
}

const DEFAULT_ORDER = 100;

// ---- 官方 PromptManager 方法（逐字，this 依赖打桩） ----
class PM {
    constructor({ serviceSettings = {}, activeCharacter = null, groupMembers = [] } = {}) {
        this.serviceSettings = serviceSettings;
        this.activeCharacter = activeCharacter;
        this._groupMembers = groupMembers;
    }
    getPromptById(identifier) {
        return this.serviceSettings.prompts.find(item => item && item.identifier === identifier) ?? null;
    }
    getActiveGroupCharacters() {
        return this._groupMembers;
    }
    preparePrompt(prompt, original = null) {
        const groupMembers = this.getActiveGroupCharacters();
        const preparedPrompt = new Prompt(prompt);

        if (typeof original === 'string') {
            if (0 < groupMembers.length) preparedPrompt.content = substituteParams(prompt.content ?? '', { original, groupOverride: groupMembers.join(', ') });
            else preparedPrompt.content = substituteParams(prompt.content, { original });
        } else {
            if (0 < groupMembers.length) preparedPrompt.content = substituteParams(prompt.content ?? '', { groupOverride: groupMembers.join(', ') });
            else preparedPrompt.content = substituteParams(prompt.content);
        }

        return preparedPrompt;
    }
    getPromptCollection(generationType) {
        generationType = String(generationType || 'normal').toLowerCase().trim();
        const promptCollection = new PromptCollection();
        const promptOrder = this.getPromptOrderForCharacter(this.activeCharacter);

        promptOrder.forEach(entry => {
            const prompt = this.getPromptById(entry.identifier);
            const allowedTrigger = entry.enabled && this.shouldTrigger(prompt, generationType);

            if (!prompt) {
                return;
            }

            if (allowedTrigger) {
                promptCollection.add(this.preparePrompt(prompt));
            } else if (entry.identifier === 'main') {
                const replacementPrompt = structuredClone(prompt);
                replacementPrompt.content = '';
                promptCollection.add(this.preparePrompt(replacementPrompt));
            }
        });

        return promptCollection;
    }
    shouldTrigger(prompt, generationType) {
        if (!Array.isArray(prompt?.injection_trigger)) return true;
        if (!prompt.injection_trigger.length) return true;
        return prompt.injection_trigger.includes(generationType);
    }
    getPromptOrderForCharacter(character) {
        return !character ? [] : (this.serviceSettings.prompt_order.find(list => String(list.character_id) === String(character.id))?.order ?? []);
    }
}

const PROJECT = ['identifier', 'role', 'content', 'name', 'system_prompt', 'position', 'injection_depth', 'injection_position', 'forbid_overrides', 'extension', 'injection_order', 'injection_trigger'];
const pick = (p) => Object.fromEntries(PROJECT.map(k => [k, p[k]]));

const cases = [];

// ---- Prompt 构造函数 ----
for (const obj of [
    {}, { identifier: 'main' }, { identifier: 'x', injection_order: 5, injection_trigger: ['normal'] },
    { identifier: 'y', extension: true, forbid_overrides: true, role: 'user', content: 'hi', system_prompt: false, position: 'start', injection_depth: 3, injection_position: 1 },
]) {
    cases.push({ kind: 'constructor', args: obj, expected: JSON.stringify(pick(new Prompt(obj))) });
}

// ---- shouldTrigger ----
const trig = [
    [undefined, 'normal'],
    [{ injection_trigger: 'not-array' }, 'normal'],
    [{ injection_trigger: [] }, 'normal'],
    [{ injection_trigger: ['normal'] }, 'normal'],
    [{ injection_trigger: ['normal'] }, 'continue'],
    [{ injection_trigger: ['continue', 'quiet'] }, 'quiet'],
    [{ injection_trigger: ['Normal'] }, 'normal'],
];
for (const [p, type] of trig) {
    cases.push({ kind: 'trigger', prompt: p ?? null, type, expected: String(new PM({}).shouldTrigger(p, type)) });
}

// ---- preparePrompt ----
const prepareCases = [
    { content: 'plain text', original: null },
    { content: 'hi {{user}} / {{char}}', original: null },
    { content: 'orig={{original}}', original: 'ORIG' },
    { content: 'no original {{original}}', original: null },
];
for (const pc of prepareCases) {
    const p = new PM({}).preparePrompt({ identifier: 'id1', name: 'N', content: pc.content, role: 'system' }, pc.original);
    cases.push({ kind: 'prepare', content: pc.content, original: pc.original, expected: JSON.stringify(pick(p)) });
}

// ---- getPromptCollection ----
const basePrompts = [
    { identifier: 'main', name: 'Main', content: 'M {{user}}', role: 'system', system_prompt: true },
    { identifier: 'worldInfoBefore', name: 'WI', content: 'WI', role: 'system' },
    { identifier: 'chatHistory', name: 'History', content: '', role: 'system', marker: true },
    { identifier: 'jailbreak', name: 'JB', content: 'JB', role: 'system' },
    { identifier: 'custom', name: 'Custom', content: 'C {{original}}', role: 'user', system_prompt: false, injection_trigger: ['normal'] },
    { identifier: 'triggered', name: 'T', content: 'T', role: 'system', injection_trigger: ['continue'] },
];
const defaultOrder = [
    { identifier: 'main', enabled: true }, { identifier: 'worldInfoBefore', enabled: true },
    { identifier: 'chatHistory', enabled: true }, { identifier: 'jailbreak', enabled: true },
];
const collectionCases = [
    { order: [], type: 'normal' },
    { order: defaultOrder, type: 'normal' },
    { order: defaultOrder, type: '  NORMAL  ' },
    { order: defaultOrder, type: '' },
    { order: [{ identifier: 'main', enabled: false }], type: 'normal' },
    { order: [{ identifier: 'unknown', enabled: true }], type: 'normal' },
    { order: [...defaultOrder, { identifier: 'custom', enabled: true }, { identifier: 'triggered', enabled: true }], type: 'normal' },
    { order: [...defaultOrder, { identifier: 'custom', enabled: true }, { identifier: 'triggered', enabled: true }], type: 'continue' },
    { order: defaultOrder.map(e => ({ ...e, enabled: false })), type: 'normal' },
    { order: [...defaultOrder, { identifier: 'custom', enabled: true }], type: 'quiet' },
];
for (const cc of collectionCases) {
    const pm = new PM({ serviceSettings: { prompts: basePrompts, prompt_order: [{ character_id: 'c1', order: cc.order }] }, activeCharacter: { id: 'c1' } });
    const out = pm.getPromptCollection(cc.type);
    cases.push({ kind: 'collection', order: cc.order, type: cc.type, expected: JSON.stringify(out.collection.map(pick)) });
}

// ---- getPromptOrderForCharacter ----
const orderCases = [
    { character: null, list: [] },
    { character: { id: 'c1' }, list: [{ character_id: 'c1', order: [{ identifier: 'main' }] }] },
    { character: { id: '5' }, list: [{ character_id: 5, order: [{ identifier: 'a', enabled: false }] }] },
    { character: { id: 'c2' }, list: [{ character_id: 'c1', order: [{ identifier: 'main' }] }] },
];
for (const oc of orderCases) {
    const pm = new PM({ serviceSettings: { prompt_order: oc.list }, activeCharacter: oc.character });
    cases.push({ kind: 'order', character: oc.character, list: oc.list, expected: JSON.stringify(pm.getPromptOrderForCharacter(oc.character)) });
}

const out = join(__dirname, '..', '..', 'engine/src/test/resources/diff/prompt-manager.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(cases, null, 1) + '\n');
console.log('prompt-manager cases:', cases.length, '->', out);
