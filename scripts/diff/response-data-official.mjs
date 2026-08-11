#!/usr/bin/env node
// script.js extractMessageFromData / extractJsonFromData → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd script.js:6217/6252。
// 打桩：main_api / oai_settings.chat_completion_source 由用例传入；
// removeReasoningFromString=恒等（reasoning.js 单独差分）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'response-data.json');

const funcs = `
let main_api = 'openai';
const oai_settings = { chat_completion_source: 'openai' };
const removeReasoningFromString = (str) => str;

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

function extractMessageFromData(data, activeApi = null) {
    function getResult() {
        if (typeof data === 'string') {
            return data;
        }
        switch (activeApi ?? main_api) {
            case 'kobold':
                return data.results[0].text;
            case 'koboldhorde':
                return data.text;
            case 'textgenerationwebui':
                return data.choices?.[0]?.text ?? data.choices?.[0]?.message?.content ?? data.content ?? data.response ?? data[0]?.content ?? '';
            case 'novel':
                return data.output;
            case 'openai':
                return data?.content?.filter(p => p.type === 'text')?.map(p => p.text)?.join('\\n\\n') ?? data?.choices?.[0]?.message?.content ?? data?.choices?.[0]?.text ?? data?.text ?? data?.message?.content?.[0]?.text ?? data?.message?.tool_plan ?? '';
            default:
                return '';
        }
    }
    const result = getResult();
    return Array.isArray(result) ? result.map(x => x.text).filter(x => x).join('') : result;
}

function extractJsonFromData(data, { mainApi = null, chatCompletionSource = null, returnInvalidJson = false } = {}) {
    mainApi = mainApi ?? main_api;
    chatCompletionSource = chatCompletionSource ?? oai_settings.chat_completion_source;
    const tryParse = (value) => {
        try {
            return JSON.parse(value);
        } catch (e) {
        }
    };
    let result = {};
    switch (mainApi) {
        case 'openai': {
            const text = extractMessageFromData(data, mainApi);
            switch (chatCompletionSource) {
                case chat_completion_sources.CLAUDE:
                    result = data?.content?.find(x => x.type === 'tool_use')?.input;
                    break;
                case chat_completion_sources.PERPLEXITY:
                    result = tryParse(removeReasoningFromString(text));
                    if (!result && returnInvalidJson) {
                        return text;
                    }
                    break;
                default:
                    result = tryParse(text);
                    if (!result && returnInvalidJson) {
                        return text;
                    }
                    break;
            }
        } break;
    }
    return JSON.stringify(result ?? {});
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    main_api = b.mainApi ?? "openai";',
    '    oai_settings.chat_completion_source = b.chatCompletionSource ?? "openai";',
    '    if (b.method === "message") return extractMessageFromData(b.data, b.activeApi ?? null);',
    '    if (b.method === "json") return extractJsonFromData(b.data, {',
    '        mainApi: b.mainApi ?? null,',
    '        chatCompletionSource: b.chatCompletionSource ?? null,',
    '        returnInvalidJson: b.returnInvalidJson ?? false,',
    '    });',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

// extractMessageFromData
await add('msg-string', { method: 'message', data: 'plain text' });
await add('msg-kobold', { method: 'message', activeApi: 'kobold', data: { results: [{ text: 'kobold text' }] } });
await add('msg-koboldhorde', { method: 'message', activeApi: 'koboldhorde', data: { text: 'horde text' } });
await add('msg-textgen-choice-text', { method: 'message', activeApi: 'textgenerationwebui', data: { choices: [{ text: 'choice text' }] } });
await add('msg-textgen-choice-message', { method: 'message', activeApi: 'textgenerationwebui', data: { choices: [{ message: { content: 'choice msg' } }] } });
await add('msg-textgen-content', { method: 'message', activeApi: 'textgenerationwebui', data: { content: 'content field' } });
await add('msg-textgen-response', { method: 'message', activeApi: 'textgenerationwebui', data: { response: 'response field' } });
await add('msg-textgen-index', { method: 'message', activeApi: 'textgenerationwebui', data: [{ content: 'index field' }] });
await add('msg-textgen-missing', { method: 'message', activeApi: 'textgenerationwebui', data: {} });
await add('msg-novel', { method: 'message', activeApi: 'novel', data: { output: 'novel text' } });
await add('msg-openai-content-multi', {
    method: 'message', activeApi: 'openai',
    data: { content: [{ type: 'text', text: 'one' }, { type: 'image' }, { type: 'text', text: 'two' }] },
});
await add('msg-openai-content-empty', { method: 'message', activeApi: 'openai', data: { content: [] } });
await add('msg-openai-choice-message', { method: 'message', activeApi: 'openai', data: { choices: [{ message: { content: 'assistant' } }] } });
await add('msg-openai-choice-text', { method: 'message', activeApi: 'openai', data: { choices: [{ text: 'choice text' }] } });
await add('msg-openai-data-text', { method: 'message', activeApi: 'openai', data: { text: 'data text' } });
await add('msg-openai-message-content', { method: 'message', activeApi: 'openai', data: { message: { content: [{ text: 'part text' }] } } });
await add('msg-openai-tool-plan', { method: 'message', activeApi: 'openai', data: { message: { tool_plan: 'plan text' } } });
await add('msg-openai-missing', { method: 'message', activeApi: 'openai', data: {} });
await add('msg-default', { method: 'message', activeApi: 'unknown', data: { text: 'ignored' } });

// extractJsonFromData
await add('json-non-openai', { method: 'json', mainApi: 'kobold', data: { text: 'x' } });
await add('json-openai-object', { method: 'json', mainApi: 'openai', chatCompletionSource: 'openai', data: { choices: [{ message: { content: '{"a":1}' } }] } });
await add('json-openai-array', { method: 'json', mainApi: 'openai', chatCompletionSource: 'openai', data: { choices: [{ message: { content: '[1,2,3]' } }] } });
await add('json-openai-string', { method: 'json', mainApi: 'openai', chatCompletionSource: 'openai', data: { choices: [{ message: { content: '"hi"' } }] } });
await add('json-openai-invalid-raw', { method: 'json', mainApi: 'openai', chatCompletionSource: 'openai', data: { choices: [{ message: { content: 'not json' } }] }, returnInvalidJson: true });
await add('json-openai-invalid-default', { method: 'json', mainApi: 'openai', chatCompletionSource: 'openai', data: { choices: [{ message: { content: 'not json' } }] }, returnInvalidJson: false });
await add('json-perplexity-valid', { method: 'json', mainApi: 'openai', chatCompletionSource: 'perplexity', data: { choices: [{ message: { content: '{"ok":true}' } }] } });
await add('json-perplexity-invalid-raw', { method: 'json', mainApi: 'openai', chatCompletionSource: 'perplexity', data: { choices: [{ message: { content: 'bad' } }] }, returnInvalidJson: true });
await add('json-claude-tool-object', {
    method: 'json', mainApi: 'openai', chatCompletionSource: 'claude',
    data: { content: [{ type: 'text', text: 'x' }, { type: 'tool_use', input: { query: 'q' } }] },
});
await add('json-claude-tool-array', {
    method: 'json', mainApi: 'openai', chatCompletionSource: 'claude',
    data: { content: [{ type: 'tool_use', input: [1, 2] }] },
});
await add('json-claude-no-tool', {
    method: 'json', mainApi: 'openai', chatCompletionSource: 'claude',
    data: { content: [{ type: 'text', text: 'x' }] },
});
await add('json-groq-valid', { method: 'json', mainApi: 'openai', chatCompletionSource: 'groq', data: { choices: [{ message: { content: '{"x":2}' } }] } });

writeFileSync(outFile, JSON.stringify({ source: 'extractMessageFromData/extractJsonFromData', cases }, null, 2));
console.log('response-data:', cases.length, 'cases ->', outFile);
