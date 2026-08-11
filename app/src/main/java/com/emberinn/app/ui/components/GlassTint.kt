package com.emberinn.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.theme.LocalThemePreset

/** 玻璃色调（官方 --SmartThemeBlurTintColor）：
 *  用户设置 stBlurTint > 当前主题预设 stBlurTint（酒馆官方 #171717）> M3 surface。
 *  alpha 由调用方按各玻璃面的透明度叠加（0.38-0.52）。 */
@Composable
fun glassTint(base: Color = MaterialTheme.colorScheme.surface): Color {
    val context = LocalContext.current
    val stTheme = LocalThemePreset.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return parseHexColor(AppearancePrefs.stBlurTint(context))
        ?: (if (dark) stTheme.stBlurTint else null)
        ?: base
}
