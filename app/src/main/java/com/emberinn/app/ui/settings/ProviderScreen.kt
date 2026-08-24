@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emberinn.app.ui.design.components.EmptyState
import com.emberinn.app.ui.components.EmberSkeletonBox

import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.ProviderIcon
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberBottomSheet
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.ProviderSpec
import com.emberinn.engine.prompt.PresetLibrary

/** 提供商列表（参照命理2：搜索 + 卡片列表 + 头像；点卡片进详情）。 */
@Composable
fun ProviderListScreen(
    vm: ProviderViewModel,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(vm.providers, query) {
        val q = query.trim()
        if (q.isBlank()) {
            vm.providers
        } else {
            vm.providers.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    it.id.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true)
            }
        }
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = "提供商与模型",
            subtitle = "${vm.providers.size} 家服务商",
            onBack = onBack,
            sky = settingsSky,
        )
        EmberTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索提供商") },
            leadingIcon = { Icon(FaIcons.MagnifyingGlass, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (profiles.isNotEmpty()) {
            Text(
                "我的连接",
                style = MaterialTheme.typography.titleSmall,
                color = EmberTheme.colors.accent,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileChip(
                        profile = profile,
                        active = profile.id == activeId,
                        onSwitch = { vm.switchActive(profile.id) },
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { spec ->
                ProviderCard(
                    spec = spec,
                    configured = profiles.any { it.providerId == spec.id },
                    onClick = { onOpenDetail(spec.id) },
                )
            }
        }
    }
    }
}

@Composable
private fun ProfileChip(
    profile: ConnectionProfile,
    active: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (active) EmberTheme.colors.surface else EmberTheme.colors.surfaceVariant,
        modifier = Modifier.clickable(onClick = onSwitch),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        ) {
            Text(
                profile.name.ifBlank { profile.providerId },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active) {
                Text(" ✓", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    FaIcons.XMark,
                    contentDescription = "删除连接",
                    tint = EmberTheme.colors.inkMute,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    spec: ProviderSpec,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .emberShadow(
                color = EmberTheme.colors.accent.copy(alpha = 0.14f),
                radius = 10.dp,
                offset = DpOffset(0.dp, 4.dp),
                alpha = 0.08f,
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EmberTheme.colors.surfaceContainerLow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            ProviderIcon(spec.icon, spec.displayName, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (configured) {
                            EmberTheme.colors.surface
                        } else {
                            EmberTheme.colors.surfaceVariant
                        },
                    ) {
                        Text(
                            if (configured) "已配置" else "未配置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (configured) {
                                EmberTheme.colors.ink
                            } else {
                                EmberTheme.colors.inkMute
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmberTheme.colors.inkMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(FaIcons.ChevronRight, contentDescription = null, tint = EmberTheme.colors.inkMute, modifier = Modifier.size(20.dp))
        }
    }
}

/** 提供商详情（参照命理2 ProviderConfigure：名称 / Key / 接口地址 / 开关与模型选择）。 */
@Composable
fun ProviderDetailScreen(
    vm: ProviderViewModel,
    providerId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val spec = remember(providerId) { vm.providers.firstOrNull { it.id == providerId } }
    if (spec == null) {
        Text("提供商不存在", modifier = Modifier.padding(24.dp))
        return
    }

    LaunchedEffect(providerId) { vm.openDetail(providerId) }

    val profiles by vm.profiles.collectAsState()
    val name by vm.profileName.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val baseUrl by vm.baseUrl.collectAsState()
    val region by vm.region.collectAsState()
    val accountId by vm.accountId.collectAsState()
    val apiVersion by vm.apiVersion.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    val contextWindow by vm.contextWindow.collectAsState()
    val maxTokens by vm.maxTokens.collectAsState()
    val sampler by vm.editingSampler.collectAsState()
    val testing by vm.testing.collectAsState()
    val message by vm.message.collectAsState()
    val reverseProxy by vm.reverseProxy.collectAsState()
    val proxyPassword by vm.proxyPassword.collectAsState()
    val customUrl by vm.customUrl.collectAsState()
    val customIncludeBody by vm.customIncludeBody.collectAsState()
    val customExcludeBody by vm.customExcludeBody.collectAsState()
    val customIncludeHeaders by vm.customIncludeHeaders.collectAsState()
    val customPromptPostProcessing by vm.customPromptPostProcessing.collectAsState()
    val bypassStatusCheck by vm.bypassStatusCheck.collectAsState()
    val showExternalModels by vm.showExternalModels.collectAsState()
    val groupModels by vm.groupModels.collectAsState()
    val sortModels by vm.sortModels.collectAsState()
    val azureDeploymentName by vm.azureDeploymentName.collectAsState()
    val azureOpenaiModel by vm.azureOpenaiModel.collectAsState()
    val vertexaiAuthMode by vm.vertexaiAuthMode.collectAsState()
    val vertexaiExpressProjectId by vm.vertexaiExpressProjectId.collectAsState()
    val vertexaiServiceAccountJson by vm.vertexaiServiceAccountJson.collectAsState()
    val nanogptProvider by vm.nanogptProvider.collectAsState()
    val nanogptPaygOverride by vm.nanogptPaygOverride.collectAsState()

    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val existing = profiles.firstOrNull { it.providerId == spec.id }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = spec.displayName,
            onBack = onBack,
            trailing = if (existing != null) {
                {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(FaIcons.TrashCan, contentDescription = "删除连接")
                    }
                }
            } else null,
            sky = settingsSky,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CollapsibleSection("连接（API Connection）", initiallyExpanded = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderIcon(spec.icon, spec.displayName, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        spec.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkMute,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ShellInput(
                value = name,
                onValueChange = vm::setProfileName,
                label = "名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            EmberTextField(
                value = apiKey,
                onValueChange = vm::setApiKey,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { keyVisible = !keyVisible }) {
                        Text(if (keyVisible) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = baseUrl,
                onValueChange = vm::setBaseUrl,
                label = { Text("接口地址") },
                singleLine = true,
                isError = spec.baseUrl.isBlank() && baseUrl.isBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (spec.regionVariants.isNotEmpty()) {
                Text(
                    "区域",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    spec.regionVariants.forEach { variant ->
                        FilterChip(
                            selected = region == variant || (region.isBlank() && variant == spec.regionVariants.first()),
                            onClick = { vm.setRegion(variant) },
                            label = { Text(variant) },
                        )
                    }
                }
            }
            if (spec.id == "workers-ai") {
                ShellInput(
                    value = accountId,
                    onValueChange = vm::setAccountId,
                    label = "账户 ID",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (spec.id == "azure") {
                ShellInput(
                    value = apiVersion,
                    onValueChange = vm::setApiVersion,
                    label = "API 版本",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable { showModelSheet = true }
                    .emberShadow(
                        color = EmberTheme.colors.accent.copy(alpha = 0.14f),
                        radius = 10.dp,
                        offset = DpOffset(0.dp, 4.dp),
                        alpha = 0.08f,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text("默认模型", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        selectedModel.ifBlank { "未选择" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmberTheme.colors.inkMute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
            Icon(FaIcons.ChevronRight, contentDescription = null, tint = EmberTheme.colors.inkMute, modifier = Modifier.size(20.dp))
                }
            }
            }
            EmberTextField(
                value = maxTokens.toString(),
                onValueChange = vm::setMaxTokens,
                label = { Text("最大回复 tokens") },
                supportingText = { Text("官方默认 300（openai_max_tokens）；思考型模型太小会只思考不出正文") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            CollapsibleSection("采样参数（Sampler Settings）", initiallyExpanded = true) {
            var showSamplerPreset by remember { mutableStateOf(false) }
            // 官方选中名持久化在 oai_settings.preset_settings_openai；App 存 PresetPrefs.samplerPreset
            var samplerPresetName by remember {
                mutableStateOf(com.emberinn.app.ui.settings.PresetPrefsStore.load(context).samplerPreset)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("采样预设（官方 sampler-${com.emberinn.app.ui.settings.PresetSettingsStore.samplerPresetType(context)}）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { showSamplerPreset = true }) {
                        Text(if (samplerPresetName.isBlank()) "Default" else samplerPresetName)
                    }
                    DropdownMenu(
                        expanded = showSamplerPreset,
                        onDismissRequest = { showSamplerPreset = false },
                    ) {
                        com.emberinn.app.ui.settings.PresetSettingsStore.samplerPresetNames(context).forEach { presetName ->
                            DropdownMenuItem(
                                text = { Text(presetName) },
                                onClick = {
                                    samplerPresetName = presetName
                                    vm.applySamplerPreset(presetName)
                                    showSamplerPreset = false
                                },
                            )
                        }
                    }
                }
            }
            SwitchRow("流式输出（stream）", sampler.stream, vm::setStreaming)
            IntRow("top_k（0 = 不发送）", sampler.topK.toString(), vm::setTopK)
            DecimalRow("min_p（0-1）", sampler.minP.toString(), vm::setMinP)
            DecimalRow("top_a（0-1）", sampler.topA.toString(), vm::setTopA)
            DecimalRow("repetition_penalty（1-2）", sampler.repetitionPenalty.toString(), vm::setRepetitionPenalty)
            IntRow("seed（-1 = 不发送）", sampler.seed.toString(), vm::setSeed)
            IntRow("n（多回复变体，1-8）", sampler.n.toString(), vm::setN)
            DecimalRow("温度（temperature）", sampler.temperature.toString()) { v ->
                vm.setTemperature(v.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 1.0)
            }
            DecimalRow("核采样（topP）", sampler.topP.toString()) { v ->
                vm.setTopP(v.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0)
            }
            DecimalRow("存在惩罚（presencePenalty）", sampler.presencePenalty.toString()) { v ->
                vm.setPresencePenalty(v.toDoubleOrNull()?.coerceIn(-2.0, 2.0) ?: 0.0)
            }
            DecimalRow("频率惩罚（frequencyPenalty）", sampler.frequencyPenalty.toString()) { v ->
                vm.setFrequencyPenalty(v.toDoubleOrNull()?.coerceIn(-2.0, 2.0) ?: 0.0)
            }
            if (spec.id == "openrouter") {
                SwitchRow("use_fallback（route=fallback）", sampler.useFallback, vm::setUseFallback)
                SwitchRow("allow_fallbacks", sampler.allowFallbacks, vm::setAllowFallbacks)
                Text(
                    "middleout",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    listOf("on", "off", "auto").forEach { value ->
                        FilterChip(
                            selected = sampler.middleout == value,
                            onClick = { vm.setMiddleout(value) },
                            label = { Text(value) },
                        )
                    }
                }
                ShellInput(
                    value = sampler.openRouterProviders.joinToString(", "),
                    onValueChange = vm::setOpenRouterProviders,
                    label = "providers（逗号分隔）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                ShellInput(
                    value = sampler.openRouterQuantizations.joinToString(", "),
                    onValueChange = vm::setOpenRouterQuantizations,
                    label = "quantizations（逗号分隔）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SwitchRow(
                "请求 token 概率（logprobs，仅支持源生效）",
                sampler.requestTokenProbabilities,
                vm::setRequestTokenProbabilities,
            )
            Text("reasoning_effort（推理强度）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("auto", "min", "low", "medium", "high", "max").forEach { value ->
                    FilterChip(
                        selected = sampler.reasoningEffort == value,
                        onClick = { vm.setReasoningEffort(value) },
                        label = { Text(value) },
                    )
                }
            }
            Text("verbosity（详细程度，gpt-5 系）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto", "low", "medium", "high").forEach { value ->
                    FilterChip(
                        selected = (sampler.verbosity ?: "auto") == value,
                        onClick = { vm.setVerbosity(if (value == "auto") "auto" else value) },
                        label = { Text(value) },
                    )
                }
            }

            }
            CollapsibleSection("预设联动与提示词（oai_settings：bias/names/提示词/工具开关）") {
            // ---- 官方 oai_settings 其余预设联动字段 ----
            Text("预设联动设置（官方 oai_settings）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            // ---- 官方 bias 预设弹窗（openai.js createNewLogitBiasPreset / onLogitBiasPresetDeleteClick /
            //      onLogitBiasPresetImportFileChange / onLogitBiasPresetExportClick / createLogitBiasListItem）----
            Text("Logit Bias 预设（官方 openai_logit_bias）", style = MaterialTheme.typography.labelLarge, color = EmberTheme.colors.accent, modifier = Modifier.padding(top = 10.dp))
            Text("bias_preset_selected（logit_bias 预设）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sampler.biasPresets.keys.forEach { name ->
                    FilterChip(
                        selected = sampler.biasPresetSelected == name,
                        onClick = { vm.setBiasPresetSelected(name) },
                        label = { Text(name) },
                    )
                }
            }
            var showBiasNew by remember { mutableStateOf(false) }
            var biasNewName by remember { mutableStateOf("") }
            var showBiasDelete by remember { mutableStateOf(false) }
            var biasEditor by remember { mutableStateOf(false) }
            var biasExportName by remember { mutableStateOf("") }
            val biasImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val name = (uri.lastPathSegment ?: "bias").substringBeforeLast('.').ifBlank { "bias" }
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (!text.isNullOrBlank()) vm.importBiasPreset(name, text)
                }
            }
            val biasExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) {
                    val json = vm.biasPresetExportJson(biasExportName)
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { biasNewName = ""; showBiasNew = true }) { Text("新建预设") }
                TextButton(onClick = { biasImportLauncher.launch(arrayOf("application/json")) }) { Text("导入预设") }
                TextButton(
                    enabled = sampler.biasPresetSelected.isNotBlank(),
                    onClick = {
                        biasExportName = sampler.biasPresetSelected
                        biasExportLauncher.launch("${sampler.biasPresetSelected}.json")
                    },
                ) { Text("导出预设") }
                TextButton(
                    enabled = sampler.biasPresetSelected.isNotBlank(),
                    onClick = { showBiasDelete = true },
                ) { Text("删除预设") }
            }
            TextButton(
                enabled = sampler.biasPresetSelected.isNotBlank(),
                onClick = { biasEditor = true },
            ) { Text("编辑条目（${sampler.biasPresetSelected.ifBlank { "未选择" }}）") }
            if (showBiasNew) {
                AlertDialog(
                    onDismissRequest = { showBiasNew = false },
                    title = { Text("新建 bias 预设") },
                    text = {
                        ShellInput(
                            value = biasNewName,
                            onValueChange = { biasNewName = it },
                            label = "预设名（必须唯一）",
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (vm.addBiasPreset(biasNewName)) showBiasNew = false
                        }) { Text("创建") }
                    },
                    dismissButton = { TextButton(onClick = { showBiasNew = false }) { Text("取消") } },
                )
            }
            if (showBiasDelete) {
                AlertDialog(
                    onDismissRequest = { showBiasDelete = false },
                    title = { Text("删除预设？") },
                    text = { Text("将删除「${sampler.biasPresetSelected}」，不可恢复。") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteBiasPreset(sampler.biasPresetSelected)
                            showBiasDelete = false
                        }) { Text("删除", color = EmberTheme.colors.danger) }
                    },
                    dismissButton = { TextButton(onClick = { showBiasDelete = false }) { Text("取消") } },
                )
            }
            if (biasEditor) {
                val presetName = sampler.biasPresetSelected
                val entries = sampler.biasPresets[presetName] ?: emptyList()
                EmberBottomSheet(onDismissRequest = { biasEditor = false }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                        Text(
                            "编辑 bias 预设：$presetName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "文本或 [token ids]；数值 -100 ~ 100（官方 openai_logit_bias 模板）",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.inkMute,
                        )
                        Spacer(Modifier.size(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                ) {
                                    EmberTextField(
                                        value = entry.text,
                                        onValueChange = { vm.updateBiasEntry(presetName, entry.id, text = it) },
                                        placeholder = { Text("Text or [token ids]") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    EmberTextField(
                                        value = entry.value.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.coerceIn(-100.0, 100.0)
                                                ?.let { vm.updateBiasEntry(presetName, entry.id, value = it) }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(84.dp),
                                    )
                                    TextButton(onClick = { vm.moveBiasEntry(presetName, entry.id, up = true) }, enabled = index > 0) { Text("↑") }
                                    TextButton(onClick = { vm.moveBiasEntry(presetName, entry.id, up = false) }, enabled = index < entries.lastIndex) { Text("↓") }
                                    TextButton(onClick = { vm.removeBiasEntry(presetName, entry.id) }) { Text("删", color = EmberTheme.colors.danger) }
                                }
                            }
                        }
                        TextButton(onClick = { vm.addBiasEntry(presetName) }) { Text("添加条目") }
                    }
                }
            }
            Text("names_behavior（消息名字模式）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "NONE", 0 to "DEFAULT", 1 to "COMPLETION", 2 to "CONTENT").forEach { (v, label) ->
                    FilterChip(
                        selected = sampler.namesBehavior == v,
                        onClick = { vm.setNamesBehavior(v) },
                        label = { Text(label) },
                    )
                }
            }
            Text("消息角色与续写（names_behavior/continue_postfix/use_sysprompt/squash_system_messages）", style = MaterialTheme.typography.labelLarge, color = EmberTheme.colors.accent, modifier = Modifier.padding(top = 10.dp))
            Text("names_behavior（消息名字模式）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "NONE", 0 to "DEFAULT", 1 to "COMPLETION", 2 to "CONTENT").forEach { (v, label) ->
                    FilterChip(
                        selected = sampler.namesBehavior == v,
                        onClick = { vm.setNamesBehavior(v) },
                        label = { Text(label) },
                    )
                }
            }
            Text("continue_postfix（继续生成后缀）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "无", " " to "空格", "\n" to "换行", "\n\n" to "双换行").forEach { (v, label) ->
                    FilterChip(
                        selected = sampler.continuePostfix == v,
                        onClick = { vm.setContinuePostfix(v) },
                        label = { Text(label) },
                    )
                }
            }
                        SwitchRow(
                "use_sysprompt（Claude/Gemini system 独立角色，官方默认关）",
                sampler.useSysprompt,
                vm::setUseSysprompt,
            )
            SwitchRow(
                "合并 system 消息（squash_system_messages，官方默认关）",
                sampler.squashSystemMessages,
                vm::setSquashSystemMessages,
            )
            Text("inline_image_quality（内联图片质量）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto", "low", "high").forEach { value ->
                    FilterChip(
                        selected = sampler.inlineImageQuality == value,
                        onClick = { vm.setInlineImageQuality(value) },
                        label = { Text(value) },
                    )
                }
            }
            Text("tool_reasoning_mode（工具推理链）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("disabled", "since_last_user", "active_chain").forEach { value ->
                    FilterChip(
                        selected = sampler.toolReasoningMode == value,
                        onClick = { vm.setToolReasoningMode(value) },
                        label = { Text(value) },
                    )
                }
            }
            Text("工具与媒体（function_calling/tool_reasoning_mode/media_inlining/web_search/request_images）", style = MaterialTheme.typography.labelLarge, color = EmberTheme.colors.accent, modifier = Modifier.padding(top = 10.dp))
            SwitchRow("media_inlining（媒体 data URL 内联，官方默认开）", sampler.mediaInlining, vm::setMediaInlining)
            SwitchRow("function_calling（工具调用总开关，官方默认关）", sampler.functionCalling, vm::setFunctionCalling)
            SwitchRow("show_thoughts（显示推理内容，官方默认开）", sampler.showThoughts, vm::setShowThoughts)
            SwitchRow("enable_web_search（联网搜索，官方默认关）", sampler.enableWebSearch, vm::setEnableWebSearch)
            SwitchRow("continue_prefill（继续生成预填，官方默认关）", sampler.continuePrefill, vm::setContinuePrefill)
            SwitchRow("max_context_unlocked（解锁上下文上限，官方默认关）", sampler.maxContextUnlocked, vm::setMaxContextUnlocked)
            // 官方 request_images 块（data-source=makersuite,vertexai）：仅 Gemini 源显示
            if (spec.id in setOf("google", "vertexai")) {
                SwitchRow("request_images（请求内联图片，官方默认关）", sampler.requestImages, vm::setRequestImages)
                Text("request_image_resolution（分辨率）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "1K", "2K", "4K").forEach { value ->
                        FilterChip(
                            selected = sampler.requestImageResolution == value,
                            onClick = { vm.setRequestImageResolution(value) },
                            label = { Text(value.ifBlank { "Auto" }) },
                        )
                    }
                }
                Text("request_image_aspect_ratio（宽高比）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "1:1", "9:16", "16:9", "3:4", "4:3", "3:2", "2:3").forEach { value ->
                        FilterChip(
                            selected = sampler.requestImageAspectRatio == value,
                            onClick = { vm.setRequestImageAspectRatio(value) },
                            label = { Text(value.ifBlank { "Auto" }) },
                        )
                    }
                }
            }
            Text("提示词模板（Prompts）", style = MaterialTheme.typography.labelLarge, color = EmberTheme.colors.accent, modifier = Modifier.padding(top = 10.dp))
            Text("官方 main/nsfw/jailbreak 快捷编辑（PromptManager serviceSettings.prompts）", style = MaterialTheme.typography.labelSmall, color = EmberTheme.colors.inkMute)
            PromptQuickEdit(context, "main", "Main Prompt")
            PromptQuickEdit(context, "nsfw", "Auxiliary Prompt")
            PromptQuickEdit(context, "jailbreak", "Post-History Instructions")
            ShellInput(
                value = sampler.sendIfEmpty,
                onValueChange = vm::setSendIfEmpty,
                label = "send_if_empty（末条 assistant 时补发）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.assistantPrefill,
                onValueChange = vm::setAssistantPrefill,
                label = "assistant_prefill（Claude 继续预填）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.newChatPrompt,
                onValueChange = vm::setNewChatPrompt,
                label = "new_chat_prompt",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.newGroupChatPrompt,
                onValueChange = vm::setNewGroupChatPrompt,
                label = "new_group_chat_prompt",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.newExampleChatPrompt,
                onValueChange = vm::setNewExampleChatPrompt,
                label = "new_example_chat_prompt",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.continueNudgePrompt,
                onValueChange = vm::setContinueNudgePrompt,
                label = "continue_nudge_prompt",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.wiFormat,
                onValueChange = vm::setWiFormat,
                label = "wi_format（世界书 {0} 占位）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.scenarioFormat,
                onValueChange = vm::setScenarioFormat,
                label = "scenario_format",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.personalityFormat,
                onValueChange = vm::setPersonalityFormat,
                label = "personality_format",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.groupNudgePrompt,
                onValueChange = vm::setGroupNudgePrompt,
                label = "group_nudge_prompt",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
                        ShellInput(
                value = sampler.impersonationPrompt,
                onValueChange = vm::setImpersonationPrompt,
                label = "impersonation_prompt（冒充模式注入提示词）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = sampler.assistantImpersonation,
                onValueChange = vm::setAssistantImpersonation,
                label = "assistant_impersonation（Claude 冒充模式预填）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            CollapsibleSection("连接高级（Advanced：代理/custom/azure/vertex/nanogpt/模型排序）") {
            // ---- 官方 oai_settings 连接类字段（settingsToUpdate isConnection=true） ----
            Text("连接高级设置（官方连接字段）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            SwitchRow("bypass_status_check（跳过状态检查）", bypassStatusCheck, vm::setBypassStatusCheck)
            // 官方 openai_show_external_models 只在 OpenAI 面板；其余源不显示
            if (spec.id == "openai") {
                SwitchRow("show_external_models（显示外部模型）", showExternalModels, vm::setShowExternalModels)
            }
            // 官方 #model_sorting_form 只对 openrouter/chutes/electronhub/nanogpt/aimlapi 显示
            if (spec.id in setOf("openrouter", "chutes", "electronhub", "nanogpt", "aimlapi")) {
                SwitchRow("group_models（按提供商分组）", groupModels, vm::setGroupModels)
                Text("sort_models（模型排序）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("alphabetically", "pricing.prompt", "pricing.completion", "context_length").forEach { value ->
                        FilterChip(
                            selected = sortModels == value,
                            onClick = { vm.setSortModels(value) },
                            label = { Text(value) },
                        )
                    }
                }
            }
            IntRow("tool_call_recurse_limit（工具递归上限，官方默认 5）", sampler.toolCallRecurseLimit.toString()) { v ->
                vm.setToolCallRecurseLimit(v.toIntOrNull() ?: 5)
            }
            ShellInput(
                value = reverseProxy,
                onValueChange = vm::setReverseProxy,
                label = "reverse_proxy（官方代理地址）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = proxyPassword,
                onValueChange = vm::setProxyPassword,
                label = "proxy_password（代理 Key）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            // 官方 Reverse Proxy 预设列表（openai.js proxies：全局 name/url/password，选中即填入）
            var proxies by remember {
                mutableStateOf(com.emberinn.app.data.ProxyPresetStore.list(context))
            }
            var proxySelected by remember { mutableStateOf("") }
            var showProxyNew by remember { mutableStateOf(false) }
            var proxyNewName by remember { mutableStateOf("") }
            Text("代理预设（官方 proxies 列表，全局）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                proxies.forEach { preset ->
                    FilterChip(
                        selected = proxySelected == preset.name,
                        onClick = {
                            proxySelected = preset.name
                            vm.setReverseProxy(preset.url)
                            vm.setProxyPassword(preset.password)
                        },
                        label = { Text(preset.name) },
                    )
                }
                TextButton(onClick = { proxyNewName = ""; showProxyNew = true }) { Text("新建预设") }
                TextButton(
                    enabled = proxySelected.isNotBlank(),
                    onClick = {
                        proxies = proxies.filterNot { it.name == proxySelected }
                        com.emberinn.app.data.ProxyPresetStore.save(context, proxies)
                        proxySelected = ""
                        vm.setReverseProxy("")
                        vm.setProxyPassword("")
                    },
                ) { Text("删除预设") }
            }
            if (showProxyNew) {
                AlertDialog(
                    onDismissRequest = { showProxyNew = false },
                    title = { Text("新建代理预设") },
                    text = {
                        ShellInput(
                            value = proxyNewName,
                            onValueChange = { proxyNewName = it },
                            label = "预设名（必须唯一）",
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val n = proxyNewName.trim()
                            if (n.isNotEmpty() && proxies.none { it.name == n }) {
                                proxies = proxies + com.emberinn.app.data.ProxyPreset(n, reverseProxy, proxyPassword)
                                com.emberinn.app.data.ProxyPresetStore.save(context, proxies)
                                proxySelected = n
                                showProxyNew = false
                            }
                        }) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { showProxyNew = false }) { Text("取消") } },
                )
            }
            if (spec.id == "custom") {
                ShellInput(
                    value = customUrl,
                    onValueChange = vm::setCustomUrl,
                    label = "custom_url（自定义 API 地址）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            ShellInput(
                value = customIncludeBody,
                onValueChange = vm::setCustomIncludeBody,
                label = "custom_include_body（YAML 合并进请求体，仅 custom 源）",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = customExcludeBody,
                onValueChange = vm::setCustomExcludeBody,
                label = "custom_exclude_body（YAML 剔除字段，仅 custom 源）",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = customIncludeHeaders,
                onValueChange = vm::setCustomIncludeHeaders,
                label = "custom_include_headers（YAML 请求头，仅 custom 源）",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text("custom_prompt_post_processing（消息合并模式）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("", "merge", "merge_tools", "semi", "semi_tools", "strict", "strict_tools", "single").forEach { value ->
                    FilterChip(
                        selected = customPromptPostProcessing == value,
                        onClick = { vm.setCustomPromptPostProcessing(value) },
                        label = { Text(value.ifBlank { "none" }) },
                    )
                }
            }
            if (spec.id == "azure") {
                ShellInput(
                    value = azureDeploymentName,
                    onValueChange = vm::setAzureDeploymentName,
                    label = "azure_deployment_name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                ShellInput(
                    value = azureOpenaiModel,
                    onValueChange = vm::setAzureOpenaiModel,
                    label = "azure_openai_model",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (spec.id == "vertexai") {
                Text("vertexai_auth_mode（认证方式）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("express" to "Express（API Key）", "full" to "Full（服务账号）").forEach { (value, label) ->
                        FilterChip(
                            selected = vertexaiAuthMode == value,
                            onClick = { vm.setVertexaiAuthMode(value) },
                            label = { Text(label) },
                        )
                    }
                }
                if (vertexaiAuthMode == "express") {
                    ShellInput(
                        value = vertexaiExpressProjectId,
                        onValueChange = vm::setVertexaiExpressProjectId,
                        label = "vertexai_express_project_id（非 us-central1 时必填）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    var saDraft by remember(vertexaiServiceAccountJson) { mutableStateOf(vertexaiServiceAccountJson) }
                    var saStatus by remember { mutableStateOf<String?>(null) }
                    Text(
                        "服务账号 JSON（官方要求 type/project_id/private_key/client_email/client_id，type=service_account）",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ShellInput(
                        value = saDraft,
                        onValueChange = { saDraft = it; saStatus = null },
                        label = "Service Account JSON",
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            saStatus = vm.setVertexaiServiceAccountJson(saDraft)
                        }) { Text("校验并保存") }
                        TextButton(onClick = {
                            saDraft = ""
                            vm.clearVertexaiServiceAccount()
                            saStatus = null
                        }) { Text("清除") }
                    }
                    if (vertexaiServiceAccountJson.isNotBlank() && saStatus == null) {
                        Text(
                            "已保存服务账号",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmberTheme.colors.accent,
                        )
                    }
                    saStatus?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = EmberTheme.colors.danger)
                    }
                }
            }
            if (spec.id == "nanogpt") {
                ShellInput(
                    value = nanogptProvider,
                    onValueChange = vm::setNanogptProvider,
                    label = "nanogpt_provider",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SwitchRow("nanogpt_payg_override", nanogptPaygOverride, vm::setNanogptPaygOverride)
            }

            }
            // 协议专属采样参数（对照官方 textgen/novel/kobold 面板）
            when (spec.protocol) {
                "textgenerationwebui" -> ProtocolSamplerEditors.TextGenEditor(context)
                "novel" -> ProtocolSamplerEditors.NovelEditor(context)
                "kobold" -> ProtocolSamplerEditors.KoboldEditor(context)
                else -> Unit
            }
            }
            CollapsibleSection("上下文与连接测试", initiallyExpanded = true) {
            EmberTextField(
                value = contextWindow.toString(),
                onValueChange = vm::setContextWindow,
                label = { Text("上下文上限（tokens）") },
                supportingText = { Text("官方默认 4095（openai_max_context），不随模型自动变化。") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("连接失败") || it.contains("不对")) {
                        EmberTheme.colors.danger
                    } else {
                        EmberTheme.colors.accent
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = vm::testConnection,
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
                Button(
                    onClick = {
                        vm.save()
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存")
                }
            }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    }

    if (showModelSheet) {
        ModelPickerSheet(vm = vm, onDismiss = { showModelSheet = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${existing?.name?.ifBlank { spec.displayName } ?: spec.displayName}」？") },
            text = { Text("删除后需要重新填写 Key 才能使用。") },
            confirmButton = {
                TextButton(onClick = {
                    existing?.let { vm.deleteProfile(it.id) }
                    confirmDelete = false
                    onBack()
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ModelPickerSheet(vm: ProviderViewModel, onDismiss: () -> Unit) {
    val models by vm.models.collectAsState()
    val selected by vm.selectedModel.collectAsState()
    val testing by vm.testing.collectAsState()
    val sort by vm.sortModels.collectAsState()
    val group by vm.groupModels.collectAsState()
    val providerId by vm.providerId.collectAsState()
    val showExternal by vm.showExternalModels.collectAsState()
    val spec = remember(providerId) { vm.providers.firstOrNull { it.id == providerId } }
    var query by remember { mutableStateOf("") }
    // 官方 openai.js：#openai_external_category 只在 show_external_models 开启时显示状态检查拉到的模型；
    // 关闭时 openai 源只显示内置默认模型列表。App 等价：openai 且关闭 → 过滤到 defaultModels。
    val visible = remember(models, providerId, showExternal, spec) {
        if (providerId == "openai" && !showExternal) {
            models.filter { it.id in (spec?.defaultModels ?: emptyList()) }
        } else {
            models
        }
    }
    // 官方 openai.js：sortModelsBy(sort_models) + groupModelsByVendor（元数据排序 1:1 差分移植）
    // items = (id, 显示名, isHeader)；显示名按官方各源 option text（name ?? info.name ?? id）
    val items: List<Triple<String, String, Boolean>> = remember(visible, query, sort, group, providerId) {
        val base = visible.filter {
            query.isBlank() ||
                it.id.contains(query, ignoreCase = true) ||
                (it.name ?: "").contains(query, ignoreCase = true) ||
                (it.infoName ?: "").contains(query, ignoreCase = true)
        }
        val sorted = com.emberinn.engine.provider.ModelSortEngine.sortModelsBy(base, sort, providerId)
        if (group) {
            com.emberinn.engine.provider.ModelSortEngine.groupModelsByVendor(sorted, providerId)
                .flatMap { (vendor, list) ->
                    listOf(Triple("", vendor, true)) + list.map { Triple(it.id, it.name ?: it.infoName ?: it.id, false) }
                }
        } else {
            sorted.map { Triple(it.id, it.name ?: it.infoName ?: it.id, false) }
        }
    }

    EmberBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium)
            EmberTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索模型") },
                leadingIcon = { Icon(FaIcons.MagnifyingGlass, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 4.dp)) {
                itemsIndexed(items, key = { i, _ -> i }) { _, (modelId, label, isHeader) ->
                    if (isHeader) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = EmberTheme.colors.accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        return@itemsIndexed
                    }
                    val isSel = modelId == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSel) EmberTheme.colors.surface.copy(alpha = 0.45f) else Color.Transparent,
                            )
                            .clickable {
                                vm.selectModel(modelId)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSel) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSel) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(EmberTheme.colors.accent),
                            ) {
                                Text("✓", style = MaterialTheme.typography.labelMedium, color = EmberTheme.colors.ink)
                            }
                        }
                    }
                }
            }
            if (models.isEmpty()) {
                if (testing) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        repeat(5) {
                            EmberSkeletonBox(
                                modifier = Modifier.fillMaxWidth().height(36.dp).padding(vertical = 6.dp),
                            )
                        }
                    }
                } else {
                    EmptyState(
                        title = "还没有模型列表",
                        body = "点「测试连接」拉取该服务商的模型列表",
                        compact = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        // 返回按钮在左上角，但留足上下间距
        IconButton(onClick = onBack) {
            Icon(FaIcons.ArrowLeft, contentDescription = "返回")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmberTheme.colors.inkMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}


@Composable
private fun DecimalRow(label: String, value: String, onChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun IntRow(label: String, value: String, onChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onChange = onChange)
    }
}

/** 官方 API 面板的分区/抽屉：标题行点击折叠（App Compose 等价物）。 */
@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(top = 18.dp, bottom = 2.dp),
    ) {
        Text(
            if (expanded) "▼" else "▶",
            style = MaterialTheme.typography.labelMedium,
            color = EmberTheme.colors.lineStrong,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = EmberTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
    }
    if (expanded) {
        content()
    }
}

/** 官方 OpenAI 面板 main/nsfw/jailbreak 快捷编辑（PromptManager serviceSettings.prompts 同名更新）。 */
@Composable
private fun PromptQuickEdit(context: android.content.Context, identifier: String, label: String) {
    val all = remember(identifier) { com.emberinn.app.data.PromptManagerPrefs.prompts(context) }
    val existing = all.firstOrNull { it.identifier == identifier }
    var text by remember(identifier) {
        mutableStateOf(existing?.content.orEmpty())
    }
    EmberTextField(
        value = text,
        onValueChange = { new ->
            text = new
            val current = com.emberinn.app.data.PromptManagerPrefs.prompts(context)
            val found = current.firstOrNull { it.identifier == identifier }
            val updated = if (found != null) found.copy(content = new) else com.emberinn.engine.prompt.PromptItem(
                identifier = identifier,
                name = label,
                content = new,
                role = "system",
                systemPrompt = true,
            )
            com.emberinn.app.data.PromptManagerPrefs.savePrompts(
                context,
                if (found != null) current.map { if (it.identifier == identifier) updated else it } else current + updated,
            )
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
