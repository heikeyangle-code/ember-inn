/**
 * EmberInn RenderKernel - render.js v0.1
 *
 * 官方基线：SillyTavern release 8172dcd (1.18.0)
 *
 * 分工边界（与引擎 MessageFormattingEngine 衔接）：
 *   引擎（Kotlin，差分锁定）：宏替换 / 正则位点 / reasoning 提取
 *   本文件（官方原版逻辑移植）：
 *     fixMarkdown → encode_tags → 引号 <q> 包裹 → align 替换
 *     → Showdown makeHtml（官方同配置 + 官方扩展）
 *     → code 块换行/&amp; 修正 → name2 剥离
 *     → encodeStyleTags → DOMPurify.sanitize(MESSAGE_SANITIZE) → decodeStyleTags('.mes_text ')
 *
 * 所有格式化函数逐字对齐官方实现，改动处均有注释标记 [EmberInn]。
 */
(function () {
    'use strict';

    // ------------------------------------------------------------------
    // 内核配置（由原生侧经 KernelConfig 注入；缺省值 = 官方 power_user 默认）
    // ------------------------------------------------------------------
    window.KernelConfig = Object.assign({
        autoFixGeneratedMarkdown: true,   // power_user.auto_fix_generated_markdown 默认 true
        encodeTags: false,                // power_user.encode_tags 默认 false（power-user.js L301，HTML 卡片可渲染的前提）
        markdownEscapeStrings: '',        // power_user.markdown_escape_strings 默认 ''
        allowName2Display: false,         // power_user.allow_name2_display 默认 false
        trimSpaces: true,                 // power_user.trim_spaces 默认 true（messageEdit 填充用）
        autoSaveEdits: false,             // power_user.auto_save_msg_edits 默认 false（power-user.js L149）
        externalMediaAllowed: true,       // [EmberInn] 放开模式：外链媒体始终允许
        // —— 运行时下发（setRuntimeConfig 桥，宿主 BehaviorPrefs 同名键）——
        streamFadeIn: false,              // power_user.stream_fade_in 默认 false（官方 stream-fadein.js）
        gestures: true,                   // power_user.gestures 默认 true（消息横滑切变体）
        sendOnEnter: 0,                   // power_user.send_on_enter：-1 AUTO / 0 关 / 1 开（移动端 AUTO=不发送）
        quickContinue: false,             // power_user.quick_continue 默认 false（#mes_continue 显隐）
        quickImpersonate: false,          // power_user.quick_impersonate 默认 false（#mes_impersonate 显隐）
    }, window.KernelConfig || {});

    // ------------------------------------------------------------------
    // Showdown converter：官方 getConverter() 配置逐字对齐（script.js L521）
    // ------------------------------------------------------------------
    var converter = new showdown.Converter({
        emoji: true,
        literalMidWordUnderscores: true,
        parseImgDimensions: true,
        tables: true,
        underline: true,
        simpleLineBreaks: true,
        strikethrough: true,
        disableForced4SpacesIndentedSublists: true,
        extensions: [window.markdownUnderscoreExt()],
    });
    converter.addExtension(window.markdownExclusionExt(), 'exclusion');

    // ------------------------------------------------------------------
    // 官方工具函数移植
    // ------------------------------------------------------------------
    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeRegex(string) {
        return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function countOccurrences(str, substr) {
        var count = 0;
        var pos = 0;
        while ((pos = str.indexOf(substr, pos)) !== -1) {
            count++;
            pos += substr.length;
        }
        return count;
    }

    function isOdd(num) {
        return num % 2 !== 0;
    }

    // 官方 power-user.js L429 fixMarkdown 原样移植
    function fixMarkdown(text, forDisplay) {
        var format = /([*_]{1,2})([\s\S]*?)\1/gm;
        var matches = [];
        var match;
        while ((match = format.exec(text)) !== null) {
            matches.push(match);
        }

        var newText = text;
        for (var i = matches.length - 1; i >= 0; i--) {
            var matchText = matches[i][0];
            var replacementText = matchText.replace(
                /(\*|_)([\t \u00a0\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]+)|([\t \u00a0\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]+)(\*|_)/g,
                '$1$4');
            newText = newText.slice(0, matches[i].index) + replacementText + newText.slice(matches[i].index + matchText.length);
        }

        if (!forDisplay) {
            return newText;
        }

        var splitText = newText.split('\n');
        for (var index = 0; index < splitText.length; index++) {
            var line = splitText[index];
            var charsToCheck = ['*', '"'];
            for (var c = 0; c < charsToCheck.length; c++) {
                var char = charsToCheck[c];
                if (line.includes(char) && isOdd(countOccurrences(line, char))) {
                    splitText[index] = line.trimEnd() + char;
                }
            }
        }

        return splitText.join('\n');
    }

    // 官方 chats.js L536 encodeStyleTags 原样移植（risuAI 版权逻辑，与官方一致）
    function encodeStyleTags(text) {
        var styleRegex = /<style>(.+?)<\/style>/gims;
        return text.replaceAll(styleRegex, function (_, match) {
            return '<custom-style>' + encodeURIComponent(match) + '</custom-style>';
        });
    }

    // 官方 chats.js L551 decodeStyleTags 原样移植；mediaAllowed 恒为 true（放开模式）
    function decodeStyleTags(text, prefix) {
        prefix = prefix || '.mes_text ';
        var styleDecodeRegex = /<custom-style>(.+?)<\/custom-style>/gms;
        var css = window.cssTools;

        function sanitizeRule(rule) {
            if (Array.isArray(rule.selectors)) {
                for (var i = 0; i < rule.selectors.length; i++) {
                    var selector = rule.selectors[i];
                    if (selector) {
                        rule.selectors[i] = prefix + sanitizeSelector(selector);
                    }
                }
            }
            if (!window.KernelConfig.externalMediaAllowed &&
                Array.isArray(rule.declarations) && rule.declarations.length > 0) {
                rule.declarations = rule.declarations.filter(function (declaration) {
                    return !declaration.value.includes('://');
                });
            }
        }

        function sanitizeSelector(selector) {
            var pseudoClasses = ['has', 'not', 'where', 'is', 'matches', 'any'];
            var pseudoRegex = new RegExp(':(' + pseudoClasses.join('|') + ')\\(([^)]+)\\)', 'g');
            selector = selector.replace(pseudoRegex, function (match, pseudoClass, content) {
                return ':' + pseudoClass + '(' + sanitizeSimpleSelector(content) + ')';
            });
            return sanitizeSimpleSelector(selector);
        }

        function sanitizeSimpleSelector(selector) {
            return selector.split(/\s+/).map(function (part) {
                return part.replace(/\.([\w-]+)/g, function (match, className) {
                    if (className.startsWith('custom-')) {
                        return match;
                    }
                    return '.custom-' + className;
                });
            }).join(' ');
        }

        function sanitizeRuleSet(ruleSet) {
            if (Array.isArray(ruleSet.selectors) || Array.isArray(ruleSet.declarations)) {
                sanitizeRule(ruleSet);
            }
            if (Array.isArray(ruleSet.rules)) {
                ruleSet.rules = ruleSet.rules.filter(function (rule) { return rule.type !== 'import'; });
                for (var i = 0; i < ruleSet.rules.length; i++) {
                    sanitizeRuleSet(ruleSet.rules[i]);
                }
            }
        }

        return text.replaceAll(styleDecodeRegex, function (_, style) {
            try {
                var styleCleaned = decodeURIComponent(style).replaceAll(/<br\/>/g, '');
                var ast = css.parse(styleCleaned);
                var sheet = ast && ast.stylesheet;
                if (sheet) {
                    sanitizeRuleSet(ast.stylesheet);
                }
                return '<style>' + css.stringify(ast) + '</style>';
            } catch (error) {
                return 'CSS ERROR: ' + error;
            }
        });
    }

    // ------------------------------------------------------------------
    // 官方 DOMPurify hooks（chats.js addDOMPurifyHooks 原样移植；
    // 外链媒体拦截段按放开模式跳过——isExternalMediaAllowed 恒为 true）
    // ------------------------------------------------------------------
    (function addDOMPurifyHooks() {
        var D = window.DOMPurify;

        // 链接允许 target="_blank"
        D.addHook('afterSanitizeAttributes', function (node) {
            if ('target' in node) {
                node.setAttribute('target', '_blank');
                node.setAttribute('rel', 'noopener');
            }
        });

        D.addHook('uponSanitizeAttribute', function (node, data, config) {
            if (!config.MESSAGE_SANITIZE) { return; }

            // MESSAGE_ALLOW_SYSTEM_UI：保留与主 UI 交互的 menu_button 类（默认关，同官方）
            var permittedNodeTypes = ['BUTTON', 'DIV'];
            if (config.MESSAGE_ALLOW_SYSTEM_UI && node.classList && node.classList.contains('menu_button') && permittedNodeTypes.includes(node.nodeName)) {
                return;
            }

            switch (data.attrName) {
                case 'class': {
                    if (data.attrValue) {
                        data.attrValue = data.attrValue.split(' ').map(function (v) {
                            if (v.startsWith('fa-') || v.startsWith('note-') || v === 'monospace') {
                                return v;
                            }
                            return 'custom-' + v;
                        }).join(' ');
                    }
                    break;
                }
            }
        });

        D.addHook('uponSanitizeElement', function (node, _, config) {
            if (!config.MESSAGE_SANITIZE) { return; }

            // 未知元素内换行转 <br>
            if (node instanceof window.HTMLUnknownElement) {
                node.innerHTML = node.innerHTML.trim();

                var candidates = [];
                var walker = document.createTreeWalker(node, window.NodeFilter.SHOW_TEXT);
                while (walker.nextNode()) {
                    var textNode = walker.currentNode;
                    if (!textNode.data.includes('\n')) continue;
                    if (textNode.parentElement && textNode.parentElement.closest('pre')) continue;
                    candidates.push(textNode);
                }

                for (var i = 0; i < candidates.length; i++) {
                    var tn = candidates[i];
                    var parts = tn.data.split('\n');
                    var frag = document.createDocumentFragment();
                    parts.forEach(function (part, idx) {
                        if (part.length) { frag.appendChild(document.createTextNode(part)); }
                        if (idx < parts.length - 1) { frag.appendChild(document.createElement('br')); }
                    });
                    tn.replaceWith(frag);
                }
            }

            // [EmberInn] 放开模式：外链媒体不拦截（官方在 !isExternalMediaAllowed 时才阻断 AUDIO/VIDEO/IMG 等）
        });
    })();

    // ------------------------------------------------------------------
    // 核心格式化管线
    // ------------------------------------------------------------------
    /** formatText 结果 LRU：showdown+DOMPurify 全管线是重进聊天全量重渲的成本大头，
     *  同文本（截断/切换/回滚/重进）命中缓存零重算。容量 400 ≈ 4 长聊天楼层量级。 */
    var formatCache = new Map();
    var FORMAT_CACHE_MAX = 400;
    /** 运行时配置代次：setRuntimeConfig bump → 旧缓存键全不命中（markdown_escape 等
     *  影响格式化结果的开关变化后，官方"重载 processor"的等价失效语义）。 */
    var kernelConfigRevision = 0;

    function formatCacheKey(mes, opts) {
        return kernelConfigRevision + ':' +
            (opts && opts.isSystem ? 'S' : '') + (opts && opts.isUser ? 'U' : '') +
            (opts && opts.chName ? '|' + opts.chName : '') + '|' + mes;
    }

    /**
     * 与官方 messageFormatting() 的显示段逐字对齐。
     * @param {string} mes       已经过引擎处理（宏/正则/reasoning）的消息文本
     * @param {object} opts      { chName, isUser, isSystem, allowName2Display }
     * @returns {string} 最终 HTML（可直接注入 .mes_text.innerHTML）
     */
    function formatText(mes, opts) {
        // 缓存命中：正文/思考块/译文重进全部零成本（流式中间态不进缓存——文本每 tick 都变，
        // 只在 renderChat 全量同步与 mountMessage 终态时命中）
        if (mes && mes.length < 200000) {
            var key = formatCacheKey(mes, opts);
            if (formatCache.has(key)) {
                var hit = formatCache.get(key);
                formatCache.delete(key);
                formatCache.set(key, hit); // LRU 触底提升
                return hit;
            }
            var result = formatTextUncached(mes, opts);
            if (formatCache.size >= FORMAT_CACHE_MAX) {
                formatCache.delete(formatCache.keys().next().value); // 淘汰最旧
            }
            formatCache.set(key, result);
            return result;
        }
        return formatTextUncached(mes, opts);
    }

    // ------------------------------------------------------------------
    // stream_fade_in（官方 stream-fadein.js 逐字移植 + 轻量 morphdom 语义）
    // ------------------------------------------------------------------
    function isSegmenterSupported() {
        return typeof Intl.Segmenter === 'function';
    }

    /** 官方 segmentTextInElement：Intl.Segmenter(word) 把文本节点拆 <span class="text_segment">
     *  （pre/code 内与空白节点跳过）——新插入 span 由官方 CSS 播 300ms fade-in。 */
    function segmentTextInElement(htmlElement, htmlContent, granularity) {
        htmlElement.innerHTML = htmlContent;
        if (!isSegmenterSupported()) { return; }
        var segmenter = new Intl.Segmenter('en-US', { granularity: granularity || 'word' });
        var textNodes = [];
        var walker = document.createTreeWalker(htmlElement, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) {
            var textNode = walker.currentNode;
            if (textNode.parentElement && textNode.parentElement.closest('pre, code')) { continue; }
            if (/^\s*$/.test(textNode.data)) { continue; }
            textNodes.push(textNode);
        }
        textNodes.forEach(function (textNode) {
            var fragment = document.createDocumentFragment();
            var segments = segmenter.segment(textNode.data);
            for (var seg of segments) {
                var span = document.createElement('span');
                span.innerText = seg.segment;
                span.className = 'text_segment';
                fragment.appendChild(span);
            }
            textNode.replaceWith(fragment);
        });
    }

    /** 轻量 morphdom 子节点 diff：同构节点（tag+class 或文本相等）复用 DOM——
     *  复用节点动画不重播（官方 morphdom 行为），仅新增 span 播 fade-in。 */
    function nodesEquivalent(a, b) {
        if (a.nodeType !== b.nodeType) { return false; }
        if (a.nodeType === 3) { return a.data === b.data; }
        return a.tagName === b.tagName && a.className === b.className;
    }

    function patchChildren(parent, newChildren) {
        var oldChildren = Array.prototype.slice.call(parent.childNodes);
        var i = 0;
        for (; i < newChildren.length; i++) {
            var o = oldChildren[i], n = newChildren[i];
            if (o && n && nodesEquivalent(o, n)) {
                if (o.nodeType === 1) { patchChildren(o, Array.prototype.slice.call(n.childNodes)); }
            } else if (o) {
                parent.replaceChild(n, o);
            } else {
                parent.appendChild(n); // 新增节点 → 内部 text_segment 播 fade-in
            }
        }
        for (var r = oldChildren.length - 1; r >= i; r--) {
            if (oldChildren[r].parentNode === parent) { parent.removeChild(oldChildren[r]); }
        }
    }

    /** 官方 applyStreamFadeIn：克隆 → 分词 → morphdom。此处等价实现：
     *  离屏构建新结构（含分词 span），同构前缀复用、差异替换、新增追加。 */
    function applyStreamFadeIn(messageTextElement, htmlContent) {
        var tmp = messageTextElement.cloneNode(false);
        segmentTextInElement(tmp, htmlContent);
        patchChildren(messageTextElement, Array.prototype.slice.call(tmp.childNodes));
    }

    /** 流式 tick 内核一站式入口（宿主 RenderKernel.updateStreaming 改走此处）：
     *  formatTextStreaming → fade-in / innerHTML → reasoning / timer 更新。 */
    function updateStreamingNode(cfg) {
        var m = document.querySelector('.mes[mesid="' + cfg.mesid + '"]');
        if (!m) { return; }
        var el = m.querySelector('.mes_text');
        if (el && cfg.text != null) {
            var html = formatTextUncached(cfg.text, {});
            if (window.KernelConfig.streamFadeIn && isSegmenterSupported()) {
                applyStreamFadeIn(el, html);
            } else {
                el.innerHTML = html;
            }
        }
        if (cfg.reasoning != null) {
            var rd = m.querySelector('.mes_reasoning');
            if (rd) { rd.innerHTML = formatTextUncached(cfg.reasoning, {}); }
        }
        if (cfg.timerValue != null) {
            var tm = m.querySelector('.mes_timer');
            if (tm) {
                tm.textContent = cfg.timerValue;
                if (cfg.timerTitle) { tm.setAttribute('title', cfg.timerTitle); }
            }
        }
    }

    function formatTextUncached(mes, opts) {
        opts = opts || {};
        var isSystem = !!opts.isSystem;
        var isUser = !!opts.isUser;
        var chName = opts.chName || '';

        if (!mes) {
            return '';
        }

        // 官方 messageFormatting L1775：isSystem 仅对 systemUserName（'SillyTavern System'）保持真，
        // 其余一律强制 false 走 markdown 格式化。此前误写 'System:' 导致所有系统消息都被错误格式化。
        if (isSystem && chName !== 'SillyTavern System') {
            isSystem = false;
        }

        if (window.KernelConfig.autoFixGeneratedMarkdown) {
            mes = fixMarkdown(mes, true);
        }

        if (!isSystem && window.KernelConfig.encodeTags) {
            mes = mes.replaceAll('<', '&lt;').replace(new RegExp('(?<!^|\\n\\s*)>', 'g'), '&gt;');
        }

        if (!isSystem) {
            // [官方] encode_tags 关闭时（默认）：先把标签内双引号换成 \ufffe 保护，包裹后还原
            if (!window.KernelConfig.encodeTags) {
                mes = mes.replace(/<([^>]+)>/g, function (_, contents) {
                    return '<' + contents.replace(/"/g, '\ufffe') + '>';
                });
            }

            // 引号包裹（script.js messageFormatting 内正则逐字复制）
            mes = mes.replace(
                /<style>[\s\S]*?<\/style>|```[\s\S]*?```|~~~[\s\S]*?~~~|``[\s\S]*?``|`[\s\S]*?`|(".*?")|(\u201C.*?\u201D)|(\u00AB.*?\u00BB)|(\u300C.*?\u300D)|(\u300E.*?\u300F)|(\uFF02.*?\uFF02)/gim,
                function (match, p1, p2, p3, p4, p5, p6) {
                    if (p1) { return '<q>"' + p1.slice(1, -1) + '"</q>'; }
                    else if (p2) { return '<q>\u201C' + p2.slice(1, -1) + '\u201D</q>'; }
                    else if (p3) { return '<q>\u00AB' + p3.slice(1, -1) + '\u00BB</q>'; }
                    else if (p4) { return '<q>\u300C' + p4.slice(1, -1) + '\u300D</q>'; }
                    else if (p5) { return '<q>\u300E' + p5.slice(1, -1) + '\u300F</q>'; }
                    else if (p6) { return '<q>\uFF02' + p6.slice(1, -1) + '\uFF02</q>'; }
                    return match;
                },
            );

            // [官方] 还原标签内双引号
            if (!window.KernelConfig.encodeTags) {
                mes = mes.replace(/\ufffe/g, '"');
            }

            mes = mes.replaceAll('\\begin{align*}', '$$');
            mes = mes.replaceAll('\\end{align*}', '$$');

            mes = converter.makeHtml(mes);

            mes = mes.replace(/<code(.*)>[\s\S]*?<\/code>/g, function (match) {
                return match.replace(/\n/gm, '\u0000');
            });
            mes = mes.replace(/\u0000/g, '\n');
            mes = mes.trim();

            mes = mes.replace(/<code(.*)>[\s\S]*?<\/code>/g, function (match) {
                return match.replace(/&amp;/g, '&');
            });
        }

        if (!window.KernelConfig.allowName2Display && !opts.allowName2Display && chName && !isUser && !isSystem) {
            mes = mes.replace(new RegExp('(^|\n)' + escapeRegex(chName) + ':', 'g'), '$1');
        }

        /** DOMPurify config：官方 messageFormatting 内配置逐字复制 */
        var config = {
            RETURN_DOM: false,
            RETURN_DOM_FRAGMENT: false,
            RETURN_TRUSTED_TYPE: false,
            MESSAGE_SANITIZE: true,
            ADD_TAGS: ['custom-style'],
        };
        mes = encodeStyleTags(mes);
        mes = window.DOMPurify.sanitize(mes, config);
        mes = decodeStyleTags(mes, '.mes_text ');

        return mes;
    }

    // ------------------------------------------------------------------
    // 消息模板挂载
    // ------------------------------------------------------------------
    var templateCache = null;

    function loadTemplate() {
        if (templateCache) { return Promise.resolve(templateCache); }
        // 单文件 bundle：模板已内联为 <template id="message_template_html">，零 fetch 往返；
        // 旧多文件路径（fetch official/message-template.html）保留为回退
        var inline = document.getElementById('message_template_html');
        if (inline && inline.content) {
            var el0 = inline.content.querySelector('.mes');
            if (el0) {
                var cached = el0.cloneNode(true);
                cached.removeAttribute('id');
                cached.classList.remove('template_element');
                templateCache = cached;
                return Promise.resolve(templateCache);
            }
        }
        return fetch('official/message-template.html')
            .then(function (r) { return r.text(); })
            .then(function (html) {
                var holder = document.createElement('div');
                holder.innerHTML = html.trim();
                // 官方同构：克隆模板内部的 .mes（根节点是 template_element，见 script.js L447）
                var el = holder.querySelector('.mes');
                el.removeAttribute('id');
                el.classList.remove('template_element');
                templateCache = el;
                return templateCache;
            });
    }

    /**
     * 消息指纹（renderChat 增量 diff 依据）：覆盖全部可变渲染字段——
     * 任一字段变化指纹即变，未变楼层整节点零重渲。
     */
    function hashStr(s) {
        var h = 5381;
        for (var i = 0; i < s.length; i += Math.max(1, (s.length >> 5) | 0)) {
            h = ((h << 5) + h + s.charCodeAt(i)) | 0;
        }
        return h;
    }

    function payloadFingerprint(p) {
        return [p.mesid, p.mes.length, hashStr(p.mes), p.currentSwipe || 0,
            p.reasoning ? p.reasoning.length + ':' + hashStr(p.reasoning) : '0',
            p.avatarUrl || '', p.tokenCount == null ? '' : p.tokenCount,
            p.timerValue || '', p.timestamp || '', p.ghost ? 1 : 0,
            p.lastInContext ? 1 : 0, (p.media && p.media.length) || 0,
            p.mediaDisplay || '', p.mediaIndex || 0, p.inlineImage == null ? '' : p.inlineImage,
            p.title || '', p.smallSysMes ? 1 : 0, p.toolCall ? 1 : 0,
            p.overswipe || '', p.swipeable == null ? '' : p.swipeable,
        ].join('~');
    }

    /**
     * 模板就绪后同步挂载一条消息（官方 script.js addOneMessage 同构）：
     * 填字段 → 思考块 → 追加 #chat → 高度回报与观察 → 点击与长按手势接线。
     */
    function mountMessage(tpl, payload) {
        var chat = document.getElementById('chat');
        var node = buildMessageNode(tpl, payload);
        chat.appendChild(node);
        reportHeight(payload.mesid, node.scrollHeight);
        observeHeight(node, payload.mesid);
        return node;
    }

    /** 构建单条消息节点（不含 append/高度观察）：clone 模板 → 填字段 → 绑定交互。
     *  renderChat diff ② 路径复用（insertBefore 就地替换变化楼层）。 */
    function buildMessageNode(tpl, payload) {
        var node = tpl.cloneNode(true);
        // 官方 updateMessageElement 属性面：type/bookmark_link/force_avatar/timestamp/title。
        node.setAttribute('mesid', payload.mesid);
        node.setAttribute('ch_name', payload.chName || '');
        node.setAttribute('is_user', payload.isUser ? 'true' : 'false');
        node.setAttribute('is_system', payload.isSystem ? 'true' : 'false');
        node.setAttribute('swipeid', String(Number(payload.currentSwipe || 0)));
        if (payload.type != null) { node.setAttribute('type', payload.type || ''); }
        else { node.setAttribute('type', ''); }
        if (payload.bookmarkLink) { node.setAttribute('bookmark_link', payload.bookmarkLink); }
        if (payload.forceAvatar) { node.setAttribute('force_avatar', 'true'); }
        if (payload.title) { node.setAttribute('title', payload.title); }
        if (payload.smallSysMes) { node.classList.add('smallSysMes'); }
        if (payload.toolCall) { node.classList.add('toolCall'); }

        // 官方 script.js L2646-2650：头像加载失败兜底为 missing-avatar 占位。
        // 无条件挂监听（模板空 src 不触发 error），再按 payload 下发真实 src。
        var avatarImg = node.querySelector('.mesAvatarWrapper .avatar img');
        if (avatarImg && !avatarImg.dataset.errBound) {
            avatarImg.dataset.errBound = '1';
            avatarImg.addEventListener('error', function () {
                avatarImg.style.display = 'none';
                if (avatarImg.parentElement) {
                    avatarImg.parentElement.innerHTML =
                        '<div class="missing-avatar fa-solid fa-user-slash"></div>';
                }
            });
        }
        if (payload.avatarUrl && avatarImg) {
            avatarImg.src = payload.avatarUrl;
            // Moonlit observers.js initAvatarInjector 移植：头像 URL 写成 .mes 的 CSS 变量
            // （--mes-avatar-thumb-url/--mes-avatar-original-url/--mes-avatar-url + dataset），
            // 供 style.css 消息样式（Echo 头像底图等）使用。本内核单一分辨率 →
            // thumb=original（官方 useOriginalAvatarImages 分辨率切换无对应物，语义等价）。
            var avatarCssUrl = "url('" + payload.avatarUrl + "')";
            node.dataset.avatarThumb = payload.avatarUrl;
            node.dataset.avatarOriginal = payload.avatarUrl;
            node.dataset.avatar = payload.avatarUrl;
            node.style.setProperty('--mes-avatar-thumb-url', avatarCssUrl);
            node.style.setProperty('--mes-avatar-original-url', avatarCssUrl);
            node.style.setProperty('--mes-avatar-url', avatarCssUrl);
        }
        var nameEl = node.querySelector('.name_text');
        if (nameEl) { nameEl.textContent = payload.chName || ''; }
        var tsEl = node.querySelector('.timestamp');
        if (tsEl && payload.timestamp) { tsEl.textContent = String(payload.timestamp); }
        if (tsEl && payload.apiModelTitle) { tsEl.setAttribute('title', payload.apiModelTitle); }
        var idEl = node.querySelector('.mesIDDisplay');
        if (idEl) { idEl.textContent = '#' + payload.messageIndex; }
        var tcEl = node.querySelector('.tokenCounterDisplay');
        var tokenCountText = (payload.tokenCount == null) ? '' : String(payload.tokenCount);
        if (tcEl && tokenCountText !== '') {
            tcEl.textContent = tokenCountText.endsWith('t') ? tokenCountText : tokenCountText + 't';
        }
        var timerEl = node.querySelector('.mes_timer');
        if (timerEl && payload.timerValue) {
            timerEl.textContent = payload.timerValue;
            if (payload.timerTitle) { timerEl.setAttribute('title', payload.timerTitle); }
        }

        var mesText = node.querySelector('.mes_text');
        if (mesText) {
            mesText.innerHTML = formatText(payload.mes, {
                chName: payload.chName,
                isUser: payload.isUser,
                isSystem: payload.isSystem,
            });
        }

        // 官方 addCopyToCodeBlocks（script.js L2720）：hljs + 复制按钮。
        if (mesText && window.hljs) {
            Array.prototype.forEach.call(mesText.querySelectorAll('pre code'), function (block) {
                window.hljs.highlightElement(block);
                var copyButton = document.createElement('i');
                copyButton.classList.add('fa-solid', 'fa-copy', 'code-copy', 'interactable');
                copyButton.title = 'Copy code';
                block.appendChild(copyButton);
            });
        }

        // 思考块：官方 reasoning.js updateDom 语义——.mes reasoning 类切换 + data-state 标记，
        // 内容走 messageFormatting（isReasoning 仅影响正则位点，引擎侧已提取，此处即 formatText）
        var detailsEl = node.querySelector('.mes_reasoning_details');
        if (detailsEl) {
            var reasoningText = payload.reasoning ? String(payload.reasoning).trim() : '';
            node.classList.toggle('reasoning', !!reasoningText);
            if (reasoningText) {
                node.setAttribute('data-reasoning-state', 'done');
                detailsEl.setAttribute('data-state', 'done');
                var contentEl = detailsEl.querySelector('.mes_reasoning');
                if (contentEl) {
                    contentEl.innerHTML = formatText(reasoningText, { chName: '', isUser: false, isSystem: false });
                }
            } else {
                node.removeAttribute('data-reasoning-state');
            }
        }

        // 官方 mes_ghost：隐藏消息在名字旁显示“AI 不可见”标记。
        node.classList.toggle('mes_hidden', !!payload.ghost);
        var ghostEl = node.querySelector('.mes_ghost');
        if (ghostEl) { ghostEl.style.display = payload.ghost ? '' : 'none'; }

        // 官方 setInContextMessages（script.js L6024-6042）：上下文边界虚线（border-top dotted）
        node.classList.toggle('lastInContext', !!payload.lastInContext);

        // 官方 refreshSwipeButtons / isMessageSwipeable / getOverswipeBehavior 语义
        // （script.js L9123-9249）：可滑=最后一条且非 user 且非 smallSys；
        // last_swipe=末位变体且 overswipe ∈ {regenerate, edit_generate}；
        // swipes_visible=有 >1 变体或 pristine greeting（chevron 恒显）。
        // 计数带零宽空格 formatSwipeCounter L9875：`n​/​total`。
        applySwipeClasses(node, payload);

        // 官方 appendMediaToMessage（script.js L2157-2412）移植：GALLERY 单图+切图条 /
        // LIST 全量平铺 / data-media-display 属性面 / inline_image===false 隐藏正文。
        mountMessageMedia(node, payload);

        // 运行时状态缓存：画廊切换 / 行内编辑 / 取消恢复都从这里取最近渲染载荷
        messageState[payload.mesid] = payload;
        // 增量 diff 指纹（DOM property：不进 attribute 不污染选择器）
        node.__fp = payloadFingerprint(payload);

        // 点击上报（链接/交互元素由 WebViewClient 外链逻辑处理，这里报宿主决策）
        node.addEventListener('click', function (ev) {
            // 官方 script.js L11806：操作按钮排展开是纯内核 DOM 状态，不上报宿主
            var hintEl = ev.target.closest ? ev.target.closest('.extraMesButtonsHint') : null;
            if (hintEl) { expandExtraMesButtons(hintEl); return; }
            // 官方 chats.js 委托表（L2348-2375）：画廊切图 / lightbox（.mes_img、放大镜）/ 删除媒体
            var imgSwipeEl = ev.target.closest ? ev.target.closest('.mes_img_swipe_left, .mes_img_swipe_right') : null;
            if (imgSwipeEl) {
                onImageSwiped(payload.mesid, imgSwipeEl.classList.contains('mes_img_swipe_left') ? 'left' : 'right');
                return;
            }
            var enlargeEl = ev.target.closest ? ev.target.closest('.mes_media_enlarge') : null;
            var mediaEl = ev.target.closest ? ev.target.closest('.mes_img, .mes_video') : null;
            if (enlargeEl || mediaEl) {
                expandMessageMedia(payload.mesid, mediaIndexOfTarget(ev.target, payload.mesid), !!enlargeEl);
                return;
            }
            if (ev.target.closest && ev.target.closest('.mes_media_delete')) {
                bridgeSend({
                    type: 'click',
                    mesid: payload.mesid,
                    messageAction: 'mes_media_delete',
                    value: String(mediaIndexOfTarget(ev.target, payload.mesid)),
                });
                return;
            }
            var actionEl = ev.target.closest ?
                ev.target.closest('[class*="mes_"], .swipe_left, .swipe_right, .del_checkbox') : null;
            // 官方删除模式点击整条 .mes：从该条截断到末尾；普通模式不吞消息链接点击。
            if (!actionEl && document.body.classList.contains('delete-mode')) {
                bridgeSend({ type: 'click', mesid: payload.mesid, messageAction: 'del_checkbox', target: null });
                return;
            }
            if (actionEl && actionEl !== node) {
                bridgeSend({
                    type: 'click',
                    mesid: payload.mesid,
                    messageAction: describeAction(actionEl),
                    target: describeTarget(ev.target),
                });
            } else {
                // 官方 click_to_edit gate（chats.js L2292）：有选中文本或已存在编辑框时不报正文点击
                if (document.querySelector('.edit_textarea')) { return; }
                if (window.getSelection && String(window.getSelection())) { return; }
                bridgeSend({ type: 'click', mesid: payload.mesid, target: describeTarget(ev.target) });
            }
        });

        // 长按手势（500ms，官方移动端长按菜单同款阈值）：报宿主弹出 ActionSheet
        (function () {
            var timer = null, startX = 0, startY = 0;
            node.addEventListener('touchstart', function (ev) {
                if (ev.touches.length !== 1) return;
                startX = ev.touches[0].clientX; startY = ev.touches[0].clientY;
                timer = setTimeout(function () {
                    timer = null;
                    bridgeSend({ type: 'longPress', mesid: payload.mesid, target: null });
                }, 500);
            }, { passive: true });
            node.addEventListener('touchmove', function (ev) {
                if (!timer) return;
                var dx = ev.touches[0].clientX - startX, dy = ev.touches[0].clientY - startY;
                if (dx * dx + dy * dy > 100) { clearTimeout(timer); timer = null; } // 移动超10px取消
            }, { passive: true });
            ['touchend', 'touchcancel'].forEach(function (evt) {
                node.addEventListener(evt, function () {
                    if (timer) { clearTimeout(timer); timer = null; }
                }, { passive: true });
            });
        })();

        return node;
    }

    /**
     * 渲染一条消息到 #chat（upsert：同 mesid 先移除再追加）。
     */
    function renderMessage(payload) {
        return loadTemplate().then(function (tpl) {
            var existing = document.querySelector('.mes[mesid="' + payload.mesid + '"]');
            if (existing) {
                // 池化复用下同一实例反复渲染：旧节点先解除观察，否则 ResizeObserver
                // 强持有已移除 DOM，长会话累积泄漏（官方单页无此问题）
                if (resizeObserver) { resizeObserver.unobserve(existing); }
                existing.remove();
            }
            delete messageState[payload.mesid];
            return mountMessage(tpl, payload);
        });
    }

    /**
     * 同步整个聊天（整页壳 C1）——增量 diff 三层：
     *  ① 楼层序列与指纹全同（重进同会话/流式 tick 收敛）：零 DOM 操作，直接返回；
     *  ② mesid 序列一致但部分楼层变化（编辑/新楼层/切变体）：只重建变化节点（insertBefore+remove）；
     *  ③ 序列变化（切换会话/回滚/删除）：清空全量重建（官方 printMessages 语义）。
     * 官方页签常驻不重渲；App 每次进聊天都 renderChat——diff 是"重进秒开"的核心。
     * opts.showMore：顶部挂 #show_more_messages（边界5 长聊天截断）。
     */
    function renderChat(payloads, opts) {
        return loadTemplate().then(function (tpl) {
            var chat = document.getElementById('chat');
            var list = payloads || [];
            var existing = Array.prototype.filter.call(
                chat.querySelectorAll(':scope > .mes'),
                function (n) { return true; },
            );

            // ② 序列一致性（mesid 逐位相等）——①/② 共用此判定
            var sameSeq = existing.length === list.length;
            if (sameSeq) {
                for (var i = 0; i < list.length; i++) {
                    if (existing[i].getAttribute('mesid') !== list[i].mesid) { sameSeq = false; break; }
                }
            }

            if (sameSeq) {
                // ① 快路径：指纹全同 → 零 DOM 操作
                var changed = [];
                for (var j = 0; j < list.length; j++) {
                    if (existing[j].__fp !== payloadFingerprint(list[j])) { changed.push(j); }
                }
                if (!changed.length) {
                    syncLastMesClass();
                    setShowMoreButton(!!(opts && opts.showMore));
                    return existing[list.length - 1] || null;
                }
                // ② 就地重建变化楼层（新节点建好插旧节点前再移除——避免闪烁空档）
                changed.forEach(function (idx) {
                    var oldNode = existing[idx];
                    var fresh = buildMessageNode(tpl, list[idx]);
                    chat.insertBefore(fresh, oldNode);
                    if (resizeObserver) { resizeObserver.unobserve(oldNode); }
                    oldNode.remove();
                    reportHeight(list[idx].mesid, fresh.scrollHeight);
                    observeHeight(fresh, list[idx].mesid);
                });
                syncLastMesClass();
                setShowMoreButton(!!(opts && opts.showMore));
                return lastMesNode();
            }

            // ③ 全量重建（官方 printMessages 语义）
            clearMessages();
            var last = null;
            list.forEach(function (p) { last = mountMessage(tpl, p); });
            syncLastMesClass();
            setShowMoreButton(!!(opts && opts.showMore));
            return last;
        });
    }

    function lastMesNode() {
        var all = document.querySelectorAll('#chat .mes');
        return all.length ? all[all.length - 1] : null;
    }

    function syncLastMesClass() {
        // 官方 addOneMessage：全量同步后 last_mes 恒为最后一条。
        Array.prototype.forEach.call(document.querySelectorAll('#chat .mes'), function (n, i, all) {
            n.classList.toggle('last_mes', i === all.length - 1);
        });
    }

    var resizeObserver = ('ResizeObserver' in window) ? new ResizeObserver(function (entries) {
        for (var i = 0; i < entries.length; i++) {
            var t = entries[i].target;
            bridgeSend({ type: 'heightChanged', mesid: t.getAttribute('mesid'), height: t.scrollHeight });
        }
    }) : null;

    function observeHeight(node, mesid) {
        if (resizeObserver) { resizeObserver.observe(node); }
    }

    function reportHeight(mesid, h) {
        bridgeSend({ type: 'height', mesid: mesid, height: h });
    }

    /** 清空全部消息节点：先解除高度观察再移除，防止 ResizeObserver 强持有已摘除 DOM；
     *  运行时载荷缓存与编辑态一并复位（切聊天/全量重建语义）。 */
    function clearMessages() {
        console.error('clearMessages called; stack=' + new Error().stack);
        var chat = document.getElementById('chat');
        Array.prototype.forEach.call(chat.querySelectorAll('.mes'), function (n) {
            if (resizeObserver) { resizeObserver.unobserve(n); }
            n.remove();
        });
        messageState = {};
        thisEditMesId = null;
        reasoningEditing = false;
        setShowMoreButton(false);
    }

    function describeTarget(el) {
        if (!el || el === document.body) { return null; }
        var cls = (typeof el.className === 'string') ? el.className.split(/\s+/)[0] : '';
        return cls ? { tag: el.tagName.toLowerCase(), cls: cls } : { tag: el.tagName.toLowerCase() };
    }

    // ------------------------------------------------------------------
    // 主题注入：官方 theme JSON 字段 → CSS 变量（power-user.js applyTheme 对齐）
    // ------------------------------------------------------------------
    // chat_display → body 类（0..2 官方；3..7 Moonlit Echoes 扩展布局顺延）
    function chatDisplayToClass(v) {
        switch (v) {
            case 0: return 'flatchat';
            case 1: return 'bubblechat';
            case 2: return 'documentstyle';
            case 3: return 'echostyle';
            case 4: return 'whisperstyle';
            case 5: return 'hushstyle';
            case 6: return 'ripplestyle';
            case 7: return 'tidestyle';
            default: return null;
        }
    }

    /** 官方控件动作名：点击桥专用。白名单精确匹配——按钮首类恒为通用样式 mes_button，
     *  按"第一个 mes_ 前缀"取会全错成 mes_button（全部菜单按钮装死的根因）；
     *  清单=官方 index.html 消息模板按钮类全量（L7350-7448）。 */
    var ACTION_CLASSES = [
        'swipe_left', 'swipe_right', 'del_checkbox',
        'mes_translate', 'sd_message_gen', 'mes_narrate', 'mes_prompt',
        'mes_hide', 'mes_unhide', 'mes_media_gallery', 'mes_media_list',
        'mes_embed', 'mes_swipe_picker', 'mes_create_bookmark', 'mes_create_branch',
        'mes_copy', 'mes_edit', 'mes_bookmark', 'mes_stop', 'mes_img_caption',
        'mes_edit_done', 'mes_edit_cancel', 'mes_edit_copy', 'mes_edit_add_reasoning',
        'mes_edit_delete', 'mes_edit_up', 'mes_edit_down',
        'mes_reasoning_edit', 'mes_reasoning_edit_done', 'mes_reasoning_edit_cancel',
        'mes_reasoning_delete', 'mes_reasoning_copy', 'mes_reasoning_close_all',
        'mes_img_swipe', 'mes_media_delete',
    ];
    function describeAction(el) {
        if (!el || !el.classList) { return null; }
        for (var i = 0; i < el.classList.length; i++) {
            if (ACTION_CLASSES.indexOf(el.classList[i]) !== -1) { return el.classList[i]; }
        }
        return null;
    }

    // 样式包（第三方主题整包 CSS，如 Moonlit Echoes style.css）：
    // 注入/移除 <link id="style-pack-style">，并把包内自定义变量写入 documentElement。
    // vars 键即 CSS 自定义属性名（可带或不带 -- 前缀），值一律字符串化。
    // extensionHref 可选：上游以扩展形式安装时全局加载的 extension.css（兼容层），
    // 存在即一并注入 <link id="style-pack-extension">——与官方「装了扩展就载它的 css」行为对齐。
    var STYLE_PACK_LINK_ID = 'style-pack-style';
    var STYLE_PACK_EXT_LINK_ID = 'style-pack-extension';
    function syncPackLink(id, href) {
        var head = document.head;
        var existing = document.getElementById(id);
        if (!href) {
            if (existing) { existing.remove(); }
            return;
        }
        if (!existing) {
            existing = document.createElement('link');
            existing.id = id;
            existing.rel = 'stylesheet';
            head.appendChild(existing);
        }
        if (existing.getAttribute('href') !== href) {
            existing.setAttribute('href', href);
        }
    }
    /**
     * 样式包应用：style.css/extension.css 注入 + preset 变量下发。
     * 变量写入与官方 Moonlit theme-applier.js 逐字一致——<style id="dynamic-theme-styles">
     * 整体替换写 `--varId: value !important`（官方 applyAllThemeSettings 同构），
     * 不用 root.style.setProperty（残留旧键：换包/恢复默认后旧变量不清除）。
     * cssBlocks：checkbox 型设置启用时注入的内嵌 CSS（官方 index.js updateCheckboxStyles
     * 语义——`<style id="css-block-${varId}">`，值为 true 写入、false 清空）。
     */
    var STYLE_PACK_VARS_EL_ID = 'dynamic-theme-styles';
    function applyStylePackVars(vars) {
        var el = document.getElementById(STYLE_PACK_VARS_EL_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = STYLE_PACK_VARS_EL_ID;
            document.head.appendChild(el);
        }
        var css = ':root {\n';
        Object.keys(vars || {}).forEach(function (key) {
            var name = key.charAt(0) === '-' ? key : '--' + key;
            css += '  ' + name + ': ' + vars[key] + ' !important;\n';
        });
        css += '}';
        el.textContent = css;
    }

    function applyStylePackCssBlocks(vars, cssBlocks) {
        Object.keys(cssBlocks || {}).forEach(function (varId) {
            var id = 'css-block-' + varId;
            var el = document.getElementById(id);
            var enabled = !!(vars && vars[varId] === true || vars && vars[varId] === 'true');
            if (!enabled) {
                if (el) { el.textContent = ''; }
                return;
            }
            if (!el) {
                el = document.createElement('style');
                el.id = id;
                document.head.appendChild(el);
            }
            el.textContent = cssBlocks[varId];
        });
    }

    /**
     * 官方 index.js applyRawCustomCss：rawCustomCss（raw-css 类 textarea 设置）文本
     * 原样注入 <style id="moonlit-raw-css">——无过滤（"raw, unfiltered, full control"
     * 语义，@import 自定义字体等全支持）。官方初始化 + themeSettingChanged(rawCustomCss)
     * 双入口，我方随 applyStylePack 全量重放同效。theme-applier 同时把它写进 :root
     * 变量块（官方行为，兼容保留）。包停用时清空——我方样式包随主题生效（非官方
     * "扩展全局常驻"），避免自定义 CSS 跨主题泄漏。
     */
    var STYLE_PACK_RAW_EL_ID = 'moonlit-raw-css';
    function applyStylePackRawCss(vars) {
        var el = document.getElementById(STYLE_PACK_RAW_EL_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = STYLE_PACK_RAW_EL_ID;
            document.head.appendChild(el);
        }
        var text = vars && typeof vars.rawCustomCss === 'string' ? vars.rawCustomCss : '';
        el.textContent = text;
    }

    /**
     * 扩展 JS 注入（官方 extensions.js addExtensionScript 同构）：
     * <script type="module" async> 挂 body 末尾，id 按扩展目录名防重。
     * 禁用时移除标签——module 顶层副作用无法内联撤销（官方同样依赖 reload），
     * 宿主对 js 型扩展的启停需重建内核实例，CSS/变量型则即时生效。
     */
    var EXT_JS_EL_PREFIX = 'extension-script-';
    function syncExtensionScript(packId, src) {
        if (!packId) { return; }
        var el = document.getElementById(EXT_JS_EL_PREFIX + packId);
        if (!src) {
            if (el) { el.remove(); }
            return;
        }
        if (el) { return; }
        el = document.createElement('script');
        el.id = EXT_JS_EL_PREFIX + packId;
        el.type = 'module';
        el.async = true;
        el.src = src;
        document.body.appendChild(el);
    }

    function applyStylePack(cfg) {
        cfg = cfg || {};
        if (!cfg.enabled || !cfg.href) {
            syncPackLink(STYLE_PACK_LINK_ID, null);
            syncPackLink(STYLE_PACK_EXT_LINK_ID, null);
            applyStylePackVars({});
            applyStylePackCssBlocks({}, cfg && cfg.cssBlocks);
            applyStylePackRawCss(null);
            syncExtensionScript(cfg.packId, null);
            return;
        }
        syncPackLink(STYLE_PACK_LINK_ID, cfg.href);
        syncPackLink(STYLE_PACK_EXT_LINK_ID, cfg.extensionHref || null);
        applyStylePackVars(cfg.vars || {});
        applyStylePackCssBlocks(cfg.vars || {}, cfg.cssBlocks);
        applyStylePackRawCss(cfg.vars || {});
        syncExtensionScript(cfg.packId, cfg.js || null);
    }

    function applyTheme(theme) {
        var root = document.documentElement;
        function set(k, v) { root.style.setProperty(k, v); }

        // 官方默认值兜底（power-user.js power_user 初始值）：主题 JSON 缺字段时按官方默认
        // 生效，而不是"不加类"——手写/裁剪 JSON 的行为才能与官方一致。
        var OFFICIAL_DEFAULTS = {
            blur_strength: 10,
            shadow_width: 2,
            font_scale: 1,
            fast_ui_mode: true,
            waifuMode: false,
            avatar_style: 0,
            chat_display: 0,
            noShadows: false,
            chat_width: 50,
            timer_enabled: true,
            timestamps_enabled: true,
            timestamp_model_icon: false,
            mesIDDisplay_enabled: false,
            hideChatAvatars_enabled: false,
            message_token_count_enabled: false,
            expand_message_actions: false,
            enableZenSliders: false,
            enableLabMode: false,
            hotswap_enabled: true,
            reduced_motion: false,
            compact_input_area: false,
            show_swipe_num_all_messages: false,
        };
        function val(k) { return (k in theme) ? theme[k] : OFFICIAL_DEFAULTS[k]; }

        var mainColor = val('main_text_color');
        if (mainColor) {
            set('--SmartThemeBodyColor', mainColor);
            var m = String(mainColor).match(/\(([^)]+)\)/);
            if (m) {
                var parts = m[1].split(',');
                set('--SmartThemeCheckboxBgColorR', parts[0]);
                set('--SmartThemeCheckboxBgColorG', parts[1]);
                set('--SmartThemeCheckboxBgColorB', parts[2]);
                set('--SmartThemeCheckboxBgColorA', parts[3]);
            }
        }
        var italicsColor = val('italics_text_color'); if (italicsColor) set('--SmartThemeEmColor', italicsColor);
        var underlineColor = val('underline_text_color'); if (underlineColor) set('--SmartThemeUnderlineColor', underlineColor);
        var quoteColor = val('quote_text_color'); if (quoteColor) set('--SmartThemeQuoteColor', quoteColor);
        var blurTintColor = val('blur_tint_color'); if (blurTintColor) set('--SmartThemeBlurTintColor', blurTintColor);
        var chatTintColor = val('chat_tint_color'); if (chatTintColor) set('--SmartThemeChatTintColor', chatTintColor);
        var userMesTint = val('user_mes_blur_tint_color'); if (userMesTint) set('--SmartThemeUserMesBlurTintColor', userMesTint);
        var botMesTint = val('bot_mes_blur_tint_color'); if (botMesTint) set('--SmartThemeBotMesBlurTintColor', botMesTint);
        var shadowColor = val('shadow_color'); if (shadowColor) set('--SmartThemeShadowColor', shadowColor);
        var borderColor = val('border_color'); if (borderColor) set('--SmartThemeBorderColor', borderColor);
        set('--blurStrength', String(val('blur_strength')));
        set('--shadowWidth', String(val('shadow_width')));
        set('--fontScale', String(val('font_scale')));
        set('--sheldWidth', val('chat_width') + 'vw');

        // custom_css 直接注入（官方 applyCustomCSS 同构；缺字段=空串=无自定义）
        var customCss = ('custom_css' in theme) ? (theme.custom_css || '') : '';
        var style = document.getElementById('custom-style');
        if (style) { style.innerHTML = customCss; }

        // 官方 compact_input_area → #send_form.compact（power-user.js switchCompactInputArea L529-532）
        var sendForm = document.getElementById('send_form');
        if (sendForm) { sendForm.classList.toggle('compact', !!val('compact_input_area')); }

        // 开关型字段 → body 类同步（与 power-user.js applyPowerUserSettings 逐项同构）
        // 官方语义：每次应用全量同步，先移除后按当前值（含缺字段时的官方默认值）添加
        var classSync = [
            // [字段, 类名, 取值语义]  true=真时加类 / inverted=假时加类 / value=枚举映射
            ['fast_ui_mode', 'no-blur', 'true'],
            ['noShadows', 'noShadows', 'true'],
            ['waifuMode', 'waifuMode', 'true'],
            ['reduced_motion', 'reduced-motion', 'true'],
            ['timestamps_enabled', 'no-timestamps', 'inverted'],
            ['timer_enabled', 'no-timer', 'inverted'],
            ['message_token_count_enabled', 'no-tokenCount', 'inverted'],
            ['mesIDDisplay_enabled', 'no-mesIDDisplay', 'inverted'],
            ['timestamp_model_icon', 'no-modelIcons', 'inverted'],
            ['hotswap_enabled', 'no-hotswap', 'inverted'],
            ['hideChatAvatars_enabled', 'hideChatAvatars', 'true'],
            ['expand_message_actions', 'expandMessageActions', 'true'],
            ['show_swipe_num_all_messages', 'swipeAllMessages', 'true'],
            ['enableZenSliders', 'enableZenSliders', 'true'],
            // 官方 L549：enableLabMode 直接以 body 类生效（bogus_folders 无 CSS 类，纯 Web 壳逻辑）
            ['enableLabMode', 'enableLabMode', 'true'],
        ];
        var managedClasses = ['no-blur','noShadows','waifuMode','reduced-motion','no-timestamps','no-timer','no-tokenCount','no-mesIDDisplay','no-modelIcons','no-hotswap','hideChatAvatars','expandMessageActions','swipeAllMessages','enableZenSliders','enableLabMode','big-avatars','square-avatars','rounded-avatars','bubblechat','documentstyle','flatchat','echostyle','whisperstyle','hushstyle','tidestyle','ripplestyle'];
        managedClasses.forEach(function (cls) { document.body.classList.remove(cls); });
        classSync.forEach(function (item) {
            var field = item[0], cls = item[1], mode = item[2];
            var v = val(field);
            var on = mode === 'true' ? !!v : !v;
            if (on) document.body.classList.add(cls);
        });
        // 头像形状：avatar_style 0 圆 / 1 矩形大头像 / 2 方形 / 3 圆角（官方 avatar_styles 枚举 L95-100）
        var avatarStyle = val('avatar_style');
        if (avatarStyle === 1) document.body.classList.add('big-avatars');
        else if (avatarStyle === 2) document.body.classList.add('square-avatars');
        else if (avatarStyle === 3) document.body.classList.add('rounded-avatars');
        // 消息布局：chat_display 0 平铺 / 1 气泡(bubblechat) / 2 文档(documentstyle)；
        // 3..7 = Moonlit Echoes 扩展布局，映射已对上游扩展 index.js initChatDisplaySwitcher
        // 逐项核实（3=echostyle/4=whisperstyle/5=hushstyle/6=ripplestyle/7=tidestyle），
        // 全量同步语义与上游一致。样式包未加载时这些类为惰性。
        var layoutClass = chatDisplayToClass(val('chat_display'));
        if (layoutClass) { document.body.classList.add(layoutClass); }

        bridgeSend({ type: 'themeApplied' });
    }

    // ------------------------------------------------------------------
    // Bridge（Android 侧注入 window.AndroidKernel）
    // ------------------------------------------------------------------
    function bridgeSend(obj) {
        if (window.AndroidKernel && typeof window.AndroidKernel.postMessage === 'function') {
            try { window.AndroidKernel.postMessage(JSON.stringify(obj)); } catch (e) { /* no-op */ }
        }
    }

    /** 运行时配置下发（宿主 BehaviorPrefs 全量同步；新实例随 applyPageSetup 重放）：
     *  cfg 键与 KernelConfig 运行时段一致，缺省键不动当前值。
     *  变化时：清格式化缓存 + 已挂载消息就地重刷正文（官方"改设置重载 processor/聊天"
     *  的即时生效语义，避免依赖宿主重发 payloads 的时序）。 */
    function setRuntimeConfig(cfg) {
        if (!cfg) { return; }
        var changed = false;
        ['streamFadeIn', 'gestures', 'sendOnEnter', 'quickContinue', 'quickImpersonate', 'autoSaveEdits', 'markdownEscapeStrings', 'trimSpaces'].forEach(function (k) {
            if (k in cfg && window.KernelConfig[k] !== cfg[k]) {
                window.KernelConfig[k] = cfg[k];
                changed = true;
            }
        });
        if (changed) {
            kernelConfigRevision += 1;
            formatCache.clear();
            reformatMountedMessages();
        }
        syncQuickButtons(); // quick_continue/quick_impersonate 按钮即时联动
    }

    /** 已挂载消息正文就地重格式化（buildMessageNode 正文段同构 + hljs/复制按钮）。 */
    function reformatMountedMessages() {
        Object.keys(messageState).forEach(function (mesid) {
            var payload = messageState[mesid];
            if (!payload || payload.mes == null) { return; }
            var node = document.querySelector('.mes[mesid="' + cssEsc(mesid) + '"]');
            if (!node) { return; }
            var mesText = node.querySelector('.mes_text');
            if (!mesText) { return; }
            mesText.innerHTML = formatText(payload.mes, {
                chName: payload.chName,
                isUser: payload.isUser,
                isSystem: payload.isSystem,
            });
            if (window.hljs) {
                Array.prototype.forEach.call(mesText.querySelectorAll('pre code'), function (block) {
                    window.hljs.highlightElement(block);
                    if (!block.querySelector('.code-copy')) {
                        var copyButton = document.createElement('i');
                        copyButton.classList.add('fa-solid', 'fa-copy', 'code-copy', 'interactable');
                        copyButton.title = 'Copy code';
                        block.appendChild(copyButton);
                    }
                });
            }
            reportHeight(mesid, node.scrollHeight);
        });
    }

    // ------------------------------------------------------------------
    // 官方 scrollChatToBottom + scrollLock 逐字语义移植（script.js L2714/L11167）。
    // auto_scroll_chat_to_bottom 官方默认 true；waifuMode 时官方强制锁定。
    // ------------------------------------------------------------------
    var scrollRequestId = null;
    var scrollLock = false;

    function scrollToBottom(waitForFrame) {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        var doScroll = function () {
            var position = chat.scrollHeight;
            if (document.body.classList.contains('waifuMode')) {
                var lastMessage = chat.querySelector('.mes:last-of-type');
                if (lastMessage) {
                    position = chat.scrollTop + lastMessage.getBoundingClientRect().top - chat.getBoundingClientRect().top;
                }
            }
            chat.scrollTop = position;
            scrollRequestId = null;
        };
        if (scrollRequestId !== null) {
            cancelAnimationFrame(scrollRequestId);
        }
        if (!waitForFrame) {
            doScroll();
            return;
        }
        scrollRequestId = requestAnimationFrame(doScroll);
    }

    (function watchChatScroll() {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        var scrollReportTimer = null, lastScrollReport = 0;
        chat.addEventListener('scroll', function () {
            if (document.body.classList.contains('waifuMode')) {
                scrollLock = true;
                bridgeSend({ type: 'chatScroll', atBottom: atBottom });
                return;
            }
            // 官方贴底容差 <5px；贴底解锁、离开锁定。
            var atBottom = Math.abs(chat.scrollHeight - chat.clientHeight - chat.scrollTop) < 5;
            if (scrollLock && atBottom) { scrollLock = false; }
            if (!scrollLock && !atBottom) { scrollLock = true; }
            // 限流 50ms：此状态只驱动跳底浮标，每帧过桥在低端机上挤占主线程（滚动顿挫主嫌）
            var nowTs = Date.now();
            if (!scrollReportTimer && nowTs - lastScrollReport > 50) {
                lastScrollReport = nowTs;
                bridgeSend({ type: 'chatScroll', atBottom: !scrollLock });
            } else if (!scrollReportTimer) {
                scrollReportTimer = setTimeout(function () {
                    scrollReportTimer = null;
                    lastScrollReport = Date.now();
                    bridgeSend({ type: 'chatScroll', atBottom: !scrollLock });
                }, 50);
            }
        }, { passive: true });
    })();

    // ------------------------------------------------------------------
    // 官方输入区（#form_sheld）管理（C3）
    // RA_checkOnlineStatus connected 分支 / showStopButton / openMessageDelete 同构；
    // 控件点击经 hostRequest 桥接宿主执行，草稿真值在宿主侧。
    // ------------------------------------------------------------------
    var deleteModeActive = false;
    /** hideSwipeButtons/showSwipeButtons 合成语义：生成中或删除模式隐藏 chevron
     *  （script.js refreshSwipeButtons 的 body.hideAllSwipeButtons + openMessageDelete 调用点） */
    function syncHideSwipes() {
        var generating = document.body.getAttribute('data-generating') === 'true';
        document.body.classList.toggle('hideAllSwipeButtons', generating || deleteModeActive);
    }

    // 官方 script.js L11806-11834：点省略号展开 .extraMesButtons（hint 隐藏、按钮排 display:flex）。
    // 动画由官方 CSS transition（.mes_buttons all var(--animation-duration-2x)）承担，这里只切状态。
    function expandExtraMesButtons(hint) {
        var buttons = hint.parentElement.querySelector('.extraMesButtons');
        if (!buttons || buttons.classList.contains('visible')) { return; }
        hint.style.display = 'none';
        buttons.classList.add('visible');
        buttons.style.display = 'flex';
    }

    // 官方 script.js L11835-11868：点击按钮排/省略号以外区域收起全部已展开排；
    // expand_message_actions（body.expandMessageActions）开启时按钮排常显，不参与收起。
    function initExtraMesButtonsOutsideClose() {
        document.addEventListener('click', function (ev) {
            if (document.body.classList.contains('expandMessageActions')) { return; }
            if (ev.target.closest && ev.target.closest('.extraMesButtons, .extraMesButtonsHint')) { return; }
            var visible = document.querySelectorAll('.extraMesButtons.visible');
            for (var i = 0; i < visible.length; i++) {
                visible[i].classList.remove('visible');
                visible[i].style.display = 'none';
            }
            var hints = document.querySelectorAll('.extraMesButtonsHint');
            for (var j = 0; j < hints.length; j++) { hints[j].style.display = ''; }
        });
    }

    function initInputArea() {
        var form = document.getElementById('send_form');
        if (!form || form.dataset.inputBooted) { return; }
        form.dataset.inputBooted = '1';
        // 连接态：EmberInn 恒为已连接（RA_checkOnlineStatus connected 分支 L339-348：
        // 去 no-connection；send_but 去 displayNone——mes_continue/mes_impersonate
        // 显隐改由官方 quick_continue/quick_impersonate 开关经 syncQuickButtons 控制）
        form.classList.remove('no-connection');
        var sendBut = document.getElementById('send_but');
        if (sendBut) { sendBut.classList.remove('displayNone'); }
        // 控件 → 宿主动作（官方各 click 处理器的桥接等价）
        function on(id, action) {
            var el = document.getElementById(id);
            if (el) { el.addEventListener('click', function () { bridgeSend({ type: 'hostRequest', hostAction: action }); }); }
        }
        on('send_but', 'chat_send');
        on('mes_stop', 'chat_interrupt');
        on('options_button', 'chat_options');
        on('attach_button', 'chat_attach');
        on('mes_impersonate', 'chat_impersonate');
        on('mes_continue', 'chat_continue');
        on('dialogue_del_mes_ok', 'chat_delete_confirm');
        on('dialogue_del_mes_cancel', 'chat_delete_cancel');
        var ta = document.getElementById('send_textarea');
        if (ta) {
            ta.addEventListener('input', function () {
                bridgeSend({ type: 'inputChanged', text: ta.value });
            });
            // 官方 send_on_enter 三态（power-user.js L149-162 + index.html #send-on-enter）：
            // -1 AUTO / 0 关 / 1 开。官方 shouldSendOnEnter：AUTO 在移动端(isMobile)不发送——
            // App 恒移动端，故 AUTO 等价关。Enter+Shift 换行不受影响。
            ta.addEventListener('keydown', function (e) {
                if (e.key !== 'Enter' || e.shiftKey || e.isComposing) { return; }
                if (window.KernelConfig.sendOnEnter === 1) {
                    e.preventDefault();
                    bridgeSend({ type: 'hostRequest', hostAction: 'chat_send' });
                }
            });
        }
        // quick_continue / quick_impersonate（官方默认 false → displayNone；
        // initInputArea 此前恒显——改为按官方开关同步，宿主 setRuntimeConfig 联动）
        syncQuickButtons();
        // 官方 power_user.gestures（swiped-events 语义）：消息横滑切变体。
        // 阈值对齐 swiped-events 默认：dx>60px 且 |dy| < dx，300ms 内。
        initMessageSwipeGestures();
        // #form_sheld 高度回报（原内联块提出为函数）
        reportFormSheldHeight();
    }

    /** 官方 quick_continue/quick_impersonate 按钮显隐（index.html 初始 displayNone + power-user.js 控制） */
    function syncQuickButtons() {
        var cont = document.getElementById('mes_continue');
        var imp = document.getElementById('mes_impersonate');
        if (cont) { cont.classList.toggle('displayNone', !window.KernelConfig.quickContinue); }
        if (imp) { imp.classList.toggle('displayNone', !window.KernelConfig.quickImpersonate); }
    }

    /** 消息横滑切变体（官方 swiped-left/right → swipe_left_right_handler）：
     *  touchstart/touchend 位移判定，滑动目标 .mes 的 mesid 经 messageAction 桥回宿主。 */
    function initMessageSwipeGestures() {
        var chat = document.getElementById('chat');
        if (!chat || chat.dataset.swipeBound) { return; }
        chat.dataset.swipeBound = '1';
        var startX = 0, startY = 0, startT = 0, targetMes = null;
        chat.addEventListener('touchstart', function (ev) {
            if (!window.KernelConfig.gestures || ev.touches.length !== 1) {
                targetMes = null;
                return;
            }
            startX = ev.touches[0].clientX;
            startY = ev.touches[0].clientY;
            startT = Date.now();
            var mes = ev.target.closest ? ev.target.closest('.mes') : null;
            // 官方仅 swipe 箭头所在消息（可滑=末条 AI 消息）滑动手势生效；生成中不触发
            targetMes = (mes && !document.body.classList.contains('hideAllSwipeButtons')) ? mes : null;
        }, { passive: true });
        chat.addEventListener('touchend', function (ev) {
            if (!targetMes) { return; }
            var t = ev.changedTouches[0];
            var dx = t.clientX - startX, dy = t.clientY - startY;
            var dt = Date.now() - startT;
            var mesid = targetMes.getAttribute('mesid');
            targetMes = null;
            if (dt > 500 || Math.abs(dx) < 60 || Math.abs(dx) < Math.abs(dy)) { return; }
            // 边缘手势留给系统返回（SwipeBack 边缘判定在内核无法感知，窄边 24px 放行宿主）
            var w = window.innerWidth;
            if (startX < 24 || startX > w - 24) { return; }
            bridgeSend({
                type: 'click',
                mesid: mesid,
                messageAction: dx < 0 ? 'swipe_left' : 'swipe_right',
                target: null,
            });
        }, { passive: true });
    }

    /** #form_sheld 高度回报：原生悬浮附件/快捷回复行动态内边距（CSS px ≈ Compose dp）。
     *  同时写 --formSheldHeight CSS 变量（Moonlit observers.js initFormSheldHeightMonitor
     *  语义：样式包 CSS 按输入区高度布局，如固定菜单高度/视口补偿）。 */
    function reportFormSheldHeight() {
        var sheld = document.getElementById('form_sheld');
        if (sheld && 'ResizeObserver' in window) {
            var reportFormHeight = function () {
                bridgeSend({ type: 'inputHeight', height: sheld.offsetHeight });
                var h = sheld.offsetHeight;
                if (h > 0) {
                    document.documentElement.style.setProperty('--formSheldHeight', h + 'px');
                }
            };
            new ResizeObserver(reportFormHeight).observe(sheld);
            requestAnimationFrame(reportFormHeight);
        }
    }

    /** 官方写法同构：$('#send_textarea').val(x)[0].dispatchEvent(new Event('input',{bubbles:true})) */
    function setInputText(text) {
        var ta = document.getElementById('send_textarea');
        if (!ta) { return; }
        ta.value = (text == null) ? '' : String(text);
        ta.dispatchEvent(new Event('input', { bubbles: true }));
    }

    /**
     * 生成/滑动状态（deactivateSendButtons/unblockGeneration 的 DOM 面）：
     * body[data-generating]/[data-swiping] 经 style.css 隐藏 send_but/mes_continue/
     * mes_impersonate 与 last_mes 按钮排；showStopButton/hideStopButton 切 #mes_stop。
     */
    function setInputState(state) {
        state = state || {};
        if ('generating' in state) {
            document.body.setAttribute('data-generating', state.generating ? 'true' : 'false');
            var stop = document.getElementById('mes_stop');
            if (stop) { stop.style.display = state.generating ? 'flex' : 'none'; }
            syncHideSwipes();
        }
        if ('swiping' in state) {
            document.body.setAttribute('data-swiping', state.swiping ? 'true' : 'false');
        }
    }

    // ------------------------------------------------------------------
    // 官方删除模式（openMessageDelete / dialogue_del_mes cancel-ok 的 DOM 状态）
    // ------------------------------------------------------------------
    function setDeleteMode(enabled) {
        var chat = document.getElementById('chat');
        if (chat) {
            Array.prototype.forEach.call(chat.querySelectorAll('.mes'), function (node) {
                var checkbox = node.querySelector(':scope > .del_checkbox');
                var forBox = node.querySelector(':scope > .for_checkbox');
                if (!checkbox || !forBox) { return; }
                checkbox.style.display = enabled ? 'grid' : 'none';
                forBox.style.display = enabled ? 'none' : 'block';
                if (!enabled) {
                    node.classList.remove('selected');
                    checkbox.checked = false;
                }
            });
        }
        document.body.classList.toggle('delete-mode', !!enabled);
        // 官方 openMessageDelete：确认条显示、输入表单隐藏；取消/确认恢复样式表默认 display
        var dlg = document.getElementById('dialogue_del_mes');
        if (dlg) { dlg.style.display = enabled ? 'block' : 'none'; }
        var form = document.getElementById('send_form');
        if (form) { form.style.display = enabled ? 'none' : ''; }
        deleteModeActive = !!enabled;
        syncHideSwipes();
    }

    function selectDeleteFrom(mesid) {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        var selectedId = Number(mesid);
        Array.prototype.forEach.call(chat.querySelectorAll('.mes'), function (node) {
            var id = Number(node.getAttribute('mesid'));
            var checkbox = node.querySelector(':scope > .del_checkbox');
            var on = !isNaN(selectedId) && id >= selectedId;
            node.classList.toggle('selected', on);
            if (checkbox) { checkbox.checked = on; }
        });
    }

    // ------------------------------------------------------------------
    // C4 官方背景（backgrounds.js onChatChanged 同构）：#bg1 background-image；
    // 会话级锁定 > 全局背景由宿主解析后下发。blur/染色不在此做——官方语义是
    // #sheld/#send_form 的 backdrop-filter 消费 --SmartThemeBlurStrength。
    // ------------------------------------------------------------------
    function setBackground(url, fitting) {
        var bg = document.getElementById('bg1');
        if (!bg) { return; }
        bg.style.backgroundImage = url ? 'url("' + String(url).replace(/"/g, '\\"') + '")' : 'none';
        bg.classList.remove('cover', 'contain', 'stretch', 'center');
        if (fitting) { bg.classList.add(String(fitting)); }
    }

    // ------------------------------------------------------------------
    // 边界1 画廊 / 边界2 lightbox / 边界5 show more / 边界3 行内编辑
    // 官方基线：chats.js appendMediaToMessage/onImageSwiped/expandMessageMedia、
    // script.js printMessages/showMoreMessages/messageEdit*、popup.js 弹层机制
    // ------------------------------------------------------------------

    /** mesid → 最近渲染载荷（画廊切换/行内编辑/取消恢复的数据源） */
    var messageState = {};
    /** 编辑态互斥（官方模块级 this_edit_mes_id，script.js L610；无 .editing 类） */
    var thisEditMesId = null;
    var thisEditMesChname = '';
    var reasoningEditing = false;

    function numericMesid(mesid) {
        var n = Number(String(mesid).replace(/^m-/, ''));
        return isNaN(n) ? -1 : n;
    }

    function maxMessageIndex() {
        var max = -1;
        Object.keys(messageState).forEach(function (mid) {
            var idx = Number(messageState[mid].messageIndex);
            if (!isNaN(idx) && idx > max) { max = idx; }
        });
        return max;
    }

    function getMediaDisplayOf(payload) {
        return payload.mediaDisplay === 'gallery' ? 'gallery' : 'list';
    }

    function mediaIndexOfTarget(target, mesid) {
        var container = target.closest ? target.closest('.mes_media_container') : null;
        if (!container) { return 0; }
        var idx = Number(container.getAttribute('data-index'));
        return isNaN(idx) ? 0 : idx;
    }

    function clampMediaIndex(payload) {
        var len = (payload.media || []).length;
        var idx = Number(payload.mediaIndex || 0);
        if (isNaN(idx) || idx < 0) { idx = 0; }
        if (len > 0 && idx > len - 1) { idx = len - 1; }
        return idx;
    }

    /** 单个媒体块：官方 appendMediaAttachment 三分支（script.js L2218-2300）——
     *  image/video/audio 各自 clone 官方模板，data-index/src/title、loadeddata→error 类，
     *  audio 另行实例化原版 AudioPlayer（scripts/audio-player.js 原样内联于 kernel.js 载荷）。 */
    function buildMediaBlock(item, index, extraTitle) {
        var block;
        if (item.type === 'video') {
            // 官方 appendVideoAttachment（L2231-2258）
            var tplVideo = document.getElementById('message_video_template');
            if (tplVideo) {
                block = tplVideo.querySelector('.mes_video_container').cloneNode(true);
            } else {
                block = document.createElement('div');
                block.className = 'mes_media_container mes_video_container';
                var vOnly = document.createElement('video');
                vOnly.className = 'mes_video';
                vOnly.controls = true;
                vOnly.setAttribute('preload', 'metadata');
                block.appendChild(vOnly);
            }
            block.setAttribute('data-index', String(index));
            var video = block.querySelector('.mes_video');
            video.setAttribute('src', item.url);
            video.setAttribute('title', item.title || extraTitle || ''); // L2240: attachment.title || mes.extra.title || ''
            var vFail = function () { video.classList.add('error'); };
            if (video.readyState >= 2 /* HAVE_CURRENT_DATA */) { /* loaded */ }
            else {
                video.addEventListener('loadeddata', function () { /* onLoad: no-op resolve */ });
                video.addEventListener('error', vFail);
            }
        } else if (item.type === 'audio') {
            // 官方 appendAudioAttachment（L2263-2300）：模板 clone + AudioPlayer 实例化
            var tplAudio = document.getElementById('message_audio_template');
            if (tplAudio) {
                block = tplAudio.querySelector('.mes_audio_container').cloneNode(true);
            } else {
                block = document.createElement('div');
                block.className = 'mes_media_container mes_audio_container audio-player';
                var aOnly = document.createElement('audio');
                aOnly.className = 'mes_audio';
                aOnly.setAttribute('preload', 'auto');
                aOnly.setAttribute('hidden', '');
                block.appendChild(aOnly);
            }
            block.setAttribute('data-index', String(index));
            var audioEl = block.querySelector('.mes_audio');
            audioEl.setAttribute('src', item.url);
            audioEl.setAttribute('title', item.title || extraTitle || ''); // L2271
            var aFail = function () { audioEl.classList.add('error'); };
            if (audioEl.readyState >= 2 /* HAVE_CURRENT_DATA */) { /* loaded */ }
            else {
                audioEl.addEventListener('loadeddata', function () { /* onLoad */ });
                audioEl.addEventListener('error', aFail);
            }
            if (window.AudioPlayer) { new window.AudioPlayer(audioEl, block); }
        } else {
            // 图片走官方模板 clone（含 mes_img_controls 放大/Caption/删除三键）
            var tplImg = document.getElementById('message_image_template');
            if (tplImg) {
                block = tplImg.querySelector('.mes_img_container').cloneNode(true);
            } else {
                block = document.createElement('div');
                block.className = 'mes_media_container mes_img_container';
                var imgOnly = document.createElement('img');
                imgOnly.className = 'mes_img';
                block.appendChild(imgOnly);
            }
            block.setAttribute('data-index', String(index));
            var img = block.querySelector('.mes_img');
            img.setAttribute('src', item.url);
            img.setAttribute('title', item.title || extraTitle || ''); // L2204
            // 官方 load/error 状态（L2207-2216）：成功去 alt/.error，失败置 alt=''+.error
            var settle = function () { img.removeAttribute('alt'); img.classList.remove('error'); };
            var fail = function () { img.setAttribute('alt', ''); img.classList.add('error'); };
            if (img.complete && img.naturalWidth > 0) { settle(); }
            else {
                img.addEventListener('load', settle);
                img.addEventListener('error', fail);
            }
        }
        return block;
    }

    /** appendMediaToMessage 移植：GALLERY 只挂当前图+切图条（单图也渲染 "1/1"），
     *  LIST 全量平铺不带 img_swipes 类；无媒体时移除 data-media-display。 */
    function mountMessageMedia(node, payload) {
        var wrapper = node.querySelector('.mes_media_wrapper');
        if (!wrapper) { return; }
        wrapper.innerHTML = '';
        var media = payload.media || [];
        var hasMedia = media.length > 0;

        // jQuery attr(null) 语义：无媒体移除属性（style.css L647 display 切换按钮随之隐藏）
        if (hasMedia) {
            node.setAttribute('data-media-display', getMediaDisplayOf(payload));
        } else {
            node.removeAttribute('data-media-display');
        }

        if (hasMedia && getMediaDisplayOf(payload) === 'gallery') {
            var mediaIndex = clampMediaIndex(payload);
            var selected = media[mediaIndex];
            var block = buildMediaBlock(selected, mediaIndex, payload.extraTitle);
            block.classList.add('img_swipes'); // script.js L2377：GALLERY 容器加 img_swipes
            var tplGallery = document.getElementById('message_gallery_controls');
            if (tplGallery) {
                var controls = tplGallery.querySelector('.mes_img_swipes').cloneNode(true);
                controls.querySelector('.mes_img_swipe_counter').textContent =
                    (mediaIndex + 1) + '/' + media.length; // 计数格式 `${i+1}/${n}`（L2374）
                block.appendChild(controls);
            }
            wrapper.appendChild(block);
        } else if (hasMedia) {
            media.forEach(function (item, index) {
                wrapper.appendChild(buildMediaBlock(item, index, payload.extraTitle));
            });
        }

        // inline_image === false 时隐藏正文（style.css .mes_text.inline_media:not(:has(.edit_textarea))）
        var textEl = node.querySelector('.mes_text');
        if (textEl) {
            textEl.classList.toggle('inline_media', hasMedia && payload.inlineImage === false);
        }
    }

    /** 画廊左右切换（chats.js onImageSwiped L2061-2102）：双向循环 wrap-around；
     *  fa-fade 守卫照抄（官方从未真正添加该类，恒 false）；DOM 本地重建 + 桥报宿主落盘。 */
    function onImageSwiped(mesid, direction) {
        var state = messageState[mesid];
        if (!state) { return; }
        var media = state.media || [];
        if (!media.length) { return; }                       // 官方 warn+return
        if (getMediaDisplayOf(state) !== 'gallery') { return; } // 仅 GALLERY 可切（L2084）
        var current = clampMediaIndex(state);
        var newIndex;
        if (direction === 'left') {
            newIndex = current === 0 ? media.length - 1 : current - 1;
        } else {
            newIndex = current === media.length - 1 ? 0 : current + 1;
        }
        state.mediaIndex = newIndex;
        rebuildMediaForMessage(mesid); // SCROLL_BEHAVIOR.ADJUST 补偿滚动
        bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_img_swipe', value: String(newIndex) });
    }

    /** 单消息媒体重建（appendMediaToMessage 默认 ADJUST：scrollHeight 差值补回 scrollTop） */
    function rebuildMediaForMessage(mesid) {
        var node = document.querySelector('.mes[mesid="' + mesid + '"]');
        var state = messageState[mesid];
        if (!node || !state) { return; }
        var chat = document.getElementById('chat');
        var prevHeight = chat ? chat.scrollHeight : 0;
        mountMessageMedia(node, state);
        if (chat) { chat.scrollTop += chat.scrollHeight - prevHeight; }
    }

    // ---- 边界2 lightbox（chats.js expandMessageMedia L875-967 + popup.js 弹层机制）----

    function openLightboxDialog(mediaElement, title) {
        var dlg = document.createElement('dialog');
        dlg.className = 'popup large_dialogue_popup transparent_dialogue_popup';
        var body = document.createElement('div');
        body.className = 'popup-body';
        var content = document.createElement('div');
        content.className = 'popup-content';
        body.appendChild(content);
        dlg.appendChild(body);
        var closeBtn = document.createElement('div');
        closeBtn.className = 'popup-button-close right_menu_button fa-solid fa-circle-xmark';
        closeBtn.setAttribute('data-result', '0');
        closeBtn.setAttribute('title', 'Close popup');
        dlg.appendChild(closeBtn);

        var holder = document.createElement('div');
        holder.className = 'img_enlarged_holder';
        holder.appendChild(mediaElement);
        var container = document.createElement('div');
        container.className = 'img_enlarged_container';
        container.appendChild(holder);

        // zoom toggle 单向语义（L929-934）：未放大 IMG → 放大；否则取消 zoomed；VIDEO 恒不放大
        mediaElement.addEventListener('click', function (event) {
            var shouldZoom = !mediaElement.classList.contains('zoomed') && mediaElement.nodeName === 'IMG';
            mediaElement.classList.toggle('zoomed', shouldZoom);
            event.stopPropagation();
        });

        if (String(title || '').trim().length > 0) {
            var pre = document.createElement('pre');
            var code = document.createElement('code');
            code.className = 'img_enlarged_title txt';
            code.textContent = title;
            // 官方 chats.js L953-955：标题点击不冒泡（不触发 dialog 关闭）
            code.addEventListener('click', function (event) { event.stopPropagation(); });
            pre.appendChild(code);
            container.appendChild(pre);
            addCopyButtonToCode(code); // 标题复制按钮（addCopyToCodeBlocks 同构）
        }

        content.appendChild(container);

        // chats.js L956-957：压过 .large_dialogue_popup 尺寸
        dlg.style.width = 'unset';
        dlg.style.height = 'unset';
        // 点 dialog 内任意处关闭；媒体/标题 stopPropagation 不关只切 zoom（L958-960）
        dlg.addEventListener('click', function () { closeLightbox(dlg); });
        dlg.addEventListener('cancel', function (ev) { ev.preventDefault(); closeLightbox(dlg); }); // ESC

        document.body.appendChild(dlg);
        if (typeof dlg.showModal === 'function') {
            dlg.setAttribute('opening', '');
            try { dlg.showModal(); } catch (e) { dlg.setAttribute('open', ''); }
            finishAnimation(dlg, 'opening');
        } else {
            dlg.setAttribute('open', ''); // jsdom 等无 showModal 环境
        }
    }

    function finishAnimation(dlg, attr) {
        var done = function () {
            dlg.removeAttribute(attr);
            dlg.removeEventListener('animationend', done);
        };
        dlg.addEventListener('animationend', done);
        setTimeout(done, 400); // 动画事件不触发（reduced-motion/jsdom）兜底
    }

    function closeLightbox(dlg) {
        if (dlg.hasAttribute('closing')) { return; }
        dlg.setAttribute('closing', '');
        var finalize = function () {
            if (typeof dlg.close === 'function') { try { dlg.close(); } catch (e) { /* already closed */ } }
            dlg.remove();
        };
        var once = function () { finalize(); };
        dlg.addEventListener('animationend', once, { once: true });
        setTimeout(once, 300); // pop-out 动画时长兜底
    }

    /** chats.js expandMessageMedia：type 分流 image/video、audio 拒绝、title 展示 */
    function expandMessageMedia(mesid, mediaIndex, startZoomed) {
        var state = messageState[mesid];
        if (!state) { return; }
        var attachment = (state.media || [])[mediaIndex];
        if (!attachment) { return; }
        if (attachment.type === 'audio') { return; } // 官方 warn+return

        var mediaElement;
        if (attachment.type === 'video') {
            mediaElement = document.createElement('video');
            mediaElement.className = 'img_enlarged';
            mediaElement.src = attachment.url;
            mediaElement.controls = true;
            mediaElement.autoplay = true;
        } else {
            mediaElement = document.createElement('img');
            mediaElement.className = 'img_enlarged';
            mediaElement.src = attachment.url;
        }

        openLightboxDialog(mediaElement, attachment.title || state.title || '');
        // .mes_media_enlarge 处理器立即 .click()（jQuery 触发 zoom 监听器）→ 打开即放大态
        if (startZoomed) {
            mediaElement.dispatchEvent(new MouseEvent('click'));
        }
    }

    // ---- 边界5 长聊天截断（script.js printMessages/showMoreMessages L1431-1486）----

    function setShowMoreButton(on) {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        var btn = document.getElementById('show_more_messages');
        if (on && !btn) {
            btn = document.createElement('div');
            btn.id = 'show_more_messages';
            btn.textContent = 'Show more messages'; // 官方硬编码英文，无 i18n key
            chat.insertBefore(btn, chat.firstChild);
        } else if (!on && btn) {
            btn.remove();
        }
    }

    function initShowMoreClick() {
        document.addEventListener('click', function (ev) {
            if (ev.target && ev.target.id === 'show_more_messages') {
                ev.stopPropagation();
                ev.preventDefault();
                bridgeSend({ type: 'hostRequest', hostAction: 'show_more_messages' });
            }
        });
    }

    /** showMoreMessages 的内核半边：上一批插到按钮之后（按钮不在则 prepend）；
     *  按钮在视口内才按高度差回滚（官方 isElementInViewport gate）。 */
    function prependMessages(list) {
        return loadTemplate().then(function (tpl) {
            var chat = document.getElementById('chat');
            if (!chat || !(list || []).length) { return null; }
            var btn = document.getElementById('show_more_messages');
            var prevHeight = chat.scrollHeight;
            var scrollTopBefore = chat.scrollTop;
            var frag = document.createDocumentFragment();
            (list || []).forEach(function (p) { frag.appendChild(mountMessage(tpl, p)); });
            if (btn) { btn.after(frag); } else { chat.insertBefore(frag, chat.firstChild); }
            var delta = chat.scrollHeight - prevHeight;
            var inViewport = false;
            try {
                var rect = (btn || chat).getBoundingClientRect();
                inViewport = !btn || (rect.top >= 0 && rect.top <= (window.innerHeight || 800));
            } catch (e) { /* 无布局环境忽略 */ }
            if (!btn || inViewport) { chat.scrollTop = scrollTopBefore + delta; }
            return list.length;
        });
    }

    // ---- 边界3 行内编辑（script.js messageEdit* L8079-8377 + chats.js click_to_edit）----

    function trimSpaces(text) {
        if (window.KernelConfig.trimSpaces === false) { return String(text); }
        return String(text == null ? '' : text).replace(/^\s+/, '').replace(/\s+$/, '');
    }

    function cssEsc(s) { return window.CSS && CSS.escape ? CSS.escape(s) : s.replace(/"/g, '\\"'); }

    /** click_to_edit 桥入口（宿主判定开关后调用）；.mes_edit 点击同路 */
    function beginEditMessage(mesid) {
        if (document.body.classList.contains('delete-mode')) { return; } // 删除模式优先（L11779）
        if (thisEditMesId != null && thisEditMesId !== mesid) {
            finishEdit(thisEditMesId, 'save'); // 官方切换语义：点另一条=自动确认旧的（L11784）
        }
        messageEdit(mesid);
    }

    function messageEdit(mesid) {
        var node = document.querySelector('.mes[mesid="' + cssEsc(mesid) + '"]');
        var state = messageState[mesid];
        if (!node || !state) { return; }

        thisEditMesId = mesid;
        thisEditMesChname = state.chName || '';
        refreshSwipeClasses(); // 被编辑消息及其上方 chevron 全隐（isMessageSwipeable L9126）

        var chatEl = document.getElementById('chat');
        var scrollPos = chatEl ? chatEl.scrollTop : 0;
        var mesBlock = node.querySelector('.mes_block');
        var mesText = mesBlock ? mesBlock.querySelector('.mes_text') : null;
        if (mesText) {
            // 缓存当前格式化 HTML：cancel/done 的本地恢复源（宿主权威刷新随后覆盖）
            state.cachedDisplayHtml = mesText.innerHTML;
            mesText.innerHTML = '';
        }
        var buttons = mesBlock ? mesBlock.querySelector('.mes_buttons') : null;
        if (buttons) { buttons.style.display = 'none'; }           // inline none（L8203）
        var editButtons = mesBlock ? mesBlock.querySelector('.mes_edit_buttons') : null;
        if (editButtons) { editButtons.style.display = 'inline-flex'; } // inline inline-flex（L8204）

        // reasoning 联动进入编辑（messageEdit L8212-8215 的 :visible 等价：有内容即可编）
        if (reasoningRawOf(state)) { beginReasoningEdit(node); }

        var ta = document.createElement('textarea');
        ta.id = 'curEditTextarea';
        ta.className = 'edit_textarea mdHotkeys';
        ta.dataset.macros = ''; // 官方逐字段（L8221-8225）
        if (mesText) { mesText.appendChild(ta); }
        var value = trimSpaces(state.rawMes != null ? state.rawMes : (state.mes || ''));
        ta.value = value;
        if (!(window.CSS && CSS.supports && CSS.supports('field-sizing', 'content'))) {
            ta.style.height = '0px';
            ta.style.height = ta.scrollHeight + 'px'; // field-sizing 兜底测高（L8230-8233）
        }
        ta.focus();
        ta.setSelectionRange(value.length, value.length); // 光标置末尾
        if (chatEl && numericMesid(mesid) >= maxMessageIndex()) {
            chatEl.scrollTop = scrollPos; // 编辑最后一条时恢复滚动位置（L8238）
        }
        updateEditArrowClasses();
    }

    function editingTextarea() {
        return document.getElementById('curEditTextarea');
    }

    function reasoningRawOf(state) {
        if (state.reasoningRaw != null) { return String(state.reasoningRaw); }
        return state.reasoning ? String(state.reasoning) : '';
    }

    function beginReasoningEdit(node, initialValue) {
        var details = node.querySelector('.mes_reasoning_details');
        if (!details || details.querySelector('.reasoning_edit_textarea')) { return; }
        details.open = true;
        var ta = document.createElement('textarea');
        ta.className = 'edit_textarea mdHotkeys reasoning_edit_textarea';
        var state = messageState[node.getAttribute('mesid')] || {};
        ta.value = initialValue != null ? String(initialValue) : reasoningRawOf(state);
        details.insertBefore(ta, details.querySelector('.mes_reasoning')); // 官方：建于 .mes_reasoning 之前
        if (!(window.CSS && CSS.supports && CSS.supports('field-sizing', 'content'))) {
            ta.style.height = '0px';
            ta.style.height = ta.scrollHeight + 'px';
        }
        ta.focus();
        ta.setSelectionRange(ta.value.length, ta.value.length);
        reasoningEditing = true;
        // 有 reasoning 编辑框时官方经 :has() 隐藏展示面（style.css L487-500），无需手动清理
    }

    function closeReasoningEdit(node) {
        var details = node ? node.querySelector('.mes_reasoning_details') : null;
        var ta = details ? details.querySelector('.reasoning_edit_textarea') : null;
        if (ta) { ta.remove(); }
        reasoningEditing = !!document.querySelector('.reasoning_edit_textarea');
    }

    function restoreEditorChrome(node, state) {
        var mesBlock = node.querySelector('.mes_block');
        var mesText = mesBlock ? mesBlock.querySelector('.mes_text') : null;
        if (mesText) {
            mesText.innerHTML = state.cachedDisplayHtml != null ? state.cachedDisplayHtml : mesText.innerHTML;
        }
        var editButtons = mesBlock ? mesBlock.querySelector('.mes_edit_buttons') : null;
        if (editButtons) { editButtons.style.display = 'none'; }
        var buttons = mesBlock ? mesBlock.querySelector('.mes_buttons') : null;
        if (buttons) { buttons.style.display = ''; }
        closeReasoningEdit(node);
    }

    /** done/cancel 公共出口：save 走桥（宿主引擎管线后权威刷新），cancel 本地丢弃恢复 */
    function finishEdit(mesid, mode) {
        var node = document.querySelector('.mes[mesid="' + cssEsc(mesid) + '"]');
        var state = messageState[mesid];
        if (!node || !state) { return; }
        var ta = editingTextarea();
        var text = ta ? ta.value : (node.querySelector('.mes_text') || {}).textContent || '';

        if (mode === 'save' && reasoningEditing) {
            var rTa = node.querySelector('.reasoning_edit_textarea');
            if (rTa) { bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_reasoning_save', value: rTa.value }); }
        }

        restoreEditorChrome(node, state);
        if (mode === 'save') {
            bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_edit_save', value: text });
        }
        thisEditMesId = null;
        thisEditMesChname = '';
        updateEditArrowClasses();
        refreshSwipeClasses(); // showSwipeButtons（done/cancel 共同尾调）
    }

    function closeMessageEditor() {
        if (thisEditMesId == null) { return; }
        finishEdit(thisEditMesId, 'cancel'); // auto_save 关 → ESC 全部取消（丢改动）
    }

    /** updateEditArrowClasses（script.js L9427-9449）：up/down 越界 disabled，copy/delete 解锁 */
    function updateEditArrowClasses() {
        if (thisEditMesId == null) { return; }
        var node = document.querySelector('.mes[mesid="' + cssEsc(thisEditMesId) + '"]');
        if (!node) { return; }
        var idx = numericMesid(thisEditMesId);
        var up = node.querySelector('.mes_edit_up');
        var down = node.querySelector('.mes_edit_down');
        if (up) { up.classList.toggle('disabled', idx <= 0); }
        if (down) { down.classList.toggle('disabled', idx >= maxMessageIndex()); }
        ['.mes_edit_copy', '.mes_edit_delete'].forEach(function (sel) {
            var b = node.querySelector(sel);
            if (b) { b.classList.remove('disabled'); }
        });
    }

    /** refreshSwipeClasses：编辑抑制重算全部节点（被编辑消息及上方 chevron 隐匿，L9126 条件
     *  messageId > this_edit_mes_id 才可滑）。mountMessage 的单节点路径与这里共用 applySwipeClasses。 */
    function refreshSwipeClasses() {
        var editIdx = thisEditMesId != null ? numericMesid(thisEditMesId) : null;
        Object.keys(messageState).forEach(function (mid) {
            var node = document.querySelector('.mes[mesid="' + cssEsc(mid) + '"]');
            if (node) { applySwipeClasses(node, messageState[mid], editIdx); }
        });
    }

    /** 官方 refreshSwipeButtons/isMessageSwipeable/getOverswipeBehavior 组合判定。
     *  suppressBelowOrEqualId：行内编辑中的消息序号（其上全部不可滑）。 */
    function applySwipeClasses(node, payload, suppressBelowOrEqualId) {
        var swipeCount = Number(payload.swipeCount || 0);
        var currentSwipe = Number(payload.currentSwipe || 0);
        // 官方 isMessageSwipeable（script.js:9123-9147）：末条 && !isSmallSys &&
        // !(extra.swipeable === false) && !is_user && messageId > this_edit_mes_id。
        // （swipeState!=EDITING 分支核心代码永不触发，仅扩展赋值。）
        var swipeable = !!payload.lastMessage && !payload.isUser && !payload.smallSysMes &&
            payload.swipeable !== false &&
            (suppressBelowOrEqualId == null || numericMesid(payload.mesid) > suppressBelowOrEqualId);
        var overswipe = payload.overswipe || '';
        var isLastSwipe = Math.max(swipeCount - 1, 0) <= currentSwipe; // (swipes?.length ?? 1)-1 <= swipe_id ?? 0
        var hasSwipes = swipeCount > 1;
        var pristineGreeting = overswipe === 'pristine_greeting';
        // 官方 L9232-9235 原样：&& 优先于 || —— (isLastSwipe && regenerate) || edit_generate
        var isOverswipeable = (isLastSwipe && overswipe === 'regenerate') || overswipe === 'edit_generate';
        node.classList.toggle('swipes_visible', swipeable && (hasSwipes || pristineGreeting));
        node.classList.toggle('last_swipe', swipeable && isOverswipeable);
        node.classList.toggle('last_mes', !!payload.lastMessage);
        var counterEl = node.querySelector('.swipes-counter');
        if (counterEl) {
            counterEl.textContent = swipeable ? (currentSwipe + 1) + '​/​' + swipeCount : '';
        }
    }

    /** 编辑框 autofit 兜底 + 自动保存监听（官方 document input 委托 L11151/L11800）。
     *  auto_save_msg_edits 官方默认 false——KernelConfig 未开时不自动保存。 */
    function initEditListeners() {
        var cssAutofit = window.CSS && CSS.supports && CSS.supports('field-sizing', 'content');
        document.addEventListener('input', function (e) {
            var t = e.target;
            if (!(t instanceof HTMLTextAreaElement)) { return; }
            if (t.classList.contains('edit_textarea')) {
                if (!cssAutofit) {
                    var chat = document.getElementById('chat');
                    var top = chat ? chat.scrollTop : 0;
                    t.style.height = '0px';
                    t.style.height = (t.scrollHeight + 4) + 'px';
                    if (chat) { chat.scrollTop = top; }
                }
                if (window.KernelConfig.autoSaveEdits && t.id === 'curEditTextarea' && thisEditMesId != null) {
                    bridgeSend({ type: 'click', mesid: thisEditMesId, messageAction: 'mes_edit_save', value: t.value });
                }
            }
        });
        // ESC：auto_save 关 → 全部取消；composing 跳过；完成后焦点回输入框（L12267-12284）
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || e.isComposing) { return; }
            var editVisible = !!editingTextarea() || document.querySelector('.reasoning_edit_textarea');
            if (!editVisible) { return; }
            closeMessageEditor();
            var sendTa = document.getElementById('send_textarea');
            if (sendTa) { sendTa.focus(); }
        });
        // 编辑按钮委托表（L11873-11933 + reasoning 按钮）
        document.addEventListener('click', function (ev) {
            var btn = ev.target.closest ? ev.target.closest(
                '.mes_edit_done,.mes_edit_cancel,.mes_edit_up,.mes_edit_down,.mes_edit_copy,' +
                '.mes_edit_delete,.mes_edit_add_reasoning,.mes_reasoning_edit_done,' +
                '.mes_reasoning_edit_cancel,.mes_reasoning_delete,.mes_reasoning_edit') : null;
            if (!btn) { return; }
            var node = btn.closest('.mes');
            if (!node) { return; }
            var mesid = node.getAttribute('mesid');

            if (btn.classList.contains('mes_reasoning_edit')) {
                beginReasoningEdit(node); return;
            }
            if (btn.classList.contains('mes_reasoning_edit_done')) {
                var rTa = node.querySelector('.reasoning_edit_textarea');
                if (rTa) { bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_reasoning_save', value: rTa.value }); }
                closeReasoningEdit(node);
                return;
            }
            if (btn.classList.contains('mes_reasoning_edit_cancel')) {
                closeReasoningEdit(node);
                return;
            }
            if (btn.classList.contains('mes_reasoning_delete')) {
                closeReasoningEdit(node);
                bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_reasoning_save', value: '' });
                return;
            }
            if (btn.classList.contains('mes_edit_done')) {
                if (thisEditMesId != null) { finishEdit(thisEditMesId, 'save'); }
                return;
            }
            if (btn.classList.contains('mes_edit_cancel')) {
                if (thisEditMesId != null) { finishEdit(thisEditMesId, 'cancel'); }
                return;
            }
            if (btn.classList.contains('mes_edit_add_reasoning')) {
                var state = messageState[mesid] || {};
                if (reasoningRawOf(state)) {
                    bridgeSend({ type: 'hostRequest', hostAction: 'toast', value: 'Reasoning already exists for this message' });
                    return;
                }
                node.classList.add('reasoning');
                var details = node.querySelector('.mes_reasoning_details');
                if (details) { details.open = true; }
                beginReasoningEdit(node, ''); // 空值起编；确认时桥存 extra.reasoning
                return;
            }
            // 结构操作（up/down/copy/delete）：数据变更一律桥回宿主执行
            var ta = editingTextarea();
            var draft = ta ? ta.value : null;
            if (btn.classList.contains('mes_edit_up') || btn.classList.contains('mes_edit_down')) {
                if (thisEditMesId == null) { return; }
                var delta = btn.classList.contains('mes_edit_up') ? -1 : 1;
                var idx = numericMesid(thisEditMesId);
                if (idx + delta < 0 || idx + delta > maxMessageIndex()) { return; } // 边界 guard（L11879）
                bridgeSend({
                    type: 'click', mesid: mesid, messageAction: 'mes_edit_move',
                    value: JSON.stringify({ delta: delta, draft: draft }),
                });
                return;
            }
            if (btn.classList.contains('mes_edit_copy')) {
                bridgeSend({
                    type: 'click', mesid: mesid, messageAction: 'mes_edit_copy',
                    value: draft != null ? draft : ((messageState[mesid] || {}).rawMes || ''),
                });
                return;
            }
            if (btn.classList.contains('mes_edit_delete')) {
                bridgeSend({ type: 'click', mesid: mesid, messageAction: 'mes_edit_delete', value: '' });
            }
        });
    }

    /** 官方 addCopyToCodeBlocks 的复制行为补全：pointerup 复制 + toast（此前只有按钮没有接线） */
    function initCodeCopy() {
        document.addEventListener('pointerup', function (ev) {
            var btn = ev.target.closest ? ev.target.closest('.code-copy') : null;
            if (!btn) { return; }
            var code = btn.closest('code');
            if (!code) { return; }
            ev.stopPropagation();
            var text = Array.prototype.filter.call(code.childNodes, function (n) { return n !== btn; })
                .map(function (n) { return n.textContent; }).join('');
            copyTextAndNotify(text);
        });
    }

    function copyTextAndNotify(text) {
        var notify = function () { bridgeSend({ type: 'hostRequest', hostAction: 'toast', value: 'Copied!' }); };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(notify, function () { legacyCopy(text); notify(); });
        } else {
            legacyCopy(text);
            notify();
        }
    }

    function legacyCopy(text) {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } catch (e) { /* no-op */ }
        ta.remove();
    }


    window.Kernel = {
        formatText: formatText,          // DOM 黄金对比入口：返回 HTML 字符串
        formatTextStreaming: formatTextUncached, // 流式 tick 专用：中间态不入 LRU（防洗掉终态缓存）
        renderMessage: renderMessage,    // 生产入口：upsert 单条消息
        renderChat: renderChat,          // 整页壳 C1：增量同步（diff 未变楼层跳过，payloads 有序）
        updateStreamingNode: updateStreamingNode, // 流式 tick 一站式（含 stream_fade_in 分支）
        setRuntimeConfig: setRuntimeConfig, // 运行时开关下发（streamFadeIn/gestures/sendOnEnter/quick×2/autoSaveEdits）
        prependMessages: prependMessages, // 边界5：show more 批量前插（按钮锚点+滚动补偿）
        scrollToBottom: scrollToBottom,  // 官方 #chat 滚动接管（C1/C2）
        setDeleteMode: setDeleteMode,
        selectDeleteFrom: selectDeleteFrom,
        setInputText: setInputText,      // C3：宿主 → #send_textarea（草稿下发/冒充流式/发送后清空）
        setInputState: setInputState,    // C3：生成/滑动状态 → data-generating/mes_stop/hideAllSwipeButtons
        setBackground: setBackground,    // C4：官方 #bg1 背景图（会话级 > 全局，宿主解析 URL）
        beginEditMessage: beginEditMessage, // 边界3：click_to_edit / .mes_edit 桥入口
        applyTheme: applyTheme,
        applyStylePack: applyStylePack,  // 第三方主题整包 CSS（无则纯官方行为）
        clear: clearMessages,
        ready: true,
    };
    initInputArea();
    initExtraMesButtonsOutsideClose();
    initShowMoreClick();
    initEditListeners();
    initCodeCopy();
    bridgeSend({ type: 'kernelReady' });
})();
