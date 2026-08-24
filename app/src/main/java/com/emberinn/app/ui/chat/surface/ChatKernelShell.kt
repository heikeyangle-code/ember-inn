package com.emberinn.app.ui.chat.surface

import android.util.Log
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
 * 与旧嵌入态行不同，这里不池化、不裁剪、不做行级高度契约；
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
    /** 官方 click_to_edit：主题开关打开时点击 .mes_text 进入编辑（chats.js L2292 语义） */
    onTextClick: (String) -> Unit = {},
    /** C3：新内核实例挂载（含崩溃自愈重挂）后把宿主草稿写回 #send_textarea */
    draftProvider: () -> String = { "" },
    deleteMode: Boolean = false,
    /** 边界5 长聊天截断：顶部挂官方 #show_more_messages（script.js printMessages） */
    showMore: Boolean = false,
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
        val textClickListener: (String, com.emberinn.app.renderer.KernelClickTarget?) -> Unit = { mesid, target ->
            if (target?.cls == "mes_text") onTextClick(mesid)
        }
        pool.addChatScrollListener(scrollListener)
        pool.addLongPressListener(longPressListener)
        pool.addMessageActionListener(actionListener)
        pool.addClickListener(textClickListener)
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
            pool.removeClickListener(textClickListener)
            pool.removeCrashListener(crashListener)
            host?.let(pool::release)
        }
    }

    LaunchedEffect(slot, mountEpoch, host) {
        val target = slot ?: return@LaunchedEffect
        if (disposed.get()) return@LaunchedEffect
        // 宿主已持实例：校验其视图仍挂在当前槽位。Compose 重组可能重建 AndroidView 容器，
        // 旧引用会把 WebView 留在已摘除的旧槽里（视口 0x0=页面活着但整树不可见）→ 重挂。
        val current = host
        if (current != null) {
            if (current.webView.parent === target) { return@LaunchedEffect }
            (current.webView.parent as? ViewGroup)?.removeView(current.webView)
            target.removeAllViews()
            target.addView(
                current.webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            current.webView.requestLayout()
            return@LaunchedEffect
        }
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
            android.util.Log.e("EmberInnKernel",
                "attach slot=${target.width}x${target.height} view=${pooled.webView.width}x${pooled.webView.height}")
            target.post {
                android.util.Log.e("EmberInnKernel",
                    "attached-post slot=${target.width}x${target.height} view=${pooled.webView.width}x${pooled.webView.height}")
            }
            // C3：草稿回填（applyPageSetup 不携带草稿，挂载点单独下发）
            RenderKernel(pooled).pushInputText(draftProvider())
        }
    }

    // 渲染签名守卫：流式 tick 会重建 payloads 实例但多数行未变；
    // 内容指纹不变时跳过 renderChat 全量重建（官方 addOneMessage 为增量，此处对齐其成本量级）
    var lastRenderSig by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(host, payloads, followBottom, showMore) {
        val kernel = host?.let(::RenderKernel) ?: return@LaunchedEffect
        val sig = payloads.size.toString() + "#" + payloads.joinToString("|") { p ->
            p.mesid + ":" + p.mes.length + ":" +
                p.mes.take(16).hashCode() + ":" + p.mes.takeLast(16).hashCode() + ":" +
                (p.reasoning?.length ?: 0) + ":" + p.currentSwipe
        } + "#" + showMore
        if (sig != lastRenderSig) {
            kernel.renderChat(payloads, showMore)
            lastRenderSig = sig
        }
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
