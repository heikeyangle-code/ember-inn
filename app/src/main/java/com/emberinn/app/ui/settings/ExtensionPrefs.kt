package com.emberinn.app.ui.settings

import android.content.Context

/** 扩展插件偏好：每个功能独立开关，默认全开。
 *  htmlMessages 复用 RenderPrefs（ember_render/html_enabled），避免双份状态。
 *  关闭对应开关后行为：
 *  - interactiveCards：``` 内 HTML 代码块不再转 iframe，按普通代码块显示
 *  - messageJs：WebView 内 JavaScript 关闭（Mermaid 需要 JS，单独由 mermaid 开关决定）
 *  - networkMedia：远程图片/资源被拦截（同第 143 轮硬化）
 *  - externalLinks：http(s) 链接在 WebView 内打开，不再跳系统浏览器
 *  - autoHeight：iframe/页面高度不轮询，固定 420dp
 *  - avatarClasses：不注入 .char-avatar/.user-avatar CSS 与头像宏
 *  - codeFolding：交互块不显示“原代码”折叠区
 *  - mermaid：Mermaid 代码块按普通代码块显示
 *  - blockJavascriptUrls：javascript: 链接不再拦截（完全放行，仅建议关闭） */
object ExtensionPrefs {

    private const val NAME = "ember_extensions"

    private fun get(context: Context, key: String, def: Boolean = true): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(key, def)

    private fun set(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    fun interactiveCards(context: Context): Boolean = get(context, "interactive_cards")
    fun setInteractiveCards(context: Context, v: Boolean) = set(context, "interactive_cards", v)

    fun messageJs(context: Context): Boolean = get(context, "message_js")
    fun setMessageJs(context: Context, v: Boolean) = set(context, "message_js", v)

    fun networkMedia(context: Context): Boolean = get(context, "network_media")
    fun setNetworkMedia(context: Context, v: Boolean) = set(context, "network_media", v)

    fun externalLinks(context: Context): Boolean = get(context, "external_links")
    fun setExternalLinks(context: Context, v: Boolean) = set(context, "external_links", v)

    fun autoHeight(context: Context): Boolean = get(context, "auto_height")
    fun setAutoHeight(context: Context, v: Boolean) = set(context, "auto_height", v)

    fun avatarClasses(context: Context): Boolean = get(context, "avatar_classes")
    fun setAvatarClasses(context: Context, v: Boolean) = set(context, "avatar_classes", v)

    fun codeFolding(context: Context): Boolean = get(context, "code_folding")
    fun setCodeFolding(context: Context, v: Boolean) = set(context, "code_folding", v)

    fun mermaid(context: Context): Boolean = get(context, "mermaid")
    fun setMermaid(context: Context, v: Boolean) = set(context, "mermaid", v)

    fun blockJavascriptUrls(context: Context): Boolean = get(context, "block_javascript_urls")
    fun setBlockJavascriptUrls(context: Context, v: Boolean) = set(context, "block_javascript_urls", v)
}
