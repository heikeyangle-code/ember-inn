package com.emberinn.app.data

/**
 * 消息“是否像 HTML”判定，对齐官方 messageFormatting → Showdown → DOMPurify → 浏览器的语义：
 * 官方把任意完整标签（包括无属性的自定义/扩展标签，如 <inner>、<UpdateVariable>、
 * <StatusPlaceHolderImpl/>）都按 HTML 解析，标签内文本在浏览器中始终可见；
 * 而 App 的原生 Markdown 渲染器（IntelliJ markdown）对 HTML 块不做任何渲染、会整块吞掉，
 * 导致这类消息显示空白。因此只要出现“完整标签”（开/闭/自闭合，含或不含属性），
 * 就应走 WebView 兜底，让浏览器按官方语义渲染。
 *
 * 纯文字比较式（a<b、x<10、1 < 2、a < b）没有完整的 >，或标签名不以字母开头，不会被误判。
 */
object MessageHtml {

    private val ANY_FENCE = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")

    /** 完整标签：<tag>、<tag/>、<tag a="b" c>、</tag>；属性区不含 < >。 */
    private val COMPLETE_TAG = Regex(
        "<[A-Za-z][A-Za-z0-9-]*(?:\\s+[^<>]*?)?\\s*/?>|</[A-Za-z][A-Za-z0-9-]*\\s*>",
    )

    fun looksLikeHtml(content: String): Boolean {
        val outsideFence = content.replace(ANY_FENCE, "")
        return COMPLETE_TAG.containsMatchIn(outsideFence)
    }
}
