@file:OptIn(androidx.compose.ui.ExperimentalUiApi::class)

package com.emberinn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * README UI 质感清单 3：触觉反馈按语义匹配，不处处用 LongPress。
 * 确认 Confirm / 开关 ToggleOn·ToggleOff / 删除 Reject / 轻点 SegmentTick。
 */
object EmberHaptics {
    fun select(feedback: HapticFeedback) = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun confirm(feedback: HapticFeedback) = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject(feedback: HapticFeedback) = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    fun toggle(feedback: HapticFeedback, on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

/**
 * README UI 质感清单 1：彩色阴影（Compose 1.9+ 稳定 dropShadow）。
 * 阴影色默认用元素自身颜色深色版（由调用方传 color），而不是纯黑。
 */
fun Modifier.emberShadow(
    color: Color = Color.Black.copy(alpha = 0.16f),
    radius: Dp = 10.dp,
    spread: Dp = 0.dp,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    alpha: Float = 1f,
): Modifier = dropShadow(
    shape = RectangleShape,
    shadow = Shadow(radius = radius, color = color, spread = spread, offset = offset, alpha = alpha),
)

/**
 * README UI 质感清单 4：自绘骨架屏（不用现成库的灰骨架），
 * 扫光颜色跟随当前主题强调色，进度用 rememberInfiniteTransition 驱动。
 */
@Composable
fun EmberSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    base: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "ember-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ember-shimmer-progress",
    )
    val density = LocalDensity.current
    val band = with(density) { 140.dp.toPx() }
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val x = progress * (size.width + band * 2) - band
                val brush = Brush.linearGradient(
                    colors = listOf(
                        base.copy(alpha = 0.10f),
                        base.copy(alpha = 0.28f),
                        base.copy(alpha = 0.10f),
                    ),
                    start = Offset(x - band / 2, 0f),
                    end = Offset(x + band / 2, size.height),
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}

/** 全局开关封装：切换时补 ToggleOn/ToggleOff 触觉 + 轻音效（README 清单 3/6）。 */
@Composable
fun EmberSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Switch(
        checked = checked,
        onCheckedChange = {
            EmberHaptics.toggle(haptic, it)
            UiSounds.toggle(context, it)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}
