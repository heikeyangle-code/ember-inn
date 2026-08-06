package com.emberinn.engine.prompt

/**
 * Handlebars 子集渲染器（对齐官方 renderStoryString 使用的模板语法）：
 * {{var}}、{{#if var}}…{{/if}}（嵌套 + {{else}}）。
 * truthy：非 null 且非空字符串（对齐 Handlebars if）。
 */
object StoryStringRenderer {

    fun render(template: String, params: Map<String, String>): String {
        return renderSection(template, params)
    }

    private fun renderSection(text: String, params: Map<String, String>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ifOpen = Regex("""\{\{#if ([A-Za-z_][A-Za-z0-9_]*)\}\}""").find(text, i)
            val close = Regex("""\{\{/if\}\}""").find(text, i)
            val plain = Regex("""\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}""").find(text, i)
            val next = listOfNotNull(ifOpen, close, plain).minByOrNull { it.range.first }
                ?: run { sb.append(text, i, text.length); break }

            if (next != ifOpen && next.range.first > i) {
                sb.append(text, i, next.range.first)
            }
            when (next) {
                ifOpen -> {
                    val name = next.groupValues[1]
                    // 找到匹配的 {{/if}}（支持嵌套）
                    val bodyStart = next.range.last + 1
                    var depth = 1
                    var pos = bodyStart
                    var end = -1
                    while (pos < text.length) {
                        val o = Regex("""\{\{#if [A-Za-z_][A-Za-z0-9_]*\}\}""").find(text, pos)
                        val c = Regex("""\{\{/if\}\}""").find(text, pos)
                        val n = listOfNotNull(o, c).minByOrNull { it.range.first } ?: break
                        if (o != null && n.range.first == o.range.first) depth++ else depth--
                        pos = n.range.last + 1
                        if (depth == 0) { end = n.range.last; break }
                    }
                    if (end < 0) { sb.append(next.value); i = next.range.last + 1; continue }
                    val body = text.substring(bodyStart, end - 6) // 去掉 {{/if}} 前位置
                    val elseSplit = splitTopLevelElse(body)
                    val truthy = params[name]?.isNotEmpty() == true
                    val chosen = if (truthy) elseSplit.first else elseSplit.second ?: ""
                    sb.append(renderSection(chosen, params))
                    i = end + 1
                }
                close -> { sb.append(next.value); i = next.range.last + 1 }
                plain -> {
                    sb.append(params[next.groupValues[1]] ?: "")
                    i = next.range.last + 1
                }
                else -> { sb.append(text, i, text.length); break }
            }
        }
        return sb.toString()
    }

    private fun splitTopLevelElse(body: String): Pair<String, String?> {
        var depth = 0
        var pos = 0
        while (pos < body.length) {
            val o = Regex("""\{\{#if [A-Za-z_][A-Za-z0-9_]*\}\}""").find(body, pos)
            val c = Regex("""\{\{/if\}\}""").find(body, pos)
            val e = Regex("""\{\{else\}\}""").find(body, pos)
            val n = listOfNotNull(o, c, e).minByOrNull { it.range.first } ?: break
            val isOpen = o != null && n.range.first == o.range.first
            val isClose = c != null && n.range.first == c.range.first
            when {
                isOpen -> depth++
                isClose -> depth--
                depth == 0 -> return body.substring(0, n.range.first) to body.substring(n.range.last + 1)
            }
            pos = n.range.last + 1
        }
        return body to null
    }
}
