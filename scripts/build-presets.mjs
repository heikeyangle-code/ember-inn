#!/usr/bin/env node
// 把官方 default/content/presets 打包进引擎 resources（快照，官方发版后重跑）。

import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outDir = join(repoRoot, 'engine', 'src', 'main', 'resources', 'presets');

function loadDir(rel) {
    const dir = join(officialRef, 'default', 'content', 'presets', rel);
    const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
    return files.map((f) => JSON.parse(readFileSync(join(dir, f), 'utf8')));
}

// 官方 context/instruct 预设文件本身不含 name（文件名即预设名），打包时补上
function loadNamedDir(rel) {
    const dir = join(officialRef, 'default', 'content', 'presets', rel);
    const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
    return files.map((f) => ({
        ...JSON.parse(readFileSync(join(dir, f), 'utf8')),
        name: f.replace(/\.json$/, ''),
    }));
}

function loadSamplerDir(rel) {
    const dir = join(officialRef, 'default', 'content', 'presets', rel);
    const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
    return files.map((f) => ({
        ...JSON.parse(readFileSync(join(dir, f), 'utf8')),
        name: f.replace(/\.json$/, ''),
    }));
}

// 用户确认裁剪：老模型专用/用处不大的内置预设不打包（2026-08-16；官方发版重跑本脚本仍保持裁剪）
const trimPresets = {
    context: new Set([
        'Alpaca', 'Alpaca-Single-Turn', 'Command R', 'DeepSeek-V2.5', 'Dots1', 'GLM-4', 'Gemma 2', 'Gemma 4',
        'Libra-32B', 'Lightning 1.1', 'Llama 2 Chat', 'Llama 3 Instruct', 'Llama 4 Instruct', 'Llama-3-Instruct-Names',
        'Metharme', 'Mistral V1', 'Mistral V2 & V3', 'Mistral V3-Tekken', 'Mistral V7-Tekken', 'Mistral V7',
        'Moonshot AI', 'OldDefault', 'OpenAI Harmony', 'Phi', 'Synthia', 'Tulu', 'simple-proxy-for-tavern',
    ]),
    instruct: new Set([
        'Alpaca-Single-Turn', 'Command R', 'DeepSeek-V2.5', 'Dots1', 'GLM-4', 'Gemma 2', 'Gemma 4', 'Koala',
        'Libra-32B', 'Lightning 1.1', 'Llama 2 Chat', 'Llama 3 Instruct', 'Llama 4 Instruct', 'Llama-3-Instruct-Names',
        'Metharme', 'Mistral V1', 'Mistral V2 & V3', 'Mistral V3-Tekken', 'Mistral V7-Tekken', 'Mistral V7',
        'Moonshot AI', 'OpenAI Harmony (Thinking)', 'OpenAI Harmony', 'OpenOrca-OpenChat', 'Phi', 'Synthia', 'Tulu',
        'Vicuna 1.0', 'Vicuna 1.1', 'WizardLM-13B', 'WizardLM', 'simple-proxy-for-tavern',
    ]),
    'sampler-novel': new Set([
        'Edgewise-Clio', 'Erato-Dragonfruit', 'Erato-Golden Arrow', 'Erato-Shosetsu', 'Erato-Wilder',
        'Erato-Zany Scribe', 'Fresh-Coffee-Clio', 'Keelback-Clio', 'Long-Press-Clio', 'Talker-Chat-Clio', 'Vingt-Un-Clio',
    ]),
    reasoning: new Set(['DeepSeek', 'Gemma 4', 'OpenAI Harmony']),
};

function trimmed(rel, data) {
    const trim = trimPresets[rel];
    return trim ? data.filter((p) => !trim.has(p.name)) : data;
}

function write(rel, data) {
    const file = join(outDir, `${rel}.json`);
    mkdirSync(dirname(file), { recursive: true });
    const out = trimmed(rel, data);
    writeFileSync(file, JSON.stringify({ source: 'sillytavern-ref default/content/presets（用户裁剪）', presets: out }, null, 2) + '\n');
    console.log(`wrote ${file} (${out.length} presets)`);
}

write('context', loadNamedDir('context'));
write('instruct', loadNamedDir('instruct'));
write('sampler-openai', loadSamplerDir('openai'));
write('sampler-textgen', loadSamplerDir('textgen'));
write('sampler-novel', loadSamplerDir('novel'));
write('sampler-kobold', loadSamplerDir('kobold'));
write('sysprompt', loadSamplerDir('sysprompt'));
write('reasoning', loadSamplerDir('reasoning'));
write('quick-replies', loadDir('quick-replies'));
