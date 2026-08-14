package com.emberinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.theme.LocalVibe

/**
 * 通用空状态：中性配色 + 可选图标（默认不画品牌符号、无动画）。
 * 装饰圆环的浓淡跟随“视觉氛围→光效”设置，默认标准档几乎不可见。
 */
@Composable
fun EmberEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    val glow = LocalVibe.current.glow
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compact) 44.dp else 60.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.06f + 0.06f * glow)),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(if (compact) 20.dp else 28.dp),
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        }
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            body,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = (if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium).lineHeight,
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 24.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            EmberPrimaryButton(label = actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            EmberSecondaryButton(label = secondaryLabel, onClick = onSecondary)
        }
    }
}
