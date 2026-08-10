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
 * 只允许在主线程访问；闲置池上限 6 个，超出即销毁，防止长聊天把内存撑爆。
 */
object WebViewPool {
    private const val MAX_IDLE = 6
    private val idle = ArrayDeque<WebView>()

    fun acquire(context: Context): WebView {
        val view = idle.removeLastOrNull() ?: WebView(context.applicationContext)
        (view.parent as? ViewGroup)?.removeView(view)
        view.tag = null
        return view
    }

    fun release(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.tag = null
        runCatching {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.webChromeClient = null
            // 丢弃旧页面的回调：复用后由新 WebViewClient 接管
            view.webViewClient = android.webkit.WebViewClient()
        }
        if (idle.size >= MAX_IDLE) {
            runCatching { view.destroy() }
        } else {
            idle.addLast(view)
        }
    }
}
