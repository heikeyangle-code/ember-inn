package com.emberinn.app.ui.settings


import androidx.compose.foundation.clickable
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.data.WorldStore
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.app.ui.home.WorldEntryEditorSheet
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import com.emberinn.app.ui.icons.PhosphorIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emberinn.engine.worldinfo.WorldInfoSettings

/** 世界书扫描设置（对齐官方 World Info 面板；App 聊天扫描用同一份配置）。 */
@Composable
fun WorldInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(WorldInfoPrefs.read(context)) }
    var includeNames by remember { mutableStateOf(WorldInfoPrefs.includeNames(context)) }
    val worldStore = remember { WorldStore(context) }
    var worlds by remember { mutableStateOf(worldStore.list()) }
    var globalSelect by remember { mutableStateOf(WorldInfoPrefs.globalSelect(context).toSet()) }
    var strategy by remember { mutableStateOf(WorldInfoPrefs.insertionStrategy(context)) }
    var newWorldName by remember { mutableStateOf("") }
    var editingWorld by remember { mutableStateOf<String?>(null) }
    var editingDrafts by remember { mutableStateOf<List<WorldEntryDraft>>(emptyList()) }
    var editingEntryIdx by remember { mutableStateOf<Int?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    fun save() = WorldInfoPrefs.save(context, settings)

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "世界书", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("扫描设置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "字段对齐官方 World Info 面板；作用于角色卡内嵌世界书的聊天扫描。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            NumberRow("深度（depth）", settings.depth.toString()) { v ->
                settings = settings.copy(depth = v.toIntOrNull() ?: 2); save()
            }
            NumberRow("最少激活数（minActivations）", settings.minActivations.toString()) { v ->
                settings = settings.copy(minActivations = v.toIntOrNull() ?: 0); save()
            }
            NumberRow("最少激活深度上限（minActivationsDepthMax）", settings.minActivationsDepthMax.toString()) { v ->
                settings = settings.copy(minActivationsDepthMax = v.toIntOrNull() ?: 0); save()
            }
            NumberRow("预算百分比（%）", settings.budgetPercent.toString()) { v ->
                settings = settings.copy(budgetPercent = (v.toIntOrNull() ?: 25).coerceIn(1, 100)); save()
            }
            NumberRow("预算上限（budgetCap，0=不限）", settings.budgetCap.toString()) { v ->
                settings = settings.copy(budgetCap = v.toIntOrNull() ?: 0); save()
            }
            NumberRow("最大递归步数（0=不限制）", settings.maxRecursionSteps.toString()) { v ->
                settings = settings.copy(maxRecursionSteps = v.toIntOrNull() ?: 0); save()
            }
            ToggleRow("递归扫描（recursive）", settings.recursive) { settings = settings.copy(recursive = it); save() }
            ToggleRow("区分大小写（caseSensitive）", settings.caseSensitive) { settings = settings.copy(caseSensitive = it); save() }
            ToggleRow("整词匹配（matchWholeWords）", settings.matchWholeWords) { settings = settings.copy(matchWholeWords = it); save() }
            ToggleRow("分组评分（useGroupScoring）", settings.useGroupScoring) { settings = settings.copy(useGroupScoring = it); save() }
            ToggleRow("扫描带名字（include_names）", includeNames) { includeNames = it; WorldInfoPrefs.saveIncludeNames(context, it) }
            Text(
                "高级：分组评分、时间效果、角色过滤等字段由角色卡条目自身控制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "改动立即保存，下次发送消息生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("外置世界（worlds/*.json）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
            Text(
                "官方双轨：内嵌卡书（角色详情页）+ 外置世界文件。角色卡用 data.extensions.world 关联（详情页），聊天 metadata.world_info 指定，下方“全局”勾选的世界始终生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                EmberTextField(
                    value = newWorldName,
                    onValueChange = { newWorldName = it },
                    label = { Text("新建世界名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val name = newWorldName.trim()
                    if (name.isNotEmpty()) {
                        worldStore.create(name)
                        worlds = worldStore.list()
                        newWorldName = ""
                    }
                }) { Text("新建") }
            }
            val currentEditing = editingWorld
            if (currentEditing != null) {
                val w = worlds.firstOrNull { it.name == currentEditing }
                if (w != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        TextButton(onClick = { editingWorld = null; editingEntryIdx = null; addingEntry = false }) { Text("← 返回世界列表") }
                        Spacer(Modifier.weight(1f))
                        Text("${w.displayName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (editingDrafts.isEmpty()) {
                        Text(
                            "没有条目。点下方新增，字段与内嵌世界书编辑器完全一致（官方全字段）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        )
                    }
                    editingDrafts.forEachIndexed { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                e.keys.ifBlank { "（无触发词）" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).clickable { editingEntryIdx = i },
                            )
                            IconButton(onClick = { editingEntryIdx = i }) {
                                Icon(PhosphorIcons.Edit, contentDescription = "编辑条目")
                            }
                            IconButton(onClick = {
                                editingDrafts = editingDrafts.filterIndexed { j, _ -> j != i }
                                worldStore.saveDrafts(currentEditing, w.displayName, editingDrafts)
                                worlds = worldStore.list()
                            }) {
                                Icon(PhosphorIcons.Delete, contentDescription = "删除条目", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Button(onClick = { addingEntry = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("＋ 新增条目（官方全字段）")
                    }
                }
            } else {
                worlds.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        FilterChip(
                            selected = w.name in globalSelect,
                            onClick = {
                                globalSelect = if (w.name in globalSelect) globalSelect - w.name else globalSelect + w.name
                                WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                            },
                            label = { Text("全局") },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${w.displayName}（${w.entryCount} 条）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            editingWorld = w.name
                            editingDrafts = worldStore.drafts(w.name)
                        }) {
                            Icon(PhosphorIcons.Edit, contentDescription = "编辑世界条目")
                        }
                        IconButton(onClick = {
                            worldStore.delete(w.name)
                            worlds = worldStore.list()
                            globalSelect = globalSelect - w.name
                            WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                        }) {
                            Icon(PhosphorIcons.Delete, contentDescription = "删除世界", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text("插入策略", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("角色优先(1)" to 1, "全局优先(2)" to 2, "均匀(0)" to 0).forEach { (label, v) ->
                    FilterChip(
                        selected = strategy == v,
                        onClick = {
                            strategy = v
                            WorldInfoPrefs.saveInsertionStrategy(context, v)
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    }

    val editing = editingWorld
    if (editing != null && (addingEntry || editingEntryIdx != null)) {
        val current = worlds.firstOrNull { it.name == editing }
        val initial = if (addingEntry) {
            WorldEntryDraft(
                id = editingDrafts.size + 1,
                keys = "",
                content = "",
                comment = "",
                constant = false,
                selective = true,
                enabled = true,
                insertionOrder = 100,
            )
        } else {
            editingDrafts.getOrNull(editingEntryIdx ?: -1)
        }
        if (initial != null) {
            WorldEntryEditorSheet(
                initial = initial,
                isNew = addingEntry,
                onSave = { draft ->
                    val next = if (addingEntry) editingDrafts + draft
                    else editingDrafts.mapIndexed { i, d -> if (i == editingEntryIdx) draft else d }
                    editingDrafts = next
                    current?.let { worldStore.saveDrafts(editing, it.displayName, next) }
                    worlds = worldStore.list()
                    addingEntry = false
                    editingEntryIdx = null
                },
                onDelete = {
                    val idx = editingEntryIdx
                    if (idx != null && idx in editingDrafts.indices) {
                        editingDrafts = editingDrafts.filterIndexed { i, _ -> i != idx }
                        current?.let { worldStore.saveDrafts(editing, it.displayName, editingDrafts) }
                        worlds = worldStore.list()
                    }
                    addingEntry = false
                    editingEntryIdx = null
                },
                onDismiss = {
                    addingEntry = false
                    editingEntryIdx = null
                },
            )
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}
