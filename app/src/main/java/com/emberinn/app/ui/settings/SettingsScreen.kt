package com.emberinn.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 设置入口：主页分组 + 提供商列表/详情（参照命理2，底层仍走酒馆 1:1 引擎）。 */
@Composable
fun SettingsScreen(vm: ProviderViewModel = viewModel()) {
    var showProviders by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    when {
        detailId != null -> ProviderDetailScreen(
            vm = vm,
            providerId = detailId!!,
            onBack = { detailId = null },
        )
        showProviders -> ProviderListScreen(
            vm = vm,
            onOpenDetail = { detailId = it },
            onBack = { showProviders = false },
        )
        else -> SettingsHome(vm, onOpenProviders = { showProviders = true })
    }
}

private data class SettingRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null,
)

private data class SettingGroup(
    val title: String,
    val rows: List<SettingRow>,
)

@Composable
private fun SettingsHome(vm: ProviderViewModel, onOpenProviders: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val activeProfile = profiles.firstOrNull { it.id == activeId }
    val activeSummary = activeProfile?.let { p ->
        val spec = vm.providers.firstOrNull { it.id == p.providerId }
        buildString {
            append(spec?.displayName ?: p.providerId)
            if (p.model.isNotBlank()) append(" · ").append(p.model)
        }
    } ?: "未配置"

    val groups = listOf(
        SettingGroup(
            "外观与主题",
            listOf(SettingRow("主题模式", "跟随系统", Icons.Filled.Star)),
        ),
        SettingGroup(
            "提供商与模型",
            listOf(
                SettingRow("提供商与模型", activeSummary, Icons.Filled.Settings, onOpenProviders),
                SettingRow("默认采样参数", "开发中", Icons.Filled.Build),
            ),
        ),
        SettingGroup(
            "语音",
            listOf(SettingRow("语音输入与朗读", "开发中", Icons.Filled.Notifications)),
        ),
        SettingGroup(
            "服务",
            listOf(SettingRow("翻译 · 图像 · 向量", "开发中", Icons.Filled.Face)),
        ),
        SettingGroup(
            "数据与隐私",
            listOf(SettingRow("备份与导出", "开发中", Icons.Filled.Lock)),
        ),
        SettingGroup(
            "关于",
            listOf(
                SettingRow("版本", "0.1.0", Icons.Filled.Info),
                SettingRow(
                    "开源仓库",
                    "github.com/heikeyangle-code/ember-inn",
                    Icons.Filled.Share,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/heikeyangle-code/ember-inn"))
                            )
                        }
                    },
                ),
            ),
        ),
    )

    val visible = remember(groups, query) {
        val q = query.trim()
        groups.mapNotNull { group ->
            val rows = if (q.isBlank()) {
                group.rows
            } else {
                group.rows.filter {
                    it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
                }
            }
            if (rows.isNotEmpty()) group.copy(rows = rows) else null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索设置") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        items(visible, key = { it.title }) { group ->
            SettingGroupCard(group)
        }
    }
}

@Composable
private fun SettingGroupCard(group: SettingGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            group.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRowView(row)
            }
        }
    }
}

@Composable
private fun SettingRowView(row: SettingRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.onClick != null, onClick = { row.onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            row.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.onClick != null) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "开发中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
