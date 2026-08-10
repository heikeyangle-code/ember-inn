package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.ColorPickerDialog
import com.emberinn.app.ui.components.parseHexColor
import com.emberinn.app.ui.components.toHex

/**
 * 消息渲染（官方 SillyTavern 字段）：正文/次要/下划线/引用/气泡/边框/阴影色 + 毛玻璃强度。
 * 空 = 跟随主题自动生成；填 #RRGGBB 即覆盖。
 */
@Composable
fun MessageRenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var blur by remember { mutableIntStateOf(AppearancePrefs.blurStrength(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "消息渲染（官方字段）", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("官方颜色字段", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "留空=跟随当前主题；填写 #RRGGBB 后覆盖对应字段（对照官方 SmartTheme 变量）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorField("正文色", "官方 --SmartThemeBodyColor（例 #DCDCD2）", AppearancePrefs.stBodyColor(context)) { AppearancePrefs.saveStBodyColor(context, it) }
                        ColorField("次要文字色", "官方 --SmartThemeEmColor：斜体 <em>/<i> + 小字时间戳（例 #919191）", AppearancePrefs.stEmColor(context)) { AppearancePrefs.saveStEmColor(context, it) }
                        ColorField("下划线色", "官方 --SmartThemeUnderlineColor（<u> 或 ~text~，例 #BCE7CF）", AppearancePrefs.stUnderlineColor(context)) { AppearancePrefs.saveStUnderlineColor(context, it) }
                        ColorField("引用色", "官方 --SmartThemeQuoteColor（<q>/blockquote/链接，例 #E18A24）", AppearancePrefs.stQuoteColor(context)) { AppearancePrefs.saveStQuoteColor(context, it) }
                        ColorField("用户气泡底色", "官方 --SmartThemeUserMesBlurTintColor（例 #0000004D）", AppearancePrefs.stUserBubble(context)) { AppearancePrefs.saveStUserBubble(context, it) }
                        ColorField("AI 气泡底色", "官方 --SmartThemeBotMesBlurTintColor（例 #3C3C3C4D）", AppearancePrefs.stBotBubble(context)) { AppearancePrefs.saveStBotBubble(context, it) }
                        ColorField("边框色", "官方 --SmartThemeBorderColor（例 #00000080）", AppearancePrefs.stBorderColor(context)) { AppearancePrefs.saveStBorderColor(context, it) }
                        ColorField("阴影色", "官方 --SmartThemeShadowColor（例 #00000080）", AppearancePrefs.stShadowColor(context)) { AppearancePrefs.saveStShadowColor(context, it) }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("毛玻璃强度", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "官方 --SmartThemeBlurStrength；0 = 关闭模糊（纯色表面）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = blur.toFloat(),
                            onValueChange = { blur = it.toInt(); AppearancePrefs.saveBlurStrength(context, it.toInt()) },
                            valueRange = 0f..40f,
                        )
                        Text("$blur", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("HTML 兜底", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "含 <q>/<u>/<font color> 等官方行内 HTML 的消息走本地 WebView + 官方 CSS 变量渲染；普通 Markdown 仍用原生渲染。此页颜色同时注入 WebView。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorField(label: String, hint: String, value: String, onSave: (String) -> Unit) {
    var draft by remember(label) { mutableStateOf(value) }
    var showPicker by remember { mutableStateOf(false) }
    val current = parseHexColor(draft)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (current != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(current),
                )
            } else {
                Text("跟随主题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; onSave(it) },
                placeholder = { Text("#RRGGBB") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(top = 4.dp),
            )
            TextButton(onClick = { showPicker = true }, modifier = Modifier.padding(start = 6.dp)) {
                Text("选色盘")
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showPicker) {
        ColorPickerDialog(
            title = label,
            initial = current,
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                draft = color.toHex()
                onSave(draft)
                showPicker = false
            },
        )
    }
}
