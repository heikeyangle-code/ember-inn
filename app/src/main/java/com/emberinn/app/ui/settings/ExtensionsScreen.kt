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
import com.emberinn.app.ui.components.EmberSwitch

/** 扩展插件：交互 HTML 卡片相关能力，每个功能独立开关，默认全开。 */
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var interactiveCards by remember { mutableStateOf(ExtensionPrefs.interactiveCards(context)) }
    var messageJs by remember { mutableStateOf(ExtensionPrefs.messageJs(context)) }
    var networkMedia by remember { mutableStateOf(ExtensionPrefs.networkMedia(context)) }
    var externalLinks by remember { mutableStateOf(ExtensionPrefs.externalLinks(context)) }
    var autoHeight by remember { mutableStateOf(ExtensionPrefs.autoHeight(context)) }
    var avatarClasses by remember { mutableStateOf(ExtensionPrefs.avatarClasses(context)) }
    var codeFolding by remember { mutableStateOf(ExtensionPrefs.codeFolding(context)) }
    var mermaid by remember { mutableStateOf(ExtensionPrefs.mermaid(context)) }
    var htmlMessages by remember { mutableStateOf(RenderPrefs.htmlEnabled(context)) }
    var blockJsUrls by remember { mutableStateOf(ExtensionPrefs.blockJavascriptUrls(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "扩展插件", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("交互 HTML 卡片（决策卡/状态栏/追踪器）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            SwitchRow("交互 HTML 卡片", "消息中 ``` 包着的 HTML 代码块 → 独立 iframe 运行（按钮/状态栏/脚本可交互）", interactiveCards) {
                interactiveCards = it; ExtensionPrefs.setInteractiveCards(context, it)
            }
            SwitchRow("消息内 JavaScript", "卡片内的 <script>/onclick/Vue/React 可执行；关掉后只显示静态 HTML", messageJs) {
                messageJs = it; ExtensionPrefs.setMessageJs(context, it)
            }
            SwitchRow("远程图片与网络加载", "卡片里的远程图片/字体/媒体可加载；关掉后网络全部拦截", networkMedia) {
                networkMedia = it; ExtensionPrefs.setNetworkMedia(context, it)
            }
            SwitchRow("链接用系统浏览器打开", "http(s) 链接跳系统浏览器；关掉后在卡片内打开", externalLinks) {
                externalLinks = it; ExtensionPrefs.setExternalLinks(context, it)
            }
            SwitchRow("自动测高", "iframe/页面高度按内容自适应；关掉后固定最高 420dp", autoHeight) {
                autoHeight = it; ExtensionPrefs.setAutoHeight(context, it)
            }
            SwitchRow("角色头像类 .char-avatar / {{charAvatarPath}}", "对齐酒馆助手：状态栏里放头像的 CSS 类与宏", avatarClasses) {
                avatarClasses = it; ExtensionPrefs.setAvatarClasses(context, it)
            }
            SwitchRow("原代码折叠", "交互卡片上方显示可展开的“原代码”；关掉后只显示卡片", codeFolding) {
                codeFolding = it; ExtensionPrefs.setCodeFolding(context, it)
            }
            item { Text("其他渲染", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            SwitchRow("Mermaid 图表", "```mermaid 代码块渲染成图表；关掉后按普通代码块显示", mermaid) {
                mermaid = it; ExtensionPrefs.setMermaid(context, it)
            }
            SwitchRow("HTML 消息渲染", "消息含 HTML 标签时用 WebView 展示（与外观页同一开关）", htmlMessages) {
                htmlMessages = it; RenderPrefs.setHtmlEnabled(context, it)
            }
            item { Text("安全", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            SwitchRow("拦截 javascript: 链接", "防止点击卡片链接时执行脚本导航；默认开，建议保持", blockJsUrls) {
                blockJsUrls = it; ExtensionPrefs.setBlockJavascriptUrls(context, it)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("说明与安全", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "这些能力对齐酒馆助手（Tavern Helper）、HTML 代码注入器与 SimTracker/RPG Companion 等决策卡插件：\n" +
                                "卡片内 JavaScript 会真实执行、可发网络请求；没有 JS 桥，碰不到 App 数据与 Android 功能。\n" +
                                "只执行你信任的角色卡代码。官方 SillyTavern 默认禁止消息脚本，本功能为有意放开。",
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
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EmberSwitch(checked = checked, onCheckedChange = onChange)
        }
    }
}
