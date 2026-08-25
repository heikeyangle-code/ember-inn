package com.emberinn.app.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.emberinn.app.ui.settings.RenderPrefs
import java.io.File

/**
 * 内核 WebView 装配：AssetLoader + 放开模式设置。
 *
 * - assets/  → 内核资产（kernel.html、官方 css/js）
 * - data/    → 头像 / 媒体 / 用户主题包（filesDir 映射）
 * 统一 https origin，相对路径与 CSP 行为与官方一致。
 */
object KernelWebViewFactory {

    const val ASSETS_PREFIX = "/assets/"
    const val DATA_PREFIX = "/data/"

    /** 头像站内源：filesDir/avatars → /avatars/、filesDir/persona-avatars → /pavatars/
     *  内核页与官方同构以 <img src> 直引，避免 file:// 与混合内容拦截 */
    const val AVATARS_PREFIX = "/avatars/"
    const val PERSONA_AVATARS_PREFIX = "/pavatars/"
    /** 消息附件站内源：filesDir/media → /media/（整页壳官方 <img>/<video>/<audio> 直引） */
    const val MEDIA_PREFIX = "/media/"
    /** C4 官方背景站内源：filesDir/backgrounds → /backgrounds/（内核页 #bg1 background-image） */
    const val BACKGROUNDS_PREFIX = "/backgrounds/"

    /** 导入主题包站内源：filesDir/themes → /themefiles/...（第三方整包 style.css 由样式包引用） */
    const val THEME_FILES_PREFIX = "/themefiles/"

    /** 用户安装扩展站内源：filesDir/extensions → /extfiles/...（ExtensionManager 管理的第三方扩展） */
    const val EXT_FILES_PREFIX = "/extfiles/"

    fun createAssetLoader(context: Context): WebViewAssetLoader {
        // 官方推荐模式：只暴露 filesDir 下的 public 子目录（同源页面可读该根下一切文件）
        val publicDir = File(context.filesDir, "public")
        return WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler(ASSETS_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler(DATA_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, publicDir))
            .addPathHandler(AVATARS_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "avatars")))
            .addPathHandler(PERSONA_AVATARS_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "persona-avatars")))
            .addPathHandler(MEDIA_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "media")))
            .addPathHandler(BACKGROUNDS_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "backgrounds").apply { mkdirs() }))
            .addPathHandler(THEME_FILES_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "themes").apply { mkdirs() }))
            .addPathHandler(EXT_FILES_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "extensions").apply { mkdirs() }))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        assetLoader: WebViewAssetLoader,
        bridge: KernelBridge,
    ): WebView {
        val web = WebView(context)
        with(web.settings) {
            // 严格模式（§5.3 用户自选收紧）：禁执行内核页 JS；默认关=全开
            javaScriptEnabled = !RenderPrefs.strictMode(context)
            domStorageEnabled = true
            // 放开模式：媒体自动播放、自动加载图片
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            // 官方度量对齐：textZoom 固定 100（系统字体缩放不得膨胀内核文本，
            // 否则换行/高度全偏离官方 CSS 语义）；overview/wideViewport 关闭——
            // viewport meta 已是 width=device-width，overview 首帧缩放反而造成不满宽
            textZoom = 100
            loadWithOverviewMode = false
            useWideViewPort = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.webViewClient = KernelWebViewClient(assetLoader, context)
        web.addJavascriptInterface(bridge, KernelProtocol.BRIDGE_NAME)
        return web
    }
}

/**
 * 站内 origin（appassets.androidplatform.net）放行；
 * 外链交给系统浏览器——卡片里的 <a href> 与 target=_blank 由此触达。
 *
 * [onRenderProcessGone]（§3.3 自愈）：返回 true 表示已处理——池侧销毁崩溃实例并
 * 通知宿主行复位重挂载，App 不闪退、raw 文本零丢失。
 */
class KernelWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val context: Context,
    private val onRenderProcessGone: (() -> Unit)? = null,
    /** 黑匣子：主帧加载失败回调（此前静默 → 整页白屏无任何线索） */
    private val onLoadError: ((String) -> Unit)? = null,
) : android.webkit.WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ) = assetLoader.shouldInterceptRequest(request.url)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame && onLoadError != null) {
            onLoadError("内核页加载失败 ${error.errorCode}：${error.description} @${request.url}")
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && onLoadError != null) {
            onLoadError("内核页 HTTP ${errorResponse.statusCode} @${request.url}")
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        if (url.host == "appassets.androidplatform.net") return false
        return openExternally(url)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRenderProcessGone?.invoke()
        return true
    }

    private fun openExternally(url: Uri): Boolean {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }
}
