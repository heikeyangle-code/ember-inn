/**
 * EmberInn RenderKernel - ST API Shim（P4 扩展桥）
 *
 * 目标：让酒馆生态脚本（MVU 变量卡、酒馆助手类、正则 UI 类）在内核页里免改运行。
 * 实现策略：官方 scripts/events.js 与 public/lib/eventemitter.js 原样内联（1:1）；
 * 需要宿主状态的 API（chat/chat_metadata/斜杠/宏）经 window.AndroidKernel 桥请求-
 * 响应到 Kotlin 侧（AppSlashExecutor/ChatStore/MacroEngine，差分锁定资产）。
 *
 * 边界登记（docs/EXTENSION_COMPATIBILITY.md）：
 *  - generate()/generateRaw() 不提供：消息级内核页不承载生成管线（由 App 主进程负责）
 *  - saveSettingsDebounced 等 settings 族返回空操作成功
 */
(function () {
    'use strict';

    // =====================================================================
    // 1. 官方 lib/eventemitter.js 原样移植（release 8172dcd）
    // =====================================================================
    function EventEmitter(autoFireAfterEmit = []) {
        this.events = {};
        this.autoFireLastArgs = new Map();
        this.autoFireAfterEmit = new Set(autoFireAfterEmit);
    }

    EventEmitter.prototype.on = function (event, listener) {
        if (event === undefined) {
            console.trace('EventEmitter: Cannot listen to undefined event');
            return;
        }
        if (typeof this.events[event] !== 'object') {
            this.events[event] = [];
        }
        this.events[event].push(listener);
        if (this.autoFireAfterEmit.has(event) && this.autoFireLastArgs.has(event)) {
            listener.apply(this, this.autoFireLastArgs.get(event));
        }
    };

    EventEmitter.prototype.makeLast = function (event, listener) {
        if (typeof this.events[event] !== 'object') {
            this.events[event] = [];
        }
        var events = this.events[event];
        var idx = events.indexOf(listener);
        if (idx > -1) { events.splice(idx, 1); }
        events.push(listener);
        if (this.autoFireAfterEmit.has(event) && this.autoFireLastArgs.has(event)) {
            listener.apply(this, this.autoFireLastArgs.get(event));
        }
    };

    EventEmitter.prototype.makeFirst = function (event, listener) {
        if (typeof this.events[event] !== 'object') {
            this.events[event] = [];
        }
        var events = this.events[event];
        var idx = events.indexOf(listener);
        if (idx > -1) { events.splice(idx, 1); }
        events.unshift(listener);
        if (this.autoFireAfterEmit.has(event) && this.autoFireLastArgs.has(event)) {
            listener.apply(this, this.autoFireLastArgs.get(event));
        }
    };

    EventEmitter.prototype.removeListener = function (event, listener) {
        if (typeof this.events[event] === 'object') {
            var idx = this.events[event].indexOf(listener);
            if (idx > -1) { this.events[event].splice(idx, 1); }
        }
    };

    EventEmitter.prototype.emit = async function (event) {
        var args = [].slice.call(arguments, 1);
        console.debug('Event emitted: ' + event);
        if (typeof this.events[event] === 'object') {
            var listeners = this.events[event].slice();
            for (var i = 0; i < listeners.length; i++) {
                try { await listeners[i].apply(this, args); }
                catch (err) { console.error(err); console.trace('Error in event listener'); }
            }
        }
        if (this.autoFireAfterEmit.has(event)) {
            this.autoFireLastArgs.set(event, args);
        }
    };

    EventEmitter.prototype.emitAndWait = function (event) {
        var args = [].slice.call(arguments, 1);
        if (typeof this.events[event] === 'object') {
            var listeners = this.events[event].slice();
            for (var i = 0; i < listeners.length; i++) {
                try { listeners[i].apply(this, args); }
                catch (err) { console.error(err); console.trace('Error in event listener'); }
            }
        }
        if (this.autoFireAfterEmit.has(event)) {
            this.autoFireLastArgs.set(event, args);
        }
    };

    EventEmitter.prototype.once = function (event, listener) {
        this.on(event, function g() {
            this.removeListener(event, g);
            listener.apply(this, arguments);
        });
    };

    window.EventEmitter = EventEmitter;

    // =====================================================================
    // 2. 官方 scripts/events.js event_types 全表（release 8172dcd 原文）
    // =====================================================================
    var event_types = {
        APP_INITIALIZED: 'app_initialized',
        APP_READY: 'app_ready',
        EXTRAS_CONNECTED: 'extras_connected',
        MESSAGE_SWIPED: 'message_swiped',
        MESSAGE_SENT: 'message_sent',
        MESSAGE_RECEIVED: 'message_received',
        MESSAGE_EDITED: 'message_edited',
        MESSAGE_DELETED: 'message_deleted',
        MESSAGE_UPDATED: 'message_updated',
        MESSAGE_FILE_EMBEDDED: 'message_file_embedded',
        MESSAGE_REASONING_EDITED: 'message_reasoning_edited',
        MESSAGE_REASONING_DELETED: 'message_reasoning_deleted',
        MESSAGE_SWIPE_DELETED: 'message_swipe_deleted',
        MORE_MESSAGES_LOADED: 'more_messages_loaded',
        IMPERSONATE_READY: 'impersonate_ready',
        CHAT_CHANGED: 'chat_id_changed',
        CHAT_LOADED: 'chatLoaded',
        GENERATION_AFTER_COMMANDS: 'GENERATION_AFTER_COMMANDS',
        GENERATION_STARTED: 'generation_started',
        GENERATION_STOPPED: 'generation_stopped',
        GENERATION_ENDED: 'generation_ended',
        SD_PROMPT_PROCESSING: 'sd_prompt_processing',
        EXTENSIONS_FIRST_LOAD: 'extensions_first_load',
        EXTENSION_SETTINGS_LOADED: 'extension_settings_loaded',
        SETTINGS_LOADED: 'settings_loaded',
        SETTINGS_UPDATED: 'settings_updated',
        GROUP_UPDATED: 'group_updated',
        MOVABLE_PANELS_RESET: 'movable_panels_reset',
        SETTINGS_LOADED_BEFORE: 'settings_loaded_before',
        SETTINGS_LOADED_AFTER: 'settings_loaded_after',
        CHATCOMPLETION_SOURCE_CHANGED: 'chatcompletion_source_changed',
        CHATCOMPLETION_MODEL_CHANGED: 'chatcompletion_model_changed',
        OAI_PRESET_CHANGED_BEFORE: 'oai_preset_changed_before',
        OAI_PRESET_CHANGED_AFTER: 'oai_preset_changed_after',
        OAI_PRESET_EXPORT_READY: 'oai_preset_export_ready',
        OAI_PRESET_IMPORT_READY: 'oai_preset_import_ready',
        WORLDINFO_SETTINGS_UPDATED: 'worldinfo_settings_updated',
        WORLDINFO_UPDATED: 'worldinfo_updated',
        CHARACTER_EDITOR_OPENED: 'character_editor_opened',
        CHARACTER_EDITED: 'character_edited',
        CHARACTER_PAGE_LOADED: 'character_page_loaded',
        CHARACTER_GROUP_OVERLAY_STATE_CHANGE_BEFORE: 'character_group_overlay_state_change_before',
        CHARACTER_GROUP_OVERLAY_STATE_CHANGE_AFTER: 'character_group_overlay_state_change_after',
        USER_MESSAGE_RENDERED: 'user_message_rendered',
        CHARACTER_MESSAGE_RENDERED: 'character_message_rendered',
        FORCE_SET_BACKGROUND: 'force_set_background',
        CHAT_DELETED: 'chat_deleted',
        CHAT_CREATED: 'chat_created',
        CHAT_RENAMED: 'chat_renamed',
        GROUP_CHAT_DELETED: 'group_chat_deleted',
        GROUP_CHAT_CREATED: 'group_chat_created',
        GENERATE_BEFORE_COMBINE_PROMPTS: 'generate_before_combine_prompts',
        GENERATE_AFTER_COMBINE_PROMPTS: 'generate_after_combine_prompts',
        GENERATE_AFTER_DATA: 'generate_after_data',
        GROUP_MEMBER_DRAFTED: 'group_member_drafted',
        GROUP_WRAPPER_STARTED: 'group_wrapper_started',
        GROUP_WRAPPER_FINISHED: 'group_wrapper_finished',
        WORLD_INFO_ACTIVATED: 'world_info_activated',
        TEXT_COMPLETION_SETTINGS_READY: 'text_completion_settings_ready',
        CHAT_COMPLETION_SETTINGS_READY: 'chat_completion_settings_ready',
        CHAT_COMPLETION_PROMPT_READY: 'chat_completion_prompt_ready',
        CHARACTER_FIRST_MESSAGE_SELECTED: 'character_first_message_selected',
        CHARACTER_DELETED: 'characterDeleted',
        CHARACTER_DUPLICATED: 'character_duplicated',
        CHARACTER_RENAMED: 'character_renamed',
        CHARACTER_RENAMED_IN_PAST_CHAT: 'character_renamed_in_past_chat',
        SMOOTH_STREAM_TOKEN_RECEIVED: 'stream_token_received',
        STREAM_TOKEN_RECEIVED: 'stream_token_received',
        STREAM_REASONING_DONE: 'stream_reasoning_done',
        FILE_ATTACHMENT_DELETED: 'file_attachment_deleted',
        WORLDINFO_FORCE_ACTIVATE: 'worldinfo_force_activate',
        OPEN_CHARACTER_LIBRARY: 'open_character_library',
        ONLINE_STATUS_CHANGED: 'online_status_changed',
        IMAGE_SWIPED: 'image_swiped',
        CONNECTION_PROFILE_LOADED: 'connection_profile_loaded',
        CONNECTION_PROFILE_CREATED: 'connection_profile_created',
        CONNECTION_PROFILE_DELETED: 'connection_profile_deleted',
        CONNECTION_PROFILE_UPDATED: 'connection_profile_updated',
        TOOL_CALLS_PERFORMED: 'tool_calls_performed',
        TOOL_CALLS_RENDERED: 'tool_calls_rendered',
        CHARACTER_MANAGEMENT_DROPDOWN: 'charManagementDropdown',
        SECRET_WRITTEN: 'secret_written',
        SECRET_DELETED: 'secret_deleted',
        SECRET_ROTATED: 'secret_rotated',
        SECRET_EDITED: 'secret_edited',
        PRESET_CHANGED: 'preset_changed',
        PRESET_DELETED: 'preset_deleted',
        PRESET_RENAMED: 'preset_renamed',
        PRESET_RENAMED_BEFORE: 'preset_renamed_before',
        MAIN_API_CHANGED: 'main_api_changed',
        WORLDINFO_ENTRIES_LOADED: 'worldinfo_entries_loaded',
        WORLDINFO_SCAN_DONE: 'worldinfo_scan_done',
        MEDIA_ATTACHMENT_DELETED: 'media_attachment_deleted',
        PERSONA_CHANGED: 'persona_changed',
        PERSONA_CREATED: 'persona_created',
        PERSONA_UPDATED: 'persona_updated',
        PERSONA_RENAMED: 'persona_renamed',
        PERSONA_DELETED: 'persona_deleted',
        TTS_JOB_STARTED: 'tts_job_started',
        TTS_AUDIO_READY: 'tts_audio_ready',
        TTS_JOB_COMPLETE: 'tts_job_complete',
        ITEMIZED_PROMPTS_LOADED: 'itemized_prompts_loaded',
        ITEMIZED_PROMPTS_SAVED: 'itemized_prompts_saved',
        ITEMIZED_PROMPTS_DELETED: 'itemized_prompts_deleted'
    };
    window.event_types = event_types;

    var eventSource = new EventEmitter([event_types.APP_READY, event_types.APP_INITIALIZED]);
    window.eventSource = eventSource;

    // =====================================================================
    // 3. 桥 RPC：window.AndroidKernel.postMessage({type:'shimRequest',...})
    // =====================================================================
    var pendingCalls = new Map();
    var reqSeq = 0;

    window.__shimRespond = function (reqId, encodedPayload) {
        var entry = pendingCalls.get(reqId);
        if (!entry) return;
        pendingCalls.delete(reqId);
        try {
            var payload = JSON.parse(decodeURIComponent(encodedPayload));
            entry.resolve(payload);
        } catch (e) {
            entry.reject(e);
        }
    };

    function shimCall(method, params) {
        return new Promise(function (resolve, reject) {
            if (!window.AndroidKernel || typeof window.AndroidKernel.postMessage !== 'function') {
                reject(new Error('ST API shim: AndroidKernel bridge unavailable'));
                return;
            }
            var reqId = ++reqSeq;
            pendingCalls.set(reqId, { resolve: resolve, reject: reject });
            window.AndroidKernel.postMessage(JSON.stringify({
                type: 'shimRequest',
                reqId: String(reqId),
                method: method,
                params: params ? JSON.stringify(params) : null,
            }));
            setTimeout(function () {
                if (pendingCalls.has(reqId)) {
                    pendingCalls.delete(reqId);
                    reject(new Error('ST API shim timeout: ' + method));
                }
            }, 15000);
        });
    }

    // =====================================================================
    // 4. substituteParams：宏替换经桥走引擎 MacroEngine（差分锁定全量宏）
    //    桥不可用时的同步兜底只处理 {{user}}/{{char}}
    // =====================================================================
    function substituteParamsLocal(text) {
        return String(text)
            .split('{{user}}').join(window.__shimUser || 'User')
            .split('{{char}}').join(window.__shimChar || 'Assistant');
    }

    async function substituteParamsAsync(text) {
        try {
            var r = await shimCall('macro.substitute', { text: String(text) });
            if (r && r.ok) return r.value;
            return substituteParamsLocal(text);
        } catch (e) {
            return substituteParamsLocal(text);
        }
    }

    function substituteParams(text) {
        // 同步签名兼容：多数脚本直接拼提示词用；异步精确版见 substituteParamsAsync
        return substituteParamsLocal(text);
    }
    substituteParams.async = substituteParamsAsync;
    window.substituteParams = substituteParams;
    window.getMacroFragment = substituteParamsLocal;

    // =====================================================================
    // 5. SillyTavern.getContext()
    // =====================================================================
    function unsupported(name) {
        return function () {
            throw new Error('[EmberInn shim] ' + name + ' 在消息级渲染内核中不可用（生成管线在 App 进程）。');
        };
    }

    var SillyTavernContext = {
        // ---- 会话状态快照（每次调用实时过桥）----
        getChatMetadata: function () {
            return shimCall('metadata.get', {}).then(function (r) { return r.ok ? r.value : {}; });
        },
        setChatMetadata: function (metadata) {
            return shimCall('metadata.set', { metadata: metadata }).then(function (r) {
                if (!r || !r.ok) throw new Error('setChatMetadata failed');
                return true;
            });
        },
        saveMetadata: function () {
            // 官方为显式落盘；本项目 metadata.set 已即时落盘，此处幂等确认
            return Promise.resolve(true);
        },

        // ---- 斜杠 ----
        executeSlashCommandsWithOptions: function (text) {
            return shimCall('slash.run', { line: String(text) }).then(function (r) {
                if (!r || !r.ok) throw new Error(r && r.error ? r.error : 'slash failed');
                return { pipe: r.value, isDryRun: false, isAborted: false, isIdle: false };
            });
        },
        executeSlashCommandsWithOptionsSync: unsupported('executeSlashCommandsWithOptionsSync'),
        executeSlashCommands: function (text) {
            return SillyTavernContext.executeSlashCommandsWithOptions(text).then(function (res) { return res.pipe; });
        },

        // ---- 事件与工具 ----
        eventSource: eventSource,
        event_types: event_types,
        substituteParams: substituteParams,

        // ---- 生成族：明确拒绝（边界登记）----
        generate: unsupported('generate'),
        generateQuietPrompt: unsupported('generateQuietPrompt'),
        generateRaw: unsupported('generateRaw'),

        // ---- 快照字段（ctx.snapshot 提供）----
        reloadChat: unsupported('reloadChat'),
        saveSettingsDebounced: function () { return Promise.resolve(); },
    };

    Object.defineProperty(SillyTavernContext, 'chat', {
        get: function () { return shimCall('ctx.snapshot', {}).then(function (r) { return r.ok ? r.value.chat : []; }); },
    });

    window.SillyTavern = {
        getContext: function () { return SillyTavernContext; },
    };

    // 常用顶层别名（酒馆助手等脚本按全局取）
    window.triggerSlash = function (cmds) {
        return SillyTavernContext.executeSlashCommandsWithOptions(cmds).then(function (r) { return r.pipe; });
    };
    window.executeSlashCommands = window.triggerSlash;
    window.executeSlashCommandsWithOptions = SillyTavernContext.executeSlashCommandsWithOptions;
})();
