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
        if (payload.avatarUrl && avatarImg) { avatarImg.src = payload.avatarUrl; }
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

        // 官方 refreshSwipeButtons / isMessageSwipeable 语义（script.js L9123-9152）：
        // 可滑=最后一条且非用户/非 smallSys；swipes_visible=有 >1 变体（左箭头随之显）；
        // last_swipe=已到末位变体（overswipe 缺省 REGENERATE → 右箭头兜底常显，style.css L1341-1343）。
        // 计数带零宽空格 formatSwipeCounter L9875：`n​/​total`。
        var swipeCount = Number(payload.swipeCount || 0);
        var currentSwipe = Number(payload.currentSwipe || 0);
        var swipeable = !!payload.lastMessage && !payload.isUser && !payload.isSystem && !payload.smallSysMes;
        node.classList.toggle('swipes_visible', swipeable && swipeCount > 1);
        node.classList.toggle('last_swipe', swipeable && (swipeCount - 1 <= currentSwipe));
        node.classList.toggle('last_mes', !!payload.lastMessage);
        var counterEl = node.querySelector('.swipes-counter');
        if (counterEl) { counterEl.textContent = (currentSwipe + 1) + '​/​' + swipeCount; }

        // 官方媒体容器：图片直接进 .mes_media_wrapper；音视频沿用原生控件语义。
        var mediaWrapper = node.querySelector('.mes_media_wrapper');
        if (mediaWrapper) {
            mediaWrapper.innerHTML = '';
            var mediaDisplay = payload.mediaDisplay || 'list';
            node.setAttribute('data-media-display', mediaDisplay);
            (payload.media || []).forEach(function (item, index) {
                var block;
                if (item.type === 'image') {
                    block = document.createElement('div');
                    block.className = 'mes_media_container mes_img_container';
                    block.setAttribute('data-index', index);
                    var img = document.createElement('img');
                    img.className = 'mes_img';
                    img.src = item.url;
                    if (item.title) { img.title = item.title; }
                    block.appendChild(img);
                } else if (item.type === 'video') {
                    block = document.createElement('div');
                    block.className = 'mes_media_container mes_video_container';
                    block.setAttribute('data-index', index);
                    var video = document.createElement('video');
                    video.className = 'mes_video';
                    video.src = item.url;
                    video.controls = true;
                    video.preload = 'metadata';
                    block.appendChild(video);
                } else if (item.type === 'audio') {
                    block = document.createElement('div');
                    block.className = 'mes_media_container mes_audio_container audio-player';
                    block.setAttribute('data-index', index);
                    var audio = document.createElement('audio');
                    audio.className = 'mes_audio';
                    audio.src = item.url;
                    audio.controls = true;
                    audio.preload = 'metadata';
                    block.appendChild(audio);
                } else {
                    return;
                }
                mediaWrapper.appendChild(block);
            });
            var hasMedia = (payload.media || []).length > 0;
            var textElForMedia = node.querySelector('.mes_text');
            if (textElForMedia) {
                textElForMedia.classList.toggle('inline_media', hasMedia && payload.mediaDisplay === 'inline');
            }
        }

        chat.appendChild(node);
        reportHeight(payload.mesid, node.scrollHeight);
        observeHeight(node, payload.mesid);

        // 点击上报（链接/交互元素由 WebViewClient 外链逻辑处理，这里报宿主决策）
        node.addEventListener('click', function (ev) {
            // 官方 script.js L11806：操作按钮排展开是纯内核 DOM 状态，不上报宿主
            var hintEl = ev.target.closest ? ev.target.closest('.extraMesButtonsHint') : null;
            if (hintEl) { expandExtraMesButtons(hintEl); return; }
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
            // 官方 addOneMessage：全量同步后 last_mes 恒为最后一条。
            Array.prototype.forEach.call(document.querySelectorAll('#chat .mes'), function (n, i, all) {
                n.classList.toggle('last_mes', i === all.length - 1);
            });
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

    /** 官方控件动作名：点击桥专用（mes_edit/mes_copy/.../swipe_left/swipe_right/del_checkbox）。 */
    function describeAction(el) {
        if (!el || !el.classList) { return null; }
        return Array.prototype.find.call(el.classList, function (cls) {
            return cls === 'swipe_left' || cls === 'swipe_right' ||
                cls === 'del_checkbox' || cls.indexOf('mes_') === 0;
        }) || null;
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
            bridgeSend({ type: 'chatScroll', atBottom: !scrollLock });
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
        // 去 no-connection；send_but/mes_continue/mes_impersonate 去 displayNone）
        form.classList.remove('no-connection');
        ['send_but', 'mes_continue', 'mes_impersonate'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) { el.classList.remove('displayNone'); }
        });
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
        }
        // #form_sheld 高度回报：原生悬浮附件/快捷回复行动态内边距（CSS px ≈ Compose dp）
        var sheld = document.getElementById('form_sheld');
        if (sheld && 'ResizeObserver' in window) {
            var reportFormHeight = function () {
                bridgeSend({ type: 'inputHeight', height: sheld.offsetHeight });
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
    // 公开 API
    // ------------------------------------------------------------------
    window.Kernel = {
        formatText: formatText,          // DOM 黄金对比入口：返回 HTML 字符串
        renderMessage: renderMessage,    // 生产入口：upsert 单条消息
        renderChat: renderChat,          // 整页壳 C1：全量同步（清空重建，payloads 有序）
        scrollToBottom: scrollToBottom,  // 官方 #chat 滚动接管（C1/C2）
        setDeleteMode: setDeleteMode,
        selectDeleteFrom: selectDeleteFrom,
        setInputText: setInputText,      // C3：宿主 → #send_textarea（草稿下发/冒充流式/发送后清空）
        setInputState: setInputState,    // C3：生成/滑动状态 → data-generating/mes_stop/hideAllSwipeButtons
        setBackground: setBackground,    // C4：官方 #bg1 背景图（会话级 > 全局，宿主解析 URL）
        applyTheme: applyTheme,
        applyStylePack: applyStylePack,  // 第三方主题整包 CSS（无则纯官方行为）
        clear: clearMessages,
        ready: true,
    };
    initInputArea();
    initExtraMesButtonsOutsideClose();
    bridgeSend({ type: 'kernelReady' });
})();
