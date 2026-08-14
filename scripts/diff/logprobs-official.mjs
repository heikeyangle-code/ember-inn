#!/usr/bin/env node
// 官方 Token 概率解析纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   scripts/openai.js parseOpenAIChatLogprobs / parseOpenAITextLogprobs / parseChatCompletionLogprobs
// 打桩（脚本头部登记）：chat_completion_sources 常量、textCompletionModels 为传入数组、oai_settings.chat_completion_source 直接传 source。
// 边界（不移植，登记）：AIMLAPI/OPENAI 等 source 名以外默认返回 null（官方 switch default）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'logprobs.json');

const chat_completion_sources = {
    AIMLAPI: 'aimlapi',
    OPENAI: 'openai',
    AZURE_OPENAI: 'azure_openai',
    DEEPSEEK: 'deepseek',
    XAI: 'xai',
    CUSTOM: 'custom',
    CHUTES: 'chutes',
};

function parseOpenAIChatLogprobs(logprobs) {
    const { content } = logprobs ?? {};

    if (!Array.isArray(content)) {
        return null;
    }

    const toTuple = (x) => [x.token, x.logprob];

    return content.map(({ token, logprob, top_logprobs = [] }) => {
        const chosenTopToken = top_logprobs.some((top) => token === top.token);
        const topLogprobs = chosenTopToken
            ? top_logprobs.map(toTuple)
            : [...top_logprobs.map(toTuple), [token, logprob]];
        return { token, topLogprobs };
    });
}

function parseOpenAITextLogprobs(logprobs) {
    const { tokens, token_logprobs, top_logprobs } = logprobs ?? {};

    if (!Array.isArray(tokens)) {
        return null;
    }

    return tokens.map((token, i) => {
        const topLogprobs = top_logprobs[i] ? Object.entries(top_logprobs[i]) : [];
        const chosenTopToken = topLogprobs.some(([topToken]) => token === topToken);
        if (!chosenTopToken) {
            topLogprobs.push([token, token_logprobs[i]]);
        }
        return { token, topLogprobs };
    });
}

function parseChatCompletionLogprobs(data, source, textCompletionModels) {
    if (!data) {
        return null;
    }

    switch (source) {
        case chat_completion_sources.AIMLAPI:
            return Object.keys(data?.choices?.[0]?.logprobs ?? {}).includes('content')
                ? parseOpenAIChatLogprobs(data.choices[0]?.logprobs)
                : parseOpenAITextLogprobs(data.choices[0]?.logprobs);
        case chat_completion_sources.OPENAI:
        case chat_completion_sources.AZURE_OPENAI:
        case chat_completion_sources.DEEPSEEK:
        case chat_completion_sources.XAI:
        case chat_completion_sources.CUSTOM:
        case chat_completion_sources.CHUTES:
            if (!data.choices?.length) {
                return null;
            }
            return textCompletionModels.includes('davinci')
                ? parseOpenAITextLogprobs(data.choices[0]?.logprobs)
                : parseOpenAIChatLogprobs(data.choices[0]?.logprobs);
        default:
    }
    return null;
}

const cases = [];
let id = 0;
function add(name, kind, data, source, models) {
    const expected = kind === 'chat'
        ? parseOpenAIChatLogprobs(data)
        : kind === 'text'
            ? parseOpenAITextLogprobs(data)
            : parseChatCompletionLogprobs(data, source, models);
    cases.push({
        id: String(++id).padStart(3, '0') + '-' + name,
        kind,
        args: kind === 'combined' ? { data, source, models } : { data },
        expected,
    });
}

// chat 解析
add('chat-empty-content', 'chat', { content: [] });
add('chat-missing-content', 'chat', {});
add('chat-null', 'chat', null);
add('chat-chosen-in-top', 'chat', { content: [{ token: 'a', logprob: -1, top_logprobs: [{ token: 'a', logprob: -1 }, { token: 'b', logprob: -2 }] }] });
add('chat-chosen-not-in-top', 'chat', { content: [{ token: 'a', logprob: -1, top_logprobs: [{ token: 'b', logprob: -2 }, { token: 'c', logprob: -3 }] }] });
add('chat-no-top', 'chat', { content: [{ token: 'a', logprob: -1 }] });
add('chat-empty-top', 'chat', { content: [{ token: 'a', logprob: -1, top_logprobs: [] }] });
add('chat-multi', 'chat', {
    content: [
        { token: 'Hello', logprob: -0.1, top_logprobs: [{ token: 'Hello', logprob: -0.1 }, { token: 'Hi', logprob: -1.2 }] },
        { token: ' world', logprob: -0.3, top_logprobs: [{ token: ' world', logprob: -0.3 }] },
    ],
});

// text 解析
add('text-basic', 'text', { tokens: ['a', 'b'], token_logprobs: [-0.1, -0.2], top_logprobs: [{ a: -0.1, c: -2 }, { b: -0.2, d: -3 }] });
add('text-chosen-absent', 'text', { tokens: ['x'], token_logprobs: [-0.5], top_logprobs: [{ y: -1 }] });
add('text-no-top', 'text', { tokens: ['x'], token_logprobs: [-0.5], top_logprobs: [] });
add('text-empty-tokens', 'text', { tokens: [], token_logprobs: [], top_logprobs: [] });
add('text-null', 'text', null);

// combined 分支
add('combined-aimlapi-chat', 'combined', { choices: [{ logprobs: { content: [{ token: 'a', logprob: -1 }] } }] }, 'aimlapi', []);
add('combined-aimlapi-text', 'combined', { choices: [{ logprobs: { tokens: ['a'], token_logprobs: [-1], top_logprobs: [] } }] }, 'aimlapi', []);
add('combined-openai-chat', 'combined', { choices: [{ logprobs: { content: [{ token: 'a', logprob: -1 }] } }] }, 'openai', []);
add('combined-openai-text-model', 'combined', { choices: [{ logprobs: { tokens: ['a'], token_logprobs: [-1], top_logprobs: [] } }] }, 'openai', ['davinci']);
add('combined-openai-no-choices', 'combined', {}, 'openai', []);
add('combined-null-data', 'combined', null, 'openai', []);
add('combined-unsupported-source', 'combined', { choices: [{ logprobs: { content: [] } }] }, 'mistral', []);

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`logprobs fixtures: ${cases.length} cases -> ${outFile}`);
