#!/usr/bin/env node
// getMaxContextTokens / getMaxResponseTokens / getMaxPromptTokens（script.js:5870/5907/5922）
// + getKayraMaxContextTokens（nai-settings.js:92）→ fixture。函数体逐字摘自官方 release 8172dcd。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'token-budget.json');

const funcs = `
let main_api = 'openai';
let max_context = 0;
let amount_gen = 0;
const oai_settings = { openai_max_context: 0, openai_max_tokens: 0 };
const nai_settings = { model_novel: '' };
let novel_data = null;

function getKayraMaxContextTokens() {
    switch (novel_data?.tier) {
        case 1:
            return 4096;
        case 2:
            return 8192;
        case 3:
            return 8192;
    }
    return null;
}

function getMaxContextTokens() {
    if (main_api == 'kobold' || main_api == 'koboldhorde' || main_api == 'textgenerationwebui') {
        return max_context;
    }
    if (main_api == 'novel') {
        let this_max_context = Number(max_context);
        if (nai_settings.model_novel.includes('clio')) {
            this_max_context = Math.min(max_context, 8192);
        }
        if (nai_settings.model_novel.includes('kayra')) {
            this_max_context = Math.min(max_context, 8192);
            const subscriptionLimit = getKayraMaxContextTokens();
            if (typeof subscriptionLimit === 'number' && this_max_context > subscriptionLimit) {
                this_max_context = subscriptionLimit;
            }
        }
        if (nai_settings.model_novel.includes('erato')) {
            this_max_context = Math.min(max_context, 8192);
            this_max_context -= 10;
        }
        return this_max_context;
    }
    if (main_api == 'openai') {
        return oai_settings.openai_max_context;
    }
    return 1487;
}

function getMaxResponseTokens() {
    if (main_api == 'kobold' || main_api == 'koboldhorde' || main_api == 'textgenerationwebui' || main_api == 'novel') {
        return amount_gen;
    }
    if (main_api == 'openai') {
        return oai_settings.openai_max_tokens;
    }
    return 0;
}

function getMaxPromptTokens(overrideResponseLength = null) {
    if (typeof overrideResponseLength !== 'number' || overrideResponseLength <= 0 || isNaN(overrideResponseLength)) {
        overrideResponseLength = null;
    }
    return getMaxContextTokens() - (overrideResponseLength || getMaxResponseTokens());
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    main_api = b.mainApi ?? "openai";',
    '    max_context = b.maxContext ?? 0;',
    '    amount_gen = b.amountGen ?? 0;',
    '    oai_settings.openai_max_context = b.openaiMaxContext ?? 0;',
    '    oai_settings.openai_max_tokens = b.openaiMaxTokens ?? 0;',
    '    nai_settings.model_novel = b.novelModel ?? "";',
    '    novel_data = b.novelTier != null ? { tier: b.novelTier } : null;',
    '    if (b.method === "context") return getMaxContextTokens();',
    '    if (b.method === "response") return getMaxResponseTokens();',
    '    if (b.method === "prompt") return getMaxPromptTokens(b.override);',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('context-kobold', { method: 'context', mainApi: 'kobold', maxContext: 2048 });
await add('context-textgen', { method: 'context', mainApi: 'textgenerationwebui', maxContext: 4096 });
await add('context-novel-clio', { method: 'context', mainApi: 'novel', maxContext: 12000, novelModel: 'clio-v1' });
await add('context-novel-kayra-no-tier', { method: 'context', mainApi: 'novel', maxContext: 12000, novelModel: 'kayra-v1', novelTier: null });
await add('context-novel-kayra-tier1', { method: 'context', mainApi: 'novel', maxContext: 12000, novelModel: 'kayra-v1', novelTier: 1 });
await add('context-novel-kayra-tier2', { method: 'context', mainApi: 'novel', maxContext: 12000, novelModel: 'kayra-v1', novelTier: 2 });
await add('context-novel-erato', { method: 'context', mainApi: 'novel', maxContext: 12000, novelModel: 'erato-v1' });
await add('context-openai', { method: 'context', mainApi: 'openai', openaiMaxContext: 32000 });
await add('context-unknown', { method: 'context', mainApi: 'claude' });
await add('response-kobold', { method: 'response', mainApi: 'kobold', amountGen: 250 });
await add('response-novel', { method: 'response', mainApi: 'novel', amountGen: 300 });
await add('response-openai', { method: 'response', mainApi: 'openai', openaiMaxTokens: 512 });
await add('response-unknown', { method: 'response', mainApi: 'claude' });
await add('prompt-default', { method: 'prompt', mainApi: 'openai', openaiMaxContext: 32000, openaiMaxTokens: 512 });
await add('prompt-override', { method: 'prompt', mainApi: 'openai', openaiMaxContext: 32000, openaiMaxTokens: 512, override: 1024 });
await add('prompt-override-zero', { method: 'prompt', mainApi: 'openai', openaiMaxContext: 32000, openaiMaxTokens: 512, override: 0 });
await add('prompt-override-negative', { method: 'prompt', mainApi: 'openai', openaiMaxContext: 32000, openaiMaxTokens: 512, override: -5 });

writeFileSync(outFile, JSON.stringify({ source: 'getMaxContextTokens/getMaxResponseTokens/getMaxPromptTokens', cases }, null, 2));
console.log('token-budget:', cases.length, 'cases ->', outFile);
