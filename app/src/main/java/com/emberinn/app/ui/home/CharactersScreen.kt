@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.home

import com.emberinn.app.ui.components.EmberEmptyState
import com.emberinn.app.ui.components.EmberMenuRow
import com.emberinn.app.ui.components.EmberSectionHeader
import com.emberinn.app.ui.components.EmberGlassDefaults
import com.emberinn.app.ui.components.emberGlass
import com.emberinn.app.ui.components.glassEdgeHighlight
import com.emberinn.app.ui.components.EmberHaptics
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.ui.theme.LocalThemePreset
import com.emberinn.app.ui.theme.LocalVibe

import com.emberinn.app.ui.icons.FaIcons
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberBottomSheet
import com.emberinn.engine.card.CardFormat
import com.skydoves.cloudy.sky
import com.skydoves.cloudy.rememberSky
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import java.io.File

@Composable
fun CharactersScreen(
    onOpenChat: (SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit = {},
    onOpenDetail: (CharacterRecord) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val characters by vm.characters.collectAsState()
    val recentSessions by vm.recentSessions.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var query by rememberSaveable { mutableStateOf("") }
    var menuRecord by remember { mutableStateOf<CharacterRecord?>(null) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showUrlImport by remember { mutableStateOf(false) }
    var urlDraft by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CharacterRecord?>(null) }
    var worldHit by remember { mutableStateOf<WorldInfoHit?>(null) }
    val searchResults = remember(query) { vm.search(query) }

    // README 首页：毛玻璃顶栏（Cloudy 背板模糊，正文区干净）
    val sky = rememberSky()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0) }
    val topBarPad = with(density) { topBarHeight.toDp() }

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
        // 静态背景层：首页顶栏毛玻璃的静态模糊源（列表滚动不再触发整屏重捕/重模糊）。
        // 左下低饱和主色光晕给玻璃一层可模糊的氛围底，避免纯色背景模糊后看不出玻璃质感。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(360.dp)
                    .offset(x = (-130).dp, y = 60.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), Color.Transparent),
                        ),
                    ),
            )
        }
        if (query.isNotBlank()) {
            SearchResultsColumn(
                query = query,
                results = searchResults,
                onQueryChange = { query = it },
                onOpenCharacter = { record -> onOpenChat(vm.openOrResume(record.id, record.name)) },
                onOpenSession = { onOpenChat(it) },
                onOpenSettings = onOpenSettings,
                onOpenWorldInfo = { worldHit = it },
                sky = sky,
            )
        } else if (characters.isEmpty()) {
            EmptyHome(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDirectChat = { onOpenChat(vm.newSession(null, "AI 对话")) },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarPad + 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp * LocalThemePreset.current.spacing),
                verticalArrangement = Arrangement.spacedBy(12.dp * LocalThemePreset.current.spacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AiChatCard(onClick = { EmberHaptics.select(haptic); onOpenChat(vm.newSession(null, "AI 对话")) })
                }
                if (recentSessions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmberSectionHeader("最近聊过")
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentSessions, key = { it.id }) { session ->
                                RecentChatCard(
                                    session = session,
                                    avatarPath = characters.firstOrNull { it.id == session.characterId }?.avatarPath,
                                    preview = vm.lastMessage(session.id),
                                    onClick = { EmberHaptics.select(haptic); onOpenChat(session) },
                                )
                            }
                        }
                    }
                }
                if (filtered.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmberSectionHeader("我的角色")
                    }
                    items(filtered, key = { it.id }) { record ->
                        CharacterCard(
                            record = record,
                            preview = vm.lastMessageFor(record.id),
                            onClick = { EmberHaptics.select(haptic); onOpenChat(vm.openOrResume(record.id, record.name)) },
                            onMenu = { menuRecord = record },
                        )
                    }
                }
            }
        }

        if (query.isBlank() && characters.isNotEmpty()) {
            HomeTopBar(
                query = query,
                onQueryChange = { query = it },
                glass = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeight = it.height }
                    .emberGlass(sky = sky, atTop = false),
            )
        }

        GlassFab(
            onClick = { EmberHaptics.select(haptic); showImportSheet = true },
            sky = sky,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (showImportSheet) {
        EmberBottomSheet(onDismissRequest = { showImportSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "导入角色卡",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                EmberMenuRow(FaIcons.Folder, "从文件导入", "PNG / JSON / CharX") {
                    showImportSheet = false
                    importLauncher.launch(arrayOf("*/*"))
                }
                EmberMenuRow(FaIcons.Download, "从 URL 导入", "角色卡直链自动识别格式") {
                    showImportSheet = false
                    urlDraft = ""
                    showUrlImport = true
                }
            }
        }
    }

    if (showUrlImport) {
        AlertDialog(
            onDismissRequest = { showUrlImport = false },
            title = { Text("从 URL 导入角色卡") },
            text = {
                Column {
                    EmberTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("角色卡直链（PNG / JSON / CharX）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "下载后自动识别格式并入库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = urlDraft.trim()
                    if (url.isBlank()) return@TextButton
                    showUrlImport = false
                    vm.importCardFromUrl(url) { ok, err ->
                        if (ok) {
                            Toast.makeText(context, "已从 URL 导入角色卡", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "导入失败：${err ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImport = false }) { Text("取消") }
            },
        )
    }

    menuRecord?.let { record ->
        EmberBottomSheet(onDismissRequest = { menuRecord = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                EmberMenuRow(FaIcons.Star, if (record.pinned) "取消置顶" else "置顶") {
                    vm.togglePin(record); menuRecord = null
                }
                EmberMenuRow(FaIcons.Plus, "新会话") {
                    onOpenChat(vm.newSession(record.id, record.name)); menuRecord = null
                }
                EmberMenuRow(FaIcons.Pencil, "查看 / 编辑详情") {
                    menuRecord = null
                    onOpenDetail(record)
                }
                EmberMenuRow(FaIcons.FileExport, "导出 JSON") {
                    exportLauncher.launch("${record.name}.json")
                }
                EmberMenuRow(FaIcons.TrashCan, "删除角色", danger = true) {
                    deleteTarget = record; menuRecord = null
                }
            }
        }
    }

    worldHit?.let { hit ->
        EmberBottomSheet(onDismissRequest = { worldHit = null }, sheetState = rememberModalBottomSheetState()) {
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
                    EmberHaptics.reject(haptic)
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
    onOpenSettings: (String?) -> Unit,
    onOpenWorldInfo: (WorldInfoHit) -> Unit,
    sky: com.skydoves.cloudy.Sky,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            query = query,
            onQueryChange = onQueryChange,
            glass = true,
            modifier = Modifier
                .fillMaxWidth()
                .emberGlass(sky = sky, atTop = false),
        )
        val isEmpty = results.characters.isEmpty() && results.sessions.isEmpty() &&
            results.worldInfo.isEmpty() && results.settings.isEmpty()
        if (isEmpty) {
            EmberEmptyState(
                title = "没有找到「$query」",
                body = "换个关键词，试试角色名 / 会话内容 / 世界书条目 / 设置项",
                icon = FaIcons.MagnifyingGlass,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            )
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
                        SettingsSearchRow(hit = hit, onClick = { onOpenSettings(hit.route) })
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
            Spacer(Modifier.width(8.dp))
            Icon(FaIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
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
private fun HomeTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    glass: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (glass) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
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
            EmberTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索角色 / 会话 / 世界书 / 设置") },
                leadingIcon = { Icon(FaIcons.MagnifyingGlass, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 玻璃悬浮按钮（README 浮层玻璃：主操作 + 毛玻璃 + 边缘高光，正文区不玻璃）。 */
@Composable
private fun GlassFab(
    onClick: () -> Unit,
    sky: com.skydoves.cloudy.Sky,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .emberShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                radius = 12.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.18f + 0.12f * LocalVibe.current.glow,
            )
            .clip(RoundedCornerShape(18.dp))
            .emberGlass(sky = sky, atTop = true, tintAlpha = EmberGlassDefaults.FAB_TINT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(FaIcons.Plus, contentDescription = "导入角色卡", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AiChatCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .emberShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                radius = 12.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.08f + 0.16f * LocalVibe.current.glow,
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        // README：AI 对话 = 玻璃渐变卡（低饱和主色 → 表面）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEdgeHighlight(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f, atTop = true)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                        ),
                    ),
                ),
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
}

@Composable
private fun RecentChatCard(
    session: SessionRecord,
    avatarPath: String?,
    preview: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().emberShadow(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
            radius = 10.dp,
            offset = DpOffset(0.dp, 4.dp),
            alpha = 0.08f + 0.16f * LocalVibe.current.glow,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // README：最近聊过 = 头像 + 名字（1 秒续聊）
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(session.name.take(1), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(session.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    preview ?: "继续聊天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(record: CharacterRecord, preview: String?, onClick: () -> Unit, onMenu: () -> Unit) {
    // README 角色卡驱动主题（第二层）：每张卡一眼不同——卡片底色带该卡 seed 的极淡 tint
    //（克制：混入 86% 底色，只体现"每卡专属氛围"，不喧宾夺主），名字用 seed、无头像占位用 seed 渐变
    val seed = record.seedColor?.let { Color(it.toInt()) }
    val cardColor = seed?.let { lerp(it, MaterialTheme.colorScheme.surfaceVariant, 0.86f) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    // README 清单 8：形状语言区分角色——每卡按自身主题配方 shape 取角（无配方=16dp 圆润）
    val cardRecipe = remember(record) { CharacterCardEdit.readThemeRecipe(record.rawJson) }
    val corner = when (cardRecipe.shape) {
        "square" -> 4.dp
        "circle" -> 24.dp
        "rounded" -> 16.dp
        else -> 16.dp
    }
    // 彩色发光阴影：角色 seed 垂直渐变光晕（网格只渲染可见卡，无卡顿）
    val glow = LocalVibe.current.glow
    val shadowColor = seed ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .emberShadow(
                brush = Brush.verticalGradient(
                    listOf(
                        shadowColor.copy(alpha = 0.30f * glow),
                        shadowColor.copy(alpha = 0.08f * glow),
                        Color.Transparent,
                    ),
                ),
                radius = 14.dp,
                spread = 2.dp,
                offset = DpOffset(0.dp, 7.dp),
            ),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Box {
            Column {
                if (record.avatarPath != null && File(record.avatarPath).exists()) {
                    AsyncImage(
                        model = File(record.avatarPath),
                        contentDescription = record.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = corner, topEnd = corner)),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(
                            Brush.linearGradient(
                                if (seed != null) {
                                    listOf(
                                        lerp(seed, MaterialTheme.colorScheme.surfaceVariant, 0.62f),
                                        lerp(seed, MaterialTheme.colorScheme.surfaceVariant, 0.85f),
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    )
                                },
                            ),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(record.name.take(1).ifBlank { "?" }, style = MaterialTheme.typography.displaySmall, color = seed ?: MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = seed ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (record.pinned) {
                            Icon(FaIcons.Star, contentDescription = "置顶", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onMenu, modifier = Modifier.size(24.dp)) {
                            Icon(FaIcons.EllipsisVertical, contentDescription = "更多", modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        // README：卡片显示最近消息预览；无会话时回退简介
                        preview?.takeIf { it.isNotBlank() } ?: record.description.ifBlank { "暂无简介" },
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
private fun EmptyHome(onImport: () -> Unit, onDirectChat: () -> Unit) {
    EmberEmptyState(
        title = "欢迎来到余烬酒馆",
        body = "导入第一张角色卡，开始你的故事",
        actionLabel = "导入角色卡",
        onAction = onImport,
        secondaryLabel = "直接开始聊天",
        onSecondary = onDirectChat,
        icon = FaIcons.User,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    )
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
