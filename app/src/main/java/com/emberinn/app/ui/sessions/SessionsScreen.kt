@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.sessions

import com.emberinn.app.ui.components.EmberEmptyState
import com.emberinn.app.ui.components.EmberGlassFab
import com.emberinn.app.ui.components.EmberHaptics
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.glassEdgeHighlight
import com.emberinn.app.ui.theme.LocalThemePreset

import com.emberinn.app.ui.icons.PhosphorIcons
import com.emberinn.app.ui.settings.AppearancePrefs
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.components.EmberTextField
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import com.emberinn.app.ui.components.EmberBottomSheet
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

    val sky = rememberSky()
    val glassDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier = Modifier.fillMaxSize()) {
        // 静态背景层：聊天列表毛玻璃顶栏的静态模糊源（列表滚动不触发整屏重捕）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(MaterialTheme.colorScheme.background),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEdgeHighlight(dark = glassDark, atTop = false)
                    .then(
                        if (AppearancePrefs.backgroundBlur(context)) {
                            Modifier.cloudy(sky = sky, radius = AppearancePrefs.blurStrength(context).coerceAtLeast(1), tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surface)
                        },
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
                    Text(
                        text = "聊天",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "全部会话 · 长按可置顶 / 导出 / 删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (sessions.isEmpty()) {
                EmptySessions(onNew = { showNewSheet = true })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp * LocalThemePreset.current.spacing),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(sessions, key = { it.id }) { session ->
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

        EmberGlassFab(
            icon = PhosphorIcons.Plus,
            contentDescription = "新建对话",
            onClick = { EmberHaptics.select(haptic); showNewSheet = true },
            sky = sky,
            dark = glassDark,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
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
                    EmberTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("群聊名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "生成模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row {
                        FilterChip(
                            selected = groupStrategy == "natural",
                            onClick = { groupStrategy = "natural" },
                            label = { Text("natural（点名/话痨）") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = groupStrategy == "pooled",
                            onClick = { groupStrategy = "pooled" },
                            label = { Text("pooled（全体池）") },
                        )
                    }
                    Text(
                        "选择成员（至少 2 个）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (characters.isEmpty()) {
                        Text(
                            "还没有角色卡，先去角色页导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
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
        EmberBottomSheet(onDismissRequest = { menuSession = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                SheetRow(PhosphorIcons.Star, if (session.pinned) "取消置顶" else "置顶") {
                    vm.togglePin(session); menuSession = null
                }
                SheetRow(PhosphorIcons.Share, "导出聊天（JSONL）") {
                    exportLauncher.launch("${session.name}-${timeStamp(session.updatedAt)}.jsonl")
                }
                SheetRow(PhosphorIcons.Delete, "删除会话", danger = true) {
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
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
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
    // 每张会话卡都带角色 seed：卡片底 tint + 左侧 seed 竖条 + 彩色发光阴影（与首页角色卡同一套语言）
    val seed = character?.seedColor?.let { Color(it.toInt()) }
    val corner = RoundedCornerShape(18.dp)
    val glow = com.emberinn.app.ui.theme.LocalVibe.current.glow
    val shadowBase = seed ?: MaterialTheme.colorScheme.primary
    val avatarFile = character?.avatarPath?.let { File(it) }?.takeIf { it.exists() }
    Surface(
        shape = corner,
        color = seed?.let { lerp(it, MaterialTheme.colorScheme.surfaceContainerLow, 0.90f) }
            ?: MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(corner)
            .emberShadow(
                brush = Brush.verticalGradient(
                    listOf(
                        shadowBase.copy(alpha = 0.26f * glow),
                        shadowBase.copy(alpha = 0.06f * glow),
                        Color.Transparent,
                    ),
                ),
                radius = 12.dp,
                spread = 1.dp,
                offset = DpOffset(0.dp, 5.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
    ) {
        Box {
            // 角色头像作为整卡淡背景（每张卡都不一样）；无头像时保留 seed tint 底
            if (avatarFile != null) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.15f,
                    modifier = Modifier.matchParentSize().clip(corner),
                )
            }
            // 左侧 seed 竖条：每卡专属色的视觉锚点
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(shadowBase, shadowBase.copy(alpha = 0.15f)),
                        ),
                    ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            ) {
                SessionAvatar(name = session.name, character = character, seed = seed)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = seed ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (session.pinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                PhosphorIcons.Star,
                                contentDescription = "置顶",
                                tint = seed ?: MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = preview?.take(80) ?: "还没有消息，点开打个招呼吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timeLabel(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                    Icon(PhosphorIcons.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                }
            }
        }
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
            .border(1.5.dp, (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.45f), corner),
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
                                    lerp(seed, MaterialTheme.colorScheme.surfaceContainerLow, 0.55f),
                                    lerp(seed, MaterialTheme.colorScheme.surfaceContainerLow, 0.80f),
                                )
                            } else {
                                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).ifBlank { "✦" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = seed ?: MaterialTheme.colorScheme.primary,
                )
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
    EmberBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = "新建对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            SheetRow(PhosphorIcons.Person, "AI 对话", subtitle = "不用角色卡，直接聊", onClick = { onPick(null) })
            if (characters.isNotEmpty()) {
                Text(
                    text = "选择一个角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            SheetRow(PhosphorIcons.Plus, "新建群聊", subtitle = "勾选已有角色，按 APPEND/SWAP 轮流生成", onClick = onGroup)
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
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    character.name.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptySessions(onNew: () -> Unit) {
    EmberEmptyState(
        title = "还没有会话",
        body = "新建一个对话，或去角色页点一张角色卡开始",
        actionLabel = "新建对话",
        onAction = onNew,
        icon = PhosphorIcons.List,
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
