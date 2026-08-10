package com.emberinn.app.ui.settings


import com.emberinn.app.ui.components.ColorField
import com.emberinn.app.ui.components.EmberSwitch
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
import androidx.compose.material3.Slider
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
                    shape = MaterialTheme.shapes.medium,
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
                SectionLabel("视觉与质感", "氛围滤镜、圆角字体、头像与文字阴影")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val vibeContext = LocalContext.current
                Surface(
                    shape = MaterialTheme.shapes.medium,
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
                                        if (p.id == "custom") {
                                            val resolved = VibePrefs.resolve(vibeContext).copy(id = "custom")
                                            VibePrefs.save(vibeContext, resolved)
                                            onVibeChanged(resolved)
                                        } else {
                                            VibePrefs.save(vibeContext, p)
                                            onVibeChanged(p)
                                        }
                                    },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                        if (vibe.id == "custom") {
                            SliderRow(
                                label = "降饱和",
                                hint = "0 = 取色原样，0.5 = 接近灰",
                                value = vibe.desaturateLight,
                                range = 0f..0.5f,
                            ) { v ->
                                val next = VibePreset("custom", "自定义", "手动调节三项参数", v, v, vibe.warmth, vibe.glow)
                                VibePrefs.save(vibeContext, next)
                                onVibeChanged(next)
                            }
                            SliderRow(
                                label = "冷暖",
                                hint = "左冷右暖",
                                value = vibe.warmth,
                                range = -0.25f..0.25f,
                            ) { v ->
                                val next = VibePreset("custom", "自定义", "手动调节三项参数", vibe.desaturateLight, vibe.desaturateDark, v, vibe.glow)
                                VibePrefs.save(vibeContext, next)
                                onVibeChanged(next)
                            }
                            SliderRow(
                                label = "光效",
                                hint = "空状态装饰与阴影强度（0 = 关闭）",
                                value = vibe.glow,
                                range = 0f..1f,
                            ) { v ->
                                val next = VibePreset("custom", "自定义", "手动调节三项参数", vibe.desaturateLight, vibe.desaturateDark, vibe.warmth, v)
                                VibePrefs.save(vibeContext, next)
                                onVibeChanged(next)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
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
                                "lxgw" to "霞鹜文楷（下载）",
                                "noto" to "Noto Sans（官方·下载）",
                            ).forEach { (v, label) ->
                                val fontReady = when (v) {
                                    "lxgw" -> FontManager.lxgwFile(appearanceContext) != null
                                    "noto" -> FontManager.notoReady(appearanceContext)
                                    else -> true
                                }
                                FilterChip(
                                    selected = font == v,
                                    enabled = fontReady || v == "lxgw" || v == "noto",
                                    onClick = {
                                        when {
                                            v == "lxgw" && !fontReady -> {
                                                font = "lxgw"
                                                fontScope.launch {
                                                    fontDownloading = true
                                                    val result = FontManager.ensureLxgw(appearanceContext)
                                                    fontDownloading = false
                                                    result.onSuccess {
                                                        AppearancePrefs.save(appearanceContext, radius, "lxgw")
                                                        onAppearanceChanged()
                                                    }.onFailure { e ->
                                                        font = AppearancePrefs.font(appearanceContext)
                                                        fontError = e.message ?: "未知错误"
                                                    }
                                                }
                                            }
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
                    shape = MaterialTheme.shapes.medium,
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
                        Slider(
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
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
                    }
                }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val glassContext = LocalContext.current
                var glassOn by remember { mutableStateOf(AppearancePrefs.chatBgAvatarGlass(glassContext)) }
                var bgBlur by remember { mutableStateOf(AppearancePrefs.chatBgBlur(glassContext)) }
                var scrimDark by remember { mutableStateOf(AppearancePrefs.chatBgScrimDark(glassContext)) }
                var scrimLight by remember { mutableStateOf(AppearancePrefs.chatBgScrimLight(glassContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("聊天背景（头像玻璃 + 遮罩）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "优先级：显式背景 > 头像玻璃背景 > 氛围渐变；模糊五档与遮罩保证正文可读",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("头像玻璃背景", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            EmberSwitch(
                                checked = glassOn,
                                onCheckedChange = { glassOn = it; AppearancePrefs.saveChatBgAvatarGlass(glassContext, it); onAppearanceChanged() },
                            )
                        }
                        Text("模糊档位", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0 to "无", 12 to "轻", 24 to "标准", 36 to "重", 48 to "极").forEach { (v, label) ->
                                FilterChip(
                                    selected = bgBlur == v,
                                    onClick = { bgBlur = v; AppearancePrefs.saveChatBgBlur(glassContext, v); onAppearanceChanged() },
                                    label = { Text(label) },
                                )
                            }
                        }
                        ColorField(
                            label = "深色遮罩颜色",
                            hint = "深色主题下盖在背景图上的颜色（例 #000000）",
                            value = AppearancePrefs.chatBgScrimDarkColor(glassContext),
                            onSave = { AppearancePrefs.saveChatBgScrimDarkColor(glassContext, it) },
                        )
                        SliderRow(
                            label = "深色遮罩强度",
                            hint = "不透明度（%），默认 65",
                            value = scrimDark.toFloat(),
                            range = 0f..90f,
                        ) { v ->
                            scrimDark = v.toInt()
                            AppearancePrefs.saveChatBgScrimDark(glassContext, v.toInt())
                            onAppearanceChanged()
                        }
                        ColorField(
                            label = "浅色遮罩颜色",
                            hint = "浅色主题下盖在背景图上的颜色（例 #FFFFFF）",
                            value = AppearancePrefs.chatBgScrimLightColor(glassContext),
                            onSave = { AppearancePrefs.saveChatBgScrimLightColor(glassContext, it) },
                        )
                        SliderRow(
                            label = "浅色遮罩强度",
                            hint = "不透明度（%），默认 30",
                            value = scrimLight.toFloat(),
                            range = 0f..60f,
                        ) { v ->
                            scrimLight = v.toInt()
                            AppearancePrefs.saveChatBgScrimLight(glassContext, v.toInt())
                            onAppearanceChanged()
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            TextButton(onClick = {
                                glassOn = true
                                bgBlur = 24
                                scrimDark = 65
                                scrimLight = 30
                                AppearancePrefs.saveChatBgAvatarGlass(glassContext, true)
                                AppearancePrefs.saveChatBgBlur(glassContext, 24)
                                AppearancePrefs.saveChatBgScrimDark(glassContext, 65)
                                AppearancePrefs.saveChatBgScrimLight(glassContext, 30)
                                AppearancePrefs.saveChatBgScrimDarkColor(glassContext, "#000000")
                                AppearancePrefs.saveChatBgScrimLightColor(glassContext, "#FFFFFF")
                                onAppearanceChanged()
                            }) { Text("恢复默认") }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("消息外观", "气泡、HTML 渲染与文字排版")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val optContext = LocalContext.current
                var bubbleStyle by remember { mutableStateOf(AppearancePrefs.bubbleStyle(optContext)) }
                var density by remember { mutableStateOf(AppearancePrefs.density(optContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
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
                        
                        
                        
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val renderContext = LocalContext.current
                var htmlEnabled by remember { mutableStateOf(RenderPrefs.htmlEnabled(renderContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { htmlEnabled = !htmlEnabled; RenderPrefs.setHtmlEnabled(renderContext, htmlEnabled); onAppearanceChanged() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HTML 消息（WebView 渲染）", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "消息含 HTML 标签时用 WebView 展示；Mermaid 代码块始终走 WebView 兜底",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        EmberSwitch(checked = htmlEnabled, onCheckedChange = { htmlEnabled = it; RenderPrefs.setHtmlEnabled(renderContext, it); onAppearanceChanged() })
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val typeContext = LocalContext.current
                var textSize by remember { mutableStateOf(AppearancePrefs.textSize(typeContext)) }
                var lineHeight by remember { mutableStateOf(AppearancePrefs.lineHeight(typeContext)) }
                var headingStyle by remember { mutableStateOf(AppearancePrefs.headingStyle(typeContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("文字排版", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "聊天正文与标题的层级、字号、行高，全部可调",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("正文字号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("small" to "小 14", "normal" to "标准 16", "official" to "官方 15", "large" to "大 18", "xlarge" to "特大 20").forEach { (v, label) ->
                                FilterChip(selected = textSize == v, onClick = { textSize = v; AppearancePrefs.saveTextSize(typeContext, v); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                        Text("行高", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("compact" to "紧凑 1.4", "normal" to "标准 1.55", "loose" to "宽松 1.7").forEach { (v, label) ->
                                FilterChip(selected = lineHeight == v, onClick = { lineHeight = v; AppearancePrefs.saveLineHeight(typeContext, v); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                        Text("标题层级", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("flat" to "聊天风（标题缩小）", "real" to "正常层级（标题放大）").forEach { (v, label) ->
                                FilterChip(selected = headingStyle == v, onClick = { headingStyle = v; AppearancePrefs.saveHeadingStyle(typeContext, v); onAppearanceChanged() }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("行为与兼容", "沉浸模式、启动行为与官方转义")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val immersiveContext = LocalContext.current
                var immersive by remember { mutableStateOf(AppearancePrefs.immersiveActions(immersiveContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { immersive = !immersive; AppearancePrefs.setImmersiveActions(immersiveContext, immersive); onAppearanceChanged() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("沉浸模式（隐藏消息常驻操作按钮）", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "默认关=最后一条 AI 消息常驻 4 键；开=全部操作收进长按菜单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        EmberSwitch(checked = immersive, onCheckedChange = { immersive = it; AppearancePrefs.setImmersiveActions(immersiveContext, it); onAppearanceChanged() })
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val optContext = LocalContext.current
                var openLastChat by remember { mutableStateOf(AppearancePrefs.openLastChat(optContext)) }
                var encodeTags by remember { mutableStateOf(AppearancePrefs.encodeTags(optContext)) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { openLastChat = !openLastChat; AppearancePrefs.saveOpenLastChat(optContext, openLastChat); onAppearanceChanged() }.padding(vertical = 6.dp),
                        ) {
                        Column(modifier = Modifier.weight(1f)) {
                        Text("启动进入上次聊天", style = MaterialTheme.typography.bodyLarge)
                        Text("默认关；开启后启动直接回到上次会话", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EmberSwitch(checked = openLastChat, onCheckedChange = { openLastChat = it; AppearancePrefs.saveOpenLastChat(optContext, it); onAppearanceChanged() })
                        }
                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { encodeTags = !encodeTags; AppearancePrefs.saveEncodeTags(optContext, encodeTags); onAppearanceChanged() }.padding(vertical = 6.dp),
                        ) {
                        Column(modifier = Modifier.weight(1f)) {
                        Text("转义标签（encode_tags）", style = MaterialTheme.typography.bodyLarge)
                        Text("官方 power_user.encode_tags：显示时把 < > 转义为 &lt; &gt;（默认关）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EmberSwitch(checked = encodeTags, onCheckedChange = { encodeTags = it; AppearancePrefs.saveEncodeTags(optContext, it); onAppearanceChanged() })
                        }
                    }
                }

            }

        }
        if (fontDownloading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("下载字体") },
                text = { Text("正在下载字体（Noto Sans 4 面约 2.2MB / 霞鹜文楷约 70MB），完成后自动应用，请稍候…") },
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
        Slider(value = value, onValueChange = onChange, valueRange = range)
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
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(preset.seed, preset.secondary, preset.tertiary).forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(color),
                    )
                    if (index < 2) Spacer(Modifier.width(6.dp))
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


