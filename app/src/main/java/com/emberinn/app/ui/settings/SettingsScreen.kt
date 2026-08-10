package com.emberinn.app.ui.settings

import com.emberinn.app.ui.icons.PhosphorIcons
import android.content.Intent
import androidx.activity.compose.BackHandler
import com.emberinn.app.ui.components.edgeSwipeBack
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.ui.theme.ThemeMode
import com.emberinn.app.ui.theme.ThemePreset
import com.emberinn.app.ui.theme.VibePreset
import com.emberinn.app.ui.theme.VibePresets

private enum class SettingsPage { HOME, PROVIDERS, PROVIDER_DETAIL, APPEARANCE, TYPOGRAPHY, RENDER, EXTENSIONS, VOICE, SERVICES, QUICK_REPLIES, WORLD_INFO, REGEX, DATA, ABOUT }

/** 设置入口：README 信息架构（分组 + 搜索 + 常用区），子页：提供商 / 外观主题 / 关于。 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    themePreset: ThemePreset,
    vibe: VibePreset = VibePresets.first(),
    onVibeChanged: (VibePreset) -> Unit = {},
    onAppearanceChanged: () -> Unit = {},
    onThemeChanged: (ThemeMode, ThemePreset) -> Unit,
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    vm: ProviderViewModel = viewModel(),
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }

    // 一键深链：聊天页“先选一个模型”→ 直接进提供商与模型页
    LaunchedEffect(deepLink) {
        when (deepLink) {
            "providers" -> page = SettingsPage.PROVIDERS
            "appearance" -> page = SettingsPage.APPEARANCE
            "voice" -> page = SettingsPage.VOICE
            "services" -> page = SettingsPage.SERVICES
            "quickreplies" -> page = SettingsPage.QUICK_REPLIES
            "worldinfo" -> page = SettingsPage.WORLD_INFO
            "regex" -> page = SettingsPage.REGEX
            "about" -> page = SettingsPage.ABOUT
            "data" -> page = SettingsPage.DATA
            else -> {}
        }
        onDeepLinkConsumed()
    }

    fun goBack() {
        page = when (page) {
            SettingsPage.PROVIDER_DETAIL -> SettingsPage.PROVIDERS
            else -> SettingsPage.HOME
        }
    }

    // 系统返回 + 边缘滑动返回（子页逐级返回；主页返回交给系统退出）
    BackHandler(enabled = page != SettingsPage.HOME) { goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .edgeSwipeBack(enabled = page != SettingsPage.HOME, onBack = ::goBack),
    ) {
    when (page) {
        SettingsPage.PROVIDERS -> ProviderListScreen(
            vm = vm,
            onOpenDetail = { id ->
                providerId = id
                page = SettingsPage.PROVIDER_DETAIL
            },
            onBack = { page = SettingsPage.HOME },
        )
        SettingsPage.PROVIDER_DETAIL -> {
            val id = providerId
            if (id != null) {
                ProviderDetailScreen(vm = vm, providerId = id, onBack = { page = SettingsPage.PROVIDERS })
            }
        }
        SettingsPage.APPEARANCE -> AppearanceScreen(
            themeMode = themeMode,
            themePreset = themePreset,
            vibe = vibe,
            onVibeChanged = onVibeChanged,
            onAppearanceChanged = onAppearanceChanged,
            onThemeChanged = onThemeChanged,
            onBack = { page = SettingsPage.HOME },
        )
        SettingsPage.TYPOGRAPHY -> TextTypographyScreen(onBack = { page = SettingsPage.HOME }, onAppearanceChanged = onAppearanceChanged)
        SettingsPage.RENDER -> MessageRenderScreen(onBack = { page = SettingsPage.HOME }, onAppearanceChanged = onAppearanceChanged)
        SettingsPage.EXTENSIONS -> ExtensionsScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.VOICE -> VoiceScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.SERVICES -> ServicesScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.QUICK_REPLIES -> QuickRepliesScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.WORLD_INFO -> WorldInfoScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.REGEX -> RegexScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.DATA -> DataPrivacyScreen(onBack = { page = SettingsPage.HOME })
        SettingsPage.ABOUT -> AboutScreen(onBack = { page = SettingsPage.HOME })
        else -> SettingsHome(
            vm = vm,
            themeMode = themeMode,
            themePreset = themePreset,
            onOpenProviders = { page = SettingsPage.PROVIDERS },
            onOpenAppearance = { page = SettingsPage.APPEARANCE },
            onOpenTypography = { page = SettingsPage.TYPOGRAPHY },
            onOpenRender = { page = SettingsPage.RENDER },
            onOpenExtensions = { page = SettingsPage.EXTENSIONS },
            onOpenVoice = { page = SettingsPage.VOICE },
            onOpenServices = { page = SettingsPage.SERVICES },
            onOpenQuickReplies = { page = SettingsPage.QUICK_REPLIES },
            onOpenWorldInfo = { page = SettingsPage.WORLD_INFO },
            onOpenRegex = { page = SettingsPage.REGEX },
            onOpenData = { page = SettingsPage.DATA },
            onOpenAbout = { page = SettingsPage.ABOUT },
        )
    }
    }
}

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsHome(
    vm: ProviderViewModel,
    themeMode: ThemeMode,
    themePreset: ThemePreset,
    onOpenProviders: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTypography: () -> Unit,
    onOpenRender: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenRegex: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var showLicense by remember { mutableStateOf(false) }

    val activeProfile = profiles.firstOrNull { it.id == activeId }
    val providerSummary = activeProfile?.let { p ->
        val spec = vm.providers.firstOrNull { it.id == p.providerId }
        buildString {
            append(spec?.displayName ?: p.providerId)
            if (p.model.isNotBlank()) append(" · ").append(p.model)
        }
    } ?: "未配置"

    val themeSummary = "${themePreset.name} · ${themeMode.label}"

    val quickActions = listOf(
        QuickAction("主题", PhosphorIcons.Star, onOpenAppearance),
        QuickAction("模型", PhosphorIcons.Settings, onOpenProviders),
        QuickAction("语音", PhosphorIcons.SpeakerHigh, onOpenVoice),
        QuickAction("备份", PhosphorIcons.Refresh, onOpenData),
    )

    val groups = listOf(
        SettingsGroup(
            "外观与主题",
            listOf(
                SettingRow("主题与视觉", "预设主题 · 视觉氛围 · 圆角 · 字体", Color.Unspecified, onOpenAppearance),
                SettingRow("文字排版", "字号 · 行高 · 标题 · 引用 · 代码 · 间距", Color.Unspecified, onOpenTypography),
                SettingRow("消息渲染（官方字段）", "正文/引用/下划线/气泡/边框/阴影/模糊强度", Color.Unspecified, onOpenRender),
            ),
        ),
        SettingsGroup(
            "提供商与模型",
            listOf(
                SettingRow("提供商与模型", providerSummary, Color.Unspecified, onOpenProviders),
            ),
        ),
        SettingsGroup(
            "语音",
            listOf(
                SettingRow(
                    "语音朗读（TTS）",
                    if (VoicePrefs.enabled(context)) "已启用 · 本机引擎可试听" else "未启用 · 可在语音页开启自动朗读",
                    Color.Unspecified,
                    onOpenVoice,
                ),
            ),
        ),
        SettingsGroup(
            "扩展插件",
            listOf(
                SettingRow("扩展插件（交互 HTML 卡片）", "iframe 渲染器 · 头像类 · 原代码折叠", Color.Unspecified, onOpenExtensions),
            ),
        ),
        SettingsGroup(
            "服务",
            listOf(
                SettingRow("翻译 · 图像 · 向量", "执行层已接入（Libre/DeepL/A1111/gpt-image/RAG）", Color.Unspecified, onOpenServices),
                SettingRow("快捷回复（全局）", "官方 Quick Reply 槽位 · 输入区快捷盘执行", Color.Unspecified, onOpenQuickReplies),
                SettingRow("世界书", "扫描深度 / 递归 / 预算", Color.Unspecified, onOpenWorldInfo),
                SettingRow("正则脚本（全局）", "GLOBAL 分桶 · 用户输入/AI 输出", Color.Unspecified, onOpenRegex),
            ),
        ),
        SettingsGroup(
            "数据与隐私",
            listOf(
                SettingRow("数据仅保存在本地", "存储位置见数据与隐私页", Color.Unspecified, onOpenData),
                SettingRow("备份与导出", "导出 zip · 二次确认", Color.Unspecified, onOpenData),
            ),
        ),
        SettingsGroup(
            "关于",
            listOf(
                SettingRow("版本", "0.1.0", Color.Unspecified),
                SettingRow("开源仓库", "GitHub", Color.Unspecified) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/heikeyangle-code/ember-inn"))
                        )
                    }
                },
                SettingRow("开源许可", "AGPL-3.0", Color.Unspecified) { showLicense = true },
            ),
        ),
    )

    val visibleGroups = remember(groups, query) {
        val q = query.trim()
        groups.mapNotNull { group ->
            val rows = if (q.isBlank()) group.rows else group.rows.filter {
                it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
            }
            if (rows.isNotEmpty()) group.copy(rows = rows) else null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "外观、模型、语音与数据都在这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索设置") },
                    leadingIcon = { Icon(PhosphorIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(PhosphorIcons.Search, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        if (query.isBlank()) {
            item {
                QuickActionsCard(quickActions)
            }
        }
        items(visibleGroups, key = { it.title }) { group ->
            SettingsGroupCard(group)
        }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("开源许可") },
            text = { Text("本软件基于 AGPL-3.0 发布：参考/翻译 SillyTavern 源码，派生义务；分发必须开源。") },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("知道了") }
            },
        )
    }
}


@Composable
private fun QuickActionsCard(actions: List<QuickAction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "常用",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            actions.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { action ->
                        QuickActionCell(action, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionCell(action: QuickAction, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = action.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(action.title, style = MaterialTheme.typography.bodyLarge)
    }
}

private data class SettingRow(
    val title: String,
    val subtitle: String,
    val dot: Color,
    val onClick: (() -> Unit)? = null,
)

private data class SettingsGroup(
    val title: String,
    val rows: List<SettingRow>,
)

@Composable
private fun SettingsGroupCard(group: SettingsGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Text(
                group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            group.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRowView(row)
            }
        }
    }
}

@Composable
private fun SettingRowView(row: SettingRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.onClick != null, onClick = { row.onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        if (row.dot != Color.Unspecified) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(row.dot),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.onClick != null) {
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 关于页：版本 / 许可 / 仓库 / 本地数据声明。 */
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "关于", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text("余烬酒馆", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "EmberInn · 以 SillyTavern 兼容为核心的原生 Android 酒馆客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoLine("版本", "0.1.0")
                    InfoLine("开源许可", "AGPL-3.0")
                    InfoLine("数据", "默认只保存在本机")
                    InfoLine("仓库", "github.com/heikeyangle-code/ember-inn")
                }
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/heikeyangle-code/ember-inn"))
                        )
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("访问开源仓库")
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 设置子页通用顶栏。 */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        // 返回按钮在左上角，但留足上下间距（避免贴最高处被状态栏遮挡）
        IconButton(onClick = onBack) {
            Icon(PhosphorIcons.ArrowLeft, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}
