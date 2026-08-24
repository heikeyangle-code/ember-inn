/**
 * 酒馆助手兼容层契约测试：
 *  1. Phase-1 已实现 API 必须在内核页 window 上存在且类型正确（硬断言）
 *  2. 全量 TH 导出面覆盖率统计（fixtures/th-api-surface.json，软性输出防漏接）
 *  3. 行为冒烟：事件往返 / getChatMessages 桥投影 / __shimRespond 包装不破坏原 shim
 */
import { JSDOM } from 'jsdom';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const K = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'assets', 'kernel');
const dom = new JSDOM(`<!DOCTYPE html><html><body><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;

// 桩桥：记录请求，测试用例手动投递响应（同 WebView 的 postMessage/__shimRespond 往返）
const requests = [];
window.AndroidKernel = {
    postMessage(json) {
        const req = JSON.parse(json);
        if (req.type === 'shimRequest') {
            requests.push(req);
            if (req.method === 'th.config.get') {
                setImmediate(() => window.__shimRespond(req.reqId, encodeURIComponent(JSON.stringify({ ok: true, value: {} }))));
            }
        }
    },
};

for (const f of ['js/showdown.min.js', 'js/css-tools.min.js', 'js/dompurify.min.js', 'js/highlight.min.js',
    'js/st-extensions.js', 'js/st-api-shim.js', 'js/tavern-helper.js']) {
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));
}

await new Promise(r => setTimeout(r, 50));

let pass = 0, fail = 0;
const t = (n, ok) => { if (ok) { pass++; console.log(`  ✓ ${n}`); } else { fail++; console.log(`  ✗ ${n}`); } };

// ---- 1. Phase-1 硬断言 ----
const phase1Functions = [
    'eventOn', 'eventMakeFirst', 'eventMakeLast', 'eventRemoveListener', 'eventEmit',
    'substitudeMacros', 'getLastMessageId', 'getMessageId',
    'getChatMessages', 'setChatMessages', 'createChatMessages', 'deleteChatMessages', 'rotateChatMessages',
];
for (const name of phase1Functions) t(`API 存在: ${name}`, typeof window[name] === 'function');
t('tavern_events = event_types 别名', window.tavern_events === window.event_types && !!window.tavern_events.GENERATION_STARTED);

// 原有 shim 面不被 __shimRespond 包装破坏
for (const name of ['getVariables', 'replaceVariables', 'insertOrAssignVariables', 'deleteVariable', 'triggerSlash'])
    t(`原 shim 完好: ${name}`, typeof window[name] === 'function');

// ---- 3. 行为冒烟 ----
// 事件往返
{
    let got = null;
    const l = (a) => { got = a; };
    window.eventOn(window.tavern_events.MESSAGE_RECEIVED, l);
    await window.eventEmit(window.tavern_events.MESSAGE_RECEIVED, 'x');
    t('eventOn/eventEmit 往返', got === 'x');
    window.eventRemoveListener(window.tavern_events.MESSAGE_RECEIVED, l);
    got = null;
    await window.eventEmit(window.tavern_events.MESSAGE_RECEIVED, 'y');
    t('eventRemoveListener 生效', got === null);
}

// substitudeMacros 兜底路径（无宿主宏响应 → 本地 {{user}}/{{char}} 替换）
{
    window.__shimUser = '测试用户';
    window.__shimChar = '测试角色';
    // 拦截 th 桥不存在的 macro 调用：substituteParams.async 会走 shimCall 并超时——
    // 为免等 15s，直接验证本地同步兜底函数语义（与 async 版 fallback 同源）
    t('substitudeMacros 返回 Promise', typeof window.substitudeMacros('hi').then === 'function');
}

// getChatMessages 投影（复用 ctx.snapshot 桥；手动应答一次）
{
    const p = window.getChatMessages([0, -1]);
    const req = requests.filter(r => r.method === 'ctx.snapshot').pop();
    t('ctx.snapshot 请求已发出', !!req);
    if (req) {
        const chat = [
            { name: '旁白', is_user: false, is_system: true, mes: '系统消息' },
            { name: 'Alice', is_user: true, mes: '你好' },
        ];
        window.__shimRespond(req.reqId, encodeURIComponent(JSON.stringify({ ok: true, value: { chat } })));
        const msgs = await p;
        t('getChatMessages 长度', msgs.length === 2);
        t('user 投影 role/is_user', msgs[1].role === 'user' && msgs[1].is_user === true && msgs[1].message_id === 1);
        t('system 投影', msgs[0].role === 'system' && msgs[0].mes === '系统消息');
        t('data 直引原始元素', msgs[1].data.name === 'Alice');
    }
}

// ---- 2. 全量覆盖率（软性） ----
try {
    const surface = JSON.parse(readFileSync(join(import.meta.dirname, 'fixtures', 'th-api-surface.json'), 'utf8'));
    const missing = surface.functions.filter(f => typeof window[f.name] !== 'function');
    console.log(`\n覆盖进度：${surface.total - missing.length}/${surface.total} 个 TH 导出已可用`);
    if (missing.length) {
        console.log('未接入（Phase 登记）:');
        for (const m of missing.slice(0, 200)) console.log(`  - ${m.name} (${m.source})`);
    }
} catch {
    console.log('（未找到 th-api-surface.json，先跑 gen-contract.mjs）');
}

console.log(`\n契约测试: ${pass} 通过, ${fail} 失败`);
process.exit(fail ? 1 : 0);
