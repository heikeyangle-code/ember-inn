package com.emberinn.app.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 动效规范实现（docs/DESIGN_SYSTEM.md §七）：
 * 弹簧物理底座 damping 0.6 / stiffness 500；生成中 aiSoft 呼吸 1.6s 循环；
 * 减动画模式全部降为 80ms fade。
 */

/** 标准交互弹簧（气泡入场/形状形变，stiffness 500 / damping 0.6）。 */
@Composable
fun rememberEmberSpring(): SpringSpec<Float> = spring(
    dampingRatio = EmberTheme.motion.springDamping,
    stiffness = EmberTheme.motion.springStiffness,
)

/** 轻量弹簧（chip 切换等小位移）。 */
@Composable
fun rememberEmberSpringLight(): SpringSpec<Float> = spring(
    dampingRatio = EmberTheme.motion.springDamping,
    stiffness = (EmberTheme.motion.springStiffness * 0.6f).coerceAtLeast(Spring.StiffnessLow),
)

/**
 * AI 呼吸光：指定颜色 1.6s 往复循环描边（生成中指示，st_awaiting_reply 同款语义）。
 * 减动画模式直接不画。
 */
fun Modifier.breathingGlow(color: Color, corner: Dp, strokeWidth: Dp = 2.dp): Modifier = composed {
    val reduced = EmberTheme.reducedMotion
    if (reduced) return@composed this
    val transition = rememberInfiniteTransition(label = "emberBreath")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breathAlpha",
    )
    drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(width = strokeWidth.toPx()),
        )
    }
}

/** 内容入场：fade + slide-up 8dp（§七 气泡入场）。减动画模式直接全量显示。 */
@Composable
fun EnterFadeSlide(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // EmberTheme 访问器是 @Composable getter：先在组合上下文读出，再进 remember/协程 lambda
    val startAtEnd = EmberTheme.reducedMotion
    val durationMs = EmberTheme.motion.sheetMs
    val progress = remember(key, startAtEnd) { Animatable(if (startAtEnd) 1f else 0f) }
    LaunchedEffect(key, durationMs) {
        if (progress.value < 1f) progress.animateTo(1f, tween(durationMs))
    }
    Box(
        modifier = modifier
            .alpha(progress.value)
            .graphicsLayer { translationY = (1f - progress.value) * 8.dp.toPx() },
    ) { content() }
}
