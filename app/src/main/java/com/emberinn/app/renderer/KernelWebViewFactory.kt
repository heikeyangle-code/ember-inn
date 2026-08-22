package com.emberinn.app.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
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

    fun createAssetLoader(context: Context): WebViewAssetLoader {
        // 官方推荐模式：只暴露 filesDir 下的 public 子目录（同源页面可读该根下一切文件）
        val publicDir = File(context.filesDir, "public")
        return WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler(ASSETS_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler(DATA_PREFIX, WebViewAssetLoader.InternalStoragePathHandler(context, publicDir))
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
            javaScriptEnabled = true
            domStorageEnabled = true
            // 放开模式：媒体自动播放、自动加载图片
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
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
 */
class KernelWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val context: Context,
) : WebViewClientCompat() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ) = assetLoader.shouldInterceptRequest(request.url)

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        if (url.host == "appassets.androidplatform.net") return false
        return openExternally(url)
    }

    private fun openExternally(url: Uri): Boolean {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url))
        }
        return true
    }
}
