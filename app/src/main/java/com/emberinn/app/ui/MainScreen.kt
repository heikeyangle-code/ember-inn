package com.emberinn.app.ui

import com.emberinn.app.ui.icons.PhosphorIcons
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.data.OnboardingPrefs
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.home.HomeViewModel
import com.emberinn.engine.card.CardFormat

private data class TabSpec(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec("角色", PhosphorIcons.Person),
    TabSpec("聊天", PhosphorIcons.List),
    TabSpec("设置", PhosphorIcons.Settings),
)

@Composable
fun MainScreen(
    themeMode: com.emberinn.app.ui.theme.ThemeMode = com.emberinn.app.ui.theme.ThemeMode.SYSTEM,
    themePreset: com.emberinn.app.ui.theme.ThemePreset = com.emberinn.app.ui.theme.ThemePresets.first(),
    vibe: com.emberinn.app.ui.theme.VibePreset = com.emberinn.app.ui.theme.VibePresets.first(),
    onVibeChanged: (com.emberinn.app.ui.theme.VibePreset) -> Unit = {},
    onAppearanceChanged: () -> Unit = {},
    onThemeChanged: (com.emberinn.app.ui.theme.ThemeMode, com.emberinn.app.ui.theme.ThemePreset) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    // README 启动行为：启动直接进入上次聊天（默认关）
    val initialLastSession = AppearancePrefs.openLastChat(context) && AppearancePrefs.lastSessionId(context).isNotBlank()
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialLastSession) 1 else 0) }
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
            // 平板/折叠屏双栏：导航轨 + 左侧列表 + 右侧聊天（README 大屏自适应）
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    Tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
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
                        onOpenSession = { session ->
                            openSession(session.id)
                            openName = session.name
                        },
                        themeMode = themeMode,
                        themePreset = themePreset,
                        vibe = vibe,
                        onVibeChanged = onVibeChanged,
                        onAppearanceChanged = onAppearanceChanged,
                        onThemeChanged = onThemeChanged,
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
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
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
                onOpenSession = { session ->
                    openSession(session.id)
                    openName = session.name
                },
                themeMode = themeMode,
                themePreset = themePreset,
                vibe = vibe,
                onVibeChanged = onVibeChanged,
                onAppearanceChanged = onAppearanceChanged,
                onThemeChanged = onThemeChanged,
                settingsDeepLink = settingsDeepLink,
                onSettingsDeepLinkConsumed = { settingsDeepLink = null },
            )
        }
    }
}


@Composable
private fun TabContent(
    selectedTab: Int,
    onOpenChat: (com.emberinn.app.data.SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onOpenDetail: (com.emberinn.app.data.CharacterRecord) -> Unit,
    onOpenSession: (com.emberinn.app.data.SessionRecord) -> Unit,
    themeMode: com.emberinn.app.ui.theme.ThemeMode,
    themePreset: com.emberinn.app.ui.theme.ThemePreset,
    vibe: com.emberinn.app.ui.theme.VibePreset,
    onVibeChanged: (com.emberinn.app.ui.theme.VibePreset) -> Unit,
    onAppearanceChanged: () -> Unit,
    onThemeChanged: (com.emberinn.app.ui.theme.ThemeMode, com.emberinn.app.ui.theme.ThemePreset) -> Unit,
    settingsDeepLink: String?,
    onSettingsDeepLinkConsumed: () -> Unit,
) {
    when (selectedTab) {
        0 -> com.emberinn.app.ui.home.CharactersScreen(
            onOpenChat = onOpenChat,
            onOpenSettings = onOpenSettings,
            onOpenDetail = onOpenDetail,
        )
        1 -> com.emberinn.app.ui.sessions.SessionsScreen(
            onOpenSession = onOpenSession,
        )
        else -> com.emberinn.app.ui.settings.SettingsScreen(
            themeMode = themeMode,
            themePreset = themePreset,
            vibe = vibe,
            onVibeChanged = onVibeChanged,
            onAppearanceChanged = onAppearanceChanged,
            onThemeChanged = onThemeChanged,
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
