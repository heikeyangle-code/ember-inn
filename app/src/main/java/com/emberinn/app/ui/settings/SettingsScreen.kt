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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import com.emberinn.app.ui.design.EmberTheme

private enum class SettingsPage { HOME, AI_RESPONSE, PROVIDERS, PROVIDER_DETAIL, ADVANCED_FORMATTING, WORLD_INFO, USER_SETTINGS, APPEARANCE, BACKGROUNDS, PERSONAS, RENDER, EXTENSIONS, TAVERN_HELPER, VOICE, SERVICES, QUICK_REPLIES, MEMORY, CAPTION, EXPRESSION, REGEX, DATA, ABOUT, AUTHORS_NOTE, PRESETS, PROMPT_MANAGER }

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
            onOpenRender = { open(SettingsPage.RENDER) },
            onOpenData = { open(SettingsPage.DATA) },
            onOpenAbout = { open(SettingsPage.ABOUT) },
        )
        SettingsPage.APPEARANCE -> AppearanceScreen(
            onBack = ::goBack,
        )
        SettingsPage.BACKGROUNDS -> BackgroundsScreen(onBack = ::goBack)
        SettingsPage.PERSONAS -> PersonaSettingsScreen(onBack = ::goBack)
        SettingsPage.RENDER -> MessageRenderScreen(onBack = ::goBack)
        SettingsPage.TAVERN_HELPER -> TavernHelperScreen(onBack = ::goBack)
        SettingsPage.EXTENSIONS -> ExtensionsHubScreen(
            onBack = ::goBack,
            onOpenServices = { open(SettingsPage.SERVICES) },
            onOpenVoice = { open(SettingsPage.VOICE) },
            onOpenQuickReplies = { open(SettingsPage.QUICK_REPLIES) },
            onOpenMemory = { open(SettingsPage.MEMORY) },
            onOpenCaption = { open(SettingsPage.CAPTION) },
            onOpenExpression = { open(SettingsPage.EXPRESSION) },
            onOpenRegex = { open(SettingsPage.REGEX) },
            onOpenTavernHelper = { open(SettingsPage.TAVERN_HELPER) },
            onOpenAuthorsNote = { open(SettingsPage.AUTHORS_NOTE) },
            onOpenData = { open(SettingsPage.DATA) },
        )
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
            onOpenTavernHelper = { open(SettingsPage.TAVERN_HELPER) },
            onOpenPersonas = { open(SettingsPage.PERSONAS) },
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

/** 官方移动端分区顺序（index.html #top-settings-holder）。value 只放实时状态，不放静态功能清单。 */
private data class OfficialSection(
    val title: String,
    val value: String?,
    val icon: ImageVector,
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
    onOpenTavernHelper: () -> Unit,
    onOpenPersonas: () -> Unit,
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

    // 官方移动端分区顺序（index.html #top-settings-holder）；副文本只留实时状态真值
    val sections = listOf(
        OfficialSection("AI 响应配置", null, FaIcons.Sliders) { onOpenAiResponse() },
        OfficialSection("API 连接", providerSummary, FaIcons.Link) { onOpenProviders() },
        OfficialSection("高级格式化", null, FaIcons.Pencil) { onOpenFormatting() },
        OfficialSection("世界书", null, FaIcons.BookOpen) { onOpenWorldInfo() },
        OfficialSection("用户设置", null, FaIcons.User) { onOpenUserSettings() },
        OfficialSection("背景", null, FaIcons.Image) { onOpenBackgrounds() },
        OfficialSection("扩展", null, FaIcons.WandMagicSparkles) { onOpenExtensionsHub() },
        OfficialSection("酒馆助手", null, FaIcons.WandMagicSparkles) { onOpenTavernHelper() },
        OfficialSection("人设管理", null, FaIcons.Users) { onOpenPersonas() },
    )

    val visibleSections = remember(sections, query, providerSummary) {
        val q = query.trim()
        if (q.isBlank()) sections
        else sections.filter { it.title.contains(q, true) || (!it.value.isNullOrBlank() && it.value.contains(q, true)) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        item {
            Column {
                Text("设置", color = EmberTheme.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.dp))
                com.emberinn.app.ui.design.components.SearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "搜索设置项",
                )
            }
        }
        items(visibleSections, key = { it.title }) { section ->
            SectionRow(section)
        }
    }
}

/** 分区行（§4.6）：图标块=内容面圆角方，行无边框无线，右端只放实时值。 */
@Composable
private fun SectionRow(section: OfficialSection) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = section.onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(section.icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(section.title, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (!section.value.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                section.value!!,
                color = c.inkMute,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.ink.copy(alpha = 0.22f), modifier = Modifier.size(14.dp))
    }
}

/** 关于页（新语言）：版本 / 许可 / 仓库 / 本地数据声明，E0 平面行式。 */
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = EmberTheme.colors
    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "关于", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text("EmberInn", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "SillyTavern 兼容的原生 Android 客户端",
                color = c.inkMute,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                InfoLine("版本", "0.1.0")
                InfoLine("开源许可", "AGPL-3.0")
                InfoLine("数据", "默认只保存在本机")
                InfoLine("仓库", "github.com/heikeyangle-code/ember-inn")
            }
            Text(
                "访问开源仓库",
                color = c.accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/heikeyangle-code/ember-inn"))
                            )
                        }
                    }
                    .padding(4.dp),
            )
        }
    }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val c = EmberTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = c.inkMute, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** 设置页容器（新语言）：纯页底平面，无玻璃重捕层。sky 形参保留以兼容既有调用点。 */
@Composable
fun SettingsGlassPage(content: @Composable (com.skydoves.cloudy.Sky) -> Unit) {
    val sky = rememberSky()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmberTheme.colors.bg),
    ) {
        content(sky)
    }
}

/** 设置子页通用顶栏（新语言 §4.6）：透明平面 + 弱墨返回粒 + 墨阶标题，无实底无投影。 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    sky: com.skydoves.cloudy.Sky? = null,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
    ) {
        // 返回按钮在左上角，但留足上下间距（避免贴最高处被状态栏遮挡）
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(FaIcons.ChevronLeft, contentDescription = "返回", tint = c.inkMute, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = c.inkMute, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}
