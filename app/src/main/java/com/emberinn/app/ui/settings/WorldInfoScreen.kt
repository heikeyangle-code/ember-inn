package com.emberinn.app.ui.settings


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.WorldStore
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.home.WorldEntryEditorSheet
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.engine.worldinfo.WorldInfoSettings

/** 世界书扫描设置（对齐官方 World Info 面板；App 聊天扫描用同一份配置）。 */
@Composable
fun WorldInfoScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
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
    var overflowAlert by remember { mutableStateOf(WorldInfoPrefs.overflowAlert(context)) }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    // 官方 world_import_dialog：导入前确认（同名覆盖提示）
    var pendingImport by remember { mutableStateOf<Pair<String, String>?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val displayName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else "world.json"
                } ?: "world.json"
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                if (text.isNotBlank()) pendingImport = displayName to text
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = exportTarget
        if (uri != null && name != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(worldStore.export(name)?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
                }
            }
            exportTarget = null
        }
    }
    fun save() = WorldInfoPrefs.save(context, settings)

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "世界书", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            GroupLabel("扫描设置")
            Text(
                "字段对齐官方 World Info 面板；作用于角色卡内嵌世界书的聊天扫描。",
                color = c.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
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
            ToggleRow("预算溢出提示（overflow_alert）", overflowAlert) { overflowAlert = it; WorldInfoPrefs.saveOverflowAlert(context, it) }
            Text(
                "高级：分组评分、时间效果、角色过滤等字段由角色卡条目自身控制。改动立即保存，下次发送消息生效。",
                color = c.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            GroupLabel("外置世界（worlds/*.json）")
            Text(
                "官方双轨：内嵌卡书（角色详情页）+ 外置世界文件。角色卡用 data.extensions.world 关联（详情页），聊天 metadata.world_info 指定，下方勾选「全局」的世界始终生效。",
                color = c.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                ShellInput(
                    value = newWorldName,
                    onValueChange = { newWorldName = it },
                    label = "新建世界名",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ShellActionButton("新建") {
                    val name = newWorldName.trim()
                    if (name.isNotEmpty()) {
                        worldStore.create(name)
                        worlds = worldStore.list()
                        newWorldName = ""
                    }
                }
                Spacer(Modifier.width(8.dp))
                ShellActionButton("导入") { importLauncher.launch("application/json") }
            }
            val currentEditing = editingWorld
            if (currentEditing != null) {
                val w = worlds.firstOrNull { it.name == currentEditing }
                if (w != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(
                            "← 返回世界列表",
                            color = c.accent,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { editingWorld = null; editingEntryIdx = null; addingEntry = false }
                                .padding(4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text("${w.displayName}", color = c.ink, fontSize = 14.sp)
                    }
                    if (editingDrafts.isEmpty()) {
                        Text(
                            "没有条目。点下方新增，字段与内嵌世界书编辑器完全一致（官方全字段）。",
                            color = c.inkMute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    editingDrafts.forEachIndexed { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                e.keys.ifBlank { "（无触发词）" },
                                color = c.ink,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f).clickable { editingEntryIdx = i },
                            )
                            Icon(
                                FaIcons.Pencil,
                                contentDescription = "编辑条目",
                                tint = c.inkMute,
                                modifier = Modifier.size(17.dp).clickable { editingEntryIdx = i }.padding(2.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                FaIcons.TrashCan,
                                contentDescription = "删除条目",
                                tint = c.danger,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable {
                                        editingDrafts = editingDrafts.filterIndexed { j, _ -> j != i }
                                        worldStore.saveDrafts(currentEditing, w.displayName, editingDrafts)
                                        worlds = worldStore.list()
                                    }
                                    .padding(2.dp),
                            )
                        }
                    }
                    ShellActionButton("＋ 新增条目（官方全字段）", modifier = Modifier.padding(top = 8.dp)) { addingEntry = true }
                }
            } else {
                worlds.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        ShellChip("全局", selected = w.name in globalSelect) {
                            globalSelect = if (w.name in globalSelect) globalSelect - w.name else globalSelect + w.name
                            WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("${w.displayName}（${w.entryCount} 条）", color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(
                            FaIcons.Pencil, contentDescription = "编辑世界条目", tint = c.inkMute,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    editingWorld = w.name
                                    editingDrafts = worldStore.drafts(w.name)
                                }
                                .padding(2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            FaIcons.FileLines, contentDescription = "导出世界", tint = c.inkMute,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    exportTarget = w.name
                                    exportLauncher.launch("${w.name}.json")
                                }
                                .padding(2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            FaIcons.TrashCan, contentDescription = "删除世界", tint = c.danger,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    worldStore.delete(w.name)
                                    worlds = worldStore.list()
                                    globalSelect = globalSelect - w.name
                                    WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                                }
                                .padding(2.dp),
                        )
                    }
                }
            }
            GroupLabel("插入策略")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                listOf("角色优先(1)" to 1, "全局优先(2)" to 2, "均匀(0)" to 0).forEach { (label, v) ->
                    ShellChip(label, selected = strategy == v) {
                        strategy = v
                        WorldInfoPrefs.saveInsertionStrategy(context, v)
                    }
                    Spacer(Modifier.width(7.dp))
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
    }

    val editing = editingWorld
    pendingImport?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入世界书") },
            text = { Text("确定导入「$name」？同名世界将被覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    if (worldStore.importWorld(name, text)) worlds = worldStore.list()
                    pendingImport = null
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } },
        )
    }
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
    ShellInput(
        value = value,
        onValueChange = onChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onChange = onChange)
    }
}
