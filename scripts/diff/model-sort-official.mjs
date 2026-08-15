/**
 * 官方 openai.js sortModelsBy / groupModelsByVendor 纯逻辑差分。
 * 提取源（SillyTavern 1.18.0 / 8172dcd）：public/scripts/openai.js 2394-2500 行（逐字）。
 * 打桩登记：
 *   - 模型对象只投影 id/name/context_length/pricing/tokens/info 字段；
 *   - 官方 filter（electronhub endpoints / chutes affine / aimlapi type）为 DOM 加载函数内联行，另行登记由 App 层实现。
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 官方函数（逐字） ----
function sortModelsBy(data, property, source) {
    switch (source) {
        case 'openrouter':
            return data.sort((a, b) => {
                if (property === 'context_length') {
                    return (b.context_length || 0) - (a.context_length || 0);
                } else if (property === 'pricing.input' || property === 'pricing.prompt') {
                    return parseFloat(a.pricing?.prompt || 0) - parseFloat(b.pricing?.prompt || 0);
                } else if (property === 'pricing.output' || property === 'pricing.completion') {
                    return parseFloat(a.pricing?.completion || 0) - parseFloat(b.pricing?.completion || 0);
                } else {
                    return a?.name && b?.name ? a.name.localeCompare(b.name) : 0;
                }
            });
        case 'chutes':
            return data.sort((a, b) => {
                if (property === 'context_length') {
                    return (b.context_length || 0) - (a.context_length || 0);
                } else if (property === 'pricing.input' || property === 'pricing.prompt') {
                    return parseFloat(a.pricing?.input || 0) - parseFloat(b.pricing?.input || 0);
                } else if (property === 'pricing.output' || property === 'pricing.completion') {
                    return parseFloat(a.pricing?.output || 0) - parseFloat(b.pricing?.output || 0);
                } else {
                    return a?.id && b?.id ? a.id.localeCompare(b.id) : 0;
                }
            });
        case 'electronhub':
            return data.sort((a, b) => {
                if (property === 'context_length') {
                    return (b.tokens || 0) - (a.tokens || 0);
                } else if (property === 'pricing.input' || property === 'pricing.prompt') {
                    return parseFloat(a.pricing?.input || 0) - parseFloat(b.pricing?.input || 0);
                } else if (property === 'pricing.output' || property === 'pricing.completion') {
                    return parseFloat(a.pricing?.output || 0) - parseFloat(b.pricing?.output || 0);
                } else {
                    return a?.name && b?.name ? a.name.localeCompare(b.name) : 0;
                }
            });
        case 'nanogpt':
            return data.sort((a, b) => {
                if (property === 'context_length') {
                    return (b.context_length || 0) - (a.context_length || 0);
                } else if (property === 'pricing.input' || property === 'pricing.prompt') {
                    return parseFloat(a.pricing?.prompt || 0) - parseFloat(b.pricing?.prompt || 0);
                } else if (property === 'pricing.output' || property === 'pricing.completion') {
                    return parseFloat(a.pricing?.completion || 0) - parseFloat(b.pricing?.completion || 0);
                } else {
                    return a?.name && b?.name ? a.name.localeCompare(b.name) : 0;
                }
            });
        case 'aimlapi':
            return data.sort((a, b) => {
                if (property === 'context_length') {
                    return (b.info?.contextLength || 0) - (a.info?.contextLength || 0);
                } else {
                    return a?.info?.name && b?.info?.name ? a.info.name.localeCompare(b.info.name) : 0;
                }
            });
        default:
            return data;
    }
}

function groupModelsByVendor(array, source) {
    switch (source) {
        case 'openrouter':
            return array.reduce((acc, curr) => {
                const vendor = curr.id.split('/')[0];
                if (!acc.has(vendor)) {
                    acc.set(vendor, []);
                }
                acc.get(vendor).push(curr);
                return acc;
            }, new Map());
        case 'electronhub':
            return array.reduce((acc, curr) => {
                const vendor = String(curr?.name || curr?.id || 'Other').split(':')[0].trim() || 'Other';
                if (!acc.has(vendor)) {
                    acc.set(vendor, []);
                }
                acc.get(vendor).push(curr);
                return acc;
            }, new Map());
        case 'nanogpt':
            return array.reduce((acc, curr) => {
                const vendorPart = /\//.test(curr.id) ? curr.id.split('/')[0] : curr.id.split('-')[0];
                const vendor = String(vendorPart?.trim()?.toLowerCase() || 'Other');
                if (!acc.has(vendor)) {
                    acc.set(vendor, []);
                }
                acc.get(vendor).push(curr);
                return acc;
            }, new Map());
        case 'chutes':
            return array.reduce((acc, curr) => {
                const vendor = curr.id.split('/')[0];
                if (!acc.has(vendor)) {
                    acc.set(vendor, []);
                }
                acc.get(vendor).push(curr);
                return acc;
            }, new Map());
        case 'aimlapi':
            return array.reduce((acc, curr) => {
                const vendor = curr.info?.developer || 'Other';
                if (!acc.has(vendor)) {
                    acc.set(vendor, []);
                }
                acc.get(vendor).push(curr);
                return acc;
            }, new Map());
        default:
            return new Map([['', array]]);
    }
}

// ---- 用例 ----
const cases = [];
const ids = (arr) => arr.map(m => m.id);

const base = [
    { id: 'b-model', name: 'Beta Model', context_length: 1000, pricing: { prompt: 2.5, completion: 5 } },
    { id: 'a-model', name: 'Alpha Model', context_length: 2000, pricing: { prompt: 1, completion: 3 } },
    { id: 'c-model', name: 'Gamma', context_length: 500 },
    { id: 'z-model', pricing: { prompt: 10 } },
    { id: 'no-meta' },
];

const sources = ['openrouter', 'chutes', 'electronhub', 'nanogpt', 'aimlapi', 'unknown'];
const properties = ['alphabetically', 'context_length', 'pricing.prompt', 'pricing.input', 'pricing.completion', 'pricing.output'];

for (const source of sources) {
    for (const property of properties) {
        const copy = structuredClone(base);
        const out = sortModelsBy(copy, property, source);
        cases.push({ kind: 'sort', source, property, input: structuredClone(base), expected: JSON.stringify(ids(out)) });
    }
}

// 特殊字段：electronhub tokens / aimlapi info
const electronhubModels = [
    { id: 'm1', name: 'Vendor B: model', tokens: 500, pricing: { input: 2, output: 4 } },
    { id: 'm2', name: 'Vendor A: model', tokens: 1000, pricing: { input: 1, output: 3 } },
    { id: 'm3', name: 'Vendor C: model', tokens: 100 },
];
for (const property of ['alphabetically', 'context_length', 'pricing.prompt', 'pricing.completion']) {
    cases.push({ kind: 'sort', source: 'electronhub', property, input: structuredClone(electronhubModels), expected: JSON.stringify(ids(sortModelsBy(structuredClone(electronhubModels), property, 'electronhub'))) });
}

const aimlapiModels = [
    { id: 'a1', info: { name: 'Zeta', contextLength: 100, developer: 'Dev B' } },
    { id: 'a2', info: { name: 'Alpha', contextLength: 200, developer: 'Dev A' } },
    { id: 'a3' },
];
for (const property of ['alphabetically', 'context_length']) {
    cases.push({ kind: 'sort', source: 'aimlapi', property, input: structuredClone(aimlapiModels), expected: JSON.stringify(ids(sortModelsBy(structuredClone(aimlapiModels), property, 'aimlapi'))) });
}

// groupModelsByVendor
const groupSets = [
    { source: 'openrouter', models: [{ id: 'anthropic/claude-x' }, { id: 'openai/gpt-y' }, { id: 'anthropic/claude-z' }] },
    { source: 'chutes', models: [{ id: 'deepseek/xx' }, { id: 'meta/yy' }] },
    { source: 'electronhub', models: [{ id: 'a', name: 'Vendor One: Model' }, { id: 'b', name: 'Vendor Two: Model' }, { id: 'c' }] },
    { source: 'nanogpt', models: [{ id: 'OpenAI/gpt-x' }, { id: 'meta-llama' }, { id: 'gpt-4o' }] },
    { source: 'aimlapi', models: [{ id: 'x', info: { developer: 'Dev A' } }, { id: 'y', info: { developer: 'Dev B' } }, { id: 'z' }] },
    { source: 'other', models: [{ id: 'a' }, { id: 'b' }] },
];
for (const g of groupSets) {
    const grouped = groupModelsByVendor(g.models, g.source);
    const expected = Object.fromEntries(Array.from(grouped.entries()).map(([k, v]) => [k, ids(v)]));
    cases.push({ kind: 'group', source: g.source, input: structuredClone(g.models), expected: JSON.stringify(expected) });
}

const out = JSON.stringify({ source: 'openai.js sortModelsBy/groupModelsByVendor', cases }, null, 2);
const target = join(__dirname, '..', '..', 'engine', 'src', 'test', 'resources', 'diff', 'model-sort.json');
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, out + '\n');
console.log(`wrote ${cases.length} cases -> ${target}`);
