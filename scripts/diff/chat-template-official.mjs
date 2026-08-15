/**
 * 官方 chat-templates.js 纯逻辑差分：deriveTemplatesFromChatTemplate / bindModelTemplates。
 * 提取源（SillyTavern 1.18.0 / 8172dcd）：public/scripts/chat-templates.js
 *   - hash_derivations / substr_derivations / parse_derivation（1-110 行数据逐字）
 *   - deriveTemplatesFromChatTemplate（120-138 行）
 *   - bindModelTemplates（141-196 行）
 * 打桩登记：
 *   - toastr（bindModelTemplates 仅提示用，不影响返回值/映射结果）
 *   - power_user 只投影 bind 逻辑用到的字段（context.preset / instruct.enabled / instruct.preset /
 *     context_derived / instruct_derived / model_templates_mappings / chat_template_hash）
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 官方 chat-templates.js 数据与函数（逐字） ----
const hash_derivations = {
    'e10ca381b1ccc5cf9db52e371f3b6651576caee0a630b452e2816b2d404d4b65': 'Llama 3 Instruct',
    '5816fce10444e03c2e9ee1ef8a4a1ea61ae7e69e438613f3b17b69d0426223a4': 'Llama 3 Instruct',
    '73e87b1667d87ab7d7b579107f01151b29ce7f3ccdd1018fdc397e78be76219d': 'Llama 3 Instruct',
    'e16746b40344d6c5b5265988e0328a0bf7277be86f1c335156eae07e29c82826': 'Mistral V2 & V3',
    '26a59556925c987317ce5291811ba3b7f32ec4c647c400c6cc7e3a9993007ba7': 'Mistral V2 & V3',
    'e4676cb56dffea7782fd3e2b577cfaf1e123537e6ef49b3ec7caa6c095c62272': 'Mistral V3-Tekken',
    '3c4ad5fa60dd8c7ccdf82fa4225864c903e107728fcaf859fa6052cb80c92ee9': 'Mistral V7',
    '3934d199bfe5b6fab5cba1b5f8ee475e8d5738ac315f21cb09545b4e665cc005': 'Mistral V7',
    'ecd6ae513fe103f0eb62e8ab5bfa8d0fe45c1074fa398b089c93a7e70c15cfd6': 'Gemma 2',
    '87fa45af6cdc3d6a9e4dd34a0a6848eceaa73a35dcfe976bd2946a5822a38bf3': 'Gemma 2',
    '7de1c58e208eda46e9c7f86397df37ec49883aeece39fb961e0a6b24088dd3c4': 'Gemma 2',
    '3b54f5c219ae1caa5c0bb2cdc7c001863ca6807cf888e4240e8739fa7eb9e02e': 'Command R',
    'ac7498a36a719da630e99d48e6ebc4409de85a77556c2b6159eeb735bcbd11df': 'Tulu',
    '54d400beedcd17f464e10063e0577f6f798fa896266a912d8a366f8a2fcc0bca': 'DeepSeek-V2.5',
    'b6835114b7303ddd78919a82e4d9f7d8c26ed0d7dfc36beeb12d524f6144eab1': 'DeepSeek-V2.5',
    '854b703e44ca06bdb196cc471c728d15dbab61e744fe6cdce980086b61646ed1': 'GLM-4',
    'aab20feb9bc6881f941ea649356130ffbc4943b3c2577c0991e1fba90de5a0fc': 'Moonshot AI',
    '70da0d2348e40aaf8dad05f04a316835fd10547bd7e3392ce337e4c79ba91c01': 'OpenAI Harmony',
    'a4c9919cbbd4acdd51ccffe22da049264b1b73e59055fa58811a99efbd7c8146': 'OpenAI Harmony',
};

const substr_derivations = [
    ['Moonshot AI', ['<|im_user|>user<|im_middle|>', '<|im_assistant|>assistant<|im_middle|>', '<|im_end|>']],
    ['OpenAI Harmony', ['<|start|>user<|message|>', '<|start|>assistant<|channel|>final<|message|>', '<|end|>']],
    ['ChatML', ['<|im_start|>user', '<|im_start|>assistant', '<|im_end|>']],
];

const parse_derivation = derivation => (typeof derivation === 'string') ? {
    'context': derivation,
    'instruct': derivation,
} : derivation;

const not_found = { context: null, instruct: null };

async function deriveTemplatesFromChatTemplate(chat_template, hash) {
    if (chat_template.trim() === '') {
        console.log('Missing chat template.');
        return not_found;
    }

    if (hash in hash_derivations) {
        return parse_derivation(hash_derivations[hash]);
    }

    for (const [derivation, substr] of substr_derivations) {
        if ([substr].flat().every(str => chat_template.includes(str))) {
            return parse_derivation(derivation);
        }
    }

    console.warn(`Unknown chat template hash: ${hash} for [${chat_template}]`);
    return not_found;
}

async function bindModelTemplates(power_user, online_status) {
    if (online_status === 'no_connection') {
        return false;
    }

    const chatTemplateHash = power_user.chat_template_hash;
    const bindModelTemplates = power_user.model_templates_mappings[online_status]
        ?? power_user.model_templates_mappings[chatTemplateHash]
        ?? {};
    const bindingsMatch = bindModelTemplates
        && power_user.context.preset == bindModelTemplates.context
        && (!power_user.instruct.enabled || power_user.instruct.preset === bindModelTemplates.instruct);

    const bound = [];

    if (bindingsMatch) {
        delete power_user.model_templates_mappings[chatTemplateHash];
        delete power_user.model_templates_mappings[online_status];
    } else {
        if (power_user.context_derived) {
            if (power_user.context.preset !== bindModelTemplates.context) {
                bound.push(`${power_user.context.preset} context preset`);
                bindModelTemplates.context = power_user.context.preset;
            }
        }
        if (power_user.instruct.enabled) {
            if (power_user.instruct_derived) {
                if (power_user.instruct.preset !== bindModelTemplates.instruct) {
                    bound.push(`${power_user.instruct.preset} instruct preset`);
                    bindModelTemplates.instruct = power_user.instruct.preset;
                }
            }
        }
        if (bound.length == 0) {
            return false;
        }

        if (!online_status.startsWith('koboldcpp/ggml-model-')) {
            power_user.model_templates_mappings[online_status] = bindModelTemplates;
        }
        if (chatTemplateHash !== '') {
            power_user.model_templates_mappings[chatTemplateHash] = bindModelTemplates;
        }
    }

    return true;
}

// ---- 用例 ----
const cases = [];

// deriveTemplatesFromChatTemplate
const deriveCases = [
    ['', 'anything'],
    ['   \n  ', 'x'],
    ['<|im_start|>user', 'unknown-hash'],
    ['<|im_start|>user\n<|im_start|>assistant\n<|im_end|>', 'unknown-hash'],
    ['a <|im_user|>user<|im_middle|> b <|im_assistant|>assistant<|im_middle|> c <|im_end|>', 'unknown-hash'],
    ['<|start|>user<|message|> <|start|>assistant<|channel|>final<|message|> <|end|>', 'unknown-hash'],
    ['partial <|im_start|>user only', 'unknown-hash'],
    ['anything', 'e10ca381b1ccc5cf9db52e371f3b6651576caee0a630b452e2816b2d404d4b65'],
    ['', 'e10ca381b1ccc5cf9db52e371f3b6651576caee0a630b452e2816b2d404d4b65'],
    ['<|im_start|>user', 'a4c9919cbbd4acdd51ccffe22da049264b1b73e59055fa58811a99efbd7c8146'],
    ['text', 'not-in-map'],
];
for (const [template, hash] of deriveCases) {
    const out = await deriveTemplatesFromChatTemplate(template, hash);
    cases.push({ kind: 'derive', template, hash, expected: JSON.stringify(out) });
}

// bindModelTemplates
function pu({ contextPreset = 'Default', instructEnabled = true, instructPreset = 'Alpaca', contextDerived = true, instructDerived = true, mappings = {}, chatHash = '' } = {}) {
    return {
        context: { preset: contextPreset },
        instruct: { enabled: instructEnabled, preset: instructPreset },
        context_derived: contextDerived,
        instruct_derived: instructDerived,
        model_templates_mappings: mappings,
        chat_template_hash: chatHash,
    };
}

const bindCases = [
    { status: 'no_connection' },
    { status: 'model-a', contextDerived: false, instructDerived: true },
    { status: 'model-a', contextDerived: true, instructDerived: false },
    { status: 'model-a', contextDerived: false, instructDerived: false },
    { status: 'model-a', instructEnabled: false, contextDerived: true },
    { status: 'model-a', instructEnabled: false, contextDerived: false },
    { status: 'model-a' },
    { status: 'model-a', mappings: { 'model-a': { context: 'Default', instruct: 'Alpaca' } } },
    { status: 'model-a', mappings: { 'model-a': { context: 'Other', instruct: 'Alpaca' } } },
    { status: 'model-a', mappings: { 'model-a': { context: 'Default', instruct: 'Other' } } },
    { status: 'model-a', mappings: { 'hash1': { context: 'Default', instruct: 'Alpaca' } }, chatHash: 'hash1' },
    { status: 'koboldcpp/ggml-model-x', mappings: {}, chatHash: 'hash1' },
    { status: 'koboldcpp/ggml-model-x', contextDerived: true, instructDerived: true, mappings: {}, chatHash: '' },
    { status: 'model-a', contextPreset: 'Custom', contextDerived: true, instructDerived: true, mappings: { 'model-a': { context: 'Old' } } },
];
for (const bc of bindCases) {
    const p = pu(bc);
    const before = JSON.stringify(p);
    const result = await bindModelTemplates(p, bc.status);
    cases.push({ kind: 'bind', before, status: bc.status, expected: JSON.stringify({ result, after: p }) });
}

const out = JSON.stringify({ source: 'chat-templates.js', cases }, null, 2);
const target = join(__dirname, '..', '..', 'engine', 'src', 'test', 'resources', 'diff', 'chat-template.json');
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, out + '\n');
console.log(`wrote ${cases.length} cases -> ${target}`);
