@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.engine.card.CardFormat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun CharactersScreen(
    onOpenChat: (SessionRecord) -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val characters by vm.characters.collectAsState()
    val recentSessions by vm.recentSessions.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    var query by rememberSaveable { mutableStateOf("") }
    var menuRecord by remember { mutableStateOf<CharacterRecord?>(null) }
    var detailRecord by remember { mutableStateOf<CharacterRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<CharacterRecord?>(null) }
    var worldHit by remember { mutableStateOf<WorldInfoHit?>(null) }
    val searchResults = remember(query) { vm.search(query) }

    // 每次进入首页/从设置返回都刷新（导入、清除数据、删除角色后列表不过期）
    LaunchedEffect(Unit) { vm.refresh() }

    val filtered = remember(characters, query) {
        if (query.isBlank()) characters
        else characters.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                val name = displayName(context, it)
                val mime = context.contentResolver.getType(it)
                val format = detectFormat(name, mime)
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } ?: return@let
                vm.importCard(bytes, format)
            }.onFailure { e ->
                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u ->
            val record = menuRecord
            if (record != null) {
                runCatching {
                    context.contentResolver.openOutputStream(u)?.use { it.write(vm.exportJson(record).toByteArray()) }
                    Toast.makeText(context, "已导出：${record.name}.json", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (query.isNotBlank()) {
            SearchResultsColumn(
                query = query,
                results = searchResults,
                onQueryChange = { query = it },
                onOpenCharacter = { record -> onOpenChat(vm.openChat(record.id, record.name)) },
                onOpenSession = { onOpenChat(it) },
                onOpenSettings = onOpenSettings,
                onOpenWorldInfo = { worldHit = it },
            )
        } else if (characters.isEmpty()) {
            EmptyHome(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDirectChat = { onOpenChat(vm.openChat(null, "AI 对话")) },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeTopBar(query = query, onQueryChange = { query = it })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AiChatCard(onClick = { onOpenChat(vm.openChat(null, "AI 对话")) })
                }
                if (recentSessions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("最近聊过", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentSessions, key = { it.id }) { session ->
                                RecentChatCard(session) { onOpenChat(session) }
                            }
                        }
                    }
                }
                if (filtered.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("我的角色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(filtered, key = { it.id }) { record ->
                        CharacterCard(
                            record = record,
                            onClick = { onOpenChat(vm.openChat(record.id, record.name)) },
                            onMenu = { menuRecord = record },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "导入角色卡")
        }
    }

    menuRecord?.let { record ->
        ModalBottomSheet(onDismissRequest = { menuRecord = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                MenuRow(Icons.Filled.Star, if (record.pinned) "取消置顶" else "置顶") {
                    vm.togglePin(record); menuRecord = null
                }
                MenuRow(Icons.Filled.Add, "新会话") {
                    onOpenChat(vm.openChat(record.id, record.name)); menuRecord = null
                }
                MenuRow(Icons.Filled.Edit, "查看 / 编辑字段") {
                    detailRecord = record; menuRecord = null
                }
                MenuRow(Icons.Filled.Share, "导出 JSON") {
                    exportLauncher.launch("${record.name}.json")
                }
                MenuRow(Icons.Filled.Delete, "删除角色", danger = true) {
                    deleteTarget = record; menuRecord = null
                }
            }
        }
    }

    detailRecord?.let { record ->
        ModalBottomSheet(onDismissRequest = { detailRecord = null }, sheetState = rememberModalBottomSheetState()) {
            CharacterFieldsSheet(
                record = record,
                onSave = { name, fields ->
                    vm.updateCharacter(record, name, fields)
                    detailRecord = null
                    Toast.makeText(context, "已保存：$name", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    worldHit?.let { hit ->
        ModalBottomSheet(onDismissRequest = { worldHit = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(
                    "世界书条目 · ${hit.characterName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(hit.key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    hit.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${record.name}」？") },
            text = { Text("角色和它的聊天记录都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(record); deleteTarget = null
                    Toast.makeText(context, "已删除：${record.name}", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SearchResultsColumn(
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onOpenCharacter: (CharacterRecord) -> Unit,
    onOpenSession: (SessionRecord) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorldInfo: (WorldInfoHit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(query = query, onQueryChange = onQueryChange)
        val isEmpty = results.characters.isEmpty() && results.sessions.isEmpty() &&
            results.worldInfo.isEmpty() && results.settings.isEmpty()
        if (isEmpty) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "没有找到「$query」",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "换个关键词，试试角色名 / 会话内容 / 世界书条目 / 设置项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (results.characters.isNotEmpty()) {
                    item { SearchGroupHeader("角色") }
                    items(results.characters, key = { "c-${it.id}" }) { record ->
                        CharacterSearchRow(record, onClick = { onOpenCharacter(record) })
                    }
                }
                if (results.sessions.isNotEmpty()) {
                    item { SearchGroupHeader("会话") }
                    items(results.sessions, key = { "s-${it.id}" }) { session ->
                        SessionSearchRow(session = session, onClick = { onOpenSession(session) })
                    }
                }
                if (results.worldInfo.isNotEmpty()) {
                    item { SearchGroupHeader("世界书") }
                    itemsIndexed(results.worldInfo, key = { i, hit -> "w-${hit.characterId}-${i}" }) { _, hit ->
                        WorldInfoSearchRow(hit = hit, onClick = { onOpenWorldInfo(hit) })
                    }
                }
                if (results.settings.isNotEmpty()) {
                    item { SearchGroupHeader("设置") }
                    items(results.settings, key = { "set-${it.label}" }) { hit ->
                        SettingsSearchRow(hit = hit, onClick = onOpenSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CharacterSearchRow(record: CharacterRecord, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            SearchAvatar(name = record.name, isRole = true)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    record.description.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("去聊天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SessionSearchRow(session: SessionRecord, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            SearchAvatar(name = session.name, isRole = session.characterId != null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("会话记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("打开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WorldInfoSearchRow(hit: WorldInfoHit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            SearchAvatar(name = "书", isRole = false)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    hit.content.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(hit.characterName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSearchRow(hit: SettingsHit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            SearchAvatar(name = "设", isRole = false)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(hit.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchAvatar(name: String, isRole: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isRole) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.take(1).ifBlank { "✦" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun HomeTopBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✦ 余烬酒馆",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索角色 / 会话 / 世界书 / 设置") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AiChatCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("✦", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("AI 对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("不用角色卡，直接聊天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentChatCard(session: SessionRecord, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(session.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("继续聊天 →", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CharacterCard(record: CharacterRecord, onClick: () -> Unit, onMenu: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box {
            Column {
                if (record.avatarPath != null && File(record.avatarPath).exists()) {
                    AsyncImage(
                        model = File(record.avatarPath),
                        contentDescription = record.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(record.name.take(1).ifBlank { "?" }, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (record.pinned) {
                            Icon(Icons.Filled.Star, contentDescription = "置顶", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onMenu, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        record.description.ifBlank { "暂无简介" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
/** 可编辑角色字段（README：每字段一行 标签+预览+点击展开编辑；保存改写 rawJson 并同步会话名）。 */
private fun CharacterFieldsSheet(record: CharacterRecord, onSave: (String, Map<String, String>) -> Unit) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val fieldDefs = listOf(
        "name" to "名字",
        "description" to "描述",
        "personality" to "性格",
        "scenario" to "场景",
        "first_mes" to "开场白",
        "mes_example" to "示例对话",
        "system_prompt" to "系统提示",
        "post_history_instructions" to "剧情后指令",
        "creator_notes" to "创作者备注",
    )
    var fields by remember(record.rawJson) {
        mutableStateOf(
            runCatching {
                val root = json.parseToJsonElement(record.rawJson).jsonObject
                val data = root["data"]?.jsonObject ?: root
                fieldDefs.associate { (key, _) -> key to (data[key]?.jsonPrimitive?.contentOrNull ?: "") }
            }.getOrDefault(emptyMap()),
        )
    }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("角色字段", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        fieldDefs.forEach { (key, label) ->
            val value = fields[key].orEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    editingKey = key
                    draft = value
                }.padding(vertical = 8.dp),
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
                Icon(Icons.Filled.Edit, contentDescription = "编辑$label", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(fields["name"].orEmpty().ifBlank { record.name }, fields)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存修改") }
    }

    editingKey?.let { key ->
        val label = fieldDefs.firstOrNull { it.first == key }?.second ?: key
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("编辑$label") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    fields = fields + (key to draft)
                    editingKey = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyHome(onImport: () -> Unit, onDirectChat: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✦", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("欢迎来到余烬酒馆", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("导入第一张角色卡，开始你的故事", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport) { Text("导入角色卡") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDirectChat) { Text("直接开始聊天") }
    }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private fun detectFormat(name: String?, mime: String?): CardFormat = when {
    name?.endsWith(".png", ignoreCase = true) == true -> CardFormat.PNG
    name?.endsWith(".charx", ignoreCase = true) == true -> CardFormat.CHARX
    name?.endsWith(".byaf", ignoreCase = true) == true -> CardFormat.BYAF
    name?.endsWith(".yaml", ignoreCase = true) == true || name?.endsWith(".yml", ignoreCase = true) == true -> CardFormat.YAML
    name?.endsWith(".json", ignoreCase = true) == true -> CardFormat.JSON
    mime == "image/png" -> CardFormat.PNG
    mime?.contains("json", ignoreCase = true) == true -> CardFormat.JSON
    else -> CardFormat.JSON
}
