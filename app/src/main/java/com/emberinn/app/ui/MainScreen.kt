package com.emberinn.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class TabSpec(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec("角色", Icons.Filled.Person),
    TabSpec("聊天", Icons.AutoMirrored.Filled.List),
    TabSpec("设置", Icons.Filled.Settings),
)

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
                0 -> PlaceholderScreen("角色", "P0 骨架：角色列表 / 导入角色卡（PNG / JSON / CharX / YAML / BYAF）")
                1 -> PlaceholderScreen("聊天", "P0 骨架：会话列表 / 新建对话 / 群聊入口")
                else -> PlaceholderScreen("设置", "P0 骨架：外观主题 / 提供商模型 / 语音 / 服务 / 数据与隐私")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "✦ $title",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
