@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.components

import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 常用色板：官方 SillyTavern 8 色 + 标准中性/彩色。 */
private val Palettes: List<Color> = listOf(
    Color(0xFFDCDCD2), Color(0xFF919191), Color(0xFFBCE7CF), Color(0xFFE18A24),
    Color(0xFF171717), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF3C3C3C),
    Color(0xFFB23A2A), Color(0xFF2E7D6B), Color(0xFFC98A2B), Color(0xFFC73E2B),
    Color(0xFF5B6CFF), Color(0xFF5A5A5E), Color(0xFF4E8D6C), Color(0xFF7C5CBF),
    Color(0xFF5E7FA3), Color(0xFFC96A8C), Color(0xFF8A5A44), Color(0xFFB08A3E),
)

private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    var h = 0f
    if (d > 0f) {
        h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        if (h < 0f) h += 360f
    }
    val s = if (max == 0f) 0f else d / max
    return floatArrayOf(h, s, max)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

private fun hsvColor(hue: Float, sat: Float, value: Float): Color {
    val rgb = hsvToRgb(hue, sat, value)
    return Color(rgb[0], rgb[1], rgb[2])
}

/**
 * 高级选色盘（README UI 质感升级）：
 * 二维 HSV 取色板（饱和×明度）+ 色相渐变条 + RGB 渐变滑杆 + 官方色板 + hex 输入。
 * 取代旧的“色板 + 三条普通滑杆”对话框，交互与官方字段语义不变。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color?,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val start = initial ?: Color(0.6f, 0.6f, 0.6f)
    val startHsv = remember(start) { rgbToHsv(start.red, start.green, start.blue) }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }
    var hex by remember { mutableStateOf(start.toHex()) }

    val current = parseHexColor(hex) ?: hsvColor(hue, sat, value)
    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        value = v
        hex = hsvColor(h, s, v).toHex()
    }
    fun applyHex(v: String) {
        hex = v
        parseHexColor(v)?.let { c ->
            val hsv = rgbToHsv(c.red, c.green, c.blue)
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    EmberBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 大预览色块：当前色 + 主题环 + 彩色阴影
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .emberShadow(color = current.copy(alpha = 0.45f), radius = 10.dp, spread = 1.dp, offset = DpOffset(0.dp, 3.dp), alpha = 0.5f)
                        .background(current)
                        .border(1.dp, EmberTheme.colors.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        current.toHex(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmberTheme.colors.inkMute,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { onConfirm(current) }) { Text("确定") }
            }

            Spacer(Modifier.height(14.dp))

            // 二维 HSV 取色板：横=饱和度，纵=明度
            SvSquare(
                hue = hue,
                saturation = sat,
                value = value,
                onChange = { s, v -> applyHsv(hue, s, v) },
            )

            Spacer(Modifier.height(12.dp))

            // 色相渐变条
            GradientSlider(
                value = hue / 360f,
                brush = Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color(0xFF00FF00), Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                ),
                onValueChange = { f -> applyHsv(f * 360f, sat, value) },
            )

            Spacer(Modifier.height(10.dp))

            // RGB 渐变滑杆：轨道渐变跟随另外两通道，直观显示该通道效果
            val rgb = hsvToRgb(hue, sat, value)
            ChannelRow("R", rgb[0], rgb[1], rgb[2]) { f -> applyHex(Color(f, rgb[1], rgb[2]).toHex()) }
            ChannelRow("G", rgb[0], rgb[1], rgb[2]) { f -> applyHex(Color(rgb[0], f, rgb[2]).toHex()) }
            ChannelRow("B", rgb[0], rgb[1], rgb[2]) { f -> applyHex(Color(rgb[0], rgb[1], f).toHex()) }

            Spacer(Modifier.height(12.dp))

            // 官方/常用色板
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Palettes.forEach { color ->
                    val selected = color.toHex() == current.toHex()
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (selected) 2.5.dp else 1.dp,
                                if (selected) EmberTheme.colors.accent else EmberTheme.colors.line,
                                CircleShape,
                            )
                            .clickable { applyHex(color.toHex()) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            EmberTextField(
                value = hex,
                onValueChange = { applyHex(it) },
                label = { Text("#RRGGBB") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 二维取色板：横=饱和度 0→1，纵=明度 1→0（上亮下暗）。 */
@Composable
private fun SvSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val base = hsvColor(hue, 1f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    onChange((o.x / size.width).coerceIn(0f, 1f), (1f - o.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f), (1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White, base))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
        Box(
            modifier = Modifier
                .offset(x = maxWidth * saturation - 9.dp, y = maxHeight * (1f - value) - 9.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}

/** 渐变滑杆：点击/拖动取值，白底圆点拇指 + 深色描边。 */
@Composable
private fun GradientSlider(
    value: Float,
    brush: Brush,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(brush)
            .pointerInput(Unit) {
                detectTapGestures { o -> onValueChange((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(
            modifier = Modifier
                .offset(x = maxWidth * value.coerceIn(0f, 1f) - 9.dp)
                .align(Alignment.CenterStart)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}

/** RGB 单通道滑杆：轨道渐变跟随当前另外两通道，右侧实时数值。 */
@Composable
private fun ChannelRow(label: String, r: Float, g: Float, b: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = EmberTheme.colors.inkMute,
            modifier = Modifier.width(22.dp),
        )
        GradientSlider(
            value = when (label) {
                "R" -> r
                "G" -> g
                else -> b
            },
            brush = Brush.horizontalGradient(
                when (label) {
                    "R" -> listOf(Color(0f, g, b), Color(1f, g, b))
                    "G" -> listOf(Color(r, 0f, b), Color(r, 1f, b))
                    else -> listOf(Color(r, g, 0f), Color(r, g, 1f))
                },
            ),
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            ((if (label == "R") r else if (label == "G") g else b) * 255).toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = EmberTheme.colors.inkMute,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
        )
    }
}
