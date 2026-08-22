package com.emberinn.app.renderer

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume

/**
 * 池化内核 WebView 管理（docs/REFACTOR_V2_PLAN.md §3.3）：
 *
 * - 预热 [warmup] 个实例（kernel.html 一次加载，后续 renderMessage 换内容不换页面，
 *   消除首帧延迟）
 * - 软上限 [maxSize]
 * - 高度/点击事件经桥回传给 ChatSurface
 */
class KernelWebViewPool(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val warmup: Int = 2,
    private val maxSize: Int = 8,
) {
    class PooledWebView internal constructor(
        val webView: WebView,
        val loader: WebViewAssetLoader,
        internal var ready: Boolean = false,
        internal var busy: Boolean = false,
    )

    private val assetLoader: WebViewAssetLoader by lazy { KernelWebViewFactory.createAssetLoader(context) }
    private val idle = ConcurrentLinkedDeque<PooledWebView>()
    private val all = java.util.concurrent.ConcurrentHashMap.newKeySet<PooledWebView>()

    /** 高度回报：(mesid, heightPx) */
    private val heightListeners = mutableListOf<(String, Float) -> Unit>()
    private val clickListeners = mutableListOf<(String, KernelClickTarget?) -> Unit>()
    private val longPressListeners = mutableListOf<(String) -> Unit>()

    fun addHeightListener(l: (mesid: String, heightDp: Float) -> Unit) { synchronized(heightListeners) { heightListeners.add(l) } }
    fun addClickListener(l: (mesid: String, target: KernelClickTarget?) -> Unit) { synchronized(clickListeners) { clickListeners.add(l) } }
    fun addLongPressListener(l: (mesid: String) -> Unit) { synchronized(longPressListeners) { longPressListeners.add(l) } }

    fun preload() {
        scope.launch(Dispatchers.Main) {
            repeat(warmup) { runCatching { createAndWarm() } }
        }
    }

    private fun makeBridge(instance: PooledWebView): KernelBridge {
        return KernelBridge(object : KernelBridge.Callbacks {
            override fun onKernelReady() {
                // JavascriptInterface 回调线程 → 标记就绪即可；等待方通过轮询/挂起恢复
                instance.ready = true
            }

            override fun onHeightChanged(mesid: String, heightDp: Float) {
                synchronized(heightListeners) { heightListeners.toList() }.forEach { it(mesid, heightDp) }
            }

            override fun onMessageClicked(mesid: String, target: KernelClickTarget?) {
                synchronized(clickListeners) { clickListeners.toList() }.forEach { it(mesid, target) }
            }

            override fun onMessageLongPressed(mesid: String) {
                synchronized(longPressListeners) { longPressListeners.toList() }.forEach { it(mesid) }
            }
        })
    }

    /**
     * 创建并预热一个实例：加载 kernel.html 并挂起等待 kernelReady。
     * 必须在主线程调用。
     */
    private suspend fun createAndWarm(): PooledWebView {
        val instance = PooledWebView(
            webView = WebView(context),
            loader = assetLoader,
        )
        synchronized(all) { all.add(instance) }
        instance.webView.let { web ->
            web.addJavascriptInterface(makeBridge(instance), KernelProtocol.BRIDGE_NAME)
            web.webViewClient = KernelWebViewClient(assetLoader, instance.webView.context)
        }
        suspendCancellableCoroutine { cont ->
            // 桥回调（后台线程）仅置位 ready；此处主线程轮询避免跨线程续体竞争
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val check = object : Runnable {
                override fun run() {
                    when {
                        instance.ready -> cont.resume(instance)
                        !cont.isActive -> Unit
                        else -> handler.postDelayed(this, 50)
                    }
                }
            }
            instance.webView.loadUrl(KernelProtocol.KERNEL_URL)
            handler.postDelayed(check, 50)
        }
        idle.addLast(instance)
        return instance
    }

    /** 取空闲实例并执行 [block]；池空则新建 */
    fun acquire(block: (PooledWebView) -> Unit) {
        scope.launch(Dispatchers.Main) {
            val existing = idle.pollFirst()
            if (existing != null) {
                existing.busy = true
                block(existing)
            } else if (synchronized(all) { all.size } < maxSize) {
                val fresh = runCatching { createAndWarm() }.getOrNull() ?: return@launch
                fresh.busy = true
                block(fresh)
            } else {
                // 池满兜底：直接新建不设限（内存压力由系统回收机制与 release 端控制）
                val fresh = runCatching { createAndWarm() }.getOrNull() ?: return@launch
                fresh.busy = true
                block(fresh)
            }
        }
    }

    fun release(pooled: PooledWebView) {
        scope.launch(Dispatchers.Main) {
            pooled.webView.evaluateJavascript("window.Kernel && window.Kernel.clear();", null)
            pooled.busy = false
            idle.addFirst(pooled)
        }
    }

    fun destroy(pooled: PooledWebView) {
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.remove(pooled) }
            idle.remove(pooled)
            pooled.webView.destroy()
        }
    }

    fun destroyAll() {
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { it.webView.destroy() }
            synchronized(all) { all.clear() }
            idle.clear()
        }
    }
}
