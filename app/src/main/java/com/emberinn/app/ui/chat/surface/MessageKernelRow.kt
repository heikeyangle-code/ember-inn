package com.emberinn.app.ui.chat.surface

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.emberinn.app.renderer.ChatDisplayMode
import com.emberinn.app.renderer.KernelMessagePayload
import com.emberinn.app.renderer.KernelWebViewPool
import com.emberinn.app.renderer.RenderKernel
import com.emberinn.app.renderer.StTheme

/**
 * 单条消息的内核宿主：从池中取 WebView，渲染后按内容高度回报给 LazyColumn。
 * raw 文本永远保存在 Kotlin 侧（ChatStore），WebView 只是显示器官——
 * 渲染进程崩溃时重建即可，数据零丢失。
 */
@Composable
fun MessageKernelRow(
    pool: KernelWebViewPool,
    payload: KernelMessagePayload,
    modifier: Modifier = Modifier,
    onHeightChanged: (mesid: String, heightPx: Float) -> Unit = { _, _ -> },
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            // 占位容器；acquire 回调里换上真实内核 WebView
            WebView(context).apply { isEnabled = false }
        },
        update = { placeholder ->
            if (placeholder.tag != RENDER_TAG) {
                placeholder.tag = RENDER_TAG
                pool.acquire { pooled ->
                    val kernel = RenderKernel(pooled)
                    pooled.webView.tag = payload.mesid
                    kernel.renderMessage(payload)
                    (placeholder.parent as? android.view.ViewGroup)?.let { parent ->
                        val index = parent.indexOfChild(placeholder)
                        parent.removeView(placeholder)
                        parent.addView(pooled.webView, index)
                        pooled.webView.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                }
            }
        },
    )
    LaunchedEffect(pool) {
        pool.addHeightListener { mesid, h -> onHeightChanged(mesid, h) }
    }
}

private const val RENDER_TAG = "ember_kernel_row"

/**
 * 聊天流式渲染节流器（docs/REFACTOR_V2_PLAN.md §3.4）：
 * 流中 120ms 节流的轻量 innerHTML 更新；流结束调用 [finish] 做权威全量管线。
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

/** 主题与布局模式的内核应用副作用 */
@Composable
fun KernelThemeEffect(
    pool: KernelWebViewPool,
    /** 官方主题原始 JSON（全字段透传给内核） */
    themeJson: String?,
    displayMode: ChatDisplayMode,
    stylePackClasses: List<String> = emptyList(),
) {
    LaunchedEffect(theme, displayMode) {
        pool.acquire { pooled ->
            val kernel = RenderKernel(pooled)
            themeJson?.let(kernel::applyThemeRaw)
            kernel.setChatDisplayMode(displayMode)
            if (stylePackClasses.isNotEmpty()) kernel.setStylePackBodyClass(*stylePackClasses.toTypedArray())
        }
    }
}
