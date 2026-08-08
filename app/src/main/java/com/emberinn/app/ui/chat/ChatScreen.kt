@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.chat

import com.emberinn.app.ui.icons.PhosphorIcons
import com.skydoves.cloudy.sky
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.cloudy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.emberinn.engine.media.MediaAttachment
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private sealed interface ChatItem {
    data class Message(val index: Int, val element: JsonElement) : ChatItem
    data object Streaming : ChatItem
    /** 思考过程但没有可挂载的 AI 消息（空正文场景），独立成卡，避免思考完就消失。 */
    data object ReasoningOnly : ChatItem
}

@Composable
fun ChatScreen(
    sessionId: String,
    name: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory {
            initializer { ChatViewModel(this[APPLICATION_KEY]!!, sessionId) }
        },
    )
    val messages by vm.messages.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val providerConfigured by vm.providerConfigured.collectAsState()
    val notice by vm.notice.collectAsState()
    val isImpersonating by vm.isImpersonating.collectAsState()
    val impersonated by vm.impersonated.collectAsState()
    val streamingReasoning by vm.streamingReasoning.collectAsState()
    val lastReasoning by vm.lastReasoning.collectAsState()
    val pendingMedia by vm.pendingMedia.collectAsState()
    val worldHits by vm.worldHits.collectAsState()
    val contextUsage by vm.contextUsage.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var menuMessageIndex by remember { mutableStateOf<Int?>(null) }
    var contextDetail by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showQuickBar by remember { mutableStateOf(false) }
    var showCharacterInfo by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteTargetIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val exportChatLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { u ->
            val text = vm.exportJsonl()
            if (text == null) {
                Toast.makeText(context, "这条会话还没有消息，无内容可导出", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    context.contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
                    Toast.makeText(context, "已导出：$name.jsonl", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri)
            vm.addPendingMedia(uri, mime)
        }
    }

    val accent = vm.accentColor?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
    val items = remember(messages, isStreaming, streamingText, lastReasoning) {
        buildList {
            messages.forEachIndexed { i, el -> add(ChatItem.Message(i, el)) }
            if (isStreaming) {
                add(ChatItem.Streaming)
            } else if (lastReasoning != null && messages.indexOfLast { el -> !isUser(el) } < 0) {
                // 空正文场景：思考过程独立成卡，不随流式结束消失
                add(ChatItem.ReasoningOnly)
            }
        }
    }
    val lastAiIndex = messages.indexOfLast { el -> !isUser(el) }

    // 冒充草稿进输入框（官方：冒充结果落到发送框，用户可改可发）
    LaunchedEffect(impersonated) {
        impersonated?.let {
            input = it
            vm.consumeImpersonation()
        }
    }

    // 每次进入聊天页重新读盘：配置模型后返回不再显示“没配置模型”
    LaunchedEffect(Unit) { vm.refreshProviderConfigured() }

    // README 手势守则：系统返回键/侧滑返回 = 回到列表
    BackHandler(onBack = onBack)

    // 自动滚底：贴底跟随；用户上滑查看历史时暂停跟随，滚回底部自动恢复（微信式）
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState, isStreaming) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.collect { nearBottom ->
            if (listState.isScrollInProgress && !nearBottom) followBottom = false
            if (nearBottom) followBottom = true
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            followBottom = true
        }
    }
    // 流式：贴底时用即时滚动到流式项末尾（正文变长不再跳顶，也不逐 token 动画）
    LaunchedEffect(streamingText, streamingReasoning) {
        if (isStreaming && followBottom) {
            listState.scrollToItem(items.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    val sky = rememberSky()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0) }
    var inputBarHeight by remember { mutableStateOf(0) }
    val topBarPad = with(density) { topBarHeight.toDp() }
    val inputBarPad = with(density) { inputBarHeight.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 源层：消息列表作为模糊来源，上下留出浮层高度
        Column(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .padding(top = topBarPad)
                .padding(bottom = inputBarPad),
        ) {
            if (!providerConfigured) {
                UnconfiguredBanner(onOpenSettings = onOpenSettings)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (items.isEmpty()) {
                    item { EmptyChat(name = name, accent = accent) }
                }
                itemsIndexed(items, key = { _, item -> when (item) {
                    is ChatItem.Message -> "m-${item.index}"
                    ChatItem.Streaming -> "streaming"
                    ChatItem.ReasoningOnly -> "reasoning-only"
                } }) { _, item ->
                    when (item) {
                        is ChatItem.Message -> {
                            val el = item.element
                            val isUserMsg = isUser(el)
                            val text = textOf(el)
                            val showActions = !isStreaming && item.index == lastAiIndex && !isUserMsg
                            val dateLabel = if (item.index == 0) {
                                dateLabelOf(el)
                            } else {
                                val prev = dateLabelOf(messages[item.index - 1])
                                val cur = dateLabelOf(el)
                                if (prev == cur) null else cur
                            }
                            MessageRow(
                                isUser = isUserMsg,
                                text = text,
                                media = mediaOf(el),
                                reasoning = if (!isStreaming && !isUserMsg && item.index == lastAiIndex) lastReasoning else null,
                                name = nameOf(el, isUserMsg),
                                time = timeOf(el),
                                dateLabel = dateLabel,
                                avatarPath = if (isUserMsg) null else vm.avatarPath,
                                accent = accent,
                                showActions = showActions,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(text))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                                onRegenerate = { vm.regenerate() },
                                onContinue = { vm.continueGeneration() },
                                onDelete = { vm.deleteMessage(item.index) },
                                onLongPress = { menuMessageIndex = item.index },
                            )
                        }
                        ChatItem.Streaming -> StreamingRow(
                            text = streamingText,
                            reasoning = streamingReasoning,
                            name = name,
                            avatarPath = vm.avatarPath,
                            accent = accent,
                            impersonating = isImpersonating,
                        )
                        ChatItem.ReasoningOnly -> {
                            lastReasoning?.let { ReasoningCard(text = it) }
                        }
                    }
                }
                notice?.let { n ->
                    item(key = "notice") {
                        Text(
                            text = n,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                }
            }

            if (!isStreaming && (contextUsage != null || worldHits.isNotEmpty())) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    contextUsage?.let { (used, max) ->
                        ContextCapsule(used = used, max = max, onClick = { contextDetail = true })
                    }
                    if (worldHits.isNotEmpty()) {
                        StatusPill("世界书 ×${worldHits.size}") {
                            Toast.makeText(
                                context,
                                "命中：" + worldHits.take(5).joinToString("、"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }

        ChatTopBar(
            name = name,
            avatarPath = vm.avatarPath,
            accent = accent,
            onBack = onBack,
            onMenu = { showMore = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeight = it.height }
                .cloudy(sky = sky, radius = 18, tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)),
        )

        ChatInputBar(
            input = input,
            onInputChange = { input = it },
            pendingMedia = pendingMedia,
            onRemoveMedia = { index -> vm.removePendingMedia(index) },
            isStreaming = isStreaming,
            canQuickContinue = !isStreaming && lastAiIndex >= 0,
            quickBarOpen = showQuickBar,
            onToggleQuickBar = { showQuickBar = !showQuickBar },
            onQuickContinue = {
                showQuickBar = false
                vm.continueGeneration()
            },
            onQuickImpersonate = {
                showQuickBar = false
                vm.impersonate()
            },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty() || pendingMedia.isNotEmpty()) {
                    vm.send(text, media = pendingMedia)
                    input = ""
                }
            },
            onStop = { vm.stop() },
            onAttach = {
                mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*"))
            },
            onVoice = {
                Toast.makeText(context, "语音输入开发中", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { inputBarHeight = it.height }
                .cloudy(sky = sky, radius = 18, tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)),
        )
    }

    if (contextDetail) {
        val usage = contextUsage
        if (usage != null) {
            ModalBottomSheet(onDismissRequest = { contextDetail = false }, sheetState = rememberModalBottomSheetState()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                    Text("上下文占用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(12.dp))
                    val (used, max) = usage
                    val pct = if (max <= 0) 0 else (used * 100 / max)
                    val gradeText = when {
                        pct >= 90 -> "红色：快满了，建议提高上限或精简提示"
                        pct >= 75 -> "橙色：偏紧，长回复可能被裁剪"
                        pct >= 50 -> "黄色：过半，留意后续消息长度"
                        else -> "绿色：空间充足"
                    }
                    Text("已用：${formatTokens(used)}", style = MaterialTheme.typography.bodyMedium)
                    Text("上限：${formatTokens(max)}", style = MaterialTheme.typography.bodyMedium)
                    Text("占比：$pct%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        gradeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            contextDetail = false
        }
    }

    menuMessageIndex?.let { index ->
        val el = messages.getOrNull(index)
        if (el != null) {
            val text = textOf(el)
            val isUserMsg = isUser(el)
            ModalBottomSheet(onDismissRequest = { menuMessageIndex = null }, sheetState = rememberModalBottomSheetState()) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        if (isUserMsg) "我的消息" else name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                    MenuRow(PhosphorIcons.Copy, "复制") {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        menuMessageIndex = null
                    }
                    MenuRow(PhosphorIcons.Edit, "编辑这条消息") {
                        editIndex = index; editDraft = text; menuMessageIndex = null
                    }
                    if (!isUserMsg) {
                        MenuRow(PhosphorIcons.MaskHappy, "冒充（让模型替你说）") {
                            vm.impersonate(); menuMessageIndex = null
                        }
                        if (index == lastAiIndex) {
                            MenuRow(PhosphorIcons.Refresh, "重新生成") {
                                vm.regenerate(); menuMessageIndex = null
                            }
                            MenuRow(PhosphorIcons.Continue, "继续生成") {
                                vm.continueGeneration(); menuMessageIndex = null
                            }
                        }
                    }
                    MenuRow(PhosphorIcons.Delete, "删除这条消息", danger = true) {
                        deleteTargetIndex = index; menuMessageIndex = null
                    }
                }
            }
        }
    }

    deleteTargetIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { deleteTargetIndex = null },
            title = { Text("删除这条消息？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMessage(index)
                    deleteTargetIndex = null
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetIndex = null }) { Text("取消") }
            },
        )
    }

    editIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editMessage(index, editDraft)
                    editIndex = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editIndex = null }) { Text("取消") }
            },
        )
    }

    vm.character?.let { character ->
        if (showCharacterInfo) {
            CharacterInfoSheet(character = character, onDismiss = { showCharacterInfo = false })
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "会话菜单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                if (vm.character != null) {
                    MenuRow(PhosphorIcons.Person, "角色详情") {
                        showMore = false
                        showCharacterInfo = true
                    }
                }
                MenuRow(PhosphorIcons.Share, "导出聊天（JSONL）") {
                    showMore = false
                    exportChatLauncher.launch("$name-${System.currentTimeMillis().toString().takeLast(8)}.jsonl")
                }
                MenuRow(PhosphorIcons.Delete, "清空会话", danger = true) {
                    showMore = false
                    showClearConfirm = true
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空会话？") },
            text = { Text("会删除这条会话的全部消息，且不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearSession()
                    showClearConfirm = false
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    name: String,
    avatarPath: String?,
    accent: Color,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .heightIn(min = 52.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(PhosphorIcons.ArrowLeft, contentDescription = "返回")
            }
            Spacer(Modifier.size(6.dp))
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 40)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "会话中 · 数据仅存本地",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp)) {
                Icon(PhosphorIcons.MoreVert, contentDescription = "更多")
            }
        }
    }
}

@Composable
private fun RoleAvatar(avatarPath: String?, name: String, accent: Color, size: Int) {
    val avatarFile = avatarPath?.let { File(it) }?.takeIf { it.exists() }
    if (avatarFile != null) {
        AsyncImage(
            model = avatarFile,
            contentDescription = name,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(size.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.take(1).ifBlank { "✦" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnconfiguredBanner(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "还没配置模型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "配好后就能开始对话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text("先选一个模型")
            }
        }
    }
}

@Composable
private fun MessageRow(
    isUser: Boolean,
    text: String,
    media: List<MediaAttachment>,
    reasoning: String?,
    name: String,
    time: String,
    avatarPath: String?,
    accent: Color,
    dateLabel: String?,
    showActions: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (dateLabel != null) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 32)
            Spacer(Modifier.size(10.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.78f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.size(4.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 18.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (isUser) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        ChatMarkdown(
                            content = text,
                            onSurface = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (media.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                MessageMedia(media = media)
            }
            if (!reasoning.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                ReasoningCard(text = reasoning)
            }
            if (showActions) {
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallAction(PhosphorIcons.Copy, "复制", onCopy)
                    SmallAction(PhosphorIcons.Refresh, "重新生成", onRegenerate)
                    SmallAction(PhosphorIcons.Continue, "继续", onContinue)
                    SmallAction(PhosphorIcons.Delete, "删除", onDelete)
                }
            }
        }
    }
    }
}

@Composable
private fun StreamingRow(
    text: String,
    reasoning: String = "",
    name: String,
    avatarPath: String?,
    accent: Color,
    impersonating: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        RoleAvatar(avatarPath = if (impersonating) null else avatarPath, name = if (impersonating) "我" else name, accent = if (impersonating) MaterialTheme.colorScheme.secondary else accent, size = 32)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
            Text(
                text = if (impersonating) "冒充草稿 · 我" else name,
                style = MaterialTheme.typography.labelMedium,
                color = if (impersonating) MaterialTheme.colorScheme.secondary else accent,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(4.dp))
            if (reasoning.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "思考过程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ChatMarkdown(
                        content = text.ifEmpty { "…" },
                        onSurface = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "▍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                        modifier = Modifier.alpha(alpha).padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * README 上下文占比胶囊：圆环进度 + token/上限 + 百分比 + 绿→黄→橙→红分级，点开详细分解。
 */
@Composable
private fun ContextCapsule(used: Int, max: Int, onClick: () -> Unit) {
    val ratio = if (max <= 0) 0f else used.toFloat() / max
    val grade = when {
        ratio >= 0.90f -> MaterialTheme.colorScheme.error
        ratio >= 0.75f -> Color(0xFFEF6C00)
        ratio >= 0.50f -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        CircularProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = grade,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "${(ratio * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = grade,
        )
        Spacer(Modifier.size(5.dp))
        Text(
            "上下文 ${formatTokens(used)}/${formatTokens(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 状态胶囊（世界书命中等），README 状态可见。 */
@Composable
private fun StatusPill(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun formatTokens(n: Int): String = if (n >= 1000) {
    "%.1fk".format(n / 1000.0)
} else {
    n.toString()
}

/** 角色详情弹层（聊天页 ⋮ → 角色详情；字段解析对齐角色列表弹层）。 */
@Composable
private fun CharacterInfoSheet(character: com.emberinn.app.data.CharacterRecord, onDismiss: () -> Unit) {
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    val fields = remember(character.rawJson) {
        runCatching {
            val root = json.parseToJsonElement(character.rawJson).jsonObject
            val data = root["data"]?.jsonObject ?: root
            listOf(
                "名字" to (data["name"]?.jsonPrimitive?.contentOrNull ?: ""),
                "描述" to (data["description"]?.jsonPrimitive?.contentOrNull ?: ""),
                "性格" to (data["personality"]?.jsonPrimitive?.contentOrNull ?: ""),
                "场景" to (data["scenario"]?.jsonPrimitive?.contentOrNull ?: ""),
                "开场白" to (data["first_mes"]?.jsonPrimitive?.contentOrNull ?: ""),
                "示例对话" to (data["mes_example"]?.jsonPrimitive?.contentOrNull ?: ""),
                "系统提示" to (data["system_prompt"]?.jsonPrimitive?.contentOrNull ?: ""),
                "剧情后指令" to (data["post_history_instructions"]?.jsonPrimitive?.contentOrNull ?: ""),
                "创作者备注" to (data["creator_notes"]?.jsonPrimitive?.contentOrNull ?: ""),
            ).filter { it.second.isNotBlank() }
        }.getOrDefault(emptyList())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).fillMaxWidth(),
        ) {
            Text("角色详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            fields.forEach { (label, value) ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            if (fields.isEmpty()) {
                Text("该卡暂无可用字段", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 输入区待发附件缩略图（可移除）。 */
@Composable
private fun PendingMediaChip(media: MediaAttachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.widthIn(max = 180.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
            if (media.type == "image") {
                AsyncImage(
                    model = mediaModel(media.url),
                    contentDescription = "附件",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (media.type == "video") "▶" else "♪", color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = media.title.ifBlank { if (media.type == "image") "图片" else if (media.type == "video") "视频" else "音频" },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(PhosphorIcons.Close, contentDescription = "移除附件", modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 消息附件渲染：图片/GIF 用 Coil3（coil-gif），音视频用 Media3 ExoPlayer（README 渲染规范）。 */
@Composable
private fun MessageMedia(media: List<MediaAttachment>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        media.forEach { m ->
            when (m.type) {
                "image" -> AsyncImage(
                    model = mediaModel(m.url),
                    contentDescription = m.title.ifBlank { "图片" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                else -> MediaPlayer(m.url, isAudio = m.type == "audio")
            }
        }
    }
}

@Composable
private fun MediaPlayer(url: String, isAudio: Boolean) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (url.startsWith("data:")) Uri.parse(url) else Uri.fromFile(File(url))
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isAudio) 56.dp else 120.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

/** 生成完的思考过程：默认折叠，点开查看。 */
@Composable
private fun ReasoningCard(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { expanded = !expanded }),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "思考过程 ▾" else "思考过程 ▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (expanded) "" else text.take(36) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 聊天气泡里的 Markdown：收敛成聊天风（正文 bodyMedium、标题降级、代码低饱和、间距克制）。 */
@Composable
private fun ChatMarkdown(content: String, onSurface: Color) {
    Markdown(
        content = content,
        modifier = Modifier.fillMaxWidth(),
        imageTransformer = Coil3ImageTransformerImpl,
        components = markdownComponents(
            codeBlock = highlightedCodeBlock,
            codeFence = highlightedCodeFence,
        ),
        colors = markdownColor(
            text = onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
            tableBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleMedium,
            h2 = MaterialTheme.typography.titleMedium,
            h3 = MaterialTheme.typography.titleSmall,
            h4 = MaterialTheme.typography.titleSmall,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.titleSmall,
            text = MaterialTheme.typography.bodyMedium,
            paragraph = MaterialTheme.typography.bodyMedium,
            ordered = MaterialTheme.typography.bodyMedium,
            bullet = MaterialTheme.typography.bodyMedium,
            list = MaterialTheme.typography.bodyMedium,
            quote = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
            code = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            inlineCode = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
        ),
        padding = markdownPadding(
            block = 3.dp,
            list = 2.dp,
            listItemTop = 2.dp,
            listItemBottom = 2.dp,
            listIndent = 10.dp,
            codeBlock = PaddingValues(10.dp),
            blockQuote = PaddingValues(horizontal = 8.dp),
        ),
    )
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    pendingMedia: List<MediaAttachment>,
    onRemoveMedia: (Int) -> Unit,
    isStreaming: Boolean,
    canQuickContinue: Boolean,
    quickBarOpen: Boolean,
    onToggleQuickBar: () -> Unit,
    onQuickContinue: () -> Unit,
    onQuickImpersonate: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column {
            if (pendingMedia.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    itemsIndexed(pendingMedia, key = { i, media -> "$i-${media.url.take(24)}" }) { index, media ->
                        PendingMediaChip(media = media, onRemove = { onRemoveMedia(index) })
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
            if (quickBarOpen) {
                Column(modifier = Modifier.padding(end = 4.dp)) {
                    TextButton(onClick = onQuickContinue, enabled = canQuickContinue, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("继续", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onQuickImpersonate, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("冒充", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onToggleQuickBar, modifier = Modifier.size(42.dp)) {
                Icon(PhosphorIcons.Book, contentDescription = "快捷工具盘")
            }
            IconButton(onClick = onAttach, modifier = Modifier.size(42.dp)) {
                Icon(PhosphorIcons.Plus, contentDescription = "附件 / 语音")
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("输入消息…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 160.dp),
            )
            if (!isStreaming) {
                IconButton(onClick = onVoice, modifier = Modifier.size(42.dp)) {
                    Icon(PhosphorIcons.Mic, contentDescription = "语音输入")
                }
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() || pendingMedia.isNotEmpty(),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        PhosphorIcons.Send,
                        contentDescription = "发送",
                        tint = if (input.isNotBlank() || pendingMedia.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            } else {
                IconButton(onClick = onStop, modifier = Modifier.size(42.dp)) {
                    Icon(PhosphorIcons.Stop, contentDescription = "停止生成", tint = MaterialTheme.colorScheme.error)
                }
            }
            }
        }
    }
}

@Composable
private fun EmptyChat(name: String, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", style = MaterialTheme.typography.displayLarge, color = accent.copy(alpha = 0.85f))
        Spacer(Modifier.size(16.dp))
        Text(
            "和 ${name.ifBlank { "TA" }} 打个招呼吧",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "第一条消息会连同角色卡、世界书与示例对话一起发给模型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

private fun isUser(el: JsonElement): Boolean {
    val v = el.jsonObject["is_user"] ?: return false
    return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
}

private fun textOf(el: JsonElement): String =
    el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""

/** 渲染输入：data URL 原样，本地路径转 File（Coil3/ExoPlayer 都能加载）。 */
private fun mediaModel(url: String): Any = if (url.startsWith("data:")) url else File(url)

private fun mediaOf(el: JsonElement): List<MediaAttachment> {
    val extra = el.jsonObject["extra"] as? JsonObject ?: return emptyList()
    return extra["media"]?.jsonArray?.mapNotNull { me ->
        val mo = me.jsonObject
        val url = mo["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        MediaAttachment(
            type = mo["type"]?.jsonPrimitive?.contentOrNull?.ifBlank { "image" } ?: "image",
            url = url,
            title = mo["title"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    } ?: emptyList()
}

private fun nameOf(el: JsonElement, isUser: Boolean): String =
    el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() } ?: if (isUser) "我" else "助手"

/** 日期分隔（微信式）：今天 / 昨天 / MM月dd日 / yyyy年MM月dd日；无日期返回 null。 */
private fun dateLabelOf(el: JsonElement): String? {
    val raw = el.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull ?: return null
    return runCatching {
        val date = Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("M月d日"))
            else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }
    }.getOrNull()
}

private fun timeOf(el: JsonElement): String {
    val raw = el.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull ?: return ""
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}
