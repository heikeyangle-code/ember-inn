#!/usr/bin/env node
// CharX（ZIP）角色卡导入（src/charx.js CharXParser + characters.js importFromCharX）→ JSON fixture。
// CharXParser/findZipStart/normalizeZipEntryPath/readFromV2/unsetPrivateFields/importFromCharX 逐字提取；
// yauzl 用官方同版本 JSZip（vendor/jszip-3.10.1.min.js）等价打桩，fs/写盘/时间打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { posix } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'charx-import.json');
const require = createRequire(import.meta.url);
const JSZip = require('./vendor/jszip-3.10.1.min.js');
const sanitize = require('./vendor/node_modules/sanitize-filename/index.js');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) return i; }
    }
    throw new Error('unbalanced');
}

function extractFunction(source, signature, name) {
    const start = source.indexOf(signature);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = source.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null;
    for (let i = parenStart; i < source.length; i++) {
        const ch = source[i];
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (/\s/.test(source[j])) j++; if (source[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return source.slice(start, scanBody(source, bodyStart) + 1);
}

function extractClass(source, name) {
    const start = source.indexOf(`export class ${name} {`);
    if (start < 0) throw new Error(`class not found: ${name}`);
    const brace = source.indexOf('{', start);
    return source.slice(start, scanBody(source, brace) + 1).replace('export class ', 'class ');
}

const charxSrc = readFileSync(join(officialRef, 'src', 'charx.js'), 'utf8');
const utilSrc = readFileSync(join(officialRef, 'src', 'util.js'), 'utf8');
const charsSrc = readFileSync(join(officialRef, 'src', 'endpoints', 'characters.js'), 'utf8');

const findZipStart = extractFunction(charxSrc, 'function findZipStart(buffer)', 'findZipStart');
const charXParser = extractClass(charxSrc, 'CharXParser');
const normalizeZipEntryPath = extractFunction(utilSrc, 'function normalizeZipEntryPath(entryName)', 'normalizeZipEntryPath');
const readFromV2 = extractFunction(charsSrc, 'function readFromV2(char)', 'readFromV2');
const unsetPrivateFields = extractFunction(charsSrc, 'function unsetPrivateFields(char)', 'unsetPrivateFields');
const importFromCharX = extractFunction(charsSrc, 'async function importFromCharX(uploadPath, { request }, preservedFileName)', 'importFromCharX');

const pathStub = { extname: posix.extname, posix };

const zipStubs = `
async function extractFileFromZipBuffer(archiveBuffer, fileExtension) {
    try {
        const zip = await JSZip.loadAsync(Buffer.from(archiveBuffer));
        let target = null;
        zip.forEach((relativePath, entry) => {
            if (!target && !entry.dir && relativePath.endsWith(fileExtension) && !relativePath.startsWith('__MACOSX')) target = entry;
        });
        if (!target) return null;
        return Buffer.from(await target.async('arraybuffer'));
    } catch {
        return null;
    }
}

async function extractFilesFromZipBuffer(archiveBuffer, fileNames) {
    const targets = new Map();
    if (Array.isArray(fileNames)) {
        for (const fileName of fileNames) {
            const normalized = normalizeZipEntryPath(fileName);
            if (normalized && !targets.has(normalized)) targets.set(normalized, true);
        }
    }
    if (targets.size === 0) return new Map();
    try {
        const zip = await JSZip.loadAsync(Buffer.from(archiveBuffer));
        const results = new Map();
        for (const target of targets.keys()) {
            const entry = zip.file(target);
            if (entry) results.set(target, Buffer.from(await entry.async('arraybuffer')));
        }
        return results;
    } catch {
        return new Map();
    }
}
`;

const lodashStub = `
const _ = {
    isUndefined: v => v === undefined,
    get: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (const k of p) { if (cur == null) return undefined; cur = cur[k]; } return cur; },
    set: (obj, path, value) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur[p[i]] == null || typeof cur[p[i]] !== 'object') cur[p[i]] = {}; cur = cur[p[i]]; } cur[p[p.length - 1]] = value; },
    unset: (obj, path) => { const p = String(path).split('.'); let cur = obj; for (let i = 0; i < p.length - 1; i++) { if (cur == null) return; cur = cur[p[i]]; } if (cur != null) delete cur[p[p.length - 1]]; },
    forEach: (obj, fn) => { for (const k in obj) fn(obj[k], k); },
};
`;

const miscStub = `
const DEFAULT_AVATAR_PATH = 'avatar';
const console = { info: () => {}, warn: () => {}, error: () => {} };
const fs = {
    readFileSync: () => request.body.zipBuffer,
    unlinkSync: () => {},
};
const getPngName = (name, directories) => name;
const persistCharXAssets = (assets, bufferMap, directories, characterFolder) => {
    const summary = {
        sprites: assets.filter(a => a.storageCategory === 'sprite').length,
        backgrounds: assets.filter(a => a.storageCategory === 'background').length,
        misc: assets.filter(a => a.storageCategory === 'misc').length,
    };
    request.body.persistSummary = summary;
    request.body.persistFolder = characterFolder;
    return summary;
};
const writeCharacterData = async (avatarPath, data, fileName, request) => {
    request.body.resultChar = JSON.parse(data);
    request.body.resultAvatar = Buffer.isBuffer(avatarPath) ? 'buffer:' + avatarPath.toString('base64') : avatarPath;
    request.body.resultFileName = fileName;
    return true;
};
const humanizedDateTime = () => request.body.fixedHuman ?? '2026-08-08@00h00m00s000ms';
Date.prototype.toISOString = () => request.body.fixedISO ?? '2026-08-08T00:00:00.000Z';
`;

const charxConstants = `
const ZIP_SIGNATURE = Buffer.from([0x50, 0x4B, 0x03, 0x04]);
const CHARX_EMBEDDED_URI_PREFIXES = ['embeded://', 'embedded://', '__asset:'];
const CHARX_IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif', 'apng', 'avif', 'bmp', 'jfif']);
const CHARX_SPRITE_TYPES = new Set(['emotion', 'expression']);
const CHARX_BACKGROUND_TYPES = new Set(['background']);
`;

const fn = [
    charxConstants,
    findZipStart,
    zipStubs,
    normalizeZipEntryPath,
    charXParser,
    readFromV2,
    unsetPrivateFields,
    importFromCharX,
].join('\n');

const runCase = new Function('request', 'JSZip', 'sanitize', 'pathStub', [
    'const path = pathStub;',
    miscStub,
    lodashStub,
    fn,
    'return (async () => {',
    '    const zipBuffer = Buffer.from(request.body.zipBase64, \'base64\');',
    '    if (request.body.sfxPrefix) { request.body.zipBuffer = Buffer.concat([Buffer.from(request.body.sfxPrefix, \'utf8\'), zipBuffer]); } else { request.body.zipBuffer = zipBuffer; }',
    '    request.user = { directories: {} };',
    '    const fileName = await importFromCharX(\'upload.charx\', { request }, request.body.preservedFileName ?? null);',
    '    const parser = new CharXParser(request.body.zipBuffer);',
    '    const parsedCard = JSON.parse((await extractFileFromZipBuffer(request.body.zipBuffer, \'card.json\')).toString());',
    '    const collected = parser.collectCharXAssets(parsedCard);',
    '    const icon = parser.pickCharXIconAsset(collected);',
    '    const mapped = parser.mapCharXAssetsForStorage(collected);',
    '    request.body.parsedIcon = icon ? { type: icon.type, name: icon.name, ext: icon.ext, zipPath: icon.zipPath, order: icon.order } : null;',
    '    request.body.parsedAssets = mapped.map(a => ({ type: a.type, name: a.name, ext: a.ext, zipPath: a.zipPath, order: a.order, storageCategory: a.storageCategory, baseName: a.baseName }));',
    '    return {',
    '        resultChar: request.body.resultChar,',
    '        resultAvatar: request.body.resultAvatar,',
    '        resultFileName: fileName,',
    '        persistSummary: request.body.persistSummary ?? null,',
    '        persistFolder: request.body.persistFolder ?? null,',
    '        parsedIcon: request.body.parsedIcon,',
    '        parsedAssets: request.body.parsedAssets,',
    '    };',
    '})();',
].join('\n'));

async function buildZip(card, files = {}) {
    const zip = new JSZip();
    zip.file('card.json', typeof card === 'string' ? card : JSON.stringify(card));
    for (const [name, data] of Object.entries(files)) {
        zip.file(name, Buffer.from(data, 'base64'));
    }
    return (await zip.generateAsync({ type: 'nodebuffer' })).toString('base64');
}

const cases = [];
async function add(id, card, files = {}, extra = {}) {
    const zipBase64 = await buildZip(card, files);
    const result = await runCase({ body: { zipBase64, fixedISO: '2026-08-08T00:00:00.000Z', fixedHuman: '2026-08-08@00h00m00s000ms', ...extra } }, JSZip, sanitize, pathStub);
    cases.push({ id, args: { body: { zipBase64, ...extra } }, expected: result });
}

await add('v3-basic', {
    spec: 'chara_card_v3',
    spec_version: '3.0',
    data: {
        name: '测试/角色:名',
        description: '描述',
        extensions: { fav: true, talkativeness: 0.7 },
    },
    chat: ['旧会话'],
    json_data: 'x',
});

await add('v2-legacy', {
    spec: 'chara_card_v2',
    name: '旧卡名',
    description: '旧描述',
    personality: '性格',
    scenario: '场景',
    first_mes: '你好',
    mes_example: '示例',
    chat: '会话',
    fav: true,
    talkativeness: 0.8,
    creator: '作者',
    tags: ['a', 'b'],
});

await add('with-assets', {
    spec: 'chara_card_v3',
    data: {
        name: '资源卡',
        description: '带资源',
        extensions: { fav: false },
        assets: [
            { type: 'icon', name: 'main.png', ext: 'png', uri: 'embeded://icons/main.png' },
            { type: 'emotion', name: 'happy.png', ext: 'png', uri: 'embedded://sprites/happy.png' },
            { type: 'background', name: 'bg', ext: 'jpg', uri: '__asset:bg/room.jpg' },
            { type: 'expression', name: 'sad.png', ext: 'webp', uri: 'embeded://sprites/sad.webp' },
            { type: 'misc', name: 'note.txt', ext: 'txt', uri: 'embeded://misc/note.txt' },
            { type: 'emotion', name: 'missing.png', ext: 'png', uri: 'embeded://sprites/missing.png' },
        ],
    },
}, {
    'icons/main.png': Buffer.from('iVBORw0KGgo=', 'base64').toString('base64'),
    'sprites/happy.png': Buffer.from('aGVsbG8=', 'base64').toString('base64'),
    'bg/room.jpg': Buffer.from('YmFja2dyb3VuZA==', 'base64').toString('base64'),
    'sprites/sad.webp': Buffer.from('d2VicA==', 'base64').toString('base64'),
    'misc/note.txt': Buffer.from('bm90ZQ==', 'base64').toString('base64'),
});

// nested card.json needs custom zip, so add it separately
async function addNested() {
    const zip = new JSZip();
    zip.file('some/folder/card.json', JSON.stringify({ spec: 'chara_card_v3', data: { name: '嵌套卡', description: 'd' } }));
    const zipBase64 = (await zip.generateAsync({ type: 'nodebuffer' })).toString('base64');
    const result = await runCase({ body: { zipBase64, fixedISO: '2026-08-08T00:00:00.000Z' } }, JSZip, sanitize, pathStub);
    cases.push({ id: 'nested-card-json', args: { body: { zipBase64 } }, expected: result });
}
await addNested();

await add('sfx-prefix', {
    spec: 'chara_card_v3',
    data: { name: '自解压卡', description: 'd' },
}, {}, { sfxPrefix: 'SELF-EXTRACTING-HEADER!' });

writeFileSync(outFile, JSON.stringify({ source: 'src/charx.js CharXParser + characters.js importFromCharX', cases }, null, 2));
console.log('charx-import:', cases.length, 'cases ->', outFile);
