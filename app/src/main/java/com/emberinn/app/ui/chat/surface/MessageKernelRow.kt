package com.emberinn.app.ui.chat.surface

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.emberinn.app.renderer.KernelMessagePayload
import com.emberinn.app.renderer.KernelWebViewPool
import com.emberinn.app.renderer.RenderKernel

/**
 * 单条消息正文的内核宿主（embed-shell 模式）：
 * 池化 WebView 只渲染官方管线的消息 HTML 正文；头像/名字/操作条/媒体等壳层交互
 * 仍由原生组件承担。raw 文本永远保存在 Kotlin 侧（ChatStore），WebView 只是显示器官——
 * 渲染进程崩溃时重建即可，数据零丢失。
 *
 * 高度契约：内核 reportHeight/ResizeObserver 回报 CSS px（≈dp），本组件据此撑高，
 * 未回报前用 [initialHeightDp] 兜底避免首帧塌陷。
 */
@Composable
fun MessageKernelRow(
    pool: KernelWebViewPool,
    payload: KernelMessagePayload,
    modifier: Modifier = Modifier,
    initialHeightDp: Float = 64f,
    onHeightChanged: ((Float) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    var host by remember(payload.mesid) { mutableStateOf<KernelWebViewPool.PooledWebView?>(null) }
    var heightDp by remember(payload.mesid) { mutableStateOf(initialHeightDp) }
    // 生命周期哨兵：onDispose 置位。acquire 回调（主线程异步）到达时若已销毁则直接归还池。
    // 不能用 slot.parent 判定——AndroidView factory 创建的容器在组合提交前 parent 恒为 null，
    // 用它判定会把每次首挂载都误判为已销毁（全空白 bug 根因）。
    val disposed = remember(payload.mesid) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val density = LocalDensity.current

    // 高度回报与长按路由：按 mesid 过滤，只认自己这条消息的事件
    DisposableEffect(pool, payload.mesid) {
        val heightListener = { id: String, h: Float ->
            if (id == payload.mesid && h > 0f) {
                heightDp = h
                onHeightChanged?.invoke(h)
            }
        }
        pool.addHeightListener(heightListener)
        val longPressListener = onLongPress?.let { cb ->
            { id: String -> if (id == payload.mesid) cb() }
        }
        if (longPressListener != null) pool.addLongPressListener(longPressListener)
        onDispose {
            disposed.set(true)
            pool.removeHeightListener(heightListener)
            if (longPressListener != null) pool.removeLongPressListener(longPressListener)
            host?.let(pool::release)
        }
    }

    // 载荷变化（文本编辑/swipe 切换）→ 权威重渲
    LaunchedEffect(host, payload) {
        val pooled = host ?: return@LaunchedEffect
        RenderKernel(pooled).renderMessage(payload)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(with(density) { heightDp.toDp() })
            .clipToBounds(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).also { slot ->
                    pool.acquire { pooled ->
                        RenderKernel(pooled).setEmbedShell(true)
                        if (!disposed.get()) {
                            // 池复用安全：先摘旧父容器再挂本槽位（release 不负责摘除）
                            (pooled.webView.parent as? ViewGroup)?.removeView(pooled.webView)
                            slot.addView(
                                pooled.webView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            host = pooled
                            // 渲染由 LaunchedEffect(host, payload) 统一触发，此处不重复
                        } else {
                            pool.release(pooled)
                        }
                    }
                }
            },
        )
    }
}

/**
 * 聊天流式渲染节流器（docs/REFACTOR_V2_PLAN.md §3.4）：
 * 流中 120ms 节流的轻量 innerHTML 更新；流结束调用 [finish] 做权威全量管线。
 * （当前流式显示仍走原生轻量路径避免换页闪烁；内核流式在 P6 聊天屏重写时启用。）
 */
class StreamingThrottler(
    private val intervalMs: Long = 120,
) {
    private var lastFlush = 0L
    private var pendingText: String? = null

    fun onChunk(kernel: RenderKernel?, mesid: String, text: String, nowMs: Long): Boolean {
        pendingText = text
        return if (nowMs - lastFlush >= intervalMs && kernel != null) {
            kernel.updateStreamingText(mesid, pendingText!!)
            lastFlush = nowMs
            pendingText = null
            true
        } else false
    }

    fun finish(kernel: RenderKernel, mesid: String, finalPayload: KernelMessagePayload) {
        kernel.renderMessage(finalPayload)
        pendingText = null
        lastFlush = 0
    }
}
