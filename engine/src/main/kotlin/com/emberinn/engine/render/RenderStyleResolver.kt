package com.emberinn.engine.render

/**
 * 样式解析与合并：标签默认（UA 级）→ style 块匹配（author 级）→ 内联 style（inline 级，最高），
 * 可继承属性（color/font* /line-height/text-align/white-space/letter-spacing）沿树向下继承。
 * 纯 JVM 无 UI 依赖。
 */
object RenderStyleResolver {

    // ---------------- 标签默认样式（UA 风格） ----------------

    private fun tagDefaults(tag: String, attrs: Map<String, String>): Map<String, String> {
        val d = LinkedHashMap<String, String>()
        when (tag) {
            "h1" -> { d["display"] = "block"; d["font-size"] = "2em"; d["font-weight"] = "700"; d["margin"] = "0.67em 0" }
            "h2" -> { d["display"] = "block"; d["font-size"] = "1.5em"; d["font-weight"] = "700"; d["margin"] = "0.83em 0" }
            "h3" -> { d["display"] = "block"; d["font-size"] = "1.17em"; d["font-weight"] = "700"; d["margin"] = "1em 0" }
            "h4" -> { d["display"] = "block"; d["font-size"] = "1em"; d["font-weight"] = "700"; d["margin"] = "1.33em 0" }
            "h5" -> { d["display"] = "block"; d["font-size"] = "0.83em"; d["font-weight"] = "700"; d["margin"] = "1.67em 0" }
            "h6" -> { d["display"] = "block"; d["font-size"] = "0.67em"; d["font-weight"] = "700"; d["margin"] = "2.33em 0" }
            "p" -> { d["display"] = "block"; d["margin"] = "0.5em 0" }
            "div", "section", "article", "aside", "header", "footer", "nav", "main", "address", "figure",
            "figcaption", "fieldset", "form", "center", "details", "summary", "dialog" -> d["display"] = "block"
            "b", "strong" -> d["font-weight"] = "700"
            "i", "em", "cite", "var" -> d["font-style"] = "italic"
            "u", "ins" -> d["text-decoration"] = "underline"
            "s", "del", "strike" -> d["text-decoration"] = "line-through"
            "small" -> d["font-size"] = "0.83em"
            "big" -> d["font-size"] = "1.17em"
            "sub" -> { d["font-size"] = "0.75em"; d["vertical-align"] = "sub" }
            "sup" -> { d["font-size"] = "0.75em"; d["vertical-align"] = "super" }
            "pre" -> { d["display"] = "block"; d["white-space"] = "pre"; d["font-family"] = "monospace" }
            "code", "kbd", "samp", "tt" -> d["font-family"] = "monospace"
            "blockquote" -> {
                d["display"] = "block"
                d["margin"] = "0.5em 0"
                d["padding"] = "0 0 0 0.75em"
                d["border-left"] = "3px solid"
            }
            "ul", "ol", "menu", "dir" -> { d["display"] = "block"; d["padding"] = "0 0 0 2em"; d["margin"] = "0.5em 0" }
            "li" -> d["display"] = "list-item"
            "hr" -> {
                d["display"] = "block"
                d["margin"] = "0.5em 0"
                d["border"] = "0"
                d["border-top"] = "1px solid"
                d["height"] = "0px"
            }
            "table" -> { d["display"] = "table"; d["border-collapse"] = "collapse" }
            "caption" -> d["display"] = "table-caption"
            "tr" -> d["display"] = "table-row"
            "td", "th" -> { d["display"] = "table-cell"; d["padding"] = "0.25em 0.5em"; d["border"] = "1px solid" }
            "img", "video", "audio", "picture", "canvas", "svg", "iframe" -> d["display"] = "inline-block"
            "input", "select", "textarea", "button", "label" -> d["display"] = "inline-block"
            "a" -> { d["text-decoration"] = "underline" }
            "font" -> {
                attrs["face"]?.let { d["font-family"] = it }
                attrs["size"]?.let {
                    val n = it.trim()
                    val px = when {
                        n.matches(Regex("[+-]?\\d+")) -> {
                            val v = n.toInt()
                            if (n.startsWith("+") || n.startsWith("-")) {
                                when (v) { -2 -> 10f; -1 -> 13f; 0 -> 16f; 1 -> 18f; 2 -> 24f; 3 -> 32f; 4 -> 48f; else -> 16f }
                            } else {
                                when (v) { 1 -> 10f; 2 -> 13f; 3 -> 16f; 4 -> 18f; 5 -> 24f; 6 -> 32f; 7 -> 48f; else -> 16f }
                            }
                        }
                        else -> return@let null
                    }
                    d["font-size"] = "${px}px"
                }
                attrs["color"]?.let { d["color"] = it }
            }
        }
        return d
    }

    private val BLOCK_TAGS = setOf(
        "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "table",
        "caption", "tr", "td", "th", "tbody", "thead", "tfoot", "hr", "details", "summary", "section",
        "article", "aside", "header", "footer", "nav", "main", "address", "figure", "figcaption", "fieldset",
        "form", "center", "dl", "dt", "dd", "menu", "dir", "dialog",
    )

    /** 可继承属性：沿树向下传播（子未显式设置时取父值）。 */
    private val INHERITABLE = setOf(
        "color", "font-family", "font-size", "font-weight", "font-style", "text-decoration", "text-align",
        "line-height", "letter-spacing", "white-space",
    )

    // ---------------- 入口 ----------------

    fun resolve(root: SanitizedNode, rules: List<ScopedCssRule>): RenderNode {
        val compiled = rules.mapNotNull { compileSelector(it.selector)?.let { c -> it to c } }
        return walk(root, compiled, emptyMap(), emptyList())
    }

    /** @param ancestors 当前节点的祖先链（根→父）。用于后代/子选择器匹配。 */
    private fun walk(
        node: SanitizedNode,
        rules: List<Pair<ScopedCssRule, CompiledSelector>>,
        inherited: Map<String, String>,
        ancestors: List<SanitizedNode.Tag>,
    ): RenderNode = when (node) {
        is SanitizedNode.Text -> RenderNode.Text(node.text)
        is SanitizedNode.Tag -> {
            val decls = LinkedHashMap<String, String>()
            // 1. UA 默认
            decls.putAll(tagDefaults(node.name, node.attrs))
            // 2. 继承
            for (k in INHERITABLE) {
                inherited[k]?.let { decls.putIfAbsent(k, it) }
            }
            // 3. style 块匹配（author，按 specificity 升序应用，后者覆盖）
            val matched = rules.filter { it.second.matches(node, ancestors) }.sortedBy { it.first.specificity }
            for ((rule, _) in matched) {
                for ((k, v) in rule.declarations) {
                    if (k in INHERITABLE) decls[k] = v else decls[k] = v
                }
            }
            // 4. 内联 style（最高优先级）
            node.attrs["style"]?.let { inline ->
                for (decl in inline.split(';')) {
                    val idx = decl.indexOf(':')
                    if (idx <= 0) continue
                    val k = decl.substring(0, idx).trim().lowercase()
                    val v = decl.substring(idx + 1).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) decls[k] = v
                }
            }
            val style = finalize(decls, tagInherit = node.name in BLOCK_TAGS || decls["display"] == "block")
            val nextAncestors = ancestors + node
            val children = node.children.map { walk(it, rules, inheritFrom(decls, style), nextAncestors) }
            RenderNode.Element(
                tag = node.name,
                attrs = node.attrs,
                classes = node.attrs["class"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                style = style,
                children = children,
                interactive = interactiveKindOf(node.name, node.attrs),
            )
        }
    }

    private fun inheritFrom(decls: Map<String, String>, style: ResolvedStyle): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (k in INHERITABLE) decls[k]?.let { out[k] = it }
        return out
    }

    private fun interactiveKindOf(tag: String, attrs: Map<String, String>): InteractiveKind = when (tag) {
        "details" -> InteractiveKind.Details
        "summary" -> InteractiveKind.Summary
        "a" -> InteractiveKind.Link
        "img" -> InteractiveKind.Image
        "video" -> InteractiveKind.Video
        "audio" -> InteractiveKind.Audio
        "input" -> if (attrs["type"] == "checkbox") InteractiveKind.InputCheckbox else InteractiveKind.None
        else -> InteractiveKind.None
    }

    // ---------------- 最终化 ----------------

    private fun finalize(decls: Map<String, String>, tagInherit: Boolean): ResolvedStyle {
        val fontSizePx = fontSizeOf(decls["font-size"])
        val lineHeight: Float? = when {
            decls["line-height"] == null -> null
            else -> lineHeightOf(decls["line-height"]!!, fontSizePx)
        }
        val margin = decls["margin"]?.let { CssStyleParser.parseBox(it, fontSizePx ?: 16f) } ?: CssBox.None
        val padding = decls["padding"]?.let { CssStyleParser.parseBox(it, fontSizePx ?: 16f) } ?: CssBox.None
        val border = borderOf(decls)
        return ResolvedStyle(
            display = decls["display"],
            color = decls["color"]?.let { CssStyleParser.parseColor(it) },
            backgroundColor = decls["background-color"]?.let { CssStyleParser.parseColor(it) },
            fontSizePx = fontSizePx,
            fontWeight = fontWeightOf(decls["font-weight"]),
            fontStyle = when (decls["font-style"]) {
                "italic", "oblique" -> "italic"
                else -> null
            },
            textDecoration = textDecorationOf(decls["text-decoration"]),
            textAlign = decls["text-align"],
            lineHeight = lineHeight,
            letterSpacingPx = decls["letter-spacing"]?.let { CssStyleParser.parseLength(it) as? CssLength.Px }?.value,
            margin = margin,
            padding = padding,
            border = border,
            borderRadius = decls["border-radius"]?.let { CssStyleParser.parseLength(it) },
            width = decls["width"]?.let { CssStyleParser.parseLength(it) },
            height = decls["height"]?.let { CssStyleParser.parseLength(it) },
            minHeight = decls["min-height"]?.let { CssStyleParser.parseLength(it) },
            opacity = decls["opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f),
            whiteSpace = decls["white-space"],
            overflow = decls["overflow"],
            verticalAlign = decls["vertical-align"],
            flexDirection = decls["flex-direction"],
            justifyContent = decls["justify-content"],
            alignItems = decls["align-items"],
            gapPx = decls["gap"]?.let { (CssStyleParser.parseLength(it) as? CssLength.Px)?.value },
            backgroundImage = if (decls["background-image"]?.startsWith("data:image") == true) decls["background-image"] else null,
            fontFamily = decls["font-family"]?.split(',')?.map { it.trim().trim('"', '\'') }?.filter { it.isNotEmpty() },
        )
    }

    private fun fontSizeOf(raw: String?): Float? {
        if (raw == null) return null
        val len = CssStyleParser.parseLength(raw) ?: return null
        return when (len) {
            is CssLength.Px -> len.value
            is CssLength.Percent -> 16f * len.value / 100f
            CssLength.Auto -> null
        }
    }

    private fun lineHeightOf(raw: String, fontSizePx: Float?): Float? {
        val v = raw.trim()
        v.toFloatOrNull()?.let { return it } // 无单位倍率
        if (v.endsWith("%")) {
            return v.dropLast(1).toFloatOrNull()?.let { it / 100f }
        }
        val len = CssStyleParser.parseLength(v) ?: return null
        if (len is CssLength.Px && fontSizePx != null && fontSizePx > 0f) return len.value / fontSizePx
        return null
    }

    private fun fontWeightOf(raw: String?): Int? = when (raw?.trim()) {
        null -> null
        "normal" -> 400
        "bold", "bolder" -> 700
        else -> raw.trim().toIntOrNull()?.coerceIn(100, 900)
    }

    private fun textDecorationOf(raw: String?): String? {
        if (raw == null) return null
        val toks = raw.trim().split(Regex("\\s+")).toSet()
        if (toks.isEmpty() || "none" in toks) return null
        val parts = ArrayList<String>()
        if ("underline" in toks) parts += "underline"
        if ("line-through" in toks) parts += "line-through"
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun borderOf(decls: Map<String, String>): CssBorder {
        val fs = decls["font-size"]?.let { fontSizeOf(it) } ?: 16f
        // border / border-top 等简写：统一取四向最大值（简化）
        fun parseSide(prefix: String): Triple<CssLength?, String?, RgbaColor?> {
            var width: CssLength? = null
            var style: String? = null
            var color: RgbaColor? = null
            val raw = decls[prefix] ?: return Triple(null, null, null)
            for (tok in raw.trim().split(Regex("\\s+"))) {
                val l = CssStyleParser.parseLength(tok, fs)
                if (l != null && width == null) {
                    width = l
                } else if (tok in setOf("solid", "dashed", "dotted", "double", "none", "hidden")) {
                    style = tok
                } else {
                    CssStyleParser.parseColor(tok)?.let { color = it }
                }
            }
            return Triple(width, style, color)
        }
        val any = parseSide("border")
        val width = decls["border-width"]?.let { CssStyleParser.parseLength(it, fs) } ?: any.first
        val style = decls["border-style"] ?: any.second
        val color = decls["border-color"]?.let { CssStyleParser.parseColor(it) } ?: any.third
        return CssBorder(width, style, color)
    }

    // ---------------- 选择器编译与匹配 ----------------

    private data class SimpleSel(
        val tag: String?,
        val classes: Set<String>,
        val id: String?,
        val negations: List<SimpleSel>,
        val alts: List<SimpleSel>,
    )

    private data class Step(val simple: SimpleSel, val combinator: String?) // ""=自身起点, " "=后代, ">"=子

    private class CompiledSelector(val steps: List<Step>) {
        /** @param ancestors 目标元素的祖先链（根→父），用于后代/子组合器回溯。 */
        fun matches(el: SanitizedNode.Tag, ancestors: List<SanitizedNode.Tag>): Boolean {
            if (steps.isEmpty()) return false
            val last = steps.last()
            if (!matchesSimple(el, last.simple)) return false
            // 回溯前面的步骤：在祖先链上匹配 steps[0..size-2]
            return matchUp(ancestors, steps.size - 2, steps.last().combinator)
        }

        private fun matchUp(ancestors: List<SanitizedNode.Tag>, idx: Int, combinator: String?): Boolean {
            if (idx < 0) return true
            val step = steps[idx]
            return when (combinator) {
                ">" -> {
                    // 父元素必须命中该 step，然后继续在更上层匹配
                    val parent = ancestors.lastOrNull() ?: return false
                    if (matchesSimple(parent, step.simple)) {
                        matchUp(ancestors.dropLast(1), idx - 1, step.combinator)
                    } else false
                }
                " " -> {
                    // 任意祖先（从最近向根回溯）
                    var i = ancestors.size - 1
                    while (i >= 0) {
                        if (matchesSimple(ancestors[i], step.simple) &&
                            matchUp(ancestors.subList(0, i), idx - 1, step.combinator)
                        ) {
                            return true
                        }
                        i--
                    }
                    false
                }
                else -> matchUp(ancestors, idx, combinator) // 兼容：无连接符视为自身起点
            }
        }
    }

    private fun classesOf(el: SanitizedNode.Tag): Set<String> =
        el.attrs["class"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun matchesSimple(el: SanitizedNode.Tag, s: SimpleSel): Boolean {
        if (s.tag != null && s.tag != el.name) return false
        if (s.id != null && el.attrs["id"] != s.id) return false
        if (s.classes.isNotEmpty() && !classesOf(el).containsAll(s.classes)) return false
        if (s.negations.isNotEmpty() && s.negations.any { matchesSimple(el, it) }) return false
        if (s.alts.isNotEmpty() && s.alts.none { matchesSimple(el, it) }) return false
        return true
    }

    private fun compileSelector(sel: String): CompiledSelector? {
        var s = sel.trim()
        // 去掉官方前缀 .mes_text（整条消息根即 .mes_text）
        if (s.startsWith(".mes_text")) {
            s = s.removePrefix(".mes_text").trim()
            if (s.isEmpty()) return null // 纯 .mes_text → 匹配根（无法表达，忽略）
        }
        if (s.isEmpty()) return null
        val steps = ArrayList<Step>()
        // 以 > 和空白分割成 (combinator, simpleString) 序列
        var prevCombinator: String? = null
        val parts = splitByCombinators(s)
        for (p in parts) {
            val simple = parseSimple(p.simple) ?: return null
            steps += Step(simple, prevCombinator)
            prevCombinator = p.combinator
        }
        return CompiledSelector(steps)
    }

    private data class Part(val simple: String, val combinator: String?) // combinator 是与下一段的连接符

    private fun splitByCombinators(sel: String): List<Part> {
        val out = ArrayList<Part>()
        var cur = StringBuilder()
        var i = 0
        val n = sel.length
        while (i < n) {
            val c = sel[i]
            when {
                c == '>' -> {
                    if (cur.isNotEmpty()) { out += Part(cur.toString().trim(), ">"); cur = StringBuilder() }
                    i++
                }
                c.isWhitespace() -> {
                    // 连续的空白视为后代连接；只在有内容时落一个 " "
                    if (cur.isNotEmpty()) {
                        // 向后看是否还有非空白内容（避免尾部空白落 " "）
                        var j = i
                        while (j < n && sel[j].isWhitespace()) j++
                        if (j < n) { out += Part(cur.toString().trim(), " "); cur = StringBuilder() }
                        i = j
                    } else {
                        i++
                    }
                }
                else -> {
                    cur.append(c); i++
                }
            }
        }
        if (cur.isNotEmpty()) out += Part(cur.toString().trim(), null)
        return out
    }

    private fun parseSimple(part: String): SimpleSel? {
        val idRegex = Regex("#([\\w-]+)")
        val classRegex = Regex("\\.([\\w-]+)")
        val ids = idRegex.findAll(part).map { it.groupValues[1] }.toList()
        val classes = classRegex.findAll(part).map { it.groupValues[1] }.toSet()
        // 去掉已匹配的 #/. 段，剩下的取开头标识符作为 tag（去掉伪类）
        var rest = idRegex.replace(part, " ")
        rest = classRegex.replace(rest, " ")
        // 伪类 :not(...)/:is(...)/:where(...)
        val negations = ArrayList<SimpleSel>()
        val alts = ArrayList<SimpleSel>()
        val pseudoRegex = Regex(":(not|is|where|matches|any)\\(([^)]+)\\)")
        val matches = pseudoRegex.findAll(rest).toList()
        for (m in matches) {
            val kind = m.groupValues[1]
            val content = m.groupValues[2]
            val innerParts = content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            when (kind) {
                "not" -> innerParts.firstOrNull()?.let { parseSimple(it) }?.let { negations += it }
                "is", "where", "matches", "any" -> innerParts.forEach { parseSimple(it)?.let { a -> alts += a } }
            }
        }
        rest = pseudoRegex.replace(rest, " ")
        val tag = Regex("^[a-zA-Z][\\w-]*").find(rest.trim())?.value
        val id = ids.firstOrNull()
        if (tag == null && id == null && classes.isEmpty() && negations.isEmpty() && alts.isEmpty()) {
            return null
        }
        // 校验：rest 中不得残留未知符号（如 :hover 等不支持伪类 → 整条忽略，避免误应用）
        val leftover = rest.trim().replace(Regex("^[a-zA-Z][\\w-]*"), "")
        if (leftover.isNotEmpty() && !leftover.all { it.isWhitespace() }) return null
        return SimpleSel(tag, classes, id, negations, alts)
    }
}
