package com.emberinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.settings.AppearancePrefs
import com.skydoves.cloudy.Sky
import com.skydoves.cloudy.cloudy

/** 玻璃色调：官方主题桥接的舞台染色（EmberTheme.stageTint ← blur_tint_color）> EmberDS surface。
 *  无本地覆盖——官方主题切换即时生效；alpha 由调用方按各玻璃面的透明度叠加（0.38-0.52）。 */
@Composable
fun glassTint(base: Color = MaterialTheme.colorScheme.surface): Color =
    EmberTheme.stageTint ?: base

/** 全局玻璃默认参数：顶栏/输入栏统一 tint 透明度，模糊半径下限，
 *  保证“内容从栏下滚过”时模糊可感知（之前各处 0.38-0.52 不一、观感像纯色）。 */
object EmberGlassDefaults {
    const val BAR_TINT = 0.40f
    const val FAB_TINT = 0.50f
    const val MIN_RADIUS = 14
}

/**
 * 全局统一玻璃表面：毛玻璃 + 发丝边缘高光一处定义，替换各屏散落的
 * “glassEdgeHighlight + cloudy/background”三件套。
 * - sky = null：该面不做玻璃（保持调用方自己的背景），直接原样返回；
 * - 底漆修复明暗跳变：Cloudy 首帧到位前 cloudy 节点直透底层（无玻璃），
 *   位图到位后突然变成“模糊背景+tint”→ 进聊天/切深浅色时玻璃罩明暗跳一下。
 *   现在恒定先铺“背景色+tint”底漆（与 cloudy 到位后的观感一致，静态
 *   背景的模糊≈原色），位图到位后无缝覆盖，全程不跳。
 * - 用户关闭“背景模糊”时用同一底漆，开关切换也不跳变。
 * - blurEnabled=false：滚动中临时退回底漆。cloudy 每帧对背后内容做全屏
 *   RenderEffect 模糊，列表滚动时是最大的 GPU 卡点；底漆与静态背景的
 *   模糊观感几乎一致，停稳后自动恢复真模糊，肉眼无感。
 * - atTop：发丝高光画上缘（悬浮卡/输入栏）还是下缘（顶栏）。
 */
@Composable
fun Modifier.emberGlass(
    sky: Sky?,
    atTop: Boolean,
    tintAlpha: Float = EmberGlassDefaults.BAR_TINT,
    blurEnabled: Boolean = true,
): Modifier {
    if (sky == null) return this
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = glassTint()
    val baseCoat = Modifier
        .background(MaterialTheme.colorScheme.background)
        .background(tint.copy(alpha = tintAlpha))
    return this
        .glassEdgeHighlight(dark = dark, atTop = atTop)
        .then(baseCoat)
        .then(
            if (AppearancePrefs.backgroundBlur(context) && blurEnabled) {
                Modifier.cloudy(
                    sky = sky,
                    radius = AppearancePrefs.blurStrength(context).coerceAtLeast(EmberGlassDefaults.MIN_RADIUS),
                    tint = tint.copy(alpha = tintAlpha),
                )
            } else {
                Modifier
            },
        )
}
