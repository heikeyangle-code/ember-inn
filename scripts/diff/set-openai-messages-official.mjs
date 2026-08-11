#!/usr/bin/env node
// openai.js setOpenAIMessages（561-640）chat→messages 构造循环 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd。
// 打桩登记：IGNORE_SYMBOL 分支省略（Symbol 无法序列化，App 不使用）；getMediaDisplay=()=>'list'、
// getMediaIndex=()=>0、getChatCompletionModel=currentModel、oai_settings.names_behavior 由参数注入；
// append_title 标题拼接在官方 coreChat.map 已先完成（引擎 toOpenAiMessages 内联等价，另有 append-title 差分）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'set-openai-messages.json');

const funcs = `
const system_message_types = { NARRATOR: 'narrator' };
const character_names_behavior = { NONE: -1, DEFAULT: 0, COMPLETION: 1, CONTENT: 2 };
let oai_settings = { chat_completion_source: '', names_behavior: 0 };
let selected_group = false;
let name1 = 'User';
let name2 = 'Char';
let currentModel = '';
const getChatCompletionModel = () => currentModel;
const getMediaDisplay = () => 'list';
const getMediaIndex = () => 0;

function setOpenAIMessages(chat) {
    let j = 0;
    const messages = [];
    const currentApi = oai_settings.chat_completion_source;
    for (let i = chat.length - 1; i >= 0; i--) {
        let role = chat[j].is_user ? 'user' : 'assistant';
        let content = chat[j].mes;
        if (chat[j].extra?.type === system_message_types.NARRATOR) {
            role = 'system';
        }
        switch (oai_settings.names_behavior) {
            case character_names_behavior.NONE:
                break;
            case character_names_behavior.DEFAULT:
                if ((selected_group && chat[j].name !== name1) || (chat[j].force_avatar && chat[j].name !== name1 && chat[j].extra?.type !== system_message_types.NARRATOR)) {
                    content = \`\${chat[j].name}: \${content}\`;
                }
                break;
            case character_names_behavior.CONTENT:
                if (chat[j].extra?.type !== system_message_types.NARRATOR) {
                    content = \`\${chat[j].name}: \${content}\`;
                }
                break;
            case character_names_behavior.COMPLETION:
                break;
            default:
                break;
        }
        content = content.replace(/\\r/gm, '');
        const name = chat[j].name;
        const media = chat[j]?.extra?.media;
        const mediaDisplay = getMediaDisplay(chat[j]);
        const mediaIndex = getMediaIndex(chat[j]);
        const invocations = chat[j]?.extra?.tool_invocations?.slice();
        const originApi = chat[j]?.extra?.api;
        const originModel = chat[j]?.extra?.model;
        const isSameModel = originApi === currentApi && originModel === currentModel;
        const isOtherGroupMember = selected_group && chat[j].name !== name2;
        const signature = isSameModel && !isOtherGroupMember ? chat[j]?.extra?.reasoning_signature : null;
        const reasoning = isSameModel && !isOtherGroupMember ? String(chat[j]?.extra?.reasoning ?? '') : '';
        if (Array.isArray(invocations) && invocations.length > 0) {
            invocations.forEach((invocation, index) => {
                if (!isSameModel && (invocation.signature || invocation.reasoning)) {
                    const cloneInvocation = structuredClone(invocation);
                    delete cloneInvocation.signature;
                    delete cloneInvocation.reasoning;
                    invocations[index] = cloneInvocation;
                }
            });
        }
        messages[i] = { 'role': role, 'content': content, name: name, 'media': media, 'mediaDisplay': mediaDisplay, 'mediaIndex': mediaIndex, 'invocations': invocations, 'signature': signature, 'reasoning': reasoning };
        j++;
    }
    return messages;
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    const b = request.body;',
    '    oai_settings.chat_completion_source = b.currentApi ?? "";',
    '    oai_settings.names_behavior = b.namesBehavior ?? 0;',
    '    selected_group = b.selectedGroup ?? false;',
    '    name1 = b.name1 ?? "User";',
    '    name2 = b.name2 ?? "Char";',
    '    currentModel = b.currentModel ?? "";',
    '    return setOpenAIMessages(b.chat);',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

const chat = (items) => items.map(m => ({
    is_user: m.isUser ?? false,
    mes: m.mes ?? '',
    name: m.name,
    force_avatar: m.forceAvatar ?? false,
    extra: {
        type: m.narrator ? 'narrator' : undefined,
        api: m.api,
        model: m.model,
        reasoning_signature: m.signature,
        reasoning: m.reasoning,
        media: m.media,
        tool_invocations: m.invocations,
    },
}));

await add('basic', {
    chat: chat([
        { name: 'User', mes: '你好', isUser: true, api: 'openai', model: 'gpt-4o' },
        { name: 'Char', mes: '嗨', api: 'openai', model: 'gpt-4o' },
    ]),
    currentApi: 'openai', currentModel: 'gpt-4o',
});
await add('names-default-group', {
    chat: chat([
        { name: 'User', mes: '你好', isUser: true },
        { name: 'Alice', mes: '我是 Alice', api: 'openai', model: 'gpt-4o' },
        { name: 'Bob', mes: '我是 Bob', api: 'openai', model: 'gpt-4o' },
    ]),
    namesBehavior: 0, selectedGroup: true, currentApi: 'openai', currentModel: 'gpt-4o',
});
await add('names-content-narrator', {
    chat: chat([
        { name: 'User', mes: '旁白', isUser: true },
        { name: 'System', mes: '窗外下雨', narrator: true },
    ]),
    namesBehavior: 2,
});
await add('names-completion', {
    chat: chat([{ name: 'Char', mes: '嗨', api: 'openai', model: 'gpt-4o' }]),
    namesBehavior: 1, currentApi: 'openai', currentModel: 'gpt-4o',
});
await add('force-avatar-default', {
    chat: chat([
        { name: 'Narr', mes: '旁白', narrator: true, forceAvatar: true },
        { name: 'Alice', mes: '带头像', forceAvatar: true, api: 'openai', model: 'gpt-4o' },
    ]),
    namesBehavior: 0, currentApi: 'openai', currentModel: 'gpt-4o',
});
await add('same-model-keeps-signature', {
    chat: chat([
        { name: 'Char', mes: '思考回复', api: 'openai', model: 'gpt-4o', signature: 'sig-1', reasoning: '想法', invocations: [{ id: 'c1', name: 'tool', parameters: '{}', result: 'ok', signature: 'inv-sig', reasoning: 'inv-r' }] },
    ]),
    currentApi: 'openai', currentModel: 'gpt-4o',
});
await add('different-model-strips', {
    chat: chat([
        { name: 'Char', mes: '旧回复', api: 'openai', model: 'gpt-4o', signature: 'sig-1', reasoning: '想法', invocations: [{ id: 'c1', name: 'tool', parameters: '{}', result: 'ok', signature: 'inv-sig', reasoning: 'inv-r' }] },
    ]),
    currentApi: 'openai', currentModel: 'gpt-5',
});
await add('group-other-member-drops-signature', {
    chat: chat([
        { name: 'Alice', mes: '群聊回复', api: 'openai', model: 'gpt-4o', signature: 'sig-1', reasoning: '想法' },
    ]),
    currentApi: 'openai', currentModel: 'gpt-4o', selectedGroup: true, name2: 'Bob',
});
await add('media-passthrough', {
    chat: chat([
        { name: 'User', mes: '看图', isUser: true, media: [{ url: 'x.png' }] },
    ]),
    currentApi: 'openai', currentModel: 'gpt-4o',
});

writeFileSync(outFile, JSON.stringify({ source: 'openai.js setOpenAIMessages', cases }, null, 2));
console.log('set-openai-messages:', cases.length, 'cases ->', outFile);
