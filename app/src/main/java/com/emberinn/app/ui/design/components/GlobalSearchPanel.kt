package com.emberinn.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons

/** 全域搜索的统一结果行。 */
data class SearchEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val group: String,
    val onOpen: () -> Unit,
)

/**
 * 全域命令面板（DESIGN_SYSTEM §三/§4.2）：长按悬浮主钮唤起，
 * 一个输入框过滤 角色 / 对话 / 世界书 / 设置项，任意设置项 ≤2 次操作直达。
 * E2 模态：主题底色垫底 + 发丝缘，无半透明赌注。
 */
@Composable
fun GlobalSearchPanel(
    entriesProvider: (String) -> List<SearchEntry>,
    onDismiss: () -> Unit,
) {
    val c = EmberTheme.colors
    var query by remember { mutableStateOf("") }
    val results = remember(query) { entriesProvider(query.trim()).take(12) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.bg)
                .border(1.dp, c.lineStrong, RoundedCornerShape(18.dp)),
        ) {
            Column(Modifier.padding(14.dp)) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜角色 / 对话 / 世界书 / 设置",
                )
                if (query.isBlank()) {
                    Text(
                        "输入以检索全部内容；斜杠命令也可直达",
                        color = c.inkMute,
                        fontSize = EmberTheme.typo.meta.fontSize,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                    )
                } else if (results.isEmpty()) {
                    Text("没有匹配结果", color = c.inkMute, fontSize = EmberTheme.typo.bodySmall.fontSize, modifier = Modifier.padding(start = 6.dp, top = 14.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        results.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = entry.onOpen)
                                    .padding(horizontal = 6.dp, vertical = 9.dp),
                            ) {
                                Icon(entry.icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, color = c.ink, fontSize = EmberTheme.typo.body.fontSize)
                                    Text(entry.subtitle, color = c.inkMute, fontSize = EmberTheme.typo.meta.fontSize, maxLines = 1)
                                }
                                Text(entry.group, color = c.ink.copy(alpha = 0.30f), fontSize = EmberTheme.typo.micro.fontSize)
                            }
                        }
                    }
                }
            }
        }
    }
}
