package com.emberinn.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.icons.FaIcons

/**
 * 首启欢迎页（完全重做）：一屏讲清三件事——这是什么（角色卡扇面主视觉）、
 * 能干什么（兼容酒馆卡/本地私密/自由扮演）、现在做什么（单一主 CTA 导入）。
 * 品牌字样与星标装饰已删：视觉自己说话。三段错峰淡入。
 */
@Composable
fun OnboardingScreen(
    onImport: () -> Unit,
    onDirectChat: () -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120); step = 1
        kotlinx.coroutines.delay(180); step = 2
        kotlinx.coroutines.delay(180); step = 3
    }

    val theme = MaterialTheme.colorScheme
    val primary = theme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 环境光：右上主色暖光 + 左下对角冷光，纯色底也能有纵深
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = 170.dp, y = (-150).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.14f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-120).dp, y = 110.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(theme.tertiary.copy(alpha = 0.10f), Color.Transparent))),
        )

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 10.dp, top = 4.dp),
        ) {
            Text("跳过", style = MaterialTheme.typography.labelLarge, color = theme.onSurfaceVariant)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // 主视觉：三张角色卡扇面（产品语义：角色们住在这家酒馆里）
            EmberFadeIn(step >= 1) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                ) {
                    // 中央辉光
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.16f), Color.Transparent))),
                    )
                    FanCard(
                        icon = FaIcons.BookOpen,
                        accent = theme.tertiary,
                        rotation = -16f,
                        offsetX = (-62).dp,
                    )
                    FanCard(
                        icon = FaIcons.WandMagicSparkles,
                        accent = theme.secondary,
                        rotation = 16f,
                        offsetX = 62.dp,
                    )
                    // 中央主卡最后绘制（Box 叠放=后画在上），形成扇面前后层次
                    FanCard(
                        icon = FaIcons.Mask,
                        accent = primary,
                        rotation = 0f,
                        offsetX = 0.dp,
                        elevate = true,
                    )
                }
            }

            Spacer(Modifier.weight(1.1f))

            // 一句话主张 + 三个卖点胶囊
            EmberFadeIn(step >= 2) {
                Text(
                    "把每次对话，都点成一炉火",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "导入 SillyTavern 角色卡，或直接开始一段 AI 对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnboardingChip(FaIcons.FileImport, "兼容酒馆角色卡")
                    OnboardingChip(FaIcons.Lock, "本地私密")
                    OnboardingChip(FaIcons.WandMagicSparkles, "自由扮演")
                }
            }

            Spacer(Modifier.weight(1.4f))

            // 主 CTA：导入角色卡（全宽大胶囊 + 渐变 + 主色投影）
            EmberFadeIn(step >= 3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .emberShadow(
                            color = primary.copy(alpha = 0.34f),
                            radius = 16.dp,
                            offset = DpOffset(0.dp, 6.dp),
                            alpha = 0.6f,
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.18f))))
                        .clickable(onClick = onImport),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FaIcons.FileImport, contentDescription = null, tint = theme.onPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "导入角色卡",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDirectChat) {
                    Text(
                        "暂不导入，先和 AI 聊聊",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.8f)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "数据仅保存在本机 · 无账号 · 无云端",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 分段入场容器：淡入 + 轻微上浮，段落感来自 stagger delay。 */
@Composable
private fun EmberFadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(420)) +
            slideInVertically(initialOffsetY = { it / 14 }, animationSpec = tween(420)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** 扇面角色卡：主题色渐变卡 + 居中图标；中央卡带彩色投影做层次锚点。 */
@Composable
private fun FanCard(
    icon: ImageVector,
    accent: Color,
    rotation: Float,
    offsetX: androidx.compose.ui.unit.Dp,
    elevate: Boolean = false,
) {
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .rotate(rotation)
            .size(width = 92.dp, height = 128.dp)
            .then(
                if (elevate) {
                    Modifier.emberShadow(
                        color = accent.copy(alpha = 0.38f),
                        radius = 14.dp,
                        offset = DpOffset(0.dp, 8.dp),
                        alpha = 0.6f,
                    )
                } else {
                    Modifier.emberShadow(color = Color.Black.copy(alpha = 0.14f), radius = 8.dp, offset = DpOffset(0.dp, 4.dp), alpha = 0.5f)
                },
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(accent, Color.White, 0.08f),
                        lerp(accent, Color.Black, 0.30f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(30.dp))
    }
}

/** 卖点胶囊：tonal 底 + 小图标 + 短文案，一眼读完不抢主行动。 */
@Composable
private fun OnboardingChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
