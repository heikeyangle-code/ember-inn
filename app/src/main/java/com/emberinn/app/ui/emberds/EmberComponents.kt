package com.emberinn.app.ui.emberds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EmberDS 基础组件（docs/DESIGN_SYSTEM.md §五）：
 * SurfaceCard / GlassBar / InkText / AiBubble / UserBubble。
 * 视觉 DNA：近黑中性底、亮度阶梯表面、极细描边、无重阴影、AI 暖金身份色。
 */

@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    tier: Int = 1, // 1 主墨 2 次墨 3 弱墨 4 禁用
    sizeSp: Float = EmberDefaults.type.bodySp,
    italic: Boolean = false,
    fontWeight: FontWeight? = null,
) {
    val c = EmberDefaults.colors
    Text(
        text = text,
        modifier = modifier,
        color = when (tier) {
            2 -> c.inkSecondary
            3 -> c.inkTertiary
            4 -> c.inkDisabled
            else -> c.ink
        },
        fontSize = sizeSp.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = fontWeight,
    )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = EmberDefaults.colors
    val s = EmberDefaults.shapes
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.card))
            .background(if (elevated) c.surfaceHigh else c.surface)
            .border(0.5.dp, c.hairline, RoundedCornerShape(s.card))
            .padding(EmberDefaults.spacing.l),
        content = content,
    )
}

/** 顶栏/底栏玻璃条：近黑半透明 + 细描边，chrome 退后 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = EmberDefaults.colors
    val s = EmberDefaults.shapes
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s.bar))
            .background(c.surface.copy(alpha = 0.72f))
            .border(0.5.dp, c.hairline, RoundedCornerShape(s.bar))
            .padding(horizontal = EmberDefaults.spacing.l, vertical = EmberDefaults.spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun AiBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = EmberDefaults.colors
    val s = EmberDefaults.shapes
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.bubble))
            .background(c.bubbleBot)
            .border(0.5.dp, c.aiGoldSoft, RoundedCornerShape(s.bubble))
            .padding(EmberDefaults.spacing.m),
    ) { InkText(text) }
}

@Composable
fun UserBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = EmberDefaults.colors
    val s = EmberDefaults.shapes
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.bubble))
            .background(c.bubbleUser)
            .border(0.5.dp, c.hairline, RoundedCornerShape(s.bubble))
            .padding(EmberDefaults.spacing.m),
    ) { InkText(text) }
}

/** 强调按钮文字色统一入口 */
val EmberAccent: Color get() = EmberDefaults.colors.accent
