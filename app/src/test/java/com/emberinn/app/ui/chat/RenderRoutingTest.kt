package com.emberinn.app.ui.chat

import com.emberinn.engine.render.InteractiveKind
import com.emberinn.engine.render.RenderNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段四隔离复验（JVM，无 UI）：验证阶段三重建的内容分流与路线 A 解析管线在数据层正确工作。
 * 路由判定（isStaticHtml）+ 静态 HTML 解析（parseStaticHtml → RenderNode 树）均为纯 JVM 逻辑。
 */
class RenderRoutingTest {

    // ---------------- 内容分流（路线 A / 路线 B） ----------------

    @Test
    fun `static html goes to route A`() {
        val samples = listOf(
            "<div style=\"color:red\">x</div>",
            "<details><summary>t</summary><p>b</p></details>",
            "<style>.a{color:#fff}</style><p class=\"a\">x</p>",
            "<img src=\"data:image/png;base64,AAAA\">",
            "<table><tr><td>a</td></tr></table>",
            "<p>你好，<strong>冒险者</strong>！</p>",
        )
        samples.forEach { s ->
            assertTrue("应判为静态（路线A）: $s", isStaticHtml(s))
        }
    }

    @Test
    fun `dynamic html goes to route B`() {
        val samples = listOf(
            "<script>alert(1)</script><p>x</p>",
            "<div onclick=\"go()\">x</div>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>",
        )
        samples.forEach { s ->
            assertEquals("应判为动态（路线B）: $s", false, isStaticHtml(s))
        }
    }

    // ---------------- 路线 A：静态 HTML → 渲染树 ----------------

    @Test
    fun `representative card html parses to render tree with styles and details`() {
        val html = """
            <style>.status{color:#00ff00;background:#111111}.slim{max-width:100%}</style>
            <div class="status slim" style="margin:4px 0">
              <details open><summary>状态</summary><p>HP: 100/100</p></details>
              <p>你好，<strong>冒险者</strong>！</p>
              <ul><li>武器</li><li>护甲</li></ul>
            </div>
        """.trimIndent()
        val root = parseStaticHtml(html)
        assertTrue(root is RenderNode.Element)
        val div = (root as RenderNode.Element).children.filterIsInstance<RenderNode.Element>()
            .firstOrNull { it.tag == "div" && it.classes.contains("custom-status") }
        assertTrue("style 块选择器应命中 div", div != null)
        val details = div!!.children.filterIsInstance<RenderNode.Element>()
            .firstOrNull { it.tag == "details" }
        assertTrue("details 应保留为原生交互", details != null)
        assertEquals(InteractiveKind.Details, details!!.interactive)
        assertTrue("open 属性应保留", details.isOpen)
        // 内联样式 + style 块合并：颜色来自 style 块
        assertEquals(1f, div.style.color!!.g, 0.01f)
    }

    @Test
    fun `dangerous content is stripped not fatal`() {
        val html = """
            <script>alert(1)</script>
            <iframe src="https://x.com"></iframe>
            <div style="background-image:url(javascript:alert(1));color:red">ok</div>
            <img src="https://external.example/a.png">
        """.trimIndent()
        val root = parseStaticHtml(html)
        val texts = buildList {
            fun walk(n: RenderNode) {
                when (n) {
                    is RenderNode.Text -> add(n.text)
                    is RenderNode.Element -> n.children.forEach { walk(it) }
                }
            }
            walk(root)
        }
        val joined = texts.joinToString("")
        assertTrue("可见文本应含 ok: $joined", joined.contains("ok"))
        assertTrue("script 内容不得上浮: $joined", !joined.contains("alert"))
        assertTrue("外部 iframe 内容不得上浮: $joined", !joined.contains("x.com"))
        val div = (root as RenderNode.Element).children.filterIsInstance<RenderNode.Element>()
            .firstOrNull { it.tag == "div" }
        assertNull("危险 background-image 应被过滤", div?.style?.backgroundImage)
    }

    @Test
    fun `external media forbidden by default in route A`() {
        val root = parseStaticHtml(
            "<img src=\"https://x.com/a.png\"><img src=\"data:image/png;base64,AAAA\">",
        )
        val imgs = (root as RenderNode.Element).children
            .filterIsInstance<RenderNode.Element>()
            .filter { it.tag == "img" }
        assertEquals(1, imgs.size)
        assertEquals("data:image/png;base64,AAAA", imgs[0].src)
    }

    @Test
    fun `unknown tags degrade to text not fatal`() {
        val root = parseStaticHtml("<giggle>hello</giggle><customtag>a\nb</customtag>")
        val texts = buildList {
            fun walk(n: RenderNode) {
                when (n) {
                    is RenderNode.Text -> add(n.text)
                    is RenderNode.Element -> n.children.forEach { walk(it) }
                }
            }
            walk(root)
        }
        assertTrue(texts.joinToString("").contains("hello"))
        assertTrue(texts.joinToString("").contains("a"))
    }
}
