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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.emberinn.app.data.Persona
import com.emberinn.app.data.PersonaStore
import com.emberinn.app.ui.components.EmberTextField
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
                onBack = onBack,
                sky = settingsSky,
                trailing = {
                    IconButton(onClick = { editNew = true; editTarget = null }) {
                        Icon(FaIcons.Plus, contentDescription = "新建人设")
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (message != null) {
                    item {
                        Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (personas.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("还没有人设", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "点右上角 + 新建；描述支持 {{char}}/{{user}} 宏",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { exportLauncher.launch("personas.json") }, modifier = Modifier.weight(1f)) {
                            Text("导出")
                        }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                            Text("导入")
                        }
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
                    if (activeId == target.id || personas.size == 1) refresh() else refresh()
                    deleteTarget = null
                    message = "已删除人设"
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    isActive: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (persona.avatarPath.isNotBlank() && File(persona.avatarPath).exists()) {
                AsyncImage(
                    model = File(persona.avatarPath),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(FaIcons.User, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name.ifBlank { "未命名" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (isActive) {
                        Tag("当前", MaterialTheme.colorScheme.primary)
                    }
                    if (isDefault) {
                        Tag("默认", MaterialTheme.colorScheme.tertiary)
                    }
                }
                persona.title.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                persona.description.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(FaIcons.EllipsisVertical, contentDescription = "更多", modifier = Modifier.size(18.dp))
                }
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
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
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
                EmberTextField(value = name, onValueChange = { name = it }, label = { Text("名称（{{user}}）") }, singleLine = true)
                EmberTextField(value = title, onValueChange = { title = it }, label = { Text("标题（可选）") }, singleLine = true)
                EmberTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（支持 {{char}}/{{user}} 宏）") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clickable { positionMenu = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                PERSONA_POSITIONS.firstOrNull { it.first == position }?.second ?: "提示词内",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(FaIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
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
                    EmberTextField(
                        value = depth,
                        onValueChange = { depth = it.filter { c -> c.isDigit() } },
                        label = { Text("注入深度") },
                        singleLine = true,
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
