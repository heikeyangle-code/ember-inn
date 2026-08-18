package com.emberinn.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 底材纹理配方：六种图元自由组合，任意密度/角度/缩放——不再是 3 选 1 的死模板。
 * - weave     经纬织纹（油画布）：主线+错位辅线两级，逐段微抖动，织物不是坐标纸
 * - stipple   布点（铜版画 stipple）：随机散点
 * - hatch     定向排线（hatching）：可调角度的平行短排线，带抖动
 * - crossHatch 交叉排线（cross-hatching）：第二方向的排线，铜版画明暗块
 * - fiber     长纤维弧线（宣纸/大理石云纹）：多段折线近似微弯长丝
 * - grain     细颗粒（胶片/矿物尘埃）：高密度低 alpha 噪点
 * 全部参数 0（默认）= 无纹理；tint=null 时纹理色随明暗自动取黑/白。
 */
data class TextureSpec(
    val weave: Float = 0f,
    val stipple: Float = 0f,
    val hatch: Float = 0f,
    val crossHatch: Float = 0f,
    val hatchAngle: Float = 45f,
    val fiber: Float = 0f,
    val grain: Float = 0f,
    /** 整体缩放（>1 图元更大更疏，<1 更细更密）。 */
    val scale: Float = 1f,
    /** 整体强度倍数（所有层 alpha 同乘）。 */
    val intensity: Float = 1f,
    /** 纹理着色（null = 深色底画白、浅色底画黑）。 */
    val tint: Color? = null,
) {
    val active: Boolean get() = weave > 0f || stipple > 0f || hatch > 0f || crossHatch > 0f || fiber > 0f || grain > 0f
}

/**
 * 主题艺术底材引擎：drawWithCache 缓存段按 TextureSpec 预生成全部图元（固定随机种子不闪烁），
 * 绘制期只做最多 7 次批量 drawPoints（Points/Lines），滚动/重组零重算。
 */
fun Modifier.themeTexture(spec: TextureSpec, dark: Boolean): Modifier = drawWithCache {
    if (!spec.active) return@drawWithCache onDrawWithContent { drawContent() }
    val base = spec.tint ?: (if (dark) Color.White else Color.Black)
    val k = spec.intensity.coerceIn(0f, 3f)
    val s = spec.scale.coerceIn(0.4f, 3f)
    val w = size.width
    val h = size.height
    val rand = Random(31 * spec.hashCode() + w.toInt() * 7 + h.toInt() * 13 + (if (dark) 1 else 2))
    val area = w * h

    // ---- 各图层：图元集 + alpha（绘制顺序 = 叠加层次） ----
    var weaveA = 0f; var weaveMain: List<Offset> = emptyList(); var weaveSub: List<Offset> = emptyList()
    var stippleA = 0f; var stipplePts: List<Offset> = emptyList()
    var hatchA = 0f; var hatchSegs: List<Offset> = emptyList()
    var crossA = 0f; var crossSegs: List<Offset> = emptyList()
    var fiberA = 0f; var fiberSegs: List<Offset> = emptyList()
    var grainA = 0f; var grainPts: List<Offset> = emptyList()

    // 织纹：主线每 gap 一根（分 4 段、端点抖动）+ 辅线错位半 gap、alpha 减半——两级交织
    if (spec.weave > 0f) {
        weaveA = 0.032f * k
        val gap = (5.2.dp.toPx() / (0.35f + spec.weave * 0.65f)) * s
        fun weaveLines(offsetX: Float, offsetY: Float): List<Offset> {
            val list = ArrayList<Offset>(4096)
            val seg = 4
            var y = -gap + offsetY
            while (y < h + gap) {
                var j = 0
                while (j < seg) {
                    val y0 = y + h / seg * j + (rand.nextFloat() - 0.5f) * gap * 0.12f
                    val y1 = y + h / seg * (j + 1) + (rand.nextFloat() - 0.5f) * gap * 0.12f
                    list.add(Offset((rand.nextFloat() - 0.5f) * gap * 0.1f, y0))
                    list.add(Offset((rand.nextFloat() - 0.5f) * gap * 0.1f, y1))
                    j++
                }
                y += gap
            }
            var x = -gap + offsetX
            while (x < w + gap) {
                var j = 0
                while (j < seg) {
                    val x0 = x + w / seg * j + (rand.nextFloat() - 0.5f) * gap * 0.12f
                    val x1 = x + w / seg * (j + 1) + (rand.nextFloat() - 0.5f) * gap * 0.12f
                    list.add(Offset(x0, (rand.nextFloat() - 0.5f) * gap * 0.1f))
                    list.add(Offset(x1, (rand.nextFloat() - 0.5f) * gap * 0.1f))
                    j++
                }
                x += gap
            }
            return list
        }
        weaveMain = weaveLines(0f, 0f)
        weaveSub = weaveLines(gap * 0.5f, gap * 0.5f)
    }

    // 布点：随机散点（铜版画 stipple / 尘埃）
    if (spec.stipple > 0f) {
        stippleA = 0.05f * k
        val n = (area / 2200f * spec.stipple).toInt().coerceIn(64, 9000)
        val pts = ArrayList<Offset>(n)
        repeat(n) { pts.add(Offset(rand.nextFloat() * w, rand.nextFloat() * h)) }
        stipplePts = pts
    }

    // 排线：定向平行短线（默认 45°），第二组交叉——铜版画明暗肌理
    if (spec.hatch > 0f) {
        hatchA = 0.032f * k
        val gap = (9.dp.toPx() / (0.4f + spec.hatch * 0.6f)) * s
        val len = 8.5.dp.toPx() * s
        val rad = Math.toRadians(spec.hatchAngle.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val list = ArrayList<Offset>(4096)
        // 覆盖旋转后的包围盒：沿法向扫描
        val diag = (w + h)
        var t = -diag
        while (t < diag) {
            var p = -diag
            while (p < diag) {
                val cx = w / 2f + (-dy) * t + dx * p
                val cy = h / 2f + dx * t + dy * p
                if (cx > -len && cx < w + len && cy > -len && cy < h + len) {
                    val jx = (rand.nextFloat() - 0.5f) * gap * 0.55f
                    val jy = (rand.nextFloat() - 0.5f) * gap * 0.55f
                    list.add(Offset(cx + jx, cy + jy))
                    list.add(Offset(cx + jx + dx * len, cy + jy + dy * len))
                }
                p += gap
            }
            t += gap
        }
        hatchSegs = list
    }
    if (spec.crossHatch > 0f) {
        crossA = 0.03f * k
        val gap = (9.dp.toPx() / (0.4f + spec.crossHatch * 0.6f)) * s
        val len = 8.5.dp.toPx() * s
        val rad = Math.toRadians(spec.hatchAngle + 90.0)
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val list = ArrayList<Offset>(4096)
        val diag = (w + h)
        var t = -diag
        while (t < diag) {
            var p = -diag
            while (p < diag) {
                val cx = w / 2f + (-dy) * t + dx * p
                val cy = h / 2f + dx * t + dy * p
                if (cx > -len && cx < w + len && cy > -len && cy < h + len) {
                    val jx = (rand.nextFloat() - 0.5f) * gap * 0.55f
                    val jy = (rand.nextFloat() - 0.5f) * gap * 0.55f
                    list.add(Offset(cx + jx, cy + jy))
                    list.add(Offset(cx + jx + dx * len, cy + jy + dy * len))
                }
                p += gap
            }
            t += gap
        }
        crossSegs = list
    }

    // 纤维：微弯长丝（宣纸草筋/大理石云纹）——随机方向 4 段折线，段间缓转
    if (spec.fiber > 0f) {
        fiberA = 0.042f * k
        val n = (area / 5200f * spec.fiber).toInt().coerceIn(16, 2600)
        val list = ArrayList<Offset>(n * 5)
        repeat(n) {
            var x = rand.nextFloat() * w
            var y = rand.nextFloat() * h
            var ang = rand.nextFloat() * (Math.PI * 2).toFloat()
            val segs = 3 + rand.nextInt(3)
            val step = (14.dp.toPx() + rand.nextFloat() * 22.dp.toPx()) * s / segs
            var j = 0
            while (j < segs) {
                list.add(Offset(x, y))
                ang += (rand.nextFloat() - 0.5f) * 0.9f
                x += cos(ang) * step
                y += sin(ang) * step
                list.add(Offset(x, y))
                j++
            }
        }
        fiberSegs = list
    }

    // 颗粒：高密度低 alpha 噪点（胶片颗粒/矿物尘）
    if (spec.grain > 0f) {
        grainA = 0.034f * k
        val n = (area / 260f * spec.grain).toInt().coerceIn(64, 11000)
        val pts = ArrayList<Offset>(n)
        repeat(n) { pts.add(Offset(rand.nextFloat() * w, rand.nextFloat() * h)) }
        grainPts = pts
    }

    val wm = weaveMain; val ws = weaveSub; val sp = stipplePts
    val hs = hatchSegs; val cs = crossSegs; val fs = fiberSegs; val gp = grainPts
    val wa = weaveA; val wsa = weaveA * 0.5f; val sa = stippleA
    val ha = hatchA; val ca = crossA; val fa = fiberA; val ga = grainA
    val sw = 1.dp.toPx()
    onDrawWithContent {
        // 纹理画在内容之下：底材是"画布"，卡片/文字浮于其上
        if (gp.isNotEmpty()) drawPoints(gp, PointMode.Points, base.copy(alpha = ga), strokeWidth = sw)
        if (ws.isNotEmpty()) drawPoints(ws, PointMode.Lines, base.copy(alpha = wsa), strokeWidth = sw)
        if (wm.isNotEmpty()) drawPoints(wm, PointMode.Lines, base.copy(alpha = wa), strokeWidth = sw)
        if (sp.isNotEmpty()) drawPoints(sp, PointMode.Points, base.copy(alpha = sa), strokeWidth = sw)
        if (fs.isNotEmpty()) drawPoints(fs, PointMode.Lines, base.copy(alpha = fa), strokeWidth = sw)
        if (hs.isNotEmpty()) drawPoints(hs, PointMode.Lines, base.copy(alpha = ha), strokeWidth = sw)
        if (cs.isNotEmpty()) drawPoints(cs, PointMode.Lines, base.copy(alpha = ca), strokeWidth = sw)
        drawContent()
    }
}

/** 全局纹理覆盖（设置→外观自定义；null=跟随主题预设）。 */
val LocalTextureOverride = staticCompositionLocalOf<TextureSpec?> { null }

/** 生效纹理：用户全局覆盖 > 主题预设。 */
@Composable
fun resolveTexture(preset: ThemePreset): TextureSpec = LocalTextureOverride.current ?: preset.texture

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
    val texture = resolveTexture(preset)
    return this
        .background(scheme.background)
        .then(
            if (aura != null) {
                Modifier.background(
                    Brush.verticalGradient(
                        // 0.82/0.86 → 0.76/0.80：天空氛围更可感（尤其浅色，别再惨白）
                        0f to lerp(aura, scheme.background, if (dark) 0.76f else 0.80f),
                        0.5f to scheme.background,
                    ),
                )
            } else {
                Modifier
            },
        )
        .then(if (texture.active) Modifier.themeTexture(texture, dark) else Modifier)
}
