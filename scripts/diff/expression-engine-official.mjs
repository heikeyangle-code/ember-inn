#!/usr/bin/env node
// 表情系统纯逻辑（expressions/index.js getExpressionImageData/chooseSpriteForExpression +
// sprites.js 标签提取 + getSpritesList 分组）→ JSON fixture。
// 函数体逐字提取；spriteCache/extension_settings/Math.random/toastr 打桩。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'expression-engine.json');

const exprSrc = readFileSync(join(officialRef, 'public', 'scripts', 'extensions', 'expressions', 'index.js'), 'utf8');
const spritesSrc = readFileSync(join(officialRef, 'src', 'endpoints', 'sprites.js'), 'utf8');

function scanBody(source, bodyStart) {
    let depth = 0, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (ch === '/' && source[i + 1] !== '/' && source[i + 1] !== '*' &&
            (i === 0 || !/[A-Za-z0-9_$)]/.test(source[i - 1]))) {
            inRegex = true;
            continue;
        }
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

const getExpressionImageData = extractFunction(exprSrc, 'function getExpressionImageData(sprite)', 'getExpressionImageData');
const chooseSpriteForExpression = extractFunction(exprSrc, 'function chooseSpriteForExpression(spriteFolderName, expression, { prevExpressionSrc = null, overrideSpriteFile = null } = {})', 'chooseSpriteForExpression');

const spritesFunctions = `
function labelFromFilename(fileName) {
    const lower = fileName.toLowerCase();
    const match = lower.match(/^(.+?)(?:[-\\.].*?)?$/);
    return match?.[1] ?? lower;
}

function groupSprites(sprites, customLabels) {
    const grouped = sprites.reduce((acc, sprite) => {
        const imageData = getExpressionImageData(sprite);
        let existingExpression = acc.find(exp => exp.label === sprite.label);
        if (existingExpression) {
            existingExpression.files.push(imageData);
        } else {
            acc.push({ label: sprite.label, files: [imageData] });
        }
        return acc;
    }, []);
    for (const expression of grouped) {
        expression.files.sort((a, b) => {
            if (a.title === expression.label) return -1;
            if (b.title === expression.label) return 1;
            return a.title.localeCompare(b.title);
        });
        for (let i = 1; i < expression.files.length; i++) {
            expression.files[i].type = 'additional';
        }
    }
    return grouped;
}
`;

const runCase = new Function([
    "const RESET_SPRITE_LABEL = '#reset';",
    "const toastr = { warning: () => {} };",
    "const t = (s) => s;",
    "const console = { debug: () => {}, info: () => {}, warn: () => {}, error: () => {} };",
    'let spriteCache = {};',
    'let extension_settings = { expressions: {} };',
    getExpressionImageData,
    spritesFunctions,
    chooseSpriteForExpression,
    'return async (request) => {',
    '    spriteCache = request.body.spriteCache ?? {};',
    '    extension_settings = request.body.settings ?? { expressions: {} };',
    '    if (!extension_settings.expressions) extension_settings.expressions = {};',
    '    const oldRandom = Math.random;',
    '    Math.random = () => request.body.random ?? 0.5;',
    '    try {',
    '        const method = request.body.method;',
    '        if (method === "labelFromFilename") return labelFromFilename(request.body.fileName);',
    '        if (method === "imageData") return getExpressionImageData(request.body.sprite);',
    '        if (method === "groupSprites") return groupSprites(request.body.sprites, request.body.customLabels ?? []);',
    '        if (method === "choose") return chooseSpriteForExpression(request.body.folderName, request.body.expression, { prevExpressionSrc: request.body.prevSrc ?? null, overrideSpriteFile: request.body.overrideFile ?? null });',
    '        throw new Error("unknown method");',
    '    } finally { Math.random = oldRandom; }',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('label-simple', { method: 'labelFromFilename', fileName: 'joy.png' });
await add('label-suffix-dash', { method: 'labelFromFilename', fileName: 'joy-1.png' });
await add('label-suffix-dot', { method: 'labelFromFilename', fileName: 'joy.expressive.png' });
await add('label-no-ext', { method: 'labelFromFilename', fileName: 'sad' });
await add('label-unicode', { method: 'labelFromFilename', fileName: '微笑-1.png' });
await add('image-data', { method: 'imageData', sprite: { label: 'joy', path: '/characters/Alice/joy.png?t=2026' }, customLabels: [] });
await add('image-data-custom', { method: 'imageData', sprite: { label: 'cool', path: '/characters/Alice/cool-1.png' }, settings: { expressions: { custom: ['cool'] } } });
await add('group-basic', { method: 'groupSprites', sprites: [
    { label: 'joy', path: '/characters/A/joy.png' },
    { label: 'joy', path: '/characters/A/joy-2.png' },
    { label: 'joy', path: '/characters/A/joy-1.png' },
    { label: 'sad', path: '/characters/A/sad.png' },
], customLabels: [] });
await add('choose-default', {
    method: 'choose', folderName: 'A', expression: 'joy',
    spriteCache: { A: [{ label: 'joy', files: [{ fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' }] }] },
    settings: { expressions: { allowMultiple: false } },
});
await add('choose-fallback', {
    method: 'choose', folderName: 'A', expression: 'anger',
    spriteCache: { A: [{ label: 'happy', files: [{ fileName: 'happy.png', title: 'happy', imageSrc: '/characters/A/happy.png' }] }] },
    settings: { expressions: { fallback_expression: 'happy', allowMultiple: false } },
});
await add('choose-reset', {
    method: 'choose', folderName: 'A', expression: '#reset',
    spriteCache: { A: [{ label: 'joy', files: [{ fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' }] }] },
    settings: { expressions: { allowMultiple: false } },
});
await add('choose-multiple-random', {
    method: 'choose', folderName: 'A', expression: 'joy', random: 0.1,
    spriteCache: { A: [{ label: 'joy', files: [
        { fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' },
        { fileName: 'joy-2.png', title: 'joy-2', imageSrc: '/characters/A/joy-2.png' },
    ] }] },
    settings: { expressions: { allowMultiple: true, rerollIfSame: false } },
});
await add('choose-override', {
    method: 'choose', folderName: 'A', expression: 'joy', overrideFile: 'joy-2.png',
    spriteCache: { A: [{ label: 'joy', files: [
        { fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' },
        { fileName: 'joy-2.png', title: 'joy-2', imageSrc: '/characters/A/joy-2.png' },
    ] }] },
    settings: { expressions: { allowMultiple: true } },
});
await add('choose-reroll-same', {
    method: 'choose', folderName: 'A', expression: 'joy', random: 0.9, prevSrc: '/characters/A/joy-2.png',
    spriteCache: { A: [{ label: 'joy', files: [
        { fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' },
        { fileName: 'joy-2.png', title: 'joy-2', imageSrc: '/characters/A/joy-2.png' },
    ] }] },
    settings: { expressions: { allowMultiple: true, rerollIfSame: true } },
});


await add('label-uppercase', { method: 'labelFromFilename', fileName: 'JOY-1.PNG' });
await add('group-empty', { method: 'groupSprites', sprites: [], customLabels: [] });
await add('choose-no-cache', {
    method: 'choose', folderName: 'X', expression: 'joy',
    spriteCache: {}, settings: { expressions: { allowMultiple: false } },
});
await add('choose-fallback-empty', {
    method: 'choose', folderName: 'A', expression: 'anger',
    spriteCache: { A: [{ label: 'happy', files: [] }] },
    settings: { expressions: { fallback_expression: 'happy', allowMultiple: false } },
});
await add('choose-reroll-all-same', {
    method: 'choose', folderName: 'A', expression: 'joy', random: 0.5, prevSrc: '/characters/A/joy.png',
    spriteCache: { A: [{ label: 'joy', files: [{ fileName: 'joy.png', title: 'joy', imageSrc: '/characters/A/joy.png' }] }] },
    settings: { expressions: { allowMultiple: true, rerollIfSame: true } },
});

writeFileSync(outFile, JSON.stringify({ source: 'expressions/index.js + sprites.js 纯逻辑', cases }, null, 2));
console.log('expression-engine:', cases.length, 'cases ->', outFile);
