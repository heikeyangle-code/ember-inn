package com.emberinn.app.renderer

import android.content.Context
import android.webkit.WebView

/**
 * WebView 渲染进程预热器：App 启动早期建一个临时实例再销毁，
 * 让系统提前拉起 chromium 渲染进程（进程冷启 = 聊天页首帧延迟的大头）。
 * 只碰进程，不加载任何页面、不占内存（WebView 即刻 destroy）。
 */
object Warmer {
    @Volatile private var done = false
    fun touch(context: Context) {
        if (done) return
        done = true
        runCatching {
            val w = WebView(context)
            w.destroy()
        }
    }
}
