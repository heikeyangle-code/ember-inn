#!/usr/bin/env node
// 媒体 token 成本估算（openai.js getImageTokenCost + addVideo/addAudio 的 263/32 每秒规则）→ fixture。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'media-cost.json');

const funcs = `
const tokensPerImage = 85;
let stubImageSize = { width: 0, height: 0 };
let stubVideoDuration = 0;
let stubAudioDuration = 0;

async function getImageSizeFromDataURLStub() { return stubImageSize; }
async function getVideoDurationFromDataURLStub() {
    if (stubVideoDuration === 'throw') throw new Error('duration unavailable');
    return stubVideoDuration;
}
async function getAudioDurationFromDataURLStub() {
    if (stubAudioDuration === 'throw') throw new Error('duration unavailable');
    return stubAudioDuration;
}

async function getImageTokenCost(dataUrl, quality) {
    if (quality === 'low') {
        return tokensPerImage;
    }

    const size = await getImageSizeFromDataURLStub(dataUrl);

    if (quality === 'auto' && size.width <= 512 && size.height <= 512) {
        return tokensPerImage;
    }

    const scale = 2048 / Math.min(size.width, size.height);
    const scaledWidth = Math.round(size.width * scale);
    const scaledHeight = Math.round(size.height * scale);

    const finalScale = 768 / Math.min(scaledWidth, scaledHeight);
    const finalWidth = Math.round(scaledWidth * finalScale);
    const finalHeight = Math.round(scaledHeight * finalScale);

    const squares = Math.ceil(finalWidth / 512) * Math.ceil(finalHeight / 512);
    const tokens = squares * 170 + 85;
    return tokens;
}

async function videoTokenCost() {
    const token = { tokens: 0 };
    try {
        const duration = await getVideoDurationFromDataURLStub();
        token.tokens += 263 * Math.ceil(duration);
    } catch (error) {
        token.tokens += 263 * 40;
    }
    return token.tokens;
}

async function audioTokenCost() {
    const token = { tokens: 0 };
    try {
        const duration = await getAudioDurationFromDataURLStub();
        token.tokens += 32 * Math.ceil(duration);
    } catch (error) {
        token.tokens += 32 * 300;
    }
    return token.tokens;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    if (b.method === "image") {',
    '        stubImageSize = { width: b.width, height: b.height };',
    '        return await getImageTokenCost("data:image/png;base64,x", b.quality);',
    '    }',
    '    if (b.method === "video") {',
    '        stubVideoDuration = b.duration;',
    '        return await videoTokenCost();',
    '    }',
    '    if (b.method === "audio") {',
    '        stubAudioDuration = b.duration;',
    '        return await audioTokenCost();',
    '    }',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

// 图片：low 恒 85
await add('image-low-large', { method: 'image', width: 3000, height: 2000, quality: 'low' });
// 图片：auto 且 <=512x512 → 85
await add('image-auto-small', { method: 'image', width: 512, height: 512, quality: 'auto' });
await add('image-auto-tiny', { method: 'image', width: 200, height: 300, quality: 'auto' });
// 图片：auto 超 512
await add('image-auto-1024', { method: 'image', width: 1024, height: 768, quality: 'auto' });
await add('image-auto-2000', { method: 'image', width: 2000, height: 1000, quality: 'auto' });
// 图片：high
await add('image-high-square', { method: 'image', width: 768, height: 768, quality: 'high' });
await add('image-high-3000', { method: 'image', width: 3000, height: 2000, quality: 'high' });
await add('image-high-ultrawide', { method: 'image', width: 4096, height: 100, quality: 'high' });
await add('image-high-2000x1', { method: 'image', width: 2000, height: 1, quality: 'high' });
// 视频：263/秒，向上取整
await add('video-0s', { method: 'video', duration: 0 });
await add('video-1p2s', { method: 'video', duration: 1.2 });
await add('video-2s', { method: 'video', duration: 2 });
await add('video-40p5s', { method: 'video', duration: 40.5 });
await add('video-fallback', { method: 'video', duration: 'throw' });
// 音频：32/秒，向上取整
await add('audio-0s', { method: 'audio', duration: 0 });
await add('audio-1p5s', { method: 'audio', duration: 1.5 });
await add('audio-300s', { method: 'audio', duration: 300 });
await add('audio-fallback', { method: 'audio', duration: 'throw' });

writeFileSync(outFile, JSON.stringify({ source: 'media 成本估算（openai.js getImageTokenCost/addVideo/addAudio）', cases }, null, 2));
console.log('media-cost:', cases.length, 'cases ->', outFile);
