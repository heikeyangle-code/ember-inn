package com.emberinn.app.ui.design.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 空态（§6.1 EmptyState）：inkMute 弱化层次，无动画无品牌滤镜。
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val c = EmberTheme.colors
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
                    .background(c.surface2),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = c.accent.copy(alpha = 0.75f),
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        }
        InkText(
            title,
            tier = InkTier.Primary,
            sizeSp = if (compact) 15f else 17f,
            fontWeight = FontWeight.SemiBold,
            lineHeightSp = if (compact) 21f else 24f,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        InkText(
            body,
            tier = InkTier.Mute,
            sizeSp = if (compact) 12f else 14f,
            lineHeightSp = if (compact) 18f else 21f,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(label = actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            GhostButton(label = secondaryLabel, onClick = onSecondary)
        }
    }
}
