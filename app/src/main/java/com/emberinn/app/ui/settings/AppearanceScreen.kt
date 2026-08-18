package com.emberinn.app.ui.settings


import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberSlider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.ui.theme.ThemeMode
import com.emberinn.app.ui.theme.ThemePreset
import com.emberinn.app.ui.theme.ThemePresets
import com.emberinn.app.ui.theme.VibePrefs
import com.emberinn.app.ui.theme.VibePreset
import com.emberinn.app.ui.theme.VibePresets
import com.emberinn.app.data.FontManager
import com.emberinn.app.ui.settings.AppearancePrefs
import kotlinx.coroutines.launch

/** 外观与主题：README 三层主题的第一层（全局）。选预设即全局实时生效。 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    themePreset: ThemePreset,
    vibe: VibePreset,
    onVibeChanged: (VibePreset) -> Unit,
    onAppearanceChanged: () -> Unit = {},
    onThemeChanged: (ThemeMode, ThemePreset) -> Unit,
    onBack: () -> Unit,
) {
    val appearanceContext = LocalContext.current
    var radius by remember { mutableStateOf(AppearancePrefs.radius(appearanceContext)) }
    var font by remember { mutableStateOf(AppearancePrefs.font(appearanceContext)) }
    val fontScope = rememberCoroutineScope()
    var fontDownloading by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf<String?>(null) }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "外观与主题", onBack = onBack, sky = settingsSky)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("主题", "主题模式与 11 套预设，点选立即全局生效")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("主题模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { onThemeChanged(mode, themePreset) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("预设主题", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "点选立即生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ThemePresets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.id == themePreset.id,
                    onClick = { onThemeChanged(themeMode, preset) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("视觉与质感", "氛围滤镜、圆角字体、头像、文字阴影与玻璃")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val vibeContext = LocalContext.current
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("视觉氛围", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "配色性格：饱和度 / 冷暖 / 光效，全部可调，默认标准无滤镜",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            VibePresets.forEach { p ->
                                FilterChip(
                                    selected = vibe.id == p.id,
                                    onClick = {
                                        // 只更新状态，持久化由 MainActivity 的 onVibeChanged 统一负责（修复双重写盘）
                                        if (p.id == "custom") {
                                            onVibeChanged(VibePrefs.resolve(vibeContext).copy(id = "custom"))
                                        } else {
                                            onVibeChanged(p)
                                        }
                                    },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                        // 实时色板预览：读当前主题 scheme，拖滑杆/换滤镜立即在此可见（也整页可见）
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            listOf(
                                "底色" to MaterialTheme.colorScheme.background,
                                "卡片" to MaterialTheme.colorScheme.surfaceContainer,
                                "主色" to MaterialTheme.colorScheme.primary,
                                "次色" to MaterialTheme.colorScheme.secondary,
                                "点缀" to MaterialTheme.colorScheme.tertiary,
                            ).forEach { (label, c) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(c),
                                    )
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                        if (vibe.id == "custom") {
                            SliderRow(
                                label = "降饱和",
                                hint = "作用于全部配色（0 = 原样，0.5 = 接近灰）",
                                value = vibe.desaturateLight,
                                range = 0f..0.5f,
                            ) { v ->
                                onVibeChanged(VibePreset("custom", "自定义", "手动调节三项参数", v, v, vibe.warmth, vibe.glow))
                            }
                            SliderRow(
                                label = "冷暖",
                                hint = "左冷右暖，作用于全部配色",
                                value = vibe.warmth,
                                range = -0.25f..0.25f,
                            ) { v ->
                                onVibeChanged(VibePreset("custom", "自定义", "手动调节三项参数", vibe.desaturateLight, vibe.desaturateDark, v, vibe.glow))
                            }
                            SliderRow(
                                label = "光效",
                                hint = "空状态装饰与阴影强度（0 = 关闭）",
                                value = vibe.glow,
                                range = 0f..1f,
                            ) { v ->
                                onVibeChanged(VibePreset("custom", "自定义", "手动调节三项参数", vibe.desaturateLight, vibe.desaturateDark, vibe.warmth, v))
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("全局圆角", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        ) {
                            listOf("default" to "系统", "square" to "方正 4dp", "rounded" to "圆润 16dp", "circle" to "浑圆 24dp").forEach { (v, label) ->
                                FilterChip(selected = radius == v, onClick = { radius = v; AppearancePrefs.save(appearanceContext, radius, font); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                        Text("全局字体", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        ) {
                            listOf(
                                "default" to "系统",
                                "serif" to "衬线（思源宋体近似）",
                                "noto" to "Noto Sans（官方·下载）",
                            ).forEach { (v, label) ->
                                val fontReady = when (v) {
                                    "noto" -> FontManager.notoReady(appearanceContext)
                                    else -> true
                                }
                                FilterChip(
                                    selected = font == v,
                                    enabled = fontReady || v == "noto",
                                    onClick = {
                                        when {
                                            v == "noto" && !fontReady -> {
                                                font = "noto"
                                                fontScope.launch {
                                                    fontDownloading = true
                                                    val result = FontManager.ensureNoto(appearanceContext)
                                                    fontDownloading = false
                                                    result.onSuccess {
                                                        AppearancePrefs.save(appearanceContext, radius, "noto")
                                                        onAppearanceChanged()
                                                    }.onFailure { e ->
                                                        font = AppearancePrefs.font(appearanceContext)
                                                        fontError = e.message ?: "未知错误"
                                                    }
                                                }
                                            }
                                            else -> {
                                                font = v
                                                AppearancePrefs.save(appearanceContext, radius, v)
                                                onAppearanceChanged()
                                            }
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val fxContext = LocalContext.current
                var shadowOn by remember { mutableStateOf(AppearancePrefs.textShadowEnabled(fxContext)) }
                var shadowStrength by remember { mutableStateOf(AppearancePrefs.textShadowStrength(fxContext)) }
                var avatarShape by remember { mutableStateOf(AppearancePrefs.avatarShape(fxContext)) }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("头像形状", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "官方默认方形 2px；圆角 10px；圆形 50%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        ) {
                            listOf("circle" to "圆形 50%", "rounded" to "圆角 10px", "square" to "方形 2px（官方）").forEach { (v, label) ->
                                FilterChip(
                                    selected = avatarShape == v,
                                    onClick = { avatarShape = v; AppearancePrefs.saveAvatarShape(fxContext, v); onAppearanceChanged() },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text("文字阴影", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "对齐官方 style.css：全站文字 0 0 2px 黑 50% 阴影（--SmartThemeShadowColor）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            EmberSwitch(
                                checked = shadowOn,
                                onCheckedChange = { shadowOn = it; AppearancePrefs.saveTextShadowEnabled(fxContext, it); onAppearanceChanged() },
                            )
                        }
                        EmberSlider(
                            value = shadowStrength.toFloat(),
                            onValueChange = { shadowStrength = it.toInt(); AppearancePrefs.saveTextShadowStrength(fxContext, it.toInt()); onAppearanceChanged() },
                            valueRange = 0f..4f,
                            steps = 3,
                        )
                        Text("强度：$shadowStrength px", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val optContext = LocalContext.current
                var blur by remember { mutableStateOf(AppearancePrefs.backgroundBlur(optContext)) }
                var blurStrength by remember { mutableStateOf(AppearancePrefs.blurStrength(optContext)) }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { blur = !blur; AppearancePrefs.saveBackgroundBlur(optContext, blur); onAppearanceChanged() }.padding(vertical = 6.dp),
                        ) {
                        Column(modifier = Modifier.weight(1f)) {
                        Text("背景模糊（玻璃表面）", style = MaterialTheme.typography.bodyLarge)
                        Text("顶栏 / 输入栏 / 浮层的 Cloudy 毛玻璃总开关", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EmberSwitch(checked = blur, onCheckedChange = { blur = it; AppearancePrefs.saveBackgroundBlur(optContext, it); onAppearanceChanged() })
                        }
                        if (blur) {
                            // 强度与开关同卡：玻璃的所有控制集中一处，不再散落到消息渲染页
                            // 下限 14 = EmberGlassDefaults.MIN_RADIUS（再低玻璃观感消失，官方默认 10 即为此被抬升）
                            Text("模糊强度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            EmberSlider(
                                value = blurStrength.coerceAtLeast(14).toFloat(),
                                onValueChange = { blurStrength = it.toInt(); AppearancePrefs.saveBlurStrength(optContext, it.toInt()); onAppearanceChanged() },
                                valueRange = 14f..40f,
                            )
                            Text("半径 $blurStrength px", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("消息外观", "气泡、密度与消息操作按钮")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val optContext = LocalContext.current
                var bubbleStyle by remember { mutableStateOf(AppearancePrefs.bubbleStyle(optContext)) }
                var density by remember { mutableStateOf(AppearancePrefs.density(optContext)) }
                var immersive by remember { mutableStateOf(AppearancePrefs.immersiveActions(optContext)) }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("气泡样式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        ) {
                            listOf("paper" to "纸面（AI 纯文本流）", "bubble" to "气泡（AI 也带气泡）").forEach { (v, label) ->
                                FilterChip(selected = bubbleStyle == v, onClick = { bubbleStyle = v; AppearancePrefs.saveBubbleStyle(optContext, v); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                        Text("密度", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        ) {
                            listOf("comfortable" to "舒适", "compact" to "紧凑").forEach { (v, label) ->
                                FilterChip(selected = density == v, onClick = { density = v; AppearancePrefs.saveDensity(optContext, v); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { immersive = !immersive; AppearancePrefs.setImmersiveActions(optContext, immersive); onAppearanceChanged() }
                                .padding(vertical = 6.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("沉浸模式", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "隐藏消息常驻操作按钮：关=最后一条 AI 消息常驻 4 键；开=全部操作收进长按菜单",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            EmberSwitch(checked = immersive, onCheckedChange = { immersive = it; AppearancePrefs.setImmersiveActions(optContext, it); onAppearanceChanged() })
                        }
                    }
                }
            }

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
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderRow(
    label: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(String.format("%.2f", value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EmberSlider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Card(onClick) 会把波纹裁进 shape；固定 20dp 圆角，避免选中不同形状主题时卡片四角跟着变
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) BorderStroke(2.dp, preset.metal ?: MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 预览色板：seed/secondary/tertiary 圆点 + 宝石菱形 + 金属高光环
                listOfNotNull(
                    preset.seed,
                    preset.secondary,
                    preset.tertiary,
                    preset.gem,
                    preset.metal,
                ).forEachIndexed { index, color ->
                    val isGem = index == 3 && preset.gem != null
                    val isMetal = index >= 4 && preset.metal != null
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .let { m ->
                                when {
                                    isGem -> m.graphicsLayer { rotationZ = 45f }.clip(RoundedCornerShape(4.dp))
                                    isMetal -> m.clip(CircleShape).drawBehind {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.45f),
                                            radius = size.minDimension / 2f - 2.dp.toPx(),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                                        )
                                    }
                                    else -> m.clip(CircleShape)
                                }
                            }
                            .background(color),
                    )
                    if (index < 4) Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                preset.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                preset.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}


