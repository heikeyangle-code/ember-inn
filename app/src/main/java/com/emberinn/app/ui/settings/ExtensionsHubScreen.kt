package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons

/** 扩展兼容状态（docs/HANDOFF.md §6.4）：仅「部分」需要显性提示，其余保持安静。 */
enum class ExtStatus { OK, PARTIAL, NATIVE }

/** 扩展中心（官方 div_extensions 移植）：各扩展面板入口，E0 行式。 */
@Composable
fun ExtensionsHubScreen(
    onBack: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenCaption: () -> Unit,
    onOpenExpression: () -> Unit,
    onOpenRegex: () -> Unit,
    onOpenTavernHelper: () -> Unit,
    onOpenAuthorsNote: () -> Unit,
    onOpenData: () -> Unit,
) {
    val entries = listOf(
        Triple("翻译 · 图像 · 向量服务", FaIcons.Language, onOpenServices),
        Triple("语音朗读 TTS", FaIcons.VolumeHigh, onOpenVoice),
        Triple("快捷回复", FaIcons.Brain, onOpenQuickReplies),
        Triple("向量记忆", FaIcons.Database, onOpenMemory),
        Triple("图像说明", FaIcons.Image, onOpenCaption),
        Triple("表情分类", FaIcons.FaceSmile, onOpenExpression),
        Triple("正则脚本", FaIcons.CodeBranch, onOpenRegex),
        Triple("酒馆助手", FaIcons.WandMagicSparkles, onOpenTavernHelper),
        Triple("作者注", FaIcons.Pencil, onOpenAuthorsNote),
        Triple("数据管理", FaIcons.Folder, onOpenData),
    )
    // 兼容状态投影（HANDOFF §6.4 登记表）：升级状态须先改登记表附验证方式
    val partial = setOf("酒馆助手")

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "扩展", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                items(entries, key = { it.first }) { entry ->
                    ExtensionRow(entry.first, entry.second, partial.contains(entry.first), entry.third)
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(title: String, icon: ImageVector, isPartial: Boolean, onClick: () -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(title, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (isPartial) {
            Text("部分兼容", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(14.dp))
    }
}
