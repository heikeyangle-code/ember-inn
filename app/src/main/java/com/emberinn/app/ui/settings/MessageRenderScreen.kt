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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.ui.graphics.luminance

/**
 * 消息渲染（官方 SillyTavern 字段）：正文/次要/下划线/引用/气泡/边框/阴影色 + 玻璃色调。
 * 空 = 跟随当前官方主题；填 #RRGGBB 即覆盖。
 */
@Composable
fun MessageRenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 当前官方主题的颜色字段默认值：留空字段显示“主题默认”预览（换主题即时更新，与渲染器解析同规则）
    val themeManager = remember { OfficialThemeManager.shared(context) }
    val currentName by themeManager.currentName.collectAsState()
    val st = remember(currentName) { themeManager.stColors() }
    fun stColor(argb: Long?) = argb?.let { androidx.compose.ui.graphics.Color(it) }
    val bodyFallback = stColor(st.mainText) ?: MaterialTheme.colorScheme.onSurface
    val emFallback = stColor(st.emText) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val underlineFallback = stColor(st.underline) ?: MaterialTheme.colorScheme.primary
    val quoteFallback = stColor(st.quote) ?: MaterialTheme.colorScheme.primary
    val userBubbleFallback = stColor(st.userBubbleTint) ?: MaterialTheme.colorScheme.primaryContainer
    val botBubbleFallback = stColor(st.botBubbleTint) ?: MaterialTheme.colorScheme.surfaceContainerLow
    val borderFallback = stColor(st.border) ?: androidx.compose.ui.graphics.Color(0x80000000)
    val shadowFallback = stColor(st.shadow) ?: androidx.compose.ui.graphics.Color(0x80000000)
    val blurTintFallback = stColor(st.blurTint) ?: EmberTheme.colors.surface

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
                            onSave = { AppearancePrefs.saveStBodyColor(context, it) },
                        )
                        ColorField(
                            label = "次要文字色", hint = "官方 --SmartThemeEmColor：斜体 <em>/<i> + 小字时间戳（例 #919191）", value = AppearancePrefs.stEmColor(context), fallback = emFallback,
                            onSave = { AppearancePrefs.saveStEmColor(context, it) },
                        )
                        ColorField(
                            label = "下划线色", hint = "官方 --SmartThemeUnderlineColor（<u> 或 ~text~，例 #BCE7CF）", value = AppearancePrefs.stUnderlineColor(context), fallback = underlineFallback,
                            onSave = { AppearancePrefs.saveStUnderlineColor(context, it) },
                        )
                        ColorField(
                            label = "引用色", hint = "官方 --SmartThemeQuoteColor（<q>/blockquote/链接，例 #E18A24）", value = AppearancePrefs.stQuoteColor(context), fallback = quoteFallback,
                            onSave = { AppearancePrefs.saveStQuoteColor(context, it) },
                        )
                        ColorField(
                            label = "用户气泡底色", hint = "官方 --SmartThemeUserMesBlurTintColor（例 #0000004D）", value = AppearancePrefs.stUserBubble(context), fallback = userBubbleFallback,
                            onSave = { AppearancePrefs.saveStUserBubble(context, it) },
                        )
                        ColorField(
                            label = "AI 气泡底色", hint = "官方 --SmartThemeBotMesBlurTintColor（例 #3C3C3C4D）", value = AppearancePrefs.stBotBubble(context), fallback = botBubbleFallback,
                            onSave = { AppearancePrefs.saveStBotBubble(context, it) },
                        )
                        ColorField(
                            label = "边框色", hint = "官方 --SmartThemeBorderColor（例 #00000080）", value = AppearancePrefs.stBorderColor(context), fallback = borderFallback,
                            onSave = { AppearancePrefs.saveStBorderColor(context, it) },
                        )
                        ColorField(
                            label = "阴影色", hint = "官方 --SmartThemeShadowColor（例 #00000080）", value = AppearancePrefs.stShadowColor(context), fallback = shadowFallback,
                            onSave = { AppearancePrefs.saveStShadowColor(context, it) },
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
                        Text("毛玻璃色调（官方字段）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "色调 = 官方 --SmartThemeBlurTintColor（默认 #171717）；留空 = 跟随主题。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorField(
                            label = "玻璃色调", hint = "官方 --SmartThemeBlurTintColor（例 #171717）", value = AppearancePrefs.stBlurTint(context), fallback = blurTintFallback,
                            onSave = { AppearancePrefs.saveStBlurTint(context, it) },
                        )
                        Text(
                            "模糊开关与强度在 外观与主题 → 背景模糊，一处控制。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                var collapse by remember { mutableStateOf(RenderPrefs.collapseNewlines(context)) }
                var separator by remember { mutableStateOf(RenderPrefs.exampleSeparator(context)) }
                var encodeTags by remember { mutableStateOf(AppearancePrefs.encodeTags(context)) }
                var fixMarkdown by remember { mutableStateOf(AppearancePrefs.fixMarkdown(context)) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("行为与兼容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "对齐官方 power_user：折叠连续换行（collapse_newlines）、消息示例分隔符（context.example_separator，默认 ***）、标签转义与 Markdown 修复。",
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
                        RenderSwitchRow(
                            label = "转义标签（encode_tags）",
                            hint = "官方 power_user.encode_tags（默认关）：开 = 把 < > 转义为纯文本、HTML 不再渲染；关 = 允许部分 HTML 标签按网页渲染",
                            checked = encodeTags,
                        ) { encodeTags = it; AppearancePrefs.saveEncodeTags(context, it) }
                        RenderSwitchRow(
                            label = "自动修复 Markdown",
                            hint = "官方 power_user.auto_fix_generated_markdown（默认开）：显示前修复模型生成的坏 Markdown",
                            checked = fixMarkdown,
                        ) { fixMarkdown = it; AppearancePrefs.saveFixMarkdown(context, it) }
                        var kernelRender by remember { mutableStateOf(RenderPrefs.kernelRender(context)) }
                        RenderSwitchRow(
                            label = "内核渲染（V2）",
                            hint = "AI 消息正文走 WebView 官方管线（Showdown/DOMPurify/代码高亮/主题 CSS 同源），头像与操作条仍为原生；关闭回退旧原生渲染",
                            checked = kernelRender,
                        ) { kernelRender = it; RenderPrefs.setKernelRender(context, it) }
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
        }
    }
    }
}

@Composable
private fun RenderSwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}
