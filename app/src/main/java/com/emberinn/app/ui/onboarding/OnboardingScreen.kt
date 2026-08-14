package com.emberinn.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.glassEdgeHighlight
import com.emberinn.app.ui.icons.PhosphorIcons

/**
 * 首启欢迎页：品牌情绪优先——全屏氛围渐变 + 环境光斑 + 发光品牌标记，
 * 主行动用两张高级卡片（导入 / 直接开始），“跳过”弱化为右上角小字，去掉默认 M3 按钮的开源味。
 */
@Composable
fun OnboardingScreen(
    onImport: () -> Unit,
    onDirectChat: () -> Unit,
    onSkip: () -> Unit,
) {
    var showWelcome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        showWelcome = true
    }

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        background,
                    ),
                ),
            ),
    ) {
        // 环境光斑：右上暖光 + 左下暗光，让背景有层次而不是纯色渐变
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = 150.dp, y = (-130).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.16f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-110).dp, y = 90.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.12f), Color.Transparent))),
        )

        // 跳过：右上角弱化入口，不抢主行动
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Text(
                "跳过",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = showWelcome,
            enter = fadeIn(animationSpec = tween(520)) +
                slideInVertically(initialOffsetY = { it / 14 }, animationSpec = tween(520)),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .emberShadow(
                            color = primary.copy(alpha = 0.38f),
                            radius = 18.dp,
                            offset = DpOffset(0.dp, 7.dp),
                        )
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.22f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✦",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "余烬酒馆",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "EmberInn · 让每个角色都成为一炉火",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                OnboardingActionCard(
                    primary = true,
                    icon = PhosphorIcons.Folder,
                    title = "导入角色卡",
                    subtitle = "支持 PNG / JSON / CHARX · 从本地文件开始",
                    onClick = onImport,
                    dark = dark,
                )
                Spacer(Modifier.height(12.dp))
                OnboardingActionCard(
                    primary = false,
                    icon = PhosphorIcons.Send,
                    title = "直接开始聊天",
                    subtitle = "暂不导入，先进酒馆看看",
                    onClick = onDirectChat,
                    dark = dark,
                )
                Spacer(Modifier.height(30.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "数据仅保存在本地 · 无账号 · 无云端",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 首启主行动卡：主卡主色渐变 + 彩色投影；次卡玻璃 tonal 容器，视觉上只有一个主 CTA。 */
@Composable
private fun OnboardingActionCard(
    primary: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val theme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val bgBrush = Brush.linearGradient(listOf(theme.primary, lerp(theme.primary, Color.Black, 0.18f)))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .emberShadow(
                color = theme.primary.copy(alpha = if (primary) 0.34f else 0.16f),
                radius = if (primary) 16.dp else 10.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = if (primary) 0.6f else 0.4f,
            )
            .clip(shape)
            .background(if (primary) bgBrush else theme.surfaceContainerHigh.copy(alpha = 0.88f))
            .glassEdgeHighlight(dark = dark, atTop = true)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (primary) theme.onPrimary.copy(alpha = 0.18f)
                        else theme.primaryContainer.copy(alpha = 0.72f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (primary) theme.onPrimary else theme.onPrimaryContainer,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (primary) theme.onPrimary else theme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (primary) theme.onPrimary.copy(alpha = 0.78f) else theme.onSurfaceVariant,
                )
            }
            Icon(
                PhosphorIcons.CaretRight,
                contentDescription = null,
                tint = if (primary) theme.onPrimary.copy(alpha = 0.85f) else theme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
