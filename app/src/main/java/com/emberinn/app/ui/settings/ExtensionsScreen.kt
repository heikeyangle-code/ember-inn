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

/** 扩展插件：HTML 卡片的“可交互性”开关（默认开）。渲染与交互分离：卡片始终渲染，此开关只决定脚本是否执行。 */
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var interactiveCards by remember { mutableStateOf(ExtensionPrefs.interactiveCards(context)) }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "扩展插件", onBack = onBack, sky = settingsSky)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                interactiveCards = !interactiveCards
                                ExtensionPrefs.setInteractiveCards(context, interactiveCards)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("交互 HTML 卡片", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "消息里 ``` 包着的 HTML 代码块始终渲染成网页卡片；此开关只决定卡片内的脚本/按钮是否可交互。\n" +
                                    "关闭后卡片照常显示，但脚本被沙箱禁止、不再执行。包含 .char-avatar、{{charAvatarPath}} 宏、原代码折叠、自动测高。默认开。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        EmberSwitch(
                            checked = interactiveCards,
                            onCheckedChange = {
                                interactiveCards = it
                                ExtensionPrefs.setInteractiveCards(context, it)
                            },
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
                        Text("说明与安全", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "渲染与交互分离：卡片渲染不依赖本开关。\n" +
                                "打开时卡片内 JavaScript 会真实执行、可发网络请求（没有 JS 桥，碰不到 App 数据与 Android 功能），仅执行你信任的角色卡代码；\n" +
                                "关闭时脚本与表单被 sandbox 沙箱禁止，卡片按静态页面显示。官方 SillyTavern 核心对 ``` 代码块只显示代码框，iframe 卡片来自第三方 HTML 代码注入器。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }
}
