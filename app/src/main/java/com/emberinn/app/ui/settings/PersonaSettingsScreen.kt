package com.emberinn.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emberinn.app.data.Persona
import com.emberinn.app.data.PersonaStore
import com.emberinn.app.data.WorldStore
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.icons.FaIcons
import java.io.File

/** 官方 persona_description_positions（下拉值）。 */
private val PERSONA_POSITIONS = listOf(
    0 to "提示词内（In Prompt）",
    2 to "作者注顶部",
    3 to "作者注底部",
    4 to "指定深度",
    9 to "不注入",
)

/**
 * 人设管理（官方 Persona Management 分区 · Power Space）：
 * 人设卡列表 + 完整编辑（名称/描述/位置/深度/角色/头像/人设世界书）
 * + 设为当前/默认 + 复制 + 删除 + 官方格式导入导出。
 * 编辑字段与聊天页人设编辑器完全一致（role/avatar/lorebook 之前无 Power Space 入口，本页补齐）。
 */
@Composable
fun PersonaSettingsScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    val context = LocalContext.current
    val store = remember { PersonaStore(context) }
    val worldStore = remember { WorldStore(context) }
    var personas by remember { mutableStateOf(store.list()) }
    var activeId by remember { mutableStateOf(store.active()?.id ?: "") }
    var defaultId by remember { mutableStateOf(store.default()?.id ?: "") }
    var editTarget by remember { mutableStateOf<Persona?>(null) }
    var editNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Persona?>(null) }
    var menuTarget by remember { mutableStateOf<Persona?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // 官方 power_user.persona_sort_order（personas.js sortPersonas）：按显示名 A-Z / Z-A，跨会话持久化
    var personaSortAsc by rememberSaveable { mutableStateOf(BehaviorPrefs.load(context).personaSortOrder != "desc") }

    fun refresh() {
        personas = store.list()
        activeId = store.active()?.id ?: ""
        defaultId = store.default()?.id ?: ""
    }

    fun togglePersonaSort() {
        personaSortAsc = !personaSortAsc
        val cur = BehaviorPrefs.load(context)
        val order = if (personaSortAsc) "asc" else "desc"
        if (cur.personaSortOrder != order) BehaviorPrefs.save(context, cur.copy(personaSortOrder = order))
    }

    val sortedPersonas = remember(personas, personaSortAsc) {
        if (personaSortAsc) personas.sortedBy { it.name.lowercase() }
        else personas.sortedByDescending { it.name.lowercase() }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }
                message = "已导出人设"
            }.onFailure { message = "导出失败：${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                val result = store.importJson(text)
                message = if (result.ok) {
                    if (result.warnings.isEmpty()) "导入成功" else "导入完成：${result.warnings.joinToString("；")}"
                } else {
                    "导入失败：文件格式无效"
                }
                refresh()
            }.onFailure { message = "导入失败：${it.message}" }
        }
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "人设管理",
                subtitle = "当前人设进入提示词（{{user}} 与描述注入）",
                onBack = onBack,
                sky = settingsSky,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 官方 persona_sort_order：A-Z ⇄ Z-A 切换（chip 即当前值）
                        ShellChip(
                            label = if (personaSortAsc) "A-Z" else "Z-A",
                            selected = false,
                            onClick = { togglePersonaSort() },
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            FaIcons.Plus,
                            contentDescription = "新建人设",
                            tint = c.accent,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { editNew = true; editTarget = null }
                                .padding(2.dp),
                        )
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = settingsPagePadding(),
            ) {
                if (message != null) {
                    item {
                        Text(message!!, color = c.accent, fontSize = EmberTheme.typo.caption.fontSize, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (personas.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有人设", color = c.ink, fontSize = EmberTheme.typo.head.fontSize)
                            Text("点右上角 + 新建；描述支持 {{char}}/{{user}} 宏", color = c.inkMute, fontSize = EmberTheme.typo.caption.fontSize, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                items(sortedPersonas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        isActive = persona.id == activeId,
                        isDefault = persona.id == defaultId,
                        onClick = { store.setActive(persona.id); refresh(); message = "已切换当前人设：${persona.name}" },
                        onMenu = { menuTarget = persona },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        com.emberinn.app.ui.design.components.ShellActionButton(label = "导出官方格式", modifier = Modifier.weight(1f)) { exportLauncher.launch("personas.json") }
                        com.emberinn.app.ui.design.components.ShellActionButton(label = "导入", modifier = Modifier.weight(1f)) { importLauncher.launch(arrayOf("application/json")) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    menuTarget?.let { persona ->
        ShellSheet(onDismiss = { menuTarget = null }, title = persona.name.ifBlank { "人设" }) {
            SheetRow(icon = FaIcons.User, label = "设为当前", subtitle = "当前人设注入提示词（{{user}}）") {
                store.setActive(persona.id)
                refresh()
                menuTarget = null
                message = "已切换当前人设：${persona.name}"
            }
            SheetRow(icon = FaIcons.Star, label = "设为默认", subtitle = "新建聊天时自动使用") {
                store.setDefault(persona.id)
                refresh()
                menuTarget = null
                message = "已设为默认人设"
            }
            SheetRow(icon = FaIcons.Copy, label = "复制人设", subtitle = "副本（不含连接）") {
                val copy = persona.copy(
                    id = "p-" + System.nanoTime().toString(36),
                    name = persona.name + " 副本",
                    connections = emptyList(),
                )
                store.save(personas + copy)
                refresh()
                menuTarget = null
            }
            SheetRow(icon = FaIcons.Pencil, label = "编辑", subtitle = "描述 / 位置 / 深度 / 角色 / 世界书 / 头像") {
                editTarget = persona
                menuTarget = null
            }
            SheetRow(icon = FaIcons.TrashCan, label = "删除", danger = true) {
                deleteTarget = persona
                menuTarget = null
            }
        }
    }

    if (editTarget != null || editNew) {
        PersonaEditDialog(
            initial = editTarget,
            worlds = worldStore.list(),
            onDismiss = { editTarget = null; editNew = false },
            onSave = { p ->
                val next = personas.toMutableList()
                val idx = next.indexOfFirst { it.id == p.id }
                if (idx >= 0) next[idx] = p else next.add(p)
                store.save(next, activeId.ifBlank { p.id }, defaultId.ifBlank { p.id })
                refresh()
                editTarget = null
                editNew = false
                message = "已保存人设"
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除人设") },
            text = { Text("确定删除「${target.name}」？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    store.save(personas.filterNot { it.id == target.id })
                    refresh()
                    deleteTarget = null
                    message = "已删除人设"
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/** 人设卡：头像 + 名称 + 当前/默认徽标 + 标题 + 描述；当前人设以操作面高亮。 */
@Composable
private fun PersonaCard(
    persona: Persona,
    isActive: Boolean,
    isDefault: Boolean,
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
            .background(if (isActive) c.surface2 else c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (persona.avatarPath.isNotBlank() && File(persona.avatarPath).exists()) {
            AsyncImage(
                model = File(persona.avatarPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(c.surfaceSink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    persona.name.take(1).ifBlank { "?" },
                    color = c.ink,
                    fontSize = EmberTheme.typo.title.fontSize,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(persona.name.ifBlank { "未命名" }, color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Text("当前", color = c.accent, fontSize = EmberTheme.typo.micro.fontSize, letterSpacing = 0.8.sp)
                }
                if (isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Text("默认", color = c.inkMute, fontSize = EmberTheme.typo.micro.fontSize, letterSpacing = 0.8.sp)
                }
            }
            if (persona.title.isNotBlank()) {
                Text(persona.title, color = c.accent, fontSize = EmberTheme.typo.caption.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (persona.description.isNotBlank()) {
                Text(persona.description, color = c.inkMute, fontSize = EmberTheme.typo.caption.fontSize, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            }
            if (persona.lorebook.isNotBlank()) {
                Text("世界书：${persona.lorebook}", color = c.ink.copy(alpha = 0.34f), fontSize = EmberTheme.typo.micro.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            FaIcons.EllipsisVertical,
            contentDescription = "更多",
            tint = c.ink.copy(alpha = 0.34f),
            modifier = Modifier.size(18.dp).clickable(onClick = onMenu).padding(2.dp),
        )
    }
}

@Composable
private fun PersonaEditDialog(
    initial: Persona?,
    worlds: List<WorldStore.WorldFile>,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit,
) {
    val context = LocalContext.current
    val original = initial ?: Persona(id = java.util.UUID.randomUUID().toString())
    var name by remember { mutableStateOf(original.name) }
    var title by remember { mutableStateOf(original.title) }
    var description by remember { mutableStateOf(original.description) }
    var position by remember { mutableStateOf(PERSONA_POSITIONS.firstOrNull { it.first == original.position }?.first ?: 0) }
    var depth by remember { mutableStateOf(original.depth.toString()) }
    var role by remember { mutableStateOf(original.role) }
    var avatarPath by remember { mutableStateOf(original.avatarPath) }
    var lorebook by remember { mutableStateOf(original.lorebook) }
    var positionMenu by remember { mutableStateOf(false) }
    var loreMenu by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val dir = File(context.filesDir, "persona-avatars").apply { mkdirs() }
            val dest = File(dir, original.id + ".png")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                avatarPath = dest.absolutePath
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建人设" else "编辑人设") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                        AsyncImage(
                            model = File(avatarPath),
                            contentDescription = "人设头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }) { Text("选择头像") }
                    if (avatarPath.isNotBlank()) {
                        TextButton(onClick = { avatarPath = "" }) { Text("清除") }
                    }
                }
                ShellInput(value = name, onValueChange = { name = it }, label = "名称（{{user}}）")
                ShellInput(value = title, onValueChange = { title = it }, label = "标题（可选）")
                ShellInput(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述（支持 {{char}}/{{user}} 宏）",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    val dc = EmberTheme.colors
                    Column {
                        Text("注入位置", fontSize = EmberTheme.typo.caption.fontSize, color = dc.inkMute)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(dc.surfaceSink)
                                .clickable { positionMenu = true }
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                        ) {
                            Text(
                                PERSONA_POSITIONS.firstOrNull { it.first == position }?.second ?: "提示词内",
                                fontSize = EmberTheme.typo.body.fontSize,
                                color = dc.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(FaIcons.ChevronDown, contentDescription = null, tint = dc.inkMute, modifier = Modifier.size(13.dp))
                        }
                    }
                    DropdownMenu(expanded = positionMenu, onDismissRequest = { positionMenu = false }) {
                        PERSONA_POSITIONS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { position = value; positionMenu = false },
                            )
                        }
                    }
                }
                if (position == 4) {
                    ShellInput(
                        value = depth,
                        onValueChange = { depth = it.filter { ch -> ch.isDigit() } },
                        label = "注入深度",
                    )
                }
                // 官方 persona 描述角色（role）：system/user/assistant
                Column {
                    Text("角色（role）", fontSize = EmberTheme.typo.caption.fontSize, color = EmberTheme.colors.inkMute)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "系统", 1 to "用户", 2 to "助手").forEach { (v, label) ->
                            ShellChip(label, selected = role == v) { role = v }
                        }
                    }
                }
                // 官方 persona lorebook：人设关联世界书（getPersonaLore 并入扫描）
                Box {
                    val dc = EmberTheme.colors
                    Column {
                        Text("人设世界书（lorebook，参与扫描）", fontSize = EmberTheme.typo.caption.fontSize, color = dc.inkMute)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(dc.surfaceSink)
                                .clickable { loreMenu = true }
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                        ) {
                            Text(
                                worlds.firstOrNull { it.name == lorebook }?.displayName ?: lorebook.ifBlank { "无" },
                                fontSize = EmberTheme.typo.body.fontSize,
                                color = dc.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(FaIcons.ChevronDown, contentDescription = null, tint = dc.inkMute, modifier = Modifier.size(13.dp))
                        }
                    }
                    DropdownMenu(expanded = loreMenu, onDismissRequest = { loreMenu = false }) {
                        DropdownMenuItem(text = { Text("无") }, onClick = { lorebook = ""; loreMenu = false })
                        worlds.forEach { w ->
                            DropdownMenuItem(
                                text = { Text(w.displayName) },
                                onClick = { lorebook = w.name; loreMenu = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onSave(
                    original.copy(
                        name = name.trim(),
                        title = title.trim(),
                        description = description,
                        position = position,
                        depth = depth.toIntOrNull()?.coerceIn(0, 99) ?: 2,
                        role = role,
                        avatarPath = avatarPath,
                        lorebook = lorebook.trim(),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
