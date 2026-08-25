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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ThemeSurface
import com.emberinn.app.ui.design.components.ShellFace
import com.emberinn.app.ui.icons.FaIcons

/**
 * 首启欢迎页（新美学版）：安静、克制、随活动主题换装。
 * 一屏三件事——这是什么（一段对话的样子）、能干什么（三个卖点粒）、现在做什么（单一主 CTA）。
 * 全部颜色取自主题令牌；reduced_motion 时分段入场直出。
 */
@Composable
fun OnboardingScreen(
    onImport: () -> Unit,
    onDirectChat: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = EmberTheme.colors
    val reduced = EmberTheme.reducedMotion

    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (reduced) {
            step = 3
        } else {
            kotlinx.coroutines.delay(140); step = 1
            kotlinx.coroutines.delay(200); step = 2
            kotlinx.coroutines.delay(200); step = 3
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // 环境光：顶部中央一点强调色微光（≤10% 透明度，随主题换色）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(c.accent.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )

        Text(
            "跳过",
            color = c.inkMute,
            fontSize = EmberTheme.typo.bodySmall.fontSize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 22.dp, top = 14.dp)
                .clickable(onClick = onSkip)
                .padding(4.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // 主视觉：一段对话的样子——AI 一句、用户一句，内容面即产品
            EmberFadeIn(step >= 1, reduced) {
                Column(Modifier.fillMaxWidth(0.86f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bubble(text = "……你终于回来了。", face = ShellFace.Content, alignEnd = false)
                    Bubble(text = "嗯，我回来了。今晚从哪段继续？", face = ShellFace.Action, alignEnd = true)
                }
            }

            Spacer(Modifier.weight(1.1f))

            EmberFadeIn(step >= 2, reduced) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "把每次对话，都点成一炉火",
                        color = c.ink,
                        fontSize = EmberTheme.typo.displaySmall.fontSize,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "导入 SillyTavern 角色卡，或直接开始一段 AI 对话",
                        color = c.inkMute,
                        fontSize = EmberTheme.typo.body.fontSize,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SellingPoint(FaIcons.FileImport, "兼容酒馆角色卡")
                        SellingPoint(FaIcons.Lock, "本地私密")
                        SellingPoint(FaIcons.WandMagicSparkles, "自由扮演")
                    }
                }
            }

            Spacer(Modifier.weight(1.4f))

            EmberFadeIn(step >= 3, reduced) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 主 CTA：操作面实底胶囊 + 发丝缘，无渐变无彩影
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(c.surface2)
                            .clickable(onClick = onImport),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(FaIcons.FileImport, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("导入角色卡", color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "暂不导入，先和 AI 聊聊",
                        color = c.accent,
                        fontSize = EmberTheme.typo.body.fontSize,
                        modifier = Modifier.clickable(onClick = onDirectChat).padding(6.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.7f)))
                        Spacer(Modifier.width(7.dp))
                        Text("数据仅保存在本机 · 无账号 · 无云端", color = c.inkMute, fontSize = EmberTheme.typo.meta.fontSize)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 分段入场容器：淡入 + 轻微上浮；减动画直出。 */
@Composable
private fun EmberFadeIn(visible: Boolean, reduced: Boolean, content: @Composable () -> Unit) {
    if (reduced) {
        Box { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(360)) +
            slideInVertically(initialOffsetY = { it / 14 }, animationSpec = tween(360)),
    ) {
        Column { content() }
    }
}

/** 对话气泡样例：内容面/操作面各一，宽度错开制造真实对话节奏。 */
@Composable
private fun Bubble(text: String, face: ShellFace, alignEnd: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        ThemeSurface(face, corner = 16.dp, modifier = Modifier.width(if (alignEnd) 218.dp else 238.dp)) {
            Text(
                text,
                color = EmberTheme.colors.inkMute,
                fontSize = EmberTheme.typo.bodySmall.fontSize,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

/** 卖点粒：内容面小胶囊 + 弱墨文字，一眼读完不抢主行动。 */
@Composable
private fun SellingPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.inkMute, fontSize = EmberTheme.typo.meta.fontSize)
    }
}
