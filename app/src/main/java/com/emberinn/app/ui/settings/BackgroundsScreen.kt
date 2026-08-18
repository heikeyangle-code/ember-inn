package com.emberinn.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.components.EmberSlider
import com.emberinn.app.ui.components.EmberSwitch

/**
 * 聊天背景（官方 div_background 分区）：
 * 背景图渲染链（显式背景 > 头像玻璃 > 氛围渐变）的模糊/遮罩/玻璃开关；
 * 单会话背景图在聊天页「更多 → 聊天背景」设置。
 */
@Composable
fun BackgroundsScreen(onBack: () -> Unit, onAppearanceChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var glassOn by remember { mutableStateOf(AppearancePrefs.chatBgAvatarGlass(context)) }
    var bgBlur by remember { mutableIntStateOf(AppearancePrefs.chatBgBlur(context)) }
    var scrimDark by remember { mutableIntStateOf(AppearancePrefs.chatBgScrimDark(context)) }
    var scrimLight by remember { mutableIntStateOf(AppearancePrefs.chatBgScrimLight(context)) }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "背景", subtitle = "聊天背景 · 模糊 · 遮罩", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("背景图", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "单会话背景在聊天页「更多 → 聊天背景」选择；优先级：显式背景 > 头像玻璃背景 > 氛围渐变",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Text("头像玻璃背景", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                EmberSwitch(
                                    checked = glassOn,
                                    onCheckedChange = {
                                        glassOn = it
                                        AppearancePrefs.saveChatBgAvatarGlass(context, it)
                                        onAppearanceChanged()
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("背景模糊", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "背景图模糊半径（px），默认标准 24",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                listOf(0 to "无", 12 to "轻", 24 to "标准", 36 to "重", 48 to "极").forEach { (v, label) ->
                                    FilterChip(
                                        selected = bgBlur == v,
                                        onClick = {
                                            bgBlur = v
                                            AppearancePrefs.saveChatBgBlur(context, v)
                                            onAppearanceChanged()
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("遮罩", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "盖在背景图上的颜色层，保证正文可读",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ColorField(
                                label = "深色遮罩颜色",
                                hint = "深色主题下的遮罩颜色（例 #000000）",
                                value = AppearancePrefs.chatBgScrimDarkColor(context),
                                onSave = { AppearancePrefs.saveChatBgScrimDarkColor(context, it) },
                            )
                            BgSliderRow(
                                label = "深色遮罩强度",
                                hint = "不透明度（%），默认 65",
                                value = scrimDark.toFloat(),
                                range = 0f..90f,
                            ) { v ->
                                scrimDark = v.toInt()
                                AppearancePrefs.saveChatBgScrimDark(context, v.toInt())
                                onAppearanceChanged()
                            }
                            ColorField(
                                label = "浅色遮罩颜色",
                                hint = "浅色主题下的遮罩颜色（例 #FFFFFF）",
                                value = AppearancePrefs.chatBgScrimLightColor(context),
                                onSave = { AppearancePrefs.saveChatBgScrimLightColor(context, it) },
                            )
                            BgSliderRow(
                                label = "浅色遮罩强度",
                                hint = "不透明度（%），默认 30",
                                value = scrimLight.toFloat(),
                                range = 0f..60f,
                            ) { v ->
                                scrimLight = v.toInt()
                                AppearancePrefs.saveChatBgScrimLight(context, v.toInt())
                                onAppearanceChanged()
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            glassOn = true
                            bgBlur = 24
                            scrimDark = 65
                            scrimLight = 30
                            AppearancePrefs.saveChatBgAvatarGlass(context, true)
                            AppearancePrefs.saveChatBgBlur(context, 24)
                            AppearancePrefs.saveChatBgScrimDark(context, 65)
                            AppearancePrefs.saveChatBgScrimLight(context, 30)
                            AppearancePrefs.saveChatBgScrimDarkColor(context, "#000000")
                            AppearancePrefs.saveChatBgScrimLightColor(context, "#FFFFFF")
                            onAppearanceChanged()
                        }) { Text("恢复默认") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BgSliderRow(
    label: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${value.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EmberSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
