package com.emberinn.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.data.OnboardingPrefs
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.InkText
import com.emberinn.app.ui.design.components.InkTier
import com.emberinn.app.ui.home.HomeViewModel
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.engine.card.CardFormat
import androidx.compose.ui.unit.sp

private data class TabSpec(val label: String, val icon: ImageVector)

/**
 * 底部导航三域（DESIGN_SYSTEM §6.3 IA）：聊天居左、世界（角色+世界书）居中、设置居右。
 */
private val Tabs = listOf(
    TabSpec("聊天", FaIcons.Comments),
    TabSpec("世界", FaIcons.Globe),
    TabSpec("设置", FaIcons.Gear),
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    // README 启动行为：启动直接进入上次聊天（默认关）
    val initialLastSession = AppearancePrefs.openLastChat(context) && AppearancePrefs.lastSessionId(context).isNotBlank()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openSessionId by rememberSaveable { mutableStateOf(if (initialLastSession) AppearancePrefs.lastSessionId(context) else null) }
    var openName by rememberSaveable { mutableStateOf("") }
    var openDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsDeepLink by rememberSaveable { mutableStateOf<String?>(null) }
    val homeVm: HomeViewModel = viewModel()

    fun openSession(id: String) {
        openSessionId = id
        AppearancePrefs.saveLastSessionId(context, id)
    }
    var showOnboarding by rememberSaveable { mutableStateOf(!OnboardingPrefs.done(context)) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching {
                val name = displayName(context, it)
                val mime = context.contentResolver.getType(it)
                val format = detectFormat(name, mime)
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } ?: return@let
                homeVm.importCard(bytes, format)
                showOnboarding = false
                OnboardingPrefs.markDone(context)
                Toast.makeText(context, "已导入角色卡，可以开始聊天了", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showOnboarding) {
        com.emberinn.app.ui.onboarding.OnboardingScreen(
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onDirectChat = {
                val session = homeVm.newSession(null, "AI 对话")
                openSession(session.id)
                openName = session.name
                showOnboarding = false
                OnboardingPrefs.markDone(context)
            },
            onSkip = {
                showOnboarding = false
                OnboardingPrefs.markDone(context)
            },
        )
        return
    }

    val detailId = openDetailId
    if (detailId != null) {
        val detailRecord = homeVm.characters.value.firstOrNull { it.id == detailId }
        if (detailRecord != null) {
            com.emberinn.app.ui.home.CharacterDetailScreen(
                record = detailRecord,
                vm = homeVm,
                onBack = { openDetailId = null },
                onOpenChat = { session ->
                    openDetailId = null
                    openSession(session.id)
                    openName = session.name
                },
            )
            return
        }
        // 角色已被删除：退回首页
        androidx.compose.runtime.SideEffect { if (openDetailId != null) openDetailId = null }
    }

    val sessionId = openSessionId
    if (sessionId != null) {
        val wide = LocalConfiguration.current.screenWidthDp >= 840
        if (wide) {
            // 平板/折叠屏双栏：导航轨 + 左侧列表 + 右侧聊天
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TabContent(
                        selectedTab = selectedTab,
                        onOpenChat = { session ->
                            openSession(session.id)
                            openName = session.name
                        },
                        onOpenSettings = { route ->
                            selectedTab = 2
                            if (route != null) settingsDeepLink = route
                        },
                        onOpenDetail = { record -> openDetailId = record.id },
                        settingsDeepLink = settingsDeepLink,
                        onSettingsDeepLinkConsumed = { settingsDeepLink = null },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                ) {
                    com.emberinn.app.ui.chat.ChatScreen(
                        sessionId = sessionId,
                        name = openName,
                        onBack = { openSessionId = null },
                        onOpenSettings = {
                            openSessionId = null
                            settingsDeepLink = "providers"
                            selectedTab = 2
                        },
                        onSwitchSession = { session ->
                            openSession(session.id)
                            openName = session.name
                        },
                    )
                }
            }
            return
        }
        com.emberinn.app.ui.chat.ChatScreen(
            sessionId = sessionId,
            name = openName,
            onBack = { openSessionId = null },
            // 聊天页里跳设置：先退出聊天（否则被早退逻辑挡住），并深链到“提供商与模型”页
            onOpenSettings = {
                openSessionId = null
                settingsDeepLink = "providers"
                selectedTab = 2
            },
            onSwitchSession = { session ->
                openSession(session.id)
                openName = session.name
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(EmberTheme.colors.bg)) {
        // 状态栏内边距在此统一处理（旧 Scaffold innerPadding 的等价物）；各屏列表自留底栏空间
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            TabContent(
                selectedTab = selectedTab,
                onOpenChat = { session ->
                    openSession(session.id)
                    openName = session.name
                },
                onOpenSettings = { route ->
                    selectedTab = 2
                    if (route != null) settingsDeepLink = route
                },
                onOpenDetail = { record -> openDetailId = record.id },
                settingsDeepLink = settingsDeepLink,
                onSettingsDeepLinkConsumed = { settingsDeepLink = null },
            )
        }
        // 底部导航浮在内容上（内容自管底部留白）
        EmberBottomNav(
            tabs = Tabs,
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 平板导航轨（EmberDS 版）。 */
@Composable
private fun NavigationRail(selected: Int, onSelect: (Int) -> Unit) {
    val c = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(c.bgTint.copy(alpha = 0.5f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tabs.forEachIndexed { index, tab ->
            val active = selected == index
            RailItem(tab.label, tab.icon, active) { onSelect(index) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = EmberTheme.colors
    val tint = if (active) c.accent else c.inkMute
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/**
 * 玻璃底栏（EmberDS 版）：bg 半透明 + 发丝顶线，胶囊指示随选中淡入，
 * 选中态 accent 提亮、未选中弱化——chrome 退后。
 */
@Composable
private fun EmberBottomNav(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EmberTheme.colors
    Column(modifier = modifier) {
        // 发丝顶线：宽=屏宽、高=1dp。不能用 fillMaxSize()——会把最小高度锁成全屏高。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(c.line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface.copy(alpha = 0.88f))
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = selected == index
                val pillAlpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(EmberTheme.motion.sheetMs),
                    label = "navPill",
                )
                val tint = if (active) c.accent else c.inkMute
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.accent.copy(alpha = 0.14f * pillAlpha)),
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    InkText(
                        tab.label,
                        tier = if (active) InkTier.Primary else InkTier.Mute,
                        sizeSp = 11f,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    onOpenChat: (SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onOpenDetail: (com.emberinn.app.data.CharacterRecord) -> Unit,
    settingsDeepLink: String?,
    onSettingsDeepLinkConsumed: () -> Unit,
) {
    when (selectedTab) {
        0 -> com.emberinn.app.ui.sessions.SessionsScreen(
            onOpenSession = onOpenChat,
        )
        1 -> com.emberinn.app.ui.home.CharactersScreen(
            onOpenChat = onOpenChat,
            onOpenSettings = onOpenSettings,
            onOpenDetail = onOpenDetail,
        )
        else -> com.emberinn.app.ui.settings.SettingsScreen(
            deepLink = settingsDeepLink,
            onDeepLinkConsumed = onSettingsDeepLinkConsumed,
        )
    }
}


private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private fun detectFormat(name: String?, mime: String?): CardFormat = when {
    name?.endsWith(".png", ignoreCase = true) == true -> CardFormat.PNG
    name?.endsWith(".charx", ignoreCase = true) == true -> CardFormat.CHARX
    name?.endsWith(".byaf", ignoreCase = true) == true -> CardFormat.BYAF
    name?.endsWith(".yaml", ignoreCase = true) == true || name?.endsWith(".yml", ignoreCase = true) == true -> CardFormat.YAML
    name?.endsWith(".json", ignoreCase = true) == true -> CardFormat.JSON
    mime == "image/png" -> CardFormat.PNG
    mime?.contains("json", ignoreCase = true) == true -> CardFormat.JSON
    else -> CardFormat.JSON
}
