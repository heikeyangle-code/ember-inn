package com.emberinn.app.ui

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

    val sessionId = openSessionId
    if (sessionId != null) {
        com.emberinn.app.ui.chat.ChatScreen(
            sessionId = sessionId,
            name = openName,
            onBack = { openSessionId = null },
            onOpenSettings = { selectedTab = 2 },
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
                )
            }
        }
    }
}

