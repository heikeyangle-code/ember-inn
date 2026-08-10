package com.emberinn.app.ui.components

import com.emberinn.app.ui.components.EmberTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 常用色板：官方 SillyTavern 8 色 + 标准中性/彩色。 */
private val Palettes: List<Color> = listOf(
    Color(0xFFDCDCD2), Color(0xFF919191), Color(0xFFBCE7CF), Color(0xFFE18A24),
    Color(0xFF171717), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF3C3C3C),
    Color(0xFFB23A2A), Color(0xFF2E7D6B), Color(0xFFC98A2B), Color(0xFFC73E2B),
    Color(0xFF5B6CFF), Color(0xFF5A5A5E), Color(0xFF4E8D6C), Color(0xFF7C5CBF),
    Color(0xFF5E7FA3), Color(0xFFC96A8C), Color(0xFF8A5A44), Color(0xFFB08A3E),
)

/**
 * 选色盘：色板点选 + RGB 滑杆 + hex 输入 + 预览。
 * 用于消息渲染（官方字段）设置页。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color?,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var red by remember { mutableFloatStateOf(initial?.red ?: 0.6f) }
    var green by remember { mutableFloatStateOf(initial?.green ?: 0.6f) }
    var blue by remember { mutableFloatStateOf(initial?.blue ?: 0.6f) }
    var hex by remember { mutableStateOf(initial?.toHex() ?: "#999999") }

    val current = parseHexColor(hex) ?: Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(current)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(current.toHex(), style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                ) {
                    Palettes.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (color.toHex() == current.toHex()) 2.dp else 1.dp,
                                    if (color.toHex() == current.toHex()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    CircleShape,
                                )
                                .clickable { hex = color.toHex(); red = color.red; green = color.green; blue = color.blue },
                        )
                    }
                }
                RgbSlider("红", red) { red = it; hex = Color(red, green, blue).toHex() }
                RgbSlider("绿", green) { green = it; hex = Color(red, green, blue).toHex() }
                RgbSlider("蓝", blue) { blue = it; hex = Color(red, green, blue).toHex() }
                EmberTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    label = { Text("#RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parseHexColor(hex)?.let(onConfirm) ?: onConfirm(current) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RgbSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text((value * 255).toInt().toString(), style = MaterialTheme.typography.labelSmall)
    }
}
