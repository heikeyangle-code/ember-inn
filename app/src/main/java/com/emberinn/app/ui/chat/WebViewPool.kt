package com.emberinn.app.ui.chat

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView

/**
 * 聊天列表 WebView 复用池。
 *
 * AndroidView 在 LazyColumn 里每滚出一条消息就会销毁/重建 WebView，而 WebView 的创建和
 * 冷启动开销很大（消息发出去变卡、列表滚动掉帧的主要来源之一）。这里把离开组合的 WebView
 * 停掉并回收到池里，下一条 HTML / 交互卡片消息直接复用同一实例。
 *
 * 与旧版不同：release 不再 loadUrl("about:blank") 清空页面——已渲染页面和 WebViewSession
 * （含实测高度）随实例一起保留，滚回来看同一条 HTML 时不重载、直接恢复高度，
 * 消除“每次滚回来整页重载 + 高度从 0 重新长”的卡顿/截断。只暂停 JS/动画以省电；
 * 在途加载会在 release 时中断，回来后由 update 自动重载。
 *
 * 只允许在主线程访问；闲置池上限 6 个，超出即销毁，防止长聊天把内存撑爆。
 */
object WebViewPool {
    private const val MAX_IDLE = 6
    private val idle = ArrayDeque<WebView>()

    fun acquire(context: Context): WebView {
        val view = idle.removeLastOrNull() ?: WebView(context.applicationContext)
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    fun release(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        // 中断在途加载：回来后 update 发现 loadToken==null 且未 loaded 会自动重载；
        // 已加载完成的页面保留内容与高度。
        val session = view.tag as? WebViewSession
        if (session != null) session.loadToken = null
        runCatching {
            view.stopLoading()
            view.onPause()
        }
        if (idle.size >= MAX_IDLE) {
            runCatching { view.destroy() }
        } else {
            idle.addLast(view)
        }
    }
}
