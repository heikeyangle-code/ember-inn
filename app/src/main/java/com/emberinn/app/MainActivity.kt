package com.emberinn.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.MainScreen
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.SkinStore
import com.emberinn.app.ui.settings.AppearancePrefs

/**
 * 应用根：EmberDS 强制暗基底（用户决策——不再跟随系统浅色，HANDOFF 待办 #1）。
 * 壳层皮肤来自 SkinStore；官方内容主题经 seed 桥只染强调三态与舞台遮罩，互不污染。
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
            remember { SkinStore.init(appContext) }
            val skin by SkinStore.skin.collectAsState()
            val official = remember { OfficialThemeManager.shared(appContext) }
            // 收集主题名：切换官方主题时触发壳层重取桥接色（验收标准 4：导入后壳层自动协调）
            val themeName by official.currentName.collectAsState()
            val bridge = remember(themeName) { official.skinColors() }
            val shell = remember(themeName) { official.shellSettings() }
            val accentOverride = bridge.accent?.let { Color(it) }
            val stageTint = bridge.stageTint?.let { Color(it) }
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
            EmberTheme(
                skin = skin,
                darkTheme = true,
                accentOverride = accentOverride,
                stageTint = stageTint,
                reducedMotion = shell.reducedMotion,
                fontFamily = fontFamily,
            ) {
                MainScreen()
            }
        }
    }
}
