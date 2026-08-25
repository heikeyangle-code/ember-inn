@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.emberinn.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.components.EmberHaptics
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.AvatarCircle
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.PosterTile
import com.emberinn.app.ui.design.components.SearchField
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.settings.BehaviorPrefs
import com.emberinn.engine.card.CardFormat

/**
 * 角色库（DESIGN_SYSTEM §4.3 海报墙定稿）：
 * 顶部常驻搜索场 + 双列瀑布海报（纵横比随卡错落）+ 幽灵导入砖。
 * 长按海报 = 置顶 / 新会话 / 详情 / 导出 / 删除。零 M3 卡片、零玻璃、零投影。
 */
@Composable
fun CharactersScreen(
    onOpenChat: (SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit = {},
    onOpenDetail: (CharacterRecord) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val c = EmberTheme.colors
    val characters by vm.characters.collectAsState()
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
    // 筛选 / 排序（§4.3 海报墙+筛选）：收藏轨道、标签轨道、官方 11 档书架排序
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // 官方 power_user.sort_field + sort_order（power-user.js 默认 name/asc），跨会话持久化
    val behavior = remember { BehaviorPrefs.load(context) }
    var sortField by rememberSaveable { mutableStateOf(behavior.sortField) }
    var sortOrder by rememberSaveable { mutableStateOf(behavior.sortOrder) }
    var showSortSheet by remember { mutableStateOf(false) }
    val searchResults = remember(query) { vm.search(query) }

    // 每次进入书架/从设置返回都刷新（导入、删除后列表不过期）
    LaunchedEffect(Unit) { vm.refresh() }

    // 卡字段索引：列表变更时一次解析（标签轨道 + aux_field 副标题共用，避免重复 parse rawJson）
    val fieldsOf = remember(characters) { characters.associate { it.id to vm.readCharacterFields(it) } }
    val tagsOf = remember(fieldsOf) {
        fieldsOf.mapValues { (_, f) -> f.tags.split(',').mapNotNull { t -> t.trim().takeIf(String::isNotEmpty) } }
    }
    val topTags = remember(tagsOf) {
        tagsOf.values.flatten().groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(10)
    }
    // 官方排序数据源：最近聊天/会话数 + 卡体量/创建时间（列表变更时一次计算）
    val activity = remember(characters) { vm.characterActivity() }
    val meta = remember(characters) { vm.characterMeta() }
    val currentSort = remember(sortField, sortOrder) { SORT_OPTIONS.firstOrNull { it.field == sortField && it.order == sortOrder } }

    val filtered = remember(characters, query, onlyFavorites, tagFilter, sortField, sortOrder, activity, meta) {
        var list = if (query.isBlank()) characters
        else characters.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        if (onlyFavorites) list = list.filter { it.pinned }
        if (tagFilter != null) list = list.filter { tagsOf[it.id]?.contains(tagFilter) == true }
        sortCharacters(list, sortField, sortOrder, activity, meta)
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

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(14.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索角色 / 会话 / 世界书 / 设置",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            // 筛选轨道：收藏 / 标签 / 排序（无搜索时展示；有筛选时保持可见以便清除）
            if (query.isBlank() && characters.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        ShellChip(label = "收藏", selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites })
                        // 官方 power_user.show_tag_filters：标签筛选轨道开关（本 App 默认开）
                        if (behavior.showTagFilters) topTags.forEach { tag ->
                            ShellChip(
                                label = tag,
                                selected = tagFilter == tag,
                                onClick = { tagFilter = if (tagFilter == tag) null else tag },
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // 排序入口：chip 即当前值摘要（官方 11 档，点击弹层切换）
                    ShellChip(
                        label = "排序 · ${currentSort?.short ?: "默认"}",
                        selected = true,
                        onClick = { showSortSheet = true },
                    )
                }
            }
            if (query.isNotBlank()) {
                SearchResultsList(
                    results = searchResults,
                    onOpenCharacter = { record -> onOpenChat(vm.openOrResume(record.id, record.name)) },
                    onOpenSession = { onOpenChat(it) },
                    onOpenSettings = onOpenSettings,
                    onOpenWorldInfo = { worldHit = it },
                )
            } else if (characters.isEmpty()) {
                EmptyLibrary(
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDirectChat = { onOpenChat(vm.newSession(null, "AI 对话")) },
                )
            } else {
                // 海报墙：双列瀑布，纵横比随卡 id 哈希错落
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered.size, key = { filtered[it].id }) { idx ->
                        val record = filtered[idx]
                        val aspect = remember(record.id) {
                            listOf(0.70f, 0.62f, 0.78f)[kotlin.math.abs(record.id.hashCode()) % 3]
                        }
                        PosterTile(
                            name = record.name + if (record.pinned) " ·↑" else "",
                            avatarPath = record.avatarPath,
                            aspect = aspect,
                            onClick = { EmberHaptics.select(haptic); onOpenDetail(record) },
                            onLongClick = { menuRecord = record },
                            // 官方 power_user.aux_field 副标题：character_version / creator，卡内为空不显示
                            subtitle = when (behavior.auxField) {
                                "creator" -> fieldsOf[record.id]?.creator
                                else -> fieldsOf[record.id]?.characterVersion
                            }?.takeIf { it.isNotBlank() },
                        )
                    }
                    item(key = "import-ghost") {
                        PosterTile(
                            name = "导入角色卡",
                            avatarPath = null,
                            ghost = true,
                            onClick = { EmberHaptics.select(haptic); showImportSheet = true },
                        )
                    }
                }
            }
        }

        // 导入圆粒：与 FloatHub 同语言
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(c.surface.copy(alpha = 0.96f))
                .border(1.dp, c.lineStrong, CircleShape)
                .clickable { EmberHaptics.select(haptic); showImportSheet = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(FaIcons.Plus, contentDescription = "导入角色卡", tint = c.ink, modifier = Modifier.size(18.dp))
        }
    }

    if (showImportSheet) {
        ShellSheet(onDismiss = { showImportSheet = false }, title = "导入角色卡") {
            SheetRow(FaIcons.Folder, "从文件导入", "PNG / JSON / CharX") {
                showImportSheet = false
                importLauncher.launch(arrayOf("*/*"))
            }
            SheetRow(FaIcons.Download, "从 URL 导入", "角色卡直链自动识别格式") {
                showImportSheet = false
                urlDraft = ""
                showUrlImport = true
            }
        }
    }

    // 官方 11 档书架排序弹层（选择即存 BehaviorPrefs，跨会话生效）
    if (showSortSheet) {
        ShellSheet(onDismiss = { showSortSheet = false }, title = "书架排序") {
            SORT_OPTIONS.forEach { opt ->
                SortOptionRow(
                    label = opt.label,
                    selected = sortField == opt.field && sortOrder == opt.order,
                    onClick = {
                        sortField = opt.field
                        sortOrder = opt.order
                        val cur = BehaviorPrefs.load(context)
                        if (cur.sortField != opt.field || cur.sortOrder != opt.order) {
                            BehaviorPrefs.save(context, cur.copy(sortField = opt.field, sortOrder = opt.order))
                        }
                        showSortSheet = false
                    },
                )
            }
        }
    }

    if (showUrlImport) {
        AlertDialog(
            onDismissRequest = { showUrlImport = false },
            title = { Text("从 URL 导入角色卡") },
            text = {
                Column {
                    ShellInput(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = "角色卡直链（PNG / JSON / CharX）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("下载后自动识别格式并入库。", color = c.inkMute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = urlDraft.trim()
                    if (url.isBlank()) return@TextButton
                    showUrlImport = false
                    vm.importCardFromUrl(url) { ok, err ->
                        if (ok) Toast.makeText(context, "已从 URL 导入角色卡", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "导入失败：${err ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showUrlImport = false }) { Text("取消") } },
        )
    }

    menuRecord?.let { record ->
        ShellSheet(onDismiss = { menuRecord = null }, title = record.name) {
            SheetRow(FaIcons.Star, if (record.pinned) "取消置顶" else "置顶") {
                vm.togglePin(record); menuRecord = null
            }
            SheetRow(FaIcons.Plus, "新会话") {
                onOpenChat(vm.newSession(record.id, record.name)); menuRecord = null
            }
            SheetRow(FaIcons.Pencil, "查看 / 编辑详情") {
                menuRecord = null
                onOpenDetail(record)
            }
            SheetRow(FaIcons.FileExport, "导出 JSON") {
                exportLauncher.launch("${record.name}.json")
            }
            SheetRow(FaIcons.TrashCan, "删除角色", danger = true) {
                deleteTarget = record; menuRecord = null
            }
        }
    }

    worldHit?.let { hit ->
        ShellSheet(onDismiss = { worldHit = null }, title = "世界书条目 · ${hit.characterName}") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(hit.key, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(hit.content, color = c.ink, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
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
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/** 搜索结果四分组：角色 / 对话 / 世界书 / 设置，安静行直达。 */
@Composable
private fun SearchResultsList(
    results: SearchResults,
    onOpenCharacter: (CharacterRecord) -> Unit,
    onOpenSession: (SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onOpenWorldInfo: (WorldInfoHit) -> Unit,
) {
    val c = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        GroupLabel("角色")
        results.characters.forEach { record ->
            ResultRow(FaIcons.Mask, record.name, "角色卡 · ${record.description.take(30)}") { onOpenCharacter(record) }
        }
        GroupLabel("对话")
        results.sessions.forEach { session ->
            ResultRow(FaIcons.Comments, session.name, "会话") { onOpenSession(session) }
        }
        GroupLabel("世界书")
        results.worldInfo.forEach { hit ->
            ResultRow(FaIcons.BookOpen, hit.key, hit.characterName) { onOpenWorldInfo(hit) }
        }
        GroupLabel("设置")
        results.settings.forEach { hit ->
            ResultRow(FaIcons.Gear, hit.label, hit.description) { onOpenSettings(hit.route) }
        }
        if (results.characters.isEmpty() && results.sessions.isEmpty() && results.worldInfo.isEmpty() && results.settings.isEmpty()) {
            Text("没有匹配结果", color = c.inkMute, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 16.dp))
        }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp)
            Text(subtitle, color = c.inkMute, fontSize = 11.sp, maxLines = 1)
        }
        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onDirectChat: () -> Unit) {
    val c = EmberTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) {
        Text("书架还是空的", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("导入 SillyTavern 角色卡开始演出", color = c.inkMute, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShellActionButton(label = "导入角色卡") { onImport() }
            ShellActionButton(label = "直接开聊") { onDirectChat() }
        }
    }
}


/** 官方 11 档书架排序选项（index.html #character_sort_order 选项表 + power-user.js sortEntitiesList 语义）。 */
private data class SortOption(val field: String, val order: String, val label: String, val short: String)

private val SORT_OPTIONS = listOf(
    SortOption("name", "asc", "名称 A → Z", "A-Z"),
    SortOption("name", "desc", "名称 Z → A", "Z-A"),
    SortOption("create_date", "desc", "最新创建", "最新创建"),
    SortOption("create_date", "asc", "最旧创建", "最旧创建"),
    SortOption("fav", "desc", "收藏优先", "收藏"),
    SortOption("date_last_chat", "desc", "最近聊天", "最近聊天"),
    SortOption("chat_size", "desc", "会话最多", "会话最多"),
    SortOption("chat_size", "asc", "会话最少", "会话最少"),
    SortOption("data_size", "desc", "Token 最多", "Token 最多"),
    SortOption("data_size", "asc", "Token 最少", "Token 最少"),
    SortOption("name", "random", "随机", "随机"),
)

/**
 * 官方排序语义 1:1：name 走名称比较；create_date / fav / date_last_chat / chat_size / data_size
 * 按官方 compareFunc（random 直接洗牌，fav 为 boolean 规则=收藏在前）。
 * 平局保持 store 顺序（置顶/最近导入在前），与官方稳定排序行为一致。
 */
private fun sortCharacters(
    list: List<CharacterRecord>,
    field: String,
    order: String,
    activity: Map<String, Pair<Long, Int>>,
    meta: Map<String, Pair<Int, Long>>,
): List<CharacterRecord> = when {
    order == "random" -> list.shuffled()
    field == "name" ->
        if (order == "desc") list.sortedByDescending { it.name.lowercase() }
        else list.sortedBy { it.name.lowercase() }
    field == "create_date" ->
        if (order == "desc") list.sortedByDescending { meta[it.id]?.second ?: 0L }
        else list.sortedBy { meta[it.id]?.second ?: 0L }
    field == "fav" -> list.sortedByDescending { it.pinned }
    field == "date_last_chat" -> list.sortedByDescending { activity[it.id]?.first ?: 0L }
    field == "chat_size" ->
        if (order == "desc") list.sortedByDescending { activity[it.id]?.second ?: 0 }
        else list.sortedBy { activity[it.id]?.second ?: 0 }
    field == "data_size" ->
        if (order == "desc") list.sortedByDescending { meta[it.id]?.first ?: 0 }
        else list.sortedBy { meta[it.id]?.first ?: 0 }
    else -> list
}

/** 排序选项行：当前项强调色 + 对勾，无图标噪音（Power Space 单选范式）。 */
@Composable
private fun SortOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (selected) c.accent else c.ink,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(FaIcons.Check, contentDescription = "当前排序", tint = c.accent, modifier = Modifier.size(14.dp))
        }
    }
}


private fun displayName(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
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
