package com.emberinn.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 万能卡片（§6.1）：surface 面 + 极细描边，无重阴影——层次靠亮度阶梯不靠投影。
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    sink: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = EmberTheme.colors
    val radius = corner ?: EmberTheme.shapes.cornerCard
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (sink) c.surfaceSink else c.surface)
            .border(0.5.dp, c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

/**
 * 玻璃栏（顶栏/底栏/常驻搜索条）：bg 半透明 + 细描边，chrome 退到幕后让内容站前排。
 * blurStrength 联动留给背景图层（P6），当前以半透明近黑表达玻璃感。
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    contentAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val c = EmberTheme.colors
    val radius = corner ?: EmberTheme.shapes.cornerCard
    val shape = RoundedCornerShape(radius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface.copy(alpha = 0.72f))
            .border(0.5.dp, c.line, shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = contentAlignment,
        content = content,
    )
}

/** 沉浸渐变遮罩：聊天页上滑淡出后只留的顶部/底部渐晕（st_chat_immersive_gradient 同款语义）。 */
@Composable
fun ScrimGradient(
    modifier: Modifier = Modifier,
    top: Boolean = true,
) {
    val c = EmberTheme.colors
    val base = EmberTheme.stageTint ?: c.bg
    androidx.compose.foundation.Box(
        modifier = modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = if (top) listOf(base.copy(alpha = 0.92f), base.copy(alpha = 0f))
                else listOf(base.copy(alpha = 0f), base.copy(alpha = 0.92f)),
            ),
        ),
    )
}
