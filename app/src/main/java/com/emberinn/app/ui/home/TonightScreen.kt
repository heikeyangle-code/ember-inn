package com.emberinn.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.AvatarCircle
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.HeroCard
import com.emberinn.app.ui.design.components.PosterTile
import com.emberinn.app.ui.design.components.RowLine
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 今夜主页（DESIGN_SYSTEM.md §4.1 定稿布局）：
 * 时钟问候 → 续读英雄卡 → 角色海报横排 → 对话时间线。无卡片框堆叠，留白即分隔。
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
        Spacer(Modifier.height(20.dp))
        Text(clock, color = c.inkMute, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(greeting, color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)

        val hero = sessions.firstOrNull()
        if (hero != null) {
            val heroChar = hero.characterId?.let { id -> characters.firstOrNull { it.id == id } }
            Spacer(Modifier.height(18.dp))
            HeroCard(
                title = heroChar?.name ?: hero.name,
                preview = vm.lastMessage(hero.id),
                caption = agoLabel(hero.updatedAt),
                avatarPath = heroChar?.avatarPath,
                onClick = { onOpenChat(hero) },
            )
        }

        GroupLabel("角色")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            characters.take(8).forEach { record ->
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

        GroupLabel("对话")
        sessions.take(7).forEach { session ->
            val sessionChar = session.characterId?.let { id -> characters.firstOrNull { it.id == id } }
            RowLine(
                title = sessionChar?.name ?: session.name,
                value = agoLabel(session.updatedAt),
                leading = { AvatarCircle(sessionChar?.avatarPath, session.name, 28.dp) },
                onClick = { onOpenChat(session) },
            )
        }
        if (sessions.isEmpty()) {
            Text("还没有对话——从上面的角色开始，或直接开一段新聊天", color = c.inkMute, fontSize = 13.sp)
        }

        Spacer(Modifier.height(150.dp))
    }
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / M月d日。 */
private fun agoLabel(ts: Long): String {
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
