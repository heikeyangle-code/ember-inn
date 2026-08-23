/**
 * st-api-shim.js 结构守护（P4）：关键 API 面存在、官方事件表完整、桥协议字段对齐。
 * 纯静态断言，不依赖 DOM。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const shim = readFileSync(join(root, 'app/src/main/assets/kernel/js/st-api-shim.js'), 'utf-8');
const html = readFileSync(join(root, 'app/src/main/assets/kernel/kernel.html'), 'utf-8');
const bridge = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/renderer/KernelBridge.kt'), 'utf-8');

let pass = 0, fail = 0;
function ok(cond, name) {
    if (cond) { pass++; console.log('  ✓', name); }
    else { fail++; console.error('  ✗', name); }
}

console.log('st-api-shim 结构:');
ok(shim.includes('window.SillyTavern'), 'SillyTavern 全局暴露');
ok(shim.includes('getContext'), 'getContext 存在');
ok(shim.includes('window.eventSource = eventSource'), 'eventSource 全局单例');
ok(shim.includes('window.event_types = event_types'), 'event_types 全局');
ok(shim.includes("MESSAGE_RECEIVED: 'message_received'"), '官方事件 MESSAGE_RECEIVED 原文');
ok(shim.includes("CHAT_CHANGED: 'chat_id_changed'"), '官方事件 CHAT_CHANGED 原名不一致点保留');
ok(shim.includes("STREAM_TOKEN_RECEIVED: 'stream_token_received'"), '流式 token 事件保留');
ok((shim.match(/EventEmitter.prototype/g) || []).length >= 7, '官方 EventEmitter 原型方法齐全(官方共7个: on/once/emit/emitAndWait/removeListener/makeFirst/makeLast)');
ok(shim.includes('autoFireAfterEmit'), 'autoFire 语义保留(APP_READY 类)');
ok(shim.includes("type: 'shimRequest'"), '桥请求类型 shimRequest');
ok(shim.includes('__shimRespond'), 'JS 响应入口');
ok(shim.includes('executeSlashCommandsWithOptions'), '斜杠 API 面');
ok(shim.includes('setChatMetadata'), 'metadata 写入面');
ok(shim.includes("generate: unsupported"), '生成族显式拒绝(边界登记)');

console.log('kernel.html 装配:');
ok(html.includes('js/st-api-shim.js'), 'shim 已引入内核页');
ok(html.indexOf('st-api-shim.js') < html.indexOf('render.js'), 'shim 先于 render.js 加载');

console.log('Kotlin 桥对齐:');
ok(bridge.includes('SHIM_REQUEST ->'), '桥分发 shimRequest');
ok(bridge.includes('onShimRequest(reqId: String, method: String, paramsJson: String) {}'), 'Callbacks 默认实现');

console.log('变量族与桥信封:');
for (const fn of ['getVariables', 'replaceVariables', 'insertOrAssignVariables', 'insertVariables', 'deleteVariable', 'updateVariablesWith']) {
    ok(shim.includes(`window.${fn} =`), `变量族 ${fn} 暴露`);
}
ok(shim.includes("var reqId = String(++reqSeq)"), 'reqId 统一字符串键（数字键=回传落空 bug）');
const installer = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/renderer/StApiShimInstaller.kt'), 'utf-8');
ok(installer.includes('"value":${vm.shimChatMetadata()}'), 'metadata.get 响应带 {ok,value} 信封');

// §7 宿主白名单：shim 动作名 ↔ KernelHostAction 常量逐一咬合（改一边必须同步另一边）
console.log('宿主白名单协议对齐:');
const models = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/renderer/KernelModels.kt'), 'utf-8');
const ACTION_TO_CONST = {
    openLink: 'OPEN_LINK',
    copyText: 'COPY_TEXT',
    share: 'SHARE',
    toast: 'TOAST',
    saveMedia: 'SAVE_MEDIA',
    saveDataUrl: 'SAVE_DATA_URL',
    vibrate: 'VIBRATE',
};
for (const [action, konst] of Object.entries(ACTION_TO_CONST)) {
    ok(shim.includes(`hostRequest('${action}'`) || shim.includes(`hostRequest("${action}"`),
        `shim 发出动作 ${action}`);
    ok(models.includes(`const val ${konst} = "${action}"`), `KernelHostAction.${konst} 登记`);
}
ok(shim.includes('window.toastr = {') && shim.includes('toastrFor('), '官方 toastr 全局兼容面存在');
ok(shim.includes("'__proto__': true"), 'mergeWith 原型污染防护在位');

// globals 双作用域：shim resolveScope + installer variables.get/set 桥咬合
ok(shim.includes("type !== 'chat' && type !== 'global'"), 'resolveScope 放行 chat|global 两作用域');
ok(installer.includes('"variables.get"') && installer.includes('"variables.set"'), 'installer variables.* 桥在位');
ok(installer.includes('GlobalVariableStore'), 'Kotlin GlobalVariableStore 接线');

// 事件下发通道：shim 接收器 + Kotlin 广播链咬合
ok(shim.includes('window.__emitKernelEvent = function'), 'Native→Web 事件接收器在位');
const kernel = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/renderer/RenderKernel.kt'), 'utf-8');
ok(kernel.includes('__emitKernelEvent') && kernel.includes('fun emitEvent'), 'RenderKernel.emitEvent 发射端在位');

// 消息级事件触发点位 v2：Kotlin 落点 ↔ 官方参数形态咬合（script.js 行号见各落点注释）
console.log('消息级事件触发点位:');
const vmSrc = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/ui/chat/ChatViewModel.kt'), 'utf-8');
const screenSrc = readFileSync(join(root, 'app/src/main/java/com/emberinn/app/ui/chat/ChatScreen.kt'), 'utf-8');
for (const ev of ['message_sent', 'message_received', 'message_edited', 'message_updated', 'message_deleted', 'message_swiped', 'message_swipe_deleted']) {
    ok(vmSrc.includes(`"${ev}" to listOf(`), `ChatViewModel 发 ${ev}`);
}
ok((vmSrc.match(/"message_received" to listOf\(/g) || []).length >= 4, 'message_received 覆盖 swipe/continue×2/normal 四落点');
ok((vmSrc.match(/"message_swiped" to listOf\(/g) || []).length >= 5, 'message_swiped 覆盖左滑/右滑×3/变体跳转五落点');
ok(vmSrc.includes('"message_updated" to listOf(index.toString())') && vmSrc.indexOf('"message_edited"') < vmSrc.indexOf('"message_updated"'), '编辑保存先 EDITED 后 UPDATED（官方 messageEditDone 顺序）');
ok(vmSrc.includes('{"messageId":$index,"swipeId":$swipeIndex,"newSwipeId":$newSwipeId}'), 'swipe_deleted 对象参数三键齐全（官方 L9328）');
ok(screenSrc.includes('first_message'), '开场白 first_message 在进页装配点发（VM init 期无订阅者会丢）');
// GENERATION_ENDED 官方语义：每轮恰一次、用户停止双发、参数=落盘后 chat.length（hideStopButton NOOP 闩）
ok(vmSrc.includes('pendingGenerationEnded') && vmSrc.includes('private fun flushPendingGenerationEnded()'), 'ENDED 挂起-消费机制在位');
ok((vmSrc.match(/flushPendingGenerationEnded\(\)/g) || []).length >= 7, 'ENDED 消费点覆盖 停止/收尾/四复位路径+rising 兜底');
ok(!/generation_ended" to listOf\(_messages\.value\.size\.toString\(\)\)/.test(vmSrc.split('private fun flushPendingGenerationEnded')[0].split('var wasStreaming = false')[1] ?? ''), 'falling-edge 不再直发落盘前长度');

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
