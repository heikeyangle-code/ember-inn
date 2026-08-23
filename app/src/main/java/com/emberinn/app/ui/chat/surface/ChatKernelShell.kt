package com.emberinn.app.ui.chat.surface

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.emberinn.app.renderer.KernelMessagePayload
import com.emberinn.app.renderer.KernelWebViewPool
import com.emberinn.app.renderer.RenderKernel

/**
 * 整页壳 C2：聊天消息区唯一 WebView 宿主。
 *
 * 与嵌入态 [MessageKernelRow] 不同，这里不池化、不裁剪、不做行级高度契约；
 * 官方 #sheld/#chat 层级和滚动语义完整交给内核页。Kotlin 侧只保存 raw 文本，
 * 崩溃自愈时全量重渲即可恢复。
 */
@Composable
fun ChatKernelShell(
    pool: KernelWebViewPool,
    payloads: List<KernelMessagePayload>,
    modifier: Modifier = Modifier,
    followBottom: Boolean = true,
    onAtBottomChanged: (Boolean) -> Unit = {},
    onLongPress: (String) -> Unit = {},
    onMessageAction: (String, String, String) -> Unit = { _, _, _ -> },
    deleteMode: Boolean = false,
) {
    var host by remember { mutableStateOf<KernelWebViewPool.PooledWebView?>(null) }
    var slot by remember { mutableStateOf<FrameLayout?>(null) }
    // 官方单页没有“每条一行一个页面”的竞态；保留崩溃 epoch 即可让 acquire 回调重新挂新实例。
    var mountEpoch by remember { mutableIntStateOf(0) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(pool) {
        val scrollListener: (Boolean) -> Unit = { atBottom -> onAtBottomChanged(atBottom) }
        val longPressListener: (String) -> Unit = { mesid -> onLongPress(mesid) }
        val actionListener: (String, String, String) -> Unit = { mesid, action, value ->
            onMessageAction(mesid, action, value)
        }
        pool.addChatScrollListener(scrollListener)
        pool.addLongPressListener(longPressListener)
        pool.addMessageActionListener(actionListener)
        val crashListener: () -> Unit = {
            host = null
            mountEpoch++
        }
        pool.addCrashListener(crashListener)
        onDispose {
            disposed.set(true)
            pool.removeChatScrollListener(scrollListener)
            pool.removeLongPressListener(longPressListener)
            pool.removeMessageActionListener(actionListener)
            pool.removeCrashListener(crashListener)
            host?.let(pool::release)
        }
    }

    LaunchedEffect(slot, mountEpoch, host) {
        val target = slot ?: return@LaunchedEffect
        if (host != null || disposed.get()) return@LaunchedEffect
        pool.acquireSingle { pooled ->
            if (disposed.get()) {
                pool.release(pooled)
                return@acquireSingle
            }
            (pooled.webView.parent as? ViewGroup)?.removeView(pooled.webView)
            target.removeAllViews()
            target.addView(
                pooled.webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            host = pooled
        }
    }

    LaunchedEffect(host, payloads, followBottom) {
        val kernel = host?.let(::RenderKernel) ?: return@LaunchedEffect
        kernel.renderChat(payloads)
        if (followBottom) kernel.scrollToBottom()
    }

    LaunchedEffect(host, deleteMode) {
        host?.let { RenderKernel(it).setDeleteMode(deleteMode) }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).also { slot = it }
            },
            update = { frame ->
                frame.layoutParams = frame.layoutParams?.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            },
        )
    }
}
