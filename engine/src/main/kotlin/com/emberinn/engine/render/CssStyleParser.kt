package com.emberinn.engine.render

/**
 * CSS 文本解析工具：颜色 / 长度 / style 块规则（镜像官方 decodeStyleTags 语义）。
 * 纯 JVM 无 UI 依赖。
 */
object CssStyleParser {

    // ---------------- 颜色 ----------------

    private val NAMED_COLORS: Map<String, String> = mapOf(
        "black" to "#000000", "silver" to "#c0c0c0", "gray" to "#808080", "grey" to "#808080",
        "white" to "#ffffff", "maroon" to "#800000", "red" to "#ff0000", "purple" to "#800080",
        "fuchsia" to "#ff00ff", "magenta" to "#ff00ff", "green" to "#008000", "lime" to "#00ff00",
        "olive" to "#808000", "yellow" to "#ffff00", "navy" to "#000080", "blue" to "#0000ff",
        "teal" to "#008080", "aqua" to "#00ffff", "cyan" to "#00ffff", "orange" to "#ffa500",
        "brown" to "#a52a2a", "pink" to "#ffc0cb", "transparent" to "#00000000",
    )

    fun parseColor(raw: String): RgbaColor? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        val lv = value.lowercase()
        NAMED_COLORS[lv]?.let { value = it }
        if (value.startsWith("#")) {
            val hex = value.substring(1)
            return when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    RgbaColor((r * 17) / 255f, (g * 17) / 255f, (b * 17) / 255f)
                }
                6 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    RgbaColor(r / 255f, g / 255f, b / 255f)
                }
                8 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                    RgbaColor(r / 255f, g / 255f, b / 255f, a / 255f)
                }
                else -> null
            }
        }
        if (value.startsWith("rgb(") || value.startsWith("rgba(")) {
            val inner = value.substring(value.indexOf('(') + 1, value.lastIndexOf(')'))
            val parts = inner.split(',').map { it.trim() }
            if (parts.size < 3) return null
            val r = parts[0].toFloatOrNull() ?: return null
            val g = parts[1].toFloatOrNull() ?: return null
            val b = parts[2].toFloatOrNull() ?: return null
            val a = parts.getOrNull(3)?.trimEnd('%')?.toFloatOrNull()?.let {
                if (parts[3].contains('%')) it / 100f else it
            } ?: 1f
            return RgbaColor(r / 255f, g / 255f, b / 255f, a)
        }
        return null
    }

    // ---------------- 长度 ----------------

    /** 解析长度。em/rem 以 [basePx] 换算（默认按 16px）；px 原样；% 为 Percent；auto 为 Auto。 */
    fun parseLength(raw: String, basePx: Float = 16f): CssLength? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        if (v.equals("auto", ignoreCase = true)) return CssLength.Auto
        if (v.endsWith("%")) {
            val f = v.dropLast(1).toFloatOrNull() ?: return null
            return CssLength.Percent(f)
        }
        if (v.endsWith("px")) {
            return CssLength.Px(v.dropLast(2).trim().toFloatOrNull() ?: return null)
        }
        if (v.endsWith("em")) {
            return CssLength.Px((v.dropLast(2).trim().toFloatOrNull() ?: return null) * basePx)
        }
        if (v.endsWith("rem")) {
            return CssLength.Px((v.dropLast(3).trim().toFloatOrNull() ?: return null) * basePx)
        }
        if (v.endsWith("pt")) {
            return CssLength.Px((v.dropLast(2).trim().toFloatOrNull() ?: return null) * (4f / 3f))
        }
        // 裸数字按 px
        return v.toFloatOrNull()?.let { CssLength.Px(it) }
    }

    /** 解析 0-3 个值 → 四向（上/右/下/左）。 */
    fun parseBox(raw: String, basePx: Float = 16f): CssBox {
        val parts = raw.trim().split(Regex("\\s+")).mapNotNull { parseLength(it, basePx) }
        return when (parts.size) {
            1 -> CssBox(parts[0], parts[0], parts[0], parts[0])
            2 -> CssBox(parts[0], parts[1], parts[0], parts[1])
            3 -> CssBox(parts[0], parts[1], parts[2], parts[1])
            4 -> CssBox(parts[0], parts[1], parts[2], parts[3])
            else -> CssBox.None
        }
    }

    // ---------------- style 块（镜像 decodeStyleTags） ----------------

    /**
     * 把 `<style>` 内容解析成 scoped 规则。
     * - 选择器类名前缀 custom-（官方 sanitizeSimpleSelector，已 custom- 开头不重复加；:has/not/where/is/matches/any 递归）
     * - @import 规则删除
     * - 外部媒体禁用时，含 `://` 的声明过滤（官方 chats.js sanitizeRule）
     */
    fun parseScopedBlock(cssText: String, externalMediaAllowed: Boolean): List<ScopedCssRule> {
        val rules = ArrayList<ScopedCssRule>()
        for (rule in parseCssRules(cssText)) {
            if (rule.type == "import") continue
            if (rule.type == "media") {
                // @media 嵌套：递归处理内部规则（选择器同样前缀化）
                for (inner in parseScopedBlock(rule.body ?: "", externalMediaAllowed)) {
                    rules += inner
                }
                continue
            }
            val selectors = rule.selectors.orEmpty().map { sanitizeSelector(it) }
            var declarations = parseDeclarations(rule.body ?: "")
            if (!externalMediaAllowed) {
                declarations = declarations.filterValues { !it.contains("://") }
            }
            for (sel in selectors) {
                if (sel.isEmpty()) continue
                rules += ScopedCssRule(
                    selector = sel,
                    declarations = declarations,
                    specificity = specificityOf(sel),
                )
            }
        }
        return rules
    }

    /** 简化选择器消毒：类名前缀 custom-；:has/not/where/is/matches/any 内嵌递归。 */
    fun sanitizeSelector(selector: String): String {
        var s = selector.trim()
        // 先处理伪类内嵌选择器
        val pseudoClasses = listOf("has", "not", "where", "is", "matches", "any")
        for (pc in pseudoClasses) {
            val re = Regex(":$pc\\(([^)]+)\\)")
            var prev: String
            do {
                prev = s
                s = re.replace(s) { m ->
                    ":${pc}(${sanitizeSimpleSelector(m.groupValues[1])})"
                }
            } while (s != prev)
        }
        return sanitizeSimpleSelector(s)
    }

    private fun sanitizeSimpleSelector(selector: String): String {
        return selector.split(Regex("\\s+")).joinToString(" ") { part ->
            part.replace(Regex("\\.([\\w-]+)")) { m ->
                val cls = m.groupValues[1]
                if (cls.startsWith("custom-")) m.value else ".custom-$cls"
            }
        }
    }

    private fun specificityOf(selector: String): Int {
        var score = 0
        // class/id 计数（type 不计，简化）
        score += Regex("\\.([\\w-]+)").findAll(selector).count() * 10
        score += Regex("#([\\w-]+)").findAll(selector).count() * 100
        return score
    }

    private fun parseDeclarations(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (decl in body.split(';')) {
            val idx = decl.indexOf(':')
            if (idx <= 0) continue
            val prop = decl.substring(0, idx).trim().lowercase()
            val value = decl.substring(idx + 1).trim()
            if (prop.isEmpty() || value.isEmpty()) continue
            out[prop] = value
        }
        return out
    }

    /** 极简 CSS 规则解析：`selector { body }`；`@import ...;` 等无块 at-rule 到 `;` 截止。 */
    private fun parseCssRules(cssText: String): List<CssRule> {
        val rules = ArrayList<CssRule>()
        var i = 0
        val n = cssText.length
        while (i < n) {
            // 跳过空白与注释
            val c = cssText[i]
            if (c.isWhitespace()) { i++; continue }
            if (c == '/' && i + 1 < n && cssText[i + 1] == '*') {
                val end = cssText.indexOf("*/", i + 2)
                i = if (end < 0) n else end + 2
                continue
            }
            val braceOpen = cssText.indexOf('{', i)
            if (braceOpen < 0) break
            val semi = cssText.indexOf(';', i)
            if (semi >= 0 && semi < braceOpen) {
                // `;` 先于 `{`：@import 等无块 at-rule（或畸形片段），直接跳过
                val head = cssText.substring(i, semi).trim()
                if (head.startsWith("@import")) {
                    rules += CssRule("import", null, null)
                }
                i = semi + 1
                continue
            }
            // 读选择器段（到 { 为止）
            val selectorText = cssText.substring(i, braceOpen).trim()
            // 找配对的 }
            var depth = 1
            var j = braceOpen + 1
            while (j < n && depth > 0) {
                when (cssText[j]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                j++
            }
            if (depth != 0) break
            val body = cssText.substring(braceOpen + 1, j - 1)
            if (selectorText.startsWith("@")) {
                if (selectorText.startsWith("@media") || selectorText.startsWith("@supports")) {
                    rules += CssRule("media", null, body)
                }
                // 其它 @ 规则忽略
            } else {
                val selectors = selectorText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                rules += CssRule("style", selectors, body)
            }
            i = j
        }
        return rules
    }

    private data class CssRule(
        val type: String,          // style / import / media
        val selectors: List<String>?,
        val body: String?,
    )
}
