@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.chat

import com.emberinn.app.ui.components.EmberEmptyState
import com.emberinn.app.ui.components.glassTint

import com.emberinn.app.data.DisplayPipeline
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.Persona
import com.emberinn.app.data.ThemeState
import com.emberinn.engine.group.GroupGenerationMode
import com.emberinn.app.ui.components.edgeSwipeBack
import com.emberinn.app.ui.components.glassEdgeHighlight
import com.emberinn.app.ui.icons.PhosphorIcons
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.settings.ExtensionPrefs
import com.emberinn.app.ui.theme.LocalThemePreset
import com.emberinn.app.ui.settings.RenderPrefs
import com.skydoves.cloudy.sky
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.cloudy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.slash.QuickReplySlot
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.emberinn.app.ui.components.parseHexColor
import com.emberinn.app.ui.components.EmberInputIcon
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberTextFieldDefaults
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.EmberBottomSheet
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

/** 贴底跟随采样：滚动方向 + 是否真正处于内容末端（最后一项底边贴近视口底）。 */
private data class ScrollSample(
    val inProgress: Boolean,
    val firstIndex: Int,
    val firstOffset: Int,
    val atTrueEnd: Boolean,
)

/** 一条消息在组合期的派生字段缓存（元素不变即复用，流式 tick 不重复解析）。 */
private data class ChatItemDerived(
    val isUser: Boolean,
    val isSystem: Boolean,
    val text: String,
    val media: List<MediaAttachment>,
    val mediaDisplay: String?,
    val mediaIndex: Int?,
    val name: String,
    val time: String,
    val swipeCount: Int,
    val curSwipe: Int,
)

/** 消息附件列表的稳定包装：Compose 把 List 视为不稳定参数，包一层 @Immutable 让 MessageRow 可跳过重组。 */
@Immutable
private data class ChatMedia(val items: List<MediaAttachment>)

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
    // /renamechat 后实时刷新顶栏/空态名字（不用 MainScreen 传入的固定 name）
    val currentName = vm.sessionName().ifBlank { name }
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
    val quickReplies by vm.quickReplies.collectAsState()
    val quickReplyOutput by vm.quickReplyOutput.collectAsState()
    val inputDraft by vm.inputDraft.collectAsState()
    val chatBackground by vm.chatBackground.collectAsState()
    val personas by vm.personas.collectAsState()
    val activePersona by vm.activePersona.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val dataBank by vm.dataBank.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    // 思考卡展开状态：流式/生成完是同一个卡，点开状态跨阶段保持，不重建
    var reasoningExpanded by rememberSaveable { mutableStateOf(false) }
    var menuMessageIndex by remember { mutableStateOf<Int?>(null) }
    var contextDetail by remember { mutableStateOf(false) }
    var worldPanel by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var tokenStatsIndex by remember { mutableStateOf<Int?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showAttachOptions by remember { mutableStateOf(false) }
    var showUrlAttachmentDialog by remember { mutableStateOf(false) }
    var urlAttachmentDraft by rememberSaveable { mutableStateOf("") }
    var showQuickBar by remember { mutableStateOf(false) }
    var showCharacterInfo by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    var personaDraftName by remember { mutableStateOf("") }
    var personaDraftDesc by remember { mutableStateOf("") }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkDraftName by remember { mutableStateOf("") }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var bookmarkToOpen by remember { mutableStateOf<String?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showDataBank by remember { mutableStateOf(false) }
    var showDataBankUrlDialog by remember { mutableStateOf(false) }
    var dataBankUrlDraft by rememberSaveable { mutableStateOf("") }
    var showAuthorsNote by remember { mutableStateOf(false) }
    var anPrompt by remember { mutableStateOf("") }
    var anPosition by remember { mutableStateOf(1) }
    var anDepth by remember { mutableStateOf(4) }
    var showGroupSettings by remember { mutableStateOf(false) }
    var pendingDisplay by remember { mutableStateOf<String?>(null) }
    var groupMode by rememberSaveable { mutableStateOf(vm.group?.generationMode ?: GroupGenerationMode.APPEND) }
    var groupStrategy by rememberSaveable { mutableStateOf(vm.group?.activationStrategy ?: "natural") }
    var imagePrompt by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteTargetIndex by remember { mutableStateOf<Int?>(null) }
    var deleteSwipeTargetIndex by remember { mutableStateOf<Int?>(null) }
    var swipePickerIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

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

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { vm.setChatBackground(it) }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri)
            vm.addPendingMedia(uri, mime)
        }
    }

    val dataBankPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { vm.addDataBankFile(it) }
    }

    // 附件来源选择：本地文件 / URL（官方 Message.addImage 等支持 URL 来源）
    if (showAttachOptions) {
        AlertDialog(
            onDismissRequest = { showAttachOptions = false },
            title = { Text("添加附件") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAttachOptions = false
                        mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*"))
                    }) { Text("从文件选择…") }
                    TextButton(onClick = {
                        showAttachOptions = false
                        showUrlAttachmentDialog = true
                    }) { Text("从 URL 添加…") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachOptions = false }) { Text("取消") }
            },
        )
    }
    if (showUrlAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showUrlAttachmentDialog = false },
            title = { Text("从 URL 添加附件") },
            text = {
                EmberTextField(
                    value = urlAttachmentDraft,
                    onValueChange = { urlAttachmentDraft = it },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = urlAttachmentDraft.trim()
                    if (u.isNotBlank()) vm.addMediaFromUrl(u)
                    urlAttachmentDraft = ""
                    showUrlAttachmentDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    urlAttachmentDraft = ""
                    showUrlAttachmentDialog = false
                }) { Text("取消") }
            },
        )
    }

    val accent = vm.accentColor?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
    val items = remember(messages, isStreaming, lastReasoning) {
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

    // 自动滚底：贴底跟随；用户上滑查看历史时暂停跟随，滚回底部自动恢复（微信式）。
    // 贴底判定 = 最后一项（含流式项）可见，不再用 3 项容差——消息少时看历史曾被误判“贴底”而拽回。
    var followBottom by remember { mutableStateOf(true) }
    // 全局快捷回复输出填输入框（官方点击槽位执行斜杠链；本 App 把文本输出放进输入框，用户可改可发）
    LaunchedEffect(quickReplyOutput) {
        quickReplyOutput?.let { output ->
            input = output
            followBottom = true
            vm.consumeQuickReplyOutput()
        }
    }

    // 冒充草稿进输入框（官方：冒充结果落到发送框，用户可改可发）
    LaunchedEffect(impersonated) {
        impersonated?.let {
            input = it
            vm.consumeImpersonation()
        }
    }

    // /setinput：官方 setinput 把文本写进输入框（用户可改可发）
    LaunchedEffect(inputDraft) {
        inputDraft?.let {
            input = it
            vm.clearInputDraft()
        }
    }

    // 每次进入聊天页重新读盘：配置模型后返回不再显示“没配置模型”；设置页改快捷回复后同步刷新
    LaunchedEffect(Unit) {
        vm.refreshProviderConfigured()
        vm.refreshQuickReplies()
        vm.refreshTheme()
    }

    // 离开聊天页：角色主题配方还原为全局主题
    DisposableEffect(Unit) {
        onDispose { ThemeState.clear() }
    }

    // README 手势守则：系统返回键/侧滑返回 = 回到列表
    BackHandler(onBack = onBack)

    // 贴底跟随：改为滚动方向判定。长消息流式途中上滑阅读不会因“最后一项仍可见”被拽回；
    // 只有真正滚回内容末端（最后一项底边贴近视口底）才恢复跟随。
    val scrollDensity = LocalDensity.current
    LaunchedEffect(listState, isStreaming) {
        var prevFirstIndex = -1
        var prevFirstOffset = 0
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            val last = info.visibleItemsInfo.lastOrNull()
            val atTrueEnd = last != null && last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + with(scrollDensity) { 36.dp.toPx() }
            ScrollSample(
                inProgress = listState.isScrollInProgress,
                firstIndex = first?.index ?: -1,
                firstOffset = first?.offset ?: 0,
                atTrueEnd = atTrueEnd,
            )
        }.collect { s ->
            if (s.inProgress) {
                val movedUp = s.firstIndex < prevFirstIndex ||
                    (s.firstIndex == prevFirstIndex && s.firstOffset < prevFirstOffset)
                if (movedUp) followBottom = false
            } else if (s.atTrueEnd) {
                followBottom = true
            }
            prevFirstIndex = s.firstIndex
            prevFirstOffset = s.firstOffset
        }
    }
    // 只有处于“贴底跟随”状态才滚底；用户上滑查看历史时不拽走。
    // 新消息/流式结束都滚到消息底边（长消息停在底部而不是顶部）。
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && followBottom) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }
    // 进入聊天：首帧布局完成后直接滚到底（首帧未测量时 scrollToItem 会被吞，内容先空后跳）。
    // 不用组合期捕获的 items（消息异步加载时可能仍是空列表），直接读当前布局总数。
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0) {
            listState.scrollToItem(target, scrollOffset = Int.MAX_VALUE)
        }
    }
    // 流式：贴底时以 120ms 节流滚动到流式项末尾（与显示同频，不再每 token 强制布局/跳动）。
    LaunchedEffect(Unit) {
        var lastScrollNanos = 0L
        snapshotFlow { streamingText to streamingReasoning }.collect { _ ->
            val now = System.nanoTime()
            if (isStreaming && followBottom && now - lastScrollNanos >= 120_000_000L) {
                val target = listState.layoutInfo.totalItemsCount - 1
                if (target >= 0) listState.scrollToItem(target, scrollOffset = Int.MAX_VALUE)
                lastScrollNanos = now
            }
        }
    }

    // 流式显示：120ms 节流（官方 streaming_fps=30 是上限不是目标；每 tick 全量解析的成本远高于 30fps 的收益）。
    // 流式中只补定界符，不跑 fixMarkdown/encodeTags（交给轻量流式渲染器），结束后一次性走完整管线。
    var displayStreaming by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        snapshotFlow { streamingText }.collect { text ->
            val now = System.nanoTime()
            if (now - lastNanos >= 120_000_000L) {
                displayStreaming = text
                lastNanos = now
            }
        }
    }
    LaunchedEffect(isStreaming) {
        if (!isStreaming) displayStreaming = streamingText
    }
    val streamingDisplay = remember(displayStreaming, isStreaming) {
        val balanced = DisplayPipeline.balanceStreamingDelimiters(displayStreaming, isFinal = !isStreaming)
        if (!isStreaming) {
            val fixed = DisplayPipeline.fixMarkdown(balanced)
            if (AppearancePrefs.encodeTags(context)) DisplayPipeline.encodeTags(fixed) else fixed
        } else {
            balanced
        }
    }

    val sky = rememberSky()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 行级外观设置：在 ChatScreen 层读一次传给列表，避免每条消息组合时各自读 SharedPreferences
    val rowDensity = AppearancePrefs.density(context)
    val rowImmersiveActions = AppearancePrefs.immersiveActions(context)
    val rowBubbleStyle = AppearancePrefs.bubbleStyle(context)
    // 玻璃边缘高光用到的深浅判断（同屏只有顶栏/输入栏两处玻璃，正文区保持干净）
    val glassDark = isDarkThemeSurface()
    var topBarHeight by remember { mutableStateOf(0) }
    var inputBarHeight by remember { mutableStateOf(0) }
    val topBarPad = with(density) { topBarHeight.toDp() }
    val inputBarPad = with(density) { inputBarHeight.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            // README 返回手势：左右边缘滑动退出
            .edgeSwipeBack(onBack = onBack),
    ) {
        // 静态背景层：氛围渐变 + 光晕 + 显式/头像背景。作为顶栏/输入栏毛玻璃的静态模糊源；
        // 不再把消息列表当 sky 源，避免每次滚动/键盘动画都重捕整屏模糊（滚动/收键盘卡顿主因）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(
                    // README 格调守则：界面克制、背景出彩——正文区干净，底部透一点角色色低饱和氛围光
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        0.6f to MaterialTheme.colorScheme.background,
                        1f to lerp(accent, MaterialTheme.colorScheme.background, 0.82f),
                    ),
                ),
        ) {
            // 左下角色色低饱和光晕（氛围层，叠在消息列表之下，正文区保持干净）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(380.dp)
                    .offset(x = (-140).dp, y = 60.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.09f), Color.Transparent),
                        ),
                    ),
            )
        // 聊天背景：显式背景（会话 chat_metadata.custom_background / 角色主题配方）> 角色头像玻璃背景 > 外层氛围渐变兜底。
        // 可读性遮罩（README 玻璃背景规范 + 调研）：深色叠黑、浅色叠纸白；模糊/遮罩强度全局可调（外观与主题）
        val glassOn = AppearancePrefs.chatBgAvatarGlass(context)
        val bgBlur = AppearancePrefs.chatBgBlur(context)
        val darkSurface = isDarkThemeSurface()
        val bgScrim = if (darkSurface) {
            AppearancePrefs.chatBgScrimDark(context) / 100f
        } else {
            AppearancePrefs.chatBgScrimLight(context) / 100f
        }
        val scrimBase = if (darkSurface) {
            parseHexColor(AppearancePrefs.chatBgScrimDarkColor(context)) ?: Color.Black
        } else {
            parseHexColor(AppearancePrefs.chatBgScrimLightColor(context)) ?: Color.White
        }
        val bgPath = chatBackground?.takeIf { java.io.File(it).exists() }
            ?: if (glassOn) vm.avatarPath?.takeIf { java.io.File(it).exists() } else null
        if (bgPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(java.io.File(bgPath)).size(1200).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (bgBlur > 0) Modifier.blur(bgBlur.dp) else Modifier),
            )
            if (bgScrim > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimBase.copy(alpha = scrimBase.alpha * bgScrim)),
                )
            }
        }

        } // 静态背景层结束

        // 消息列表：上下留出浮层高度（不再是模糊源，滚动/键盘动画不再触发整屏模糊重绘）
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 最小安全留白：浮层实测高度未就绪（首帧/键盘变化）时也不会盖住消息
                .padding(top = maxOf(topBarPad, 64.dp))
                .padding(bottom = maxOf(inputBarPad, 96.dp)),
        ) {
            if (!providerConfigured) {
                UnconfiguredBanner(onOpenSettings = onOpenSettings)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    if (rowDensity == "compact") 4.dp else 8.dp,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (items.isEmpty()) {
                    item { EmptyChat(name = currentName, accent = accent) }
                }
                itemsIndexed(
                    items,
                    key = { _, item -> when (item) {
                        is ChatItem.Message -> "m-${item.index}"
                        // 流式项与生成完成的最后一条消息共用同一 key + contentType：
                        // 结束瞬间是“内容原地替换”而不是“删一行+插一行”，不触发位移动画/闪跳
                        ChatItem.Streaming -> "m-${items.lastIndex}"
                        ChatItem.ReasoningOnly -> "m-${items.lastIndex}"
                    } },
                    contentType = { _, item -> when (item) {
                        is ChatItem.Message -> "chat-message"
                        ChatItem.Streaming -> "chat-message"
                        ChatItem.ReasoningOnly -> "chat-message"
                    } },
                ) { _, item ->
                    when (item) {
                        is ChatItem.Message -> {
                            val el = item.element
                            // 派生字段只随消息元素变化重算：流式 tick 不再为历史消息反复解析 JSON/读缓存
                            val derived = remember(el) {
                                val user = isUser(el)
                                ChatItemDerived(
                                    isUser = user,
                                    isSystem = isSystem(el),
                                    text = vm.displayTextOf(item.index),
                                    media = mediaOf(el),
                                    mediaDisplay = extraDisplayOf(el),
                                    mediaIndex = extraIndexOf(el),
                                    name = nameOf(el, user),
                                    time = timeOf(el),
                                    swipeCount = vm.swipeCountOf(el),
                                    curSwipe = vm.currentSwipeOf(el),
                                )
                            }
                            val isUserMsg = derived.isUser
                            val isSystemMsg = derived.isSystem
                            val text = derived.text
                            // 附件列表包一层稳定类型，避免 List 参数让整行不可跳过重组
                            val mediaList = remember(derived.media) { ChatMedia(derived.media) }
                            val immersiveActions = rowImmersiveActions
                            val showActions = !isStreaming && item.index == lastAiIndex && !isUserMsg && !isSystemMsg && !immersiveActions
                            val isPrevSameSender =
                                item.index > 0 && isUser(messages[item.index - 1]) == isUserMsg
                            val prevEl = if (item.index == 0) null else messages[item.index - 1]
                            val dateLabel = remember(item.index, el, prevEl) {
                                if (prevEl == null) {
                                    dateLabelOf(el)
                                } else {
                                    val prev = dateLabelOf(prevEl)
                                    val cur = dateLabelOf(el)
                                    if (prev == cur) null else cur
                                }
                            }
                            MessageRow(
                                modifier = Modifier,
                                isUser = isUserMsg,
                                isSystem = isSystemMsg,
                                text = text,
                                media = mediaList,
                                mediaDisplay = derived.mediaDisplay,
                                mediaIndex = derived.mediaIndex,
                                onMediaIndexChange = { idx -> vm.setMediaIndex(item.index, idx) },
                                reasoning = if (!isStreaming && !isUserMsg && item.index == lastAiIndex) lastReasoning else null,
                                reasoningExpanded = reasoningExpanded,
                                onReasoningToggle = { reasoningExpanded = !reasoningExpanded },
                                name = derived.name,
                                time = derived.time,
                                dateLabel = dateLabel,
                                avatarPath = if (isUserMsg) null else vm.avatarPath,
                                accent = accent,
                                aiBubble = rowBubbleStyle == "bubble",
                                onImageToggle = { vm.setMediaDisplay(item.index) },
                                showActions = showActions,
                                swipeCount = derived.swipeCount,
                                curSwipe = derived.curSwipe,
                                isPrevSameSender = isPrevSameSender,
                                onSwipeLeft = { vm.swipeLeft(item.index) },
                                onSwipeRight = { vm.swipeRight(item.index) },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(text))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                                onRegenerate = { vm.regenerate() },
                                onContinue = { vm.continueGeneration() },
                                onDelete = { deleteTargetIndex = item.index },
                                onLongPress = { menuMessageIndex = item.index },
                            )
                        }
                        ChatItem.Streaming -> StreamingRow(
                            modifier = Modifier,
                            text = streamingDisplay,
                            reasoning = streamingReasoning,
                            reasoningExpanded = reasoningExpanded,
                            onReasoningToggle = { reasoningExpanded = !reasoningExpanded },
                            name = currentName,
                            avatarPath = vm.avatarPath,
                            accent = accent,
                            impersonating = isImpersonating,
                        )
                        ChatItem.ReasoningOnly -> {
                            lastReasoning?.let {
                                ReasoningCard(
                                    text = it,
                                    expanded = reasoningExpanded,
                                    onToggle = { reasoningExpanded = !reasoningExpanded },
                                )
                            }
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

        }

        ChatTopBar(
            name = currentName,
            avatarPath = vm.avatarPath,
            accent = accent,
            onBack = onBack,
            onMenu = { showMore = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeight = it.height }
                .glassEdgeHighlight(dark = glassDark, atTop = false)
                .then(
                    if (AppearancePrefs.backgroundBlur(context)) {
                        Modifier.cloudy(sky = sky, radius = AppearancePrefs.blurStrength(context).coerceAtLeast(1), tint = glassTint().copy(alpha = 0.38f))
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    },
                ),
        )

        ChatInputBar(
            accent = accent,
            input = input,
            onInputChange = { input = it },
            pendingMedia = pendingMedia,
            pendingDisplay = pendingDisplay,
            onDisplayChange = { pendingDisplay = it },
            onRemoveMedia = { index -> vm.removePendingMedia(index) },
            isStreaming = isStreaming,
            canQuickContinue = !isStreaming && vm.canContinueGeneration(),
            quickBarOpen = showQuickBar,
            worldHitsCount = worldHits.size,
            contextUsage = contextUsage,
            onOpenWorldPanel = { worldPanel = true },
            onOpenContextDetail = { contextDetail = true },
            onToggleQuickBar = { showQuickBar = !showQuickBar },
            quickReplies = quickReplies,
            onQuickReply = { label -> vm.runQuickReply(label) },
            onQuickImage = { showImageDialog = true; showQuickBar = false },
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
                    followBottom = true
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val accepted = vm.send(text, media = pendingMedia, mediaDisplay = pendingDisplay)
                    if (accepted) {
                        input = ""
                        pendingDisplay = null
                        // 发完先滚底再收键盘：滚动在键盘未收起的小视口里完成，键盘收起后视口向下扩展，
                        // 最后一条仍钉在底部——不猜动画时长，也没有“滚到旧视口”的中间态
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
                            }
                            keyboardController?.hide()
                        }
                    }
                }
            },
            onStop = { vm.stop() },
            onAttach = {
                showAttachOptions = true
            },
            onVoice = {
                Toast.makeText(context, "语音输入开发中", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { inputBarHeight = it.height }
                .glassEdgeHighlight(dark = glassDark, atTop = true)
                .then(
                    if (AppearancePrefs.backgroundBlur(context)) {
                        Modifier.cloudy(sky = sky, radius = AppearancePrefs.blurStrength(context).coerceAtLeast(1), tint = glassTint().copy(alpha = 0.42f))
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    },
                ),
        )
    }

    if (worldPanel) {
        EmberBottomSheet(onDismissRequest = { worldPanel = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("世界书命中", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(4.dp))
                Text(
                    "本次发送注入的世界书条目（点击状态胶囊打开）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                if (worldHits.isEmpty()) {
                    Text("没有命中条目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        WorldHitLight(MaterialTheme.colorScheme.primary, "常驻")
                        WorldHitLight(MaterialTheme.colorScheme.secondary, "关键词")
                        WorldHitLight(Color(0xFFE09A3E), "概率")
                        WorldHitLight(MaterialTheme.colorScheme.tertiary, "向量")
                    }
                    worldHits.forEach { hit ->
                        val hitLabel = when {
                            hit.constant -> "常驻"
                            hit.vectorized -> "向量"
                            hit.useProbability -> "概率命中"
                            else -> "关键词命中"
                        }
                        val lightColor = when {
                            hit.constant -> MaterialTheme.colorScheme.primary
                            hit.vectorized -> MaterialTheme.colorScheme.tertiary
                            hit.useProbability -> Color(0xFFE09A3E)
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape)
                                    .background(lightColor),
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hit.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    listOf(
                                        hitLabel,
                                        hit.key.takeIf { it.isNotBlank() }?.let { "键：$it" },
                                        hit.positionLabel,
                                        "${hit.tokens} token",
                                    ).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (contextDetail) {
        val usage = contextUsage
        if (usage != null) {
            EmberBottomSheet(onDismissRequest = { contextDetail = false }, sheetState = rememberModalBottomSheetState()) {
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
        }
    }

    menuMessageIndex?.let { index ->
        val el = messages.getOrNull(index)
        if (el != null) {
            val text = textOf(el)
            val isUserMsg = isUser(el)
            EmberBottomSheet(onDismissRequest = { menuMessageIndex = null }, sheetState = rememberModalBottomSheetState()) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        if (isUserMsg) "我的消息" else currentName,
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
                    MenuRow(PhosphorIcons.SpeakerHigh, "朗读这条消息") {
                        vm.narrateMessage(index)
                        menuMessageIndex = null
                    }
                    MenuRow(PhosphorIcons.FileText, "翻译这条消息") {
                        vm.translateMessage(index)
                        menuMessageIndex = null
                    }
                    MenuRow(PhosphorIcons.ChartBar, "Token 统计") {
                        tokenStatsIndex = index
                        menuMessageIndex = null
                    }
                    MenuRow(PhosphorIcons.BookmarkSimple, "创建书签（存档到此）") {
                        menuMessageIndex = null
                        bookmarkDraftName = vm.defaultBookmarkName()
                        showBookmarkDialog = true
                    }
                    val swipeCount = vm.swipeCountOf(el)
                    val isSystemMsg = isSystem(el)
                    if (!isUserMsg && !isSystemMsg) {
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
                            // 官方 swipe：任何 AI 消息都能生成变体（AI 消息落盘即带 swipes，恒显示入口）
                            MenuRow(PhosphorIcons.CaretRight, "生成新回复（变体）") {
                                vm.generateSwipe(); menuMessageIndex = null
                            }
                        }
                    }
                    if (swipeCount >= 1 && !isSystemMsg) {
                        MenuRow(PhosphorIcons.CaretLeft, "上一个回复") {
                            vm.swipeLeft(index); menuMessageIndex = null
                        }
                        MenuRow(PhosphorIcons.CaretRight, "下一个回复") {
                            vm.swipeRight(index); menuMessageIndex = null
                        }
                        MenuRow(PhosphorIcons.List, "变体列表") {
                            swipePickerIndex = index; menuMessageIndex = null
                        }
                        if (swipeCount > 1) {
                            MenuRow(PhosphorIcons.Delete, "删除当前回复", danger = true) {
                                deleteSwipeTargetIndex = index; menuMessageIndex = null
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
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
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

    deleteSwipeTargetIndex?.let { index ->
        val el = messages.getOrNull(index)
        val cur = if (el != null) vm.currentSwipeOf(el) + 1 else 0
        AlertDialog(
            onDismissRequest = { deleteSwipeTargetIndex = null },
            title = { Text("删除这个回复？") },
            text = { Text("将删除该消息的第 $cur 个回复变体，删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    if (el != null) {
                        vm.deleteSwipe(index, vm.currentSwipeOf(el))
                        Toast.makeText(context, "已删除该回复", Toast.LENGTH_SHORT).show()
                    }
                    deleteSwipeTargetIndex = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSwipeTargetIndex = null }) { Text("取消") }
            },
        )
    }

    editIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("编辑消息") },
            text = {
                EmberTextField(
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
        EmberBottomSheet(onDismissRequest = { showMore = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "会话菜单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                MenuRow(PhosphorIcons.Folder, "聊天背景") {
                    showMore = false
                    backgroundPicker.launch(arrayOf("image/*"))
                }
                if (chatBackground != null) {
                    MenuRow(PhosphorIcons.Folder, "清除聊天背景") {
                        showMore = false
                        vm.clearChatBackground()
                    }
                }
                MenuRow(PhosphorIcons.BookmarkSimple, "书签") {
                    showMore = false
                    showBookmarksSheet = true
                }
                MenuRow(PhosphorIcons.FileText, "数据银行（向量检索）") {
                    showMore = false
                    showDataBank = true
                }
                MenuRow(PhosphorIcons.Edit, "作者注释") {
                    showMore = false
                    val draft = vm.authorsNoteDraft()
                    anPrompt = draft.prompt
                    anPosition = draft.position
                    anDepth = draft.depth
                    showAuthorsNote = true
                }
                if (vm.group != null) {
                    MenuRow(PhosphorIcons.Person, "群聊设置") {
                        showMore = false
                        groupMode = vm.group?.generationMode ?: GroupGenerationMode.APPEND
                        groupStrategy = vm.group?.activationStrategy ?: "natural"
                        showGroupSettings = true
                    }
                }
                MenuRow(PhosphorIcons.Person, "人设") {
                    showMore = false
                    showPersonaPicker = true
                }
                if (vm.character != null) {
                    MenuRow(PhosphorIcons.Person, "角色详情") {
                        showMore = false
                        showCharacterInfo = true
                    }
                }
                MenuRow(PhosphorIcons.Share, "导出聊天（JSONL）") {
                    showMore = false
                    exportChatLauncher.launch("$currentName-${System.currentTimeMillis().toString().takeLast(8)}.jsonl")
                }
                MenuRow(PhosphorIcons.Delete, "清空会话", danger = true) {
                    showMore = false
                    showClearConfirm = true
                }
            }
        }
    }

    if (showAuthorsNote) {
        AlertDialog(
            onDismissRequest = { showAuthorsNote = false },
            title = { Text("作者注释") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    EmberTextField(
                        value = anPrompt,
                        onValueChange = { anPrompt = it },
                        label = { Text("注释内容（留空清除）") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "注入位置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = anPosition == 2, onClick = { anPosition = 2 }, label = { Text("提示词前") })
                        FilterChip(selected = anPosition == 0, onClick = { anPosition = 0 }, label = { Text("提示词内") })
                        FilterChip(selected = anPosition == 1, onClick = { anPosition = 1 }, label = { Text("对话内") })
                    }
                    EmberTextField(
                        value = anDepth.toString(),
                        onValueChange = { anDepth = it.toIntOrNull() ?: 4 },
                        label = { Text("深度（对话内注入时生效）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveAuthorsNote(anPrompt.trim(), anPosition, anDepth, 0)
                    showAuthorsNote = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAuthorsNote = false }) { Text("取消") }
            },
        )
    }

    if (showGroupSettings) {
        AlertDialog(
            onDismissRequest = { showGroupSettings = false },
            title = { Text("群聊设置") },
            text = {
                Column {
                    Text("生成模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        FilterChip(
                            selected = groupMode == GroupGenerationMode.APPEND,
                            onClick = { groupMode = GroupGenerationMode.APPEND },
                            label = { Text("全员依次（APPEND）") },
                        )
                        Spacer(Modifier.widthIn(min = 8.dp))
                        FilterChip(
                            selected = groupMode == GroupGenerationMode.SWAP,
                            onClick = { groupMode = GroupGenerationMode.SWAP },
                            label = { Text("轮流（SWAP）") },
                        )
                    }
                    Text("激活策略", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        FilterChip(
                            selected = groupStrategy == "natural",
                            onClick = { groupStrategy = "natural" },
                            label = { Text("natural") },
                        )
                        Spacer(Modifier.widthIn(min = 8.dp))
                        FilterChip(
                            selected = groupStrategy == "pooled",
                            onClick = { groupStrategy = "pooled" },
                            label = { Text("pooled") },
                        )
                    }
                    Text(
                        "APPEND=本轮全员依次回复；SWAP=上一发言人之后轮流。策略切换对下一轮生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveGroupSettings(groupMode, groupStrategy)
                    showGroupSettings = false
                    Toast.makeText(context, "已保存群聊设置", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupSettings = false }) { Text("取消") }
            },
        )
    }

    if (showPersonaPicker) {
        EmberBottomSheet(onDismissRequest = { showPersonaPicker = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "人设",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                if (personas.isEmpty()) {
                    Text(
                        "还没有人设。新建后，人设描述会注入提示词（官方 Persona Management 语义）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                personas.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.setPersona(p.id)
                            showPersonaPicker = false
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.name.ifBlank { "（未命名）" },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (activePersona?.id == p.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (p.description.isNotBlank()) {
                                Text(
                                    p.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (activePersona?.id == p.id) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            editingPersona = p
                            personaDraftName = p.name
                            personaDraftDesc = p.description
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(PhosphorIcons.Edit, contentDescription = "编辑人设", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { vm.deletePersona(p.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(PhosphorIcons.Delete, contentDescription = "删除人设", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
                TextButton(
                    onClick = {
                        editingPersona = Persona(id = "p-" + System.nanoTime().toString(36), name = "", description = "")
                        personaDraftName = ""
                        personaDraftDesc = ""
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("＋ 新建人设") }
            }
        }
    }

    editingPersona?.let { target ->
        AlertDialog(
            onDismissRequest = { editingPersona = null },
            title = { Text(if (target.name.isBlank()) "新建人设" else "编辑人设") },
            text = {
                Column {
                    EmberTextField(
                        value = personaDraftName,
                        onValueChange = { personaDraftName = it },
                        label = { Text("人设名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmberTextField(
                        value = personaDraftDesc,
                        onValueChange = { personaDraftDesc = it },
                        label = { Text("描述（支持 {{char}}/{{user}} 宏）") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.savePersona(target.copy(name = personaDraftName.trim(), description = personaDraftDesc))
                    editingPersona = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingPersona = null }) { Text("取消") }
            },
        )
    }

    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            title = { Text("图像生成") },
            text = {
                EmberTextField(
                    value = imagePrompt,
                    onValueChange = { imagePrompt = it },
                    label = { Text("提示词（AUTOMATIC1111）") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val prompt = imagePrompt.trim()
                    if (prompt.isNotEmpty()) vm.generateImage(prompt)
                    showImageDialog = false
                    imagePrompt = ""
                }) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { showImageDialog = false }) { Text("取消") }
            },
        )
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("创建书签") },
            text = {
                EmberTextField(
                    value = bookmarkDraftName,
                    onValueChange = { bookmarkDraftName = it },
                    label = { Text("书签名（当前聊天存档）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = bookmarkDraftName.trim()
                    if (name.isNotEmpty()) vm.createBookmark(name)
                    showBookmarkDialog = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) { Text("取消") }
            },
        )
    }

    if (showBookmarksSheet) {
        EmberBottomSheet(onDismissRequest = { showBookmarksSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "书签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                if (bookmarks.isEmpty()) {
                    Text(
                        "还没有书签。长按消息 → 创建书签，把当前聊天存档下来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                bookmarks.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showBookmarksSheet = false
                            bookmarkToOpen = name
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            vm.deleteBookmark(name)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(PhosphorIcons.Delete, contentDescription = "删除书签", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }

    if (showDataBank) {
        EmberBottomSheet(onDismissRequest = { showDataBank = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "数据银行（向量检索）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                Text(
                    "把文本文件放进数据银行，发送消息时会按官方 vectors 扩展分块检索，把相关内容注入提示词（设置→服务→向量 开启）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (dataBank.isEmpty()) {
                    Text(
                        "还没有文件。点下面“添加文件”选一个 txt / md 文档。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                dataBank.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.removeDataBankFile(name) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(
                        onClick = { dataBankPicker.launch(arrayOf("text/plain", "text/markdown", "application/json")) },
                    ) {
                        Text("添加文件")
                    }
                    TextButton(onClick = { showDataBankUrlDialog = true; dataBankUrlDraft = "" }) {
                        Text("从 URL 添加")
                    }
                }
            }
        }
    }

    if (showDataBankUrlDialog) {
        AlertDialog(
            onDismissRequest = { showDataBankUrlDialog = false },
            title = { Text("从 URL 添加数据银行文件") },
            text = {
                EmberTextField(
                    value = dataBankUrlDraft,
                    onValueChange = { dataBankUrlDraft = it },
                    placeholder = { Text("https://…（文本/markdown）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = dataBankUrlDraft.trim()
                    if (u.isNotBlank()) vm.addDataBankUrl(u)
                    dataBankUrlDraft = ""
                    showDataBankUrlDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showDataBankUrlDialog = false }) { Text("取消") }
            },
        )
    }

    tokenStatsIndex?.let { index ->
        val stats = vm.messageTokenCount(index)
        if (stats != null) {
            AlertDialog(
                onDismissRequest = { tokenStatsIndex = null },
                title = { Text("Token 统计") },
                text = {
                    Column {
                        Text(
                            "按当前模型 tokenizer 估算：${stats.second} tokens",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            stats.first,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { tokenStatsIndex = null }) { Text("关闭") }
                },
            )
        }
    }

    swipePickerIndex?.let { index ->
        val currentEl = messages.getOrNull(index)
        val variants = vm.swipeVariantsOf(index)
        if (variants.isNotEmpty() && currentEl != null) {
            val currentSwipe = vm.currentSwipeOf(currentEl)
            EmberBottomSheet(onDismissRequest = { swipePickerIndex = null }, sheetState = rememberModalBottomSheetState()) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        "回复变体（${currentSwipe + 1}/${variants.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                    variants.forEachIndexed { i, text ->
                        val current = i == currentSwipe
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.swipeToVariant(index, i)
                                swipePickerIndex = null
                            }.padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.widthIn(min = 8.dp))
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (current) {
                                Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }

    bookmarkToOpen?.let { name ->
        AlertDialog(
            onDismissRequest = { bookmarkToOpen = null },
            title = { Text("打开书签「$name」？") },
            text = { Text("当前聊天会被书签存档内容替换，此操作不可撤销。建议先创建新书签。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.openBookmark(name)
                    bookmarkToOpen = null
                }) { Text("打开", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToOpen = null }) { Text("取消") }
            },
        )
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
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
                .heightIn(min = 52.dp),
        ) {
            // 返回按钮在左上角（配合边缘滑动返回），留足上下间距避免贴最高处
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
    // 头像形状（全局设置，对齐官方 --avatar-base-border-radius：方形 2px / 圆角 10px / 圆形 50%）
    val shape = when (AppearancePrefs.avatarShape(LocalContext.current)) {
        "square" -> RoundedCornerShape(2.dp)
        "rounded" -> RoundedCornerShape(10.dp)
        else -> CircleShape
    }
    // accent 细描边：角色代入感的视觉锚点，描边叠在图片边缘
    val ring = Modifier
        .size(size.dp)
        .clip(shape)
        .border(1.dp, accent.copy(alpha = 0.4f), shape)
    if (avatarFile != null) {
        AsyncImage(
            model = avatarFile,
            contentDescription = name,
            modifier = ring,
        )
    } else {
        Box(
            modifier = ring.background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).ifBlank { "✦" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
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
    modifier: Modifier = Modifier,
    isUser: Boolean,
    isSystem: Boolean = false,
    text: String,
    media: ChatMedia,
    mediaDisplay: String? = null,
    mediaIndex: Int? = null,
    onMediaIndexChange: (Int) -> Unit = {},
    reasoning: String?,
    reasoningExpanded: Boolean = false,
    onReasoningToggle: () -> Unit = {},
    name: String,
    time: String,
    avatarPath: String?,
    accent: Color,
    dateLabel: String?,
    showActions: Boolean,
    swipeCount: Int = 0,
    curSwipe: Int = 0,
    isPrevSameSender: Boolean = true,
    aiBubble: Boolean = false,
    onImageToggle: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val textShadow = chatTextShadow()
    val stTheme = LocalThemePreset.current
    val stDark = isDarkThemeSurface()
    val emColor = parseHexColor(AppearancePrefs.stEmColor(context))
        ?: (if (stDark) stTheme.stEm else null)
        ?: MaterialTheme.colorScheme.outline
    val userBubbleColor = parseHexColor(AppearancePrefs.stUserBubble(context))
        ?: (if (stDark) stTheme.stUserBubble else null)
        ?: MaterialTheme.colorScheme.primaryContainer
    val botBubbleColor = parseHexColor(AppearancePrefs.stBotBubble(context))
        ?: (if (stDark) stTheme.stBotBubble else null)
        ?: MaterialTheme.colorScheme.surfaceContainerLow
    val bubbleBorder = (parseHexColor(AppearancePrefs.stBorderColor(context))
        ?: (if (stDark) stTheme.stBorder else null))?.let { BorderStroke(1.dp, it) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 间距层级：不同发言者之间留白更大，同一发言者连续消息收紧（纸面对话流而非堆砌）
        if (dateLabel == null && !isPrevSameSender) {
            Spacer(Modifier.size(7.dp))
        }
        if (dateLabel != null) {
            Text(
                text = dateLabel,
                style = (MaterialTheme.typography.labelSmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                color = emColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (isUser) 12.dp else 0.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 36)
            Spacer(Modifier.size(10.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.78f else 1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = (MaterialTheme.typography.labelMedium).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                    color = when {
                        isUser || isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> accent
                    },
                    fontWeight = FontWeight.Medium,
                    fontStyle = if (isSystem) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = time,
                    style = (MaterialTheme.typography.labelSmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                    color = emColor,
                )
            }
            // 思考过程：一个卡，正文上方，默认折叠，点开展开（流式/生成完共用同一状态）
            if (!reasoning.isNullOrBlank()) {
                Spacer(Modifier.size(4.dp))
                ReasoningCard(
                    text = reasoning,
                    expanded = reasoningExpanded,
                    onToggle = onReasoningToggle,
                )
            }
            Spacer(Modifier.size(3.dp))
            // 滑动切回复：AI 气泡横滑（右滑=下一个/生成变体，左滑=上一个）；不干扰列表纵向滚动
            var bubbleModifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
            if (!isUser && !isSystem) {
                val threshold = with(LocalDensity.current) { 56.dp.toPx() }
                bubbleModifier = bubbleModifier.then(
                    Modifier.pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (total > threshold) onSwipeLeft()
                                else if (total < -threshold) onSwipeRight()
                                total = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            total += dragAmount
                        }
                    },
                )
            }
            if (isUser) {
                // 用户消息保留右侧胶囊：对话分隔锚点，和 AI 纯文本流形成对比
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    color = userBubbleColor,
                    border = bubbleBorder,
                    modifier = bubbleModifier,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        ChatMarkdown(
                            content = text,
                            onSurface = MaterialTheme.colorScheme.onPrimaryContainer,
                            isSystem = false,
                            charAvatarPath = avatarPath,
                        )
                    }
                }
            } else if (aiBubble) {
                // README 气泡样式=bubble：AI 也带低对比气泡
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
                    color = botBubbleColor,
                    border = bubbleBorder,
                    modifier = bubbleModifier,
                ) {
                    ChatMarkdown(
                        content = text,
                        onSurface = if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        isSystem = isSystem,
                        charAvatarPath = avatarPath,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    )
                }
            } else {
                // AI 消息去气泡：纯 markdown 文本流，靠留白分隔（纸面阅读感）
                ChatMarkdown(
                    content = text,
                    onSurface = if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    isSystem = isSystem,
                    charAvatarPath = avatarPath,
                    modifier = bubbleModifier,
                )
            }
            // 回复变体计数条（对齐官方 swipes-counter：n/total + 左右箭头；仅在已有变体时显示）
            if (swipeCount >= 1 && !isSystem) {
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSwipeLeft,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            PhosphorIcons.CaretLeft,
                            contentDescription = "上一个回复",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = "${curSwipe + 1}/${swipeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    IconButton(
                        onClick = onSwipeRight,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            PhosphorIcons.CaretRight,
                            contentDescription = "下一个回复",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            if (media.items.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                MessageMedia(media = media.items, display = mediaDisplay, index = mediaIndex, onIndexChange = onMediaIndexChange, onImageToggle = onImageToggle)
            }
            if (showActions) {
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    modifier: Modifier = Modifier,
    text: String,
    reasoning: String = "",
    reasoningExpanded: Boolean = false,
    onReasoningToggle: () -> Unit = {},
    name: String,
    avatarPath: String?,
    accent: Color,
    impersonating: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    val caretScale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "caretScale",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        RoleAvatar(avatarPath = if (impersonating) null else avatarPath, name = if (impersonating) "我" else name, accent = if (impersonating) MaterialTheme.colorScheme.secondary else accent, size = 36)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (impersonating) "冒充草稿 · 我" else name,
                style = MaterialTheme.typography.labelMedium,
                color = if (impersonating) MaterialTheme.colorScheme.secondary else accent,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(4.dp))
            // 流式思考：同一个卡，默认折叠，点开展开看实时思考过程
            if (reasoning.isNotBlank()) {
                ReasoningCard(
                    text = reasoning,
                    expanded = reasoningExpanded,
                    onToggle = onReasoningToggle,
                )
                Spacer(Modifier.size(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreamingMarkdown(
                    content = text.ifEmpty { "…" },
                )
                // 呼吸圆点光标：缩放 + 淡入淡出，比 ▍ 打字光标更细腻
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 2.dp)
                        .size(6.dp)
                        .graphicsLayer {
                            scaleX = caretScale
                            scaleY = caretScale
                            this.alpha = caretAlpha
                        }
                        .background(accent, CircleShape),
                )
            }
        }
    }
}

/** 流式轻量渲染：流式中不跑完整 Markdown 解析（mikepenz 流式更新会 cancel/restart 空转，整段 parse 高概率被丢弃），
 *  只做粗粒度着色（标题→粗体、**粗**、*斜*、~~删~~、~下划线~、行内码、六种引号对、链接）。
 *  生成结束后由 ChatMarkdown 一次性完整重渲染，视觉与最终一致。 */
@Composable
private fun StreamingMarkdown(content: String) {
    val context = LocalContext.current
    val stTheme = LocalThemePreset.current
    val stDark = isDarkThemeSurface()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bodyColor = parseHexColor(AppearancePrefs.stBodyColor(context))
        ?: (if (stDark) stTheme.stBody else null)
        ?: onSurface
    val quoteColor = parseHexColor(AppearancePrefs.stQuoteColor(context))
        ?: (if (stDark) stTheme.stQuote else null)
        ?: MaterialTheme.colorScheme.primary
    val emColor = parseHexColor(AppearancePrefs.stEmColor(context))
        ?: (if (stDark) stTheme.stEm else null)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val underlineColor = parseHexColor(AppearancePrefs.stUnderlineColor(context))
        ?: (if (stDark) stTheme.stUnderline else null)
        ?: MaterialTheme.colorScheme.primary
    val styled = remember(content, bodyColor, quoteColor, emColor, underlineColor) {
        streamingStyledText(content, bodyColor, quoteColor, emColor, underlineColor)
    }
    Text(
        text = styled,
        style = chatTypography().body,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 轻量流式着色（见 StreamingMarkdown）。 */
private fun streamingStyledText(
    raw: String,
    bodyColor: Color,
    quoteColor: Color,
    emColor: Color,
    underlineColor: Color,
): AnnotatedString {
    val cleaned = Regex("""(?m)^\s{0,3}(#{1,6})\s+(.+)$""").replace(raw) { m -> "**${m.groupValues[2]}**" }
    val out = AnnotatedString.Builder()
    out.pushStyle(SpanStyle(color = bodyColor))
    val pattern = Regex(
        """\*\*([^*\n]+)\*\*|\*([^*\n]+)\*|~~([^~\n]+)~~|(?<!~)~([^~\n]+)~(?!~)|`([^`\n]+)`|"([^"]*)"|“([^”]*)”|«([^»]*)»|「([^」]*)」|『([^』]*)』|＂([^＂]*)＂|\[([^\]\n]+)\]\(([^)\n]+)\)""",
    )
    var last = 0
    for (m in pattern.findAll(cleaned)) {
        out.append(cleaned.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> {
                out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                out.append(g[1])
                out.pop()
            }
            g[2].isNotEmpty() -> {
                out.pushStyle(SpanStyle(color = emColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                out.append(g[2])
                out.pop()
            }
            g[3].isNotEmpty() -> {
                out.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                out.append(g[3])
                out.pop()
            }
            g[4].isNotEmpty() -> {
                out.pushStyle(SpanStyle(color = underlineColor, textDecoration = TextDecoration.Underline))
                out.append(g[4])
                out.pop()
            }
            g[5].isNotEmpty() -> {
                out.pushStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                out.append(g[5])
                out.pop()
            }
            else -> {
                val isLink = m.value.startsWith("[")
                out.pushStyle(SpanStyle(color = quoteColor, fontWeight = if (isLink) FontWeight.Medium else null))
                out.append(if (isLink) g[12] else m.value)
                out.pop()
            }
        }
        last = m.range.last + 1
    }
    out.append(cleaned.substring(last))
    return out.toAnnotatedString()
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

/** 状态胶囊（世界书命中等），README 状态可见；中性低调，不抢输入区。 */
@Composable
private fun StatusPill(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    EmberBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
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

/** 消息附件渲染：图片/GIF 用 Coil3（coil-gif），音视频用 Media3 ExoPlayer（README 渲染规范）。
 *  gallery = 官方 extra.media_display=GALLERY：多图单张显示 + 左右滑切（media_index 落盘）+ 圆点计数；
 *  list / 缺省 = 全部纵向排列。 */
@Composable
private fun MessageMedia(
    media: List<MediaAttachment>,
    display: String? = null,
    index: Int? = null,
    onIndexChange: (Int) -> Unit = {},
    onImageToggle: () -> Unit = {},
) {
    val images = media.filter { it.type == "image" }
    val others = media.filter { it.type != "image" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (display == "gallery" && images.size > 1) {
            val safeIndex = (index ?: 0).coerceIn(0, images.lastIndex)
            val image = images[safeIndex]
            val threshold = with(LocalDensity.current) { 48.dp.toPx() }
            var dragTotal by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImageToggle)
                    .pointerInput(images.size, safeIndex) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragTotal += amount
                            },
                            onDragEnd = {
                                if (dragTotal < -threshold && safeIndex < images.lastIndex) {
                                    onIndexChange(safeIndex + 1)
                                } else if (dragTotal > threshold && safeIndex > 0) {
                                    onIndexChange(safeIndex - 1)
                                }
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f },
                        )
                    },
            ) {
                AsyncImage(
                    model = mediaModel(image.url),
                    contentDescription = image.title.ifBlank { "图片" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                repeat(images.size) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == safeIndex) 7.dp else 5.dp)
                            .background(
                                if (i == safeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
        } else {
            // 官方渲染：.mes_img 内联大图（max-width:100%、max-height:40vh、圆角 5px）；
            // 点击图片在 LIST ↔ GALLERY 间切换（官方 chats.js switchMessageMediaDisplay）
            media.forEach { m ->
                when (m.type) {
                    "image" -> AsyncImage(
                        model = mediaModel(m.url),
                        contentDescription = m.title.ifBlank { "图片" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onImageToggle),
                    )
                    else -> MediaPlayer(m.url, isAudio = m.type == "audio")
                }
            }
        }
        // 图库模式：图片在图库区显示，非图媒体（音视频）统一在下方列出一次
        if (display == "gallery" && images.size > 1) {
            others.forEach { m -> MediaPlayer(m.url, isAudio = m.type == "audio") }
        }
    }
}

/** 读取消息 extra.media_display（list/gallery）。 */
private fun extraDisplayOf(el: JsonElement): String? =
    (el.jsonObject["extra"] as? JsonObject)?.get("media_display")?.jsonPrimitive?.contentOrNull
        ?.takeIf { it == "list" || it == "gallery" }

/** 读取消息 extra.media_index（gallery 当前选中，缺省 0）。 */
private fun extraIndexOf(el: JsonElement): Int? =
    (el.jsonObject["extra"] as? JsonObject)?.get("media_index")?.jsonPrimitive?.content?.toIntOrNull()

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

/** 思考过程：唯一的一个卡，正文上方；受控展开（流式/生成完共用同一状态），默认折叠。 */
@Composable
private fun ReasoningCard(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val textShadow = chatTextShadow()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "思考过程 ▾" else "思考过程 ▸",
                style = (MaterialTheme.typography.labelSmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium,
            )
        }
        if (expanded) {
            Spacer(Modifier.size(5.dp))
            Text(
                text = text,
                style = (MaterialTheme.typography.bodySmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 聊天文字排版（独立“文字排版”设置页全量可调）：正文/行高/字重/标题/引用/代码/间距。 */
@Composable
private fun chatTypography(): ChatTypography {
    val context = LocalContext.current
    val textSize = AppearancePrefs.textSize(context)
    val lineHeight = AppearancePrefs.lineHeight(context)
    val bodyWeightPref = AppearancePrefs.bodyWeight(context)
    val headingStyle = AppearancePrefs.headingStyle(context)
    val h1Mult = AppearancePrefs.headingH1(context)
    val h2Mult = AppearancePrefs.headingH2(context)
    val codeMult = AppearancePrefs.codeSize(context)
    val inlineCodeMult = AppearancePrefs.inlineCodeSize(context)
    val quoteItalic = AppearancePrefs.quoteItalic(context)
    val baseTypography = MaterialTheme.typography
    val shadow = chatTextShadow()
    // 排版参数不变就复用整套 TextStyle：流式每 tick / 每条消息重组时不再重复分配几十个对象
    return remember(textSize, lineHeight, bodyWeightPref, headingStyle, h1Mult, h2Mult, codeMult, inlineCodeMult, quoteItalic, shadow, baseTypography) {
        val textSizeSp = when (textSize) {
            "small" -> 14f
            "official" -> 15f
            "large" -> 18f
            "xlarge" -> 20f
            else -> 16f
        }
        val lineFactor = when (lineHeight) {
            "compact" -> 1.4f
            "loose" -> 1.7f
            else -> 1.55f
        }
        val bodyWeight = when (bodyWeightPref) {
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            else -> FontWeight.Normal
        }
        val realHeading = headingStyle == "real"
        fun size(mult: Float) = (textSizeSp * mult).sp
        fun line(mult: Float) = (textSizeSp * mult * lineFactor).sp
        val body = baseTypography.bodyMedium.copy(
            fontSize = size(1f),
            lineHeight = line(1f),
            fontWeight = bodyWeight,
        )
        val typography = ChatTypography(
            body = body,
            h1 = if (realHeading) {
                baseTypography.headlineMedium.copy(fontSize = size(1.5f * h1Mult), lineHeight = line(1.5f * h1Mult), fontWeight = FontWeight.Bold)
            } else {
                baseTypography.titleMedium.copy(fontSize = size(1.15f * h1Mult), lineHeight = line(1.15f * h1Mult), fontWeight = FontWeight.SemiBold)
            },
            h2 = if (realHeading) {
                baseTypography.headlineSmall.copy(fontSize = size(1.3f * h2Mult), lineHeight = line(1.3f * h2Mult), fontWeight = FontWeight.Bold)
            } else {
                baseTypography.titleMedium.copy(fontSize = size(1.15f * h2Mult), lineHeight = line(1.15f * h2Mult), fontWeight = FontWeight.SemiBold)
            },
            h3 = if (realHeading) {
                baseTypography.titleLarge.copy(fontSize = size(1.15f), lineHeight = line(1.15f), fontWeight = FontWeight.SemiBold)
            } else {
                baseTypography.titleSmall.copy(fontSize = size(1.05f), lineHeight = line(1.05f), fontWeight = FontWeight.Medium)
            },
            h4 = baseTypography.titleSmall.copy(fontSize = size(1.05f), lineHeight = line(1.05f), fontWeight = FontWeight.Medium),
            h5 = baseTypography.titleSmall.copy(fontSize = size(1f), lineHeight = line(1f), fontWeight = FontWeight.Medium),
            h6 = baseTypography.titleSmall.copy(fontSize = size(1f), lineHeight = line(1f), fontWeight = FontWeight.Medium),
            quoteItalic = quoteItalic,
            codeMult = codeMult,
            inlineCodeMult = inlineCodeMult,
        )
        // 官方 style.css：全站文字 text-shadow 0 0 2px rgba(0,0,0,.5)（--SmartThemeShadowColor），全局可调
        if (shadow == null) {
            typography
        } else {
            typography.copy(
                body = typography.body.copy(shadow = shadow),
                h1 = typography.h1.copy(shadow = shadow),
                h2 = typography.h2.copy(shadow = shadow),
                h3 = typography.h3.copy(shadow = shadow),
                h4 = typography.h4.copy(shadow = shadow),
                h5 = typography.h5.copy(shadow = shadow),
                h6 = typography.h6.copy(shadow = shadow),
            )
        }
    }
}

/** 深色表面判断：主题预设的官方 st* 字段是深色专属真值（官方无浅色），浅色模式回退 M3 自动配色。 */
@Composable
private fun isDarkThemeSurface(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/** 全局文字阴影（外观设置）：颜色跟随官方 --SmartThemeShadowColor（消息渲染页可改，默认 rgba(0,0,0,.5)）。 */
@Composable
private fun chatTextShadow(): androidx.compose.ui.graphics.Shadow? {
    val context = LocalContext.current
    val enabled = AppearancePrefs.textShadowEnabled(context)
    val blur = AppearancePrefs.textShadowStrength(context)
    val stTheme = LocalThemePreset.current
    val stDark = isDarkThemeSurface()
    val shadowColor = parseHexColor(AppearancePrefs.stShadowColor(context))
        ?: (if (stDark) stTheme.stShadow else null)
        ?: Color(0x80000000)
    // 设置值不变就复用同一个 Shadow：滚动/流式高频重组时不再每次分配
    return remember(enabled, blur, shadowColor) {
        if (!enabled || blur <= 0) {
            null
        } else {
            androidx.compose.ui.graphics.Shadow(
                color = shadowColor,
                offset = androidx.compose.ui.geometry.Offset.Zero,
                blurRadius = blur.toFloat(),
            )
        }
    }
}

private data class ChatTypography(
    val body: androidx.compose.ui.text.TextStyle,
    val h1: androidx.compose.ui.text.TextStyle,
    val h2: androidx.compose.ui.text.TextStyle,
    val h3: androidx.compose.ui.text.TextStyle,
    val h4: androidx.compose.ui.text.TextStyle,
    val h5: androidx.compose.ui.text.TextStyle,
    val h6: androidx.compose.ui.text.TextStyle,
    val quoteItalic: Boolean,
    val codeMult: Float,
    val inlineCodeMult: Float,
)

/** 官方行内 HTML → 原生可渲染标记：<q>→引用色、<u>/~text~→下划线色、<font color>→指定色、
 *  引号对→引用色（官方 messageFormatting 先转 <q> 再交给 Showdown）；em/b/s/hr/br→Markdown。
 *  标记是私有区字符，渲染前由 applyOfficialMarkers 统一剥掉并按官方 style.css 语义上色。
 *  代码围栏/行内代码/<style> 块先占位保护，避免引号、波浪线、HTML 标签转换污染代码内容
 *  （官方 messageFormatting 的正则同样把 ``` / ~~~ / `` / ` / <style> 放在引号匹配之前）。 */
private fun preprocessOfficialHtml(content: String, convertQuotes: Boolean = true): String {
    val protectedSegments = mutableListOf<String>()
    var out = content
    out = Regex(
        "```[\\s\\S]*?```|~~~[\\s\\S]*?~~~|``[^`\\n]*``|`[^`\\n]*`|<style>[\\s\\S]*?</style>",
        RegexOption.IGNORE_CASE,
    ).replace(out) { m ->
        protectedSegments += m.value
        "\uE100${protectedSegments.lastIndex}\uE101"
    }
    out = Regex("<q[^>]*>([\\s\\S]*?)</q>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE001${m.groupValues[1]}\uE002" }
    out = Regex("<u[^>]*>([\\s\\S]*?)</u>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE003${m.groupValues[1]}\uE004" }
    out = Regex("<(em|i)[^>]*>([\\s\\S]*?)</(em|i)>", RegexOption.IGNORE_CASE).replace(out) { m -> "*${m.groupValues[2]}*" }
    out = Regex("<(b|strong)[^>]*>([\\s\\S]*?)</(b|strong)>", RegexOption.IGNORE_CASE).replace(out) { m -> "**${m.groupValues[2]}**" }
    out = Regex("<(s|strike|del)[^>]*>([\\s\\S]*?)</(s|strike|del)>", RegexOption.IGNORE_CASE).replace(out) { m -> "~~${m.groupValues[2]}~~" }
    out = Regex("<font[^>]*color=[\"']?#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})[\"']?[^>]*>([\\s\\S]*?)</font>", RegexOption.IGNORE_CASE)
        .replace(out) { m -> "\uE005#${m.groupValues[1]}\uE006${m.groupValues[2]}\uE007" }
    out = Regex("<hr[^>]*>", RegexOption.IGNORE_CASE).replace(out, "\n\n---\n\n")
    out = Regex("<br[^>]*>", RegexOption.IGNORE_CASE).replace(out, "  \n")
    // 官方 DOMPurify 白名单里的文本级标签原生渲染（浏览器 UA 默认样式 1:1）：
    // sub/sup 上下标、ins 下划线、small/big 缩放、mark 黄底、kbd/samp/tt/code 等宽、
    // var/dfn/cite 斜体、abbr/acronym 虚线下划线；data/time/wbr 无视觉效果，剥标签留内容。
    // 私有区标记 \uE020-\uE031，applyOfficialMarkers 按层叠加；bdi/bdo/ruby 等方向/注音语义
    // 原生无法表达，仍由 OFFICIAL_HTML_TAG 走 WebView。
    out = Regex("<sub[^>]*>([\\s\\S]*?)</sub>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE020${m.groupValues[1]}\uE021" }
    out = Regex("<sup[^>]*>([\\s\\S]*?)</sup>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE022${m.groupValues[1]}\uE023" }
    out = Regex("<ins[^>]*>([\\s\\S]*?)</ins>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE024${m.groupValues[1]}\uE025" }
    out = Regex("<small[^>]*>([\\s\\S]*?)</small>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE026${m.groupValues[1]}\uE027" }
    out = Regex("<big[^>]*>([\\s\\S]*?)</big>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE028${m.groupValues[1]}\uE029" }
    out = Regex("<mark[^>]*>([\\s\\S]*?)</mark>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE02A${m.groupValues[1]}\uE02B" }
    out = Regex("<(?:kbd|samp|tt|code)[^>]*>([\\s\\S]*?)</(?:kbd|samp|tt|code)>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE02C${m.groupValues[1]}\uE02D" }
    out = Regex("<(?:var|dfn|cite)[^>]*>([\\s\\S]*?)</(?:var|dfn|cite)>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE02E${m.groupValues[1]}\uE02F" }
    out = Regex("<(?:abbr|acronym)(?=[^>]*\\btitle\\b)[^>]*>([\\s\\S]*?)</(?:abbr|acronym)>", RegexOption.IGNORE_CASE).replace(out) { m -> "\uE030${m.groupValues[1]}\uE031" }
    out = Regex("</?(?:abbr|acronym)[^>]*>", RegexOption.IGNORE_CASE).replace(out, "")
    out = Regex("</?(?:data|time|wbr)[^>]*>", RegexOption.IGNORE_CASE).replace(out, "")
    // 可原生表达的 HTML（减少 Web 兜底）：
    // <a href="..">text</a> → [text](url)；无 href 的 <a> 剥标签（官方无视觉）
    // <img src=".."> → ![img](url)（Coil 原生图片）；无 src 的 <img> 剥标签
    // 无属性的 <div>/<p>：块级语义用空行近似（markdown 段落分隔，官方块级排版接近）
    // 无属性的 <span>：行内无视觉语义，直接剥；带属性的 span/div/p 由 Web 元素切分器接管
    out = Regex(
        "<a\\b(?=[^>]*\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))[^>]*>([\\s\\S]*?)</a>",
        RegexOption.IGNORE_CASE,
    ).replace(out) { m ->
        val href = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
        "[${m.groupValues[4]}]($href)"
    }
    out = Regex("</?a[^>]*>", RegexOption.IGNORE_CASE).replace(out, "")
    out = Regex(
        "<img\\b(?=[^>]*\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))[^>]*>",
        RegexOption.IGNORE_CASE,
    ).replace(out) { m ->
        val src = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
        val alt = Regex(
            "\\balt\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            RegexOption.IGNORE_CASE,
        ).find(m.value)?.let { g ->
            g.groupValues[1].ifEmpty { g.groupValues[2].ifEmpty { g.groupValues[3] } }
        } ?: "img"
        "![$alt]($src)"
    }
    out = Regex("<img[^>]*>", RegexOption.IGNORE_CASE).replace(out, "")
    out = Regex("<(div|p)\\s*>", RegexOption.IGNORE_CASE).replace(out, "\n\n")
    out = Regex("</(div|p)\\s*>", RegexOption.IGNORE_CASE).replace(out, "\n")
    out = Regex("<span\\s*>|</span\\s*>", RegexOption.IGNORE_CASE).replace(out, "")
    // 官方 messageFormatting：引号对 → <q>（引用色）；先转私有标记，渲染时整段上色（含内部 Markdown）。
    // 官方仅对非系统消息做引号转换（script.js `if (!isSystem)`），系统消息不做
    if (convertQuotes) {
        out = Regex("\"([^\"]*)\"|“([^”]*)”|«([^»]*)»|「([^」]*)」|『([^』]*)』|＂([^＂]*)＂")
            .replace(out) { m -> "\uE001${m.value}\uE002" }
    }
    // Showdown underline:true：单波浪线 ~text~ → <u>（排除 ~~）
    out = Regex("(?<!~)~([^~\\n]+)~(?!~)").replace(out) { m -> "\uE003${m.groupValues[1]}\uE004" }
    for ((i, seg) in protectedSegments.withIndex()) out = out.replace("\uE100$i\uE101", seg)
    return out
}

/** 标记区间（原始字符串坐标）。 */
private data class OfficialMarker(
    val open: Int,
    val innerStart: Int,
    val innerEnd: Int,
    val close: Int,
    val color: Color? = null,
)

/** 已映射坐标的区间（开区间）。 */
private data class OfficialSpan(val start: Int, val end: Int, val color: Color? = null)

/** outer 区间去掉 holes 覆盖后剩下的分段（holes 为已映射坐标，开区间）。 */
private fun minus(outer: OfficialSpan, holes: List<OfficialSpan>): List<OfficialSpan> {
    val res = mutableListOf<OfficialSpan>()
    var cursor = outer.start
    for (h in holes.sortedBy { it.start }) {
        if (h.end <= cursor) continue
        if (h.start > cursor) res += OfficialSpan(cursor, minOf(h.start, outer.end))
        cursor = maxOf(cursor, h.end)
        if (cursor >= outer.end) break
    }
    if (cursor < outer.end) res += OfficialSpan(cursor, outer.end)
    return res
}

/** 官方 CSS 语义的标记后处理：剥掉 \uE001-\uE007 与文本级标签标记（\uE020-\uE031），并按层上色/加样式。
 *  分层顺序对齐 style.css + 浏览器 UA 默认：
 *  - 文本级（sub/sup/small/big/mark/mono/var/cite/dfn/ins/abbr）先叠加（UA 默认，author 色可覆盖）
 *  - em/i 先按 emColor 着色（库的 annotator 完成）
 *  - q/引号对 → 整段引用色（官方 q em 继承 → 覆盖 em；strong 无色 → 继承引用色）
 *  - u/~text~ → 下划线色+下划线；em 段保留斜体色（官方 .mes_text em 优先于 u）
 *  - font[color] → 最后整段指定色（官方 font[color] em/i/u/q 全部 inherit）
 *  嵌套（引号内引号、font 内 em/u/q、文本级套文本级）由栈配对 + 层序解决。 */
private fun applyOfficialMarkers(
    source: AnnotatedString,
    quoteColor: Color,
    underlineColor: Color,
    baseFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
): AnnotatedString {
    val raw = source.text
    val qStack = ArrayDeque<Int>()
    val uStack = ArrayDeque<Int>()
    val fontStack = ArrayDeque<Pair<Int, Int>>() // (open, sep)
    val subStack = ArrayDeque<Int>()
    val supStack = ArrayDeque<Int>()
    val insStack = ArrayDeque<Int>()
    val smallStack = ArrayDeque<Int>()
    val bigStack = ArrayDeque<Int>()
    val markStack = ArrayDeque<Int>()
    val monoStack = ArrayDeque<Int>()
    val semItalicStack = ArrayDeque<Int>()
    val abbrStack = ArrayDeque<Int>()
    val markers = mutableListOf<OfficialMarker>()
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '\uE001' -> qStack.addLast(i)
            '\uE002' -> qStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE003' -> uStack.addLast(i)
            '\uE004' -> uStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE005' -> fontStack.addLast(i to -1)
            '\uE006' -> if (fontStack.isNotEmpty()) {
                val (open, _) = fontStack.removeLast()
                fontStack.addLast(open to i)
            }
            '\uE007' -> fontStack.removeLastOrNull()?.let { (open, sep) ->
                if (sep > open) {
                    val hex = raw.substring(open + 1, sep)
                    markers += OfficialMarker(open, sep + 1, i, i, parseHexColor(hex) ?: quoteColor)
                }
            }
            '\uE020' -> subStack.addLast(i)
            '\uE021' -> subStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE022' -> supStack.addLast(i)
            '\uE023' -> supStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE024' -> insStack.addLast(i)
            '\uE025' -> insStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE026' -> smallStack.addLast(i)
            '\uE027' -> smallStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE028' -> bigStack.addLast(i)
            '\uE029' -> bigStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE02A' -> markStack.addLast(i)
            '\uE02B' -> markStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE02C' -> monoStack.addLast(i)
            '\uE02D' -> monoStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE02E' -> semItalicStack.addLast(i)
            '\uE02F' -> semItalicStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
            '\uE030' -> abbrStack.addLast(i)
            '\uE031' -> abbrStack.removeLastOrNull()?.let { markers += OfficialMarker(it, it + 1, i, i) }
        }
        i++
    }

    // 剥标记字符并建立旧坐标→新坐标映射
    val removed = BooleanArray(raw.length)
    for (m in markers) {
        removed[m.open] = true
        removed[m.close] = true
        if (m.color != null) {
            // font：开标记与 hex/分隔符之间的字符也剥掉
            for (j in m.open + 1 until m.innerStart) removed[j] = true
        }
    }
    val removedBefore = IntArray(raw.length + 1)
    var count = 0
    for (idx in raw.indices) {
        removedBefore[idx] = count
        if (removed[idx]) count++
    }
    removedBefore[raw.length] = count
    fun map(old: Int): Int = old - removedBefore[old]

    val sb = StringBuilder(raw.length - count)
    for (idx in raw.indices) if (!removed[idx]) sb.append(raw[idx])
    val stripped = sb.toString()

    val emSpans = mutableListOf<OfficialSpan>()
    val mappedSpans = source.spanStyles.mapNotNull { span ->
        val start = map(span.start)
        val end = map(span.end)
        if (start >= end) return@mapNotNull null
        if (span.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) {
            emSpans += OfficialSpan(start, end)
        }
        AnnotatedString.Range(span.item, start, end)
    }
    val mappedParagraphs = source.paragraphStyles.mapNotNull { p ->
        val start = map(p.start)
        val end = map(p.end)
        if (start >= end) null else AnnotatedString.Range(p.item, start, end)
    }

    val qSpans = markers.filter { m ->
        raw.getOrNull(m.open) == '\uE001' && m.close > m.open && m.innerStart < m.innerEnd
    }.map { OfficialSpan(map(it.innerStart), map(it.innerEnd), quoteColor) }
    val uSpans = mutableListOf<OfficialSpan>()
    val fontSpans = mutableListOf<OfficialSpan>()
    val subSpans = mutableListOf<OfficialSpan>()
    val supSpans = mutableListOf<OfficialSpan>()
    val insSpans = mutableListOf<OfficialSpan>()
    val smallSpans = mutableListOf<OfficialSpan>()
    val bigSpans = mutableListOf<OfficialSpan>()
    val markSpans = mutableListOf<OfficialSpan>()
    val monoSpans = mutableListOf<OfficialSpan>()
    val semItalicSpans = mutableListOf<OfficialSpan>()
    val abbrSpans = mutableListOf<OfficialSpan>()
    for (m in markers) {
        if (m.innerStart >= m.innerEnd) continue
        val start = map(m.innerStart)
        val end = map(m.innerEnd)
        if (start >= end) continue
        when {
            m.color != null -> fontSpans += OfficialSpan(start, end, m.color)
            raw.getOrNull(m.open) == '\uE003' -> uSpans += OfficialSpan(start, end, underlineColor)
            raw.getOrNull(m.open) == '\uE020' -> subSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE022' -> supSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE024' -> insSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE026' -> smallSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE028' -> bigSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE02A' -> markSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE02C' -> monoSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE02E' -> semItalicSpans += OfficialSpan(start, end)
            raw.getOrNull(m.open) == '\uE030' -> abbrSpans += OfficialSpan(start, end)
        }
    }

    // 分层（官方 CSS：元素自身的颜色规则优先于继承，因此 q 与 u 互相避让）：
    // 文本级 UA 默认（sub/sup/small/big/mark/mono/var/cite/dfn/ins/abbr）→ em 基色 →
    // q（避开 u 段）→ u 下划线+色（避开 q 与 em 段）→ font 最后全覆盖。
    // 文本级样式先加、author 色（q/u/font/em）后加，与浏览器 UA 样式 < author 样式一致。
    // Chromium UA html.css：sub/sup/small 均为 font-size: smaller（≈0.83em），big 为 larger（1.2em）。
    // TextUnit 无 isSpecified（那是 Color 的属性），用 type 判空；Unspecified 时乘法会抛异常，先兜底。
    val hasBaseSize = baseFontSize.type != androidx.compose.ui.unit.TextUnitType.Unspecified
    val subSupSize = if (hasBaseSize) baseFontSize * 0.83f else androidx.compose.ui.unit.TextUnit.Unspecified
    val smallSize = if (hasBaseSize) baseFontSize * 0.83f else androidx.compose.ui.unit.TextUnit.Unspecified
    val bigSize = if (hasBaseSize) baseFontSize * 1.2f else androidx.compose.ui.unit.TextUnit.Unspecified
    val finalSpans = mappedSpans.toMutableList()
    for (s in subSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(
            fontSize = subSupSize,
            baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript,
        ),
        s.start,
        s.end,
    )
    for (s in supSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(
            fontSize = subSupSize,
            baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript,
        ),
        s.start,
        s.end,
    )
    for (s in insSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(textDecoration = TextDecoration.Underline),
        s.start,
        s.end,
    )
    for (s in smallSpans) finalSpans += AnnotatedString.Range(SpanStyle(fontSize = smallSize), s.start, s.end)
    for (s in bigSpans) finalSpans += AnnotatedString.Range(SpanStyle(fontSize = bigSize), s.start, s.end)
    for (s in monoSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        s.start,
        s.end,
    )
    for (s in semItalicSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        s.start,
        s.end,
    )
    // abbr/acronym：浏览器 UA 为虚线，Compose 无虚线，用实线近似
    for (s in abbrSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(textDecoration = TextDecoration.Underline),
        s.start,
        s.end,
    )
    for (q in qSpans) {
        for (seg in minus(q, uSpans)) {
            finalSpans += AnnotatedString.Range(SpanStyle(color = quoteColor), seg.start, seg.end)
        }
    }
    for (u in uSpans) {
        finalSpans += AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.Underline), u.start, u.end)
        for (seg in minus(u, qSpans).flatMap { minus(it, emSpans) }) {
            finalSpans += AnnotatedString.Range(
                SpanStyle(color = underlineColor, textDecoration = TextDecoration.Underline),
                seg.start,
                seg.end,
            )
        }
    }
    for (f in fontSpans) f.color?.let { finalSpans += AnnotatedString.Range(SpanStyle(color = it), f.start, f.end) }
    // mark 最后加：Chromium UA html.css `mark { background-color: Mark; color: MarkText }`（黄底黑字）。
    // UA 声明优先于继承的 author 色，所以 q/u/font/em 的颜色都不能覆盖它——与官方浏览器行为一致。
    for (s in markSpans) finalSpans += AnnotatedString.Range(
        SpanStyle(background = Color(0xFFFFFF00), color = Color(0xFF000000)),
        s.start,
        s.end,
    )

    val out = AnnotatedString.Builder(stripped)
    for (span in finalSpans) out.addStyle(span.item, span.start, span.end)
    for (p in mappedParagraphs) out.addStyle(p.item, p.start, p.end)
    // Compose 1.11+ 的行内内容（Markdown 图片等）是字符串注解，不是 inlineContent map，需原样平移
    val inlineTag = "androidx.compose.foundation.text.inlineContent"
    for (a in source.getStringAnnotations(inlineTag, 0, raw.length)) {
        val start = map(a.start)
        val end = map(a.end)
        if (start < end) out.addStringAnnotation(inlineTag, a.item, start, end)
    }
    return out.toAnnotatedString()
}

/** 官方行内标记的最终渲染节点：必须在 Markdown 的 CompositionLocalProvider 内调用。
 *  流程：buildMarkdownAnnotatedString 生成基础样式 → applyOfficialMarkers 剥 \uE001-\uE007/\uE020-\uE031 并按层上色/加样式。
 *  text / paragraph / heading1-6 / setextHeading1-2 全部走这里，否则默认组件会绕过管线。 */
@Composable
private fun OfficialMarkdownNode(
    model: com.mikepenz.markdown.compose.components.MarkdownComponentModel,
    style: androidx.compose.ui.text.TextStyle,
    bodyColor: Color,
    emColor: Color,
    quoteColor: Color,
    underlineColor: Color,
    contentChildType: org.intellij.markdown.IElementType? = null,
) {
    // 斜体 annotator 递归构建时需要同一份 settings；用 holder 避免初始化顺序问题
    val settingsHolder = remember { arrayOfNulls<AnnotatorSettings>(1) }
    val emAnnotator = remember(emColor) {
        markdownAnnotator(
            config = markdownAnnotatorConfig(eolAsNewLine = true),
            annotate = { content, child ->
                // 官方 .mes_text i/em { color: emColor }：斜体单独着色；引号/下划线/字体色由
                // applyOfficialMarkers 在最终 AnnotatedString 上按官方 CSS 层级统一处理
                if (child.type == MarkdownElementTypes.EMPH) {
                    pushStyle(SpanStyle(color = emColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    settingsHolder[0]?.let { buildMarkdownAnnotatedString(content, child, it) }
                    pop()
                    true
                } else {
                    false
                }
            },
        )
    }
    val mdSettings = annotatorSettings(
        annotator = emAnnotator,
        linkTextSpanStyle = TextLinkStyles(style = SpanStyle(color = quoteColor)),
    )
    settingsHolder[0] = mdSettings
    // 对齐 MarkdownHeader：ATX/SETEXT 标题只渲染内容子节点，否则 # 号会原样输出
    val targetNode = contentChildType?.let { model.node.findChildOfType(it) } ?: model.node
    // 官方正文色：无色样式（正文/标题/代码）补 bodyColor；引用等已指定色的样式保持自身颜色
    val resolvedStyle = if (style.color.isSpecified) style else style.copy(color = bodyColor)
    val built = remember(model.content, model.node, targetNode, emAnnotator, style, bodyColor, quoteColor) {
        buildAnnotatedString {
            pushStyle(resolvedStyle.toSpanStyle())
            buildMarkdownAnnotatedString(model.content, targetNode, mdSettings)
            pop()
        }
    }
    val styled = remember(built, quoteColor, underlineColor, style.fontSize) {
        applyOfficialMarkers(built, quoteColor, underlineColor, baseFontSize = style.fontSize)
    }
    MarkdownText(
        content = styled,
        node = model.node,
        modifier = Modifier.fillMaxWidth(),
        style = resolvedStyle,
        sourceContent = model.content,
    )
}

/** 官方富文本标签清单：命中即需要 WebView 兜底（对齐官方 messageFormatting → DOMPurify 后由浏览器渲染）。 */
private val OFFICIAL_HTML_TAG = Regex(
    "<font\\b|</?span|</?div|<style|<table|<img|<a\\b|</?blockquote|<ul\\b|<ol\\b|<li\\b|<p\\b|<pre\\b|<h[1-6]\\b|<center\\b|<figure\\b|<video\\b|<audio\\b|<button\\b" +
        "|</?section|</?header|</?footer|</?main|</?nav|</?aside|</?article|</?form|<input\\b|<select\\b|<textarea\\b|<label\\b|<details\\b|<summary\\b|<canvas\\b|<svg\\b|<math\\b|<template\\b|<mark\\b|<progress\\b|<meter\\b|<output\\b|<fieldset\\b|<legend\\b|<dialog\\b|<menu\\b|<picture\\b|<source\\b|<track\\b|<map\\b|<area\\b|<iframe\\b|<hgroup\\b|<address\\b|<figcaption\\b|<data\\b|<time\\b|<var\\b|<samp\\b|<kbd\\b|<abbr\\b|<bdi\\b|<bdo\\b|<ruby\\b|<rt\\b|<rp\\b" +
        // DOMPurify 默认白名单里文本级标签已由 preprocessOfficialHtml 原生转换（sub/sup/ins/small/big/
        // mark/kbd/samp/tt/code/var/dfn/cite/abbr/acronym）；这里保留它们作为转换失败时的 Web 兜底。
        "|<sub\\b|<sup\\b|<ins\\b|<small\\b|<big\\b|<tt\\b|<acronym\\b|<dfn\\b|<cite\\b|<code\\b" +
        // 布局/交互/媒体/完整网页标签：官方 DOMPurify 白名单放行，浏览器原生渲染，WebView 兜底。
        "|<script\\b|</?html|<head\\b|<body\\b|<title\\b|<meta\\b|<link\\b" +
        "|<caption\\b|<col\\b|<colgroup\\b|<tbody\\b|<thead\\b|<tfoot\\b|<tr\\b|<td\\b|<th\\b" +
        "|<dl\\b|<dt\\b|<dd\\b|<datalist\\b|<optgroup\\b|<option\\b" +
        "|<marquee\\b|<blink\\b|<nobr\\b|<xmp\\b|<shadow\\b|<menuitem\\b|<slot\\b",
    RegexOption.IGNORE_CASE,
)

/** 分段渲染的段类型：原生 Markdown / WebView HTML / 交互卡片 / Mermaid。 */
private enum class SegmentKind { Native, WebHtml, Interactive, Mermaid }

private class ChatSegment(val kind: SegmentKind, val raw: String, val display: String? = null)

private val ANY_FENCE = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
private val INTERACTIVE_FENCE = Regex("```[a-zA-Z]*\\n([\\s\\S]*?)```")
private val MERMAID_FENCE = Regex("```\\s*mermaid\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

/** 交互卡片判定：与 embedInteractiveBlocks 同一套正则，避免分段器与 iframe 转换器不一致。 */
private fun isInteractiveFence(raw: String): Boolean {
    if (!raw.startsWith("```")) return false
    val m = INTERACTIVE_FENCE.find(raw) ?: return false
    val inner = m.groupValues[1].trim()
    return (inner.startsWith("<") && inner.endsWith(">")) || inner.contains("<body", ignoreCase = true)
}

private fun appendTextSegment(
    out: MutableList<ChatSegment>,
    text: String,
    isSystem: Boolean,
    htmlEnabled: Boolean,
) {
    if (text.isBlank()) return
    val pre = preprocessOfficialHtml(text, convertQuotes = !isSystem)
    val officialHtml = OFFICIAL_HTML_TAG.containsMatchIn(pre)
    val looksHtml = looksLikeHtml(pre)
    if ((officialHtml || looksHtml) && htmlEnabled) {
        out += ChatSegment(SegmentKind.WebHtml, text)
    } else {
        out += ChatSegment(SegmentKind.Native, text, pre)
    }
}

/** 真正需要独立 WebView 的块级/结构标签（含带属性的 div/p；font 仅 face/size 时）。
 *  行内 Web 标签（button/input/span[属性]/font face-size/ruby/bdi/bdo 等）无法与原生文本混排，
 *  仍按 12.6 登记整段走 Web；a/img 已原生转换，不进此清单。 */
private val WEB_BLOCK_TAG = Regex(
    "<(table|ul|ol|li|blockquote|pre|h[1-6]|center|figure|figcaption|address|hgroup|section|header|footer|main|nav|aside|article|details|summary|dialog|menu|dl|dt|dd|form|fieldset|legend|style|script|template|marquee|blink|nobr|xmp|picture|video|audio|canvas|svg|math|iframe)\\b" +
        "|<(div|p)\\b(?=[^>]*\\s(?:class|style|align|id|data-[\\w-]+|title|dir|lang)=)" +
        "|<font\\b(?=[^>]*\\s(?:face|size)=)",
    RegexOption.IGNORE_CASE,
)

/** 在围栏外切出“真正需要 WebView”的元素区间：从开标签到同名闭标签（同层嵌套计数，自闭合除外）。
 *  无闭标签（残缺 HTML）延伸到文本末尾；跨围栏的残缺元素按当前片段处理，见 12.6。 */
private fun carveWebElementRanges(text: String): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var i = 0
    val fence = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
    while (i < text.length) {
        val f = fence.find(text, i)
        val t = WEB_BLOCK_TAG.find(text, i)
        val fi = f?.range?.first ?: Int.MAX_VALUE
        val ti = t?.range?.first ?: Int.MAX_VALUE
        if (fi == Int.MAX_VALUE && ti == Int.MAX_VALUE) break
        if (fi <= ti) {
            i = f!!.range.last + 1
            continue
        }
        val name = t!!.groupValues[1].ifEmpty { t.groupValues[2].ifEmpty { t.groupValues[3] } }
        if (t.value.trimEnd().endsWith("/>")) {
            out += t.range.first until t.range.last + 1
            i = t.range.last + 1
            continue
        }
        var depth = 1
        var j = t.range.last + 1
        val openRe = Regex("<${Regex.escape(name)}\\b(?![^>]*/\\s*>)[^>]*>", RegexOption.IGNORE_CASE)
        val closeRe = Regex("</${Regex.escape(name)}\\s*>", RegexOption.IGNORE_CASE)
        while (depth > 0 && j < text.length) {
            val o = openRe.find(text, j)
            val c = closeRe.find(text, j)
            val oi = o?.range?.first ?: Int.MAX_VALUE
            val ci = c?.range?.first ?: Int.MAX_VALUE
            if (ci == Int.MAX_VALUE) break
            if (oi < ci) {
                depth++
                j = o!!.range.last + 1
            } else {
                depth--
                j = c!!.range.last + 1
            }
        }
        val end = if (depth == 0) j else text.length
        out += t.range.first until end
        i = end
    }
    return out
}

/** 把一条消息切成段：先切出真正需要 WebView 的块级 HTML 元素（周围文字保持原生 Markdown），
 *  再对非 Web 部分按围栏切分（交互卡/Mermaid/普通代码块），最后按官方富标签兜底。
 *  修复“文字+卡片混排时整条进 WebView，围栏外 **粗体** 等 Markdown 语法失效”。 */
private fun buildMessageSegments(
    content: String,
    isSystem: Boolean,
    htmlEnabled: Boolean,
    interactiveCardsOn: Boolean,
): List<ChatSegment> {
    val out = mutableListOf<ChatSegment>()
    fun appendFenced(text: String) {
        var last = 0
        for (m in ANY_FENCE.findAll(text)) {
            appendTextSegment(out, text.substring(last, m.range.first), isSystem, htmlEnabled)
            val raw = m.value
            when {
                interactiveCardsOn && isInteractiveFence(raw) -> out += ChatSegment(SegmentKind.Interactive, raw)
                MERMAID_FENCE.containsMatchIn(raw) -> out += ChatSegment(SegmentKind.Mermaid, raw)
                else -> out += ChatSegment(SegmentKind.Native, raw, preprocessOfficialHtml(raw, convertQuotes = !isSystem))
            }
            last = m.range.last + 1
        }
        appendTextSegment(out, text.substring(last), isSystem, htmlEnabled)
    }
    var cursor = 0
    for (r in carveWebElementRanges(content)) {
        if (htmlEnabled) {
            appendFenced(content.substring(cursor, r.first))
            out += ChatSegment(SegmentKind.WebHtml, content.substring(r.first, r.last + 1))
        } else {
            appendFenced(content.substring(cursor, r.last + 1))
        }
        cursor = r.last + 1
    }
    appendFenced(content.substring(cursor))
    return out
}

/** 换行版高亮代码块（官方 style.css 代码块 pre-wrap）：长行自动换行，不再横向滚动截断。 */
@Composable
private fun WrappingHighlightedCode(
    code: String,
    language: String?,
    style: androidx.compose.ui.text.TextStyle,
) {
    val dark = isSystemInDarkTheme()
    val builder = remember(dark) {
        dev.snipme.highlights.Highlights.Builder().theme(dev.snipme.highlights.model.SyntaxThemes.default(darkMode = dark))
    }
    val highlighted = remember(code, language, builder) {
        val syntaxLanguage = language?.let { dev.snipme.highlights.model.SyntaxLanguage.getByName(it) }
        val codeHighlights = builder.code(code)
            .let { if (syntaxLanguage != null) it.language(syntaxLanguage) else it }
            .build()
            .getHighlights()
        buildAnnotatedString {
            append(code)
            codeHighlights.forEach {
                when (it) {
                    is dev.snipme.highlights.model.ColorHighlight -> addStyle(
                        SpanStyle(color = Color(it.rgb).copy(alpha = 1f)),
                        it.location.start,
                        it.location.end,
                    )
                    is dev.snipme.highlights.model.BoldHighlight -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        it.location.start,
                        it.location.end,
                    )
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)),
    ) {
        Text(
            text = highlighted,
            style = style,
            softWrap = true,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        )
    }
}

/** 原生 Markdown 渲染：官方行内字段已由 preprocessOfficialHtml 转成原生标记。 */
@Composable
private fun NativeMarkdown(
    content: String,
    onSurface: Color,
    isSystem: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 官方字段：用户设置 > 当前主题默认（酒馆官方=官方真值） > 跟随 M3 自动生成
    val stTheme = LocalThemePreset.current
    val stDark = isDarkThemeSurface()
    val bodyColor = parseHexColor(AppearancePrefs.stBodyColor(context))
        ?: (if (stDark) stTheme.stBody else null)
        ?: onSurface
    val quoteColor = parseHexColor(AppearancePrefs.stQuoteColor(context))
        ?: (if (stDark) stTheme.stQuote else null)
        ?: MaterialTheme.colorScheme.primary
    val emColor = parseHexColor(AppearancePrefs.stEmColor(context))
        ?: (if (stDark) stTheme.stEm else null)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val underlineColor = parseHexColor(AppearancePrefs.stUnderlineColor(context))
        ?: (if (stDark) stTheme.stUnderline else null)
        ?: MaterialTheme.colorScheme.primary
    val type = chatTypography()
    // 颜色/排版/间距工厂是 @Composable（读主题），直接在组合上下文调用；组件 lambda 走 remember 复用
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val divider = MaterialTheme.colorScheme.outlineVariant
    // markdownColor 是 @Composable 工厂（读主题），只能在组合上下文调用，不能进 remember
    val colors = markdownColor(
        text = bodyColor,
        codeBackground = codeBg.copy(alpha = 0.55f),
        inlineCodeBackground = codeBg.copy(alpha = 0.45f),
        dividerColor = divider,
        tableBackground = codeBg.copy(alpha = 0.25f),
    )
    val typography = markdownTypography(
        h1 = type.h1,
        h2 = type.h2,
        h3 = type.h3,
        h4 = type.h4,
        h5 = type.h5,
        h6 = type.h6,
        text = type.body,
        paragraph = type.body,
        ordered = type.body,
        bullet = type.body,
        list = type.body,
        quote = type.body.copy(
            color = quoteColor,
            fontStyle = if (type.quoteItalic) {
                androidx.compose.ui.text.font.FontStyle.Italic
            } else {
                androidx.compose.ui.text.font.FontStyle.Normal
            },
        ),
        textLink = androidx.compose.ui.text.TextLinkStyles(
            style = androidx.compose.ui.text.SpanStyle(color = quoteColor),
        ),
        code = type.body.copy(
            fontSize = (type.body.fontSize.value * type.codeMult).sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        ),
        inlineCode = type.body.copy(
            fontSize = (type.body.fontSize.value * type.inlineCodeMult).sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        ),
    )
    val components = remember(bodyColor, emColor, quoteColor, underlineColor, type) {
        markdownComponents(
            // 默认 MarkdownParagraph / MarkdownHeader 会直接调 MarkdownText、绕过自定义 text 组件，
            // 导致 \uE001-\uE007 占位符残留（引号旁两个方框）且不上色；所以 text/paragraph/heading 全走同一管线
            text = { model -> OfficialMarkdownNode(model, model.typography.text, bodyColor, emColor, quoteColor, underlineColor) },
            paragraph = { model -> OfficialMarkdownNode(model, model.typography.paragraph, bodyColor, emColor, quoteColor, underlineColor) },
            heading1 = { model -> OfficialMarkdownNode(model, model.typography.h1, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            heading2 = { model -> OfficialMarkdownNode(model, model.typography.h2, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            heading3 = { model -> OfficialMarkdownNode(model, model.typography.h3, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            heading4 = { model -> OfficialMarkdownNode(model, model.typography.h4, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            heading5 = { model -> OfficialMarkdownNode(model, model.typography.h5, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            heading6 = { model -> OfficialMarkdownNode(model, model.typography.h6, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.ATX_CONTENT) },
            setextHeading1 = { model -> OfficialMarkdownNode(model, model.typography.h1, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.SETEXT_CONTENT) },
            setextHeading2 = { model -> OfficialMarkdownNode(model, model.typography.h2, bodyColor, emColor, quoteColor, underlineColor, MarkdownTokenTypes.SETEXT_CONTENT) },
            // 官方 style.css 代码块 pre-wrap 语义：换行版高亮代码块，避免 mikepenz 默认
            // horizontalScroll 让长行（如 JSON 状态栏）被“框住、看不全”
            codeBlock = { model ->
                MarkdownCodeBlock(content = model.content, node = model.node, style = model.typography.code) { code, language, style ->
                    WrappingHighlightedCode(code = code, language = language, style = style)
                }
            },
            codeFence = { model ->
                MarkdownCodeFence(content = model.content, node = model.node, style = model.typography.code) { code, language, style ->
                    WrappingHighlightedCode(code = code, language = language, style = style)
                }
            },
            blockQuote = { model ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    MarkdownBlockQuote(model.content, model.node, style = model.typography.quote)
                }
            },
            checkbox = { model ->
                MarkdownCheckBox(
                    content = model.content,
                    node = model.node,
                    style = type.body,
                    checkedIndicator = { checked, modifier ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (checked) quoteColor else Color.Transparent)
                                .border(1.dp, quoteColor, RoundedCornerShape(4.dp)),
                        ) {
                            if (checked) {
                                Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    },
                )
            },
        )
    }
    val blockSpacing = AppearancePrefs.blockSpacing(context)
    val listIndent = AppearancePrefs.listIndent(context)
    // markdownPadding 是 @Composable 工厂（读 LocalMarkdownPadding），只能在组合上下文调用
    val padding = markdownPadding(
            block = when (blockSpacing) {
                "compact" -> 2.dp
                "loose" -> 5.dp
                else -> 3.dp
            },
            list = 2.dp,
            listItemTop = 2.dp,
            listItemBottom = 2.dp,
            listIndent = listIndent.toFloatOrNull()?.dp ?: 10.dp,
            codeBlock = PaddingValues(10.dp),
            blockQuote = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        )
    val mdAnnotator = remember {
        markdownAnnotator(config = markdownAnnotatorConfig(eolAsNewLine = true))
    }
    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        imageTransformer = Coil3ImageTransformerImpl,
        components = components,
        colors = colors,
        typography = typography,
        padding = padding,
        annotator = mdAnnotator,
    )
}

/** 分段渲染：围栏外 Markdown 走原生，交互卡/Mermaid/富 HTML 段各自进独立 WebView。 */
@Composable
private fun SegmentedMarkdown(
    segments: List<ChatSegment>,
    modifier: Modifier = Modifier,
    onSurface: Color,
    isSystem: Boolean = false,
    charAvatarPath: String? = null,
    userAvatarPath: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg.kind) {
                SegmentKind.Native -> NativeMarkdown(
                    content = seg.display ?: seg.raw,
                    onSurface = onSurface,
                    isSystem = isSystem,
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentKind.WebHtml -> WebViewHtml(
                    html = sanitizeHtmlForWebView(seg.raw),
                    modifier = Modifier.fillMaxWidth(),
                    charAvatarPath = charAvatarPath,
                    userAvatarPath = userAvatarPath,
                )
                SegmentKind.Interactive -> WebViewHtml(
                    html = sanitizeHtmlForWebView(seg.raw),
                    modifier = Modifier.fillMaxWidth(),
                    charAvatarPath = charAvatarPath,
                    userAvatarPath = userAvatarPath,
                )
                SegmentKind.Mermaid -> WebViewHtml(
                    html = mermaidHtmlOf(seg.raw) ?: sanitizeHtmlForWebView(seg.raw),
                    modifier = Modifier.fillMaxWidth(),
                    charAvatarPath = charAvatarPath,
                    userAvatarPath = userAvatarPath,
                )
            }
        }
    }
}

/** 聊天里的 Markdown：官方行内字段原生渲染；富 HTML / 交互卡片 / Mermaid 分段进 WebView 兜底（README 高级渲染）。 */
@Composable
private fun ChatMarkdown(
    content: String,
    onSurface: Color,
    isSystem: Boolean = false,
    charAvatarPath: String? = null,
    userAvatarPath: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val htmlEnabled = RenderPrefs.htmlEnabled(context)
    val interactiveCardsOn = ExtensionPrefs.interactiveCards(context)
    val segments = remember(content, isSystem, htmlEnabled, interactiveCardsOn) {
        buildMessageSegments(content, isSystem, htmlEnabled, interactiveCardsOn)
    }
    // 全原生段（纯 Markdown/普通代码块/官方行内字段）仍按整条一次渲染，保持原有排版；
    // 只有出现 WebView 段（富 HTML/交互卡/Mermaid）才分段，避免拆散列表/引用等跨段 Markdown 结构
    if (segments.none { it.kind != SegmentKind.Native }) {
        val displayContent = remember(content, isSystem) {
            preprocessOfficialHtml(content, convertQuotes = !isSystem)
        }
        NativeMarkdown(
            content = displayContent,
            onSurface = onSurface,
            isSystem = isSystem,
            modifier = modifier,
        )
    } else {
        SegmentedMarkdown(
            segments = segments,
            modifier = modifier,
            onSurface = onSurface,
            isSystem = isSystem,
            charAvatarPath = charAvatarPath,
            userAvatarPath = userAvatarPath,
        )
    }
}

/** 提取 ```mermaid 代码块并返回 HTML 片段，由 officialStyledHtml 统一包成完整页面。
 *  不要在这里返回 <!DOCTYPE html>：再被 officialStyledHtml 包裹会变成 html 套 html，
 *  导致 mermaid 脚本/样式落进错误 DOM 而不渲染。 */
private fun mermaidHtmlOf(content: String): String? {
    val m = Regex("```\\s*mermaid\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(content)
        ?: return null
    val diagram = m.groupValues[1].trim().replace("</", "&lt;/")
    return """<script src="mermaid.min.js"></script>
<pre class="mermaid">$diagram</pre>
<script>mermaid.initialize({startOnLoad:true,theme:'base'});</script>"""
}

/** 粗略 HTML 判定：围栏外存在带属性或自闭合的标签才算富 HTML（忽略 markdown 代码围栏内的）。
 *  裸标签（<b>/<i>/<q>/<u>/<s>/<font color> 等）已被 preprocessOfficialHtml 原生转换，
 *  官方富标签由 OFFICIAL_HTML_TAG 接管；这里只兜底自定义/带属性标签，避免 <tag> 出现在
 *  普通文字里（如 JSON 示例、a<b> 比较）把整条消息误送进 WebView 变成空白。 */
private fun looksLikeHtml(content: String): Boolean {
    val outsideFence = content.replace(ANY_FENCE, "")
    return Regex("<[a-zA-Z][^>]*(?:=|/>)").containsMatchIn(outsideFence)
}

/** 简易消毒（第 178 轮全放开）：消息里的脚本/事件/iframe 原样放行（用户要求活动页/交互页面能跑）；
 *  只拦 javascript: URL，避免点击链接时在卡片内执行脚本导航。安全风险见 HANDOFF 第 178 轮登记。 */
private fun sanitizeHtmlForWebView(html: String): String =
    html.replace(Regex("javascript:", RegexOption.IGNORE_CASE), "blocked:")

/** 注入到兜底 WebView 页面的测高脚本：ResizeObserver 事件驱动 + 图片未就绪时 1s 低速兜底。
 *  高度经 addJavascriptInterface 的 EmberInnBridge 直接回调 Kotlin；
 *  onPageFinished 轮询作为页面脚本/桥接失效时的第二道兜底。 */
private val WEBVIEW_MEASURE_SCRIPT = """<script>
(function(){
  var last='';
  function report(){
    var imgs=document.images,p=0;
    for(var i=0;i<imgs.length;i++){if(!imgs[i].complete)p++;}
    var h=Math.max(document.body.scrollHeight,document.documentElement.scrollHeight);
    var sig=h+':'+p;
    if(sig!==last){last=sig;if(window.EmberInnBridge){window.EmberInnBridge.onMeasure(h,p);}}
    return p===0;
  }
  if(window.ResizeObserver){new ResizeObserver(report).observe(document.documentElement);}
  window.addEventListener('load',report);
  var t=setInterval(function(){if(report())clearInterval(t);},1000);
  setTimeout(function(){clearInterval(t);report();},15000);
})();
</script>"""

/** onPageFinished 兜底轮询用的纯字符串高度表达式：返回 "高度:未加载图片数"。 */
private val WEBVIEW_HEIGHT_JS =
    "(function(){var imgs=document.images,p=0;for(var i=0;i<imgs.length;i++){if(!imgs[i].complete)p++;}return Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)+':'+p;})()"

private fun injectMeasureScript(html: String): String {
    val idx = html.lastIndexOf("</body>")
    return if (idx >= 0) {
        html.substring(0, idx) + WEBVIEW_MEASURE_SCRIPT + html.substring(idx)
    } else {
        html + WEBVIEW_MEASURE_SCRIPT
    }
}

/** 一次 WebView 加载会话：token 用于丢弃旧页面回调，html 用于判断是否需要重载。 */
private class WebViewSession {
    var token: Any = Any()
    var html: String? = null
}

/** JS → Kotlin 测高桥：只回传高度/未加载图片数，不暴露任何其它能力。 */
private class WebViewMeasureBridge(private val onMeasure: (Int, Int) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onMeasure(height: Int, pending: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { onMeasure(height, pending) }
    }
}

private fun configureWebView(
    view: WebView,
    ctx: android.content.Context,
    session: WebViewSession,
    page: String,
    onMeasure: (Int, Int) -> Unit,
) {
    val token = session.token
    view.tag = session
    view.setBackgroundColor(0x00000000)
    view.settings.javaScriptEnabled = true
    view.settings.domStorageEnabled = true
    view.settings.allowFileAccess = true
    // 本地 HTML 需要加载 file:// 字体/图片（WebView 默认禁止 file→file 跨源）；
    // 消息内容本身是本地拼接页，交互 JS 已按用户要求全开，保持同等级放行
    view.settings.allowFileAccessFromFileURLs = true
    view.settings.allowUniversalAccessFromFileURLs = true
    view.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    view.removeJavascriptInterface("EmberInnBridge")
    view.addJavascriptInterface(WebViewMeasureBridge(onMeasure), "EmberInnBridge")
    view.webViewClient = object : android.webkit.WebViewClient() {
        // 放开网络与链接（用户要求全部放开，不加开关）：远程图片/资源正常加载；
        // http(s) 链接交给系统浏览器打开，不在 WebView 内跳走
        override fun shouldOverrideUrlLoading(
            view: android.webkit.WebView?,
            request: android.webkit.WebResourceRequest?,
        ): Boolean {
            val url = request?.url?.toString().orEmpty()
            if (url.startsWith("https://") || url.startsWith("http://")) {
                try {
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) {
                }
                return true
            }
            return false
        }

        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            super.onPageFinished(view, url)
            // 页面脚本正常时 ResizeObserver 已上报；这里再轮询兜底。
            // 轮询不能省：Compose 初始给 WebView 的高度是 0 时页面可能根本不做内容布局，
            // 只有先给一个可见高度、再读 scrollHeight，WebView 才会真正“长出来”。
            var stable = 0
            var lastPx = 0
            var ticks = 0
            fun measure() {
                if (view?.tag !== session || session.token !== token) return
                ticks++
                if (ticks > 60) return
                view?.evaluateJavascript(WEBVIEW_HEIGHT_JS) { value ->
                    if (view?.tag === session && session.token === token) {
                        val parts = value.trim('"').split(':')
                        val px = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val pending = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        if (px > 0) {
                            if (px == lastPx && pending == 0) stable++ else stable = 0
                            lastPx = px
                            onMeasure(px, pending)
                        }
                        if (stable < 3) view?.postDelayed({ measure() }, 250)
                    }
                }
            }
            measure()
        }
    }
    view.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    session.html = page
    // Android WebView 对非 base64 的 HTML 会按 URL 解析：#/% 等字符把内容截断成空白页
    // （Android 11+ 已知问题，官方与社区一致解法是 base64 编码加载）。
    // 保留 file:///android_asset/ 作 baseUrl，mermaid.min.js 与 file:// 字体仍可正常解析。
    val encoded = android.util.Base64.encodeToString(page.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    view.loadDataWithBaseURL("file:///android_asset/", encoded, "text/html", "base64", null)
}

/** WebView 兜底渲染（HTML 消息 / Mermaid / 交互卡片）。第 178 轮按用户要求 JS 全开（活动页/交互页面能跑；
 *  官方 DOMPurify 禁脚本，此为已知偏差）；网络与外链已放开，http(s) 链接用系统浏览器打开。
 *  自动测高：WRAP_CONTENT 的 WebView 在 Compose 里会塌成 0 高（之前的 HTML 显示不出来的根因）。
 *  实例来自 WebViewPool 复用，避免 LazyColumn 滚动时反复创建 WebView 导致卡顿。 */
@Composable
private fun WebViewHtml(
    html: String,
    modifier: Modifier = Modifier,
    charAvatarPath: String? = null,
    userAvatarPath: String? = null,
) {
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    var heightPx by remember { mutableIntStateOf(0) }
    val stTheme = LocalThemePreset.current
    val stDark = isDarkThemeSurface()
    val body = parseHexColor(AppearancePrefs.stBodyColor(context)) ?: (if (stDark) stTheme.stBody else null)
    val em = parseHexColor(AppearancePrefs.stEmColor(context)) ?: (if (stDark) stTheme.stEm else null)
    val underline = parseHexColor(AppearancePrefs.stUnderlineColor(context)) ?: (if (stDark) stTheme.stUnderline else null)
    val quote = parseHexColor(AppearancePrefs.stQuoteColor(context)) ?: (if (stDark) stTheme.stQuote else null)
    val styled = remember(html, body, em, underline, quote, charAvatarPath, userAvatarPath) {
        injectMeasureScript(
            officialStyledHtml(html, context, body, em, underline, quote, charAvatarPath, userAvatarPath),
        )
    }
    val webView = remember { WebViewPool.acquire(context) }
    fun onMeasure(px: Int, pending: Int) {
        // pending > 0 表示还有图片在加载：只允许继续长高，不允许提前缩矮；
        // pending == 0 时允许回缩，修复折叠/切 tab 后高度卡在旧值的问题。
        if (px > 0 && (pending == 0 || px > heightPx)) heightPx = px
    }
    AndroidView(
        factory = { ctx ->
            val session = WebViewSession()
            configureWebView(
                view = webView,
                ctx = ctx,
                session = session,
                page = styled,
                onMeasure = { px, pending -> onMeasure(px, pending) },
            )
            webView
        },
        update = { view ->
            val session = view.tag as? WebViewSession
            if (session?.html != styled) {
                session?.let {
                    it.token = Any()
                    heightPx = 0
                    configureWebView(
                        view = view,
                        ctx = context,
                        session = it,
                        page = styled,
                        onMeasure = { px, pending -> onMeasure(px, pending) },
                    )
                }
            }
        },
        onRelease = { view -> WebViewPool.release(view) },
        modifier = modifier
            .fillMaxWidth()
            .height(
                run {
                    val maxHeight = (screenHeightDp * 0.75f).dp
                    // WebView 的 scrollHeight 是 CSS 像素，1 CSS px == 1 dp，不能按 Android 物理像素换算；
                    // 旧代码 heightPx.toDp() 在高密度屏上会把高度除以 density，HTML 卡被压成几乎看不见的细条。
                    val measured = heightPx.toFloat().dp.coerceAtMost(maxHeight)
                    // 测高还没回来时给一个可见兜底高度，否则 WebView 高度恒 0、内容永远渲染不出来
                    if (heightPx > 0) measured else minOf(160.dp, maxHeight)
                }
            )
            .clip(RoundedCornerShape(12.dp)),
    )
}

/** 交互代码块渲染器（对齐 Tavern Helper 渲染器 / ST HTML 代码注入器机制）：
 *  消息里 ``` 包裹、内容以 < 开头以 > 结尾（或含 <body>）的代码块 → 替换成独立 iframe 网页，
 *  卡内 <script>/onclick/框架 JS 在 iframe 里正常运行（按钮/状态栏/表单可交互）；onload 自动按内容测高。
 *  非交互代码块保留为 <pre><code>；围栏外的纯文本转义后按 pre-wrap 显示（保留换行）。
 *  安全提示：交互代码块等同于执行任意脚本，与第 178 轮 JS 全开同等级，已在 HANDOFF 登记。 */
private fun embedInteractiveBlocks(raw: String): String {
    val fence = Regex("```[a-zA-Z]*\\n([\\s\\S]*?)```")
    val out = StringBuilder()
    var last = 0
    for (m in fence.findAll(raw)) {
        out.append(embedPlainText(raw.substring(last, m.range.first)))
        val inner = m.groupValues[1].trim()
        if ((inner.startsWith("<") && inner.endsWith(">")) || inner.contains("<body", ignoreCase = true)) {
            val escaped = inner
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            out.append("<details style=\"margin-bottom:4px\"><summary>原代码</summary><pre style=\"white-space:pre-wrap;word-break:break-word\"><code>")
                .append(inner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("</code></pre></details>")
            out.append(
                "<iframe srcdoc=\"$escaped\" style=\"width:100%;border:0;display:block\" " +
                    "onload=\"var f=this;function h(){f.style.height=(f.contentWindow.document.documentElement.scrollHeight+5)+'px'};h();setTimeout(h,150);setTimeout(h,500);setTimeout(h,1500);setTimeout(h,3000);var d=f.contentWindow.document;if(d&&d.documentElement){if(window.ResizeObserver){new ResizeObserver(h).observe(d.documentElement);}if(window.MutationObserver){new MutationObserver(h).observe(d.documentElement,{subtree:true,childList:true,attributes:true,characterData:true});}}\"></iframe>",
            )
        } else {
            out.append("<pre style=\"white-space:pre-wrap;word-break:break-word\"><code>")
                .append(inner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("</code></pre>")
        }
        last = m.range.last + 1
    }
    out.append(embedPlainText(raw.substring(last)))
    return out.toString()
}

/** 围栏外纯文本：转义后按 pre-wrap 显示；若本身是 HTML（含 <）则原样放行。 */
private fun embedPlainText(segment: String): String {
    if (segment.isBlank() || segment.contains('<')) return segment
    return "<div style=\"white-space:pre-wrap;word-break:break-word\">" +
        segment.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") +
        "</div>"
}

/** 把官方字段（正文/次要/下划线/引用/代码）注入 HTML 兜底渲染的 CSS。 */
private fun officialStyledHtml(
    raw: String,
    context: android.content.Context,
    body: androidx.compose.ui.graphics.Color?,
    em: androidx.compose.ui.graphics.Color?,
    underline: androidx.compose.ui.graphics.Color?,
    quote: androidx.compose.ui.graphics.Color?,
    charAvatarPath: String? = null,
    userAvatarPath: String? = null,
): String {
    val fontSize = when (AppearancePrefs.textSize(context)) {
        "small" -> "14px"
        "official" -> "15px"
        "large" -> "18px"
        "xlarge" -> "20px"
        else -> "16px"
    }
    fun css(c: androidx.compose.ui.graphics.Color?): String = c?.let {
        "#%02X%02X%02X".format((it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
    } ?: "inherit"
    // 官方 --mainFontFamily Noto Sans：下载的同一批 TTF（4 面）供 WebView 兜底使用
    val noto = FontManager.notoFiles(context)
    val notoFace = if (noto.size == 4) buildString {
        append("@font-face{font-family:'Noto Sans';src:url('file://${noto[0]}') format('truetype');font-weight:400;font-style:normal}\n")
        append("@font-face{font-family:'Noto Sans';src:url('file://${noto[1]}') format('truetype');font-weight:700;font-style:normal}\n")
        append("@font-face{font-family:'Noto Sans';src:url('file://${noto[2]}') format('truetype');font-weight:400;font-style:italic}\n")
        append("@font-face{font-family:'Noto Sans';src:url('file://${noto[3]}') format('truetype');font-weight:700;font-style:italic}\n")
    } else {
        ""
    }
    // 官方 * { text-shadow: 0 0 2px var(--SmartThemeShadowColor) }，颜色跟随消息渲染页“阴影色”设置
    val textShadowCss = if (AppearancePrefs.textShadowEnabled(context)) {
        val blur = AppearancePrefs.textShadowStrength(context)
        if (blur > 0) {
            val shadowColor = parseHexColor(AppearancePrefs.stShadowColor(context)) ?: Color(0x80000000)
            val shadowRgba = "rgba(${(shadowColor.red * 255).toInt()},${(shadowColor.green * 255).toInt()},${(shadowColor.blue * 255).toInt()},${shadowColor.alpha})"
            "text-shadow:0 0 ${blur}px $shadowRgba;"
        } else {
            ""
        }
    } else {
        ""
    }
    val bodyHtml = embedInteractiveBlocks(raw)
        .replace("{{charAvatarPath}}", charAvatarPath ?: "")
        .replace("{{userAvatarPath}}", userAvatarPath ?: "")
    fun avatarUrl(path: String?): String? = path?.let {
        if (it.startsWith("file://") || it.startsWith("content://")) it else "file://$it"
    }
    val charUrl = avatarUrl(charAvatarPath)
    val userUrl = avatarUrl(userAvatarPath)
    val avatarCss = buildString {
        charUrl?.let { append(".char-avatar,.char_avatar{background-image:url('$it')}\n") }
        userUrl?.let { append(".user-avatar,.user_avatar{background-image:url('$it')}\n") }
    }
    return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
$notoFace
$avatarCss
body{font-family:'Noto Sans',sans-serif;${textShadowCss}color:${css(body)};font-size:$fontSize;line-height:1.55;margin:0;word-break:break-word;background:transparent}
em,i{color:${css(em)}}
q{color:${css(quote)}} q em,q i{color:inherit}
u{color:${css(underline)}}
a{color:${css(quote)};text-decoration:none}
a:hover{filter:brightness(1.25)}
img{max-width:100%;max-height:75vh}
font[color] em,font[color] i,font[color] u,font[color] q{color:inherit}
blockquote{border-left:3px solid ${css(quote)};padding-left:10px;background:rgba(0,0,0,.3);margin:0}
p{margin-top:0;margin-bottom:10px}
p:last-child{margin-bottom:0}
table{border-spacing:0;border-collapse:collapse;margin-bottom:10px}
td,th{border:1px solid;padding:.25em}
ol,ul{margin-top:5px;margin-bottom:5px}
li tt{display:inline-block}
pre,pre code{white-space:pre-wrap;word-break:break-word}
strong em,strong,h1,h2{font-weight:bold}
code{font-family:monospace}
</style></head><body>$bodyHtml</body></html>"""
}


@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    // 幽灵按钮：低对比、小尺寸，功能保留但不抢戏
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
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
    accent: Color,
    input: String,
    onInputChange: (String) -> Unit,
    pendingMedia: List<MediaAttachment>,
    pendingDisplay: String?,
    onDisplayChange: (String?) -> Unit,
    onRemoveMedia: (Int) -> Unit,
    isStreaming: Boolean,
    canQuickContinue: Boolean,
    quickBarOpen: Boolean,
    worldHitsCount: Int,
    contextUsage: Pair<Int, Int>?,
    onOpenWorldPanel: () -> Unit,
    onOpenContextDetail: () -> Unit,
    onToggleQuickBar: () -> Unit,
    quickReplies: List<QuickReplySlot>,
    onQuickReply: (String) -> Unit,
    onQuickImage: () -> Unit,
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
            // README 状态可见：上下文占比 + 世界书命中常驻输入栏顶部（不占消息区）
            if (!isStreaming && (contextUsage != null || worldHitsCount > 0)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                ) {
                    contextUsage?.let { (used, max) ->
                        ContextCapsule(used = used, max = max, onClick = onOpenContextDetail)
                    }
                    if (worldHitsCount > 0) {
                        StatusPill("世界书 ×$worldHitsCount", onClick = onOpenWorldPanel)
                    }
                }
            }
            if (pendingMedia.isNotEmpty()) {
                if (pendingMedia.count { it.type == "image" } > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
                    ) {
                        Text("显示：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = pendingDisplay != "gallery",
                            onClick = { onDisplayChange(null) },
                            label = { Text("列表") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = pendingDisplay == "gallery",
                            onClick = { onDisplayChange("gallery") },
                            label = { Text("图库") },
                        )
                    }
                }
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
                val enabledReplies = quickReplies.filter { it.enabled }
                // 快捷工具 + 快捷回复统一成一条横向胶囊流（图像/继续/冒充 + 角色预设），
                // 比原来的“竖排文字按钮”更轻、更整齐
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    item(key = "quick-image") {
                        EmberQuickPill("图像", onClick = onQuickImage, enabled = true)
                    }
                    item(key = "quick-continue") {
                        EmberQuickPill("继续", onClick = onQuickContinue, enabled = canQuickContinue)
                    }
                    item(key = "quick-impersonate") {
                        EmberQuickPill("冒充", onClick = onQuickImpersonate, enabled = true)
                    }
                    items(enabledReplies, key = { it.label }) { slot ->
                        EmberQuickPill(slot.label.ifBlank { "（未命名）" }, onClick = { onQuickReply(slot.label) }, enabled = true)
                    }
                }
            }
            EmberInputIcon(
                onClick = onToggleQuickBar,
                icon = PhosphorIcons.Book,
                contentDescription = "快捷工具盘",
            )
            EmberInputIcon(
                onClick = onAttach,
                icon = PhosphorIcons.Plus,
                contentDescription = "附件 / 语音",
            )
            EmberTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "输入消息…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = EmberTextFieldDefaults.colors(
                    cursorColor = accent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.26f),
                ),
                focusGlow = accent,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 160.dp),
            )
            if (!isStreaming) {
                EmberInputIcon(
                    onClick = onVoice,
                    icon = PhosphorIcons.Mic,
                    contentDescription = "语音输入",
                )
                val canSend = input.isNotBlank() || pendingMedia.isNotEmpty()
                ChatSendButton(accent = accent, canSend = canSend, onSend = onSend)
            } else {
                IconButton(onClick = onStop, modifier = Modifier.size(42.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .emberShadow(color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), radius = 10.dp, offset = DpOffset(0.dp, 3.dp), alpha = 0.35f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    ) {
                        Icon(PhosphorIcons.Stop, contentDescription = "停止生成", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(18.dp))
                    }
                }
            }
            }
        }
    }
}

/** 输入区快捷胶囊（快捷工具/快捷回复共用）：999 圆角 tonal 小胶囊，禁用态自动降级。 */
@Composable
private fun EmberQuickPill(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
        },
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** 发送按钮：保留角色 seed 取色（accent 底 + 自适应亮/暗图标），升级为 38dp 圆钮 + accent 柔光。 */
@Composable
private fun ChatSendButton(accent: Color, canSend: Boolean, onSend: () -> Unit) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black.copy(alpha = 0.8f) else Color.White
    IconButton(
        onClick = onSend,
        enabled = canSend,
        modifier = Modifier.size(42.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .then(
                    if (canSend) {
                        Modifier.emberShadow(
                            color = accent.copy(alpha = 0.45f),
                            radius = 10.dp,
                            spread = 1.dp,
                            offset = DpOffset(0.dp, 3.dp),
                            alpha = 0.4f,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .background(
                    if (canSend) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
        ) {
            Icon(
                PhosphorIcons.Send,
                contentDescription = "发送",
                tint = if (canSend) onAccent else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyChat(name: String, accent: Color) {
    EmberEmptyState(
        title = "和 ${name.ifBlank { "TA" }} 打个招呼吧",
        body = "第一条消息会连同角色卡、世界书与示例对话一起发给模型",
        accent = accent,
        icon = PhosphorIcons.Book,
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp, bottom = 24.dp),
    )
}

@Composable
private fun WorldHitLight(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isUser(el: JsonElement): Boolean {
    val v = el.jsonObject["is_user"] ?: return false
    return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
}

private fun textOf(el: JsonElement): String {
    // 官方 script.js：message?.extra?.display_text ?? message.mes（translate 扩展译文优先）
    val extra = el.jsonObject["extra"] as? JsonObject
    val display = extra?.get("display_text")?.jsonPrimitive?.contentOrNull
    if (!display.isNullOrBlank()) return display
    return el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""
}

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

/** 是否系统消息（/hide 隐藏、/comment 注释等；官方 coreChat 过滤 is_system）。 */
private fun isSystem(el: JsonElement): Boolean =
    el.jsonObject["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
