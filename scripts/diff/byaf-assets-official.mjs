#!/usr/bin/env node
// BYAF 资源提取（byaf.js getCharacterImages/getChatBackgrounds）→ JSON fixture。
// 逐字提取官方方法体；extractFileFromZipBuffer/fsPromises/path/urlJoin 打桩，this.#data 换成 zipData。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'byaf-assets.json');

const byafSrc = readFileSync(join(officialRef, 'src', 'byaf.js'), 'utf8');

function extractMethod(name) {
    const marker = 'async ' + name + '(';
    const s = byafSrc.indexOf(marker);
    if (s < 0) throw new Error(name + ' not found');
    let i = byafSrc.indexOf('{', s);
    let depth = 0, inString = null, inLineComment = false, inBlockComment = false;
    for (; i < byafSrc.length; i++) {
        const ch = byafSrc[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && byafSrc[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && byafSrc[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && byafSrc[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return byafSrc.slice(s, i + 1); }
    }
    throw new Error(name + ' unbalanced');
}

const imagesMethod = extractMethod('getCharacterImages')
    .replace('this.#data', 'zipData')
    .replace('async getCharacterImages(character, characterPath) {', 'const getCharacterImages = async (character, characterPath) => {');
const backgroundsMethod = extractMethod('getChatBackgrounds')
    .replace('this.#data', 'zipData')
    .replace('async getChatBackgrounds(character, scenarios) {', 'const getChatBackgrounds = async (character, scenarios) => {');

const stub = [
    'const extractFileFromZipBuffer = (data, path) => data.files[path] ?? null;',
    "const fsPromises = { readFile: async () => Buffer.from('DEFAULT_AVATAR') };",
    "const DEFAULT_AVATAR_PATH = 'default.png';",
    'const path = {',
    "    dirname: (p) => p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : '',",
    "    basename: (p) => p.includes('/') ? p.slice(p.lastIndexOf('/') + 1) : p,",
    '};',
    'const urlJoin = (...args) => args.join("/").split("/").filter(Boolean).join("/");',
    'const console = { warn: () => {}, error: () => {}, log: () => {} };',
].join('\n');

const imageFn = new Function('zipData', stub + '\n' + imagesMethod + '\nreturn getCharacterImages;');
const bgFn = new Function('zipData', stub + '\n' + backgroundsMethod + '\nreturn getChatBackgrounds;');

const cases = [];
async function add(id, files, character, characterPath, scenarios) {
    const zipFiles = {};
    for (const [k, v] of Object.entries(files)) zipFiles[k] = Buffer.isBuffer(v) ? v : Buffer.from(v);
    const zipData = { files: zipFiles };
    const runImages = imageFn(zipData);
    const runBgs = bgFn(zipData);
    const images = (await runImages(character, characterPath)).map(x => ({
        filename: x.filename,
        image: x.image.toString(),
        label: x.label,
    }));
    const backgrounds = (await runBgs(character, scenarios)).map(x => ({
        name: x.name,
        data: x.data.toString(),
        paths: x.paths,
    }));
    const argsFiles = {};
    for (const [k, v] of Object.entries(files)) argsFiles[k] = (Buffer.isBuffer(v) ? v : Buffer.from(v)).toString('base64');
    cases.push({ id, args: { files: argsFiles, character, characterPath, scenarios }, expected: { images, backgrounds } });
}

const avatarA = Buffer.from('AAAA');
const avatarB = Buffer.from('BBBB');
const bgA = Buffer.from('BGAA');
const bgB = Buffer.from('BGBB');
const defaultAvatar = 'DEFAULT_AVATAR';

await add('no-images-fallback', {
    'characters/a/char.json': '{}',
}, { name: 'A' }, 'characters/a/char.json', []);

await add('basic-images', {
    'characters/a/char.json': '{}',
    'characters/a/images/avatar.png': avatarA,
    'characters/a/images/alt.png': avatarB,
}, { name: 'A', images: [
    { path: 'images/avatar.png', label: 'main' },
    { path: 'images/alt.png', label: 'alt' },
] }, 'characters/a/char.json', []);

await add('image-parent-path', {
    'characters/a/char.json': '{}',
    'images/global.png': avatarA,
}, { name: 'A', images: [{ path: '../../images/global.png', label: '' }] }, 'characters/a/char.json', []);

await add('image-missing', {
    'characters/a/char.json': '{}',
}, { name: 'A', images: [{ path: 'images/missing.png', label: 'x' }] }, 'characters/a/char.json', []);

await add('backgrounds-dedupe', {
    'bg1.png': bgA,
    'bg2.png': bgA,
    'bg3.png': bgB,
}, { name: 'Name' }, 'characters/a/char.json', [
    { backgroundImage: 'bg1.png' },
    { backgroundImage: 'bg2.png' },
    { backgroundImage: 'bg3.png' },
    {},
]);

await add('backgrounds-empty-and-missing', {
    'bg1.png': bgA,
}, { name: 'Name' }, 'characters/a/char.json', [
    { backgroundImage: 'missing.png' },
    {},
    { backgroundImage: 'bg1.png' },
]);

writeFileSync(outFile, JSON.stringify({ source: 'byaf.js getCharacterImages/getChatBackgrounds', cases }, null, 2));
console.log('byaf-assets:', cases.length, 'cases ->', outFile);
