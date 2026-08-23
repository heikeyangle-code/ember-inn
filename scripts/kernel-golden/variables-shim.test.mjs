/**
 * 变量族行为金测试：st-api-shim.js §6 酒馆助手变量族在沙箱 VM 里真实执行，
 * 桥由内存 chat_metadata 存储模拟。期望值逐条对照 JS-Slash-Runner
 * src/function/variables.ts（mergeWith customizer / 多源合并顺序 / klona 语义），
 * 存储位置对照官方 variables.js（chat = chat_metadata.variables）。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const shimSource = readFileSync(join(root, 'app/src/main/assets/kernel/js/st-api-shim.js'), 'utf-8');

let pass = 0, fail = 0;
function ok(cond, name) {
    if (cond) { pass++; console.log('  ✓', name); }
    else { fail++; console.error('  ✗', name); }
}

/** 建沙箱：window=沙箱自身；AndroidKernel.post 按方法路由到内存 metadata/global 存储 */
function makeSandbox() {
    const store = { metadata: { variables: {} }, globals: {} };
    const calls = [];
    const sandbox = {
        console: { debug() {}, error() {}, trace() {}, warn() {} },
        setTimeout,
        clearTimeout,
        JSON,
        Promise,
        Date,
        Object,
        Array,
        Error,
        Number,
        String,
        Boolean,
        isNaN,
    };
    sandbox.window = sandbox;
    sandbox.globalThis = sandbox;
    sandbox.AndroidKernel = {
        postMessage(json) {
            const req = JSON.parse(json);
            const params = req.params ? JSON.parse(req.params) : null;
            calls.push({ method: req.method, params });
            let response;
            if (req.method === 'metadata.get') {
                response = { ok: true, value: JSON.parse(JSON.stringify(store.metadata)) };
            } else if (req.method === 'metadata.set') {
                store.metadata = params.metadata;
                response = { ok: true };
            } else if (req.method === 'variables.get') {
                response = { ok: true, value: JSON.parse(JSON.stringify(store.globals)) };
            } else if (req.method === 'variables.set') {
                store.globals = params.variables;
                response = { ok: true };
            } else if (req.method === 'slash.run') {
                response = { ok: true, value: '' };
            } else if (req.method === 'macro.substitute') {
                response = { ok: true, value: req.params.text };
            } else {
                response = { ok: false, error: 'unsupported method: ' + req.method };
            }
            const encoded = encodeURIComponent(JSON.stringify(response));
            sandbox.__shimRespond(req.reqId, encoded);
        },
    };
    vm.createContext(sandbox);
    vm.runInContext(shimSource, sandbox, { filename: 'st-api-shim.js' });
    return { sandbox, store, calls, vm };
}

const t = makeSandbox();
{
    console.log('getVariables:');
    t.store.metadata.variables = { gold: 100, deep: { a: 1 } };
    // 官方/酒馆助手：chat = _.get(chat_metadata,'variables',{})，返回克隆
    const vars = await t.sandbox.getVariables({ type: 'chat' });
    ok(vars.gold === 100 && vars.deep.a === 1, '读取 chat_metadata.variables');
    ok(t.calls[0].method === 'metadata.get', '经 metadata.get 桥');
    vars.gold = 999;
    ok(t.store.metadata.variables.gold === 100, '返回克隆（改结果不污染存储）');
    const def = await t.sandbox.getVariables(); // 酒馆助手默认 {type:'chat'}
    ok(def.gold === 100, '默认参数即 chat 作用域');
    const empty = await makeSandbox().sandbox.getVariables({});
    ok(empty && typeof empty === 'object' && Object.keys(empty).length === 0, '无变量时回 {}（_.get 默认值）');
}

{
    console.log('replaceVariables:');
    const s = makeSandbox();
    await s.sandbox.replaceVariables({ hp: 5, items: ['剑'] });
    ok(s.store.metadata.variables.hp === 5, '整表替换落盘（chat_metadata.variables 直写）');
    ok(Array.isArray(s.store.metadata.variables.items), '数组原样保存');
    const back = await s.sandbox.getVariables({});
    ok(back.hp === 5, '读回一致（round-trip）');
    // global 作用域已接（variables.get/set 桥 + GlobalVariableStore），专组用例见「global 作用域」
    await s.sandbox.replaceVariables({}, { type: 'global' });
    ok(s.store.globals && !Array.isArray(s.store.globals), 'global replace 走独立桥不落 chat 存储');
}

{
    console.log('insertOrAssignVariables（覆盖式深合并，数组整体替换）:');
    const s = makeSandbox();
    s.store.metadata.variables = {
        神乐光: { 好感度: 10, 位置: '教室', 状态: ['正常', '平静'] },
        金币: 50,
    };
    const result = await s.sandbox.insertOrAssignVariables({
        神乐光: { 好感度: 20, 心情: '开心', 状态: ['惊讶'] },
        银币: 3,
    });
    const v = s.store.metadata.variables;
    ok(v.神乐光.好感度 === 20, '标量覆盖');
    ok(v.神乐光.位置 === '教室', '旧键保留（对象递归合并非替换）');
    ok(v.神乐光.心情 === '开心', '新嵌套键插入');
    ok(JSON.stringify(v.神乐光.状态) === '["惊讶"]', '数组整体替换（customizer rhs 胜出，非逐元素合并）');
    ok(v.金币 === 50 && v.银币 === 3, '顶层增删各归其位');
    ok(result.神乐光.好感度 === 20, '返回合并结果（updateVariablesWith 返回值语义）');
}

{
    console.log('insertVariables（插入不覆盖：_.mergeWith({}, vars, old)，旧叶子胜出）:');
    const s = makeSandbox();
    s.store.metadata.variables = { a: 1, b: { x: 1 }, arr: [1] };
    const result = await s.sandbox.insertVariables({ b: { y: 2 }, c: 9, arr: [2, 3] });
    const v = s.store.metadata.variables;
    ok(v.a === 1 && v.b.x === 1 && v.c === 9, '缺键补全、已有键不被覆盖');
    ok(v.b.y === 2, '对象分支仍深合并（旧叶子优先但新子键可进）');
    ok(JSON.stringify(v.arr) === '[1]', '数组同样旧值胜出（customizer 对多源依序生效）');
    ok(result.a === 1, '返回结果表');
}

{
    console.log('deleteVariable:');
    const s = makeSandbox();
    s.store.metadata.variables = { a: { b: 1, keep: 2 }, top: 3 };
    const r1 = await s.sandbox.deleteVariable('a.b');
    ok(r1.delete_occurred === true && r1.variables.a.keep === 2, '点路径删除 + 返回 {variables, delete_occurred}');
    const r2 = await s.sandbox.deleteVariable('不存在的路径');
    ok(r2.delete_occurred === false, '未命中时 delete_occurred=false');
    const r3 = await s.sandbox.deleteVariable('top');
    ok(r3.delete_occurred === true && s.store.metadata.variables.top === undefined, '顶层键删除');
}

{
    console.log('updateVariablesWith:');
    const s = makeSandbox();
    s.store.metadata.variables = { n: 1 };
    const r1 = await s.sandbox.updateVariablesWith(old => ({ n: old.n + 1 }));
    ok(r1.n === 2 && s.store.metadata.variables.n === 2, '同步 updater 读改写一体');
    const r2 = await s.sandbox.updateVariablesWith(async old => ({ n: old.n + 10 }));
    ok(r2 instanceof Promise || r2.n === 12, '异步 updater 支持（isPromise 分支）');
    await r2;
    ok(s.store.metadata.variables.n === 12, '异步结果仍落盘');
}

{
    console.log('结构守护:');
    const shim = shimSource;
    for (const fn of ['getVariables', 'replaceVariables', 'insertOrAssignVariables', 'insertVariables', 'deleteVariable', 'updateVariablesWith']) {
        ok(shim.includes(`window.${fn} = ${fn}`), `window.${fn} 全局暴露`);
    }
    ok(shim.includes('chat_metadata.variables') || shim.includes("chat\" 作用域"), 'chat 作用域存储位置注释登记');
}

{
    console.log('global 作用域（官方 extension_settings.variables.global）:');
    const s = makeSandbox();
    // 默认参数 = chat；global 显式 option 走 variables.get/set 桥
    const g = await s.sandbox.getVariables({ type: 'global' });
    ok(typeof g === 'object' && Object.keys(g).length === 0, 'global 初始为空对象');
    await s.sandbox.replaceVariables({ mvu: { hp: 100 } }, { type: 'global' });
    ok(s.store.globals.mvu?.hp === 100, 'replaceVariables(global) 经 variables.set 桥落盘');
    await s.sandbox.insertOrAssignVariables({ mvu: { mp: 50 }, extra: 1 }, { type: 'global' });
    ok(s.store.globals.mvu.hp === 100 && s.store.globals.mvu.mp === 50, 'insertOrAssign(global) 深合并不覆盖旧叶');
    await s.sandbox.insertVariables({ mvu: { hp: 999 }, onlyNew: true }, { type: 'global' });
    ok(s.store.globals.mvu.hp === 100 && s.store.globals.onlyNew === true, 'insertVariables(global) 旧值胜出');
    const got = await s.sandbox.getVariables({ type: 'global' });
    ok(got.mvu.hp === 100 && got.extra === 1, 'getVariables(global) 读回合并结果');
    const del = await s.sandbox.deleteVariable('mvu.mp', { type: 'global' });
    ok(del.delete_occurred === true && s.store.globals.mvu.mp === undefined, 'deleteVariable(global) 点路径删除');
    // chat/global 隔离
    await s.sandbox.replaceVariables({ chatOnly: true });
    ok(s.store.metadata.variables.chatOnly === true && s.store.globals.chatOnly === undefined, '默认 chat 与 global 存储互不污染');
    // 不支持的作用域仍显式报错
    let threw = false;
    try { await s.sandbox.getVariables({ type: 'message' }); } catch { threw = true; }
    ok(threw, 'message 等宿主态作用域显式抛错（边界登记）');
}

{
    console.log('Native→Web 事件下发通道（event_types 触发点位）:');
    const s = makeSandbox();
    ok(typeof s.sandbox.__emitKernelEvent === 'function', '__emitKernelEvent 接收器暴露');
    const got = [];
    s.sandbox.eventSource.on('generation_started', (type) => got.push(['started', type]));
    s.sandbox.eventSource.on('generation_ended', (n) => got.push(['ended', n]));
    // 按 Kotlin RenderKernel.emitEvent 的拼装形态执行（evaluateJavascript 收到的即该 JS 串）
    s.vm.runInContext('window.__emitKernelEvent("generation_started", "normal");', s.sandbox);
    s.vm.runInContext('window.__emitKernelEvent("generation_ended", 3);', s.sandbox);
    await new Promise((r) => setTimeout(r, 10)); // 官方 emit 是 async
    ok(got.length === 2 && got[0][1] === 'normal' && typeof got[0][1] === 'string', 'generation_started 带官方首参 type');
    ok(got[1][0] === 'ended' && got[1][1] === 3 && typeof got[1][1] === 'number', 'generation_ended 带 chat.length 数值');
}

{
    console.log('原型污染防护（对齐 lodash CVE-2020-8203 修复）:');
    const s = makeSandbox();
    s.store.metadata.variables = { keep: 1 };
    await s.sandbox.insertOrAssignVariables(JSON.parse('{"__proto__":{"polluted":"pwn"},"constructor":{"prototype":{"x":1}},"keep":2}'));
    ok(({}).polluted === undefined, '__proto__ 键不污染 Object.prototype');
    ok(s.store.metadata.variables.polluted === undefined && s.store.metadata.variables.keep === 2, '危险键跳过、正常键照常合并');
    const r = await s.sandbox.deleteVariable('__proto__.polluted');
    ok(r.delete_occurred === false, 'unsetPath 危险键段拒绝');
    const r2 = await s.sandbox.deleteVariable('keep');
    ok(r2.delete_occurred === true && s.store.metadata.variables.keep === undefined, '正常路径删除不受防护影响');
}

console.log(`\n变量族金测试: ${pass} 通过, ${fail} 失败`);
if (fail > 0) process.exit(1);
