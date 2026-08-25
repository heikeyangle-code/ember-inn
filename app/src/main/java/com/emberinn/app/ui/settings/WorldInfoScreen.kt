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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.WorldStore
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.AccordionGroup
import com.emberinn.app.ui.design.components.AvatarCircle
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.components.StatBadges
import com.emberinn.app.ui.home.WorldEntryEditorSheet
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.engine.worldinfo.WorldInfoSettings
import java.io.File

/**
 * 世界书（Power Space · 内容驱动）：世界卡列表（搜索/新建/导入）→ 世界主页
 * （关联角色 + 条目预览 + 官方全字段条目编辑）→ 扫描设置折叠组（Progressive Disclosure）。
 * 官方双轨保留：内嵌卡书在角色编辑器；外置世界在此管理（全局勾选 / 导入导出 / 插入策略）。
 */
@Composable
fun WorldInfoScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    val context = LocalContext.current
    var settings by remember { mutableStateOf(WorldInfoPrefs.read(context)) }
    var includeNames by remember { mutableStateOf(WorldInfoPrefs.includeNames(context)) }
    val worldStore = remember { WorldStore(context) }
    var worlds by remember { mutableStateOf(worldStore.list()) }
    var globalSelect by remember { mutableStateOf(WorldInfoPrefs.globalSelect(context).toSet()) }
    var strategy by remember { mutableStateOf(WorldInfoPrefs.insertionStrategy(context)) }
    var newWorldName by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var viewingWorld by remember { mutableStateOf<String?>(null) }
    var editingDrafts by remember { mutableStateOf<List<WorldEntryDraft>>(emptyList()) }
    var editingEntryIdx by remember { mutableStateOf<Int?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var overflowAlert by remember { mutableStateOf(WorldInfoPrefs.overflowAlert(context)) }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    var deleteTargetWorld by remember { mutableStateOf<WorldStore.WorldFile?>(null) }
    var worldMenu by remember { mutableStateOf<WorldStore.WorldFile?>(null) }
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
    fun refreshWorlds() { worlds = worldStore.list() }

    // 搜索过滤：世界名 / 显示名 / 条目触发词与内容
    val shownWorlds = remember(worlds, search, editingDrafts) {
        val q = search.trim()
        if (q.isEmpty()) worlds
        else worlds.filter { w ->
            w.name.contains(q, true) || w.displayName.contains(q, true) ||
                (viewingWorld == w.name && editingDrafts.any { e ->
                    e.keys.contains(q, true) || e.content.contains(q, true) || e.comment.contains(q, true)
                })
        }
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        val viewing = viewingWorld
        SettingsTopBar(
            title = if (viewing == null) "世界书" else (worlds.firstOrNull { it.name == viewing }?.displayName ?: "世界书"),
            subtitle = if (viewing == null) "外置世界 · 全局生效 · 扫描设置" else "世界主页 · ${worlds.firstOrNull { it.name == viewing }?.entryCount ?: 0} 条条目",
            onBack = { if (viewing == null) onBack() else viewingWorld = null },
            sky = settingsSky,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (viewing == null) {
                // ---------------- 世界列表态 ----------------
                ShellInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "搜索世界 / 触发词",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
                            refreshWorlds()
                            newWorldName = ""
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ShellActionButton("导入") { importLauncher.launch("application/json") }
                }
                if (shownWorlds.isEmpty()) {
                    Text(
                        if (worlds.isEmpty()) "还没有外置世界。新建一个，或导入官方 worlds/*.json。"
                        else "没有匹配「${search.trim()}」的世界。",
                        color = c.inkMute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                shownWorlds.forEach { w ->
                    WorldCard(
                        world = w,
                        isGlobal = w.name in globalSelect,
                        onClick = {
                            viewingWorld = w.name
                            editingDrafts = worldStore.drafts(w.name)
                        },
                        onMenu = { worldMenu = w },
                    )
                }

                // ---------------- 扫描设置折叠组（Progressive Disclosure） ----------------
                AccordionGroup(
                    title = "扫描设置",
                    summary = "深度 ${settings.depth} · 预算 ${settings.budgetPercent}% · ${if (settings.recursive) "递归开" else "递归根"}",
                ) {
                    Column(Modifier.padding(top = 6.dp)) {
                        Text(
                            "字段对齐官方 World Info 面板；作用于角色卡内嵌世界书的聊天扫描。改动立即保存，下次发送生效。",
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
                            "高级：分组评分、时间效果、角色过滤等字段由角色卡条目自身控制。",
                            color = c.inkMute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                        )
                    }
                }
                AccordionGroup(
                    title = "插入策略",
                    summary = listOf("均匀(0)" to 0, "角色优先(1)" to 1, "全局优先(2)" to 2).firstOrNull { it.second == strategy }?.first,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        listOf("角色优先(1)" to 1, "全局优先(2)" to 2, "均匀(0)" to 0).forEach { (label, v) ->
                            ShellChip(label, selected = strategy == v) {
                                strategy = v
                                WorldInfoPrefs.saveInsertionStrategy(context, v)
                            }
                            Spacer(Modifier.width(7.dp))
                        }
                    }
                    Text(
                        "官方 world_info_insertion_strategy：条目在世界信息块内的排序依据。",
                        color = c.inkMute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                Spacer(Modifier.height(120.dp))
            } else {
                // ---------------- 世界主页态 ----------------
                val w = worlds.firstOrNull { it.name == viewing }
                if (w == null) {
                    viewingWorld = null
                } else {
                    // 关联角色：官方 data.extensions.world 指向此世界的角色卡
                    val linked = remember(viewing, worlds) {
                        runCatching {
                            CharacterStore(context).list().filter { ch ->
                                val link = CharacterCardEdit.readWorldLink(ch.rawJson)
                                link == w.name || link == w.displayName
                            }
                        }.getOrDefault(emptyList())
                    }
                    StatBadges(
                        buildList {
                            add("${w.entryCount}" to "条条目")
                            add("${linked.size}" to "个关联角色")
                            if (w.name in globalSelect) add("全局生效" to "")
                        },
                    )
                    if (linked.isNotEmpty()) {
                        GroupLabel("关联角色")
                        Text(
                            "这些角色的卡片通过 data.extensions.world 关联本世界，聊天时自动扫描。",
                            color = c.inkMute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                        linked.take(6).forEach { ch ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                            ) {
                                AvatarCircle(ch.avatarPath?.takeIf { File(it).exists() }, ch.name, 36.dp)
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text(ch.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    ch.description.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = c.inkMute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    GroupLabel("条目")
                    if (editingDrafts.isEmpty()) {
                        Text(
                            "没有条目。点下方新增，字段与内嵌世界书编辑器完全一致（官方全字段）。",
                            color = c.inkMute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                    editingDrafts.forEachIndexed { i, e ->
                        WorldEntryRow(
                            draft = e,
                            onClick = { editingEntryIdx = i },
                            onDelete = {
                                editingDrafts = editingDrafts.filterIndexed { j, _ -> j != i }
                                worldStore.saveDrafts(viewing, w.displayName, editingDrafts)
                                refreshWorlds()
                            },
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        ShellActionButton("＋ 新增条目（官方全字段）", modifier = Modifier.weight(1f)) { addingEntry = true }
                        Spacer(Modifier.width(8.dp))
                        ShellActionButton(if (w.name in globalSelect) "取消全局" else "设为全局") {
                            globalSelect = if (w.name in globalSelect) globalSelect - w.name else globalSelect + w.name
                            WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ShellActionButton("导出", modifier = Modifier.weight(1f)) {
                            exportTarget = w.name
                            exportLauncher.launch("${w.name}.json")
                        }
                        Spacer(Modifier.width(8.dp))
                        ShellActionButton("删除世界", modifier = Modifier.weight(1f)) { deleteTargetWorld = w }
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
    }

    val editing = viewingWorld
    pendingImport?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入世界书") },
            text = { Text("确定导入「$name」？同名世界将被覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    if (worldStore.importWorld(name, text)) refreshWorlds()
                    pendingImport = null
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } },
        )
    }
    deleteTargetWorld?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTargetWorld = null },
            title = { Text("删除「${target.displayName}」") },
            text = { Text("世界文件与全部 ${target.entryCount} 条条目将被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    worldStore.delete(target.name)
                    refreshWorlds()
                    if (viewingWorld == target.name) viewingWorld = null
                    globalSelect = globalSelect - target.name
                    WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                    deleteTargetWorld = null
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTargetWorld = null }) { Text("取消") } },
        )
    }
    worldMenu?.let { target ->
        ShellSheet(onDismiss = { worldMenu = null }, title = target.displayName) {
            SheetRow(icon = FaIcons.Globe, label = if (target.name in globalSelect) "取消全局生效" else "全局生效", subtitle = "官方 globalSelect：所有聊天始终扫描") {
                globalSelect = if (target.name in globalSelect) globalSelect - target.name else globalSelect + target.name
                WorldInfoPrefs.saveGlobalSelect(context, globalSelect.toList())
                worldMenu = null
            }
            SheetRow(icon = FaIcons.FileLines, label = "导出世界", subtitle = "${target.entryCount} 条条目 → JSON") {
                exportTarget = target.name
                worldMenu = null
                exportLauncher.launch("${target.name}.json")
            }
            SheetRow(icon = FaIcons.TrashCan, label = "删除世界", subtitle = "删除 ${target.name}.json") {
                deleteTargetWorld = target
                worldMenu = null
            }
        }
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
                    refreshWorlds()
                    addingEntry = false
                    editingEntryIdx = null
                },
                onDelete = {
                    val idx = editingEntryIdx
                    if (idx != null && idx in editingDrafts.indices) {
                        editingDrafts = editingDrafts.filterIndexed { i, _ -> i != idx }
                        current?.let { worldStore.saveDrafts(editing, it.displayName, editingDrafts) }
                        refreshWorlds()
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

/** 世界卡：名称 + 条目数 + 全局徽标，点击进世界主页，更多进动作面板。 */
@Composable
private fun WorldCard(
    world: WorldStore.WorldFile,
    isGlobal: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2),
        ) {
            Icon(FaIcons.Globe, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                world.displayName,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${world.entryCount} 条条目${if (isGlobal) " · 全局生效" else ""}",
                color = c.inkMute,
                fontSize = 11.sp,
            )
        }
        if (isGlobal) {
            Text("全局", color = c.accent, fontSize = 10.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(end = 8.dp))
        }
        Icon(
            FaIcons.EllipsisVertical,
            contentDescription = "世界操作",
            tint = c.ink.copy(alpha = 0.34f),
            modifier = Modifier.size(18.dp).clickable(onClick = onMenu).padding(2.dp),
        )
    }
}

/** 条目行：触发词 + 内容预览 + 状态徽标，点击进官方全字段编辑器。 */
@Composable
private fun WorldEntryRow(
    draft: WorldEntryDraft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.keys.ifBlank { draft.comment.ifBlank { "（无触发词）" } },
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!draft.enabled) {
                    Spacer(Modifier.width(8.dp))
                    Text("禁用", color = c.inkMute, fontSize = 10.sp)
                }
                if (draft.constant) {
                    Spacer(Modifier.width(8.dp))
                    Text("常量", color = c.accent, fontSize = 10.sp)
                }
            }
            if (draft.content.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    draft.content,
                    color = c.inkMute,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            FaIcons.Pencil,
            contentDescription = "编辑条目",
            tint = c.inkMute,
            modifier = Modifier.size(17.dp).clickable(onClick = onClick).padding(2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            FaIcons.TrashCan,
            contentDescription = "删除条目",
            tint = c.danger,
            modifier = Modifier.size(17.dp).clickable(onClick = onDelete).padding(2.dp),
        )
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
