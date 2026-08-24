package com.emberinn.app.renderer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 进程级内核池持有者（架构级冷启动修复，2026-08-24）。
 *
 * 官方 SillyTavern 是浏览器常驻页签：页面只加载一次，之后切聊天/切会话都在同一份
 * 活着的 DOM 上增量写消息，所以"点进去就有"。等价架构 = 池与内核页随进程存活、
 * 整个生命周期只建一次；ChatScreen 每次只是取用，不再负责创建与销毁。
 *
 * 此前池建在 ChatScreen 的 remember{} 里，两大恶果：
 * ①每次进入聊天都从零建 WebView + 加载内核页（低端机空白 8~10s）；
 * ②离开聊天旧池失去引用但 WebView 仍存活（泄漏），多次进出后多个实例挤爆
 *   共享渲染进程 → 进程被杀 → 开场白出现 1~2s 后消失只剩背景（9.2b 主诉）。
 *
 * 红线遵守：单实例串行预热，禁止并发创建（9.1 教训）；预热推迟到首帧之后，不抢启动。
 */
object KernelPoolHolder {
    private const val TAG = "EmberInnKernel"

    /** 首帧后延迟预热：WebView 创建与页面加载不与启动首帧抢主线程 */
    private const val WARM_DELAY_MS = 600L

    @Volatile
    private var pool: KernelWebViewPool? = null

    /** 取进程级池；不存在则创建并安排预热。context 一律转 applicationContext 防泄漏 Activity。 */
    fun get(context: Context): KernelWebViewPool =
        pool ?: synchronized(this) {
            pool ?: create(context.applicationContext)
        }

    /** 应用入口尽早调用（MainActivity.onCreate）：确保池存在并安排空闲预热（幂等）。 */
    fun warm(context: Context) {
        get(context)
    }

    private fun create(appContext: Context): KernelWebViewPool =
        KernelWebViewPool(appContext).also { created ->
            pool = created
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { created.preload() }
                    .onFailure { Log.e(TAG, "内核预热调度失败", it) }
            }, WARM_DELAY_MS)
        }
}
