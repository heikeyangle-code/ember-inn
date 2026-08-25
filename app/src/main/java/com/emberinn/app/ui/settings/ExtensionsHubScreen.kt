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
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons

private data class ExtensionEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

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
        ExtensionEntry("翻译 · 图像 · 向量服务", "第三方服务 Key 与开关", FaIcons.Language, onOpenServices),
        ExtensionEntry("语音朗读 TTS", "系统 TTS · 自动朗读", FaIcons.VolumeHigh, onOpenVoice),
        ExtensionEntry("快捷回复", "一键执行斜杠命令组", FaIcons.Brain, onOpenQuickReplies),
        ExtensionEntry("向量记忆", "数据银行 · 长期记忆", FaIcons.Database, onOpenMemory),
        ExtensionEntry("图像说明", "本地模型为图片生成描述", FaIcons.Image, onOpenCaption),
        ExtensionEntry("表情分类", "角色立绘表情切换", FaIcons.FaceSmile, onOpenExpression),
        ExtensionEntry("正则脚本", "输入/输出文本正则替换", FaIcons.CodeBranch, onOpenRegex),
        ExtensionEntry("酒馆助手", "前端卡脚本沙箱 · MVU 变量框架", FaIcons.WandMagicSparkles, onOpenTavernHelper),
        ExtensionEntry("作者注", "固定注入提示词与深度", FaIcons.Pencil, onOpenAuthorsNote),
        ExtensionEntry("数据管理", "导出 · 备份 · 清除", FaIcons.Folder, onOpenData),
    )
    // 兼容状态投影（HANDOFF §6.4 登记表）：升级状态须先改登记表附验证方式
    val partial = setOf("酒馆助手")

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "扩展", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = settingsPagePadding(),
            ) {
                items(entries, key = { it.title }) { entry ->
                    ExtensionRow(entry, partial.contains(entry.title))
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(entry: ExtensionEntry, isPartial: Boolean) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize)
            Text(entry.subtitle, color = c.inkMute, fontSize = EmberTheme.typo.caption.fontSize)
        }
        if (isPartial) {
            Text("部分兼容", color = c.accent, fontSize = EmberTheme.typo.meta.fontSize, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(14.dp))
    }
}
