/**
 * 官方对齐差分：variables.js 数学/布尔/长度/排序 斜杠命令。
 * 提取源（SillyTavern 1.18.0 / release 8172dcd）：
 *   - public/scripts/variables.js:494-560 evalBoolean
 *   - public/scripts/variables.js:620-814 parseNumericSeries / performOperation /
 *     add/mul/min/max/sub/div/mod/pow/round/abs/sqrt/sin/cos/log / customSortComparitor /
 *     sortArrayObjectCallback / lenValuesCallback
 *   - public/scripts/utils.js:1011-1022 isTrueBoolean / isFalseBoolean
 * 打桩：
 *   - resolveVariable(name, scope)：官方查作用域变量；差分 fixture 中 scope 是 {name:value}，
 *     查不到时返回原名字面量（与官方 getOperand 兜底 String(operand) 一致）。
 *   - convertValueType(str, 'array')：官方类型转换器，差分用 JSON.parse 等价。
 *   - 已知未覆盖差异登记：JS Number.toString 在 >=1e21 用科学计数法、Kotlin 阈值不同；
 *     十六进制/Infinity 字面量 Number() 解析差异；二者本脚本用例均限制在常规十进制区间，
 *     Kotlin 侧用“整数值 → Long 字符串，否则 Double.toString”复刻 JS 常规输出。
 *   - sin/cos/log：JS Math.* 与 Java StrictMath 个别输入存在 1 ULP 尾差（如 cos(1000)），
 *     差分断言对这三者允许 1e-12 容差，其余命令逐字比对。
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 官方函数（逐字） ----
function isTrueBoolean(arg) {
    return ['on', 'true', '1'].includes(arg?.trim()?.toLowerCase());
}
function isFalseBoolean(arg) {
    return ['off', 'false', '0'].includes(arg?.trim()?.toLowerCase());
}
function evalBoolean(rule, a, b) {
    if (a === undefined) {
        throw new Error('Left operand is not provided');
    }
    if (b === undefined) {
        switch (rule) {
            case undefined:
            case 'not': {
                const resultOnTruthy = rule !== 'not';
                if (isTrueBoolean(String(a))) return resultOnTruthy;
                if (isFalseBoolean(String(a))) return !resultOnTruthy;
                return a ? resultOnTruthy : !resultOnTruthy;
            }
            default:
                throw new Error(`Unknown boolean comparison rule for truthy check. If right operand is not provided, the rule must not provided or be 'not'. Provided: ${rule}`);
        }
    }
    rule ??= 'eq';
    if (typeof a === 'number' && typeof b === 'number') {
        const aNumber = Number(a);
        const bNumber = Number(b);
        switch (rule) {
            case 'gt': return aNumber > bNumber;
            case 'gte': return aNumber >= bNumber;
            case 'lt': return aNumber < bNumber;
            case 'lte': return aNumber <= bNumber;
            case 'eq': return aNumber === bNumber;
            case 'neq': return aNumber !== bNumber;
            case 'in':
            case 'nin':
                break;
            default:
                throw new Error(`Unknown boolean comparison rule for type number. Accepted: gt, gte, lt, lte, eq, neq. Provided: ${rule}`);
        }
    }
    let aString = (typeof a === 'string') ? a.toLowerCase() : JSON.stringify(a).toLowerCase();
    let bString = (typeof b === 'string') ? b.toLowerCase() : JSON.stringify(b).toLowerCase();
    switch (rule) {
        case 'in': return aString.includes(bString);
        case 'nin': return !aString.includes(bString);
        case 'eq': return aString === bString;
        case 'neq': return aString !== bString;
        default:
            throw new Error(`Unknown boolean comparison rule for type string. Accepted: in, nin, eq, neq. Provided: ${rule}`);
    }
}

const resolveVariable = (name, scope) => (scope && scope[name] !== undefined ? scope[name] : name);
const convertValueType = (value, type) => (type === 'array' ? JSON.parse(value) : value);

function parseNumericSeries(value, scope = null) {
    if (typeof value === 'number') {
        return [value];
    }
    let values = Array.isArray(value) ? value : value.split(' ');
    if (values.length === 1 && typeof values[0] === 'string') {
        if (values[0].startsWith('[')) {
            values = convertValueType(values[0], 'array');
        } else {
            values = values[0].split(' ');
        }
    }
    const array = values.map(i => typeof i === 'string' ? i.trim() : i)
        .filter(i => i !== '')
        .map(i => isNaN(Number(i)) ? Number(resolveVariable(String(i), scope)) : Number(i))
        .filter(i => !isNaN(i));
    return array;
}

function performOperation(value, operation, singleOperand = false, scope = null) {
    function getResult() {
        if (!value) {
            return 0;
        }
        const array = parseNumericSeries(value, scope);
        if (array.length === 0) {
            return 0;
        }
        const result = singleOperand ? operation(array[0]) : operation(array);
        if (isNaN(result)) {
            return 0;
        }
        return result;
    }
    const result = getResult();
    return String(result);
}

const addValuesCallback = (args, value) => performOperation(value, (array) => array.reduce((a, b) => a + b, 0), false, args._scope);
const mulValuesCallback = (args, value) => performOperation(value, (array) => array.reduce((a, b) => a * b, 1), false, args._scope);
const minValuesCallback = (args, value) => performOperation(value, (array) => Math.min(...array), false, args._scope);
const maxValuesCallback = (args, value) => performOperation(value, (array) => Math.max(...array), false, args._scope);
const subValuesCallback = (args, value) => performOperation(value, (array) => array.reduce((a, b) => a - b, array.shift() ?? 0), false, args._scope);
const divValuesCallback = (args, value) => performOperation(value, (array) => {
    if (array[1] === 0) { return 0; }
    return array[0] / array[1];
}, false, args._scope);
const modValuesCallback = (args, value) => performOperation(value, (array) => {
    if (array[1] === 0) { return 0; }
    return array[0] % array[1];
}, false, args._scope);
const powValuesCallback = (args, value) => performOperation(value, (array) => Math.pow(array[0], array[1]), false, args._scope);
const sinValuesCallback = (args, value) => performOperation(value, Math.sin, true, args._scope);
const cosValuesCallback = (args, value) => performOperation(value, Math.cos, true, args._scope);
const logValuesCallback = (args, value) => performOperation(value, Math.log, true, args._scope);
const roundValuesCallback = (args, value) => performOperation(value, Math.round, true, args._scope);
const absValuesCallback = (args, value) => performOperation(value, Math.abs, true, args._scope);
const sqrtValuesCallback = (args, value) => performOperation(value, Math.sqrt, true, args._scope);

function lenValuesCallback(value) {
    let parsedValue = value;
    try {
        parsedValue = JSON.parse(value);
    } catch {
        // could not parse
    }
    if (Array.isArray(parsedValue)) {
        return parsedValue.length;
    }
    switch (typeof parsedValue) {
        case 'string': return parsedValue.length;
        case 'object': return Object.keys(parsedValue).length;
        case 'number': return String(parsedValue).length;
        default: return 0;
    }
}

function customSortComparitor(a, b) {
    if (typeof a != typeof b) {
        a = typeof a;
        b = typeof b;
    }
    return a > b ? 1 : a < b ? -1 : 0;
}

function sortArrayObjectCallback(args, value) {
    let parsedValue;
    if (typeof value == 'string') {
        try {
            parsedValue = JSON.parse(value);
        } catch {
            return value;
        }
    } else {
        parsedValue = value;
    }
    if (Array.isArray(parsedValue)) {
        parsedValue.sort(customSortComparitor);
    } else if (typeof parsedValue == 'object') {
        let keysort = args.keysort;
        if (isFalseBoolean(keysort)) {
            parsedValue = Object.keys(parsedValue).sort(function (a, b) { return customSortComparitor(parsedValue[a], parsedValue[b]); });
        } else {
            parsedValue = Object.keys(parsedValue).sort(customSortComparitor);
        }
    }
    return JSON.stringify(parsedValue);
}

// ---- 用例（穷举各分支与边界） ----
const cases = [];
const scope = { count: '4', i: '-2', pi: '3.14', big: '123456789012345678' };

function math(name, value, sc) {
    cases.push({ kind: 'math', op: name, value: typeof value === 'number' ? String(value) : value, scope: sc ?? null });
}
for (const op of ['add', 'mul', 'min', 'max', 'sub', 'div', 'mod', 'pow', 'round', 'abs', 'sqrt', 'sin', 'cos', 'log']) {
    math(op, '');
    math(op, '   ');
    math(op, '0');
    math(op, '5');
    math(op, '-5');
    math(op, '10 20');
    math(op, '10 3 2');
    math(op, '10 0');
    math(op, '0 10');
    math(op, '1.5 2.25');
    math(op, 'a b c');        // 全非数字
    math(op, '10 xyz 5');     // 混合，非数字被丢弃
    math(op, '[10, 3, 2]');
    math(op, '["count", 15, 2, "i"]', scope);
    math(op, 'count 15 2 i', scope);
    math(op, '["x"]', scope);
    math(op, '-3 4');         // 验证单目只取第一个、双目只取前两个
}
// 追加穷举：边界 / 格式 / 混合类型 / Unicode / 缺失操作数
for (const op of ['add', 'mul', 'min', 'max', 'sub', 'div', 'mod', 'pow', 'round', 'abs', 'sqrt', 'sin', 'cos', 'log']) {
    math(op, '-0');
    math(op, '0.1 0.2');
    math(op, '1 2 3 4 5');
    math(op, '1e3 2e2');
    math(op, '9999999999999999');
    math(op, ' 10   20 ');
    math(op, '1.5');
    math(op, '["1", 2, "3.5"]');
    math(op, '["i", "count"]', scope);
}
// evalBoolean
function eb(rule, a, b) {
    cases.push({ kind: 'bool', rule: rule === undefined ? '__none__' : rule, a, b: b === undefined ? '__none__' : b });
}
eb(undefined, '10', '10');
eb('eq', '10', '10');
eb('eq', '10', '10.0');
eb('eq', 'Abc', 'abc');
eb('eq', 'abc', 'abd');
eb('neq', 'Abc', 'abc');
eb('in', 'Hello World', 'hello');
eb('nin', 'Hello World', 'xyz');
eb('in', 12345, 45);
eb('nin', 12345, 99);
eb('gt', 10, 5);
eb('gte', 10, 10);
eb('lt', 5, 10);
eb('lte', 5, 5);
eb('gt', 'abc', 5);   // 字符串 gt → 抛错
eb('lt', 5, 'abc');   // 数字+字符串 → 字符串分支 → 抛错
eb('not', 'on', undefined);
eb('not', 'off', undefined);
eb('not', '', undefined);
eb(undefined, 'true', undefined);
eb(undefined, 'false', undefined);
eb(undefined, 'hello', undefined);
eb(undefined, 0, undefined);
eb('gt', 5, undefined); // 无右操作数 + gt → 抛错
eb('eq', 10, 10.0);
eb('in', 10, 45);
eb('eq', '10', 10);
eb('eq', 10, '10.0');
eb('in', 123, '2');
eb('nin', 'abc', '');
eb(undefined, '', undefined);
eb('not', '', undefined);
eb('not', '0', undefined);
eb(undefined, '0', undefined);
eb(undefined, 0, undefined);
eb('eq', 'ABC', 'abc');
eb('in', 'hello', '');
eb('__none__', 'x', undefined);
// len
for (const v of ['hello', 'hello world', '', '123', '-3.5', '[1,2,3]', '[]', '{"a":1,"b":2}', '{}', 'true', 'false', 'null', '["a","b"]', '[{"x":1}]',
    '"123"', '"hello"', '[1,2,3,4]', '{"a":1,"b":2,"c":3}', '1e3', '0.001', '-0', '"你好"', '  hello  ', '[[1],[2],[3]]']) {
    cases.push({ kind: 'len', value: v });
}
// sort
const sortCases = [
    ['[5,3,4,1,2]', undefined],
    ['["b","a","c"]', undefined],
    ['[3,"a",1,"b"]', undefined],
    ['[1.5,1.2,2]', undefined],
    ['[true,false,true]', undefined],
    ['{"b":2,"a":10,"c":1}', 'true'],
    ['{"b":2,"a":10,"c":1}', 'false'],
    ['{"b":"x","a":"y"}', 'false'],
    ['not json', undefined],
    ['{"z":1,"a":2}', 'off'],
    ['[5,"a",true,3,"b"]', undefined],
    ['{"x":1,"y":2}', undefined],
    ['[2,1,2,3]', undefined],
    ['["b","B","a"]', undefined],
    ['[1,null,2]', undefined],
    ['[true,false]', undefined],
    ['{"2":"b","1":"a","10":"c"}', undefined],
    ['["hello","world","hello"]', undefined],
];
for (const [value, keysort] of sortCases) {
    cases.push({ kind: 'sort', value, keysort: keysort ?? null });
}

const fixture = [];
for (const c of cases) {
    let expected, throws = false;
    try {
        if (c.kind === 'math') {
            const map = { add: addValuesCallback, mul: mulValuesCallback, min: minValuesCallback, max: maxValuesCallback, sub: subValuesCallback, div: divValuesCallback, mod: modValuesCallback, pow: powValuesCallback, round: roundValuesCallback, abs: absValuesCallback, sqrt: sqrtValuesCallback, sin: sinValuesCallback, cos: cosValuesCallback, log: logValuesCallback };
            expected = map[c.op]({ _scope: c.scope ?? {} }, c.value);
        } else if (c.kind === 'bool') {
            const a = c.a === '__none__' ? undefined : c.a;
            const b = c.b === '__none__' ? undefined : c.b;
            const rule = c.rule === '__none__' ? undefined : c.rule;
            expected = String(evalBoolean(rule, a, b));
        } else if (c.kind === 'len') {
            expected = String(lenValuesCallback(c.value));
        } else {
            expected = sortArrayObjectCallback({ keysort: c.keysort ?? 'true' }, c.value);
        }
    } catch (e) {
        throws = true;
        expected = String(e.message ?? e);
    }
    fixture.push({ kind: c.kind, op: c.op ?? null, value: c.value, scope: c.scope ?? null, rule: c.rule ?? null, a: c.a ?? null, b: c.b ?? null, keysort: c.keysort ?? null, throws, expected });
}

const out = join(__dirname, '..', '..', 'engine/src/test/resources/diff/slash-math.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(fixture, null, 1) + '\n');
console.log('fixture cases:', fixture.length, '->', out);
