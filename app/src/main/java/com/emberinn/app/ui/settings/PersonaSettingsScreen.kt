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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emberinn.app.data.Persona
import com.emberinn.app.data.PersonaStore
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellInput
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
 * 人设管理（官方 Persona Management 分区）：
 * 列表 + 新建/编辑（名称、描述、位置、深度）+ 设为当前/默认 + 删除 + 官方格式导入导出。
 */
@Composable
fun PersonaSettingsScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val context = LocalContext.current
    val store = remember { PersonaStore(context) }
    var personas by remember { mutableStateOf(store.list()) }
    var activeId by remember { mutableStateOf(store.active()?.id ?: "") }
    var defaultId by remember { mutableStateOf(store.default()?.id ?: "") }
    var editTarget by remember { mutableStateOf<Persona?>(null) }
    var editNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Persona?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        personas = store.list()
        activeId = store.active()?.id ?: ""
        defaultId = store.default()?.id ?: ""
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
                    Icon(
                        FaIcons.Plus,
                        contentDescription = "新建人设",
                        tint = c.accent,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { editNew = true; editTarget = null }
                            .padding(2.dp),
                    )
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (message != null) {
                    item {
                        Text(message!!, color = c.accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (personas.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有人设", color = c.ink, fontSize = 16.sp)
                            Text("点右上角 + 新建；描述支持 {{char}}/{{user}} 宏", color = c.inkMute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                items(personas, key = { it.id }) { persona ->
                    PersonaRow(
                        persona = persona,
                        isActive = persona.id == activeId,
                        isDefault = persona.id == defaultId,
                        onClick = { store.setActive(persona.id); refresh(); message = "已切换当前人设：${persona.name}" },
                        onEdit = { editTarget = persona },
                        onSetDefault = { store.setDefault(persona.id); refresh(); message = "已设为默认人设" },
                        onDelete = { deleteTarget = persona },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        ShellActionButton("导出官方格式", onClick = { exportLauncher.launch("personas.json") }, modifier = Modifier.weight(1f))
                        ShellActionButton("导入", onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (editTarget != null || editNew) {
        PersonaEditDialog(
            initial = editTarget,
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
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PersonaRow(
    persona: Persona,
    isActive: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = EmberTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) c.surface2 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 11.dp),
        ) {
            if (persona.avatarPath.isNotBlank() && File(persona.avatarPath).exists()) {
                AsyncImage(
                    model = File(persona.avatarPath),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(c.surfaceSink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(FaIcons.User, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name.ifBlank { "未命名" }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (isActive) {
                        Tag("当前", c.accent)
                    }
                    if (isDefault) {
                        Tag("默认", c.inkMute)
                    }
                }
                persona.title.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.accent, fontSize = 12.sp)
                }
                persona.description.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.inkMute, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                Icon(
                    FaIcons.EllipsisVertical,
                    contentDescription = "更多",
                    tint = c.inkMute,
                    modifier = Modifier.size(18.dp).clickable { menu = true }.padding(2.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("设为默认") }, onClick = { menu = false; onSetDefault() })
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    val c = EmberTheme.colors
    Box(
        modifier = Modifier
            .padding(start = 7.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 10.sp, color = if (color == c.inkMute) c.inkMute else color, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun PersonaEditDialog(initial: Persona?, onDismiss: () -> Unit, onSave: (Persona) -> Unit) {
    val original = initial ?: Persona(id = java.util.UUID.randomUUID().toString())
    var name by remember { mutableStateOf(original.name) }
    var title by remember { mutableStateOf(original.title) }
    var description by remember { mutableStateOf(original.description) }
    var position by remember { mutableStateOf(PERSONA_POSITIONS.firstOrNull { it.first == original.position }?.first ?: 0) }
    var depth by remember { mutableStateOf(original.depth.toString()) }
    var positionMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建人设" else "编辑人设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("注入位置", fontSize = 12.sp, color = dc.inkMute)
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
                                fontSize = 14.sp,
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
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
