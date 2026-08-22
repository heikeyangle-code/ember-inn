package com.emberinn.app.ui.settings

import com.emberinn.app.ui.icons.FaIcons
import android.content.Intent
import androidx.activity.compose.BackHandler
import com.emberinn.app.ui.components.edgeSwipeBack
import com.emberinn.app.ui.components.emberGlass
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.components.EmberTextField

private enum class SettingsPage { HOME, AI_RESPONSE, PROVIDERS, PROVIDER_DETAIL, ADVANCED_FORMATTING, WORLD_INFO, USER_SETTINGS, APPEARANCE, BACKGROUNDS, PERSONAS, TYPOGRAPHY, RENDER, EXTENSIONS, INTERACTIVE, VOICE, SERVICES, QUICK_REPLIES, MEMORY, CAPTION, EXPRESSION, REGEX, DATA, ABOUT, AUTHORS_NOTE, PRESETS, PROMPT_MANAGER }

/** 设置入口：对照官方 SillyTavern 移动端 8 分区抽屉（AI 响应配置 / API 连接 / 高级格式化 / 世界书 / 用户设置 / 背景 / 扩展 / 人设管理）。 */
@Composable
fun SettingsScreen(
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    vm: ProviderViewModel = viewModel(),
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    // 导航栈：子页返回回真实上一级（旧实现所有子页返回都跳 HOME，二级页如“排版”返回越过“用户设置”）。
    // 主页列表位置/搜索词提升到本层常驻：切子页再返回不再重置滚动位置。
    val navStack = remember { mutableStateListOf<SettingsPage>() }
    val homeListState = rememberLazyListState()
    var homeQuery by rememberSaveable { mutableStateOf("") }

    fun open(target: SettingsPage) {
        if (target == page) return
        navStack.add(page)
        page = target
    }

    fun goBack() {
        page = navStack.removeLastOrNull() ?: SettingsPage.HOME
    }

    // 一键深链：聊天页“先选一个模型”→ 直接进 API 连接分区
    LaunchedEffect(deepLink) {
        when (deepLink) {
            "providers" -> open(SettingsPage.PROVIDERS)
            "ai" -> open(SettingsPage.AI_RESPONSE)
            "formatting" -> open(SettingsPage.ADVANCED_FORMATTING)
            "worldinfo" -> open(SettingsPage.WORLD_INFO)
            "user" -> open(SettingsPage.USER_SETTINGS)
            "appearance" -> open(SettingsPage.USER_SETTINGS)
            "backgrounds" -> open(SettingsPage.BACKGROUNDS)
            "personas" -> open(SettingsPage.PERSONAS)
            "voice" -> open(SettingsPage.VOICE)
            "services" -> open(SettingsPage.SERVICES)
            "quickreplies" -> open(SettingsPage.QUICK_REPLIES)
            "memory" -> open(SettingsPage.MEMORY)
            "caption" -> open(SettingsPage.CAPTION)
            "expression" -> open(SettingsPage.EXPRESSION)
            "regex" -> open(SettingsPage.REGEX)
            "about" -> open(SettingsPage.ABOUT)
            "data" -> open(SettingsPage.DATA)
            else -> {}
        }
        onDeepLinkConsumed()
    }

    // 系统返回 + 边缘滑动返回（栈逐级返回；主页返回交给系统退出）
    BackHandler(enabled = page != SettingsPage.HOME) { goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .edgeSwipeBack(enabled = page != SettingsPage.HOME, onBack = ::goBack),
    ) {
    when (page) {
        SettingsPage.AI_RESPONSE -> AiResponseScreen(
            vm = vm,
            onBack = ::goBack,
            onOpenPresets = { open(SettingsPage.PRESETS) },
            onOpenPromptManager = { open(SettingsPage.PROMPT_MANAGER) },
            onOpenProviders = { open(SettingsPage.PROVIDERS) },
        )
        SettingsPage.PROVIDERS -> ProviderListScreen(
            vm = vm,
            onOpenDetail = { id ->
                providerId = id
                open(SettingsPage.PROVIDER_DETAIL)
            },
            onBack = ::goBack,
        )
        SettingsPage.PROVIDER_DETAIL -> {
            val id = providerId
            if (id != null) {
                ProviderDetailScreen(vm = vm, providerId = id, onBack = ::goBack)
            }
        }
        SettingsPage.ADVANCED_FORMATTING -> PresetsScreen(onBack = ::goBack)
        SettingsPage.WORLD_INFO -> WorldInfoScreen(onBack = ::goBack)
        SettingsPage.USER_SETTINGS -> UserSettingsScreen(
            onBack = ::goBack,
            onOpenAppearance = { open(SettingsPage.APPEARANCE) },
            onOpenTypography = { open(SettingsPage.TYPOGRAPHY) },
            onOpenRender = { open(SettingsPage.RENDER) },
            onOpenData = { open(SettingsPage.DATA) },
            onOpenAbout = { open(SettingsPage.ABOUT) },
        )
        SettingsPage.APPEARANCE -> AppearanceScreen(
            onBack = ::goBack,
        )
        SettingsPage.BACKGROUNDS -> BackgroundsScreen(onBack = ::goBack)
        SettingsPage.PERSONAS -> PersonaSettingsScreen(onBack = ::goBack)
        SettingsPage.TYPOGRAPHY -> TextTypographyScreen(onBack = ::goBack)
        SettingsPage.RENDER -> MessageRenderScreen(onBack = ::goBack)
        SettingsPage.EXTENSIONS -> ExtensionsHubScreen(
            onBack = ::goBack,
            onOpenServices = { open(SettingsPage.SERVICES) },
            onOpenVoice = { open(SettingsPage.VOICE) },
            onOpenQuickReplies = { open(SettingsPage.QUICK_REPLIES) },
            onOpenMemory = { open(SettingsPage.MEMORY) },
            onOpenCaption = { open(SettingsPage.CAPTION) },
            onOpenExpression = { open(SettingsPage.EXPRESSION) },
            onOpenRegex = { open(SettingsPage.REGEX) },
            onOpenInteractive = { open(SettingsPage.INTERACTIVE) },
            onOpenAuthorsNote = { open(SettingsPage.AUTHORS_NOTE) },
            onOpenData = { open(SettingsPage.DATA) },
        )
        SettingsPage.INTERACTIVE -> ExtensionsScreen(onBack = ::goBack)
        SettingsPage.VOICE -> VoiceScreen(onBack = ::goBack)
        SettingsPage.SERVICES -> ServicesScreen(onBack = ::goBack)
        SettingsPage.QUICK_REPLIES -> QuickRepliesScreen(onBack = ::goBack)
        SettingsPage.MEMORY -> MemoryScreen(onBack = ::goBack)
        SettingsPage.CAPTION -> CaptionScreen(onBack = ::goBack)
        SettingsPage.EXPRESSION -> ExpressionScreen(onBack = ::goBack)
        SettingsPage.REGEX -> RegexScreen(onBack = ::goBack)
        SettingsPage.AUTHORS_NOTE -> AuthorsNoteSettingsScreen(onBack = ::goBack)
        SettingsPage.PRESETS -> PresetsScreen(onBack = ::goBack)
        SettingsPage.PROMPT_MANAGER -> PromptManagerScreen(onBack = ::goBack)
        SettingsPage.DATA -> DataPrivacyScreen(onBack = ::goBack)
        SettingsPage.ABOUT -> AboutScreen(onBack = ::goBack)
        else -> SettingsHome(
            vm = vm,
            listState = homeListState,
            query = homeQuery,
            onQueryChange = { homeQuery = it },
            onOpenAiResponse = { open(SettingsPage.AI_RESPONSE) },
            onOpenProviders = { open(SettingsPage.PROVIDERS) },
            onOpenFormatting = { open(SettingsPage.ADVANCED_FORMATTING) },
            onOpenWorldInfo = { open(SettingsPage.WORLD_INFO) },
            onOpenUserSettings = { open(SettingsPage.USER_SETTINGS) },
            onOpenBackgrounds = { open(SettingsPage.BACKGROUNDS) },
            onOpenExtensionsHub = { open(SettingsPage.EXTENSIONS) },
            onOpenPersonas = { open(SettingsPage.PERSONAS) },
            onOpenTypography = { open(SettingsPage.TYPOGRAPHY) },
            onOpenRender = { open(SettingsPage.RENDER) },
            onOpenVoice = { open(SettingsPage.VOICE) },
            onOpenServices = { open(SettingsPage.SERVICES) },
            onOpenQuickReplies = { open(SettingsPage.QUICK_REPLIES) },
            onOpenMemory = { open(SettingsPage.MEMORY) },
            onOpenCaption = { open(SettingsPage.CAPTION) },
            onOpenExpression = { open(SettingsPage.EXPRESSION) },
            onOpenRegex = { open(SettingsPage.REGEX) },
            onOpenAuthorsNote = { open(SettingsPage.AUTHORS_NOTE) },
            onOpenPresets = { open(SettingsPage.PRESETS) },
            onOpenPromptManager = { open(SettingsPage.PROMPT_MANAGER) },
            onOpenData = { open(SettingsPage.DATA) },
            onOpenAbout = { open(SettingsPage.ABOUT) },
        )
    }
    }
}

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** 官方移动端 8 分区抽屉顺序（index.html #top-settings-holder）；hue 为该分区图标块的专属色调。 */
private data class OfficialSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val hue: Color,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsHome(
    vm: ProviderViewModel,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenAiResponse: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenFormatting: () -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenUserSettings: () -> Unit,
    onOpenBackgrounds: () -> Unit,
    onOpenExtensionsHub: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenTypography: () -> Unit,
    onOpenRender: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenCaption: () -> Unit,
    onOpenExpression: () -> Unit,
    onOpenRegex: () -> Unit,
    onOpenAuthorsNote: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenPromptManager: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val context = LocalContext.current
    val activeProfile = profiles.firstOrNull { it.id == activeId }
    val providerSummary = activeProfile?.let { p ->
        val spec = vm.providers.firstOrNull { it.id == p.providerId }
        buildString {
            append(spec?.displayName ?: p.providerId)
            if (p.model.isNotBlank()) append(" · ").append(p.model)
        }
    } ?: "未配置"

    // 官方 8 分区（顺序对照官方 index.html 顶部抽屉栏）；图标块按分区配色，
    // 页面不再一水灰——每张卡自带身份色（低饱和 tint，克制不花哨）
    val sectionHue = MaterialTheme.colorScheme
    val sections = listOf(
        OfficialSection("AI 响应配置", "参数预设 · 采样器 · 快速提示词 · Prompt Manager", FaIcons.Gear, sectionHue.primary) { onOpenAiResponse() },
        OfficialSection("API 连接", providerSummary, FaIcons.Link, sectionHue.tertiary) { onOpenProviders() },
        OfficialSection("高级格式化", "上下文 · 指导 · 系统提示 · 推理 · Master 导入导出", FaIcons.Pencil, sectionHue.secondary) { onOpenFormatting() },
        OfficialSection("世界书", "激活世界 · 扫描深度 / 递归 / 预算", FaIcons.BookOpen, Color(0xFF43A047)) { onOpenWorldInfo() },
        OfficialSection("用户设置", "UI 主题 · 个性化 · 聊天/消息处理 · 自动滑动/续写", FaIcons.User, sectionHue.primary) { onOpenUserSettings() },
        OfficialSection("背景", "聊天背景 · 模糊 · 遮罩", FaIcons.Image, Color(0xFF8E24AA)) { onOpenBackgrounds() },
        OfficialSection("扩展", "翻译 · 图像 · 向量 · TTS · 快捷回复 · 正则 · 记忆 …", FaIcons.WandMagicSparkles, Color(0xFFFB8C00)) { onOpenExtensionsHub() },
        OfficialSection("人设管理", "用户设定 · 描述 · 位置 · 连接", FaIcons.User, sectionHue.secondary) { onOpenPersonas() },
    )

    val visibleSections = remember(sections, query, providerSummary) {
        val q = query.trim()
        if (q.isBlank()) sections
        else sections.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "对照官方移动端布局的八个分区",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "v0.1.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                EmberTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("搜索设置") },
                    leadingIcon = { Icon(FaIcons.MagnifyingGlass, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(FaIcons.XMark, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        items(visibleSections, key = { it.title }) { section ->
            OfficialSectionCard(section)
        }
    }
}

@Composable
private fun OfficialSectionCard(section: OfficialSection) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = section.onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 分区身份色图标块：色调低饱和混合（14% tint），图标取分区色
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(section.hue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = section.hue,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    section.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                FaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 关于页：版本 / 许可 / 仓库 / 本地数据声明。 */
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "关于", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text("EmberInn", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "SillyTavern 兼容的原生 Android 客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 设置页玻璃容器：静态背景层作为顶栏毛玻璃的 sky 源（内容滚动不触发整屏重捕）。 */
@Composable
fun SettingsGlassPage(content: @Composable (com.skydoves.cloudy.Sky) -> Unit) {
    val sky = rememberSky()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(EmberTheme.colors.bg),
        )
        content(sky)
    }
}

/** 设置子页通用顶栏（玻璃模式：传 sky 后为毛玻璃 + 边缘高光；不传保持透明）。 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    sky: com.skydoves.cloudy.Sky? = null,
) {
    Surface(
        color = if (sky != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.16f) else Color.Transparent,
        shadowElevation = if (sky != null) 1.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .emberGlass(sky = sky, atTop = false),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        ) {
            // 返回按钮在左上角，但留足上下间距（避免贴最高处被状态栏遮挡）
            IconButton(onClick = onBack) {
                Icon(FaIcons.ArrowLeft, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }
    }
}
