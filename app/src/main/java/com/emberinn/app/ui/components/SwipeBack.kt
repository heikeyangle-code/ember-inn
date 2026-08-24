package com.emberinn.app.ui.components

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 边缘滑动返回（左右均可）：从屏幕左/右 48dp 内起始的水平拖动，累计位移超过屏宽 22% 时触发 onBack。
 * 与列表滚动/消息横滑不冲突：只在边缘起始时接管拖动。
 */
fun Modifier.edgeSwipeBack(enabled: Boolean = true, onBack: () -> Unit): Modifier = composed {
    val currentOnBack by rememberUpdatedState(onBack)
    if (!enabled) return@composed this
    this.pointerInput(Unit) {
        // 手写边缘判定（2026-08-25）：detectHorizontalDragGestures 过横向 slop 后无条件消费
        // 整条事件流——竖向滚动带一点横向抖动就被抢走，WebView 收到 cancel 滚动当场冻死
        // （「滑一段就不动」根因）。现改为：只有起手在边缘 48dp 内才认领，其余全程旁观不消费。
        val edge = 48.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val inEdge = down.position.x <= edge || down.position.x >= size.width - edge
                var claimed = false
                var total = 0f
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: break
                    val dx = change.positionChange().x
                    if (!claimed) {
                        if (!inEdge) { break }
                        if (abs(change.position.x - down.position.x) > slop) { claimed = true }
                    } else {
                        total += dx
                        change.consume()
                    }
                    if (!change.pressed) { break }
                }
                if (claimed && abs(total) > size.width * 0.22f) { currentOnBack() }
            }
        }
    }
}
