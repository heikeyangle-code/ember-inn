@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.home

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.app.ui.icons.PhosphorIcons
import java.io.File

/**
 * 角色详情编辑页（P1-4）：官方 v2 字段全集编辑 + 世界书条目管理 + 备用开场白。
 * 本地状态收集改动，底部"保存修改"一次写回（v2 归一，talkativeness 进 extensions）。
 */
@Composable
fun CharacterDetailScreen(
    record: CharacterRecord,
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenChat: (SessionRecord) -> Unit,
) {
    val context = LocalContext.current
    val seed = record.seedColor?.let { Color(it.toInt()) }

    var fields by remember(record.id) { mutableStateOf(vm.readCharacterFields(record)) }
    var entries by remember(record.id) { mutableStateOf(vm.readWorldEntries(record)) }
    var dirty by remember { mutableStateOf(false) }

    var editingKey by remember { mutableStateOf<String?>(null) }
    var fieldDraft by remember { mutableStateOf("") }
    var editingEntryIdx by remember { mutableStateOf<Int?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var editingGreetingIdx by remember { mutableStateOf<Int?>(null) }
    var greetingDraft by remember { mutableStateOf("") }
    var editingDepth by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { s -> s.write(vm.exportJson(record).toByteArray()) }
                Toast.makeText(context, "已导出：${record.name}.json", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setField(key: String, v: String) {
        fields = when (key) {
            "name" -> fields.copy(name = v)
            "description" -> fields.copy(description = v)
            "personality" -> fields.copy(personality = v)
            "scenario" -> fields.copy(scenario = v)
            "mes_example" -> fields.copy(mesExample = v)
            "system_prompt" -> fields.copy(systemPrompt = v)
            "post_history_instructions" -> fields.copy(postHistoryInstructions = v)
            "creator" -> fields.copy(creator = v)
            "character_version" -> fields.copy(characterVersion = v)
            "creator_notes" -> fields.copy(creatorNotes = v)
            "tags" -> fields.copy(tags = v)
            else -> fields
        }
        dirty = true
    }

    val save = {
        vm.saveCharacterFields(record, fields)
        vm.saveWorldEntries(record, entries)
        dirty = false
        Toast.makeText(context, "已保存：${fields.name.ifBlank { record.name }}", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：返回 + 名称 + 菜单（开始聊天/导出/置顶/删除）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(PhosphorIcons.ArrowLeft, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fields.name.ifBlank { record.name },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (dirty) "有未保存的修改" else "角色详情与编辑",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(PhosphorIcons.DotsThreeVertical, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("开始聊天") },
                            leadingIcon = { Icon(PhosphorIcons.Send, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenChat(vm.openOrResume(record.id, fields.name.ifBlank { record.name }))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 JSON") },
                            leadingIcon = { Icon(PhosphorIcons.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("${fields.name.ifBlank { record.name }}.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (record.pinned) "取消置顶" else "置顶") },
                            leadingIcon = { Icon(PhosphorIcons.Star, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                vm.togglePin(record)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除角色", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(PhosphorIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 168.dp),
            ) {
                // 头部：大头像 + 名字 + 描述 + 统计
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        if (record.avatarPath != null && File(record.avatarPath).exists()) {
                            AsyncImage(
                                model = File(record.avatarPath),
                                contentDescription = record.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(72.dp).clip(CircleShape).background(
                                    Brush.linearGradient(
                                        if (seed != null) {
                                            listOf(lerp(seed, MaterialTheme.colorScheme.surface, 0.55f), seed)
                                        } else {
                                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                                        },
                                    ),
                                ),
                            ) {
                                Text(
                                    record.name.take(1),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fields.description.ifBlank { "（无描述）" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("世界书 ${entries.size} 条")
                                StatChip("开场白 ${fields.alternateGreetings.size}")
                                if (fields.tags.isNotBlank()) StatChip("标签 ${fields.tags.split(',').count { it.isNotBlank() }}")
                            }
                        }
                    }
                    HorizontalDivider()
                }

                item {
                    SectionHeader("基础字段")
                }
                item {
                    FieldRow("名字", fields.name) {
                        editingKey = "name"; fieldDraft = fields.name
                    }
                }
                item {
                    FieldRow("描述", fields.description) {
                        editingKey = "description"; fieldDraft = fields.description
                    }
                }
                item {
                    FieldRow("性格", fields.personality) {
                        editingKey = "personality"; fieldDraft = fields.personality
                    }
                }
                item {
                    FieldRow("场景", fields.scenario) {
                        editingKey = "scenario"; fieldDraft = fields.scenario
                    }
                }
                item {
                    FieldRow("开场白", fields.firstMes) {
                        editingKey = "first_mes"; fieldDraft = fields.firstMes
                    }
                }
                item {
                    FieldRow("示例对话", fields.mesExample) {
                        editingKey = "mes_example"; fieldDraft = fields.mesExample
                    }
                }

                item {
                    SectionHeader("备用开场白", "${fields.alternateGreetings.size} 个")
                }
                if (fields.alternateGreetings.isEmpty()) {
                    item {
                        Text(
                            "没有备用开场白。点击下方按钮新增，新会话可从备用开场白开始。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                    }
                }
                items(fields.alternateGreetings.size) { i ->
                    GreetingRow(
                        text = fields.alternateGreetings[i],
                        onEdit = { editingGreetingIdx = i; greetingDraft = fields.alternateGreetings[i] },
                        onDelete = {
                            fields = fields.copy(alternateGreetings = fields.alternateGreetings.filterIndexed { j, _ -> j != i })
                            dirty = true
                        },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { editingGreetingIdx = fields.alternateGreetings.size; greetingDraft = "" },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text("＋ 新增开场白") }
                }

                item {
                    SectionHeader("世界书", "${entries.size} 条")
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            "没有世界书条目。新增关键词条目后，聊到关键词时内容会自动注入上下文。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                    }
                }
                items(entries.size) { i ->
                    WorldEntryRow(
                        entry = entries[i],
                        onEdit = { editingEntryIdx = i },
                        onToggle = {
                            entries = entries.mapIndexed { j, e -> if (j == i) e.copy(enabled = !e.enabled) else e }
                            dirty = true
                        },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { addingEntry = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text("＋ 新增条目") }
                }

                item {
                    SectionHeader("高级")
                }
                item {
                    FieldRow("系统提示", fields.systemPrompt) {
                        editingKey = "system_prompt"; fieldDraft = fields.systemPrompt
                    }
                }
                item {
                    FieldRow("剧情后指令", fields.postHistoryInstructions) {
                        editingKey = "post_history_instructions"; fieldDraft = fields.postHistoryInstructions
                    }
                }
                item {
                    FieldRow(
                        "深度提示",
                        fields.depthPrompt.ifBlank { "深度 ${fields.depthPromptDepth} · 角色 ${fields.depthPromptRole}" },
                    ) { editingDepth = true }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("话痨程度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(90.dp))
                        Slider(
                            value = fields.talkativeness,
                            onValueChange = { fields = fields.copy(talkativeness = it); dirty = true },
                            valueRange = 0f..1f,
                            steps = 19,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                fields.talkativeness < 0.3f -> "安静"
                                fields.talkativeness < 0.7f -> "适中"
                                else -> "话多"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
                item {
                    FieldRow("作者", fields.creator) {
                        editingKey = "creator"; fieldDraft = fields.creator
                    }
                }
                item {
                    FieldRow("版本", fields.characterVersion) {
                        editingKey = "character_version"; fieldDraft = fields.characterVersion
                    }
                }
                item {
                    FieldRow("创作者备注", fields.creatorNotes) {
                        editingKey = "creator_notes"; fieldDraft = fields.creatorNotes
                    }
                }
                item {
                    FieldRow("标签", fields.tags) {
                        editingKey = "tags"; fieldDraft = fields.tags
                    }
                }
            }
        }

        // 底部固定保存栏
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Button(
                onClick = save,
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (dirty) "保存修改" else "没有修改",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    // ---- 字段编辑对话框 ----
    editingKey?.let { key ->
        val label = when (key) {
            "name" -> "名字"
            "description" -> "描述"
            "personality" -> "性格"
            "scenario" -> "场景"
            "mes_example" -> "示例对话"
            "system_prompt" -> "系统提示"
            "post_history_instructions" -> "剧情后指令"
            "creator" -> "作者"
            "character_version" -> "版本"
            "creator_notes" -> "创作者备注"
            else -> "标签"
        }
        val multiline = key != "name" && key != "tags" && key != "creator" && key != "character_version"
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("编辑$label") },
            text = {
                OutlinedTextField(
                    value = fieldDraft,
                    onValueChange = { fieldDraft = it },
                    minLines = if (multiline) 3 else 1,
                    maxLines = if (multiline) 10 else 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    setField(key, fieldDraft)
                    editingKey = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("取消") }
            },
        )
    }

    // ---- 深度提示对话框 ----
    if (editingDepth) {
        var prompt by remember(fields.depthPrompt) { mutableStateOf(fields.depthPrompt) }
        var depth by remember(fields.depthPromptDepth) { mutableStateOf(fields.depthPromptDepth) }
        var role by remember(fields.depthPromptRole) { mutableStateOf(fields.depthPromptRole) }
        AlertDialog(
            onDismissRequest = { editingDepth = false },
            title = { Text("编辑深度提示") },
            text = {
                Column {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("（空）") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = depth,
                        onValueChange = { depth = it.filter { c -> c.isDigit() } },
                        label = { Text("注入深度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("system", "user", "assistant").forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fields = fields.copy(
                        depthPrompt = prompt,
                        depthPromptDepth = depth.ifBlank { "4" },
                        depthPromptRole = role,
                    )
                    dirty = true
                    editingDepth = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingDepth = false }) { Text("取消") }
            },
        )
    }

    // ---- 备用开场白编辑 ----
    editingGreetingIdx?.let { idx ->
        val isNew = idx >= fields.alternateGreetings.size
        AlertDialog(
            onDismissRequest = { editingGreetingIdx = null },
            title = { Text(if (isNew) "新增开场白" else "编辑开场白") },
            text = {
                OutlinedTextField(
                    value = greetingDraft,
                    onValueChange = { greetingDraft = it },
                    minLines = 3,
                    maxLines = 10,
                    placeholder = { Text("角色说的第一句话") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = greetingDraft.trim()
                    if (trimmed.isNotEmpty()) {
                        fields = if (isNew) {
                            fields.copy(alternateGreetings = fields.alternateGreetings + trimmed)
                        } else {
                            fields.copy(alternateGreetings = fields.alternateGreetings.mapIndexed { j, g -> if (j == idx) trimmed else g })
                        }
                        dirty = true
                    }
                    editingGreetingIdx = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingGreetingIdx = null }) { Text("取消") }
            },
        )
    }

    // ---- 世界书条目编辑 ----
    val editingEntry = entries.getOrNull(editingEntryIdx ?: -1)
    if (editingEntry != null || addingEntry) {
        WorldEntryEditorSheet(
            initial = editingEntry ?: WorldEntryDraft(
                id = (entries.maxOfOrNull { it.id } ?: 0) + 1,
                keys = "", content = "", comment = "",
                constant = false, selective = true, enabled = true, insertionOrder = 100,
            ),
            isNew = addingEntry,
            onSave = { d ->
                if (addingEntry) {
                    entries = entries + d
                } else {
                    val i = editingEntryIdx ?: 0
                    entries = entries.mapIndexed { j, e -> if (j == i) d else e }
                }
                dirty = true
                addingEntry = false
                editingEntryIdx = null
            },
            onDelete = {
                val i = editingEntryIdx
                if (i != null && i in entries.indices) {
                    entries = entries.filterIndexed { j, _ -> j != i }
                    dirty = true
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

    // ---- 删除确认 ----
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${record.name}」？") },
            text = { Text("角色和它的聊天记录都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(record)
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
private fun SectionHeader(title: String, count: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                value.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(PhosphorIcons.Edit, contentDescription = "编辑$label", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider()
}

@Composable
private fun GreetingRow(text: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 6.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(PhosphorIcons.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(PhosphorIcons.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

@Composable
private fun WorldEntryRow(entry: WorldEntryDraft, onEdit: () -> Unit, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (entry.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.keys.ifBlank { "（无触发词）" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (entry.keys.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.constant) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "恒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (entry.selective) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.content.ifBlank { entry.comment.ifBlank { "（空内容）" } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "插入顺序 ${entry.insertionOrder}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(PhosphorIcons.Edit, contentDescription = "编辑条目", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun WorldEntryEditorSheet(
    initial: WorldEntryDraft,
    isNew: Boolean,
    onSave: (WorldEntryDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var keys by remember(initial) { mutableStateOf(initial.keys) }
    var content by remember(initial) { mutableStateOf(initial.content) }
    var comment by remember(initial) { mutableStateOf(initial.comment) }
    var constant by remember(initial) { mutableStateOf(initial.constant) }
    var selective by remember(initial) { mutableStateOf(initial.selective) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var order by remember(initial) { mutableStateOf(initial.insertionOrder.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (isNew) "新增世界书条目" else "编辑世界书条目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "聊到触发词时，内容自动注入上下文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = keys,
                onValueChange = { keys = it },
                label = { Text("触发词（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("备注（仅作者可见）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = order,
                onValueChange = { order = it.filter { c -> c.isDigit() } },
                label = { Text("插入顺序") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("恒定（常驻上下文）", constant) { constant = it }
            SwitchRow("选择性（配合逻辑）", selective) { selective = it }
            SwitchRow("启用", enabled) { enabled = it }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) { Text("删除条目", color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = {
                        onSave(
                            WorldEntryDraft(
                                id = initial.id,
                                keys = keys.trim(),
                                content = content,
                                comment = comment,
                                constant = constant,
                                selective = selective,
                                enabled = enabled,
                                insertionOrder = order.toIntOrNull() ?: 100,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存条目") }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
