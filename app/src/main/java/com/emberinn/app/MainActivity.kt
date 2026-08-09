package com.emberinn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.emberinn.app.ui.MainScreen
import com.emberinn.app.ui.theme.EmberInnTheme
import com.emberinn.app.ui.theme.ThemeMode
import com.emberinn.app.ui.theme.ThemePrefs
import com.emberinn.app.ui.theme.ThemePreset
import com.emberinn.app.ui.theme.ThemePresets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 原生启动屏：启动窗口先按 Splash 主题渲染（主题 windowBackground），内容就绪后切回常规主题
        setTheme(R.style.Theme_EmberInn)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by remember { mutableStateOf(ThemePrefs.mode(this)) }
            var preset by remember { mutableStateOf(ThemePrefs.preset(this)) }
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            EmberInnTheme(darkTheme = darkTheme, preset = preset) {
                MainScreen(
                    onThemeChanged = { newMode: ThemeMode, newPreset: ThemePreset ->
                        mode = newMode
                        preset = newPreset
                        ThemePrefs.save(this, newMode, newPreset)
                    },
                )
            }
        }
    }
}
