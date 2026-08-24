package com.emberinn.app.renderer

import android.os.SystemClock

/**
 * 内核诊断事件史（「内核体检」面板数据源，用户要求黑匣子可见化——不依赖 logcat）。
 *
 * 记录内核全生命周期事实：实例创建/加载耗时、kernelReady、渲染进程崩溃、
 * 每次 renderChat 载荷数、JS 侧 clearMessages、页面 console 错误等。
 * 有界环形（新事件在前），随体检 JSON 一起展示/复制。
 */
object KernelDiagnostics {
    private const val MAX_EVENTS = 80

    private val events = ArrayDeque<String>()

    /** 进程启动基准：事件时间 = 相对启动秒数，跨会话可对齐 */
    private val bootAt = SystemClock.elapsedRealtime()

    fun log(message: String) {
        val t = ((SystemClock.elapsedRealtime() - bootAt) / 100f) / 10f // 启动后秒数，1 位小数
        val line = "t+${t}s $message"
        synchronized(events) {
            events.addFirst(line)
            while (events.size > MAX_EVENTS) events.removeLast()
        }
    }

    /** 新→旧的事件行快照 */
    fun dump(): List<String> = synchronized(events) { events.toList() }
}
