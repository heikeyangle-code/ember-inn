#!/usr/bin/env node
// 媒体内容块转换（prompt-converters.js Claude/Gemini 部分，逐字提取）→ fixture。
// 注意：与官方相同，convertClaudePart 会原地修改 part；add() 用结构化克隆保留原始 args。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'media-convert.json');

// 逐字来自官方 src/prompt-converters.js：
// - convertClaudeMessages 的数组内容块 image_url/text 分支
// - convertGooglePrompt 的 addDataUrlPart + GEMINI_MEDIA_RESOLUTION
const funcs = `
const GEMINI_MEDIA_RESOLUTION = {
    low: 'media_resolution_low',
    high: 'media_resolution_high',
};

function convertClaudePart(part, name = null) {
    if (part.type === 'image_url') {
        const imageEntry = part?.image_url;
        const imageData = imageEntry?.url;
        const mimeType = imageData?.split(';')?.[0].split(':')?.[1];
        const base64Data = imageData?.split(',')?.[1];

        return {
            type: 'image',
            source: {
                type: 'base64',
                media_type: mimeType,
                data: base64Data,
            },
        };
    }

    if (part.type === 'text') {
        if (name) part.text = name + ': ' + part.text;
        return { type: 'text', text: part.text || '\\u200b' };
    }

    return part;
}

function convertGeminiPart(part, model) {
    const addDataUrlPart = (url, defaultMimeType, detail = null) => {
        if (url && url.startsWith('data:')) {
            const [header, base64Data] = url.split(',');
            const mimeType = header.match(/data:([^;]+)/)?.[1] || defaultMimeType;
            const mediaResolution = GEMINI_MEDIA_RESOLUTION[detail] || null;

            const out = {
                inlineData: {
                    mimeType: mimeType,
                    data: base64Data,
                },
            };

            // https://ai.google.dev/gemini-api/docs/gemini-3#media_resolution
            if (/gemini-3/.test(model) && mediaResolution) {
                out.mediaResolution = {
                    level: mediaResolution,
                };
            }

            return out;
        }
        return null;
    };

    if (part.type === 'text') return { text: part.text };
    if (part.type === 'image_url') return addDataUrlPart(part.image_url?.url, 'image/png', part.image_url?.detail);
    if (part.type === 'video_url') return addDataUrlPart(part.video_url?.url, 'video/mp4', part.video_url?.detail);
    if (part.type === 'audio_url') return addDataUrlPart(part.audio_url?.url, 'audio/mpeg');
    return part;
}
`;

const runCase = new Function([
    funcs,
    'return (request) => {',
    '    const b = request.body;',
    '    if (b.target === "claude") return convertClaudePart(b.part, b.name ?? null);',
    '    if (b.target === "gemini") return convertGeminiPart(b.part, b.model ?? "");',
    '    throw new Error("unknown target");',
    '};',
].join('\n'));

const cases = [];
function add(id, body) {
    // 官方函数可能原地修改 body（claude text 加名字前缀），args 保留克隆以驱动引擎输入。
    const argsBody = JSON.parse(JSON.stringify(body));
    const expected = runCase()({ body });
    cases.push({ id, args: { body: argsBody }, expected });
}

// Claude 基础
add('claude-image', { target: 'claude', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,AAAA' } } });
add('claude-text', { target: 'claude', part: { type: 'text', text: 'hello' } });
add('claude-text-empty', { target: 'claude', part: { type: 'text', text: '' } });
add('claude-text-name', { target: 'claude', part: { type: 'text', text: 'hi' }, name: 'Alice' });
add('claude-text-name-empty', { target: 'claude', part: { type: 'text', text: 'hi' }, name: '' });
add('claude-image-remote-url', { target: 'claude', part: { type: 'image_url', image_url: { url: 'https://x/a.png' } } });
add('claude-image-no-comma', { target: 'claude', part: { type: 'image_url', image_url: { url: 'data:image/png' } } });
add('claude-image-url-missing', { target: 'claude', part: { type: 'image_url', image_url: {} } });
add('claude-unknown-part', { target: 'claude', part: { type: 'tool_use', id: 'x' } });
add('claude-video-unchanged', { target: 'claude', part: { type: 'video_url', video_url: { url: 'data:video/mp4;base64,BBBB' } } });

// Gemini 基础
add('gemini-image', { target: 'gemini', part: { type: 'image_url', image_url: { url: 'data:image/jpeg;base64,CCCC' } } });
add('gemini-video', { target: 'gemini', part: { type: 'video_url', video_url: { url: 'data:video/mp4;base64,DDDD' } } });
add('gemini-audio', { target: 'gemini', part: { type: 'audio_url', audio_url: { url: 'data:audio/mpeg;base64,EEEE' } } });
add('gemini-text', { target: 'gemini', part: { type: 'text', text: 'ok' } });
add('gemini-text-empty', { target: 'gemini', part: { type: 'text', text: '' } });
add('gemini-unknown-part', { target: 'gemini', part: { type: 'function_call', name: 'f' } });

// Gemini 分辨率（官方 media_resolution_low/high）
add('gemini-image-gemini3-high', { target: 'gemini', model: 'gemini-3-pro', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,FFFF', detail: 'high' } } });
add('gemini-image-gemini3-low', { target: 'gemini', model: 'gemini-3-pro', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,GGGG', detail: 'low' } } });
add('gemini-image-low-non3', { target: 'gemini', model: 'gemini-2.5-pro', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,HHHH', detail: 'low' } } });
add('gemini-image-gemini3-unknown-detail', { target: 'gemini', model: 'gemini-3-pro', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,IIII', detail: 'auto' } } });

// Gemini URL 解析边缘
add('gemini-image-remote-url', { target: 'gemini', part: { type: 'image_url', image_url: { url: 'https://x/a.png' } } });
add('gemini-audio-remote-url', { target: 'gemini', part: { type: 'audio_url', audio_url: { url: 'https://x/a.mp3' } } });
add('gemini-image-no-comma', { target: 'gemini', part: { type: 'image_url', image_url: { url: 'data:image/png' } } });
add('gemini-image-extra-comma', { target: 'gemini', part: { type: 'image_url', image_url: { url: 'data:image/png;base64,AAAA,BBBB' } } });
add('gemini-image-url-missing', { target: 'gemini', part: { type: 'image_url', image_url: {} } });

writeFileSync(outFile, JSON.stringify({ source: 'prompt-converters.js 媒体块转换（逐字提取）', cases }, null, 2));
console.log('media-convert:', cases.length, 'cases ->', outFile);
