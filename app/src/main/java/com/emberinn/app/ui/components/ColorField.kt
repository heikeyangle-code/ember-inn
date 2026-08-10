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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.icons.PhosphorIcons

/** 颜色字段：色块（即选色入口）+ 标签 + hex 等宽预览 + 编辑按钮 + hex 输入。
 *  选色盘支持 #RRGGBB / #AARRGGBB / 3 位简写。
 *  fallback = 当前主题默认值：字段留空时显示主题默认（跟随主题），换主题即时更新。 */
@Composable
fun ColorField(label: String, hint: String, value: String, onSave: (String) -> Unit, fallback: androidx.compose.ui.graphics.Color? = null) {
    var draft by remember(label, value) { mutableStateOf(value) }
    var showPicker by remember { mutableStateOf(false) }
    val current = parseHexColor(draft)
    val effective = current ?: fallback
    val swatchShape = RoundedCornerShape(8.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(swatchShape)
                    .background(
                        effective ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, swatchShape)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                if (current == null) {
                    Text("—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
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
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(34.dp)) {
                Icon(
                    PhosphorIcons.Edit,
                    contentDescription = "选色",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        OutlinedTextField(
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
