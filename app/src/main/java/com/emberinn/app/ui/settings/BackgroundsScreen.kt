package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.EmberTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 聊天背景（官方 div_background 分区对齐 backgrounds.js）：
 * - 全局背景列表 + 选择（官方点缩略图 → background_settings.url）
 * - #background_fitting 五档适配选择器（classic/cover/contain/stretch/center）
 * - 内置官方默认背景库首启播种（assets/seed-backgrounds → filesDir/backgrounds）
 * - 导入（系统照片选择器）/ 删除；会话级锁定在聊天页「更多 → 聊天背景」
 * 模糊/遮罩全部由官方主题字段控制（blur_strength / chat_tint_color），此处无独立开关。
 */
@Composable
fun BackgroundsScreen(onBack: () -> Unit, onAppearanceChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val bgDir = remember { File(context.filesDir, "backgrounds").apply { mkdirs() } }
    var files by remember { mutableStateOf(bgDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()) }
    var currentBg by remember { mutableStateOf(AppearancePrefs.globalBackground(context)) }
    var fitting by remember { mutableStateOf(AppearancePrefs.globalBackgroundFitting(context)) }

    // 官方默认背景库播种：逐字拷自基线 default/content/backgrounds 精选，缺哪个补哪个（幂等）
    LaunchedEffect(Unit) {
        val seeded = runCatching {
            context.assets.list("seed-backgrounds")?.filter { it.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".png") } }
        }.getOrNull().orEmpty()
        var changed = false
        for (name in seeded) {
            val dest = File(bgDir, name)
            if (!dest.exists()) {
                runCatching {
                    context.assets.open("seed-backgrounds/$name").use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                changed = true
            }
        }
        if (changed) files = bgDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun refresh(path: String, fit: String) {
        currentBg = path
        fitting = fit
        onAppearanceChanged()
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                // 官方 getBackgroundPath 同构：文件名即库内标识；重名追加序号不覆盖
                var name = File(uri.lastPathSegment ?: "background.jpg").name.ifBlank { "background.jpg" }
                if (!name.matches(Regex(".*\\.(jpe?g|png|webp|gif)", RegexOption.IGNORE_CASE))) name += ".jpg"
                var dest = File(bgDir, name)
                var n = 1
                while (dest.exists()) {
                    val dot = name.lastIndexOf('.')
                    dest = File(bgDir, "${name.substring(0, dot)}-$n${name.substring(dot)}")
                    n++
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                files = bgDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
            }
        }
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "背景", subtitle = "全局背景与适配 · 会话级在聊天页设置", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        color = EmberTheme.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("导入图片", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
                            Text(
                                "从相册导入背景图；模糊/遮罩由主题控制。",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmberTheme.colors.inkMute,
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    importPicker.launch(arrayOf("image/*"))
                                }) { Text("选择图片…") }
                            }
                        }
                    }
                }
                item {
                    // 官方 #background_fitting 五档（backgrounds.js setFittingClass L1632-1638）
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        color = EmberTheme.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("适配方式", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
                            Text(
                                "classic=样式表默认；其余四档与官方 backgrounds.css 类同名。",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmberTheme.colors.inkMute,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                AppearancePrefs.FITTINGS.forEach { option ->
                                    FilterChip(
                                        selected = fitting == option,
                                        onClick = {
                                            AppearancePrefs.saveGlobalBackgroundFitting(context, option)
                                            refresh(currentBg, option)
                                        },
                                        label = { Text(option) },
                                    )
                                }
                            }
                        }
                    }
                }
                items(files.size) { index ->
                    val f = files[index]
                    val active = f.absolutePath == currentBg
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = EmberTheme.colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppearancePrefs.saveGlobalBackground(context, f.absolutePath)
                                refresh(f.absolutePath, fitting)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (active) {
                                Text(
                                    "使用中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmberTheme.colors.accent,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            TextButton(onClick = {
                                runCatching { f.delete() }
                                if (active) {
                                    AppearancePrefs.saveGlobalBackground(context, "")
                                    currentBg = ""
                                }
                                files = bgDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
                                onAppearanceChanged()
                            }) { Text("删除") }
                        }
                    }
                }
                if (files.isEmpty()) {
                    item {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = EmberTheme.colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "暂无背景图。首次进入会自动导入内置官方背景库。",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmberTheme.colors.inkMute,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (currentBg.isNotBlank()) {
                            TextButton(onClick = {
                                AppearancePrefs.saveGlobalBackground(context, "")
                                refresh("", fitting)
                            }) { Text("清除全局背景") }
                        }
                    }
                }
            }
        }
    }
}
