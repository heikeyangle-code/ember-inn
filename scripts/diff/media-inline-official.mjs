#!/usr/bin/env node
// 媒体内联（openai.js Message.addImage/addVideo/addAudio 纯内容部分）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'media-inline.json');

const funcs = `
function ensureContentIsArray(content) {
    const textContent = content;
    if (!Array.isArray(content)) {
        content = [];
        if (typeof textContent === 'string') content.push({ type: 'text', text: textContent });
    }
    return content;
}
function addImage(content, image, quality) {
    const arr = ensureContentIsArray(content);
    arr.push({ type: 'image_url', image_url: { 'url': image, 'detail': quality } });
    return arr;
}
function addVideo(content, video, quality) {
    const arr = ensureContentIsArray(content);
    arr.push({ type: 'video_url', video_url: { 'url': video, 'detail': quality } });
    return arr;
}
function addAudio(content, audio) {
    const arr = ensureContentIsArray(content);
    arr.push({ type: 'audio_url', audio_url: { 'url': audio } });
    return arr;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    let content = b.content;',
    '    if (b.image) content = addImage(content, b.image, b.quality ?? "auto");',
    '    if (b.video) content = addVideo(content, b.video, b.quality ?? "auto");',
    '    if (b.audio) content = addAudio(content, b.audio);',
    '    return content;',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const argsBody = JSON.parse(JSON.stringify(body));
    const expected = await runCase()({ body });
    cases.push({ id, args: { body: argsBody }, expected });
}

await add('image-empty', { content: '', image: 'https://x/a.png' });
await add('image-text', { content: 'hello', image: 'data:image/png;base64,AAAA' });
await add('video-text', { content: 'hi', video: 'https://x/a.mp4', quality: 'low' });
await add('audio-text', { content: 'hi', audio: 'data:audio/mpeg;base64,BBBB' });
await add('all', { content: 'msg', image: 'https://x/a.png', video: 'https://x/a.mp4', audio: 'https://x/a.mp3' });
await add('already-array', { content: [{ type: 'text', text: 'x' }], image: 'https://x/a.png' });
await add('empty-no-media', { content: '' });

writeFileSync(outFile, JSON.stringify({ source: 'openai.js Message 媒体内联纯逻辑', cases }, null, 2));
console.log('media-inline:', cases.length, 'cases ->', outFile);
