package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.icons.FaIcons

/**
 * 用户设置（官方 div_user_settings 移植）：
 * UI 主题/排版/渲染入口 + 聊天与消息处理（power_user 语义）+ 自动滑动/续写 + 用户提示偏置。
 */
@Composable
fun UserSettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTypography: () -> Unit,
    onOpenRender: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    var behavior by remember { mutableStateOf(BehaviorPrefs.load(context)) }
    var autoContinue by remember { mutableStateOf(GenerationPrefs.autoContinueEnabled(context)) }
    var autoContinueLength by remember { mutableStateOf(GenerationPrefs.autoContinueTargetLength(context).toString()) }

    fun saveBehavior(s: BehaviorSettings = behavior) {
        behavior = s
        BehaviorPrefs.save(context, s)
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "用户设置", subtitle = "UI 主题 · 聊天处理 · 自动化", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    UserNavCard("外观与主题", "主题模式 · 预设 · 氛围滤镜 · 圆角字体", FaIcons.Paintbrush, onOpenAppearance)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) { UserNavCard("排版", "字号 · 行距", FaIcons.FileLines, onOpenTypography) }
                        Box(modifier = Modifier.weight(1f)) { UserNavCard("消息渲染", "Markdown · 气泡", FaIcons.Eye, onOpenRender) }
                    }
                }
                item {
                    UserSectionCard(title = "聊天与消息处理", subtitle = "官方 power_user Chat/Message Handling") {
                        UserSwitchRow(
                            label = "裁剪空格",
                            hint = "trim_spaces：消息首尾空格裁剪（默认开）",
                            checked = behavior.trimSpaces,
                        ) { saveBehavior(behavior.copy(trimSpaces = it)) }
                        UserSwitchRow(
                            label = "裁剪残句",
                            hint = "trim_sentences：截断不完整句子（默认关）",
                            checked = behavior.trimSentences,
                        ) { saveBehavior(behavior.copy(trimSentences = it)) }
                        UserSwitchRow(
                            label = "保留角色名前缀",
                            hint = "allow_name2_display：显示时保留正文“角色名:”前缀（默认关）",
                            checked = behavior.allowName2Display,
                        ) { saveBehavior(behavior.copy(allowName2Display = it)) }
                        UserSwitchRow(
                            label = "示例置顶",
                            hint = "pin_examples：对话示例固定在上下文末尾（默认关）",
                            checked = behavior.pinExamples,
                        ) { saveBehavior(behavior.copy(pinExamples = it)) }
                        UserSwitchRow(
                            label = "发送时剥离示例",
                            hint = "strip_examples：发送前从提示词剥离示例（默认关）",
                            checked = behavior.stripExamples,
                        ) { saveBehavior(behavior.copy(stripExamples = it)) }
                        UserSwitchRow(
                            label = "角色名作停止串",
                            hint = "names_as_stop_strings（默认开）",
                            checked = behavior.namesAsStopStrings,
                        ) { saveBehavior(behavior.copy(namesAsStopStrings = it)) }
                        UserSwitchRow(
                            label = "消息 token 计数",
                            hint = "message_token_count_enabled：消息气泡显示 token 数（默认关）",
                            checked = behavior.messageTokenCount,
                        ) { saveBehavior(behavior.copy(messageTokenCount = it)) }
                    }
                }
                item {
                    UserSectionCard(title = "自动滑动 / 自动续写", subtitle = "回复过短自动重掷与继续生成") {
                        UserSwitchRow(
                            label = "自动续写",
                            hint = "auto_continue：回复低于目标长度自动继续（默认关）",
                            checked = autoContinue,
                        ) {
                            autoContinue = it
                            GenerationPrefs.saveAutoContinue(
                                context,
                                it,
                                autoContinueLength.toIntOrNull() ?: 0,
                                GenerationPrefs.allowChatCompletions(context),
                            )
                        }
                        if (autoContinue) {
                            EmberTextField(
                                value = autoContinueLength,
                                onValueChange = { v ->
                                    autoContinueLength = v
                                    GenerationPrefs.saveAutoContinue(
                                        context,
                                        true,
                                        v.filter { it.isDigit() }.toIntOrNull() ?: 0,
                                        GenerationPrefs.allowChatCompletions(context),
                                    )
                                },
                                label = { Text("目标长度（tokens，0 = 按模型默认）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        UserSwitchRow(
                            label = "自动滑动",
                            hint = "auto_swipe：不满足条件自动重掷（默认关）",
                            checked = behavior.autoSwipe,
                        ) { saveBehavior(behavior.copy(autoSwipe = it)) }
                        if (behavior.autoSwipe) {
                            EmberTextField(
                                value = behavior.autoSwipeMinimumLength.toString(),
                                onValueChange = { v ->
                                    saveBehavior(behavior.copy(autoSwipeMinimumLength = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                },
                                label = { Text("最短回复长度（不足则重掷）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    UserSectionCard(title = "用户提示偏置", subtitle = "每次请求附带的固定提示") {
                        UserSwitchRow(
                            label = "显示偏置输入框",
                            hint = "show_user_prompt_bias（默认开）",
                            checked = behavior.showUserPromptBias,
                        ) { saveBehavior(behavior.copy(showUserPromptBias = it)) }
                        EmberTextField(
                            value = behavior.userPromptBias,
                            onValueChange = { saveBehavior(behavior.copy(userPromptBias = it)) },
                            label = { Text("偏置内容") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) { UserNavCard("数据与隐私", "导出 · 清除", FaIcons.Folder, onOpenData) }
                        Box(modifier = Modifier.weight(1f)) { UserNavCard("关于", "版本 · 许可", FaIcons.Star, onOpenAbout) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserNavCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun UserSectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun UserSwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}
