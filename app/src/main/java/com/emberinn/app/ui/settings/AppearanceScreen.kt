package com.emberinn.app.ui.settings


import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberSlider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.design.AppearanceBus
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.InkTier
import com.emberinn.app.ui.design.components.InkText
import com.emberinn.app.ui.design.components.SectionTitle
import com.emberinn.app.ui.design.components.SurfaceCard
import com.emberinn.app.ui.design.components.EmberChip
import com.emberinn.app.ui.design.components.ChipRow
import com.emberinn.app.ui.design.components.EmberBottomSheet
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.design.components.SheetRowTone
import com.emberinn.app.ui.icons.FaIcons
import kotlinx.coroutines.launch

/**
 * 外观（DESIGN_SYSTEM §五）：酒馆官方主题管理 + 显示偏好。
 * 官方主题=唯一换装来源：字段推导供内核与壳层（ShellTheme），无独立皮肤体系。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val official = remember { OfficialThemeManager.shared(context) }
    val themes by official.themes.collectAsState()
    val currentTheme by official.currentName.collectAsState()

    var radius by remember { mutableStateOf(AppearancePrefs.radius(context)) }
    var font by remember { mutableStateOf(AppearancePrefs.font(context)) }
    val fontScope = rememberCoroutineScope()
    var fontDownloading by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf<String?>(null) }

    // 官方主题：导入 / 导出 / 管理（导出、删除收进弹层）
    var manageSheetFor by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                    ?: error("无法读取文件")
                val name = official.import(text)
                Toast.show(context, "已导入主题：$name")
            }.onFailure { e ->
                Toast.show(context, "导入失败：${e.message}")
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = pendingExport
        if (uri != null && name != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(official.export(name)?.toByteArray()) }
                Toast.show(context, "已导出：${name}.json")
            }.onFailure { e ->
                Toast.show(context, "导出失败：${e.message}")
            }
        }
        pendingExport = null
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "外观", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---------------- 官方内容主题（壳层换装唯一来源：字段推导整壳生效） ----------------
                item {
                    Column {
                        SectionTitle("酒馆官方主题")
                        InkText(
                            "内核渲染层配色，与官方 SillyTavern 主题文件完全互导；强调色自动协调到壳层",
                            tier = InkTier.Mute,
                            sizeSp = 12f,
                        )
                    }
                }
                items(themes.size) { index ->
                    val meta = themes[index]
                    val active = meta.name == currentTheme
                    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = { official.select(meta.name) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                InkText(meta.name, tier = InkTier.Primary, sizeSp = 14f, fontWeight = FontWeight.Medium)
                                InkText(
                                    if (meta.bundled) "内置" else "导入",
                                    tier = InkTier.Mute,
                                    sizeSp = 11f,
                                )
                            }
                            if (active) InkText("使用中", tier = InkTier.Primary, sizeSp = 12f, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    ChipRow {
                        EmberChip(label = "导入主题 JSON", selected = false, onClick = { importLauncher.launch(arrayOf("*/*")) })
                        EmberChip(label = "管理当前主题", selected = false, onClick = { manageSheetFor = currentTheme })
                    }
                }

                // ---------------- 显示 ----------------
                item {
                    SectionTitle("显示")
                    PreferenceGroup {
                        GroupLabel("全局圆角")
                        ChipRow(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                            listOf("default" to "系统", "square" to "方正", "rounded" to "圆润", "circle" to "浑圆").forEach { (v, label) ->
                                EmberChip(
                                    label = label,
                                    selected = radius == v,
                                    onClick = { radius = v; AppearancePrefs.save(context, radius, font); AppearanceBus.notifyChanged() },
                                )
                            }
                        }
                    }
                }
                item {
                    PreferenceGroup {
                        GroupLabel("全局字体")
                        ChipRow(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                            listOf(
                                "default" to "系统",
                                "serif" to "衬线",
                                "noto" to "Noto Sans",
                            ).forEach { (v, label) ->
                                val fontReady = when (v) {
                                    "noto" -> FontManager.notoReady(context)
                                    else -> true
                                }
                                EmberChip(
                                    label = if (v == "noto" && !fontReady) "$label ↓" else label,
                                    selected = font == v,
                                    onClick = {
                                        when {
                                            v == "noto" && !fontReady -> {
                                                font = "noto"
                                                fontScope.launch {
                                                    fontDownloading = true
                                                    val result = FontManager.ensureNoto(context)
                                                    fontDownloading = false
                                                    result.onSuccess {
                                                        AppearancePrefs.save(context, radius, "noto")
                                                        AppearanceBus.notifyChanged()
                                                    }.onFailure { e ->
                                                        font = AppearancePrefs.font(context)
                                                        fontError = e.message ?: "未知错误"
                                                    }
                                                }
                                            }
                                            else -> {
                                                font = v
                                                AppearancePrefs.save(context, radius, v)
                                                AppearanceBus.notifyChanged()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // ---------------- 头像与文字 ----------------
                item {
                }

                // ---------------- 玻璃 ----------------
                item {
                    BlurGroup(onChanged = { AppearanceBus.notifyChanged() })
                }

                // ---------------- 消息外观 ----------------
                item {
                    MessageAppearanceGroup(onChanged = { AppearanceBus.notifyChanged() })
                }
            }
        }
    }

    // 管理当前主题弹层
    val manageTarget = manageSheetFor
    if (manageTarget != null) {
        val meta = themes.firstOrNull { it.name == manageTarget }
        EmberBottomSheet(visible = true, onDismiss = { manageSheetFor = null }, title = manageTarget) {
            SheetRow(
                label = "导出 JSON",
                icon = FaIcons.Download,
                onClick = {
                    pendingExport = manageTarget
                    exportLauncher.launch("${manageTarget}.json")
                    manageSheetFor = null
                },
            )
            if (meta != null && !meta.bundled) {
                SheetRow(
                    label = "删除主题",
                    icon = FaIcons.TrashCan,
                    tone = SheetRowTone.Danger,
                    onClick = {
                        confirmDelete = manageTarget
                        manageSheetFor = null
                    },
                )
            }
        }
    }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除主题") },
            text = { Text("确定删除「$name」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = official.delete(name)
                    Toast.show(context, if (ok) "已删除" else "删除失败")
                    confirmDelete = null
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }

    if (fontDownloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("下载字体") },
            text = { Text("正在下载字体（Noto Sans 4 面约 2.2MB），完成后自动应用，请稍候…") },
            confirmButton = {},
        )
    }
    fontError?.let { err ->
        AlertDialog(
            onDismissRequest = { fontError = null },
            title = { Text("字体下载失败") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { fontError = null }) { Text("知道了") }
            },
        )
    }
}

/** 设置分组容器：surface 卡统一包裹。 */
@Composable
private fun PreferenceGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EmberTheme.shapes.cornerCard))
            .background(c.surface)
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun GroupLabel(text: String) {
    InkText(text, tier = InkTier.Soft, sizeSp = 13f, fontWeight = FontWeight.Medium)
}

@Composable
private fun SwitchPrefRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EmberSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

/** 背景模糊组（玻璃总开关 + 强度）。 */
@Composable
private fun BlurGroup(onChanged: () -> Unit) {
    val context = LocalContext.current
    var blur by remember { mutableStateOf(AppearancePrefs.backgroundBlur(context)) }
    var blurStrength by remember { mutableStateOf(AppearancePrefs.blurStrength(context)) }
    PreferenceGroup {
        SwitchPrefRow(
            title = "背景模糊（玻璃表面）",
            subtitle = "顶栏 / 输入栏 / 浮层的毛玻璃总开关",
            checked = blur,
            onToggle = { blur = it; AppearancePrefs.saveBackgroundBlur(context, it); onChanged() },
        )
        if (blur) {
            // 下限 14 = EmberGlassDefaults.MIN_RADIUS（再低玻璃观感消失）
            InkText("模糊强度", tier = InkTier.Soft, sizeSp = 13f, fontWeight = FontWeight.Medium)
            EmberSlider(
                value = blurStrength.coerceAtLeast(14).toFloat(),
                onValueChange = { blurStrength = it.toInt(); AppearancePrefs.saveBlurStrength(context, it.toInt()); onChanged() },
                valueRange = 14f..40f,
            )
            InkText("半径 $blurStrength px", tier = InkTier.Mute, sizeSp = 11f)
        }
    }
}

/** 气泡样式 / 密度 / 沉浸模式组。 */
@Composable
private fun MessageAppearanceGroup(onChanged: () -> Unit) {
    val context = LocalContext.current
    var bubbleStyle by remember { mutableStateOf(AppearancePrefs.bubbleStyle(context)) }
    var density by remember { mutableStateOf(AppearancePrefs.density(context)) }
    var immersive by remember { mutableStateOf(AppearancePrefs.immersiveActions(context)) }
    PreferenceGroup {
        GroupLabel("气泡样式")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf("paper" to "纸面", "bubble" to "气泡").forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = bubbleStyle == v,
                    onClick = { bubbleStyle = v; AppearancePrefs.saveBubbleStyle(context, v); onChanged() },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        GroupLabel("密度")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf("comfortable" to "舒适", "compact" to "紧凑").forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = density == v,
                    onClick = { density = v; AppearancePrefs.saveDensity(context, v); onChanged() },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SwitchPrefRow(
            title = "沉浸模式",
            subtitle = "隐藏消息常驻操作按钮：开 = 全部操作收进长按菜单",
            checked = immersive,
            onToggle = { immersive = it; AppearancePrefs.setImmersiveActions(context, it); onChanged() },
        )
    }
}

/** Toast 简写。 */
private object Toast {
    fun show(context: android.content.Context, msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
