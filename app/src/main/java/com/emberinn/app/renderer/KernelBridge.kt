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
        /** 官方消息控件动作（整页壳 C2）：mesid + 官方 class 动作名 */
        fun onMessageAction(mesid: String, action: String, value: String) {}
        fun onMessageLongPressed(mesid: String)
        /** st-api-shim 请求（P4）：method/params 由上层 handler 解答，respond 回送 JS */
        fun onShimRequest(reqId: String, method: String, paramsJson: String) {}
        /** 白名单宿主能力请求（§5.3）：action ∈ KernelHostAction，value 为 URL/文本等参数 */
        fun onHostAction(action: String, value: String) {}
        /** #chat 滚动贴底状态（整页壳 C1/C2）：true=在底部（跳底浮标隐藏） */
        fun onChatScroll(atBottom: Boolean) {}
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
            KernelEventType.CLICK -> {
                if (event.action != null) {
                    callbacks.onMessageAction(event.mesid ?: "", event.action, event.value ?: "")
                } else {
                    callbacks.onMessageClicked(event.mesid ?: "", event.target)
                }
            }
            KernelEventType.LONG_PRESS -> callbacks.onMessageLongPressed(event.mesid ?: "")
            KernelEventType.SHIM_REQUEST ->
                event.reqId?.let { id ->
                    callbacks.onShimRequest(id, event.method ?: "", event.params ?: "{}")
                }
            KernelEventType.HOST_REQUEST ->
                event.hostAction?.let { action -> callbacks.onHostAction(action, event.value ?: "") }
            KernelEventType.CHAT_SCROLL ->
                event.atBottom?.let { callbacks.onChatScroll(it) }
        }
    }
}
