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
 * 纹理层配方：六种图元自由组合，任意密度/角度/缩放——画布的"底子"。
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
 * 色域：一个径向色斑（水彩湿画/光晕/撞色泼彩）。
 * x/y 为画布相对位置（0-1），radius 为占画布长边的比例，alpha 为浓度。
 */
data class ColorWash(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float = 0.55f,
    val alpha: Float = 0.2f,
)

/** 便捷构造（主题预设用）。 */
fun wash(color: Color, x: Float, y: Float, radius: Float = 0.55f, alpha: Float = 0.2f) =
    ColorWash(color, x, y, radius, alpha)

/** 随机泼彩：按选中色板散布色域（"重掷布局"用，种子固定则不闪）。 */
fun randomWashes(colors: List<Color>, strength: Float, seed: Int): List<ColorWash> {
    val rand = Random(seed)
    return colors.map { c ->
        ColorWash(
            color = c,
            x = 0.08f + rand.nextFloat() * 0.84f,
            y = 0.08f + rand.nextFloat() * 0.84f,
            radius = 0.35f + rand.nextFloat() * 0.45f,
            alpha = strength * (0.6f + rand.nextFloat() * 0.4f),
        )
    }
}

/** 定向渐变层：日落/夜幕/霓虹——angle 0=首色在顶部，90=首色在左侧，任意角度。 */
data class CanvasGradient(
    val colors: List<Color>,
    val angle: Float = 0f,
    val alpha: Float = 0.25f,
)

/**
 * 画布配方（画板级自由度）：色域泼彩 + 定向渐变 + 六图元纹理，三层任意组合。
 * 绘制顺序 = 色域 → 渐变 → 纹理 → 内容：像先铺底色再上肌理的作画顺序。
 */
data class BackdropSpec(
    val washes: List<ColorWash> = emptyList(),
    val gradient: CanvasGradient? = null,
    val texture: TextureSpec = TextureSpec(),
) {
    val active: Boolean get() = washes.isNotEmpty() || (gradient != null && gradient.alpha > 0f) || texture.active
}

/**
 * 纹理图元引擎：drawWithCache 按配方预生成全部图元（固定随机种子不闪烁），
 * 绘制期只做批量 drawPoints（Points/Lines），滚动/重组零重算。
 */
private fun Modifier.textureLayer(spec: TextureSpec, dark: Boolean): Modifier = drawWithCache {
    if (!spec.active) return@drawWithCache onDrawWithContent { drawContent() }
    val base = spec.tint ?: (if (dark) Color.White else Color.Black)
    val k = spec.intensity.coerceIn(0f, 3f)
    val s = spec.scale.coerceIn(0.4f, 3f)
    val w = size.width
    val h = size.height
    val rand = Random(31 * spec.hashCode() + w.toInt() * 7 + h.toInt() * 13 + (if (dark) 1 else 2))
    val area = w * h

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

    // 排线：定向平行短线（默认 45°），带抖动——铜版画明暗肌理
    if (spec.hatch > 0f) {
        hatchA = 0.032f * k
        val gap = (9.dp.toPx() / (0.4f + spec.hatch * 0.6f)) * s
        val len = 8.5.dp.toPx() * s
        val rad = Math.toRadians(spec.hatchAngle.toDouble())
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

    // 纤维：微弯长丝（宣纸草筋/大理石云纹）——随机方向多段折线，段间缓转
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

/**
 * 画布引擎：色域泼彩（软边径向渐变）→ 定向渐变 → 纹理图元，画在内容之下。
 * 色域/渐变在浅色模式自动收敛（×0.8/×0.85），深色全浓度——同一配方深浅都成立。
 */
fun Modifier.canvasBackdrop(spec: BackdropSpec, dark: Boolean): Modifier {
    val washLayer = if (spec.washes.isEmpty()) Modifier else Modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val dim = maxOf(w, h)
        val mod = if (dark) 1f else 0.8f
        val brushes = spec.washes.map { wash ->
            val a = (wash.alpha * mod).coerceIn(0f, 1f)
            val center = Offset(wash.x * w, wash.y * h)
            val radius = (wash.radius * dim).coerceAtLeast(1f)
            wash to Brush.radialGradient(
                colors = listOf(
                    wash.color.copy(alpha = a),
                    wash.color.copy(alpha = a * 0.45f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            )
        }
        onDrawWithContent {
            brushes.forEach { (wsh, brush) ->
                drawRect(brush = brush, alpha = 1f)
            }
            drawContent()
        }
    }
    val gradientLayer = spec.gradient?.takeIf { it.alpha > 0f && it.colors.size >= 2 }?.let { g ->
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val mod = if (dark) 1f else 0.85f
            val a = (g.alpha * mod).coerceIn(0f, 1f)
            val rad = Math.toRadians(g.angle.toDouble())
            val dx = sin(rad).toFloat()
            val dy = cos(rad).toFloat()
            val half = maxOf(w, h) * 0.75f
            val cx = w / 2f
            val cy = h / 2f
            val brush = Brush.linearGradient(
                colors = g.colors.map { c -> c.copy(alpha = c.alpha * a) },
                start = Offset(cx - dx * half, cy - dy * half),
                end = Offset(cx + dx * half, cy + dy * half),
            )
            onDrawWithContent {
                drawRect(brush = brush)
                drawContent()
            }
        }
    } ?: Modifier
    return this
        .then(washLayer)
        .then(gradientLayer)
        .then(if (spec.texture.active) Modifier.textureLayer(spec.texture, dark) else Modifier)
}

/** 全局画布覆盖（设置→外观自定义；null=跟随主题预设）。 */
val LocalBackdropOverride = staticCompositionLocalOf<BackdropSpec?> { null }

/** 生效画布：用户全局覆盖 > 主题预设。 */
@Composable
fun resolveBackdrop(preset: ThemePreset): BackdropSpec = LocalBackdropOverride.current ?: preset.backdrop

/**
 * 页面艺术底：底色 + 顶部天空氛围（深色 auraTop / 浅色 auraTopLight）+ 画布三层（色域/渐变/纹理）。
 * 官方主题与未启用艺术扩展的主题全部退化为纯底色，行为与旧实现一致。
 */
@Composable
fun Modifier.emberBackdrop(): Modifier {
    val preset = LocalThemePreset.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val aura = if (dark) preset.auraTop else preset.auraTopLight
    val backdrop = resolveBackdrop(preset)
    return this
        .background(scheme.background)
        .then(
            if (aura != null) {
                Modifier.background(
                    Brush.verticalGradient(
                        0f to lerp(aura, scheme.background, if (dark) 0.76f else 0.80f),
                        0.5f to scheme.background,
                    ),
                )
            } else {
                Modifier
            },
        )
        .then(if (backdrop.active) Modifier.canvasBackdrop(backdrop, dark) else Modifier)
}

/** 效果库：一键画布配方（设置→外观直接套用，套用后仍可在自定义里继续改）。 */
val BackdropLibrary: List<Pair<String, BackdropSpec>> = listOf(
    "落日熔金" to BackdropSpec(
        gradient = CanvasGradient(listOf(Color(0xFF2E8B9A), Color(0xFFE8804A)), 135f, 0.22f),
        texture = TextureSpec(grain = 0.35f),
    ),
    "霓虹雨夜" to BackdropSpec(
        washes = listOf(
            wash(Color(0xFFE84B9A), 0.15f, 0.85f, 0.75f, 0.20f),
            wash(Color(0xFF4BE0E8), 0.85f, 0.15f, 0.75f, 0.20f),
        ),
    ),
    "水彩粉彩" to BackdropSpec(
        washes = listOf(
            wash(Color(0xFFE88AA8), 0.2f, 0.2f, 0.6f, 0.16f),
            wash(Color(0xFF8FB8E8), 0.8f, 0.3f, 0.65f, 0.14f),
            wash(Color(0xFFB89AE8), 0.5f, 0.8f, 0.6f, 0.15f),
        ),
        texture = TextureSpec(fiber = 0.5f),
    ),
    "星穹夜幕" to BackdropSpec(
        gradient = CanvasGradient(listOf(Color(0xFF2B3B8F), Color.Transparent), 0f, 0.30f),
        texture = TextureSpec(stipple = 0.6f, grain = 0.25f, scale = 1.4f),
    ),
    "大理石云纹" to BackdropSpec(
        washes = listOf(
            wash(Color(0xFFE8E4DA), 0.3f, 0.3f, 0.7f, 0.20f),
            wash(Color(0xFFB8B4AA), 0.7f, 0.6f, 0.6f, 0.14f),
        ),
        texture = TextureSpec(fiber = 0.55f, grain = 0.3f, scale = 1.6f),
    ),
    "火山余烬" to BackdropSpec(
        gradient = CanvasGradient(listOf(Color.Transparent, Color(0xFF8A2E1F)), 0f, 0.30f),
        texture = TextureSpec(stipple = 0.5f, grain = 0.4f, scale = 1.3f),
    ),
    "极光垂帘" to BackdropSpec(
        gradient = CanvasGradient(listOf(Color(0xFF3ED8A0), Color(0xFF7B5AD8)), 115f, 0.22f),
        washes = listOf(wash(Color(0xFF3ED8A0), 0.25f, 0.1f, 0.5f, 0.18f)),
        texture = TextureSpec(grain = 0.2f),
    ),
    "晨雾海面" to BackdropSpec(
        washes = listOf(
            wash(Color(0xFF8FA3B0), 0.25f, 0.3f, 0.8f, 0.12f),
            wash(Color(0xFFC9D4D8), 0.7f, 0.6f, 0.7f, 0.12f),
        ),
        texture = TextureSpec(stipple = 0.5f, grain = 0.3f),
    ),
    "羊皮古卷" to BackdropSpec(
        washes = listOf(wash(Color(0xFFD8A85C), 0.5f, 0.3f, 0.6f, 0.14f)),
        texture = TextureSpec(fiber = 0.7f, grain = 0.4f, stipple = 0.25f),
    ),
    "素白画布" to BackdropSpec(
        texture = TextureSpec(weave = 0.8f, grain = 0.2f),
    ),
)
