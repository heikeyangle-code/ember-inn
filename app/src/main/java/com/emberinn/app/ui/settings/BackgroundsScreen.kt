package com.emberinn.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.EmberSwitch
import java.io.File

/**
 * 聊天背景（官方 div_background 分区）：
 * 全局背景列表 + 选择；会话级锁定在聊天页「更多 → 聊天背景」。
 * 模糊/遮罩全部由官方主题字段控制（blur_strength / chat_tint_color），此处无独立开关。
 */
@Composable
fun BackgroundsScreen(onBack: () -> Unit, onAppearanceChanged: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bgDir = remember { File(context.filesDir, "backgrounds").apply { mkdirs() } }
    val files by remember { mutableStateOf(bgDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()) }
    var currentBg by remember { mutableStateOf(AppearancePrefs.globalBackground(context)) }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "背景", subtitle = "全局背景 · 会话级在聊天页设置", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("背景图", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "选择后写入全局背景；聊天页可按会话覆盖。模糊/遮罩由主题控制。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(files.size) { index ->
                    val f = files[index]
                    val active = f.absolutePath == currentBg
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentBg = f.absolutePath
                                AppearancePrefs.saveGlobalBackground(context, f.absolutePath)
                                onAppearanceChanged()
                            },
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (active) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (files.isEmpty()) {
                    item {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "暂无背景图。将图片放入 filesDir/backgrounds/ 目录即可。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                item {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (currentBg.isNotBlank()) {
                            TextButton(onClick = {
                                currentBg = ""
                                AppearancePrefs.saveGlobalBackground(context, "")
                                onAppearanceChanged()
                            }) { Text("清除全局背景") }
                        }
                    }
                }
            }
        }
    }
}
