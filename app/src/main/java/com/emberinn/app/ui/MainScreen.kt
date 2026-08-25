package com.emberinn.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emberinn.app.data.OnboardingPrefs
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.FloatHub
import com.emberinn.app.ui.design.components.HubItem
import com.emberinn.app.ui.home.HomeViewModel
import com.emberinn.app.ui.home.TonightScreen
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.engine.card.CardFormat

/** 导航四目的地（DESIGN_SYSTEM.md §三）：今夜 / 对话 / 角色 / 设置。 */
private data class HubDest(val label: String, val icon: ImageVector)

private val Destinations = listOf(
    HubDest("今夜", FaIcons.ClockRotateLeft),
    HubDest("对话", FaIcons.Comments),
    HubDest("角色", FaIcons.Mask),
    HubDest("设置", FaIcons.Gear),
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    // README 启动行为：启动直接进入上次聊天（默认关）
    val initialLastSession = AppearancePrefs.openLastChat(context) && AppearancePrefs.lastSessionId(context).isNotBlank()
    var selectedDest by rememberSaveable { mutableIntStateOf(0) }
    var openSessionId by rememberSaveable { mutableStateOf(if (initialLastSession) AppearancePrefs.lastSessionId(context) else null) }
    var openName by rememberSaveable { mutableStateOf("") }
    var openDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    // 角色主页 → 编辑器（Power Space）：Character≠编辑器，主页承担视觉，编辑器承担深度
    var editingCharacter by rememberSaveable { mutableStateOf(false) }
    var settingsDeepLink by rememberSaveable { mutableStateOf<String?>(null) }
    var showGlobalSearch by rememberSaveable { mutableStateOf(false) }
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
            if (editingCharacter) {
                // 编辑器（Power Space）：返回角色主页而不是退出层级
                com.emberinn.app.ui.home.CharacterDetailScreen(
                    record = detailRecord,
                    vm = homeVm,
                    onBack = { editingCharacter = false },
                    onOpenChat = { session ->
                        editingCharacter = false
                        openDetailId = null
                        openSession(session.id)
                        openName = session.name
                    },
                )
            } else {
                // 角色主页（Companion Space）：幕布英雄 + 故事轨道 + 身份区
                com.emberinn.app.ui.home.CharacterHomeScreen(
                    record = detailRecord,
                    vm = homeVm,
                    onBack = { openDetailId = null },
                    onOpenChat = { session ->
                        openDetailId = null
                        openSession(session.id)
                        openName = session.name
                    },
                    onEdit = { editingCharacter = true },
                )
            }
            return
        }
        // 角色已被删除：退回首页
        androidx.compose.runtime.SideEffect { if (openDetailId != null) openDetailId = null }
    }

    val sessionId = openSessionId
    if (sessionId != null) {
        val wide = LocalConfiguration.current.screenWidthDp >= 840
        if (wide) {
            // 平板/折叠屏双栏：左缘导航轨 + 列表 + 聊天
            Row(modifier = Modifier.fillMaxSize()) {
                HubRail(
                    selected = selectedDest,
                    onSelect = { selectedDest = it },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    DestContent(
                        selected = selectedDest,
                        homeVm = homeVm,
                        onOpenChat = { session ->
                            openSession(session.id)
                            openName = session.name
                        },
                        onOpenSettings = { route ->
                            selectedDest = 3
                            if (route != null) settingsDeepLink = route
                        },
                        onOpenDetail = { record -> openDetailId = record.id; editingCharacter = false },
                        settingsDeepLink = settingsDeepLink,
                        onSettingsDeepLinkConsumed = { settingsDeepLink = null },
                        onSelectDestination = { selectedDest = it },
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
                            selectedDest = 3
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
            // 聊天页里跳设置：先退出聊天（否则被早退逻辑挡住），并深链到"提供商与模型"页
            onOpenSettings = {
                openSessionId = null
                settingsDeepLink = "providers"
                selectedDest = 3
            },
            onSwitchSession = { session ->
                openSession(session.id)
                openName = session.name
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(EmberTheme.colors.bg)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            DestContent(
                selected = selectedDest,
                homeVm = homeVm,
                onOpenChat = { session ->
                    openSession(session.id)
                    openName = session.name
                },
                onOpenSettings = { route ->
                    selectedDest = 3
                    if (route != null) settingsDeepLink = route
                },
                onOpenDetail = { record -> openDetailId = record.id; editingCharacter = false },
                settingsDeepLink = settingsDeepLink,
                onSettingsDeepLinkConsumed = { settingsDeepLink = null },
                onSelectDestination = { selectedDest = it },
            )
        }
        // 悬浮主钮浮在内容上（§三范式）：静默圆粒 + 展开竖栈；长按=全域搜索
        FloatHub(
            items = Destinations.map { HubItem(it.label, it.icon) },
            selected = selectedDest,
            onSelect = { selectedDest = it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            onLongPress = { showGlobalSearch = true },
        )

        if (showGlobalSearch) {
            com.emberinn.app.ui.design.components.GlobalSearchPanel(
                entriesProvider = { q ->
                    val r = homeVm.search(q)
                    buildList {
                        r.characters.forEach { ch ->
                            add(com.emberinn.app.ui.design.components.SearchEntry(ch.name, "角色卡", FaIcons.Mask, "角色") {
                                openDetailId = ch.id
                                editingCharacter = false
                            })
                        }
                        r.sessions.forEach { se ->
                            add(com.emberinn.app.ui.design.components.SearchEntry(se.name, "对话", FaIcons.Comments, "对话") {
                                openSession(se.id)
                                openName = se.name
                            })
                        }
                        r.worldInfo.forEach { wi ->
                            add(com.emberinn.app.ui.design.components.SearchEntry(wi.key, wi.characterName, FaIcons.BookOpen, "世界书") {
                                settingsDeepLink = "worldinfo"
                                selectedDest = 3
                            })
                        }
                        r.settings.forEach { st ->
                            add(com.emberinn.app.ui.design.components.SearchEntry(st.label, st.description, FaIcons.Gear, "设置") {
                                if (st.route != null) settingsDeepLink = st.route
                                selectedDest = 3
                            })
                        }
                    }
                },
                onDismiss = { showGlobalSearch = false },
            )
        }
    }
}

/** 平板左缘导航轨：与 FloatHub 同一目的地集合。 */
@Composable
private fun HubRail(selected: Int, onSelect: (Int) -> Unit) {
    val c = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(c.bgTint.copy(alpha = 0.4f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Destinations.forEachIndexed { index, dest ->
            RailItem(dest.label, dest.icon, selected == index) { onSelect(index) }
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
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun DestContent(
    selected: Int,
    homeVm: HomeViewModel,
    onOpenChat: (SessionRecord) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onOpenDetail: (com.emberinn.app.data.CharacterRecord) -> Unit,
    settingsDeepLink: String?,
    onSettingsDeepLinkConsumed: () -> Unit,
    onSelectDestination: (Int) -> Unit = {},
) {
    when (selected) {
        0 -> TonightScreen(
            vm = homeVm,
            onOpenChat = onOpenChat,
            onOpenDetail = onOpenDetail,
            onGoLibrary = { onSelectDestination(2) },
        )
        1 -> com.emberinn.app.ui.sessions.SessionsScreen(
            onOpenSession = onOpenChat,
        )
        2 -> com.emberinn.app.ui.home.CharactersScreen(
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
