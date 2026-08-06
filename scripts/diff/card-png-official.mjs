#!/usr/bin/env node
// 官方 PNG 角色卡读写 → JSON fixture 生成器。
// write/read 逐字取自 character-card-parser.js；chunk 库（png-chunks-extract/
// png-chunk-text/encode/crc32）以等价实现内联（Kotlin 侧同样自实现）。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'card-png.json');

const parserSrc = readFileSync(join(officialRef, 'src', 'character-card-parser.js'), 'utf8');
const encodeSrc = readFileSync(join(officialRef, 'src', 'png', 'encode.js'), 'utf8');

function extractFunction(source, name) {
    const start = source.indexOf(`function ${name}`);
    if (start < 0) throw new Error(`not found: ${name}`);
    const parenStart = source.indexOf('(', start);
    let parenDepth = 0;
    let bodyStart = -1;
    let paramString = null;
    for (let i = parenStart; i < source.length; i++) {
        const ch = source[i];
        if (paramString) {
            if (ch === '\\') { i++; continue; }
            if (ch === paramString) paramString = null;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === '`') { paramString = ch; continue; }
        if (ch === '(') parenDepth++;
        else if (ch === ')') {
            parenDepth--;
            if (parenDepth === 0) {
                let j = i + 1;
                while (j < source.length && /\s/.test(source[j])) j++;
                if (source[j] === '{') bodyStart = j;
                break;
            }
        }
    }
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return source.slice(start, scanBody(source, bodyStart) + 1);
}

function extractArrow(source, name) {
    const start = source.indexOf(`const ${name} = (`);
    if (start < 0) throw new Error(`not found: const ${name}`);
    const arrow = source.indexOf('=>', start);
    const bodyStart = source.indexOf('{', arrow);
    if (bodyStart < 0) throw new Error(`no body: ${name}`);
    return source.slice(start, scanBody(source, bodyStart) + 1);
}

function scanBody(source, bodyStart) {
    let depth = 0;
    let inString = null;
    let inRegex = false;
    let inLineComment = false;
    let inBlockComment = false;
    let prevSignificant = '';
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) {
            if (ch === '\n') inLineComment = false;
            continue;
        }
        if (inBlockComment) {
            if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; }
            continue;
        }
        if (inString) {
            if (ch === '\\') { i++; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (inRegex) {
            if (ch === '\\') { i++; continue; }
            if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; }
            if (ch === '/') inRegex = false;
            continue;
        }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; i++; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; i++; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; prevSignificant = ch; continue; }
        if (ch === '/' && !/[A-Za-z0-9_)\]}"']/.test(prevSignificant)) { inRegex = true; continue; }
        if (/\s/.test(ch)) continue;
        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) return i;
        }
        prevSignificant = ch;
    }
    throw new Error('unbalanced body');
}

const write = extractArrow(parserSrc, 'write');
const read = extractArrow(parserSrc, 'read');
const encode = extractFunction(encodeSrc, 'encode');

const libStubs = `
// png-chunks-extract 等价实现
function pngChunksExtract(buffer) {
    const chunks = [];
    let pos = 8;
    const u8 = new Uint8Array(buffer);
    while (pos < u8.length) {
        const length = (u8[pos] << 24) | (u8[pos + 1] << 16) | (u8[pos + 2] << 8) | u8[pos + 3];
        const name = String.fromCharCode(u8[pos + 4], u8[pos + 5], u8[pos + 6], u8[pos + 7]);
        const data = u8.slice(pos + 8, pos + 8 + length);
        chunks.push({ name, data });
        pos += 12 + length;
    }
    return chunks;
}

// png-chunk-text 等价实现
const PNGtext = {
    encode(keyword, text) {
        const kw = Buffer.from(keyword, 'latin1');
        const tx = Buffer.from(text, 'latin1');
        return { name: 'tEXt', data: Buffer.concat([kw, Buffer.from([0]), tx]) };
    },
    decode(data) {
        const buf = Buffer.from(data);
        const sep = buf.indexOf(0);
        return { keyword: buf.slice(0, sep).toString('latin1'), text: buf.slice(sep + 1).toString('latin1') };
    },
};

// crc 包等价实现（CRC-32）
let crcTable = null;
function crc32(buf, previous = 0) {
    if (!crcTable) {
        crcTable = new Int32Array(256);
        for (let n = 0; n < 256; n++) {
            let c = n;
            for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            crcTable[n] = c;
        }
    }
    let crc = (previous ^ -1) >>> 0;
    for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ crcTable[(crc ^ buf[i]) & 0xff];
    return (crc ^ -1) >>> 0;
}

${encode}
`;

const stub = libStubs + `
const extract = pngChunksExtract;
${write}
${read}
`;

const v2Json = '{"name":"测试","description":"旧卡","spec":"chara_card_v2"}';
const v3Json = '{"name":"测试","description":"新卡","data":{"extensions":{"fav":true}}}';

const moduleText = stub + `
function makeMinimalPng() {
    const ihdr = new Uint8Array(13);
    const idat = new Uint8Array([1, 2, 3]);
    return Buffer.from(encode([
        { name: 'IHDR', data: ihdr },
        { name: 'IDAT', data: idat },
        { name: 'IEND', data: new Uint8Array(0) },
    ]));
}
function makeWithOldChunks(minimalPng) {
    const chunks = pngChunksExtract(minimalPng);
    chunks.splice(-1, 0, PNGtext.encode('chara', Buffer.from('old-v2', 'utf8').toString('base64')));
    chunks.splice(-1, 0, PNGtext.encode('ccv3', Buffer.from('old-v3', 'utf8').toString('base64')));
    return Buffer.from(encode(chunks));
}
function makeCharaOnlyPng(minimalPng, json) {
    const chunks = pngChunksExtract(minimalPng);
    chunks.splice(-1, 0, PNGtext.encode('chara', Buffer.from(json, 'utf8').toString('base64')));
    return Buffer.from(encode(chunks));
}

const minimalPng = makeMinimalPng();
const withOldChunks = makeWithOldChunks(minimalPng);
const charaOnlyPng = makeCharaOnlyPng(minimalPng, ${JSON.stringify(v2Json)});
const v3JsonConst = ${JSON.stringify(v3Json)};
const roundtripPng = (() => {
    const chunks = pngChunksExtract(minimalPng);
    const base64 = Buffer.from(v3JsonConst, 'utf8').toString('base64');
    chunks.splice(-1, 0, PNGtext.encode('chara', base64));
    const v3 = JSON.parse(v3JsonConst);
    v3.spec = 'chara_card_v3';
    v3.spec_version = '3.0';
    chunks.splice(-1, 0, PNGtext.encode('ccv3', Buffer.from(JSON.stringify(v3), 'utf8').toString('base64')));
    return Buffer.from(encode(chunks));
})();

const __cases = [
    { id: 'write_v3_card', fn: 'write', inputPng: minimalPng.toString('base64'), data: v3JsonConst },
    { id: 'write_removes_old_chunks', fn: 'write', inputPng: withOldChunks.toString('base64'), data: v3JsonConst },
    { id: 'write_non_json_only_chara', fn: 'write', inputPng: minimalPng.toString('base64'), data: 'not-json' },
    { id: 'read_v3_precedence', fn: 'read', inputPng: withOldChunks.toString('base64') },
    { id: 'read_chara_only', fn: 'read', inputPng: charaOnlyPng.toString('base64') },
    { id: 'roundtrip', fn: 'read', inputPng: roundtripPng.toString('base64') },
];
const __out = [];
for (const c of __cases) {
    const image = Buffer.from(c.inputPng, 'base64');
    if (c.fn === 'write') {
        const out = write(image, c.data);
        __out.push({ id: c.id, fn: c.fn, data: c.data ?? '', inputPng: c.inputPng, expected: Buffer.from(out).toString('base64') });
    } else {
        __out.push({ id: c.id, fn: c.fn, inputPng: c.inputPng, expected: read(image) });
    }
}
return __out;
`;

const runner = new Function('module', 'exports', 'require', moduleText);
const results = runner({}, {});

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases: results }, null, 2) + '\n');
console.log(`wrote ${outFile} (${results.length} cases)`);
