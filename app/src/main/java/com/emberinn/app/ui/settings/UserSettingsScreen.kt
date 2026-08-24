package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons

/**
 * 用户设置（官方 div_user_settings 移植）：
 * UI 主题/排版/渲染入口 + 聊天与消息处理（power_user 语义）+ 自动滑动/续写 + 用户提示偏置。
 * 视觉层=新语言：E0 分组留白分隔，无卡片框；全部 hint 保留官方键名与默认值口径。
 */
@Composable
fun UserSettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
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
            SettingsTopBar(title = "用户设置", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item {
                    UserNavRow("外观与主题", "主题模式 · 氛围滤镜 · 圆角字体", FaIcons.Paintbrush, onOpenAppearance)
                }
                item {
                    UserNavRow("消息渲染", "官方配色 · 兼容行为", FaIcons.Eye, onOpenRender)
                }
                item {
                    Column {
                        GroupLabel("聊天与消息处理")
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
                        UserSwitchRow(
                            label = "新消息自动滚到最新",
                            hint = "auto_scroll_chat_to_bottom：生成时自动贴底跟随（默认开）",
                            checked = behavior.autoScrollChatToBottom,
                        ) { saveBehavior(behavior.copy(autoScrollChatToBottom = it)) }
                    }
                }
                item {
                    Column {
                        GroupLabel("流式输出")
                        UserSwitchRow(
                            label = "平滑流式",
                            hint = "smooth_streaming：逐字揭示代替整段蹦出（默认关）",
                            checked = behavior.smoothStreaming,
                        ) { saveBehavior(behavior.copy(smoothStreaming = it)) }
                        if (behavior.smoothStreaming) {
                            ShellInput(
                                value = behavior.smoothStreamingSpeed.toString(),
                                onValueChange = { v ->
                                    saveBehavior(
                                        behavior.copy(smoothStreamingSpeed = v.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 100) ?: 50),
                                    )
                                },
                                label = "速度（1-100，官方默认 50，越大越快）",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            UserSwitchRow(
                                label = "思考块不平滑",
                                hint = "smooth_streaming_no_think（默认关；本 App 思考走独立通道已不平滑，仅存档）",
                                checked = behavior.smoothStreamingNoThink,
                            ) { saveBehavior(behavior.copy(smoothStreamingNoThink = it)) }
                        }
                    }
                }
                item {
                    Column {
                        GroupLabel("自动滑动 / 自动续写")
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
                            ShellInput(
                                value = autoContinueLength,
                                onValueChange = { v ->
                                    autoContinueLength = v
                                    GenerationPrefs.saveAutoContinue(
                                        context,
                                        true,
                                        v.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0,
                                        GenerationPrefs.allowChatCompletions(context),
                                    )
                                },
                                label = "目标长度（tokens，0 = 按模型默认）",
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
                            ShellInput(
                                value = behavior.autoSwipeMinimumLength.toString(),
                                onValueChange = { v ->
                                    saveBehavior(behavior.copy(autoSwipeMinimumLength = v.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0))
                                },
                                label = "最短回复长度（不足则重掷）",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    var openLastChat by remember { mutableStateOf(AppearancePrefs.openLastChat(context)) }
                    Column {
                        GroupLabel("启动与恢复")
                        UserSwitchRow(
                            label = "启动进入上次聊天",
                            hint = "开启后启动直接回到上次会话（默认关）",
                            checked = openLastChat,
                        ) {
                            openLastChat = it
                            AppearancePrefs.saveOpenLastChat(context, it)
                        }
                    }
                }
                item {
                    Column {
                        GroupLabel("用户提示偏置")
                        UserSwitchRow(
                            label = "显示偏置输入框",
                            hint = "show_user_prompt_bias（默认开）",
                            checked = behavior.showUserPromptBias,
                        ) { saveBehavior(behavior.copy(showUserPromptBias = it)) }
                        if (behavior.showUserPromptBias) {
                            ShellInput(
                                value = behavior.userPromptBias,
                                onValueChange = { saveBehavior(behavior.copy(userPromptBias = it)) },
                                label = "偏置内容（每次请求附带）",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Column {
                        GroupLabel("数据")
                        UserNavRow("数据与隐私", "导出 · 清除", FaIcons.Folder, onOpenData)
                        UserNavRow("关于", "版本 · 许可", FaIcons.CircleInfo, onOpenAbout)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserNavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp)
            Text(subtitle, color = c.inkMute, fontSize = 12.sp)
        }
        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun UserSwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = c.ink, fontSize = 15.sp)
            Text(hint, color = c.inkMute, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        EmberSwitch(checked = checked, onChange = onChange)
    }
}
