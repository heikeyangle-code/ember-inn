#!/usr/bin/env node
// 官方工具调用循环决策（script.js generate：4436 canPerformToolCalls、
// 5351-5378 流式分支、5482-5500 非流式分支；tool-calling.js 682-688 canPerformToolCalls）→ fixture。
// 函数体照官方逐字提取；打桩登记：ToolManager.isToolCallingSupported=用例参数、
// invokeFunctionTools 的结果用 invocations/stealthCalls 数量表示、deleteLastMessage 不执行、
// Generate 递归只记录 shouldRecurse/nextDepth；shouldStopGeneration 官方可为数字 0（truthy 判定），
// 输出归一为 Boolean（打桩登记）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outFile = join(here, '..', '..', 'engine', 'src', 'test', 'resources', 'diff', 'tool-loop.json');

const funcs = `
// ToolManager.canPerformToolCalls（tool-calling.js:682-688）
function canPerformToolCalls(type, toolCallingSupported) {
    const noToolCallTypes = ['impersonate', 'quiet', 'continue'];
    const isSupported = toolCallingSupported;
    return isSupported && !noToolCallTypes.includes(type);
}

function toolLoopDecision(c) {
    const canPerform = !c.dryRun && canPerformToolCalls(c.type, c.toolCallingSupported) && c.depth < c.recurseLimit;
    let shouldDeleteMessage = false;
    let shouldStopGeneration = false;
    let shouldRecurse = false;
    let nextDepth = c.depth;

    if (c.isStreaming) {
        const isStreamFinished = c.isStreamFinished;
        const isStreamWithToolCalls = c.isStreamWithToolCalls;
        if (canPerform && isStreamFinished && isStreamWithToolCalls) {
            const lastMessage = { mes: c.lastMessageMes, extra: { reasoning: c.hasReasoning ? 'x' : undefined } };
            const hasToolCalls = c.hasToolCalls;
            shouldDeleteMessage = c.type !== 'swipe' && ['', '...'].includes(lastMessage?.mes) && !lastMessage?.extra?.reasoning && ['', '...'].includes(c.streamingResult);
            const invocationResult = { invocations: new Array(c.invocationCount), stealthCalls: new Array(c.stealthCalls ? 1 : 0) };
            shouldStopGeneration = (!invocationResult.invocations.length && shouldDeleteMessage) || invocationResult.stealthCalls.length;
            if (hasToolCalls) {
                if (!shouldStopGeneration) {
                    nextDepth = c.depth + 1;
                    shouldRecurse = true;
                }
            }
        }
    } else {
        if (canPerform) {
            const hasToolCalls = c.hasToolCalls;
            shouldDeleteMessage = c.type !== 'swipe' && ['', '...'].includes(c.lastMessageMes) && !c.hasReasoning;
            const invocationResult = { invocations: new Array(c.invocationCount), stealthCalls: new Array(c.stealthCalls ? 1 : 0) };
            shouldStopGeneration = (!invocationResult.invocations.length && shouldDeleteMessage) || invocationResult.stealthCalls.length;
            if (hasToolCalls) {
                if (!shouldStopGeneration) {
                    nextDepth = c.depth + 1;
                    shouldRecurse = true;
                }
            }
        }
    }

    return {
        canPerformToolCalls: canPerform,
        shouldDeleteMessage: shouldDeleteMessage,
        shouldStopGeneration: !!shouldStopGeneration,
        shouldRecurse: shouldRecurse,
        nextDepth: nextDepth,
    };
}
`;

const run = new Function([funcs, 'return (c) => toolLoopDecision(c);'].join('\n'));

const cases = [];
function add(id, body) {
    cases.push({ id, args: body, expected: run()(body) });
}

add('streaming-recurse', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: true, isStreamFinished: true, isStreamWithToolCalls: true, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 2, stealthCalls: false });
add('streaming-stop-empty', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: true, isStreamFinished: true, isStreamWithToolCalls: true, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 0, stealthCalls: false });
add('streaming-stop-stealth', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: true, isStreamFinished: true, isStreamWithToolCalls: true, hasToolCalls: true, lastMessageMes: 'x', hasReasoning: false, streamingResult: 'x', invocationCount: 0, stealthCalls: true });
add('streaming-not-finished', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: true, isStreamFinished: false, isStreamWithToolCalls: true, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 2, stealthCalls: false });
add('streaming-no-toolcalls', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: true, isStreamFinished: true, isStreamWithToolCalls: true, hasToolCalls: false, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 2, stealthCalls: false });
add('nonstream-recurse', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('nonstream-stop-reasoning', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: true, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('impersonate-disabled', { dryRun: false, type: 'impersonate', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('quiet-disabled', { dryRun: false, type: 'quiet', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('continue-disabled', { dryRun: false, type: 'continue', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('dryrun-disabled', { dryRun: true, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('unsupported-disabled', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: false, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('depth-limit', { dryRun: false, type: 'normal', depth: 5, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('swipe-no-delete', { dryRun: false, type: 'swipe', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '', hasReasoning: false, streamingResult: '', invocationCount: 3, stealthCalls: false });
add('ellipsis-mes-delete', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: '...', hasReasoning: false, streamingResult: '...', invocationCount: 0, stealthCalls: false });
add('normal-mes-keep', { dryRun: false, type: 'normal', depth: 0, recurseLimit: 5, toolCallingSupported: true, isStreaming: false, isStreamFinished: true, isStreamWithToolCalls: false, hasToolCalls: true, lastMessageMes: 'hello', hasReasoning: false, streamingResult: 'hello', invocationCount: 0, stealthCalls: false });

writeFileSync(outFile, JSON.stringify({ source: 'script.js 工具循环决策 + tool-calling.js canPerformToolCalls', cases }, null, 2));
console.log('tool-loop:', cases.length, 'cases ->', outFile);
