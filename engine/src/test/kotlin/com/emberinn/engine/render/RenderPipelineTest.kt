package com.emberinn.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路线 A 引擎层端到端测试：消毒 → 样式块 → 树构建。
 * 用例覆盖：白名单/属性消毒、style 属性保守过滤、独立 style 块（前缀化/@import/外部媒体）、
 * details 原生交互、未知标签降级、脚本删除、媒体规则、后代/子选择器匹配。
 */
class RenderPipelineTest {

    private fun render(raw: String, config: HtmlSanitizerEngine.Config = HtmlSanitizerEngine.Config()): RenderNode {
        val result = HtmlSanitizerEngine.sanitize(raw, config)
        return RenderStyleResolver.resolve(result.root, result.styleRules)
    }

    private fun RenderNode.root(): RenderNode.Element = this as RenderNode.Element

    private fun RenderNode.Element.child(i: Int): RenderNode = children[i]

    private fun RenderNode.Element.texts(): List<String> {
        val out = ArrayList<String>()
        fun walk(n: RenderNode) {
            when (n) {
                is RenderNode.Text -> out += n.text
                is RenderNode.Element -> n.children.forEach { walk(it) }
            }
        }
        walk(this)
        return out
    }

    // ---------------- 基础树 ----------------

    @Test
    fun `basic p and strong map to element tree`() {
        val root = render("<p>hello <strong>world</strong></p>").root()
        assertEquals("body", root.tag)
        val p = root.child(0) as RenderNode.Element
        assertEquals("p", p.tag)
        assertEquals("hello ", (p.child(0) as RenderNode.Text).text)
        val strong = p.child(1) as RenderNode.Element
        assertEquals("strong", strong.tag)
        assertEquals(700, strong.style.fontWeight)
    }

    @Test
    fun `text-only content is preserved`() {
        val root = render("plain text").root()
        assertEquals(listOf("plain text"), root.texts())
    }

    // ---------------- style 属性 ----------------

    @Test
    fun `inline style color fontsize and background parsed`() {
        val root = render("<p style=\"color:#ff0000;font-size:20px;background-color:rgb(0,128,0)\">x</p>").root()
        val p = root.child(0) as RenderNode.Element
        val s = p.style
        assertEquals(1f, s.color!!.r, 0.01f)
        assertEquals(0f, s.color!!.g, 0.01f)
        assertEquals(20f, s.fontSizePx!!, 0.01f)
        assertEquals(0f, s.backgroundColor!!.r, 0.01f)
        assertEquals(128f / 255f, s.backgroundColor!!.g, 0.01f)
    }

    @Test
    fun `inline style margin box parsed`() {
        val root = render("<div style=\"margin:4px 8px 12px 16px\">x</div>").root()
        val div = root.child(0) as RenderNode.Element
        val m = div.style.margin
        assertEquals(4f, (m.top as CssLength.Px).value, 0.01f)
        assertEquals(8f, (m.right as CssLength.Px).value, 0.01f)
        assertEquals(12f, (m.bottom as CssLength.Px).value, 0.01f)
        assertEquals(16f, (m.left as CssLength.Px).value, 0.01f)
    }

    @Test
    fun `dangerous inline style declarations removed`() {
        val root = render(
            "<div style=\"color:red;background-image:url(javascript:alert(1));behavior:url(x);-moz-binding:url(x);" +
                "content:expression(alert(1));display:block\">x</div>"
        ).root()
        val div = root.child(0) as RenderNode.Element
        val s = div.style
        assertNotNull(s.color)
        assertEquals("block", s.display)
        assertNull(s.backgroundImage)
    }

    @Test
    fun `javascript href rejected by uri check`() {
        val root = render("<a href=\"javascript:alert(1)\" target=\"_blank\">x</a>").root()
        val a = root.child(0) as RenderNode.Element
        assertEquals("a", a.tag)
        assertNull(a.attrs["href"]) // 危险 href 被剔除，但标签保留（KEEP_CONTENT）
        assertEquals(InteractiveKind.Link, a.interactive)
    }

    @Test
    fun `normal href kept`() {
        val root = render("<a href=\"https://example.com/x\">x</a>").root()
        val a = root.child(0) as RenderNode.Element
        assertEquals("https://example.com/x", a.attrs["href"])
    }

    // ---------------- class 前缀化 ----------------

    @Test
    fun `class is custom prefixed but fa preserved`() {
        val root = render("<div class=\"box fa-star monospace\">x</div>").root()
        val div = root.child(0) as RenderNode.Element
        assertEquals(setOf("custom-box", "fa-star", "monospace"), div.classes)
    }

    // ---------------- 独立 style 块 ----------------

    @Test
    fun `style block selector gets custom prefix and applies`() {
        val raw = "<style>.red { color: #ff0000 }</style><p class=\"red\">x</p>"
        val root = render(raw).root()
        val p = root.child(0) as RenderNode.Element
        assertEquals("p", p.tag)
        assertEquals(1f, p.style.color!!.r, 0.01f)
    }

    @Test
    fun `style block import removed and external declaration filtered when media forbidden`() {
        val raw = "<style>@import url(http://evil.css);.a { color: red; background-image: url(https://x.com/i.png) }</style><div class=\"a\">x</div>"
        val result = HtmlSanitizerEngine.sanitize(raw)
        assertTrue(result.styleRules.none { it.selector.contains("import") })
        val div = RenderStyleResolver.resolve(result.root, result.styleRules).root().child(0) as RenderNode.Element
        assertNotNull(div.style.color)
        assertNull(div.style.backgroundImage) // 外部 URL 声明默认被过滤
    }

    @Test
    fun `descendant and child combinator selectors match`() {
        val raw = "<style>.outer .inner { color: #0000ff } .outer > .direct { font-weight: 700 }</style>" +
            "<div class=\"outer\"><div class=\"inner\">a</div><div><div class=\"inner\">b</div></div><div class=\"direct\">c</div></div>"
        val root = render(raw).root()
        val outer = root.child(0) as RenderNode.Element
        val inner1 = outer.child(0) as RenderNode.Element
        val wrapper = outer.child(1) as RenderNode.Element
        val inner2 = wrapper.child(0) as RenderNode.Element
        val direct = outer.child(2) as RenderNode.Element
        // 后代选择器：深层 inner 也命中
        assertEquals(1f, inner1.style.color!!.b, 0.01f)
        assertEquals(1f, inner2.style.color!!.b, 0.01f)
        // 子选择器：仅直接子命中，内层不命中
        assertEquals(700, direct.style.fontWeight)
        assertNull(inner1.style.fontWeight)
    }

    @Test
    fun `inline style beats style block`() {
        val raw = "<style>.x { color: #ff0000 }</style><p class=\"x\" style=\"color:#00ff00\">a</p>"
        val root = render(raw).root()
        val p = root.child(0) as RenderNode.Element
        assertEquals(0f, p.style.color!!.r, 0.01f)
        assertEquals(1f, p.style.color!!.g, 0.01f)
    }

    // ---------------- details 原生交互 ----------------

    @Test
    fun `details and summary are preserved as native interactive`() {
        val root = render("<details><summary>title</summary><p>body</p></details>").root()
        val details = root.child(0) as RenderNode.Element
        assertEquals("details", details.tag)
        assertEquals(InteractiveKind.Details, details.interactive)
        val summary = details.child(0) as RenderNode.Element
        assertEquals(InteractiveKind.Summary, summary.interactive)
        assertEquals(listOf("title", "body"), details.texts())
    }

    @Test
    fun `details open attribute preserved`() {
        val root = render("<details open><summary>t</summary><p>b</p></details>").root()
        val details = root.child(0) as RenderNode.Element
        assertTrue(details.isOpen)
    }

    // ---------------- 降级 / 删除 ----------------

    @Test
    fun `unknown element degrades to its text content`() {
        val root = render("<giggle>hello</giggle>").root()
        assertEquals(listOf("hello"), root.texts())
    }

    @Test
    fun `script and iframe removed entirely`() {
        val root = render("<script>alert(1)</script><iframe src=\"https://x.com\"></iframe><p>keep</p>").root()
        assertEquals(listOf("keep"), root.texts())
    }

    @Test
    fun `unknown element with newline inside becomes br outside pre`() {
        val root = render("<customtag>a\nb</customtag>").root()
        val nodes = root.children
        assertTrue(nodes.any { it is RenderNode.Element && it.tag == "br" })
    }

    // ---------------- 媒体规则 ----------------

    @Test
    fun `external image dropped when media forbidden`() {
        val root = render("<img src=\"https://x.com/a.png\"><img src=\"data:image/png;base64,AAAA\">").root()
        val imgs = root.children.filterIsInstance<RenderNode.Element>().filter { it.tag == "img" }
        assertEquals(1, imgs.size)
        assertEquals("data:image/png;base64,AAAA", imgs[0].attrs["src"])
    }

    @Test
    fun `external image kept when media allowed`() {
        val config = HtmlSanitizerEngine.Config(externalMediaAllowed = true)
        val root = render("<img src=\"https://x.com/a.png\">", config).root()
        val imgs = root.children.filterIsInstance<RenderNode.Element>().filter { it.tag == "img" }
        assertEquals(1, imgs.size)
        assertEquals("https://x.com/a.png", imgs[0].attrs["src"])
    }

    // ---------------- 其它标签语义 ----------------

    @Test
    fun `heading gets default block and font size`() {
        val root = render("<h1>t</h1>").root()
        val h1 = root.child(0) as RenderNode.Element
        assertEquals("h1", h1.tag)
        assertTrue(h1.style.isBlock)
        assertEquals(32f, h1.style.fontSizePx!!, 0.01f) // 2em @ 16px
    }

    @Test
    fun `code and pre get monospace`() {
        val root = render("<pre><code>val x = 1</code></pre>").root()
        val pre = root.child(0) as RenderNode.Element
        assertTrue(pre.style.whiteSpace == "pre")
        val code = pre.child(0) as RenderNode.Element
        assertTrue(code.style.fontFamily!!.contains("monospace"))
    }

    @Test
    fun `media tags get inline-block display`() {
        val root = render("<img src=\"data:image/png;base64,AAAA\"><video src=\"data:video/mp4;base64,BBBB\"></video>").root()
        val img = root.child(0) as RenderNode.Element
        assertEquals(InteractiveKind.Image, img.interactive)
        val video = root.child(1) as RenderNode.Element
        assertEquals(InteractiveKind.Video, video.interactive)
    }
}
