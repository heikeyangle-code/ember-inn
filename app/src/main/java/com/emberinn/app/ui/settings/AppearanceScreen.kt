package com.emberinn.app.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.ui.theme.ThemeMode
import com.emberinn.app.ui.theme.ThemePreset
import com.emberinn.app.ui.theme.ThemePresets
import com.emberinn.app.ui.settings.AppearancePrefs

/** 外观与主题：README 三层主题的第一层（全局）。选预设即全局实时生效。 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    themePreset: ThemePreset,
    onThemeChanged: (ThemeMode, ThemePreset) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "外观与主题", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("主题模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeChanged(mode, themePreset) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("预设主题", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "点选立即生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ThemePresets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.id == themePreset.id,
                    onClick = { onThemeChanged(themeMode, preset) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val appearanceContext = LocalContext.current
                var radius by remember { mutableStateOf(AppearancePrefs.radius(appearanceContext)) }
                var font by remember { mutableStateOf(AppearancePrefs.font(appearanceContext)) }
                Text("全局圆角", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)) {
                    listOf("default" to "系统", "square" to "方正 4dp", "rounded" to "圆润 16dp", "circle" to "浑圆 24dp").forEach { (v, label) ->
                        FilterChip(selected = radius == v, onClick = { radius = v; AppearancePrefs.save(appearanceContext, radius, font) }, label = { Text(label) })
                    }
                }
                Text("全局字体", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    listOf("default" to "系统", "serif" to "衬线（思源宋体近似）").forEach { (v, label) ->
                        FilterChip(selected = font == v, onClick = { font = v; AppearancePrefs.save(appearanceContext, radius, font) }, label = { Text(label) })
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val renderContext = LocalContext.current
                var htmlEnabled by remember { mutableStateOf(RenderPrefs.htmlEnabled(renderContext)) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { htmlEnabled = !htmlEnabled; RenderPrefs.setHtmlEnabled(renderContext, htmlEnabled) }
                        .padding(vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("HTML 消息（WebView 渲染）", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "消息含 HTML 标签时用 WebView 展示；Mermaid 代码块始终走 WebView 兜底",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = htmlEnabled, onCheckedChange = { htmlEnabled = it; RenderPrefs.setHtmlEnabled(renderContext, it) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ComingSoonCard()
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(preset.seed, preset.secondary, preset.tertiary).forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(color),
                    )
                    if (index < 2) Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                preset.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                preset.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ComingSoonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Text(
                "更多选项",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            listOf("字体", "圆角与密度", "背景模糊").forEachIndexed { index, label ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "开发中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
