package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.components.EmberSwitch

/**
 * 消息渲染行为开关：对齐官方 power_user 行为字段（折叠换行/示例分隔符/标签转义/Markdown 修复）与内核排障开关。
 * 颜色与排版没有本地覆盖——全部由官方主题接管（ShellTheme 字段推导 + 内核主题 CSS）。
 */
@Composable
fun MessageRenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "消息渲染", onBack = onBack, sky = settingsSky)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                var collapse by remember { mutableStateOf(RenderPrefs.collapseNewlines(context)) }
                var separator by remember { mutableStateOf(RenderPrefs.exampleSeparator(context)) }
                var encodeTags by remember { mutableStateOf(AppearancePrefs.encodeTags(context)) }
                var fixMarkdown by remember { mutableStateOf(AppearancePrefs.fixMarkdown(context)) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = EmberTheme.colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("行为与兼容", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
                        Text(
                            "对齐官方 power_user：折叠连续换行（collapse_newlines）、消息示例分隔符（context.example_separator，默认 ***）、标签转义与 Markdown 修复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.inkMute,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text("折叠连续换行", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            EmberSwitch(
                                checked = collapse,
                                onChange = {
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
                        var strictMode by remember { mutableStateOf(RenderPrefs.strictMode(context)) }
                        RenderSwitchRow(
                            label = "内核严格模式（排障用）",
                            hint = "禁用 WebView JavaScript，只保留静态 HTML/CSS 兜底渲染（图片/样式正常、脚本与扩展桥全部失效）。默认关=全功能零打扰；开启后需重进聊天页生效",
                            checked = strictMode,
                        ) { strictMode = it; RenderPrefs.setStrictMode(context, it) }
                        ShellInput(
                            value = separator,
                            onValueChange = {
                                separator = it
                                RenderPrefs.setExampleSeparator(context, it)
                            },
                            label = "消息示例分隔符（example_separator）",
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
            Text(hint, style = MaterialTheme.typography.bodySmall, color = EmberTheme.colors.inkMute)
        }
        EmberSwitch(checked = checked, onChange = onChange)
    }
}
