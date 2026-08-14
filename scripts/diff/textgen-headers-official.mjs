#!/usr/bin/env node
// 官方 src/additional-headers.js getMancerHeaders / getInfermaticAIHeaders / getFeatherlessHeaders → JSON fixture。
// 函数体逐字取自官方源码（readSecret 打桩、FEATHERLESS_HEADERS 取官方 constants.js）。
// 输出 engine/src/test/resources/diff/textgen-headers.json。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'textgen-headers.json');

const src = readFileSync(join(officialRef, 'src', 'additional-headers.js'), 'utf8');
const constants = readFileSync(join(officialRef, 'src', 'constants.js'), 'utf8');

// 官方 constants.js FEATHERLESS_HEADERS 逐字
const m = constants.match(/export const FEATHERLESS_HEADERS = \{\n([\s\S]*?)\n\};/);
if (!m) throw new Error('FEATHERLESS_HEADERS not found');
const FEATHERLESS_HEADERS = Function(`return {${m[1]}}`)();

const SECRET_KEYS = { MANCER: 'mancer', INFERMATICAI: 'infermaticai', FEATHERLESS: 'featherless' };
const secretStore = { mancer: 'mancer-key', infermaticai: 'infermatic-key', featherless: 'feather-key' };
function readSecret(directories, key, secretId = null) {
    return secretStore[key] ?? '';
}

// 以下三个函数体逐字取自官方 src/additional-headers.js
function getMancerHeaders(directories, secretId = null) {
    const apiKey = readSecret(directories, SECRET_KEYS.MANCER, secretId);

    return apiKey ? ({
        'X-API-KEY': apiKey,
        'Authorization': `Bearer ${apiKey}`,
    }) : {};
}

function getInfermaticAIHeaders(directories, secretId = null) {
    const apiKey = readSecret(directories, SECRET_KEYS.INFERMATICAI, secretId);

    return apiKey ? ({
        'Authorization': `Bearer ${apiKey}`,
    }) : {};
}

function getFeatherlessHeaders(directories, secretId = null) {
    const apiKey = readSecret(directories, SECRET_KEYS.FEATHERLESS, secretId);
    const baseHeaders = { ...FEATHERLESS_HEADERS };

    return apiKey ? Object.assign(baseHeaders, { 'Authorization': `Bearer ${apiKey}` }) : baseHeaders;
}

const dirs = {};
const cases = [
    { id: 'mancer_with_key', fn: getMancerHeaders, args: [dirs, null] },
    { id: 'mancer_without_key', fn: () => { secretStore.mancer = ''; try { return getMancerHeaders(dirs, null); } finally { secretStore.mancer = 'mancer-key'; } } },
    { id: 'infermaticai_with_key', fn: getInfermaticAIHeaders, args: [dirs, null] },
    { id: 'infermaticai_without_key', fn: () => { secretStore.infermaticai = ''; try { return getInfermaticAIHeaders(dirs, null); } finally { secretStore.infermaticai = 'infermatic-key'; } } },
    { id: 'featherless_with_key', fn: getFeatherlessHeaders, args: [dirs, null] },
    { id: 'featherless_without_key', fn: () => { secretStore.featherless = ''; try { return getFeatherlessHeaders(dirs, null); } finally { secretStore.featherless = 'feather-key'; } } },
];

const results = cases.map(c => ({ id: c.id, output: c.fn(...(c.args || [])) }));
writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
