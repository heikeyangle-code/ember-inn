package com.emberinn.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * README UI 质感清单 11：品牌化空状态。
 * 余烬/炭火意象：微光圆环 + 弱脉冲动效，配"下一步做什么"按钮，绝不白屏。
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
    /** 紧凑模式：用于设置子页/弹层内的行内空状态，不抢主按钮。 */
    compact: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "ember-glow")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500), RepeatMode.Reverse),
        label = "ember-glow-alpha",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (compact) 48.dp else 72.dp)) {
            Canvas(modifier = Modifier.size(if (compact) 38.dp else 58.dp)) {
                drawCircle(accent.copy(alpha = 0.12f * glow), radius = size.minDimension / 2)
                drawCircle(accent.copy(alpha = 0.22f * glow), radius = size.minDimension * 0.38f)
            }
            Text(
                "✦",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium,
                color = accent.copy(alpha = 0.9f),
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
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
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}
