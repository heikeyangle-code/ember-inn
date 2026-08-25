@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.components.edgeSwipeBack
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.RailHeader
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.components.StoryCard
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.settings.BehaviorPrefs
import java.io.File

/**
 * 角色主页（UI_REDESIGN_V3 §三 Companion Space · §4.4 幕布式）：
 * Hero 头图渐隐 + 头像压缝 → 强主操作（继续/开始故事）→ 故事轨道（一角色多故事）
 * → 身份区（描述/性格/场景/标签）→ 编辑器入口（Power Space 在另一屏）。
 * 视觉主体是角色本身；技术字段（正则/变量/模型覆盖/世界书）全部留在编辑器，不在这里堆砌。
 */
@Composable
fun CharacterHomeScreen(
    record: CharacterRecord,
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenChat: (SessionRecord) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes

    val fields = remember(record.id, record.rawJson) { vm.readCharacterFields(record) }
    val entries = remember(record.id, record.rawJson) { vm.readWorldEntries(record) }
    var sessions by remember(record.id) { mutableStateOf(vm.sessionsForCharacter(record.id)) }
    // 从聊天返回时重取（本屏离开组合后重组会重跑这里）
    LaunchedEffect(Unit) { sessions = vm.sessionsForCharacter(record.id) }

    var showActions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 官方 spoiler_free_mode：描述默认遮蔽防剧透，点击 peek
    val spoilerFree = remember { BehaviorPrefs.load(context).spoilerFreeMode }
    var spoilerPeeked by remember(record.id) { mutableStateOf(false) }

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

    fun openMostRecent() {
        val latest = sessions.firstOrNull()
        if (latest != null) {
            onOpenChat(latest)
        } else {
            val s = vm.newSession(record.id, fields.name.ifBlank { record.name })
            sessions = listOf(s) + sessions
            onOpenChat(s)
        }
    }

    BackHandler(onBack = onBack)
    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack(onBack = onBack).background(c.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 幕布式英雄区：头图渐隐入页面底，头像压缝 + 名字 + 简介折叠 + 强主操作 ----
            com.emberinn.app.ui.design.components.CharacterHeroBlock(
                name = fields.name.ifBlank { record.name },
                avatarPath = record.avatarPath?.takeIf { File(it).exists() },
                imagePath = (record.backgroundPath ?: record.avatarPath)?.takeIf { File(it).exists() },
                subtitle = if (spoilerFree && !spoilerPeeked) {
                    if (fields.description.isBlank()) null else "描述已隐藏（防剧透模式）"
                } else {
                    fields.description.ifBlank { null }
                },
                primaryLabel = if (sessions.isEmpty()) "开始对话" else "继续故事",
                onPrimary = ::openMostRecent,
                meta = {
                    com.emberinn.app.ui.design.components.StatBadges(
                        buildList {
                            if (sessions.isNotEmpty()) add("${sessions.size}" to "段故事")
                            if (entries.isNotEmpty()) add("${entries.size}" to "条世界书")
                            if (fields.alternateGreetings.isNotEmpty()) add("${fields.alternateGreetings.size}" to "个开场白")
                        },
                    )
                },
            )

            // ---- 次操作行：开新故事（明示一角色多故事）+ 编辑角色（进 Power Space 编辑器） ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                GhostAction(
                    label = "开新故事",
                    modifier = Modifier.weight(1f),
                ) {
                    val s = vm.newSession(record.id, fields.name.ifBlank { record.name })
                    sessions = listOf(s) + sessions
                    onOpenChat(s)
                }
                Spacer(Modifier.width(10.dp))
                GhostAction(
                    label = "编辑角色",
                    modifier = Modifier.weight(1f),
                    onClick = onEdit,
                )
            }

            // ---- 故事轨道：我和这个角色经历过的故事（最近在前） ----
            RailHeader("故事")
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (sessions.isEmpty()) {
                    Text(
                        "还没有故事。点上面的开始对话，写下你们的第一句。",
                        color = c.inkMute,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                } else {
                    sessions.forEach { session ->
                        StoryCard(
                            title = session.name,
                            preview = vm.lastMessage(session.id),
                            caption = agoLabel(session.updatedAt),
                            avatarPath = record.avatarPath?.takeIf { File(it).exists() },
                            badge = if (session.pinned) "置顶" else null,
                            onClick = { onOpenChat(session) },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // ---- 身份区：描述 / 性格 / 场景（编辑排版，留白分组，不套卡片） ----
            if (fields.description.isNotBlank() || fields.personality.isNotBlank() || fields.scenario.isNotBlank()) {
                RailHeader("关于")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (fields.description.isNotBlank()) {
                        if (spoilerFree && !spoilerPeeked) {
                            SpoilerPeek { spoilerPeeked = true }
                        } else {
                            Text(
                                fields.description,
                                color = c.ink.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                    if (fields.personality.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("性格", color = c.inkMute, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(fields.personality, color = c.ink.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 22.sp)
                    }
                    if (fields.scenario.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("场景", color = c.inkMute, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(fields.scenario, color = c.ink.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }

            // ---- 标签：官方 tags（逗号拆分） ----
            val tags = fields.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                com.emberinn.app.ui.design.components.SectionRail(contentPadding = 20.dp) {
                    tags.take(12).forEach { tag -> TagChip(tag) }
                }
            }

            // ---- 创作信息（弱墨脚注，不做管理后台观感） ----
            val creator = fields.creator.ifBlank { null }
            val version = fields.characterVersion.ifBlank { null }
            if (creator != null || version != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    listOfNotNull(creator?.let { "作者 $it" }, version?.let { "版本 $it" }).joinToString(" · "),
                    color = c.ink.copy(alpha = 0.34f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            Spacer(Modifier.height(150.dp))
        }

        // ---- 浮动顶栏：返回 + 动作菜单（贴幕布之上，随滚动淡出由幕布渐隐自然承接） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 10.dp, top = 10.dp),
        ) {
            Icon(
                FaIcons.ChevronLeft,
                contentDescription = "返回",
                tint = c.ink,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.bg.copy(alpha = 0.35f))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                FaIcons.EllipsisVertical,
                contentDescription = "更多",
                tint = c.ink,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.bg.copy(alpha = 0.35f))
                    .clickable { showActions = true }
                    .padding(6.dp),
            )
        }
    }

    // ---- 动作面板：编辑 / 导出 / 置顶 / 删除（低频动作收进 Sheet，不占顶栏） ----
    if (showActions) {
        ShellSheet(onDismiss = { showActions = false }, title = record.name) {
            SheetRow(icon = FaIcons.Pencil, label = "编辑角色", subtitle = "字段 / 世界书 / 正则 / 变量 / 模型覆盖") {
                showActions = false
                onEdit()
            }
            SheetRow(icon = FaIcons.ShareNodes, label = "导出 JSON", subtitle = "官方 v2 角色卡格式") {
                showActions = false
                exportLauncher.launch("${record.name}.json")
            }
            SheetRow(
                icon = FaIcons.Star,
                label = if (record.pinned) "取消置顶" else "置顶",
                subtitle = "置顶后在今夜与角色库优先展示",
            ) {
                showActions = false
                vm.togglePin(record)
            }
            SheetRow(icon = FaIcons.TrashCan, label = "删除角色", danger = true) {
                showActions = false
                confirmDelete = true
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${record.name}」？") },
            text = { Text("角色和它的全部故事都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(record)
                    onBack()
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 次操作幽灵钮：内容面 + 主题圆角（视觉权重低于唯一强主操作）。 */
@Composable
private fun GhostAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** 剧透遮蔽行：官方 spoiler_free_mode 的 peek 交互。 */
@Composable
private fun SpoilerPeek(onPeek: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .clickable(onClick = onPeek)
            .padding(14.dp),
    ) {
        Text(
            "描述已隐藏（防剧透模式）· 点击显示",
            color = c.inkMute,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 静态标签胶囊：官方 tags 展示（无交互，弱墨描边）。 */
@Composable
private fun TagChip(text: String) {
    val c = EmberTheme.colors
    Text(
        text,
        color = c.inkMute,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}
