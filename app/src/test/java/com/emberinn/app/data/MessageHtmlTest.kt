package com.emberinn.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 完整标签判定：对齐官方 Showdown/DOMPurify 的“自定义/无属性标签也按 HTML 可见”语义。 */
class MessageHtmlTest {

    @Test
    fun `custom tags without attributes are html`() {
        assertTrue(MessageHtml.looksLikeHtml("<UpdateVariable>\n<initvar>\n状态: 1\n</initvar>\n</UpdateVariable>正文"))
        assertTrue(MessageHtml.looksLikeHtml("<inner>内心戏</inner>"))
        assertTrue(MessageHtml.looksLikeHtml("<StatusPlaceHolderImpl/>"))
        assertTrue(MessageHtml.looksLikeHtml("前文</inner>结尾"))
        assertTrue(MessageHtml.looksLikeHtml("<div class=\"card\">hi</div>"))
    }

    @Test
    fun `plain comparisons are not html`() {
        assertFalse(MessageHtml.looksLikeHtml("a<b"))
        assertFalse(MessageHtml.looksLikeHtml("x<10,y=20"))
        assertFalse(MessageHtml.looksLikeHtml("1 < 2"))
        assertFalse(MessageHtml.looksLikeHtml("a < b"))
        assertFalse(MessageHtml.looksLikeHtml("I <3 you"))
        assertFalse(MessageHtml.looksLikeHtml("纯文字没有尖括号"))
    }

    @Test
    fun `fenced html is ignored by lookahead but fence itself renders as card`() {
        assertFalse(MessageHtml.looksLikeHtml("```html\n<p>hi</p>\n```"))
    }

    @Test
    fun `single complete tag still counts`() {
        assertTrue(MessageHtml.looksLikeHtml("<custom-tag>"))
        assertTrue(MessageHtml.looksLikeHtml("</custom-tag>"))
        assertTrue(MessageHtml.looksLikeHtml("<video controls>"))
    }
}
