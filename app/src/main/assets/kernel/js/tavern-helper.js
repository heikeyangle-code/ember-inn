/**
 * EmberInn RenderKernel - 酒馆助手（TavernHelper / JS-Slash-Runner v4.9.3）兼容层
 *
 * 架构边界（用户拍板）：独立模块——不修改 render.js / st-api-shim.js 任何逻辑；
 *   - 消息渲染挂接用 MutationObserver 监听 #chat，渲染管线零侵入
 *   - API 面构建在 st-api-shim.js 已暴露的 window 全局之上（eventSource/event_types/
 *     substituteParams/shimCall 同款桥协议）
 *   - 宿主配置经 shimRequest('th.config.get') 拉取 + 'tavern_helper_config' 事件增量下发
 *
 * 对齐来源（~/js-slash-runner-ref）：
 *   - @types/function/*.d.ts：导出函数名与签名形状（契约测试基线）
 *   - src/function/event.ts：eventOn/eventEmit/eventMakeFirst/eventMakeLast/eventRemoveListener
 *   - src/function/util.ts：substitudeMacros（官方拼写）、getLastMessageId、getMessageId
 *   - src/function/chat_message.ts：getChatMessages/setChatMessages/createChatMessages/deleteChatMessages
 *   - src/panel/render/iframe.ts createSrcContent：代码块→同源 iframe（srcdoc 继承本页
 *     origin，脚本可直取 window.parent 的全套 API——与 TH 运行时模型一致）
 *   - src/store/iframe_runtimes/message.ts calcToRender：depth/depth_ignore_hidden 过滤语义
 *   - src/type/settings.ts GlobalSettings.render/script：配置字段与默认值
 */
(function () {
    'use strict';

    // =====================================================================
    // 1. 配置（默认值 = TH GlobalSettings.render/script；宿主可事件下发覆盖）
    // =====================================================================
    var config = {
        renderEnabled: true,        // render.enabled
        depth: 0,                   // render.depth（0=全部楼层）
        depthIgnoreHidden: false,   // render.depth_ignore_hidden
        collapseCodeBlock: 'frontend_only', // render.collapse_code_block: none|frontend_only|all
        allowStreaming: false,      // render.allow_streaming
        scriptEnabled: true,        // script.enabled.global
    };

    function applyConfig(raw) {
        if (!raw || typeof raw !== 'object') { return; }
        var r = raw.render || raw;
        if (typeof r.enabled === 'boolean') { config.renderEnabled = r.enabled; }
        if (typeof r.depth === 'number') { config.depth = Math.max(0, Math.floor(r.depth)); }
        if (typeof r.depth_ignore_hidden === 'boolean') { config.depthIgnoreHidden = r.depth_ignore_hidden; }
        if (typeof r.collapse_code_block === 'string') { config.collapseCodeBlock = r.collapse_code_block; }
        if (typeof r.allow_streaming === 'boolean') { config.allowStreaming = r.allow_streaming; }
        var s = raw.script || {};
        if (typeof s.enabled === 'boolean') { config.scriptEnabled = s.enabled; }
        else if (typeof s.enabled === 'object' && s.enabled && typeof s.enabled.global === 'boolean') {
            config.scriptEnabled = s.enabled.global;
        }
    }
    applyConfig(window.TavernHelperConfig);

    // 启动配置拉取在文末统一走标准请求表（patchResponder 之后）；此处仅事件增量下发。


    // =====================================================================
    // 2. 事件族别名（@types/function/event.d.ts 导出面）
    // =====================================================================
    window.tavern_events = window.event_types;

    function wrapRegister(register) {
        return function (event, listener) {
            register.call(window.eventSource, String(event), listener);
            return listener;
        };
    }
    /** 注册监听并返回 listener（TH 语义，便于 eventRemoveListener 回传） */
    window.eventOn = wrapRegister(function (e, l) { this.on(e, l); });
    /** 插到队首（官方 makeFirst 直通） */
    window.eventMakeFirst = wrapRegister(function (e, l) { this.makeFirst(e, l); });
    /** 插到队尾（官方 makeLast 直通） */
    window.eventMakeLast = wrapRegister(function (e, l) { this.makeLast(e, l); });
    window.eventRemoveListener = function (event, listener) {
        window.eventSource.removeListener(String(event), listener);
    };
    window.eventEmit = function (event) {
        var args = Array.prototype.slice.call(arguments);
        args[0] = String(event);
        return window.eventSource.emit.apply(window.eventSource, args);
    };

    // =====================================================================
    // 3. 宏与消息工具（util.ts：substitudeMacros 为 TH 的历史拼写，按它导出）
    // =====================================================================
    /** 异步精确宏替换（走引擎 MacroEngine 全量宏）；TH 端本就是 async */
    window.substitudeMacros = function (text) {
        if (window.substituteParams && window.substituteParams.async) {
            return window.substituteParams.async(text);
        }
        return Promise.resolve(String(text == null ? '' : text));
    };

    function chatSnapshot() {
        return window.SillyTavern.getContext().chat.then(function (chat) {
            return Array.isArray(chat) ? chat : [];
        });
    }

    /** 最后一条消息的 id（空聊天返回 undefined，TH 同语义抛错前先给 -1 兼容面） */
    window.getLastMessageId = function () {
        return chatSnapshot().then(function (chat) {
            return chat.length > 0 ? chat.length - 1 : -1;
        });
    };
    /** 数字直返；对象取 message_id；其余 -1（TH getMessageId 兜底形状） */
    window.getMessageId = function (message) {
        if (typeof message === 'number') { return Promise.resolve(message); }
        if (message && typeof message === 'object' && typeof message.message_id === 'number') {
            return Promise.resolve(message.message_id);
        }
        return Promise.resolve(-1);
    };

    // =====================================================================
    // 4. 消息族（chat_message.d.ts 形状；数据面经 th.* 桥到 VM）
    //
    //    TH 消息对象投影：{message_id, name, role, is_user, is_system, is_hidden,
    //    mes, data}——data 即原始元素（我们的 chat 元素本身就是官方 JSONL 形状，
    //    data 直引即可；swipes/swipe_id 等字段随原始元素透传）
    // =====================================================================
    function projectMessage(el, index) {
        if (!el || typeof el !== 'object') { return undefined; }
        var isUser = el.is_user === true;
        return {
            message_id: index,
            name: el.name != null ? el.name : '',
            role: isUser ? 'user' : (el.is_system === true ? 'system' : 'assistant'),
            is_user: isUser,
            is_system: el.is_system === true,
            is_hidden: !!(el.extra && el.extra.is_system === true) || el.is_system === true && !isUser,
            mes: el.mes != null ? el.mes : '',
            data: el,
        };
    }

    /**
     * 取消息片段。range 语义对齐 TH：
     *   undefined/[null,null]=全部；[start,end] 双闭区间；负数从尾部倒数；
     *   越界位补 undefined（保持索引对齐）
     */
    window.getChatMessages = function (range) {
        return chatSnapshot().then(function (chat) {
            var len = chat.length;
            var start = 0, end = len - 1;
            if (Array.isArray(range)) {
                var a = range[0], b = range[1];
                start = (a == null) ? 0 : (a < 0 ? len + a : a);
                end = (b == null) ? len - 1 : (b < 0 ? len + b : b);
            } else if (typeof range === 'number') {
                start = range < 0 ? len + range : range;
                end = start;
            }
            var out = [];
            for (var i = Math.max(0, start); i <= Math.min(end, len - 1); i++) {
                out.push(projectMessage(chat[i], i));
            }
            return out;
        });
    };

    /** 改写消息正文（message_id 定位；附加参数后续版本扩展） */
    window.setChatMessages = function (messageId, message) {
        if (typeof messageId !== 'number') { return Promise.reject(new Error('setChatMessages: message_id 必须为数字')); }
        return shimCallStrict('th.message.set', { message_id: messageId, message: String(message == null ? '' : message) })
            .then(function (r) { return undefined; });
    };

    window.createChatMessages = function () {
        return Promise.reject(new Error('[EmberInn] createChatMessages 尚未接入（Phase 2 登记：需宿主新增消息 API）'));
    };
    window.deleteChatMessages = function (messageId) {
        var ids = Array.isArray(messageId) ? messageId : [messageId];
        ids.sort(function (a, b) { return b - a; }); // 从大到小删，避免位移
        return ids.reduce(function (p, id) {
            return p.then(function () {
                if (typeof id !== 'number') { throw new Error('deleteChatMessages: message_id 必须为数字'); }
                return shimCallStrict('th.message.delete', { message_id: id }).then(function () { return undefined; });
            });
        }, Promise.resolve());
    };
    window.rotateChatMessages = function () {
        return Promise.reject(new Error('[EmberInn] rotateChatMessages 尚未接入（Phase 2 登记）'));
    };

    function shimCallStrict(method, params) {
        return new Promise(function (resolve, reject) {
            if (!window.AndroidKernel || typeof window.AndroidKernel.postMessage !== 'function') {
                reject(new Error('TavernHelper: AndroidKernel bridge unavailable'));
                return;
            }
            var reqId = '__th_' + (++shimCallStrict.seq);
            shimCallStrict.pending[reqId] = { resolve: resolve, reject: reject };
            window.AndroidKernel.postMessage(JSON.stringify({
                type: 'shimRequest', reqId: reqId, method: method,
                params: params ? JSON.stringify(params) : null,
            }));
            setTimeout(function () {
                if (shimCallStrict.pending[reqId]) {
                    delete shimCallStrict.pending[reqId];
                    reject(new Error('TavernHelper timeout: ' + method));
                }
            }, 15000);
        });
    }
    shimCallStrict.seq = 0;
    shimCallStrict.pending = {};

    // __shimRespond 分发：st-api-shim 只认识自己的表；这里旁路注册独立请求表。
    // （不改 st-api-shim.js：在其加载后包一层全局响应入口。）
    (function patchResponder() {
        var orig = window.__shimRespond;
        window.__shimRespond = function (reqId, encodedPayload) {
            var entry = shimCallStrict.pending[reqId];
            if (entry) {
                delete shimCallStrict.pending[reqId];
                try {
                    var payload = JSON.parse(decodeURIComponent(encodedPayload));
                    if (payload && payload.ok) { entry.resolve(payload); }
                    else { entry.reject(new Error(payload && payload.error ? payload.error : 'request failed')); }
                } catch (e) { entry.reject(e); }
                return;
            }
            if (orig) { orig(reqId, encodedPayload); }
        };
    })();
    // 启动拉取宿主配置（失败保持 TH 默认值）
    if (window.AndroidKernel) {
        try {
            shimCallStrict('th.config.get', null)
                .then(function (r) { applyConfig(r.value); scheduleSweep(); })
                .catch(function () { /* 默认配置 */ });
        } catch (e) { /* no-op */ }
    }

    // =====================================================================
    // 5. 渲染器：消息内 js/ts 代码块 → 同源沙箱 iframe
    //    （panel/render/iframe.ts createSrcContent 同构；srcdoc 继承父 origin，
    //     卡内脚本可直接使用本层暴露的全部 API 与 window.parent.SillyTavern）
    // =====================================================================
    // 管线实际类名形如 "custom-js custom-language-js"（encodeStyleTags 前缀），逐 token 匹配
    var RUNNABLE_LANG = /^(?:custom-)?(?:language-)?(?:js|javascript|ts|typescript)$/i;

    /** 前端卡判定（util/is_frontend.ts 逐字语义）：内容含任一标志即界面卡，
     *  与围栏语言标签无关——```html/无语言围栏的完整 HTML 文档都算 */
    function isFrontendContent(text) {
        return ['html>', '<head>', '<body'].some(function (tag) {
            return text.indexOf(tag) !== -1;
        });
    }

    function shouldRunNode(node) {
        var toks = (node.className || '').split(/\s+/);
        for (var i = 0; i < toks.length; i++) {
            if (toks[i] && RUNNABLE_LANG.test(toks[i])) { return true; }
        }
        // 前端卡路径：语言标签不是 js/ts 但内容是完整 HTML 文档（市面界面卡主流形态）
        return isFrontendContent(node.textContent || '');
    }

    function isGenerating() {
        return document.body.getAttribute('data-generating') === 'true';
    }

    function withinDepth(mesNode) {
        if (!(config.depth > 0)) { return true; }
        // calcToRender：depth 从尾部计数；depth_ignore_hidden 时跳过 AI 不可见楼层
        var all = document.querySelectorAll('#chat > .mes');
        var visible = [];
        for (var i = 0; i < all.length; i++) {
            if (config.depthIgnoreHidden && all[i].getAttribute('is_system') === 'true') { continue; }
            visible.push(all[i]);
        }
        var tail = visible.slice(-config.depth);
        return tail.indexOf(mesNode) !== -1;
    }

    function autoResizeScript() {
        return '<script>(function(){var f=function(){var h=document.documentElement.scrollHeight;' +
            'if(window.frameElement){window.frameElement.style.height=h+"px";}};' +
            'if("ResizeObserver" in window){new ResizeObserver(f).observe(document.documentElement);}' +
            'window.addEventListener("load",f);setTimeout(f,50);})();<\/script>';
    }

    function buildSrcdoc(code) {
        return '<!DOCTYPE html><html><head><meta charset="utf-8">' +
            '<style>html,body{margin:0;padding:0;background:transparent;overflow:visible}' +
            'body{font-family:inherit;color:#ddd}</style>' +
            '</head><body>' + autoResizeScript() +
            '<script type="module">\n' + code + '\n<\/script></body></html>';
    }

    function makeIframe(code) {
        var frame = document.createElement('iframe');
        frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-modals allow-popups');
        frame.className = 'th-script-frame';
        frame.style.cssText = 'width:100%;border:0;background:transparent;display:block;';
        frame.srcdoc = buildSrcdoc(code);
        return frame;
    }

    function shouldRun(node) {
        if (!config.scriptEnabled) { return false; }
        if (!config.renderEnabled) { return false; }
        if (!config.allowStreaming && isGenerating()) { return false; }
        return RUNNABLE_LANG.test(node.className || '');
    }

    function collapseWrap(frame, codeText) {
        // collapse_code_block：frontend_only/all 时给个可展开容器（none 直接裸放）
        if (config.collapseCodeBlock === 'none') { return frame; }
        var box = document.createElement('details');
        box.className = 'th-script-box';
        var summary = document.createElement('summary');
        summary.textContent = '脚本';
        summary.style.cssText = 'cursor:pointer;opacity:.7;font-size:.9em;';
        box.appendChild(summary);
        box.appendChild(frame);
        return box;
    }

    function processMes(mesNode) {
        if (mesNode.dataset.thSwept === '1') { return; }
        mesNode.dataset.thSwept = '1';
        if (!withinDepth(mesNode)) { return; }
        var codes = mesNode.querySelectorAll('pre > code');
        Array.prototype.forEach.call(codes, function (code) {
            if (code.dataset.thDone === '1') { return; }
            if (!shouldRunNode(code)) { return; }
            var pre = code.parentElement;
            var src = code.textContent || '';
            var frame = makeIframe(src);
            code.dataset.thDone = '1';
            pre.replaceWith(collapseWrap(frame, src));
        });
    }

    function sweepAll() {
        if (!config.scriptEnabled || !config.renderEnabled) { return; }
        Array.prototype.forEach.call(document.querySelectorAll('#chat > .mes'), processMes);
    }
    var sweepTimer = null;
    function scheduleSweep() {
        if (sweepTimer) { return; }
        sweepTimer = setTimeout(function () {
            sweepTimer = null;
            sweepAll();
        }, 120);
    }

    // 零侵入挂接：只观察 #chat 变化，不触碰渲染管线
    function armObserver() {
        var chat = document.getElementById('chat');
        if (!chat || !('MutationObserver' in window)) { return; }
        new MutationObserver(scheduleSweep).observe(chat, { childList: true, subtree: true });
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { armObserver(); scheduleSweep(); });
    } else {
        armObserver();
        scheduleSweep();
    }

    // 配置热更新（宿主 emitEvent 下发）
    if (window.eventSource && window.event_types) {
        window.eventSource.on('tavern_helper_config', function (rawJson) {
            try { applyConfig(typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson); } catch (e) { /* no-op */ }
            scheduleSweep();
        });
    }

    // =====================================================================
    // 6. 世界书族（Batch A）：Bridge 只搬官方原文，官方↔TH 形状转换在此层
    //    逐字对照 src/function/worldbook.ts L176-356 的字段映射
    // =====================================================================
    var POSITION_TO_TYPE = {
        '0': 'before_character_definition', '1': 'after_character_definition',
        '5': 'before_example_messages', '6': 'after_example_messages',
        '2': 'before_author_note', '3': 'after_author_note',
        '4': 'at_depth', '7': 'outlet',
    };
    var TYPE_TO_POSITION = {
        before_character_definition: 0, after_character_definition: 1,
        before_example_messages: 5, after_example_messages: 6,
        before_author_note: 2, after_author_note: 3, at_depth: 4, outlet: 7,
    };
    var SELECTIVE_LOGIC = { 0: 'and_any', 1: 'not_all', 2: 'not_any', 3: 'and_all' };
    var LOGIC_TO_NUM = { and_any: 0, not_all: 1, not_any: 2, and_all: 3 };
    var ROLE_TO_NAME = { 0: 'system', 1: 'user', 2: 'assistant' };
    var NAME_TO_ROLE = { system: 0, user: 1, assistant: 2 };

    /** TH parseRegexFromString：'/pattern/flags' → RegExp，其余原样 */
    function parseRegexFromString(value) {
        if (typeof value !== 'string') { return value; }
        var m = value.match(/^\/(.*)\/([gimsuy]*)$/s);
        if (m) { try { return new RegExp(m[1], m[2]); } catch (e) { /* 非法正则按字面量 */ } }
        return value;
    }

    function toStringValue(v) {
        return (v instanceof RegExp) ? ('/' + v.source + '/' + v.flags) : String(v == null ? '' : v);
    }

    /** 官方条目 → TH WorldbookEntry（toWorldbookEntry 同构） */
    function toWorldbookEntry(o) {
        o = o || {};
        var delayUntil = typeof o.delayUntilRecursion === 'number' && o.delayUntilRecursion > 0 ? o.delayUntilRecursion : null;
        return {
            uid: typeof o.uid === 'number' ? o.uid : 0,
            name: o.comment != null ? o.comment : '',
            enabled: o.disable !== true,
            strategy: {
                type: o.constant ? 'constant' : o.vectorized ? 'vectorized' : 'selective',
                keys: (o.key || []).map(parseRegexFromString),
                keys_secondary: {
                    logic: SELECTIVE_LOGIC[o.selectiveLogic] || 'and_any',
                    keys: (o.keysecondary || []).map(parseRegexFromString),
                },
                scan_depth: o.scanDepth != null ? o.scanDepth : 'same_as_global',
            },
            position: {
                type: POSITION_TO_TYPE[String(o.position)] || 'before_character_definition',
                role: ROLE_TO_NAME[o.role == null ? 0 : o.role] || 'system',
                depth: typeof o.depth === 'number' ? o.depth : 4,
                order: typeof o.order === 'number' ? o.order : 100,
            },
            content: o.content != null ? o.content : '',
            probability: o.useProbability === false ? 100 : (typeof o.probability === 'number' ? o.probability : 100),
            recursion: {
                prevent_incoming: o.excludeRecursion === true,
                prevent_outgoing: o.preventRecursion === true,
                delay_until: delayUntil,
            },
            effect: {
                sticky: typeof o.sticky === 'number' && o.sticky > 0 ? o.sticky : null,
                cooldown: typeof o.cooldown === 'number' && o.cooldown > 0 ? o.cooldown : null,
                delay: typeof o.delay === 'number' && o.delay > 0 ? o.delay : null,
            },
        };
    }

    /** TH WorldbookEntry(部分) → 官方条目（fromWorldbookEntry 同构） */
    function fromWorldbookEntry(e, displayIndex) {
        e = e || {};
        var st = e.strategy || {};
        var pos = e.position || {};
        var rec = e.recursion || {};
        var eff = e.effect || {};
        return {
            uid: typeof e.uid === 'number' ? e.uid : displayIndex,
            displayIndex: displayIndex,
            comment: e.name != null ? String(e.name) : '',
            disable: !(e.enabled !== false),
            constant: st.type ? st.type === 'constant' : true,
            selective: st.type === 'selective',
            key: (st.keys || []).map(toStringValue),
            selectiveLogic: LOGIC_TO_NUM[(st.keys_secondary || {}).logic] != null
                ? LOGIC_TO_NUM[st.keys_secondary.logic] : 0,
            keysecondary: ((st.keys_secondary || {}).keys || []).map(toStringValue),
            scanDepth: st.scan_depth === 'same_as_global' || st.scan_depth == null ? null : st.scan_depth,
            vectorized: st.type === 'vectorized',
            position: TYPE_TO_POSITION[pos.type] != null ? TYPE_TO_POSITION[pos.type] : 0,
            role: NAME_TO_ROLE[pos.role] != null ? NAME_TO_ROLE[pos.role] : 0,
            depth: typeof pos.depth === 'number' ? pos.depth : 4,
            order: typeof pos.order === 'number' ? pos.order : 100,
            content: e.content != null ? String(e.content) : '',
            useProbability: e.probability !== undefined,
            probability: typeof e.probability === 'number' ? e.probability : 100,
            excludeRecursion: rec.prevent_incoming === true,
            preventRecursion: rec.prevent_outgoing === true,
            delayUntilRecursion: typeof rec.delay_until === 'number' ? rec.delay_until : false,
            sticky: typeof eff.sticky === 'number' ? eff.sticky : false,
            cooldown: typeof eff.cooldown === 'number' ? eff.cooldown : false,
            delay: typeof eff.delay === 'number' ? eff.delay : false,
        };
    }

    function worldEntries(name) {
        return shimCallStrict('th.worldbook.raw', { name: name }).then(function (r) {
            var parsed = JSON.parse(r.value);
            var map = parsed && typeof parsed === 'object' ? (parsed.entries || parsed) : {};
            var arr = [];
            Object.keys(map).forEach(function (uid) { arr[Number(uid)] = map[uid]; });
            var out = [];
            for (var i = 0; i < arr.length; i++) { if (arr[i]) { out.push(toWorldbookEntry(arr[i])); } }
            return out;
        });
    }

    function saveWorldEntries(name, thEntries) {
        var entriesMap = {};
        (thEntries || []).forEach(function (e, i) {
            var o = fromWorldbookEntry(e, i);
            entriesMap[String(o.uid != null ? o.uid : i)] = o;
        });
        return shimCallStrict('th.worldbook.saveRaw', {
            name: String(name), content: JSON.stringify({ entries: entriesMap }),
        }).then(function (r) { return !!r.value; });
    }

    window.getWorldbookNames = function () {
        return shimCallStrict('th.worldbook.names', null).then(function (r) { return r.value || []; });
    };
    /** 全局世界书：App 尚无全局绑定概念，空表（Batch B 登记接等价物） */
    window.getGlobalWorldbookNames = function () { return Promise.resolve([]); };
    window.getChatWorldbookName = function () {
        return shimCallStrict('th.worldbook.chatWorld', null).then(function (r) { return r.value || null; });
    };
    window.rebindChatWorldbook = function (_chatName, worldbookName) {
        return shimCallStrict('th.worldbook.setChatWorld', { name: worldbookName == null ? null : String(worldbookName) })
            .then(function () { return undefined; });
    };
    window.getOrCreateChatWorldbook = function (_chatName, worldbookName) {
        return window.getChatWorldbookName().then(function (current) {
            if (current) { return current; }
            var target = worldbookName != null ? String(worldbookName) : ('chat-' + (window.__shimChar || 'default') + '-worldbook');
            return shimCallStrict('th.worldbook.create', { name: target })
                .then(function () { return shimCallStrict('th.worldbook.setChatWorld', { name: target }); })
                .then(function () { return target; });
        });
    };
    window.getWorldbook = function (name) { return worldEntries(String(name)); };
    window.createWorldbook = function (name, entries) {
        return shimCallStrict('th.worldbook.create', { name: String(name) })
            .then(function () { return saveWorldEntries(String(name), entries || []); });
    };
    window.createOrReplaceWorldbook = function (name, entries) {
        return window.getWorldbookNames().then(function (names) {
            return names.indexOf(String(name)) !== -1
                ? saveWorldEntries(String(name), entries || [])
                : window.createWorldbook(String(name), entries);
        });
    };
    window.deleteWorldbook = function (name) {
        return shimCallStrict('th.worldbook.delete', { name: String(name) }).then(function (r) { return !!r.value; });
    };
    window.replaceWorldbook = function (name, entries) { return saveWorldEntries(String(name), entries); };
    window.updateWorldbookWith = function (name, updater) {
        return window.getWorldbook(name).then(function (entries) {
            var result = updater(entries);
            return (result && typeof result.then === 'function')
                ? result.then(function (next) { return saveWorldEntries(String(name), next); })
                : saveWorldEntries(String(name), result);
        });
    };
    window.createWorldbookEntries = function (name, entries) {
        return window.getWorldbook(name).then(function (existing) {
            var maxUid = existing.reduce(function (m, e) { return Math.max(m, e.uid); }, -1);
            (entries || []).forEach(function (e, i) {
                e.uid = (e && typeof e.uid === 'number') ? e.uid : ++maxUid;
                existing.push(e);
            });
            return saveWorldEntries(String(name), existing);
        });
    };
    window.deleteWorldbookEntries = function (name, uids) {
        var kill = {};
        (uids || []).forEach(function (u) { kill[u] = true; });
        return window.getWorldbook(name).then(function (existing) {
            return saveWorldEntries(String(name), existing.filter(function (e) { return !kill[e.uid]; }));
        });
    };
    window.rebindGlobalWorldbooks = function () {
        return Promise.reject(new Error('[EmberInn] rebindGlobalWorldbooks 待 Batch B（全局绑定概念未落地）'));
    };

    // =====================================================================
    // 7. 版本常量（version.ts；TH 包版本与官方基线版本）
    // =====================================================================
    window.getTavernHelperVersion = function () { return '4.9.3'; };
    window.getTavernVersion = function () { return '1.18.0'; };
})();
