#!/usr/bin/env node
// 媒体附件纯逻辑（script.js getMediaDisplay/getMediaIndex + constants.js getFromMime）→ fixture。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'media-engine.json');

const funcs = `
const MEDIA_DISPLAY = { LIST: 'list', GALLERY: 'gallery' };
const MEDIA_TYPE = {
    IMAGE: 'image', VIDEO: 'video', AUDIO: 'audio',
    getFromMime: (mimeType) => {
        if (mimeType.startsWith('image/')) return MEDIA_TYPE.IMAGE;
        if (mimeType.startsWith('video/')) return MEDIA_TYPE.VIDEO;
        if (mimeType.startsWith('audio/')) return MEDIA_TYPE.AUDIO;
        return null;
    },
};

function getMediaDisplay(mes) {
    const value = mes?.extra?.media_display || mes?.power_user_media_display || MEDIA_DISPLAY.LIST;
    return Object.values(MEDIA_DISPLAY).includes(value) ? value : MEDIA_DISPLAY.LIST;
}

function getMediaIndex(mes) {
    if (!Array.isArray(mes?.extra?.media)) return 0;
    const value = mes.extra?.media_index;
    if (isNaN(value) || value < 0 || value >= mes.extra.media.length) return 0;
    return value;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    if (b.method === "display") return getMediaDisplay(b.mes);',
    '    if (b.method === "index") return getMediaIndex(b.mes);',
    '    if (b.method === "mime") return MEDIA_TYPE.getFromMime(b.mime);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('display-default', { method: 'display', mes: {} });
await add('display-gallery', { method: 'display', mes: { extra: { media_display: 'gallery' } } });
await add('display-invalid', { method: 'display', mes: { extra: { media_display: 'bogus' } } });
await add('display-power-user', { method: 'display', mes: { power_user_media_display: 'gallery' } });
await add('display-empty-string', { method: 'display', mes: { extra: { media_display: '' } } });
await add('index-no-media', { method: 'index', mes: {} });
await add('index-valid', { method: 'index', mes: { extra: { media: [1, 2, 3], media_index: 1 } } });
await add('index-string', { method: 'index', mes: { extra: { media: [1, 2], media_index: '1' } } });
await add('index-negative', { method: 'index', mes: { extra: { media: [1, 2], media_index: -1 } } });
await add('index-out-of-range', { method: 'index', mes: { extra: { media: [1, 2], media_index: 5 } } });
await add('index-nan', { method: 'index', mes: { extra: { media: [1, 2], media_index: 'abc' } } });
await add('index-null', { method: 'index', mes: { extra: { media: [1, 2], media_index: null } } });
await add('mime-image', { method: 'mime', mime: 'image/png' });
await add('mime-video', { method: 'mime', mime: 'video/mp4' });
await add('mime-audio', { method: 'mime', mime: 'audio/mpeg' });
await add('mime-text', { method: 'mime', mime: 'text/plain' });
await add('mime-uppercase', { method: 'mime', mime: 'IMAGE/png' });

writeFileSync(outFile, JSON.stringify({ source: 'media 纯逻辑', cases }, null, 2));
console.log('media-engine:', cases.length, 'cases ->', outFile);
