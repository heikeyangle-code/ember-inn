package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.components.EmberSlider
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons

/**
 * AI 响应配置（官方 left-nav-panel #ai_response_configuration 移植）：
 * 采样预设选择即应用（按活动协议取预设目录）+ 上下文/回复长度 + 采样器 + 流式，
 * 直接编辑当前激活连接的采样参数，保存走 ProviderViewModel（与提供商详情页同一条路径）。
 */
@Composable
fun AiResponseScreen(
    vm: ProviderViewModel,
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenPromptManager: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val c = EmberTheme.colors
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val active = profiles.firstOrNull { it.id == activeId }

    // 载入激活 profile 的编辑状态（openDetail 内含 sampler / contextWindow / maxTokens）
    LaunchedEffect(active?.providerId) {
        active?.providerId?.takeIf { it.isNotBlank() }?.let { vm.openDetail(it) }
    }

    val sampler by vm.editingSampler.collectAsState()
    val contextWindow by vm.contextWindow.collectAsState()
    val maxTokens by vm.maxTokens.collectAsState()
    val message by vm.message.collectAsState()
    var presetMenu by remember { mutableStateOf(false) }
    val presetNames = remember(active?.providerId) { PresetSettingsStore.samplerPresetNames(context) }
    val presetName = remember(active?.providerId) { PresetPrefsStore.load(context).samplerPreset }
    val spec = remember(active?.providerId) { active?.providerId?.let { vm.providers.firstOrNull { p -> p.id == it } } }

    fun saveAll() = vm.save()

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "AI 响应配置",
                subtitle = active?.let { "${spec?.displayName ?: it.providerId} · ${it.model.ifBlank { "未选模型" }}" } ?: "未配置连接",
                onBack = onBack,
                sky = settingsSky,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = settingsPagePadding(),
            ) {
                item {
                    // 当前连接行（点击进 API 连接分区）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenProviders)
                            .padding(horizontal = 4.dp, vertical = 9.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(FaIcons.Link, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(spec?.displayName ?: "API 连接", color = c.ink, fontSize = 15.sp)
                            Text(
                                active?.model?.ifBlank { "未选模型" } ?: "未配置",
                                color = c.inkMute,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(14.dp))
                    }
                }
                item {
                    Column {
                        GroupLabel("采样预设")
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surfaceSink)
                                    .clickable { presetMenu = true }
                                    .padding(horizontal = 13.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    presetName.ifBlank { "未选择" },
                                    color = c.ink,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(FaIcons.ChevronDown, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(13.dp))
                            }
                            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                                presetNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(if (name == "gui") "GUI KoboldAI Settings" else name) },
                                        onClick = {
                                            presetMenu = false
                                            vm.applySamplerPreset(name)
                                        },
                                    )
                                }
                            }
                        }
                        ActionTextRow("管理全部预设（上下文 / 指导 / 系统提示 / 推理）", FaIcons.Folder, onOpenPresets)
                    }
                }
                item {
                    Column {
                        GroupLabel("长度")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ShellInput(
                                value = contextWindow.toString(),
                                onValueChange = vm::setContextWindow,
                                label = "上下文长度",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            ShellInput(
                                value = maxTokens.toString(),
                                onValueChange = vm::setMaxTokens,
                                label = "回复长度",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    Column {
                        GroupLabel("采样器")
                        SamplerSliderRow("Temperature", sampler.temperature.toFloat(), 0f..2f) { vm.setTemperature(it.toDouble()) }
                        SamplerSliderRow("Top-P", sampler.topP.toFloat(), 0f..1f) { vm.setTopP(it.toDouble()) }
                        SamplerSliderRow("Presence Penalty", sampler.presencePenalty.toFloat(), -2f..2f) { vm.setPresencePenalty(it.toDouble()) }
                        SamplerSliderRow("Frequency Penalty", sampler.frequencyPenalty.toFloat(), -2f..2f) { vm.setFrequencyPenalty(it.toDouble()) }
                    }
                }
                item {
                    Column {
                        GroupLabel("流式与其他")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("流式输出", color = c.ink, fontSize = 15.sp)
                                Text("打字机式逐步显示回复", color = c.inkMute, fontSize = 12.sp)
                            }
                            EmberSwitch(checked = sampler.stream, onChange = vm::setStreaming)
                        }
                        ActionTextRow("Prompt Manager（提示词编排）", FaIcons.ListUl, onOpenPromptManager)
                        if (message != null) {
                            Text(message!!, color = c.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                item {
                    // 主操作：操作面实底胶囊
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(c.surface2)
                            .clickable { saveAll() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("保存到当前连接", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplerSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val c = EmberTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(String.format("%.2f", value), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        EmberSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionTextRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = c.accent, fontSize = 13.sp)
    }
}
