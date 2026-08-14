#!/usr/bin/env node
// 官方 src/util.js mergeObjectWithYaml / excludeKeysByYaml → JSON fixture。
// 函数体逐字取自官方源码；yaml 依赖 = 官方 util.js 的 'yaml' 包（生成前 npm i yaml，见 README）。
// 输出 engine/src/test/resources/diff/yaml-merge.json。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'yaml-merge.json');

const require = createRequire(import.meta.url);
// 优先项目内 node_modules；否则用本地安装的 yaml 包
let yaml;
try {
    yaml = require('yaml');
} catch {
    yaml = require('/data/data/com.termux/files/usr/tmp/diffdeps/node_modules/yaml');
}

const src = readFileSync(join(officialRef, 'src', 'util.js'), 'utf8');

// 以下两个函数体逐字取自官方 src/util.js（yaml.parse 与官方同包）
export function mergeObjectWithYaml(obj, yamlString) {
    if (!yamlString) {
        return;
    }

    try {
        const parsedObject = yaml.parse(yamlString);

        if (Array.isArray(parsedObject)) {
            for (const item of parsedObject) {
                if (typeof item === 'object' && item && !Array.isArray(item)) {
                    Object.assign(obj, item);
                }
            }
        } else if (parsedObject && typeof parsedObject === 'object') {
            Object.assign(obj, parsedObject);
        }
    } catch {
        // Do nothing
    }
}

export function excludeKeysByYaml(obj, yamlString) {
    if (!yamlString) {
        return;
    }

    try {
        const parsedObject = yaml.parse(yamlString);

        if (Array.isArray(parsedObject)) {
            parsedObject.forEach(key => {
                delete obj[key];
            });
        } else if (typeof parsedObject === 'object') {
            Object.keys(parsedObject).forEach(key => {
                delete obj[key];
            });
        } else if (typeof parsedObject === 'string') {
            delete obj[parsedObject];
        }
    } catch {
        // Do nothing
    }
}

function clone(v) { return JSON.parse(JSON.stringify(v)); }

const cases = [
    { id: 'merge_flat', fn: 'merge', obj: { model: 'm' }, yaml: 'temperature: 0.7\nstream: true\nstop: [x]\nname: "a: b"\n' },
    { id: 'merge_nested', fn: 'merge', obj: { model: 'm' }, yaml: 'provider:\n  order:\n    - a\n    - b\n  allow: true\n' },
    { id: 'merge_anchors_mergekey', fn: 'merge', obj: { model: 'm' }, yaml: 'base: &b\n  x: 1\n  y: 2\nchild:\n  <<: *b\n  y: 3\n' },
    { id: 'merge_top_level_array', fn: 'merge', obj: { model: 'm' }, yaml: '- {a: 1}\n- {b: 2, a: 9}\n' },
    { id: 'merge_multidoc_ignored', fn: 'merge', obj: { model: 'm' }, yaml: 'a: 1\n---\nb: 2\n' },
    { id: 'merge_invalid_ignored', fn: 'merge', obj: { model: 'm' }, yaml: 'a: [unclosed\n' },
    { id: 'merge_empty', fn: 'merge', obj: { model: 'm' }, yaml: '' },
    { id: 'exclude_array', fn: 'exclude', obj: { a: 1, b: 2, c: 3 }, yaml: '- a\n- c\n' },
    { id: 'exclude_object', fn: 'exclude', obj: { a: 1, b: 2 }, yaml: 'b: whatever\n' },
    { id: 'exclude_string', fn: 'exclude', obj: { a: 1, b: 2 }, yaml: 'a\n' },
    { id: 'exclude_multidoc_ignored', fn: 'exclude', obj: { a: 1, b: 2 }, yaml: 'a: 1\n---\nb: 2\n' },
];

const results = cases.map(c => {
    const obj = clone(c.obj);
    if (c.fn === 'merge') mergeObjectWithYaml(obj, c.yaml);
    else excludeKeysByYaml(obj, c.yaml);
    return { id: c.id, input: { obj: c.obj, yaml: c.yaml }, output: obj };
});
writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
