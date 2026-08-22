package com.emberinn.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 按钮三态：主按钮（accent 实底）/幽灵按钮（细描边）/危险按钮。
 * 层次靠亮度与描边，不靠阴影；按下反馈交给 ripple（clickable 默认）。
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(corner ?: (s.cornerChip + 4.dp))
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) c.accent else c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) readableOnLabel(c.accent) else c.inkMute,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(corner ?: (s.cornerChip + 4.dp))
    Box(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, if (enabled) c.lineStrong else c.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) c.inkSoft else c.inkSoft2,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun DangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(corner ?: (s.cornerChip + 4.dp))
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.danger.copy(alpha = if (enabled) 0.16f else 0.08f))
            .border(0.5.dp, c.danger.copy(alpha = if (enabled) 0.45f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = c.danger.copy(alpha = if (enabled) 1f else 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun readableOnLabel(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF17171A) else Color(0xFFF2F3F5)
