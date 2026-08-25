package com.emberinn.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.AvatarCircle
import com.emberinn.app.ui.design.components.PosterTile
import com.emberinn.app.ui.design.components.RailHeader
import com.emberinn.app.ui.design.components.SectionRail
import com.emberinn.app.ui.design.components.StoryCard
import com.emberinn.app.ui.icons.FaIcons
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 今夜主页（UI_REDESIGN_V3 §三 Companion Space · Editorial 定稿）：
 * 时段问候 Display → 续读英雄故事 → 角色海报轨道（收藏优先）→ 故事时间线（今晚/昨晚分组）。
 * 无 Dashboard 化：设置/API 一律不上首页；非对称层级=英雄卡 > 轨道 > 时间线。
 */
@Composable
fun TonightScreen(
    vm: HomeViewModel,
    onOpenChat: (SessionRecord) -> Unit,
    onOpenDetail: (CharacterRecord) -> Unit,
    onGoLibrary: () -> Unit,
) {
    val c = EmberTheme.colors
    val characters by vm.characters.collectAsState()
    val sessions by vm.recentSessions.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    val calendar = remember { Calendar.getInstance() }
    val hour = remember { calendar.get(Calendar.HOUR_OF_DAY) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.US).format(calendar.time) }
    val greeting = when {
        hour < 5 -> "夜深了"
        hour < 9 -> "早上好"
        hour < 12 -> "上午好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        hour < 23 -> "晚上好"
        else -> "夜深了"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // ---- Editorial 页头：时钟弱墨 + 问候 Display Light ----
        Spacer(Modifier.height(18.dp))
        Text(clock, color = c.inkMute, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(2.dp))
        Text(greeting, color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 0.4.sp, lineHeight = 40.sp)

        // ---- 英雄故事：最近会话（大头像 + 两行预览 + 时间） ----
        val hero = sessions.firstOrNull()
        if (hero != null) {
            val heroChar = hero.characterId?.let { id -> characters.firstOrNull { it.id == id } }
            Spacer(Modifier.height(18.dp))
            HeroStory(
                record = hero,
                character = heroChar,
                preview = vm.lastMessage(hero.id),
                timeLabel = agoLabel(hero.updatedAt),
                onClick = { onOpenChat(hero) },
            )
        }

        // ---- 收藏角色轨道（pinned 优先置前） ----
        val pinned = characters.filter { it.pinned }
        if (pinned.isNotEmpty()) {
            RailHeader("收藏的角色")
            SectionRail {
                pinned.forEach { record ->
                    PosterTile(
                        name = record.name,
                        avatarPath = record.avatarPath,
                        width = 92.dp,
                        aspect = 0.70f,
                        onClick = { onOpenDetail(record) },
                    )
                }
            }
        }

        // ---- 角色轨道：全部（收藏在前），末位导入幽灵位 ----
        RailHeader("角色", onSeeAll = onGoLibrary)
        SectionRail {
            val ordered = characters.sortedByDescending { it.pinned }
            ordered.take(10).forEach { record ->
                PosterTile(
                    name = record.name,
                    avatarPath = record.avatarPath,
                    width = 92.dp,
                    aspect = 0.70f,
                    onClick = { onOpenDetail(record) },
                )
            }
            PosterTile(name = "导入角色卡", avatarPath = null, width = 92.dp, onClick = onGoLibrary, ghost = true)
        }

        // ---- 故事时间线：今晚 / 昨晚 分组（置顶在最前） ----
        val rest = sessions.drop(1)
        if (rest.isNotEmpty()) {
            RailHeader("故事")
            val (tonight, earlier) = rest.partition { isToday(it.updatedAt) }
            tonight.forEach { session ->
                StoryLine(session, characters, vm, onOpenChat)
            }
            if (tonight.isNotEmpty() && earlier.isNotEmpty()) {
                GroupDivider("更早")
            }
            earlier.forEach { session ->
                StoryLine(session, characters, vm, onOpenChat)
            }
        }
        if (sessions.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "还没有对话——从上面的角色开始，或直接开一段新聊天",
                color = c.inkMute,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }

        Spacer(Modifier.height(150.dp))
    }
}

/** 英雄故事卡：Companion Space 视觉主体（面=内容面，弱化边框，留白即层级）。 */
@Composable
private fun HeroStory(
    record: SessionRecord,
    character: CharacterRecord?,
    preview: String?,
    timeLabel: String,
    onClick: () -> Unit,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        HeroAvatar(character?.avatarPath, record.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    character?.name ?: record.name,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (record.pinned) {
                    Spacer(Modifier.width(7.dp))
                    Icon(FaIcons.Star, contentDescription = "置顶", tint = c.accent, modifier = Modifier.size(11.dp))
                }
            }
            if (!preview.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(preview, color = c.inkMute, fontSize = 13.sp, maxLines = 2, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(timeLabel, color = c.ink.copy(alpha = 0.34f), fontSize = 11.sp, letterSpacing = 0.6.sp)
        }
    }
}

/** 英雄头像：52dp 人圆 + 主题环。 */
@Composable
private fun HeroAvatar(avatarPath: String?, name: String) {
    val c = EmberTheme.colors
    if (avatarPath != null) {
        Box {
            AvatarCircle(avatarPath, name, 52.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(c.bg),
            )
        }
    } else {
        AvatarCircle(null, name, 52.dp)
    }
}

/** 时间线故事行：轻量 Row 式（低于英雄卡的视觉权重）。 */
@Composable
private fun StoryLine(
    session: SessionRecord,
    characters: List<CharacterRecord>,
    vm: HomeViewModel,
    onOpenChat: (SessionRecord) -> Unit,
) {
    val sessionChar = session.characterId?.let { id -> characters.firstOrNull { it.id == id } }
    StoryCard(
        title = sessionChar?.name ?: session.name,
        preview = vm.lastMessage(session.id),
        caption = agoLabel(session.updatedAt),
        avatarPath = sessionChar?.avatarPath,
        badge = if (session.pinned) "置顶" else null,
        onClick = { onOpenChat(session) },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** 分组弱分隔：小字距标签（无线，留白分组）。 */
@Composable
private fun GroupDivider(label: String) {
    val c = EmberTheme.colors
    Text(
        label.uppercase(),
        color = c.ink.copy(alpha = 0.30f),
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp),
    )
}

/** 是否今天。 */
private fun isToday(ts: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / M月d日。 */
internal fun agoLabel(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val minutes = diff / 60000L
    if (minutes < 1) return "刚刚"
    if (minutes < 60) return minutes.toString() + " 分钟前"
    val hours = minutes / 60L
    if (hours < 24) return hours.toString() + " 小时前"
    if (hours < 48) return "昨天"
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    return (cal.get(Calendar.MONTH) + 1).toString() + "月" + cal.get(Calendar.DAY_OF_MONTH).toString() + "日"
}
