package com.emberinn.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.MainScreen
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
        enableEdgeToEdge()
        setContent {
            val appContext = applicationContext
            val official = remember { OfficialThemeManager.shared(appContext) }
            // 官方主题字段 → 壳层令牌单向推导：导入/切换任何官方主题，整壳即时换装
            val themeJson by official.currentThemeJson.collectAsState()
            val derived = remember(themeJson) { com.emberinn.app.ui.design.ShellTheme.derive(themeJson) }
            val shell = remember(themeJson) { official.shellSettings() }
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
            EmberTheme(
                colors = derived.colors,
                chat = derived.chat,
                stageTint = derived.stageTint,
                reducedMotion = shell.reducedMotion,
                fontFamily = fontFamily,
                radius = radius,
            ) {
                MainScreen()
            }
        }
    }
}
