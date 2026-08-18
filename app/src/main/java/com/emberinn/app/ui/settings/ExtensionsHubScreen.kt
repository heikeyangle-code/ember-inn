package com.emberinn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.icons.PhosphorIcons

private data class ExtensionEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** 扩展中心（官方 div_extensions 移植）：各扩展面板入口。 */
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
    onOpenInteractive: () -> Unit,
    onOpenAuthorsNote: () -> Unit,
    onOpenData: () -> Unit,
) {
    val entries = listOf(
        ExtensionEntry("翻译 · 图像 · 向量服务", "第三方服务 Key 与开关", PhosphorIcons.Translate, onOpenServices),
        ExtensionEntry("语音朗读 TTS", "系统 TTS · 自动朗读", PhosphorIcons.SpeakerHigh, onOpenVoice),
        ExtensionEntry("快捷回复", "一键执行斜杠命令组", PhosphorIcons.Lightning, onOpenQuickReplies),
        ExtensionEntry("向量记忆", "数据银行 · 长期记忆", PhosphorIcons.ChartBar, onOpenMemory),
        ExtensionEntry("图像说明", "本地模型为图片生成描述", PhosphorIcons.ImageSquare, onOpenCaption),
        ExtensionEntry("表情分类", "角色立绘表情切换", PhosphorIcons.MaskHappy, onOpenExpression),
        ExtensionEntry("正则脚本", "输入/输出文本正则替换", PhosphorIcons.GitBranch, onOpenRegex),
        ExtensionEntry("互动模式", "扩展互动配置", PhosphorIcons.Sparkle, onOpenInteractive),
        ExtensionEntry("作者注", "固定注入提示词与深度", PhosphorIcons.Edit, onOpenAuthorsNote),
        ExtensionEntry("数据管理", "导出 · 备份 · 清除", PhosphorIcons.Folder, onOpenData),
    )

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "扩展", subtitle = "翻译 · 图像 · 向量 · TTS · 快捷回复 · 正则 …", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.title }) { entry ->
                    ExtensionCard(entry)
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(entry: ExtensionEntry) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = entry.onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(PhosphorIcons.CaretRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
