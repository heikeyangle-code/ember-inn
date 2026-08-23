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
        externalMediaAllowed: true,       // [EmberInn] 放开模式：外链媒体始终允许
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
    /**
     * 与官方 messageFormatting() 的显示段逐字对齐。
     * @param {string} mes       已经过引擎处理（宏/正则/reasoning）的消息文本
     * @param {object} opts      { chName, isUser, isSystem, allowName2Display }
     * @returns {string} 最终 HTML（可直接注入 .mes_text.innerHTML）
     */
    function formatText(mes, opts) {
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
     * 模板就绪后同步挂载一条消息（官方 script.js addOneMessage 同构）：
     * 填字段 → 思考块 → 追加 #chat → 高度回报与观察 → 点击与长按手势接线。
     */
    function mountMessage(tpl, payload) {
        var chat = document.getElementById('chat');
        var node = tpl.cloneNode(true);
        node.setAttribute('mesid', payload.mesid);
        node.setAttribute('ch_name', payload.chName || '');
        node.setAttribute('is_user', payload.isUser ? 'true' : 'false');
        node.setAttribute('is_system', payload.isSystem ? 'true' : 'false');

        if (payload.avatarUrl) {
            var img = node.querySelector('.mesAvatarWrapper .avatar img');
            if (img) { img.src = payload.avatarUrl; }
        }
        var nameEl = node.querySelector('.name_text');
        if (nameEl) { nameEl.textContent = payload.chName || ''; }
        var tsEl = node.querySelector('.timestamp');
        if (tsEl && payload.timestamp) { tsEl.textContent = String(payload.timestamp); }
        var tcEl = node.querySelector('.tokenCounterDisplay');
        if (tcEl && payload.tokenCount != null) {
            tcEl.textContent = String(payload.tokenCount);
        }

        var mesText = node.querySelector('.mes_text');
        if (mesText) {
            mesText.innerHTML = formatText(payload.mes, {
                chName: payload.chName,
                isUser: payload.isUser,
                isSystem: payload.isSystem,
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

        chat.appendChild(node);
        reportHeight(payload.mesid, node.scrollHeight);
        observeHeight(node, payload.mesid);

        // 点击上报（链接/交互元素由 WebViewClient 外链逻辑处理，这里报宿主决策）
        node.addEventListener('click', function (ev) {
            bridgeSend({ type: 'click', mesid: payload.mesid, target: describeTarget(ev.target) });
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
            return mountMessage(tpl, payload);
        });
    }

    /**
     * 全量同步整个聊天（整页壳 C1）：清空重建，payloads 顺序即聊天顺序。
     * 切聊天 / 首挂载走这里；增量渲染走 renderMessage（模板缓存后两者均同步可用）。
     */
    function renderChat(payloads) {
        return loadTemplate().then(function (tpl) {
            clearMessages();
            var last = null;
            (payloads || []).forEach(function (p) { last = mountMessage(tpl, p); });
            return last;
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

    /** 清空全部消息节点：先解除高度观察再移除，防止 ResizeObserver 强持有已摘除 DOM */
    function clearMessages() {
        var chat = document.getElementById('chat');
        Array.prototype.forEach.call(chat.querySelectorAll('.mes'), function (n) {
            if (resizeObserver) { resizeObserver.unobserve(n); }
            n.remove();
        });
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
    function applyStylePack(cfg) {
        cfg = cfg || {};
        if (!cfg.enabled || !cfg.href) {
            syncPackLink(STYLE_PACK_LINK_ID, null);
            syncPackLink(STYLE_PACK_EXT_LINK_ID, null);
            return;
        }
        syncPackLink(STYLE_PACK_LINK_ID, cfg.href);
        syncPackLink(STYLE_PACK_EXT_LINK_ID, cfg.extensionHref || null);
        var vars = cfg.vars || {};
        var root = document.documentElement;
        Object.keys(vars).forEach(function (key) {
            var name = key.charAt(0) === '-' ? key : '--' + key;
            root.style.setProperty(name, String(vars[key]));
        });
    }

    function applyTheme(theme) {
        var root = document.documentElement;
        function set(k, v) { root.style.setProperty(k, v); }

        if (theme.main_text_color) {
            set('--SmartThemeBodyColor', theme.main_text_color);
            var m = theme.main_text_color.match(/\(([^)]+)\)/);
            if (m) {
                var parts = m[1].split(',');
                set('--SmartThemeCheckboxBgColorR', parts[0]);
                set('--SmartThemeCheckboxBgColorG', parts[1]);
                set('--SmartThemeCheckboxBgColorB', parts[2]);
                set('--SmartThemeCheckboxBgColorA', parts[3]);
            }
        }
        if (theme.italics_text_color) set('--SmartThemeEmColor', theme.italics_text_color);
        if (theme.underline_text_color) set('--SmartThemeUnderlineColor', theme.underline_text_color);
        if (theme.quote_text_color) set('--SmartThemeQuoteColor', theme.quote_text_color);
        if (theme.blur_tint_color) set('--SmartThemeBlurTintColor', theme.blur_tint_color);
        if (theme.chat_tint_color) set('--SmartThemeChatTintColor', theme.chat_tint_color);
        if (theme.user_mes_blur_tint_color) set('--SmartThemeUserMesBlurTintColor', theme.user_mes_blur_tint_color);
        if (theme.bot_mes_blur_tint_color) set('--SmartThemeBotMesBlurTintColor', theme.bot_mes_blur_tint_color);
        if (theme.shadow_color) set('--SmartThemeShadowColor', theme.shadow_color);
        if (theme.border_color) set('--SmartThemeBorderColor', theme.border_color);
        if (theme.blur_strength != null) set('--blurStrength', String(theme.blur_strength));
        if (theme.shadow_width != null) set('--shadowWidth', String(theme.shadow_width));
        if (theme.font_scale != null) set('--fontScale', String(theme.font_scale));
        if (theme.chat_width != null) set('--sheldWidth', theme.chat_width + 'vw');

        // custom_css 直接注入（官方 applyCustomCSS 同构）
        var style = document.getElementById('custom-style');
        if (style) { style.innerHTML = theme.custom_css || ''; }

        // 开关型字段 → body 类同步（与 power-user.js applyPowerUserSettings 逐项同构）
        // 官方语义：每次应用全量同步，先移除后按当前值添加
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
            if (!(field in theme)) return;
            var v = theme[field];
            var on = mode === 'true' ? !!v : !v;
            if (on) document.body.classList.add(cls);
        });
        // 头像形状：avatar_style 0 圆 / 1 矩形大头像 / 2 方形 / 3 圆角（官方 avatar_styles 枚举 L95-100）
        if ('avatar_style' in theme) {
            if (theme.avatar_style === 1) document.body.classList.add('big-avatars');
            else if (theme.avatar_style === 2) document.body.classList.add('square-avatars');
            else if (theme.avatar_style === 3) document.body.classList.add('rounded-avatars');
        }
        // 消息布局：chat_display 0 平铺 / 1 气泡(bubblechat) / 2 文档(documentstyle)；
        // 3..7 = Moonlit Echoes 扩展布局，映射已对上游扩展 index.js initChatDisplaySwitcher
        // 逐项核实（3=echostyle/4=whisperstyle/5=hushstyle/6=ripplestyle/7=tidestyle），
        // 全量同步语义与上游一致。样式包未加载时这些类为惰性。
        if ('chat_display' in theme) {
            var layoutClass = chatDisplayToClass(theme.chat_display);
            if (layoutClass) { document.body.classList.add(layoutClass); }
        }

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

    // ------------------------------------------------------------------
    // 滚动接管（整页壳 C1）：fullchat 模式下 #chat 为滚动容器，向宿主回报贴底
    // 状态（宿主据此驱动「跳到底部」浮标）；嵌入态无内部滚动，事件静默。
    // ------------------------------------------------------------------
    function scrollToBottom(smooth) {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        if (typeof chat.scrollTo === 'function') {
            try {
                chat.scrollTo({ top: chat.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
                return;
            } catch (e) { /* 老内核不支持 options 形式，落到底部直接赋值 */ }
        }
        chat.scrollTop = chat.scrollHeight;
    }

    (function watchChatScroll() {
        var chat = document.getElementById('chat');
        if (!chat) { return; }
        var pending = false;
        chat.addEventListener('scroll', function () {
            if (pending) { return; }
            pending = true;
            setTimeout(function () {
                pending = false;
                // 贴底判定：距底不足 40px 视为在底部（移动端跳底浮标同级容差）
                var atBottom = chat.scrollHeight - chat.scrollTop - chat.clientHeight < 40;
                bridgeSend({ type: 'chatScroll', atBottom: atBottom });
            }, 100);
        }, { passive: true });
    })();

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------
    window.Kernel = {
        formatText: formatText,          // DOM 黄金对比入口：返回 HTML 字符串
        renderMessage: renderMessage,    // 生产入口：upsert 单条消息
        renderChat: renderChat,          // 整页壳 C1：全量同步（清空重建，payloads 有序）
        scrollToBottom: scrollToBottom,  // 官方 #chat 滚动接管（C1/C2）
        applyTheme: applyTheme,
        applyStylePack: applyStylePack,  // 第三方主题整包 CSS（无则纯官方行为）
        clear: clearMessages,
        ready: true,
    };
    bridgeSend({ type: 'kernelReady' });
})();
