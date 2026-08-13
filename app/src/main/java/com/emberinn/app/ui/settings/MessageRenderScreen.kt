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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberSlider
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
    val blurTintFallback = if (dark) stTheme.stBlurTint else null ?: MaterialTheme.colorScheme.surface

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "消息渲染（官方字段）", onBack = onBack, sky = settingsSky)
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
                        Text(
                            "此页颜色同时注入 WebView 兜底渲染（含 <q>/<u>/<font color> 等官方行内 HTML 的消息）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
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
                        Text("毛玻璃（官方字段）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "色调 = 官方 --SmartThemeBlurTintColor（默认 #171717）；强度 = --SmartThemeBlurStrength，0 = 关闭模糊（纯色表面）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorField(
                            label = "玻璃色调", hint = "官方 --SmartThemeBlurTintColor（例 #171717）", value = AppearancePrefs.stBlurTint(context), fallback = blurTintFallback,
                            onSave = { AppearancePrefs.saveStBlurTint(context, it); onAppearanceChanged() },
                        )
                        Text("毛玻璃强度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        EmberSlider(
                            value = blur.toFloat(),
                            onValueChange = { blur = it.toInt(); AppearancePrefs.saveBlurStrength(context, it.toInt()); onAppearanceChanged() },
                            valueRange = 0f..40f,
                        )
                        Text("$blur", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                var collapse by remember { mutableStateOf(RenderPrefs.collapseNewlines(context)) }
                var separator by remember { mutableStateOf(RenderPrefs.exampleSeparator(context)) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("行为与兼容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "对齐官方 power-user：折叠连续换行（collapse_newlines）与消息示例分隔符（context.example_separator，默认 ***）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text("折叠连续换行", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            EmberSwitch(
                                checked = collapse,
                                onCheckedChange = {
                                    collapse = it
                                    RenderPrefs.setCollapseNewlines(context, it)
                                },
                            )
                        }
                        EmberTextField(
                            value = separator,
                            onValueChange = {
                                separator = it
                                RenderPrefs.setExampleSeparator(context, it)
                            },
                            label = { Text("消息示例分隔符（example_separator）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
            item {
                var behavior by remember { mutableStateOf(BehaviorPrefs.load(context)) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("官方行为（power-user）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        EmberTextField(
                            value = behavior.userPromptBias,
                            onValueChange = {
                                behavior = behavior.copy(userPromptBias = it)
                                BehaviorPrefs.save(context, behavior)
                            },
                            label = { Text("回复前缀（user_prompt_bias；会拼在生成回复前）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        BehaviorToggle("显示回复前缀（show_user_prompt_bias）", behavior.showUserPromptBias) {
                            behavior = behavior.copy(showUserPromptBias = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("清理时裁掉句尾（trim_sentences）", behavior.trimSentences) {
                            behavior = behavior.copy(trimSentences = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("清理时裁掉首尾空格（trim_spaces）", behavior.trimSpaces) {
                            behavior = behavior.copy(trimSpaces = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("示例固定顶部（pin_examples）", behavior.pinExamples) {
                            behavior = behavior.copy(pinExamples = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("完全移除示例（strip_examples）", behavior.stripExamples) {
                            behavior = behavior.copy(stripExamples = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("名字作为停用词（names_as_stop_strings）", behavior.namesAsStopStrings) {
                            behavior = behavior.copy(namesAsStopStrings = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("消息显示 token 数（message_token_count）", behavior.messageTokenCount) {
                            behavior = behavior.copy(messageTokenCount = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        BehaviorToggle("自动滑动（auto_swipe）", behavior.autoSwipe) {
                            behavior = behavior.copy(autoSwipe = it)
                            BehaviorPrefs.save(context, behavior)
                        }
                        EmberTextField(
                            value = behavior.autoSwipeMinimumLength.toString(),
                            onValueChange = {
                                behavior = behavior.copy(autoSwipeMinimumLength = it.toIntOrNull() ?: 0)
                                BehaviorPrefs.save(context, behavior)
                            },
                            label = { Text("自动滑动最短长度（0=关闭）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        var blacklistText by remember { mutableStateOf(behavior.autoSwipeBlacklist.joinToString(", ")) }
                        EmberTextField(
                            value = blacklistText,
                            onValueChange = {
                                blacklistText = it
                                behavior = behavior.copy(autoSwipeBlacklist = it.split(',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet())
                                BehaviorPrefs.save(context, behavior)
                            },
                            label = { Text("自动滑动黑名单（逗号分隔）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        EmberTextField(
                            value = behavior.autoSwipeBlacklistThreshold.toString(),
                            onValueChange = {
                                behavior = behavior.copy(autoSwipeBlacklistThreshold = it.toIntOrNull() ?: 0)
                                BehaviorPrefs.save(context, behavior)
                            },
                            label = { Text("黑名单命中阈值（次）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BehaviorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}
