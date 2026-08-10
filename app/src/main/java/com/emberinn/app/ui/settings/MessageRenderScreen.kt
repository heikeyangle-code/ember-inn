package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.theme.LocalThemePreset
import androidx.compose.ui.graphics.luminance

/**
 * 消息渲染（官方 SillyTavern 字段）：正文/次要/下划线/引用/气泡/边框/阴影色 + 毛玻璃强度。
 * 空 = 跟随主题自动生成；填 #RRGGBB 即覆盖。
 */
@Composable
fun MessageRenderScreen(onBack: () -> Unit, onAppearanceChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var blur by remember { mutableIntStateOf(AppearancePrefs.blurStrength(context)) }
    // 当前主题的官方字段默认值：字段留空时显示“主题默认”预览（换主题即时更新，与渲染器解析同规则）
    val stTheme = LocalThemePreset.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bodyFallback = if (dark) stTheme.stBody else null ?: MaterialTheme.colorScheme.onSurface
    val emFallback = if (dark) stTheme.stEm else null ?: MaterialTheme.colorScheme.onSurfaceVariant
    val underlineFallback = if (dark) stTheme.stUnderline else null ?: MaterialTheme.colorScheme.primary
    val quoteFallback = if (dark) stTheme.stQuote else null ?: MaterialTheme.colorScheme.primary
    val userBubbleFallback = if (dark) stTheme.stUserBubble else null ?: MaterialTheme.colorScheme.primaryContainer
    val botBubbleFallback = if (dark) stTheme.stBotBubble else null ?: MaterialTheme.colorScheme.surfaceContainerLow
    val borderFallback = if (dark) stTheme.stBorder else null ?: androidx.compose.ui.graphics.Color(0x80000000)
    val shadowFallback = if (dark) stTheme.stShadow else null ?: androidx.compose.ui.graphics.Color(0x80000000)

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
                        ColorField(
                            label = "正文色", hint = "官方 --SmartThemeBodyColor（例 #DCDCD2）", value = AppearancePrefs.stBodyColor(context), fallback = bodyFallback,
                            onSave = { AppearancePrefs.saveStBodyColor(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "次要文字色", hint = "官方 --SmartThemeEmColor：斜体 <em>/<i> + 小字时间戳（例 #919191）", value = AppearancePrefs.stEmColor(context), fallback = emFallback,
                            onSave = { AppearancePrefs.saveStEmColor(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "下划线色", hint = "官方 --SmartThemeUnderlineColor（<u> 或 ~text~，例 #BCE7CF）", value = AppearancePrefs.stUnderlineColor(context), fallback = underlineFallback,
                            onSave = { AppearancePrefs.saveStUnderlineColor(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "引用色", hint = "官方 --SmartThemeQuoteColor（<q>/blockquote/链接，例 #E18A24）", value = AppearancePrefs.stQuoteColor(context), fallback = quoteFallback,
                            onSave = { AppearancePrefs.saveStQuoteColor(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "用户气泡底色", hint = "官方 --SmartThemeUserMesBlurTintColor（例 #0000004D）", value = AppearancePrefs.stUserBubble(context), fallback = userBubbleFallback,
                            onSave = { AppearancePrefs.saveStUserBubble(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "AI 气泡底色", hint = "官方 --SmartThemeBotMesBlurTintColor（例 #3C3C3C4D）", value = AppearancePrefs.stBotBubble(context), fallback = botBubbleFallback,
                            onSave = { AppearancePrefs.saveStBotBubble(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "边框色", hint = "官方 --SmartThemeBorderColor（例 #00000080）", value = AppearancePrefs.stBorderColor(context), fallback = borderFallback,
                            onSave = { AppearancePrefs.saveStBorderColor(context, it); onAppearanceChanged() },
                        )
                        ColorField(
                            label = "阴影色", hint = "官方 --SmartThemeShadowColor（例 #00000080）", value = AppearancePrefs.stShadowColor(context), fallback = shadowFallback,
                            onSave = { AppearancePrefs.saveStShadowColor(context, it); onAppearanceChanged() },
                        )
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
                            onValueChange = { blur = it.toInt(); AppearancePrefs.saveBlurStrength(context, it.toInt()); onAppearanceChanged() },
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

