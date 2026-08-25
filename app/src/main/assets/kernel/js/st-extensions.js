/**
 * EmberInn RenderKernel - Showdown 扩展
 * 从官方 SillyTavern release 8172dcd scripts/showdown-underscore.js 与
 * scripts/showdown-exclusion.js 等价内联（去除模块依赖，配置注入）。
 */
(function () {
    'use strict';

    function canUseNegativeLookbehind() {
        try {
            new RegExp('(?<!_)');
            return true;
        } catch (e) {
            return false;
        }
    }

    // 官方 showdown-underscore.js 原样逻辑
    window.markdownUnderscoreExt = function () {
        try {
            if (!canUseNegativeLookbehind()) {
                console.log('Showdown-underscore extension: Negative lookbehind not supported. Skipping.');
                return [];
            }
            return [{
                type: 'output',
                regex: new RegExp('(<code(?:\\s+[^>]*)?>[\\s\\S]*?<\\/code>|<style(?:\\s+[^>]*)?>[\\s\\S]*?<\\/style>)|\\b(?<!_)_(?!_)(.*?)(?<!_)_(?!_)\\b', 'gi'),
                replace: function (match, tagContent, italicContent) {
                    if (tagContent) {
                        return match;
                    } else if (italicContent) {
                        return '<em>' + italicContent + '</em>';
                    }
                    return match;
                },
            }];
        } catch (e) {
            console.error('Error in Showdown-underscore extension:', e);
            return [];
        }
    };

    // 官方 showdown-exclusion.js 原样逻辑；substituteParams 由内核配置注入。
    // [EmberInn] 空串判定移入 filter：官方"改字符串即重载 processor"，本内核 converter
    // 只建一次——每次格式化实时读 KernelConfig.markdownEscapeStrings，语义等价即时生效。
    window.markdownExclusionExt = function () {
        return [{
            type: 'lang',
            filter: function (text) {
                var cfg = window.KernelConfig || {};
                if (!cfg.markdownEscapeStrings) {
                    return text;
                }
                var escapedExclusions = String(cfg.markdownEscapeStrings)
                    .split(',')
                    .filter(function (element) { return element.length > 0; })
                    .map(function (element) {
                        return '(' + element.split('').map(function (char) { return '\\' + char; }).join('') + ')';
                    });
                if (escapedExclusions.length === 0) {
                    return text;
                }
                var replaceRegex = new RegExp('^(' + escapedExclusions.join('|') + ')\n', 'gm');
                return text.replace(replaceRegex, function (match) {
                    return match.replace(replaceRegex, '\u0000' + match + ' \n');
                });
            },
        }];
    };
})();
