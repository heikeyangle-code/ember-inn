package com.emberinn.engine.render

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 路线 A 的 HTML 消毒引擎。
 *
 * 以官方 DOMPurify 3.4.2（SillyTavern 默认）为蓝本复刻：
 *  - 标签白名单 = purify.js `html$1`（含 details/summary/img/video/audio/style；不含 script/iframe/object/embed）
 *  - 属性白名单 = purify.js `html` + data-* / aria-*
 *  - URI 检查 = `IS_ALLOWED_URI`（javascript:/vbscript:/data:(非媒体) 等被拒）
 *  - KEEP_CONTENT=true（禁用标签保子节点上提）；FORBID_CONTENTS（script/style 等）连同内容删除
 *  - class 属性 custom- 前缀化（保留 fa-{star}/note-{star}/monospace）
 *  - 媒体规则：外部媒体禁用时删除含外部 URL 的媒体节点（chats.js uponSanitizeElement）
 *
 * 差异（有意的、更保守）：
 *  - DOMPurify 3.4.2 不对 style 属性内容做 CSS 解析（原样放行，安全靠浏览器）；
 *    我们是原生 CSS 解释器，必须自实现保守 CSS 消毒：剔除 url()/expression/behavior/-moz-binding/@import/危险协议。
 *  - style 块按官方 decodeStyleTags 语义：选择器类名前缀 custom-、删 @import、外部媒体禁用时删含 :// 的声明。
 */
object HtmlSanitizerEngine {

    data class Config(
        /** 官方 forbid_external_media 取反：默认 false = 禁外部媒体。 */
        val externalMediaAllowed: Boolean = false,
        /** 消息内 style 块恒保留（官方 messageFormatting 无条件 decode）。 */
        val styleBlocksAllowed: Boolean = true,
    )

    /** 镜像 purify.js html$1。 */
    private val ALLOWED_TAGS: Set<String> = setOf(
        "a", "abbr", "acronym", "address", "area", "article", "aside", "audio", "b", "bdi", "bdo", "big", "blink",
        "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code", "col", "colgroup",
        "content", "data", "datalist", "dd", "decorator", "del", "details", "dfn", "dialog", "dir", "div", "dl",
        "dt", "element", "em", "fieldset", "figcaption", "figure", "font", "footer", "form", "h1", "h2", "h3", "h4",
        "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "img", "input", "ins", "kbd", "label", "legend",
        "li", "main", "map", "mark", "marquee", "menu", "menuitem", "meter", "nav", "nobr", "ol", "optgroup",
        "option", "output", "p", "picture", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp", "search",
        "section", "select", "shadow", "slot", "small", "source", "spacer", "span", "strike", "strong", "style",
        "sub", "summary", "sup", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead", "time",
        "tr", "track", "tt", "u", "ul", "var", "video", "wbr", "custom-style",
    )

    /** 镜像 purify.js `html` 属性白名单。 */
    private val ALLOWED_ATTRS: Set<String> = setOf(
        "accept", "action", "align", "alt", "autocapitalize", "autocomplete", "autopictureinpicture", "autoplay",
        "background", "bgcolor", "border", "capture", "cellpadding", "cellspacing", "checked", "cite", "class",
        "clear", "color", "cols", "colspan", "controls", "controlslist", "coords", "crossorigin", "datetime",
        "decoding", "default", "dir", "disabled", "disablepictureinpicture", "disableremoteplayback", "download",
        "draggable", "enctype", "enterkeyhint", "exportparts", "face", "for", "headers", "height", "hidden", "high",
        "href", "hreflang", "id", "inert", "inputmode", "integrity", "ismap", "kind", "label", "lang", "list",
        "loading", "loop", "low", "max", "maxlength", "media", "method", "min", "minlength", "multiple", "muted",
        "name", "nonce", "noshade", "novalidate", "nowrap", "open", "optimum", "part", "pattern", "placeholder",
        "playsinline", "popover", "popovertarget", "popovertargetaction", "poster", "preload", "pubdate", "radiogroup",
        "readonly", "rel", "required", "rev", "reversed", "role", "rows", "rowspan", "spellcheck", "scope",
        "selected", "shape", "size", "sizes", "slot", "span", "srclang", "start", "src", "srcset", "step", "style",
        "summary", "tabindex", "title", "translate", "type", "usemap", "valign", "value", "width", "wrap", "xmlns",
    )

    /** 镜像 purify.js DEFAULT_URI_SAFE_ATTRIBUTES（值不参与 URI 检查的惰性属性）。 */
    private val URI_SAFE_ATTRS: Set<String> = setOf(
        "alt", "class", "for", "id", "label", "name", "pattern", "placeholder", "role", "summary", "title",
        "value", "style", "xmlns",
    )

    /** 镜像 purify.js DEFAULT_DATA_URI_TAGS。 */
    private val DATA_URI_TAGS: Set<String> = setOf("audio", "video", "img", "source", "image", "track")

    /** 镜像 purify.js DEFAULT_FORBID_CONTENTS（禁用时连同内容删除）。 */
    private val FORBID_CONTENTS: Set<String> = setOf(
        "annotation-xml", "audio", "colgroup", "desc", "foreignobject", "head", "iframe", "math", "mi", "mn", "mo",
        "ms", "mtext", "noembed", "noframes", "noscript", "plaintext", "script", "style", "svg", "template",
        "thead", "title", "video", "xmp",
    )

    private val ALLOWED_SCHEMES = listOf(
        "http:", "https:", "ftp:", "mailto:", "tel:", "callto:", "sms:", "cid:", "xmpp:", "matrix:",
    )

    private val MEDIA_TAGS = setOf("audio", "video", "source", "track", "embed", "object", "img")

    /** 镜像 IS_ALLOWED_URI：javascript:/vbscript:/data:(非媒体) 等被拒；相对路径按官方语义。 */
    fun isAllowedUri(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return true
        val first = v.first()
        // 不以字母开头（# / . ? 相对/锚点/协议相对）→ 允许
        if (!first.isLetter()) return true
        if (ALLOWED_SCHEMES.any { v.startsWith(it, ignoreCase = true) }) return true
        // scheme-like 前缀：后随非 [a-zA-Z+.\\-:] 或字符串结束才允许（javascript: 被拒）
        val scheme = Regex("^[a-zA-Z+.\\-]+").find(v)?.value ?: return true
        val rest = v.substring(scheme.length)
        return rest.isEmpty() || rest.first() != ':'
    }

    private fun isValidAttribute(tag: String, name: String, value: String): Boolean {
        if (name in URI_SAFE_ATTRS) return true
        if (isAllowedUri(value)) return true
        if ((name == "src" || name == "xlink:href" || name == "href") &&
            value.startsWith("data:") && tag in DATA_URI_TAGS
        ) {
            return true
        }
        // ALLOW_UNKNOWN_PROTOCOLS=false（官方默认）
        return false
    }

    /** class 值：custom- 前缀化（fa-{star}/note-{star}/monospace 保留），镜像 chats.js uponSanitizeAttribute。 */
    fun sanitizeClass(value: String): Set<String> {
        return value.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { v ->
                when {
                    v.startsWith("fa-") || v.startsWith("note-") || v == "monospace" -> v
                    else -> "custom-$v"
                }
            }.toSet()
    }

    /**
     * 保守内联 style 消毒（我们是原生 CSS 解释器，比 DOMPurify 更严）：
     * 剔除 url()/expression/behavior/-moz-binding/@import/javascript:/vbscript: 等危险声明。
     * 返回保留的 declarations（prop -> value，已小写属性名）。
     */
    fun sanitizeInlineStyle(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (decl in raw.split(';')) {
            val idx = decl.indexOf(':')
            if (idx <= 0) continue
            val prop = decl.substring(0, idx).trim().lowercase()
            val value = decl.substring(idx + 1).trim()
            if (prop.isEmpty() || value.isEmpty()) continue
            if (prop.contains("expression") || prop.contains("behavior") || prop == "-moz-binding" ||
                prop.contains("url(")
            ) {
                continue
            }
            val vlow = value.lowercase()
            if (vlow.contains("url(") || vlow.contains("expression") || vlow.contains("@import") ||
                vlow.contains("javascript:") || vlow.contains("vbscript:") || vlow.contains("-moz-binding") ||
                vlow.contains("behavior:")
            ) {
                continue
            }
            out[prop] = value
        }
        return out
    }

    /** 外部 URL 判定（镜像 chats.js isExternalUrl：含 :// 或以 // 开头）。 */
    fun isExternalUrl(url: String): Boolean =
        url.contains("://") || url.startsWith("//")

    /**
     * 消毒入口。
     * @return 可见 DOM 树 + 样式块规则（官方 scoped 语义）。
     */
    fun sanitize(raw: String, config: Config = Config()): SanitizeResult {
        val doc = Jsoup.parseBodyFragment(raw)
        val styleRules = ArrayList<ScopedCssRule>()
        // 合成根节点：承载整条消息（对应官方 .mes_text 容器语义，供后代选择器匹配起点）
        val children = sanitizeChildren(doc.body(), config, styleRules)
        val root = SanitizedNode.Tag("body", emptyMap(), children)
        return SanitizeResult(root = root, styleRules = styleRules)
    }

    private fun sanitizeChildren(
        parent: org.jsoup.nodes.Element,
        config: Config,
        styleRules: MutableList<ScopedCssRule>,
    ): List<SanitizedNode> {
        val out = ArrayList<SanitizedNode>()
        for (child in parent.childNodes()) {
            sanitizeNode(child, config, styleRules, out)
        }
        return out
    }

    private fun sanitizeNode(
        node: Node,
        config: Config,
        styleRules: MutableList<ScopedCssRule>,
        out: MutableList<SanitizedNode>,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotEmpty()) out += SanitizedNode.Text(text)
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when {
                    // style 块：抽取并加工成 scoped 规则，不进入可见树
                    tag == "style" && config.styleBlocksAllowed -> {
                        styleRules += CssStyleParser.parseScopedBlock(node.html(), config.externalMediaAllowed)
                    }
                    // 官方 custom-style 往返保护（encodeStyleTags/decodeStyleTags 语义）：解码后解析
                    tag == "custom-style" && config.styleBlocksAllowed -> {
                        val raw = node.html()
                        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrElse { raw }
                        styleRules += CssStyleParser.parseScopedBlock(decoded.replace("<br/>", ""), config.externalMediaAllowed)
                    }
                    // 禁用且内容连坐（script/style 等）→ 整体删除
                    tag !in ALLOWED_TAGS && tag in FORBID_CONTENTS -> {
                        // drop entirely
                    }
                    // 禁用但 KEEP_CONTENT → 子节点上提（未知元素降级为文本）
                    tag !in ALLOWED_TAGS -> {
                        val kept = sanitizeChildren(node, config, styleRules)
                        // 镜像 HTMLUnknownElement 钩子：换行→<br>（<pre> 内跳过）
                        if (node.hasAncestorWithTag("pre")) {
                            out += kept
                        } else {
                            for (k in kept) {
                                when (k) {
                                    is SanitizedNode.Text -> {
                                        val parts = k.text.split("\n")
                                        parts.forEachIndexed { i, part ->
                                            if (i > 0) out += SanitizedNode.Tag("br", emptyMap(), emptyList())
                                            if (part.isNotEmpty()) out += SanitizedNode.Text(part)
                                        }
                                    }
                                    else -> out += k
                                }
                            }
                        }
                    }
                    // 媒体规则：外部媒体禁用时删除外部 URL 媒体节点
                    tag in MEDIA_TAGS && !config.externalMediaAllowed && mediaIsExternal(node) -> {
                        // drop node (含子节点，镜像 node.remove())
                    }
                    else -> {
                        val attrs = sanitizeAttributes(tag, node)
                        val children = sanitizeChildren(node, config, styleRules)
                        out += SanitizedNode.Tag(tag, attrs, children)
                    }
                }
            }
            // 注释/未知节点类型忽略
            else -> Unit
        }
    }

    private fun Element.hasAncestorWithTag(tag: String): Boolean {
        var p = parent()
        while (p != null) {
            if (p.tagName().lowercase() == tag) return true
            p = p.parent()
        }
        return false
    }

    private fun mediaIsExternal(node: Element): Boolean {
        val src = node.attr("src")
        val data = node.attr("data")
        val srcset = node.attr("srcset")
        if (srcset.isNotEmpty() && srcset.split(',').any { raw ->
                val url = raw.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                isExternalUrl(url)
            }
        ) {
            return true
        }
        if (src.isNotEmpty() && isExternalUrl(src)) return true
        if (data.isNotEmpty() && isExternalUrl(data)) return true
        return false
    }

    private fun sanitizeAttributes(tag: String, el: Element): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (attr in el.attributes()) {
            val name = attr.key.lowercase()
            val value = attr.value.trim()
            // data-* / aria-* 放行
            val allowed = name in ALLOWED_ATTRS || name.startsWith("data-") || name.startsWith("aria-")
            if (!allowed) continue
            if (!isValidAttribute(tag, name, value)) continue
            out[name] = when (name) {
                "class" -> sanitizeClass(value).joinToString(" ")
                "style" -> sanitizeInlineStyle(value).entries.joinToString(";") { "${it.key}:${it.value}" }
                else -> value
            }
        }
        return out
    }
}
