package com.emberinn.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.MainScreen
import com.emberinn.app.ui.components.EmberToastHost
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.settings.AppearancePrefs

/**
 * 应用根：暗色为基线（用户决策——不跟随系统浅色）。
 * 壳层调色板 = 官方主题字段推导（ShellTheme.derive）：切换官方主题整壳即时换装。
 */
class MainActivity : ComponentActivity() {

    /** 官方每次加载设置都会把当前采样预设应用到 oai_settings；App 等价在冷启动应用一次。 */
    private fun applySelectedSamplerPresetOnLoad() {
        val ctx = applicationContext
        if (com.emberinn.app.data.ChatRepository(ctx).profile() == null) return
        val prefs = com.emberinn.app.ui.settings.PresetPrefsStore.load(ctx)
        if (prefs.samplerPreset.isNotBlank()) {
            com.emberinn.app.ui.settings.PresetSettingsStore.applySampler(ctx, prefs.samplerPreset)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching { applySelectedSamplerPresetOnLoad() }
        // 已下线字体的旧文件回收（lxgw.ttf 等），启动时静默执行
        runCatching { FontManager.cleanupLegacy(this) }
        // 内核页进程级只建一次（官方常驻页签等价架构）：入口尽早拿池，首帧后自动预热，
        // 进聊天零等待；预热失败不影响启动
        runCatching { com.emberinn.app.renderer.KernelPoolHolder.warm(applicationContext) }
        enableEdgeToEdge()
        setContent {
            val appContext = applicationContext
            val official = remember { OfficialThemeManager.shared(appContext) }
            // 官方主题字段 → 壳层令牌单向推导：导入/切换任何官方主题，整壳即时换装
            val themeJson by official.currentThemeJson.collectAsState()
            val derived = remember(themeJson) { com.emberinn.app.ui.design.ShellTheme.derive(themeJson) }
            val shell = remember(themeJson) { official.shellSettings() }
            // 主题切换 lerp 400ms（第 16 阶段 Polish，审计表遗留项）：全套壳层颜色平滑过渡；
            // 官方 reduced_motion / 壳层动效减弱档 → 80ms 近似瞬切
            var animatedColors by remember {
                mutableStateOf(derived.colors)
            }
            LaunchedEffect(derived.colors) {
                val start = animatedColors
                val end = derived.colors
                if (start == end) return@LaunchedEffect
                val reduced = shell.reducedMotion ||
                    AppearancePrefs.motionLevel(appContext) == "reduced"
                val durationMs = if (reduced) 80 else 400
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMs),
                ) { v, _ ->
                    animatedColors = start.lerpTo(end, v)
                }
                animatedColors = end
            }
            // 字体等外观偏好变更经 AppearanceBus 推送，此处按 revision 重读
            val appearanceRev by com.emberinn.app.ui.design.AppearanceBus.revision.collectAsState()
            val fontFamily = remember(appearanceRev) {
                when (AppearancePrefs.font(appContext)) {
                    "noto" -> {
                        val files = FontManager.notoFiles(appContext)
                        if (files.size == 4) {
                            FontFamily(
                                Font(files[0], FontWeight.Normal, FontStyle.Normal),
                                Font(files[1], FontWeight.Bold, FontStyle.Normal),
                                Font(files[2], FontWeight.Normal, FontStyle.Italic),
                                Font(files[3], FontWeight.Bold, FontStyle.Italic),
                            )
                        } else {
                            FontFamily.Default
                        }
                    }
                    "source", "serif" -> FontFamily.Serif
                    else -> FontFamily.Default
                }
            }
            val radius = remember(appearanceRev) { AppearancePrefs.radius(appContext) }
            // 壳层个性化（第 15 阶段）：密度/动效档位，只影响壳层令牌，Chat Theme 不动
            val density = remember(appearanceRev) { AppearancePrefs.shellDensity(appContext) }
            val motionLevel = remember(appearanceRev) { AppearancePrefs.motionLevel(appContext) }
            EmberTheme(
                colors = animatedColors,
                chat = derived.chat,
                stageTint = derived.stageTint,
                reducedMotion = shell.reducedMotion,
                blur = derived.blurRadius,
                fontFamily = fontFamily,
                radius = radius,
                density = density,
                motionLevel = motionLevel,
            ) {
                Box {
                    MainScreen()
                    // 官方 toastr 应用内浮层：六位置随主题 toastr_position 全版本生效
                    EmberToastHost()
                }
            }
        }
    }
}
