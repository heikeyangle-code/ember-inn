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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.emberinn.app.ui.components.EmberSlider
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.icons.PhosphorIcons

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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    // 当前连接卡片（点击进 API 连接分区）
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProviders),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(PhosphorIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    spec?.displayName ?: "API 连接",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    active?.model?.ifBlank { "未选模型" } ?: "未配置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(PhosphorIcons.CaretRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    AiSectionCard(title = "采样预设", subtitle = "按当前连接协议取预设目录，选择即应用") {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth().clickable { presetMenu = true },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        presetName.ifBlank { "未选择" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(PhosphorIcons.CaretDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
                        TextButtonRow(text = "管理全部预设（上下文 / 指导 / 系统提示 / 推理）", icon = PhosphorIcons.Folder, onClick = onOpenPresets)
                    }
                }
                item {
                    AiSectionCard(title = "长度", subtitle = "上下文上限与单次回复长度（tokens）") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            EmberTextField(
                                value = contextWindow.toString(),
                                onValueChange = vm::setContextWindow,
                                label = { Text("上下文长度") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            EmberTextField(
                                value = maxTokens.toString(),
                                onValueChange = vm::setMaxTokens,
                                label = { Text("回复长度") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    AiSectionCard(title = "采样器", subtitle = "官方滑块语义：点数值可手动输入") {
                        SamplerSliderRow(
                            label = "Temperature",
                            value = sampler.temperature.toFloat(),
                            range = 0f..2f,
                            onChange = { vm.setTemperature(it.toDouble()) },
                        )
                        SamplerSliderRow(
                            label = "Top-P",
                            value = sampler.topP.toFloat(),
                            range = 0f..1f,
                            onChange = { vm.setTopP(it.toDouble()) },
                        )
                        SamplerSliderRow(
                            label = "Presence Penalty",
                            value = sampler.presencePenalty.toFloat(),
                            range = -2f..2f,
                            onChange = { vm.setPresencePenalty(it.toDouble()) },
                        )
                        SamplerSliderRow(
                            label = "Frequency Penalty",
                            value = sampler.frequencyPenalty.toFloat(),
                            range = -2f..2f,
                            onChange = { vm.setFrequencyPenalty(it.toDouble()) },
                        )
                    }
                }
                item {
                    AiSectionCard(title = "流式与其他", subtitle = "流式输出与 Prompt Manager") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("流式输出", style = MaterialTheme.typography.bodyMedium)
                                Text("打字机式逐步显示回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            EmberSwitch(checked = sampler.stream, onCheckedChange = vm::setStreaming)
                        }
                        TextButtonRow(text = "Prompt Manager（提示词编排）", icon = PhosphorIcons.List, onClick = onOpenPromptManager)
                        if (message != null) {
                            Text(
                                message!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { saveAll() },
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text("保存到当前连接", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
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
private fun SamplerSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
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
private fun TextButtonRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
