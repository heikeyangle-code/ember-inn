package com.emberinn.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
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
 * 空态（§6.1）：inkMute 弱化层次、ShellActionButton 行动粒，无动画无品牌滤镜。
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
    val typo = EmberTheme.typo
    val titleType = if (compact) typo.subhead else typo.head
    val bodyType = if (compact) typo.caption else typo.body
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
                    tint = c.inkMute,
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        }
        Text(
            title,
            color = c.ink,
            fontSize = titleType.fontSize,
            fontWeight = FontWeight.SemiBold,
            lineHeight = titleType.lineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            body,
            color = c.inkMute,
            fontSize = bodyType.fontSize,
            lineHeight = bodyType.lineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            ShellActionButton(label = actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                secondaryLabel,
                color = c.accent,
                fontSize = EmberTheme.typo.bodySmall.fontSize,
                modifier = Modifier.clickable(onClick = onSecondary),
            )
        }
    }
}
