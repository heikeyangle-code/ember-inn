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
import androidx.compose.runtime.mutableIntStateOf
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
 * 单条消息的内核宿主（全 DOM 行）：
 * 池化 WebView 渲染官方模板整行 .mes（头像/名字/时间戳/正文一体，官方移动端结构）；
 * 原生只保留宿主交互面（长按菜单/操作条/swipe 手势由调用方叠加）。
 * raw 文本永远保存在 Kotlin 侧（ChatStore），WebView 只是显示器官——
 * 渲染进程崩溃时重建即可，数据零丢失。
 *
 * 高度契约：内核 reportHeight/ResizeObserver 回报 CSS px（≈dp），本组件据此撑高，
 * 未回报前用 [initialHeightDp] 兜底避免首帧塌陷。
 *
 * 挂载协议（§3.3 自愈）：取实例从 AndroidView factory 移入 LaunchedEffect——崩溃时
 * 池剔除实例并触发 crashListener → mountEpoch++ / host 复位 → 效果重跑换新实例重挂，
 * payload 效果随后自动权威重渲。factory 只负责造容器槽位。
 */
@Composable
fun MessageKernelRow(
    pool: KernelWebViewPool,
    payload: KernelMessagePayload,
    modifier: Modifier = Modifier,
    initialHeightDp: Float = 64f,
    onHeightChanged: ((Float) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    streamingText: String? = null,
) {
    var host by remember(payload.mesid) { mutableStateOf<KernelWebViewPool.PooledWebView?>(null) }
    var heightDp by remember(payload.mesid) { mutableStateOf(initialHeightDp) }
    var slot by remember(payload.mesid) { mutableStateOf<FrameLayout?>(null) }
    // 崩溃自愈计数：crashListener 触发 ++，重跑挂载效果换新实例
    var mountEpoch by remember(payload.mesid) { mutableIntStateOf(0) }
    // 生命周期哨兵：onDispose 置位。acquire 回调（主线程异步）到达时若已销毁则直接归还池。
    // 不能用 slot.parent 判定——AndroidView factory 创建的容器在组合提交前 parent 恒为 null，
    // 用它判定会把每次首挂载都误判为已销毁（全空白 bug 根因）。
    val disposed = remember(payload.mesid) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val density = LocalDensity.current

    // 高度回报与长按路由：按 mesid 过滤，只认自己这条消息的事件；崩溃事件全局广播
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
        val crashListener: () -> Unit = {
            host = null
            mountEpoch++
        }
        pool.addCrashListener(crashListener)
        onDispose {
            disposed.set(true)
            pool.removeHeightListener(heightListener)
            if (longPressListener != null) pool.removeLongPressListener(longPressListener)
            pool.removeCrashListener(crashListener)
            host?.let(pool::release)
        }
    }

    // 挂载：槽位就绪且当前无宿主时从池取实例挂上（崩溃后 epoch 变化重跑实现自愈）
    LaunchedEffect(slot, mountEpoch, host) {
        val s = slot ?: return@LaunchedEffect
        if (host != null || disposed.get()) return@LaunchedEffect
        pool.acquire { pooled ->
            // 全 DOM 行：不再挂 embed-shell——头像/名字/正文全由官方模板渲染
            if (disposed.get()) {
                pool.release(pooled)
                return@acquire
            }
            // 双保险：异步回调间可能已有宿主挂上（epoch 连跳/重组竞态），多的归还池
            if (host != null) {
                pool.release(pooled)
                return@acquire
            }
            (pooled.webView.parent as? ViewGroup)?.removeView(pooled.webView)
            s.removeAllViews()
            s.addView(
                pooled.webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            host = pooled
            // 渲染由 LaunchedEffect(host, payload) 统一触发，此处不重复
        }
    }

    // 载荷变化（文本编辑/swipe 切换）→ 权威重渲
    LaunchedEffect(host, payload) {
        val pooled = host ?: return@LaunchedEffect
        RenderKernel(pooled).renderMessage(payload)
    }

    // 内核流式（§3.4）：流中 120ms 节流轻量 innerHTML 更新；流结束 payload 换最终文本走上面的权威全量管线。
    val throttler = remember(payload.mesid) { StreamingThrottler() }
    LaunchedEffect(host, streamingText) {
        if (streamingText != null) {
            val pooled = host ?: return@LaunchedEffect
            throttler.onChunk(RenderKernel(pooled), payload.mesid, streamingText, System.currentTimeMillis())
        }
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
                FrameLayout(ctx).also { slot = it }
            },
        )
    }
}

/**
 * 聊天流式渲染节流器（docs/REFACTOR_V2_PLAN.md §3.4）：
 * 流中 120ms 节流的轻量 innerHTML 更新；流结束调用 [finish] 做权威全量管线。
 * （内核是唯一消息渲染管线；本行为其宿主容器。）
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
