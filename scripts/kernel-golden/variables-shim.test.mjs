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

/** 建沙箱：window=沙箱自身；AndroidKernel.post 按方法路由到内存 metadata 存储 */
function makeSandbox() {
    const store = { metadata: { variables: {} } };
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
    return { sandbox, store, calls };
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
    let threw = false;
    try { await s.sandbox.replaceVariables({}, { type: 'global' }); } catch (e) { threw = true; }
    ok(threw, 'global 作用域显式报错（宿主存储未接，边界登记）');
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
