#!/usr/bin/env node
// ToolManager.parseToolCalls / #applyToolCallDelta（tool-calling.js:427）→ fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩：isToolCallingSupported 由用例设置；#tools/#INPUT_DELTA_KEY 已内联。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'tool-calls.json');

const funcs = `
let __toolCallingSupported = true;
const INPUT_DELTA_KEY = '__input_json_delta';

function isToolCallingSupported() {
    return __toolCallingSupported;
}

function applyToolCallDelta(target, delta) {
    for (const key in delta) {
        if (!Object.prototype.hasOwnProperty.call(delta, key)) continue;
        if (key === '__proto__' || key === 'constructor') continue;
        const deltaValue = delta[key];
        const targetValue = target[key];
        if (deltaValue === null || deltaValue === undefined) {
            if (targetValue) {
                continue;
            }
            target[key] = deltaValue;
            continue;
        }
        if (typeof deltaValue === 'string') {
            if (typeof targetValue === 'string') {
                target[key] = targetValue + deltaValue;
            } else {
                target[key] = deltaValue;
            }
        } else if (typeof deltaValue === 'object' && !Array.isArray(deltaValue)) {
            if (typeof targetValue !== 'object' || targetValue === null || Array.isArray(targetValue)) {
                target[key] = {};
            }
            applyToolCallDelta(target[key], deltaValue);
        } else {
            target[key] = deltaValue;
        }
    }
}

function parseToolCalls(toolCalls, parsed, toolSignatures = {}) {
    if (!isToolCallingSupported()) {
        return;
    }
    if (Array.isArray(parsed?.choices)) {
        for (const choice of parsed.choices) {
            const choiceIndex = (typeof choice.index === 'number') ? choice.index : null;
            const choiceDelta = choice.delta;
            if (choiceIndex === null || !choiceDelta) {
                continue;
            }
            const toolCallDeltas = choiceDelta?.tool_calls;
            if (!Array.isArray(toolCallDeltas)) {
                continue;
            }
            if (!Array.isArray(toolCalls[choiceIndex])) {
                toolCalls[choiceIndex] = [];
            }
            for (const toolCallDelta of toolCallDeltas) {
                const toolCallIndex = toolCallDelta?.index >= 0 ? toolCallDelta.index : toolCallDeltas.indexOf(toolCallDelta);
                if (isNaN(toolCallIndex)) {
                    continue;
                }
                if (toolCalls[choiceIndex][toolCallIndex] === undefined) {
                    toolCalls[choiceIndex][toolCallIndex] = {};
                }
                const targetToolCall = toolCalls[choiceIndex][toolCallIndex];
                applyToolCallDelta(targetToolCall, toolCallDelta);
                if (Object.hasOwn(toolSignatures, targetToolCall.id)) {
                    targetToolCall.signature = toolSignatures[targetToolCall.id];
                }
            }
        }
    }
    const cohereToolEvents = ['message-start', 'tool-call-start', 'tool-call-delta', 'tool-call-end'];
    if (cohereToolEvents.includes(parsed?.type) && typeof parsed?.delta?.message === 'object') {
        const choiceIndex = 0;
        const toolCallIndex = parsed?.index ?? 0;
        if (!Array.isArray(toolCalls[choiceIndex])) {
            toolCalls[choiceIndex] = [];
        }
        if (toolCalls[choiceIndex][toolCallIndex] === undefined) {
            toolCalls[choiceIndex][toolCallIndex] = {};
        }
        const targetToolCall = toolCalls[choiceIndex][toolCallIndex];
        applyToolCallDelta(targetToolCall, parsed.delta.message);
    }
    if (typeof parsed?.content_block === 'object') {
        const choiceIndex = 0;
        const toolCallIndex = parsed?.index ?? 0;
        if (parsed?.content_block?.type === 'tool_use') {
            if (!Array.isArray(toolCalls[choiceIndex])) {
                toolCalls[choiceIndex] = [];
            }
            if (toolCalls[choiceIndex][toolCallIndex] === undefined) {
                toolCalls[choiceIndex][toolCallIndex] = {};
            }
            const targetToolCall = toolCalls[choiceIndex][toolCallIndex];
            applyToolCallDelta(targetToolCall, parsed.content_block);
        }
    }
    if (typeof parsed?.delta === 'object') {
        const choiceIndex = 0;
        const toolCallIndex = parsed?.index ?? 0;
        const targetToolCall = toolCalls[choiceIndex]?.[toolCallIndex];
        if (targetToolCall) {
            if (parsed?.delta?.type === 'input_json_delta') {
                const jsonDelta = parsed?.delta?.partial_json;
                if (!targetToolCall[INPUT_DELTA_KEY]) {
                    targetToolCall[INPUT_DELTA_KEY] = '';
                }
                targetToolCall[INPUT_DELTA_KEY] += jsonDelta;
            }
        }
    }
    if (parsed?.type === 'content_block_stop') {
        const choiceIndex = 0;
        const toolCallIndex = parsed?.index ?? 0;
        const targetToolCall = toolCalls[choiceIndex]?.[toolCallIndex];
        if (targetToolCall) {
            const jsonDeltaString = targetToolCall[INPUT_DELTA_KEY];
            if (jsonDeltaString) {
                try {
                    const jsonDelta = { input: JSON.parse(jsonDeltaString) };
                    delete targetToolCall[INPUT_DELTA_KEY];
                    applyToolCallDelta(targetToolCall, jsonDelta);
                } catch (error) {
                }
            }
        }
    }
    if (Array.isArray(parsed?.candidates)) {
        for (let choiceIndex = 0; choiceIndex < parsed.candidates.length; choiceIndex++) {
            const candidate = parsed.candidates[choiceIndex];
            if (Array.isArray(candidate?.content?.parts)) {
                for (let partIndex = 0; partIndex < candidate.content.parts.length; partIndex++) {
                    const part = candidate.content.parts[partIndex];
                    if (part.functionCall) {
                        if (!Array.isArray(toolCalls[choiceIndex])) {
                            toolCalls[choiceIndex] = [];
                        }
                        const toolCallIndex = toolCalls[choiceIndex].length;
                        if (toolCalls[choiceIndex][toolCallIndex] === undefined) {
                            toolCalls[choiceIndex][toolCallIndex] = {};
                        }
                        const targetToolCall = toolCalls[choiceIndex][toolCallIndex];
                        if (part.thoughtSignature) {
                            targetToolCall.thoughtSignature = part.thoughtSignature;
                        }
                        applyToolCallDelta(targetToolCall, part.functionCall);
                    }
                }
            }
        }
    }
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    __toolCallingSupported = b.supported ?? true;',
    '    const toolCalls = [];',
    '    const chunks = b.chunks ?? [b.parsed];',
    '    for (const chunk of chunks) {',
    '        parseToolCalls(toolCalls, chunk, b.toolSignatures ?? {});',
    '    }',
    '    return toolCalls;',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('openai-single-tool-args-concat', {
    chunks: [
        { choices: [{ index: 0, delta: { tool_calls: [{ index: 0, id: 'call_1', type: 'function', function: { name: 'weather', arguments: "{\"ci" } }] } }] },
        { choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { arguments: "ty\":\"x\"}" } }] } }] },
    ],
});
await add('openai-multi-choice', {
    chunks: [
        { choices: [
            { index: 0, delta: { tool_calls: [{ index: 0, id: 'a', function: { name: 'f1' } }] } },
            { index: 1, delta: { tool_calls: [{ index: 0, id: 'b', function: { name: 'f2' } }] } },
        ] },
    ],
});
await add('openai-signature-transfer', {
    toolSignatures: { call_1: 'SIG' },
    chunks: [{ choices: [{ index: 0, delta: { tool_calls: [{ index: 0, id: 'call_1', function: { name: 'f' } }] } }] }],
});
await add('cohere-tool-call', {
    chunks: [{ type: 'tool-call-delta', index: 0, delta: { message: { name: 'tool', arguments: '{}' } } }],
});
await add('anthropic-tool-use', {
    chunks: [{ type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 'toolu_1', name: 'search', input: {} } }],
});
await add('anthropic-input-json-delta', {
    chunks: [
        { type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 'toolu_1', name: 'search' } },
        { type: 'input_json_delta', index: 0, delta: { type: 'input_json_delta', partial_json: '{"q":"a' } },
        { type: 'input_json_delta', index: 0, delta: { type: 'input_json_delta', partial_json: 'b"}' } },
        { type: 'content_block_stop', index: 0 },
    ],
});
await add('gemini-function-call', {
    chunks: [{
        candidates: [{
            content: {
                parts: [{
                    functionCall: { name: 'g', args: { x: 1 } },
                    thoughtSignature: 'TS',
                }],
            },
        }],
    }],
});
await add('unsupported', {
    supported: false,
    chunks: [{ choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { name: 'f' } }] } }] }],
});

writeFileSync(outFile, JSON.stringify({ source: 'ToolManager.parseToolCalls', cases }, null, 2));
console.log('tool-calls:', cases.length, 'cases ->', outFile);
