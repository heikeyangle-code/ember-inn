package com.emberinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.icons.PhosphorIcons

/** 颜色字段（README UI 质感升级）：大色块（彩色阴影 + 白边内描边）+ 标签 + hex 等宽预览 + 编辑按钮 + hex 输入。
 *  整行可点开选色盘；选色盘支持 #RRGGBB / #AARRGGBB / 3 位简写。
 *  fallback = 当前主题默认值：字段留空时显示主题默认（跟随主题），换主题即时更新。 */
@Composable
fun ColorField(label: String, hint: String, value: String, onSave: (String) -> Unit, fallback: Color? = null) {
    var draft by remember(label, value) { mutableStateOf(value) }
    var showPicker by remember { mutableStateOf(false) }
    val current = parseHexColor(draft)
    val effective = current ?: fallback
    val swatchShape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showPicker = true }
                .padding(vertical = 2.dp),
        ) {
            // 大色块：当前色 + 彩色阴影 + 白边内描边（高级感），点击即选色
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(swatchShape)
                    .emberShadow(
                        color = (effective ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.4f),
                        radius = 9.dp,
                        spread = 1.dp,
                        offset = DpOffset(0.dp, 3.dp),
                        alpha = 0.45f,
                    )
                    .background(
                        effective ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), swatchShape),
                contentAlignment = Alignment.Center,
            ) {
                if (current == null) {
                    Text("—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(
                    when {
                        current != null -> draft.ifBlank { "#RRGGBB" }
                        fallback != null -> fallback.toHex() + " · 跟随主题"
                        else -> "跟随主题"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Icon(
                PhosphorIcons.Edit,
                contentDescription = "选色",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
        }
        EmberTextField(
            value = draft,
            onValueChange = { draft = it; onSave(it) },
            placeholder = { Text("#RRGGBB") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (showPicker) {
        ColorPickerDialog(
            title = label,
            initial = effective,
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                draft = color.toHex()
                onSave(draft)
                showPicker = false
            },
        )
    }
}
