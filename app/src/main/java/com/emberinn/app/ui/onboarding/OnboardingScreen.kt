package com.emberinn.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 首启欢迎页（README）：低饱和氛围渐变 + 两个主选项 + 跳过 + 本地数据信任信号。 */
@Composable
fun OnboardingScreen(
    onImport: () -> Unit,
    onDirectChat: () -> Unit,
    onSkip: () -> Unit,
) {
    // README 品牌开场：1.5–2s 微光 → 淡入欢迎内容（Lottie 资产未提供，用 Compose 动画替代）
    var showWelcome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1600)
        showWelcome = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("✦", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(visible = showWelcome, enter = fadeIn(animationSpec = tween(600))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "欢迎来到余烬酒馆",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "每个角色都是一炉火，故事在余烬里继续",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onImport, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("导入角色卡")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDirectChat, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("直接开始聊天")
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSkip) {
                Text("跳过")
            }
            Spacer(Modifier.height(36.dp))
            Text(
                "数据仅保存在本地",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
                }
            }
        }
    }
}
