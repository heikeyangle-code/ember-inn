@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.sessions

import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.components.EmptyState
import com.emberinn.app.ui.components.EmberMenuRow as SheetRow
import com.emberinn.app.ui.components.EmberHaptics
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.emberGlass
import com.emberinn.app.ui.design.EmberTheme

import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.AvatarCircle
import com.emberinn.app.ui.icons.FaIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.skydoves.cloudy.sky
import com.emberinn.engine.group.GroupGenerationMode
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 聊天 Tab：全部会话（按时间，置顶优先）+ 新建对话；长按 = 置顶 / 导出 / 删除。 */
@Composable
fun SessionsScreen(
    onOpenSession: (SessionRecord) -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsState()
    val characters by vm.characters.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var showGroupSheet by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupMemberIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var groupMode by rememberSaveable { mutableStateOf(GroupGenerationMode.APPEND) }
    var groupStrategy by rememberSaveable { mutableStateOf("natural") }
    var menuSession by remember { mutableStateOf<SessionRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionRecord?>(null) }
    var renameTarget by remember { mutableStateOf<SessionRecord?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    // 每次进入聊天 Tab / 从聊天页返回都刷新（时间、置顶、最后消息预览）
    LaunchedEffect(Unit) { vm.refresh() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { u ->
            val session = menuSession
            if (session != null) {
                val text = vm.exportJsonl(session.id)
                if (text == null) {
                    Toast.makeText(context, "这条会话还没有消息，无内容可导出", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        context.contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
                        Toast.makeText(context, "已导出：${session.name}.jsonl", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // 搜索 + 归档筛选：默认隐藏归档故事（官方 archive_chats），可切换查看/恢复
    val visibleSessions = remember(sessions, search, showArchived) {
        sessions
            .filter { if (showArchived) true else !it.archived }
            .filter { s ->
                search.isBlank() || s.name.contains(search.trim(), true) ||
                    (vm.previewOf(s.id)?.contains(search.trim(), true) == true)
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(EmberTheme.colors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "对话",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = EmberTheme.colors.ink,
                    letterSpacing = 1.sp,
                )
                if (sessions.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        ShellInput(
                            value = search,
                            onValueChange = { search = it },
                            label = "搜索故事 / 消息",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        if (sessions.any { it.archived }) {
                            ShellChip("已归档", selected = showArchived) { showArchived = !showArchived }
                        }
                    }
                }
            }

            if (visibleSessions.isEmpty()) {
                if (sessions.isEmpty()) {
                    EmptySessions(onNew = { showNewSheet = true })
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (showArchived) "没有已归档的故事" else "没有匹配「${search.trim()}」的故事",
                            color = EmberTheme.colors.lineStrong,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleSessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            character = characters.firstOrNull { it.id == session.characterId },
                            preview = remember(session) { vm.previewOf(session.id) },
                            onClick = { EmberHaptics.select(haptic); onOpenSession(session) },
                            onMenu = { menuSession = session },
                        )
                    }
                }
            }
        }

        // 新建对话圆粒：与 FloatHub 同语言（内容面 + 发丝缘）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(EmberTheme.colors.surface.copy(alpha = 0.96f))
                .border(1.dp, EmberTheme.colors.lineStrong, CircleShape)
                .clickable { EmberHaptics.select(haptic); showNewSheet = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(FaIcons.Plus, contentDescription = "新建对话", tint = EmberTheme.colors.ink, modifier = Modifier.size(18.dp))
        }
    }

    if (showNewSheet) {
        NewChatSheet(
            characters = characters,
            onPick = { character ->
                showNewSheet = false
                val session = vm.newSession(character?.id, character?.name ?: "AI 对话")
                onOpenSession(session)
            },
            onGroup = {
                showNewSheet = false
                groupName = ""
                groupMemberIds = emptySet()
                groupMode = GroupGenerationMode.APPEND
                groupStrategy = "natural"
                showGroupSheet = true
            },
            onDismiss = { showNewSheet = false },
        )
    }

    if (showGroupSheet) {
        AlertDialog(
            onDismissRequest = { showGroupSheet = false },
            title = { Text("新建群聊") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ShellInput(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = "群聊名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "生成模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row {
                        FilterChip(
                            selected = groupMode == GroupGenerationMode.APPEND,
                            onClick = { groupMode = GroupGenerationMode.APPEND },
                            label = { Text("全员依次（APPEND）") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = groupMode == GroupGenerationMode.SWAP,
                            onClick = { groupMode = GroupGenerationMode.SWAP },
                            label = { Text("轮流（SWAP）") },
                        )
                    }
                    Text(
                        "激活策略",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row {
                        ShellChip("natural（点名/话痨）", selected = groupStrategy == "natural") {
                            groupStrategy = "natural"
                        }
                        Spacer(Modifier.width(8.dp))
                        ShellChip("pooled（全体池）", selected = groupStrategy == "pooled") {
                            groupStrategy = "pooled"
                        }
                    }
                    Text(
                        "选择成员（至少 2 个）",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    characters.forEach { character ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                groupMemberIds = if (character.id in groupMemberIds) {
                                    groupMemberIds - character.id
                                } else {
                                    groupMemberIds + character.id
                                }
                            }.padding(vertical = 8.dp),
                        ) {
                            Text(
                                character.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (character.id in groupMemberIds) "✓" else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmberTheme.colors.accent,
                            )
                        }
                    }
                    if (characters.isEmpty()) {
                        Text(
                            "还没有角色卡，先去角色页导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.lineStrong,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val session = vm.newGroupSession(groupMemberIds.toList(), groupName, groupMode, groupStrategy)
                    showGroupSheet = false
                    if (session != null) onOpenSession(session)
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupSheet = false }) { Text("取消") }
            },
        )
    }

    menuSession?.let { session ->
        ShellSheet(onDismiss = { menuSession = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                SheetRow(FaIcons.Star, if (session.pinned) "取消置顶" else "置顶") {
                    vm.togglePin(session); menuSession = null
                }
                SheetRow(FaIcons.Pencil, "重命名") {
                    renameTarget = session; renameDraft = session.name; menuSession = null
                }
                SheetRow(FaIcons.BoxArchive, if (session.archived) "恢复故事" else "归档故事", subtitle = if (session.archived) "回到对话列表" else "从列表隐藏，可随时恢复") {
                    vm.setArchived(session, !session.archived); menuSession = null
                }
                SheetRow(FaIcons.FileExport, "导出聊天（JSONL）") {
                    exportLauncher.launch("${session.name}-${timeStamp(session.updatedAt)}.jsonl")
                }
                SheetRow(FaIcons.TrashCan, "删除会话", danger = true) {
                    deleteTarget = session; menuSession = null
                }
            }
        }
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${session.name}」？") },
            text = { Text("这条会话的全部消息都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    EmberHaptics.reject(haptic)
                    vm.delete(session); deleteTarget = null
                    Toast.makeText(context, "已删除：${session.name}", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                ShellInput(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = "故事名",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(session, renameDraft)
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionRecord,
    character: CharacterRecord?,
    preview: String?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    // 新语言：无卡片框的安静行——头像 + 名字 + 预览 + 时间；置顶星标用强调色，别无装饰
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        AvatarCircle(character?.avatarPath, session.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.name,
                    color = c.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        FaIcons.Star,
                        contentDescription = "置顶",
                        tint = c.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (session.archived) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        FaIcons.BoxArchive,
                        contentDescription = "已归档",
                        tint = c.inkMute,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview?.take(80) ?: "还没有消息，点开打个招呼吧",
                color = c.inkMute,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = timeLabel(session.updatedAt), color = c.inkMute, fontSize = 11.sp)
        Icon(
            FaIcons.EllipsisVertical,
            contentDescription = "更多",
            tint = c.inkMute,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onMenu)
                .padding(5.dp),
        )
    }
}

@Composable
private fun SessionAvatar(name: String, character: CharacterRecord?, seed: Color?) {
    val avatarFile = character?.avatarPath?.let { File(it) }?.takeIf { it.exists() }
    val corner = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(corner)
            .border(1.5.dp, (seed ?: EmberTheme.colors.accent).copy(alpha = 0.45f), corner),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(corner),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            if (seed != null) {
                                listOf(
                                    lerp(seed, EmberTheme.colors.surface, 0.55f),
                                    lerp(seed, EmberTheme.colors.surface, 0.80f),
                                )
                            } else {
                                listOf(EmberTheme.colors.surface, EmberTheme.colors.surface2)
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (name.isBlank()) {
                    Icon(
                        FaIcons.User,
                        contentDescription = null,
                        tint = seed ?: EmberTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = seed ?: EmberTheme.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewChatSheet(
    characters: List<CharacterRecord>,
    onPick: (CharacterRecord?) -> Unit,
    onGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    ShellSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = "新建对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            SheetRow(FaIcons.User, "AI 对话", subtitle = "不用角色卡，直接聊", onClick = { onPick(null) })
            if (characters.isNotEmpty()) {
                Text(
                    text = "选择一个角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmberTheme.colors.accent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                characters.forEach { character ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onPick(character) }).padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        CharacterAvatar(character)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(character.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                character.description.ifBlank { "角色卡" }.take(40),
                                style = MaterialTheme.typography.bodySmall,
                                color = EmberTheme.colors.inkMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            SheetRow(FaIcons.Plus, "新建群聊", subtitle = "勾选已有角色，按 APPEND/SWAP 轮流生成", onClick = onGroup)
        }
    }
}

@Composable
private fun CharacterAvatar(character: CharacterRecord) {
    val file = character.avatarPath?.let { File(it) }?.takeIf { it.exists() }
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
    } else {
        Surface(shape = CircleShape, color = EmberTheme.colors.surfaceSink, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    character.name.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EmberTheme.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun EmptySessions(onNew: () -> Unit) {
    EmptyState(
        title = "还没有会话",
        body = "新建一个对话，或去角色页点一张角色卡开始",
        actionLabel = "新建对话",
        onAction = onNew,
        icon = FaIcons.ListUl,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    )
}

private fun timeLabel(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDateTime.now(zone).toLocalDate()
    return when {
        dt.toLocalDate() == today -> dt.format(DateTimeFormatter.ofPattern("HH:mm"))
        dt.year == today.year -> dt.format(DateTimeFormatter.ofPattern("MM-dd"))
        else -> dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}

private fun timeStamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
