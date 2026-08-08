package com.emberinn.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.data.OnboardingPrefs
import com.emberinn.app.ui.home.HomeViewModel
import com.emberinn.engine.card.CardFormat

private data class TabSpec(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec("角色", Icons.Filled.Person),
    TabSpec("聊天", Icons.AutoMirrored.Filled.List),
    TabSpec("设置", Icons.Filled.Settings),
)

@Composable
fun MainScreen(
    themeMode: com.emberinn.app.ui.theme.ThemeMode = com.emberinn.app.ui.theme.ThemeMode.SYSTEM,
    themePreset: com.emberinn.app.ui.theme.ThemePreset = com.emberinn.app.ui.theme.ThemePresets.first(),
    onThemeChanged: (com.emberinn.app.ui.theme.ThemeMode, com.emberinn.app.ui.theme.ThemePreset) -> Unit = { _, _ -> },
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var openName by rememberSaveable { mutableStateOf("") }
    var settingsDeepLink by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val homeVm: HomeViewModel = viewModel()
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
                val session = homeVm.openChat(null, "AI 对话")
                openSessionId = session.id
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

    val sessionId = openSessionId
    if (sessionId != null) {
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
            when (selectedTab) {
                0 -> com.emberinn.app.ui.home.CharactersScreen(
                    onOpenChat = { session ->
                        openSessionId = session.id
                        openName = session.name
                    },
                    onOpenSettings = { selectedTab = 2 },
                )
                1 -> com.emberinn.app.ui.sessions.SessionsScreen(
                    onOpenSession = { session ->
                        openSessionId = session.id
                        openName = session.name
                    },
                )
                else -> com.emberinn.app.ui.settings.SettingsScreen(
                    themeMode = themeMode,
                    themePreset = themePreset,
                    onThemeChanged = onThemeChanged,
                    deepLink = settingsDeepLink,
                    onDeepLinkConsumed = { settingsDeepLink = null },
                )
            }
        }
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
