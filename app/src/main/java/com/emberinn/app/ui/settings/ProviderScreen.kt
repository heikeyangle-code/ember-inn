@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.settings

import com.emberinn.app.ui.icons.PhosphorIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.ProviderIcon
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.ProviderSpec

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "提供商与模型", subtitle = "22 家服务商，点卡片配置", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索提供商") },
            leadingIcon = { Icon(PhosphorIcons.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (profiles.isNotEmpty()) {
            Text(
                "我的连接",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun ProfileChip(
    profile: ConnectionProfile,
    active: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
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
            Text(
                "✕",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 2.dp),
            )
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                        shape = MaterialTheme.shapes.small,
                        color = if (configured) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            if (configured) "已配置" else "未配置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (configured) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val testing by vm.testing.collectAsState()
    val message by vm.message.collectAsState()

    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val existing = profiles.firstOrNull { it.providerId == spec.id }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = spec.displayName,
            onBack = onBack,
            trailing = if (existing != null) {
                {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(PhosphorIcons.Delete, contentDescription = "删除连接")
                    }
                }
            } else null,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderIcon(spec.icon, spec.displayName, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        spec.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = vm::setProfileName,
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            OutlinedTextField(
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
            OutlinedTextField(
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
                OutlinedTextField(
                    value = accountId,
                    onValueChange = vm::setAccountId,
                    label = { Text("账户 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (spec.id == "azure") {
                OutlinedTextField(
                    value = apiVersion,
                    onValueChange = vm::setApiVersion,
                    label = { Text("API 版本") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clickable { showModelSheet = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text("默认模型", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        selectedModel.ifBlank { "未选择" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = maxTokens.toString(),
                onValueChange = vm::setMaxTokens,
                label = { Text("最大回复 tokens") },
                supportingText = { Text("思考型模型会先耗思考额度，太小会导致只有思考没有正文（如 512 常被掐空，建议 8192）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = contextWindow.toString(),
                onValueChange = vm::setContextWindow,
                label = { Text("上下文上限（tokens）") },
                supportingText = { Text("聊天页占比胶囊的分母；默认 8192，建议按模型真实窗口填（如 128000）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("连接失败") || it.contains("不对")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
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
            Spacer(Modifier.size(24.dp))
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
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
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
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        models.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索模型") },
                leadingIcon = { Icon(PhosphorIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 4.dp)) {
                items(filtered, key = { it }) { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.selectModel(model)
                            onDismiss()
                        }.padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Text(
                            model,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (model == selected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (models.isEmpty()) {
                Text(
                    "暂无模型，请先测试连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(PhosphorIcons.ArrowLeft, contentDescription = "返回")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
