package com.emberinn.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 选择器 chips（§6.1）：accentSoft/accentBg 选中洗色，未选中细描边弱化。
 */
@Composable
fun EmberChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(s.cornerChip)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) c.accentBg else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                0.5.dp,
                if (selected) c.accentSoft else c.line,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(
            label,
            tier = if (selected) InkTier.Primary else InkTier.Soft,
            sizeSp = 13f,
        )
    }
}

/** 横向 chip 行：等宽排布或滚动由调用方决定，默认紧凑间距。 */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
