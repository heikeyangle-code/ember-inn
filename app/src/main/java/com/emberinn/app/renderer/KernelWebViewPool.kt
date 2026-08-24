package com.emberinn.app.renderer

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import com.emberinn.app.ui.settings.RenderPrefs
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
    private val messageActionListeners = mutableListOf<(String, String, String) -> Unit>()
    private val longPressListeners = mutableListOf<(String) -> Unit>()
    /** 白名单宿主能力请求（§5.3）：(action, value) */
    private val uiActionListeners = mutableListOf<(String, String) -> Unit>()
    /** 渲染进程崩溃实例剔除回调：宿主行据此复位重挂载（§3.3 自愈） */
    private val crashListeners = mutableListOf<() -> Unit>()
    /** #chat 滚动贴底状态（整页壳 C1/C2）：驱动跳底浮标显隐 */
    private val chatScrollListeners = mutableListOf<(Boolean) -> Unit>()
    /** C3 官方输入区：#send_textarea 草稿镜像 + #form_sheld 高度回报 */
    private val inputListeners = mutableListOf<(String) -> Unit>()
    private val inputHeightListeners = mutableListOf<(Float) -> Unit>()

    fun addHeightListener(l: (mesid: String, heightDp: Float) -> Unit) { synchronized(heightListeners) { heightListeners.add(l) } }
    fun removeHeightListener(l: (mesid: String, heightDp: Float) -> Unit) { synchronized(heightListeners) { heightListeners.remove(l) } }
    fun addClickListener(l: (mesid: String, target: KernelClickTarget?) -> Unit) { synchronized(clickListeners) { clickListeners.add(l) } }
    fun removeClickListener(l: (mesid: String, target: KernelClickTarget?) -> Unit) { synchronized(clickListeners) { clickListeners.remove(l) } }
    fun addMessageActionListener(l: (mesid: String, action: String, value: String) -> Unit) {
        synchronized(messageActionListeners) { messageActionListeners.add(l) }
    }
    fun removeMessageActionListener(l: (mesid: String, action: String, value: String) -> Unit) {
        synchronized(messageActionListeners) { messageActionListeners.remove(l) }
    }
    fun addLongPressListener(l: (mesid: String) -> Unit) { synchronized(longPressListeners) { longPressListeners.add(l) } }
    fun addUiActionListener(l: (action: String, value: String) -> Unit) { synchronized(uiActionListeners) { uiActionListeners.add(l) } }
    fun removeUiActionListener(l: (action: String, value: String) -> Unit) { synchronized(uiActionListeners) { uiActionListeners.remove(l) } }
    fun addCrashListener(l: () -> Unit) { synchronized(crashListeners) { crashListeners.add(l) } }
    fun removeCrashListener(l: () -> Unit) { synchronized(crashListeners) { crashListeners.remove(l) } }
    fun addChatScrollListener(l: (atBottom: Boolean) -> Unit) { synchronized(chatScrollListeners) { chatScrollListeners.add(l) } }
    fun removeChatScrollListener(l: (atBottom: Boolean) -> Unit) { synchronized(chatScrollListeners) { chatScrollListeners.remove(l) } }
    fun addInputListener(l: (String) -> Unit) { synchronized(inputListeners) { inputListeners.add(l) } }
    fun removeInputListener(l: (String) -> Unit) { synchronized(inputListeners) { inputListeners.remove(l) } }
    fun addInputHeightListener(l: (Float) -> Unit) { synchronized(inputHeightListeners) { inputHeightListeners.add(l) } }
    fun removeInputHeightListener(l: (Float) -> Unit) { synchronized(inputHeightListeners) { inputHeightListeners.remove(l) } }

    /** C3 输入区页面级状态：随主题/崩溃重建一起全量同步（applyPageSetup） */
    data class InputState(val generating: Boolean = false, val swiping: Boolean = false)
    @Volatile private var currentInputState: InputState = InputState()

    /** 黑匣子：内核页创建/加载/握手任一环失败的宿主通知（此前全部静默吞掉 → 白屏无线索） */
    private val errorListeners = mutableListOf<(String) -> Unit>()
    fun addErrorListener(l: (String) -> Unit) { synchronized(errorListeners) { errorListeners.add(l) } }
    fun removeErrorListener(l: (String) -> Unit) { synchronized(errorListeners) { errorListeners.remove(l) } }

    /** 黑匣子上报：logcat 常驻 + 宿主监听者（主线程回调，可直接弹 toast）。 */
    fun reportError(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        scope.launch(Dispatchers.Main) {
            val full = message + (error?.let { "：${it.message}" } ?: "")
            synchronized(errorListeners) { errorListeners.toList() }.forEach { it(full) }
        }
    }

    /** 生成/滑动状态变更入口：广播到全部存活实例并记为页面状态 */
    fun updateInputState(generating: Boolean, swiping: Boolean = false) {
        currentInputState = InputState(generating, swiping)
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { RenderKernel(it).setInputState(generating, swiping) }
        }
    }

    /** C3 草稿/冒充流式下发：#send_textarea 全实例广播（内核回发 inputChanged 镜像收敛宿主态） */
    fun pushInputText(text: String) {
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { RenderKernel(it).pushInputText(text) }
        }
    }

    // C4 官方背景：页面级状态（崩溃重建/新实例随 applyPageSetup 恢复）
    @Volatile private var currentBackgroundUrl: String? = null
    @Volatile private var currentBackgroundFitting: String? = null

    /** 背景变更入口（backgrounds.js onChatChanged 同构）：url=null 清除，fitting 可选 cover/contain/stretch/center */
    fun updateBackground(url: String?, fitting: String? = null) {
        currentBackgroundUrl = url
        currentBackgroundFitting = fitting
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { RenderKernel(it).setBackground(url, fitting) }
        }
    }

    /** 整页壳 C2：单实例宿主专用；语义上仍复用池，但聊天页不再并发占用多个内核页。 */
    fun acquireSingle(block: (PooledWebView) -> Unit) = acquire(block)

    /**
     * st-api-shim 请求处理器（P4 扩展桥）：由宿主层安装（StApiShimInstaller）。
     * 在 WebView 桥后台线程回调；handler 自行调度协程并最终调用 respond(payloadJson)。
     */
    @Volatile var shimHandler: ((method: String, paramsJson: String, respond: (String) -> Unit) -> Unit)? = null
    fun removeLongPressListener(l: (mesid: String) -> Unit) { synchronized(longPressListeners) { longPressListeners.remove(l) } }

    // 每页主题状态：新建实例 ready 后自动应用；updateTheme/updateStylePack 广播到全部存活实例。
    // bodyClasses 为全量同步语义（含 chat_display 布局类 + app-host-actions 宿主接管标记）。
    @Volatile private var currentThemeJson: String? = null
    // 整页壳 C2：fullchat 是页面级状态，必须随主题/崩溃重建一起全量同步；
    // app-host-actions 只保留 reasoning 编辑按钮等宿主未接管项的过渡隐藏。
    @Volatile private var currentBodyClasses: List<String> = listOf("fullchat", "app-host-actions")
    @Volatile private var currentStylePackEnabled: Boolean = false
    @Volatile private var currentStylePackHref: String? = null
    @Volatile private var currentStylePackExtensionHref: String? = null
    @Volatile private var currentStylePackVars: String? = null

    /** 主题/布局变更入口（ChatScreen 收集 OfficialThemeManager 流后调用） */
    fun updateTheme(themeJson: String?, bodyClasses: List<String>) {
        currentThemeJson = themeJson
        currentBodyClasses = bodyClasses
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { applyPageSetup(it) }
        }
    }

    /** 样式包变更入口：enabled/href/extensionHref/varsJson 全量透传内核（vars 为原始 JSON 对象字面量） */
    fun updateStylePack(enabled: Boolean, href: String?, varsJson: String?, extensionHref: String? = null) {
        currentStylePackEnabled = enabled
        currentStylePackHref = href
        currentStylePackExtensionHref = extensionHref
        currentStylePackVars = varsJson
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { applyPageSetup(it) }
        }
    }

    /**
     * 官方事件广播（event_types 触发点位接线）：广播到全部存活实例。
     * args 传 JSON 字面量字符串列表（如 listOf("0", "\"swipe\"")），由 RenderKernel.emitEvent 拼装。
     * 时机对齐官方 script.js emit 点位；卡脚本经 eventSource.on 监听。
     */
    fun emitEvent(type: String, args: List<String> = emptyList()) {
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.toList() }.forEach { RenderKernel(it).emitEvent(type, args) }
        }
    }

    private fun applyPageSetup(instance: PooledWebView) {
        val kernel = RenderKernel(instance)
        // 顺序契约：先主题变量 → 再布局类 → 最后样式包（整包 CSS 可覆盖前两者，
        // 与官方「power-user 设置 → 扩展主题 CSS」的层叠顺序一致）
        currentThemeJson?.let(kernel::applyThemeRaw)
        kernel.setBodyClasses(currentBodyClasses)
        kernel.applyStylePack(currentStylePackEnabled, currentStylePackHref, currentStylePackVars, currentStylePackExtensionHref)
        kernel.setInputState(currentInputState.generating, currentInputState.swiping)
        currentBackgroundUrl?.let { kernel.setBackground(it, currentBackgroundFitting) }
    }

    fun preload() {
        scope.launch(Dispatchers.Main) {
            repeat(warmup) { runCatching { createAndWarm() }.onFailure { reportError("内核实例预热失败", it) } }
        }
    }

    /** 渲染进程崩溃处理（§3.3 自愈）：销毁崩溃实例、剔出池、通知宿主行复位重挂载。
     *  raw 文本在 Kotlin 侧，重建零丢失。 */
    private fun handleProcessGone(instance: PooledWebView) {
        scope.launch(Dispatchers.Main) {
            synchronized(all) { all.remove(instance) }
            idle.remove(instance)
            // 官方契约：destroy() 前先脱离视图树，避免持有已损坏实例的父容器泄漏/绘制异常
            (instance.webView.parent as? ViewGroup)?.removeView(instance.webView)
            runCatching { instance.webView.destroy() }
            synchronized(crashListeners) { crashListeners.toList() }.forEach { it() }
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

            override fun onMessageAction(mesid: String, action: String, value: String) {
                synchronized(messageActionListeners) { messageActionListeners.toList() }
                    .forEach { it(mesid, action, value) }
            }

            override fun onMessageLongPressed(mesid: String) {
                synchronized(longPressListeners) { longPressListeners.toList() }.forEach { it(mesid) }
            }

            override fun onShimRequest(reqId: String, method: String, paramsJson: String) {
                // URLEncoder + JS decodeURIComponent：免转义陷阱回传任意 JSON 文本
                val respond: (String) -> Unit = { payload ->
                    val encoded = java.net.URLEncoder.encode(payload, "UTF-8")
                    instance.webView.post {
                        instance.webView.evaluateJavascript(
                            "window.__shimRespond&&window.__shimRespond('$reqId',\"$encoded\");",
                            null,
                        )
                    }
                }
                val handler = shimHandler
                if (handler != null) handler(method, paramsJson, respond)
                else respond("{\"ok\":false,\"error\":\"shim handler not installed\"}")
            }

            override fun onHostAction(action: String, value: String) {
                synchronized(uiActionListeners) { uiActionListeners.toList() }.forEach { it(action, value) }
            }

            override fun onChatScroll(atBottom: Boolean) {
                synchronized(chatScrollListeners) { chatScrollListeners.toList() }.forEach { it(atBottom) }
            }

            override fun onInputChanged(text: String) {
                synchronized(inputListeners) { inputListeners.toList() }.forEach { it(text) }
            }

            override fun onInputHeight(heightPx: Float) {
                synchronized(inputHeightListeners) { inputHeightListeners.toList() }.forEach { it(heightPx) }
            }
        })
    }

    /**
     * 创建并预热一个实例：加载 kernel.html 并挂起等待 kernelReady。
     * 必须在主线程调用。
     */
    private suspend fun createAndWarm(): PooledWebView {
        val webView = try {
            WebView(context)
        } catch (t: Throwable) {
            reportError("WebView 创建失败（系统 WebView 服务不可用/损坏?）", t)
            throw t
        }
        val instance = PooledWebView(
            webView = webView,
            loader = assetLoader,
        )
        synchronized(all) { all.add(instance) }
        instance.webView.let { web ->
            // WebSettings 此前从未在池路径应用（javaScriptEnabled 默认 false）——
            // 与 KernelWebViewFactory.create 的放开模式设置对齐（§5.3）
            with(web.settings) {
                javaScriptEnabled = !RenderPrefs.strictMode(context)
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                // 官方度量对齐（同 KernelWebViewFactory）：textZoom 固定 100，关 overview/wideViewport
                textZoom = 100
                loadWithOverviewMode = false
                useWideViewPort = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            web.isVerticalScrollBarEnabled = false
            web.isHorizontalScrollBarEnabled = false
            web.addJavascriptInterface(makeBridge(instance), KernelProtocol.BRIDGE_NAME)
            web.webViewClient = KernelWebViewClient(
                assetLoader,
                instance.webView.context,
                onRenderProcessGone = { handleProcessGone(instance) },
                onLoadError = { reportError(it) },
            )
            // 黑匣子：页面 console 全量转发 logcat（未捕获异常以 Uncaught 前缀同走此路）
            web.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val level = it.messageLevel()
                        if (level != android.webkit.ConsoleMessage.MessageLevel.DEBUG &&
                            level != android.webkit.ConsoleMessage.MessageLevel.TIP
                        ) {
                            Log.w(
                                TAG,
                                "[console:$level] ${it.message()} " +
                                    "(${it.sourceId()?.substringAfterLast('/') ?: "?"}:${it.lineNumber()})",
                            )
                        }
                    }
                    return true
                }
            }
        }
        suspendCancellableCoroutine { cont ->
            // 桥回调（后台线程）仅置位 ready；此处主线程轮询避免跨线程续体竞争。
            // 黑匣子：超时不再无限死等（此前 kernelReady 不到 → 挂起永久 → 整页空白无任何线索）
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            val check = object : Runnable {
                override fun run() {
                    when {
                        instance.ready -> cont.resume(instance)
                        !cont.isActive -> Unit
                        System.currentTimeMillis() >= deadline -> {
                            synchronized(all) { all.remove(instance) }
                            runCatching { instance.webView.destroy() }
                            cont.resumeWithException(
                                IllegalStateException(
                                    "kernelReady 超时(${READY_TIMEOUT_MS / 1000}s)：页面未加载或 JS 未执行" +
                                        "（严格模式开启？主帧加载失败？见上方 logcat）",
                                ),
                            )
                        }
                        else -> handler.postDelayed(this, 50)
                    }
                }
            }
            instance.webView.loadUrl(KernelProtocol.KERNEL_URL)
            handler.postDelayed(check, 50)
        }
        // 就绪即套当前主题与布局类（新实例无需等 updateTheme 广播）
        runCatching { applyPageSetup(instance) }
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
                val fresh = runCatching { createAndWarm() }.onFailure { reportError("内核实例创建失败", it) }.getOrNull() ?: return@launch
                fresh.busy = true
                block(fresh)
            } else {
                // 池满兜底：直接新建不设限（内存压力由系统回收机制与 release 端控制）
                val fresh = runCatching { createAndWarm() }.onFailure { reportError("内核实例创建失败", it) }.getOrNull() ?: return@launch
                fresh.busy = true
                block(fresh)
            }
        }
    }

    fun release(pooled: PooledWebView) {
        scope.launch(Dispatchers.Main) {
            pooled.webView.evaluateJavascript("window.Kernel && window.Kernel.clear();", null)
            // 摘除旧父容器：复用实例挂新槽位前必须脱离回收槽（否则不可见）
            (pooled.webView.parent as? android.view.ViewGroup)?.removeView(pooled.webView)
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

    companion object {
        private const val TAG = "EmberInnKernel"
        /** kernelReady 等待上限：正常冷启动 <2s；超时即视为页面加载/JS 执行失败（黑匣子上报后销毁实例） */
        private const val READY_TIMEOUT_MS = 15_000L
    }
}
