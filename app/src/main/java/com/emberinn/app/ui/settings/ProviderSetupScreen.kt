package com.emberinn.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.ProviderSpec

/** 提供商三步配置（README：选供应商 → 粘贴 API Key → 完成）。 */
@Composable
fun ProviderSetupScreen(vm: ProviderSetupViewModel = viewModel()) {
    val step by vm.step.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("提供商与模型", style = MaterialTheme.typography.headlineSmall)
        Text(
            "选一个提供商，填 Key，选模型，就能聊天。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val message by vm.message.collectAsState()
        message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when (step) {
            1 -> ProviderStep(vm)
            2 -> KeyStep(vm)
            3 -> ModelStep(vm)
            else -> DoneStep(vm)
        }
    }
}

@Composable
private fun ProviderStep(vm: ProviderSetupViewModel) {
    var query by remember { mutableStateOf("") }
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val filtered = remember(vm.providers, query) {
        vm.providers.filter {
            query.isBlank() ||
                it.displayName.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        if (profiles.isNotEmpty()) {
            Text("已保存的连接", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileChip(
                        profile = profile,
                        active = profile.id == activeId,
                        onSwitch = { vm.switchProfile(profile.id) },
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索提供商") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { provider ->
                ProviderCard(provider, onClick = { vm.selectProvider(provider.id) })
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
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onSwitch),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        ) {
            Text(
                profile.name.ifBlank { profile.providerId },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active) {
                Text(" ✓", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "✕",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ProviderCard(provider: ProviderSpec, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(provider.icon.ifBlank { "🔌" }, style = MaterialTheme.typography.headlineMedium)
            Text(
                provider.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                if (provider.requiresKey) "需要 Key" else "免 Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun KeyStep(vm: ProviderSetupViewModel) {
    val provider = vm.provider()
    val apiKey by vm.apiKey.collectAsState()
    val profileName by vm.profileName.collectAsState()
    val region by vm.region.collectAsState()
    val accountId by vm.accountId.collectAsState()
    val baseUrl by vm.baseUrlOverride.collectAsState()
    val apiVersion by vm.apiVersionOverride.collectAsState()
    val advanced by vm.advancedExpanded.collectAsState()
    val testing by vm.testing.collectAsState()

    if (provider == null) {
        Text("请先返回选择提供商。", modifier = Modifier.padding(top = 24.dp))
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(provider.icon.ifBlank { "🔌" }, style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(provider.description, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = profileName,
            onValueChange = vm::setProfileName,
            label = { Text("档案名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = vm::setApiKey,
            label = { Text("API Key") },
            singleLine = true,
            supportingText = { Text("粘贴自动去空格；免 Key 的提供商可留空") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (provider.regionVariants.isNotEmpty()) {
            Text("区域", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                provider.regionVariants.forEach { variant ->
                    FilterChip(
                        selected = region == variant || (region.isBlank() && variant == provider.regionVariants.first()),
                        onClick = { vm.setRegion(variant) },
                        label = { Text(variant) },
                    )
                }
            }
        }
        if (provider.id == "workers-ai") {
            OutlinedTextField(
                value = accountId,
                onValueChange = vm::setAccountId,
                label = { Text("Cloudflare 账户 ID") },
                singleLine = true,
                supportingText = { Text("在 Cloudflare 仪表盘右上角可找到") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        TextButton(onClick = vm::toggleAdvanced, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (advanced) "收起高级设置" else "高级设置")
        }
        AnimatedVisibility(visible = advanced) {
            Column {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = vm::setBaseUrlOverride,
                    label = { Text("接口地址（留空用默认）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiVersion,
                    onValueChange = vm::setApiVersionOverride,
                    label = { Text("API 版本（留空用默认）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = vm::testConnection,
                enabled = !testing,
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("测试连接")
                }
            }
            TextButton(onClick = vm::back) { Text("上一步") }
        }
        Text(
            "测试成功会自动拉取模型列表并选中默认模型；拉不到时用预填模型兜底。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ModelStep(vm: ProviderSetupViewModel) {
    val models by vm.models.collectAsState()
    val selected by vm.selectedModel.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        models.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("第 3 步：选择模型", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索模型") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(filtered, key = { it }) { model ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable { vm.selectModel(model) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            model,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (model == selected) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (models.isEmpty()) {
            Text(
                "没拉到模型列表？可在高级设置里改接口地址，或直接用预填模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(
            onClick = vm::finish,
            enabled = selected.isNotBlank() || models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (selected.isNotBlank()) "完成" else "用默认模型完成")
        }
        TextButton(onClick = vm::back, modifier = Modifier.fillMaxWidth()) { Text("上一步") }
    }
}

@Composable
private fun DoneStep(vm: ProviderSetupViewModel) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text("配置完成 🎉", style = MaterialTheme.typography.titleLarge)
        Text(
            "回到聊天页即可发送真实对话；也可以在这里切换连接档案。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (profiles.isNotEmpty()) {
            Text("连接档案", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            profiles.forEach { profile ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (profile.id == activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clickable { vm.switchProfile(profile.id) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            profile.name.ifBlank { profile.providerId },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (profile.id == activeId) {
                            Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Button(
            onClick = vm::restart,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text("再添加一个")
        }
    }
}
