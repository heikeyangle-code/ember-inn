package com.emberinn.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 主题艺术底材：把"画在什么上"画进界面（古典油画/铜版蚀刻/水墨宣纸的底子）。
 * - oil  油画布：经纬交织的织纹，厚涂颜料的画布感
 * - etch 铜版蚀刻：布点（stipple）+ 短排线（hatching），复古插画书的版画肌理
 * - wash 宣纸：稀疏柔尘，水墨留白的纸感
 * 点/线集合在 drawWithCache 缓存段预生成一次（固定随机种子，不闪烁），
 * 绘制只有两次批量图元调用（PointMode.Points / Lines），滚动/重组不重算。
 */
fun Modifier.themeTexture(texture: String, dark: Boolean): Modifier = drawWithCache {
    val grain = if (dark) Color.White else Color.Black
    val rand = Random(texture.hashCode() * 31 + size.width.toInt() * 7 + size.height.toInt() * 13)
    var dots: List<Offset> = emptyList()
    var dotAlpha = 0f
    // PointMode.Lines：相邻两点连成一段线，全部线段打进同一列表一次画完
    var segs: List<Offset> = emptyList()
    var segAlpha = 0f
    when (texture) {
        "oil" -> {
            // 画布经纬：横竖两组织线
            segAlpha = 0.028f
            val gap = 4.dp.toPx()
            val list = ArrayList<Offset>((size.width / gap).toInt() * (size.height / gap).toInt() * 2 + 64)
            var y = 0f
            while (y < size.height) {
                list.add(Offset(0f, y)); list.add(Offset(size.width, y))
                y += gap
            }
            var x = 0f
            while (x < size.width) {
                list.add(Offset(x, 0f)); list.add(Offset(x, size.height))
                x += gap
            }
            segs = list
        }
        "etch" -> {
            // 蚀刻：随机布点 + 45° 短排线（铜版画明暗肌理）
            dotAlpha = 0.055f
            segAlpha = 0.032f
            val pts = ArrayList<Offset>(4096)
            val n = (size.width * size.height / 900f).toInt().coerceAtMost(6000)
            repeat(n) { pts.add(Offset(rand.nextFloat() * size.width, rand.nextFloat() * size.height)) }
            dots = pts
            val gap = 8.dp.toPx()
            val len = 9.dp.toPx()
            val list = ArrayList<Offset>(4096)
            var y = -len
            while (y < size.height + len) {
                var x = -len
                while (x < size.width + len) {
                    val jx = x + rand.nextFloat() * gap * 0.6f
                    val jy = y + rand.nextFloat() * gap * 0.6f
                    list.add(Offset(jx, jy))
                    list.add(Offset(jx + len * 0.62f, jy - len * 0.78f))
                    x += gap
                }
                y += gap
            }
            segs = list
        }
        "wash" -> {
            // 宣纸微尘：稀疏的柔尘点
            dotAlpha = 0.03f
            val pts = ArrayList<Offset>(2048)
            val n = (size.width * size.height / 2400f).toInt().coerceAtMost(2400)
            repeat(n) { pts.add(Offset(rand.nextFloat() * size.width, rand.nextFloat() * size.height)) }
            dots = pts
        }
    }
    val dd = dots
    val ss = segs
    val dA = dotAlpha
    val sA = segAlpha
    onDrawWithContent {
        // 纹理画在内容之下：底材是"画布"，卡片/文字浮于其上
        if (dd.isNotEmpty()) {
            drawPoints(dd, PointMode.Points, grain.copy(alpha = dA), strokeWidth = 1.dp.toPx())
        }
        if (ss.isNotEmpty()) {
            drawPoints(ss, PointMode.Lines, grain.copy(alpha = sA), strokeWidth = 1.dp.toPx())
        }
        drawContent()
    }
}

/**
 * 页面艺术底：底色 + 顶部天空氛围（深色 auraTop / 浅色 auraTopLight，极淡渐变）+ 主题底材纹理。
 * 官方主题与未启用艺术扩展的主题全部退化为纯底色，行为与旧实现一致。
 */
@Composable
fun Modifier.emberBackdrop(): Modifier {
    val preset = LocalThemePreset.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val aura = if (dark) preset.auraTop else preset.auraTopLight
    return this
        .background(scheme.background)
        .then(
            if (aura != null) {
                Modifier.background(
                    Brush.verticalGradient(
                        0f to lerp(aura, scheme.background, if (dark) 0.82f else 0.86f),
                        0.5f to scheme.background,
                    ),
                )
            } else {
                Modifier
            },
        )
        .then(if (preset.texture != "none") Modifier.themeTexture(preset.texture, dark) else Modifier)
}
