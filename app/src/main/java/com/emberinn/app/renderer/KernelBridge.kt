package com.emberinn.app.renderer

import android.webkit.JavascriptInterface

/**
 * WebBridge：内核 WebView 的唯一 native 通道（window.AndroidKernel）。
 *
 * 放开模式原则（docs/REFACTOR_V2_PLAN.md §5.3）：桥的能力面主动提供实用功能
 * （开链接/复制/存图/触感），让卡片交互能触达系统能力；但不存在任意 Android
 * API / 文件系统 / Intent 通道——这是 WebView 架构边界而非安全洁癖。
 */
class KernelBridge(
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onKernelReady()
        fun onHeightChanged(mesid: String, heightDp: Float)
        fun onMessageClicked(mesid: String, target: KernelClickTarget?)
        fun onMessageLongPressed(mesid: String)
    }

    @JavascriptInterface
    fun postMessage(json: String) {
        val event = runCatching {
            KernelProtocol.json.decodeFromString(KernelEvent.serializer(), json)
        }.getOrNull() ?: return

        when (event.type) {
            KernelEventType.KERNEL_READY -> callbacks.onKernelReady()
            KernelEventType.HEIGHT, KernelEventType.HEIGHT_CHANGED ->
                event.mesid?.let { id -> callbacks.onHeightChanged(id, event.height ?: 0f) }
            KernelEventType.CLICK -> callbacks.onMessageClicked(event.mesid ?: "", event.target)
            KernelEventType.LONG_PRESS -> callbacks.onMessageLongPressed(event.mesid ?: "")
        }
    }
}
