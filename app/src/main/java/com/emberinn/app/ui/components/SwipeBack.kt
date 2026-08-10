package com.emberinn.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
        val edge = 48.dp.toPx()
        var active = false
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                active = offset.x <= edge || offset.x >= size.width - edge
                total = 0f
            },
            onHorizontalDrag = { change, amount ->
                if (!active) return@detectHorizontalDragGestures
                change.consume()
                total += amount
            },
            onDragEnd = {
                if (active && abs(total) > size.width * 0.22f) currentOnBack()
                active = false
            },
            onDragCancel = { active = false },
        )
    }
}
