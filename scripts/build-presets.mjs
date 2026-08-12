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

function write(rel, data) {
    const file = join(outDir, `${rel}.json`);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, JSON.stringify({ source: 'sillytavern-ref default/content/presets', presets: data }, null, 2) + '\n');
    console.log(`wrote ${file} (${data.length} presets)`);
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
