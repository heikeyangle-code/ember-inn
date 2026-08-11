#!/usr/bin/env node
// getStreamingReply（openai.js:3128）+ tryParseStreamingError（openai.js:1624）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩：oai_settings/isDataURL 由用例/内置实现；toastr/checkQuota/checkModeration 用记录器替代。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'streaming-response.json');

const funcs = `
const chat_completion_sources = {
    OPENAI: 'openai', CLAUDE: 'claude', OPENROUTER: 'openrouter', AI21: 'ai21',
    MAKERSUITE: 'makersuite', VERTEXAI: 'vertexai', MISTRALAI: 'mistralai',
    CUSTOM: 'custom', COHERE: 'cohere', PERPLEXITY: 'perplexity', GROQ: 'groq',
    ELECTRONHUB: 'electronhub', CHUTES: 'chutes', NANOGPT: 'nanogpt',
    DEEPSEEK: 'deepseek', AIMLAPI: 'aimlapi', XAI: 'xai', POLLINATIONS: 'pollinations',
    MOONSHOT: 'moonshot', FIREWORKS: 'fireworks', COMETAPI: 'cometapi',
    AZURE_OPENAI: 'azure_openai', ZAI: 'zai', SILICONFLOW: 'siliconflow',
    WORKERS_AI: 'workers_ai', MINIMAX: 'minimax',
};
let oai_settings = { chat_completion_source: 'openai', show_thoughts: true };
const isDataURL = (url) => typeof url === 'string' && url.startsWith('data:');

function getStreamingReply(data, state, { chatCompletionSource = null, overrideShowThoughts = null } = {}) {
    const chat_completion_source = chatCompletionSource ?? oai_settings.chat_completion_source;
    const show_thoughts = overrideShowThoughts ?? oai_settings.show_thoughts;
    if (chat_completion_source === chat_completion_sources.CLAUDE) {
        if (show_thoughts) {
            state.reasoning += data?.delta?.thinking || '';
        }
        return data?.delta?.text || '';
    } else if ([chat_completion_sources.MAKERSUITE, chat_completion_sources.VERTEXAI].includes(chat_completion_source)) {
        const inlineData = data?.candidates?.[0]?.content?.parts?.filter(x => x.inlineData && !x.thought)?.map(x => x.inlineData) || [];
        if (Array.isArray(inlineData) && inlineData.length > 0) {
            state.images.push(...inlineData.map(x => \`data:\${x.mimeType};base64,\${x.data}\`).filter(isDataURL));
        }
        if (show_thoughts) {
            state.reasoning += (data?.candidates?.[0]?.content?.parts?.filter(x => x.thought)?.map(x => x.text)?.[0] || '');
        }
        const parts = data?.candidates?.[0]?.content?.parts || [];
        parts.forEach((part) => {
            if (part.thoughtSignature && typeof part.text === 'string') {
                state.signature = part.thoughtSignature;
            }
        });
        return data?.candidates?.[0]?.content?.parts?.filter(x => !x.thought)?.map(x => x.text)?.[0] || '';
    } else if (chat_completion_source === chat_completion_sources.COHERE) {
        return data?.delta?.message?.content?.text || data?.delta?.message?.tool_plan || '';
    } else if (chat_completion_source === chat_completion_sources.DEEPSEEK) {
        if (show_thoughts) {
            state.reasoning += (data.choices?.filter(x => x?.delta?.reasoning_content)?.[0]?.delta?.reasoning_content || '');
        }
        return data.choices?.[0]?.delta?.content || '';
    } else if (chat_completion_source === chat_completion_sources.XAI) {
        if (show_thoughts) {
            state.reasoning += (data.choices?.filter(x => x?.delta?.reasoning_content)?.[0]?.delta?.reasoning_content || '');
        }
        return data.choices?.[0]?.delta?.content || '';
    } else if (chat_completion_source === chat_completion_sources.OPENROUTER) {
        const imageUrls = data?.choices?.[0]?.delta?.images?.filter(x => x.type === 'image_url')?.map(x => x?.image_url?.url) || [];
        if (Array.isArray(imageUrls) && imageUrls.length > 0) {
            state.images.push(...imageUrls.filter(isDataURL));
        }
        if (show_thoughts) {
            state.reasoning +=
                data.choices?.filter(x => x?.delta?.reasoning)?.[0]?.delta?.reasoning ??
                data.choices?.filter(x => x?.delta?.reasoning_content)?.[0]?.delta?.reasoning_content ??
                data.choices?.filter(x => x?.message?.reasoning)?.[0]?.message?.reasoning ??
                data.choices?.filter(x => x?.message?.reasoning_content)?.[0]?.message?.reasoning_content ??
                '';
        }
        const reasoningDetails = [
            ...(data?.choices?.[0]?.delta?.reasoning_details || []),
            ...(data?.choices?.[0]?.message?.reasoning_details || []),
        ];
        reasoningDetails.forEach((detail) => {
            if (detail.type === 'reasoning.encrypted' && detail.data) {
                const isToolLikeId = typeof detail.id === 'string' && /^(tool_|call_)/.test(detail.id);
                if (typeof detail.id === 'string' && detail.id.length > 0) {
                    state.toolSignatures[detail.id] = detail.data;
                }
                if (!isToolLikeId) {
                    state.signature = detail.data;
                }
            }
        });
        return data.choices?.[0]?.delta?.content ?? data.choices?.[0]?.message?.content ?? data.choices?.[0]?.text ?? '';
    } else if ([chat_completion_sources.CUSTOM, chat_completion_sources.POLLINATIONS, chat_completion_sources.AIMLAPI, chat_completion_sources.MOONSHOT, chat_completion_sources.COMETAPI, chat_completion_sources.ELECTRONHUB, chat_completion_sources.NANOGPT, chat_completion_sources.ZAI, chat_completion_sources.SILICONFLOW, chat_completion_sources.CHUTES, chat_completion_sources.WORKERS_AI].includes(chat_completion_source)) {
        if (show_thoughts) {
            state.reasoning +=
                data.choices?.filter(x => x?.delta?.reasoning_content)?.[0]?.delta?.reasoning_content ??
                data.choices?.filter(x => x?.delta?.reasoning)?.[0]?.delta?.reasoning ??
                '';
        }
        return data.choices?.[0]?.delta?.content ?? data.choices?.[0]?.message?.content ?? data.choices?.[0]?.text ?? '';
    } else if (chat_completion_source === chat_completion_sources.MISTRALAI) {
        if (show_thoughts) {
            state.reasoning += (data.choices?.filter(x => x?.delta?.content?.[0]?.thinking)?.[0]?.delta?.content?.[0]?.thinking?.[0]?.text || '');
        }
        const content = data.choices?.[0]?.delta?.content ?? data.choices?.[0]?.message?.content ?? data.choices?.[0]?.text ?? '';
        return Array.isArray(content) ? content.map(x => x.text).filter(x => x).join('') : content;
    } else {
        return data.choices?.[0]?.delta?.content ?? data.choices?.[0]?.message?.content ?? data.choices?.[0]?.text ?? '';
    }
}

let __quota = false;
let __moderation = false;
let __errorMessage = null;
let __message = null;
let __detail = null;
const toastr = { error: () => {}, info: () => {} };
const response = { statusText: 'Bad Request' };

function checkQuotaError(data, { quiet = false } = {}) {
    if (!data) return;
    if (data.quota_error) {
        __quota = true;
        throw new Error(data);
    }
}
function checkModerationError(data, { quiet = false } = {}) {
    const moderationError = data?.error?.message?.includes('requires moderation');
    if (moderationError && !quiet) {
        __moderation = true;
    }
}
function tryParseStreamingError(decoded, { quiet = false } = {}) {
    try {
        const data = JSON.parse(decoded);
        if (!data) return;
        checkQuotaError(data, { quiet });
        checkModerationError(data, { quiet });
        if (data.error) {
            __errorMessage = data.error.message || response.statusText;
            throw new Error(data);
        }
        if (data.message) {
            __message = data.message;
            throw new Error(data);
        }
        if (data.detail) {
            __detail = data.detail?.error?.message || response.statusText;
            throw new Error(data);
        }
    } catch {
    }
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    oai_settings.chat_completion_source = b.chatCompletionSource ?? "openai";',
    '    oai_settings.show_thoughts = b.showThoughts ?? true;',
    '    if (b.method === "stream") {',
    '        const state = { reasoning: "", images: [], signature: null, toolSignatures: {} };',
    '        const text = getStreamingReply(b.data, state, { chatCompletionSource: b.source ?? null, overrideShowThoughts: b.overrideShowThoughts ?? null });',
    '        return { text, state };',
    '    }',
    '    if (b.method === "error") {',
    '        __quota = false; __moderation = false; __errorMessage = null; __message = null; __detail = null;',
    '        tryParseStreamingError(b.decoded, { quiet: b.quiet ?? false });',
    '        return { quota: __quota, moderation: __moderation, errorMessage: __errorMessage, message: __message, detail: __detail };',
    '    }',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('stream-claude-text', { method: 'stream', source: 'claude', data: { delta: { text: 'hi' } } });
await add('stream-claude-thinking', { method: 'stream', source: 'claude', showThoughts: true, data: { delta: { thinking: 'think', text: 'hi' } } });
await add('stream-vertex-image', {
    method: 'stream', source: 'vertexai', data: {
        candidates: [{ content: { parts: [
            { text: 'answer' },
            { inlineData: { mimeType: 'image/png', data: 'AAAA' }, thought: false },
            { text: 'secret', thought: true },
        ] } }],
    },
});
await add('stream-vertex-signature', {
    method: 'stream', source: 'makersuite', data: {
        candidates: [{ content: { parts: [
            { text: 'a', thoughtSignature: 'SIG' },
        ] } }],
    },
});
await add('stream-cohere-content', { method: 'stream', source: 'cohere', data: { delta: { message: { content: { text: 'cohere' } } } } });
await add('stream-cohere-tool-plan', { method: 'stream', source: 'cohere', data: { delta: { message: { tool_plan: 'plan' } } } });
await add('stream-deepseek', { method: 'stream', source: 'deepseek', showThoughts: true, data: { choices: [{ delta: { reasoning_content: 'r', content: 'c' } }] } });
await add('stream-xai', { method: 'stream', source: 'xai', showThoughts: true, data: { choices: [{ delta: { reasoning_content: 'r', content: 'c' } }] } });
await add('stream-openrouter-reasoning', {
    method: 'stream', source: 'openrouter', showThoughts: true,
    data: { choices: [{ delta: { reasoning: 'r', content: 'c', reasoning_details: [{ id: 'main', type: 'reasoning.encrypted', data: 'SIG' }, { id: 'tool_x', type: 'reasoning.encrypted', data: 'TSIG' }] } }] },
});
await add('stream-openrouter-image', {
    method: 'stream', source: 'openrouter',
    data: { choices: [{ delta: { images: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,AA' } }], content: 'c' } }] },
});
await add('stream-custom-reasoning', { method: 'stream', source: 'custom', showThoughts: true, data: { choices: [{ delta: { reasoning_content: 'r', content: 'c' } }] } });
await add('stream-mistral-array', {
    method: 'stream', source: 'mistralai', showThoughts: true,
    data: { choices: [{ delta: { content: [{ text: 'a' }, { text: 'b' }, { text: '' }] } }] },
});
await add('stream-default', { method: 'stream', source: 'openai', data: { choices: [{ delta: { content: 'default' } }] } });
await add('error-none', { method: 'error', decoded: '{"ok":true}' });
await add('error-quota', { method: 'error', decoded: '{"quota_error":true}' });
await add('error-moderation', { method: 'error', decoded: '{"error":{"message":"requires moderation","metadata":{"reasons":["x"],"flagged_input":"f"}}}' });
await add('error-message', { method: 'error', decoded: '{"error":{"message":"boom"}}' });
await add('error-message-field', { method: 'error', decoded: '{"message":"plain"}' });
await add('error-detail', { method: 'error', decoded: '{"detail":{"error":{"message":"deep"}}}' });
await add('error-invalid-json', { method: 'error', decoded: 'not json' });

writeFileSync(outFile, JSON.stringify({ source: 'getStreamingReply/tryParseStreamingError', cases }, null, 2));
console.log('streaming-response:', cases.length, 'cases ->', outFile);
