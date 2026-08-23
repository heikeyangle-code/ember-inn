@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.chat

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.emberinn.app.data.DisplayPipeline
import com.emberinn.app.data.ExpressionStore
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.data.Persona
import com.emberinn.app.data.ThemeState
import com.emberinn.app.renderer.ChatDisplayMode
import com.emberinn.app.renderer.KernelHostAction
import com.emberinn.app.renderer.KernelMessagePayload
import com.emberinn.app.renderer.KernelWebViewPool
import com.emberinn.app.renderer.StApiShimInstaller
import com.emberinn.app.ui.chat.surface.MessageKernelRow
import com.emberinn.app.ui.components.EmberBottomSheet
import com.emberinn.app.ui.components.EmberInputIcon
import com.emberinn.app.ui.components.EmberMenuRow as MenuRow
import com.emberinn.app.ui.components.EmberPrimaryButton
import com.emberinn.app.ui.components.EmberSlider
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberTextFieldDefaults
import com.emberinn.app.ui.components.edgeSwipeBack
import com.emberinn.app.ui.components.emberGlass
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.parseHexColor
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmptyState
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.settings.ExpressionPrefs
import com.emberinn.engine.expression.ExpressionApi
import com.emberinn.engine.group.GroupGenerationMode
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.prompt.CfgPromptEngine
import com.emberinn.engine.prompt.LogprobsEngine
import com.emberinn.engine.slash.QuickReplySlot
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/** 一条消息在组合期的派生字段缓存（元素不变即复用，流式 tick 不重复解析）。 */
private data class ChatItemDerived(
    val isUser: Boolean,
    val isSystem: Boolean,
    val text: String,
    /** 本条消息自己的思考（官方 reasoning 扩展：逐条存 extra.reasoning，各自渲染折叠块）。 */
    val reasoning: String?,
    val media: List<MediaAttachment>,
    val mediaDisplay: String?,
    val mediaIndex: Int?,
    val name: String,
    val time: String,
    val swipeCount: Int,
    val curSwipe: Int,
    /** 按消息 extra.force_avatar/original_avatar 解析的头像（官方 sendas avatar= 渲染）。 */
    val avatarPath: String?,
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
    onSwitchSession: (com.emberinn.app.data.SessionRecord) -> Unit = {},
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
    val isStreaming by vm.isStreaming.collectAsState()
    val providerConfigured by vm.providerConfigured.collectAsState()
    val notice by vm.notice.collectAsState()
    val isImpersonating by vm.isImpersonating.collectAsState()
    val impersonated by vm.impersonated.collectAsState()
    val lastReasoning by vm.lastReasoning.collectAsState()
    val showThoughtsNow by vm.showThoughts.collectAsState()
    val pendingMedia by vm.pendingMedia.collectAsState()
    val worldHits by vm.worldHits.collectAsState()
    val contextUsage by vm.contextUsage.collectAsState()
    val promptPreview by vm.promptPreview.collectAsState()
    val quickReplies by vm.quickReplies.collectAsState()
    val quickReplyOutput by vm.quickReplyOutput.collectAsState()
    val captionDraft by vm.captionDraft.collectAsState()
    val imageRefineDraft by vm.imageRefineDraft.collectAsState()
    val captionPromptAsk by vm.captionPromptRequest.collectAsState()
    val inputDraft by vm.inputDraft.collectAsState()
    val chatBackground by vm.chatBackground.collectAsState()
    val personas by vm.personas.collectAsState()
    val activePersona by vm.activePersona.collectAsState()
    val defaultPersona by vm.defaultPersona.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val dataBank by vm.dataBank.collectAsState()
    val displayRevision by vm.displayRevision.collectAsState()
    val generationEpoch by vm.generationEpoch.collectAsState()
    val pastChatsRevision by vm.pastChatsRevision.collectAsState()

    // ---- V2 内核渲染：池 + 官方主题全量同步（单轨，内核为唯一消息渲染管线） ----
    val context = LocalContext.current
    val kernelPool = remember {
        KernelWebViewPool(context).also { it.preload() }
    }
    DisposableEffect(Unit) {
        // 宿主能力白名单（V2 §5.3）：卡片脚本经 AppBridge/toastr 触达系统能力
        val mainHandler = Handler(Looper.getMainLooper())
        val uiAction: (String, String) -> Unit = { action, value ->
            mainHandler.post { handleHostAction(context, action, value) }
        }
        kernelPool.addUiActionListener(uiAction)
        StApiShimInstaller.install(
            kernelPool,
            vm,
            clipboardReader = {
                // shim 线程（Default）→ 主线程读剪贴板（API29+ 聚焦要求）；异常回空串
                runCatching {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                    }
                }.getOrDefault("")
            },
            globalVariables = com.emberinn.app.renderer.GlobalVariableStore(context),
        )
        onDispose {
            kernelPool.removeUiActionListener(uiAction)
            StApiShimInstaller.uninstall(kernelPool)
            kernelPool.destroyAll()
        }
    }
    // 官方事件下发（event_types 触发点位）：VM 状态机事件 → 池广播；进页即发 chat_id_changed
    // （官方 selectChat L1696 同参 getCurrentChatId()；卡脚本在此重置 per-chat 状态）
    LaunchedEffect(kernelPool) {
        vm.kernelEvents.collect { (type, args) -> kernelPool.emitEvent(type, args) }
    }
    LaunchedEffect(kernelPool) {
        kernelPool.emitEvent("chat_id_changed", listOf(kotlinx.serialization.json.JsonPrimitive(vm.currentChatId).toString()))
        // 官方 getChatResult：打开的聊天恰有 1 条消息（开场白）→ MESSAGE_RECEIVED(0,'first_message')（script.js:7646）
        if (vm.messages.value.size == 1) {
            kernelPool.emitEvent("message_received", listOf("0", "\"first_message\""))
        }
    }
    val themeManager = remember { OfficialThemeManager.shared(context) }
    val officialThemeJson by themeManager.currentThemeJson.collectAsState()
    val stylePack by themeManager.currentStylePack.collectAsState()
    // chat_display 布局类随主题派生（0..2 官方 + 3..7 Moonlit 扩展顺延）。
    // 全 DOM 行：头像/名字/操作 chrome 全在内核页，原生侧不再挂 embed-shell。
    LaunchedEffect(officialThemeJson) {
        val shell = themeManager.shellSettings()
        kernelPool.updateTheme(
            officialThemeJson,
            listOfNotNull(ChatDisplayMode.entries.getOrElse(shell.chatDisplay) { ChatDisplayMode.FLAT }.bodyClass),
        )
    }
    // 样式包（第三方整包 CSS + 可选扩展兼容层）：官方主题 enabled=false，内核侧零污染
    LaunchedEffect(stylePack) {
        kernelPool.updateStylePack(stylePack.enabled, stylePack.href, stylePack.varsJson, stylePack.extensionHref)
    }
    // ------------------------------------------------------------------------

    var input by rememberSaveable { mutableStateOf("") }
    // 思考卡展开状态：流式/生成完是同一个卡，点开状态跨阶段保持，不重建
    var reasoningExpanded by rememberSaveable { mutableStateOf(false) }
    // 思考卡默认折叠；每次流式开始强制收起（展开+每 tick 全量渲染是滑动卡顿主因）
    LaunchedEffect(isStreaming) {
        if (isStreaming) reasoningExpanded = false
    }
    // 官方 reasoning 扩展：每条消息的 Thoughts 块各自独立展开/折叠（DOM 级状态、默认折叠、不落盘）。
    // 以 send_date 为身份键，滑动/刷新不串行；流式结束后 finalized 行回到折叠（官方重新渲染重置）。
    val reasoningExpandedMap = remember { mutableStateMapOf<String, Boolean>() }
    var menuMessageIndex by remember { mutableStateOf<MsgTarget?>(null) }
    // 官方 openMessageDelete 删除模式：每条消息出现勾选框，勾选一条 → 从该条截断到末尾（this_del_mes）
    var deleteMode by remember { mutableStateOf(false) }
    var deleteCheckIndex by remember { mutableStateOf<Int?>(null) }
    // 官方 displayPastChats：管理聊天文件弹层（同角色/群全部聊天）
    var showPastChats by remember { mutableStateOf(false) }
    var pastChatsQuery by remember { mutableStateOf("") }
    var renameChatTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    var renameChatDraft by remember { mutableStateOf("") }
    var deleteChatTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var pendingExportTextName by remember { mutableStateOf("") }
    var pendingExportJsonl by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    var contextDetail by remember { mutableStateOf(false) }
    var worldPanel by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showConvertGroupConfirm by remember { mutableStateOf(false) }
    var showLogprobsSheet by remember { mutableStateOf(false) }
    var tokenStatsIndex by remember { mutableStateOf<MsgTarget?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showCfgSheet by remember { mutableStateOf(false) }
    var showPromptPreview by remember { mutableStateOf(false) }
    var showWorldPicker by remember { mutableStateOf(false) }
    var showAttachOptions by remember { mutableStateOf(false) }
    var showUrlAttachmentDialog by remember { mutableStateOf(false) }
    var urlAttachmentDraft by rememberSaveable { mutableStateOf("") }
    var showCharacterInfo by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    var personaDraftName by remember { mutableStateOf("") }
    var personaDraftDesc by remember { mutableStateOf("") }
    var personaDraftPosition by remember { mutableStateOf(0) }
    var personaDraftDepth by remember { mutableStateOf(2) }
    var personaDraftRole by remember { mutableStateOf(0) }
    var personaDraftTitle by remember { mutableStateOf("") }
    var personaDraftLorebook by remember { mutableStateOf("") }
    var personaDraftConnectChar by remember { mutableStateOf(false) }
    var personaDraftConnectGroup by remember { mutableStateOf(false) }
    var personaDraftAvatar by remember { mutableStateOf("") }
    var personaShowLorePicker by remember { mutableStateOf(false) }
    var showSyncNameConfirm by remember { mutableStateOf(false) }
    var personaQuery by remember { mutableStateOf("") }
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
    var anRole by remember { mutableStateOf(0) }
    var anInterval by remember { mutableStateOf(1) }
    var charaNotePrompt by remember { mutableStateOf("") }
    var charaNoteUse by remember { mutableStateOf(false) }
    var charaNotePosition by remember { mutableStateOf(0) }
    val openAuthorsNote = {
        val draft = vm.authorsNoteDraft()
        anPrompt = draft.prompt
        anPosition = draft.position
        anDepth = draft.depth
        anRole = draft.role
        anInterval = draft.interval
        val charaDraft = vm.charaNoteDraft()
        charaNotePrompt = charaDraft?.prompt.orEmpty()
        charaNoteUse = charaDraft?.useChara == true
        charaNotePosition = charaDraft?.position ?: 0
        showAuthorsNote = true
    }
    var showGroupSettings by remember { mutableStateOf(false) }
    var pendingDisplay by remember { mutableStateOf<String?>(null) }
    var groupMode by rememberSaveable { mutableStateOf(vm.group?.generationMode ?: GroupGenerationMode.APPEND) }
    var groupStrategy by rememberSaveable { mutableStateOf(vm.group?.activationStrategy ?: "natural") }
    var imagePrompt by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf<MsgTarget?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteTargetIndex by remember { mutableStateOf<MsgTarget?>(null) }
    var deleteSwipeTargetIndex by remember { mutableStateOf<MsgTarget?>(null) }
    var swipePickerIndex by remember { mutableStateOf<MsgTarget?>(null) }
    val listState = rememberLazyListState()
    // 滚动中（含惯性滑动）临时关掉顶栏/输入栏的实时模糊：cloudy 每帧全屏 RenderEffect
    // 是聊天滚动掉帧的最大 GPU 卡点；停稳后自动恢复，底漆观感与静态模糊几乎一致
    val glassBlurActive by remember {
        derivedStateOf { !listState.isScrollInProgress }
    }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val exportChatLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { u ->
            val pending = pendingExportJsonl
            val text = pending?.let { vm.exportJsonl(it.first) } ?: vm.exportJsonl()
            val exportName = pending?.second ?: name
            pendingExportJsonl = null
            if (text == null) {
                Toast.makeText(context, "这条会话还没有消息，无内容可导出", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    context.contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
                    Toast.makeText(context, "已导出：$exportName.jsonl", Toast.LENGTH_SHORT).show()
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

    // 官方 past chats 行内 fa-file-lines：Download chat as plain text document
    val exportChatTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            val text = pendingExportText
            if (text != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    Toast.makeText(context, "已导出：$pendingExportTextName.txt", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingExportText = null
    }

    var embedTargetIndex by remember { mutableStateOf<MsgTarget?>(null) }
    val embedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = embedTargetIndex
        embedTargetIndex = null
        // 文件选择器期间消息列表可能刷新：按身份重定位（失配放弃，防附件挂到别的消息）
        val idx = target?.resolve(messages)
        if (uri != null && idx != null) vm.addMediaToMessage(idx, uri, null)
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

    val personaAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = editingPersona
        if (uri != null && target != null) {
            val dir = File(context.filesDir, "persona-avatars").apply { mkdirs() }
            val dest = File(dir, target.id + ".png")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                personaDraftAvatar = dest.absolutePath
            }
        }
    }

    val personaBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.backupPersonas(it) } }

    val personaRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.restorePersonas(it) } }

    // 附件与输入工具：官方输入区没有独立“快捷工具盘”，统一由附件面板提供来源 + 图像生成/图片描述
    if (showAttachOptions) {
        EmberBottomSheet(onDismissRequest = { showAttachOptions = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text("添加附件与工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "图片 / 视频 / 音频、URL，或直接生成图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
                AttachSheetRow(FaIcons.Folder, "从文件选择…", "图片 / 视频 / 音频", enabled = true) {
                    showAttachOptions = false
                    mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*"))
                }
                AttachSheetRow(FaIcons.Link, "从 URL 添加…", "粘贴图片 / 媒体链接", enabled = true) {
                    showAttachOptions = false
                    showUrlAttachmentDialog = true
                }
                AttachSheetRow(FaIcons.Image, "AI 图像生成", "用当前模型生成图片", enabled = true) {
                    showAttachOptions = false
                    showImageDialog = true
                }
                AttachSheetRow(
                    FaIcons.WandMagicSparkles,
                    "图片描述",
                    if (pendingMedia.any { it.type == "image" }) "为已选图片生成描述并发送" else "先添加图片后可用",
                    enabled = pendingMedia.any { it.type == "image" },
                ) {
                    showAttachOptions = false
                    vm.startCaptionFlow()
                }
                AttachSheetRow(FaIcons.Microphone, "语音输入", "开发中", enabled = false) {}
            }
        }
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

    // 强调色派生链：角色主题配方 > 官方主题字段推导（ShellTheme：quote_text_color → accent）
    val accent = vm.accentColor?.let { Color(it.toInt()) }
        ?: EmberTheme.colors.accent
    val items = remember(messages, isStreaming, lastReasoning, isImpersonating) {
        buildList {
            // reverseLayout=true：第 0 项固定在视口底部，因此最新内容（流式/最后一条）放在最前。
            // 官方冒充：不建消息、聊天区不显示，结果只进输入框（onProgressStreaming isImpersonate → send_textarea）
            if (isStreaming && !isImpersonating) {
                add(ChatItem.Streaming)
            } else if (lastReasoning != null && !isImpersonating && messages.indexOfLast { el -> !isUser(el) } < 0) {
                // 空正文场景：思考过程独立成卡，不随流式结束消失
                add(ChatItem.ReasoningOnly)
            }
            for (i in messages.indices.reversed()) {
                add(ChatItem.Message(i, messages[i]))
            }
        }
    }
    // 最后一条 AI 消息（排除用户/系统）：reasoning 兜底与生成类入口的目标。
    // 官方 swipe/生成目标是 chat.length-1 且非用户非系统（isMessageSwipeable）。
    val lastAiIndex = messages.indexOfLast { el -> !isUser(el) && !isSystem(el) }

    // 贴底跟随：用户上滑查看历史时暂停跟随，滚回底部自动恢复（微信式）。
    // reverseLayout=true：贴底判定 = firstVisibleItemIndex == 0（官方 LazyColumn 语义，不再读 layoutInfo）。
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
    // 官方 onStartStreaming(script.js:3570)：冒充开始即清空输入框——旧草稿不保留（流式期间显示清洗后的草稿流）
    LaunchedEffect(isImpersonating) {
        if (isImpersonating) input = ""
    }

    // /setinput：官方 setinput 把文本写进输入框（用户可改可发）
    LaunchedEffect(inputDraft) {
        inputDraft?.let {
            input = it
            vm.clearInputDraft()
        }
    }

    // 会话切换重置：MainScreen 的 onSwitchSession 原地换 sessionId（组合复用），vm(key=sessionId)
    // 换新实例，但上面这些 remember/rememberSaveable 本地状态会残留旧会话的草稿/弹层/删除模式/
    // 推理展开表/群聊设置——必须显式清空，否则切会话后旧状态串台（新会话里弹旧会话的重命名框等）。
    LaunchedEffect(sessionId) {
        input = ""
        reasoningExpanded = false
        reasoningExpandedMap.clear()
        menuMessageIndex = null
        editIndex = null
        editDraft = ""
        deleteTargetIndex = null
        deleteSwipeTargetIndex = null
        swipePickerIndex = null
        tokenStatsIndex = null
        deleteMode = false
        deleteCheckIndex = null
        showPastChats = false
        pastChatsQuery = ""
        renameChatTarget = null
        renameChatDraft = ""
        deleteChatTarget = null
        pendingExportText = null
        pendingExportTextName = ""
        pendingExportJsonl = null
        contextDetail = false
        worldPanel = false
        showClearConfirm = false
        showConvertGroupConfirm = false
        showLogprobsSheet = false
        showMore = false
        showCfgSheet = false
        showPromptPreview = false
        showWorldPicker = false
        showAttachOptions = false
        showUrlAttachmentDialog = false
        urlAttachmentDraft = ""
        showCharacterInfo = false
        showPersonaPicker = false
        personaQuery = ""
        editingPersona = null
        showBookmarkDialog = false
        bookmarkDraftName = ""
        showBookmarksSheet = false
        bookmarkToOpen = null
        showImageDialog = false
        showDataBank = false
        showDataBankUrlDialog = false
        dataBankUrlDraft = ""
        showAuthorsNote = false
        showGroupSettings = false
        pendingDisplay = null
        imagePrompt = ""
        showSyncNameConfirm = false
        // 群聊设置从新 VM 重读（rememberSaveable 初值捕获的是旧 vm.group）
        groupMode = vm.group?.generationMode ?: GroupGenerationMode.APPEND
        groupStrategy = vm.group?.activationStrategy ?: "natural"
        // 新会话从最新一条开始看（旧列表滚动位置对新会话无意义）
        followBottom = true
    }

    // 斜杠命令清单随 VM 重建（会话切换后 vm 实例更换，避免持有旧实例快照）
    val slashCommands = remember(vm) { vm.slashCommandList() }

    // 每次进入聊天页重新读盘：配置模型后返回不再显示“没配置模型”；设置页改快捷回复后同步刷新。
    // key=vm：导航复用组合而 VM 实例更换时重启副作用，避免协程持有旧 VM 引用（刷新打到旧会话）。
    LaunchedEffect(vm) {
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

    // 贴底跟随（reverseLayout=true）：官方 LazyColumn 语义下 firstVisibleItemIndex==0 即“滚到底部”。
    // 新消息/流式内容增长时底部天然钉住，无需任何 scrollToItem 强制滚动（旧的 layoutInfo 采样 +
    // 多条 scrollToItem(Int.MAX_VALUE) 是滚动卡顿主因）。只有用户上滑（滚动位置变大）才暂停跟随。
    LaunchedEffect(listState) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val movedAway = listState.isScrollInProgress &&
                    (index > prevIndex || (index == prevIndex && offset > prevOffset))
                if (movedAway) followBottom = false
                if (index == 0 && offset == 0) followBottom = true
                prevIndex = index
                prevOffset = offset
            }
    }
    // 用户上滑看历史期间新消息到了不拽走；发送/快捷回复重新打开跟随时才回到最新。
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && followBottom) {
            // 首帧尚未测量时 animateScrollToItem 会被吞甚至越界：先等列表布局出条目再滚。
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            // 瞬时定位：reverseLayout 下已贴底（index 0）就直接跳过，避免键盘收起/新消息时反复纠正滚动位置
            if (listState.firstVisibleItemIndex != 0) {
                listState.scrollToItem(0)
            }
        }
    }
    // 官方 generate() 开头 scrollLock = false：任何生成类型（发送/继续/重生成/变体/冒充/群聊轮次）
    // 开始时恢复自动贴底跟随——用户上滑看历史时点重新生成，官方会回到最新并跟随。
    LaunchedEffect(generationEpoch) {
        if (generationEpoch > 0) followBottom = true
    }
    // 显示设置（encode_tags/正则/行为）变更即时生效：DisplayCacheVersion bump → VM 清缓存 + 全表行重算。
    // key=vm 同上：VM 更换时重订阅，collect 回调不落在旧实例上。
    LaunchedEffect(vm) {
        snapshotFlow { com.emberinn.app.data.DisplayCacheVersion.version }.drop(1).collect {
            vm.onDisplaySettingsChanged()
        }
    }

    val sky = rememberSky()
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // 行级外观设置：在 ChatScreen 层读一次传给列表，避免每条消息组合时各自读 SharedPreferences
    val rowDensity = AppearancePrefs.density(context)
    val rowImmersiveActions = AppearancePrefs.immersiveActions(context)
    val rowBubbleStyle = AppearancePrefs.bubbleStyle(context)
    var topBarHeight by remember { mutableStateOf(0) }
    val topBarPad = with(density) { topBarHeight.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            // README 返回手势：左右边缘滑动退出
            .edgeSwipeBack(onBack = onBack),
    ) {
        // 静态背景层：氛围渐变 + 光晕 + 显式/头像背景。作为顶栏/输入栏毛玻璃的静态模糊源；
        // 不再把消息列表当 sky 源，避免每次滚动/键盘动画都重捕整屏模糊（滚动/收键盘卡顿主因）。
        // EmberDS 舞台：低饱和近黑中性底；氛围渐变/宝石光晕/金属微光/画布纹理全部退役
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(EmberTheme.colors.bg)
                .background((EmberTheme.stageTint ?: Color.Transparent).copy(alpha = 0.30f)),
        ) {
        // 聊天背景：显式背景（会话 chat_metadata.custom_background / 角色主题配方）> 角色头像玻璃背景 > 舞台底色兑底
        // 可读性遮罩：深色叠黑、浅色叠纸白；模糊/遮罩强度全局可调（外观与主题）
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

        // 消息列表 + 输入栏同一列：列表 weight(1f) 占满剩余空间，输入栏沉底；imePadding 只作用这一列
        Column(
            modifier = Modifier
                .fillMaxSize()
                // imePadding 只作用于“列表 + 输入栏”这一列：键盘开合只托起输入栏、列表从底部收放，
                // 顶部栏与静态背景不参与 IME 重排，键盘动画不再整屏跳动。
                .imePadding()
                .padding(top = maxOf(topBarPad, 64.dp)),
        ) {
            if (!providerConfigured) {
                UnconfiguredBanner(onOpenSettings = onOpenSettings)
            }

            // 列表 + jump-to-bottom 浮标的同一画布（DESIGN_SYSTEM §6.2：浮标 + 未读跳转）
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    if (rowDensity == "compact") 4.dp else 8.dp,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (items.isEmpty()) {
                    item { EmptyChat(name = currentName) }
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
                            // 派生字段随消息元素或显示管线修订号变化重算：流式 tick 不重算历史行；
                            // displayRevision 变化（列表刷新/显示设置变更）后立即取新显示文本，修复改设置后残留旧文本
                            val derived = remember(el, displayRevision) {
                                val user = isUser(el)
                                ChatItemDerived(
                                    isUser = user,
                                    isSystem = isSystem(el),
                                    text = vm.displayTextOf(item.index),
                                    reasoning = (el.jsonObject["extra"] as? JsonObject)
                                        ?.get("reasoning")?.jsonPrimitive?.contentOrNull,
                                    media = mediaOf(el),
                                    mediaDisplay = extraDisplayOf(el),
                                    mediaIndex = extraIndexOf(el),
                                    name = nameOf(el, user),
                                    time = timeOf(el),
                                    swipeCount = vm.swipeCountOf(el),
                                    curSwipe = vm.currentSwipeOf(el),
                                    avatarPath = if (user) null else vm.avatarPathOf(item.index),
                                )
                            }
                            val isUserMsg = derived.isUser
                            val isSystemMsg = derived.isSystem
                            val text = derived.text
                            // 内核路径文本：引擎只做正则/宏前处理，fixMarkdown/encode_tags 由内核接管
                            val kernelText = remember(el, displayRevision) { vm.kernelDisplayTextOf(item.index) }
                            // 附件列表包一层稳定类型，避免 List 参数让整行不可跳过重组
                            val mediaList = remember(derived.media) { ChatMedia(derived.media) }
                            val immersiveActions = rowImmersiveActions
                            // 底部操作条仅最后一条 AI 显示（⋯/flag/pencil 与变体箭头同行）；
                            // 用户消息无常驻图标，长按气泡出菜单（官方移动端同款交互）
                            val showActions = !isStreaming && !isSystemMsg && !immersiveActions &&
                                !isUserMsg && item.index == lastAiIndex
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
                            // 官方 reasoning 扩展：每条 AI 消息各自渲染 Thoughts 块（extra.reasoning 逐条落盘）；
                            // 最后一条 AI 在落盘刷新落地前一帧用内存 lastReasoning 兜底；show_thoughts 关闭时全部隐藏。
                            val reasoningSrc = if (!isUserMsg && !isSystemMsg && showThoughtsNow) {
                                derived.reasoning ?: if (item.index == lastAiIndex) lastReasoning else null
                            } else null
                            val reasoningDisplay = remember(reasoningSrc, displayRevision) {
                                reasoningSrc?.takeIf { it.isNotBlank() }?.let { vm.displayReasoningText(it) }
                            }
                            val reasoningKey = remember(el) {
                                el.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull ?: "idx-${item.index}"
                            }
                            MessageRow(
                                modifier = Modifier,
                                isUser = isUserMsg,
                                isSystem = isSystemMsg,
                                // 内核流式启用后，历史行在流式期间也保持内核渲染（不再整列回退原生）
                                kernelPool = kernelPool,
                                mesid = "m-${item.index}",
                                kernelText = kernelText,
                                text = text,
                                media = mediaList,
                                mediaDisplay = derived.mediaDisplay,
                                mediaIndex = derived.mediaIndex,
                                onMediaIndexChange = { idx -> vm.setMediaIndex(item.index, idx) },
                                reasoning = reasoningDisplay,
                                reasoningExpanded = reasoningExpandedMap[reasoningKey] == true,
                                onReasoningToggle = {
                                    reasoningExpandedMap[reasoningKey] = !(reasoningExpandedMap[reasoningKey] == true)
                                },
                                name = derived.name,
                                time = derived.time,
                                dateLabel = dateLabel,
                                // 官方：用户消息头像 = 人设头像（user_avatar，无则默认占位）；AI = 角色/force_avatar
                                avatarPath = if (isUserMsg) {
                                    activePersona?.avatarPath?.takeIf { java.io.File(it).exists() }
                                } else {
                                    derived.avatarPath ?: vm.avatarPath
                                },
                                spritePath = (item.element.jsonObject["extra"] as? JsonObject)
                                    ?.get("sprite")?.jsonPrimitive?.contentOrNull,
                                tokenCount = (item.element.jsonObject["extra"] as? JsonObject)
                                    ?.get("token_count")?.jsonPrimitive?.contentOrNull,
                                accent = accent,
                                aiBubble = rowBubbleStyle == "bubble",
                                onImageToggle = { vm.setMediaDisplay(item.index) },
                                showActions = showActions,
                                // 官方 isMessageSwipeable：仅 chat.length-1 且非用户、非系统、生成中隐藏（script.js:9123-9145）
                                // —— 最后一条是用户消息时，前面的 AI 消息也不显示 swipe 控件
                                swipeCount = if (item.index == messages.lastIndex && !isUserMsg && !isSystemMsg && !isStreaming) derived.swipeCount else 0,
                                curSwipe = derived.curSwipe,
                                isPrevSameSender = isPrevSameSender,
                                onSwipeLeft = { vm.swipeLeft(item.index) },
                                onSwipeRight = { vm.swipeRight(item.index) },
                                onSwipePicker = { swipePickerIndex = MsgTarget(item.index, el) },
                                onEdit = { editIndex = MsgTarget(item.index, el); editDraft = text },
                                onMore = { menuMessageIndex = MsgTarget(item.index, el) },
                                onBookmark = {
                                    bookmarkDraftName = vm.defaultBookmarkName()
                                    showBookmarkDialog = true
                                },
                                onClassifyExpression = { t, cb -> vm.classifyExpression(t, cb) },
                                classifyEnabled = true,
                                deleteCheck = if (deleteMode) deleteCheckIndex == item.index else null,
                                onDeleteCheck = if (deleteMode) ({
                                    deleteCheckIndex = if (deleteCheckIndex == item.index) null else item.index
                                }) else null,
                                onLongPress = { if (!deleteMode) menuMessageIndex = MsgTarget(item.index, el) },
                            )
                        }
                        ChatItem.Streaming -> {
                            // 流式状态只在“流式这一行”订阅：每 token 更新不会让整棵消息列表重组。
                            // VM 已按官方 streaming_fps≈30 单层节流（33ms 一帧），这里直接消费，不再叠 UI 节流
                            // （此前 100ms+120ms 双层叠加 ~220ms 一帧，是流式视觉卡顿主因）。
                            val st by vm.streamingText.collectAsState()
                            val sr by vm.streamingReasoning.collectAsState()
                            // 思考流式同正文：定界符补齐 + fixMarkdown + encode_tags（官方 messageFormatting 每 tick）
                            val reasoningDisplay = remember(sr, isStreaming) {
                                val balanced = DisplayPipeline.balanceStreamingDelimiters(sr, isFinal = !isStreaming)
                                val fixed = com.emberinn.engine.prompt.FixMarkdown.fix(balanced, forDisplay = true)
                                if (AppearancePrefs.encodeTags(context)) com.emberinn.engine.prompt.MessageFormattingEngine.encodeTags(fixed) else fixed
                            }
                            // 官方 messageFormatting 每 tick：定界符补齐（onProgressStreaming）+ fixMarkdown(forDisplay=true)
                            // + encode_tags（auto_fix_generated_markdown 默认开）——流式中也必须跑，否则未闭合 ** 会露符号
                            val streamingDisplay = remember(st, isStreaming) {
                                val balanced = DisplayPipeline.balanceStreamingDelimiters(st, isFinal = !isStreaming)
                                val fixed = com.emberinn.engine.prompt.FixMarkdown.fix(balanced, forDisplay = true)
                                if (AppearancePrefs.encodeTags(context)) com.emberinn.engine.prompt.MessageFormattingEngine.encodeTags(fixed) else fixed
                            }
                            StreamingRow(
                                modifier = Modifier,
                                text = streamingDisplay,
                                reasoning = reasoningDisplay,
                                reasoningExpanded = reasoningExpanded,
                                onReasoningToggle = { reasoningExpanded = !reasoningExpanded },
                                name = currentName,
                                avatarPath = vm.avatarPath,
                                accent = accent,
                                impersonating = isImpersonating,
                                kernelPool = if (!isImpersonating) kernelPool else null,
                                mesid = "m-${items.lastIndex}",
                            )
                        }
                        ChatItem.ReasoningOnly -> {
                            lastReasoning?.let {
                                ReasoningCard(
                                    text = vm.displayReasoningText(it),
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

                // jump-to-bottom 浮标：用户上滑看历史时出现，点击回到最新并恢复贴底跟随
                if (!followBottom) {
                    val jumpScope = rememberCoroutineScope()
                    Surface(
                        shape = CircleShape,
                        color = EmberTheme.colors.surface.copy(alpha = 0.92f),
                        border = BorderStroke(0.5.dp, EmberTheme.colors.line),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                    ) {
                        IconButton(onClick = {
                            followBottom = true
                            jumpScope.launch { listState.animateScrollToItem(0) }
                        }) {
                            Icon(
                                FaIcons.ChevronDown,
                                contentDescription = "回到最新",
                                tint = EmberTheme.colors.inkSoft,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // 官方 #dialogue_del_mes（Delete/Cancel）：删除模式时输入栏替换为确认条
            if (deleteMode) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (deleteCheckIndex == null) "点选一条消息：从该条起全部删除"
                            else "将从第 ${deleteCheckIndex!! + 1} 条起全部删除",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { deleteMode = false; deleteCheckIndex = null }) { Text("取消") }
                        TextButton(
                            enabled = deleteCheckIndex != null,
                            onClick = {
                                deleteCheckIndex?.let { vm.truncateFrom(it) }
                                deleteMode = false
                                deleteCheckIndex = null
                                followBottom = true
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("删除") }
                    }
                }
            } else {
                ChatInputBar(
                accent = accent,
                input = input,
                impersonating = isImpersonating,
                impersonationDraft = vm.impersonationDraft,
                onInputChange = { input = it },
                pendingMedia = pendingMedia,
                pendingDisplay = pendingDisplay,
                onDisplayChange = { pendingDisplay = it },
                onRemoveMedia = { index -> vm.removePendingMedia(index) },
                isStreaming = isStreaming,
                canQuickContinue = !isStreaming && vm.canContinueGeneration(),
                worldHitsCount = worldHits.size,
                contextUsage = contextUsage,
                onOpenWorldPanel = { worldPanel = true },
                onOpenContextDetail = { contextDetail = true },
                quickReplies = quickReplies,
                onQuickReply = { label -> vm.runQuickReply(label) },
                onQuickContinue = { vm.continueGeneration() },
                onQuickImpersonate = { vm.impersonate() },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() || pendingMedia.isNotEmpty()) {
                        followBottom = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val accepted = vm.send(text, media = pendingMedia, mediaDisplay = pendingDisplay)
                        if (accepted) {
                            input = ""
                            pendingDisplay = null
                            keyboardController?.hide()
                        }
                    }
                },
                onStop = { vm.stop() },
                onAttach = {
                    showAttachOptions = true
                },
                slashCommands = slashCommands,
                modifier = Modifier
                    .fillMaxWidth()
                    .emberGlass(sky = sky, atTop = true, blurEnabled = glassBlurActive),
                )
            }
        }

        // 沉浸顶栏（V2 §5.2 聊天页）：贴底阅读时完整显示；向上翻历史淡出，留渐变遮罩保顶部消息可读
        val topBarSolid by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 }
        }
        val topBarAlpha by animateFloatAsState(
            targetValue = if (topBarSolid) 1f else 0f,
            animationSpec = tween(180),
            label = "chatTopBarAlpha",
        )
        if (topBarAlpha < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(maxOf(topBarPad, 64.dp) + 20.dp)
                    .graphicsLayer { alpha = 1f - topBarAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(EmberTheme.colors.bg.copy(alpha = 0.88f), Color.Transparent),
                        ),
                    ),
            )
        }
        ChatTopBar(
            name = currentName,
            avatarPath = vm.avatarPath,
            accent = accent,
            onBack = onBack,
            onMenu = { showMore = true },
            onPersona = { showPersonaPicker = true },
            onAuthorsNote = { openAuthorsNote() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = topBarAlpha }
                .onSizeChanged { topBarHeight = it.height }
                .emberGlass(sky = sky, atTop = false, blurEnabled = glassBlurActive),
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

    menuMessageIndex?.let { target ->
        // 打开菜单期间列表可能被刷新（翻译/变体等异步完成）：按身份重定位，漂移则关菜单不误操作
        val index = target.resolve(messages)
        val el = index?.let { messages.getOrNull(it) }
        if (index != null && el != null) {
            val text = textOf(el)
            val isUserMsg = isUser(el)
            val isSystemMsg = isSystem(el)
            val msgName = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val isRealSystem = isSystemMsg && msgName == SYSTEM_USER_NAME
            val swipeCount = vm.swipeCountOf(el)
            val mediaOfMsg = mediaOf(el)
            EmberBottomSheet(onDismissRequest = { menuMessageIndex = null }, sheetState = rememberModalBottomSheetState()) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        if (isUserMsg) "我的消息" else msgName.ifBlank { currentName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                    // ── 分层顺序整体颠倒：越常用越靠上（操作 → 变体 → 存档 → 结构 → 官方扩展），不再往下滑 ──
                    // ── 官方常驻按钮：copy（剪贴板）/ edit / delete ──
                    MenuSectionLabel("操作")
                    MenuRow(FaIcons.Copy, "复制文本") {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        menuMessageIndex = null
                    }
                    MenuRow(FaIcons.Pencil, "编辑这条消息") {
                        editIndex = MsgTarget(index, el); editDraft = text; menuMessageIndex = null
                    }
                    MenuRow(FaIcons.TrashCan, "删除这条消息", danger = true) {
                        deleteTargetIndex = MsgTarget(index, el); menuMessageIndex = null
                    }
                    // ── 回复变体（官方 swipe chevrons：仅最后一条消息；swipes_visible 需 >1，
                    //    overswipe=REGENERATE 时右箭头恒显 = 生成新变体入口；官方 per-message
                    //    无 regenerate/impersonate/continue——重新生成是全局操作，在 ⋯ 菜单）──
                    val isLastMessage = index == messages.lastIndex
                    if (!isUserMsg && !isSystemMsg && (swipeCount > 1 || isLastMessage)) {
                        MenuSectionLabel("回复变体（Swipes）")
                        if (isLastMessage) {
                            MenuRow(FaIcons.ChevronRight, "生成新回复（变体）") {
                                vm.generateSwipe(); menuMessageIndex = null
                            }
                        }
                        if (swipeCount > 1) {
                            MenuRow(FaIcons.ChevronLeft, "上一个回复") {
                                vm.swipeLeft(index); menuMessageIndex = null
                            }
                            MenuRow(FaIcons.ChevronRight, "下一个回复") {
                                vm.swipeRight(index); menuMessageIndex = null
                            }
                        }
                        if (swipeCount >= 1) {
                            MenuRow(FaIcons.Bookmark, "变体列表") {
                                swipePickerIndex = MsgTarget(index, el); menuMessageIndex = null
                            }
                        }
                        if (swipeCount > 1) {
                            MenuRow(FaIcons.TrashCan, "删除当前回复", danger = true) {
                                deleteSwipeTargetIndex = MsgTarget(index, el); menuMessageIndex = null
                            }
                        }
                    }
                    // ── 存档（官方 mes_create_bookmark / mes_create_branch）──
                    MenuSectionLabel("存档")
                    MenuRow(FaIcons.Flag, "创建书签（存档到此）") {
                        menuMessageIndex = null
                        bookmarkDraftName = vm.defaultBookmarkName()
                        showBookmarkDialog = true
                    }
                    MenuRow(FaIcons.CodeBranch, "创建分支（Branch）") {
                        menuMessageIndex = null
                        vm.createBranch(index)?.let { onSwitchSession(it) }
                    }
                    // ── 官方编辑模式按钮（mes_edit_*）：上移/下移/创建副本 ──
                    MenuSectionLabel("消息结构（官方编辑模式）")
                    if (index > 0) {
                        MenuRow(FaIcons.ChevronUp, "上移一条") {
                            vm.moveMessage(index, -1)
                            menuMessageIndex = null
                        }
                    }
                    if (index < messages.lastIndex) {
                        MenuRow(FaIcons.ChevronDown, "下移一条") {
                            vm.moveMessage(index, 1)
                            menuMessageIndex = null
                        }
                    }
                    MenuRow(FaIcons.Copy, "创建副本（插到本条之后）") {
                        vm.duplicateMessage(index)
                        menuMessageIndex = null
                    }
                    // ── 官方 extraMesButtons 顺序：翻译 → 生成图片 → 朗读 → Prompt → 隐藏 → 媒体样式 → 嵌入 ──
                    MenuRow(FaIcons.Language, "翻译这条消息") {
                        vm.translateMessage(index)
                        menuMessageIndex = null
                    }
                    if (text.isNotBlank()) {
                        MenuRow(FaIcons.Paintbrush, "生成图片（用这条消息作提示）") {
                            menuMessageIndex = null
                            vm.generateImageForMessage(index)
                        }
                    }
                    MenuRow(FaIcons.Bullhorn, "朗读这条消息") {
                        vm.narrateMessage(index)
                        menuMessageIndex = null
                    }
                    MenuRow(FaIcons.SquarePollHorizontal, "提示词分节明细（官方 Prompt Itemization）") {
                        tokenStatsIndex = MsgTarget(index, el)
                        menuMessageIndex = null
                    }
                    if (!isRealSystem) {
                        val hidden = isSystemMsg
                        MenuRow(
                            if (hidden) FaIcons.EyeSlash else FaIcons.Eye,
                            if (hidden) "取消隐藏（恢复参与提示词）" else "隐藏（不进提示词）",
                        ) {
                            vm.hideMessage(index, !hidden)
                            menuMessageIndex = null
                        }
                    }
                    if (mediaOfMsg.isNotEmpty()) {
                        MenuRow(FaIcons.Image, "切换媒体显示样式（列表/图库）") {
                            vm.setMediaDisplay(index)
                            menuMessageIndex = null
                        }
                    }
                    MenuRow(FaIcons.Plus, "嵌入附件（Embed）") {
                        menuMessageIndex = null
                        embedTargetIndex = MsgTarget(index, el)
                        embedPicker.launch(arrayOf("*/*"))
                    }
                }
            }
        } else {
            // 目标消息已被删除：收起菜单（组合期不写状态，用副作用清理）
            LaunchedEffect(target) { menuMessageIndex = null }
        }
    }

    if (showConvertGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showConvertGroupConfirm = false },
            title = { Text("转换为群聊？") },
            text = { Text("将当前聊天转换为群聊（成员=当前角色），此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showConvertGroupConfirm = false
                    vm.convertToGroup()?.let { onSwitchSession(it) }
                }) { Text("转换", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showConvertGroupConfirm = false }) { Text("取消") }
            },
        )
    }

    deleteTargetIndex?.let { target ->
        val index = target.resolve(messages)
        if (index != null) {
            AlertDialog(
                onDismissRequest = { deleteTargetIndex = null },
                title = { Text("删除这条消息？") },
                text = { Text("删除后不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        val cur = target.resolve(messages)
                        if (cur != null) vm.deleteMessage(cur)
                        deleteTargetIndex = null
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargetIndex = null }) { Text("取消") }
                },
            )
        } else {
            LaunchedEffect(target) { deleteTargetIndex = null }
        }
    }

    deleteSwipeTargetIndex?.let { target ->
        val index = target.resolve(messages)
        val el = index?.let { messages.getOrNull(it) }
        val cur = if (el != null) vm.currentSwipeOf(el) + 1 else 0
        if (index != null && el != null) {
            AlertDialog(
                onDismissRequest = { deleteSwipeTargetIndex = null },
                title = { Text("删除这个回复？") },
                text = { Text("将删除该消息的第 $cur 个回复变体，删除后不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        val rIdx = target.resolve(messages)
                        val rEl = rIdx?.let { messages.getOrNull(it) }
                        if (rIdx != null && rEl != null) {
                            vm.deleteSwipe(rIdx, vm.currentSwipeOf(rEl))
                            Toast.makeText(context, "已删除该回复", Toast.LENGTH_SHORT).show()
                        }
                        deleteSwipeTargetIndex = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteSwipeTargetIndex = null }) { Text("取消") }
                },
            )
        } else {
            LaunchedEffect(target) { deleteSwipeTargetIndex = null }
        }
    }

    editIndex?.let { target ->
        val index = target.resolve(messages)
        if (index != null) {
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
                        // 保存前重定位：弹层打开期间列表刷新则按身份找回，失配放弃（防写错消息）
                        val cur = target.resolve(messages)
                        if (cur != null) vm.editMessage(cur, editDraft)
                        editIndex = null
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { editIndex = null }) { Text("取消") }
                },
            )
        } else {
            LaunchedEffect(target) { editIndex = null }
        }
    }

    vm.character?.let { character ->
        if (showCharacterInfo) {
            CharacterInfoSheet(character = character, onDismiss = { showCharacterInfo = false })
        }
    }

    if (showMore) {
        // 官方 option_back_to_main：仅分支会话（metadata.main_chat）显示
        val parentSession = remember { vm.parentSession() }
        EmberBottomSheet(onDismissRequest = { showMore = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "会话菜单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                // ── 官方 #options 顶部组：作者注释 / CFG / logprobs ──
                MenuSectionLabel("写作工具（官方 options）")
                MenuRow(FaIcons.FileLines, "作者注释") {
                    showMore = false
                    val draft = vm.authorsNoteDraft()
                    anPrompt = draft.prompt
                    anPosition = draft.position
                    anDepth = draft.depth
                    anRole = draft.role
                    anInterval = draft.interval
                    val charaDraft = vm.charaNoteDraft()
                    charaNotePrompt = charaDraft?.prompt.orEmpty()
                    charaNoteUse = charaDraft?.useChara == true
                    charaNotePosition = charaDraft?.position ?: 0
                    showAuthorsNote = true
                }
                MenuRow(FaIcons.ScaleBalanced, "CFG Scale（引导缩放）") {
                    showMore = false
                    showCfgSheet = true
                }
                MenuRow(FaIcons.ChartPie, "Token 概率（logprobs）") {
                    showMore = false
                    showLogprobsSheet = true
                }
                // ── 官方检查点组：back_to_main / new_bookmark / convert_to_group ──
                MenuSectionLabel("检查点与分支")
                parentSession?.let { parent ->
                    MenuRow(FaIcons.ArrowLeft, "回到父聊天（${parent.name}）") {
                        showMore = false
                        onSwitchSession(parent)
                    }
                }
                // 官方 options 弹层 option_new_bookmark：在当前位置存检查点（分支聊天）
                MenuRow(FaIcons.Flag, "保存检查点（书签）") {
                    showMore = false
                    bookmarkDraftName = vm.defaultBookmarkName()
                    showBookmarkDialog = true
                }
                MenuRow(FaIcons.Bookmark, "书签列表") {
                    showMore = false
                    showBookmarksSheet = true
                }
                if (vm.character != null && vm.group == null) {
                    MenuRow(FaIcons.PeopleArrows, "转换为群聊") {
                        showMore = false
                        showConvertGroupConfirm = true
                    }
                }
                // ── 官方聊天管理组：start_new_chat / close_chat / select_chat ──
                MenuSectionLabel("聊天管理")
                MenuRow(FaIcons.Comments, "开始新聊天（旧的保留在会话列表）") {
                    showMore = false
                    vm.startNewChat()?.let(onSwitchSession)
                }
                MenuRow(FaIcons.AddressBook, "管理聊天文件（Past Chats）") {
                    showMore = false
                    showPastChats = true
                }
                // ── 官方生成组：delete_mes / regenerate / impersonate / continue ──
                MenuSectionLabel("生成")
                MenuRow(FaIcons.Repeat, "重新生成（最后一条 AI 回复）") {
                    showMore = false
                    vm.regenerate()
                }
                MenuRow(FaIcons.User, "冒充（让模型替你说）") {
                    showMore = false
                    vm.impersonate()
                }
                MenuRow(FaIcons.ArrowRight, "继续生成") {
                    showMore = false
                    vm.continueGeneration()
                }
                MenuRow(FaIcons.TrashCan, "删除消息（勾选一条，从该条起删除）", danger = true, enabled = !isStreaming) {
                    showMore = false
                    deleteMode = true
                    deleteCheckIndex = null
                }
                // ── App 扩展（官方无此入口，移动端便捷项）──
                MenuSectionLabel("更多")
                MenuRow(FaIcons.Image, "聊天背景") {
                    showMore = false
                    backgroundPicker.launch(arrayOf("image/*"))
                }
                if (chatBackground != null) {
                    MenuRow(FaIcons.XMark, "清除聊天背景") {
                        showMore = false
                        vm.clearChatBackground()
                    }
                }
                MenuRow(FaIcons.MagnifyingGlass, "数据银行（向量检索）") {
                    showMore = false
                    showDataBank = true
                }
                MenuRow(FaIcons.Flask, "提示词预览（dryRun）") {
                    showMore = false
                    showPromptPreview = true
                    vm.previewPrompt()
                }
                // App 扩展：官方 options 无“清空当前会话”（官方只删文件/删消息）；移动端便捷项，二次确认
                MenuRow(FaIcons.Eraser, "清空当前会话", danger = true, enabled = messages.isNotEmpty()) {
                    showMore = false
                    showClearConfirm = true
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setShowThoughtsQuick(!showThoughtsNow) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Icon(FaIcons.Brain, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "显示思考过程（show_thoughts）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    EmberSwitch(checked = showThoughtsNow, onCheckedChange = { vm.setShowThoughtsQuick(it) })
                }
                if (vm.group != null) {
                    MenuRow(FaIcons.Users, "群聊设置") {
                        showMore = false
                        groupMode = vm.group?.generationMode ?: GroupGenerationMode.APPEND
                        groupStrategy = vm.group?.activationStrategy ?: "natural"
                        showGroupSettings = true
                    }
                }
                MenuRow(FaIcons.User, "人设") {
                    showMore = false
                    showPersonaPicker = true
                }
                if (vm.character != null) {
                    MenuRow(FaIcons.CircleInfo, "角色详情") {
                        showMore = false
                        showCharacterInfo = true
                    }
                }
                MenuRow(FaIcons.Download, "导出聊天（JSONL）") {
                    showMore = false
                    exportChatLauncher.launch("$currentName-${System.currentTimeMillis().toString().takeLast(8)}.jsonl")
                }
                MenuRow(FaIcons.WandMagicSparkles, "记忆总结（立即）") {
                    showMore = false
                    vm.forceMemorySummary()
                }
                MenuRow(FaIcons.BookOpen, "外置世界（本会话）") {
                    showMore = false
                    showWorldPicker = true
                }
            }
        }
    }

    // 官方 displayPastChats（#select_chat_popup）：同角色/群聊天文件列表 + 搜索 +
    // 行内 改名(fa-pencil) / 导出JSONL(fa-file-export) / 导出txt(fa-file-lines) / 删除(fa-skull)，当前聊天高亮。
    if (showPastChats) {
        // key 含 pastChatsRevision：弹层内改名/删除后即时重读列表（否则要等 messages 变化才刷新）
        val entries = remember(showPastChats, pastChatsQuery, messages, pastChatsRevision) {
            vm.pastChats(pastChatsQuery)
        }
        EmberBottomSheet(onDismissRequest = { showPastChats = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        "管理聊天文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    // 官方 newChatFromManageScreenButton：弹层内直接开新聊天
                    TextButton(onClick = {
                        showPastChats = false
                        vm.startNewChat()?.let(onSwitchSession)
                    }) {
                        Icon(FaIcons.Comments, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("开始新聊天")
                    }
                }
                // 官方 #select_chat_search
                EmberTextField(
                    value = pastChatsQuery,
                    onValueChange = { pastChatsQuery = it },
                    singleLine = true,
                    placeholder = { Text("搜索聊天文件…") },
                    leadingIcon = { Icon(FaIcons.MagnifyingGlass, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                    itemsIndexed(entries, key = { _, e -> e.record.id }) { _, e ->
                        PastChatRow(
                            entry = e,
                            onClick = {
                                showPastChats = false
                                if (!e.isCurrent) onSwitchSession(e.record)
                            },
                            onRename = { renameChatTarget = e.record.id to e.record.name; renameChatDraft = e.record.name },
                            onExportJsonl = {
                                pendingExportJsonl = e.record.id to e.record.name
                                exportChatLauncher.launch("${e.record.name}.jsonl")
                            },
                            onExportText = {
                                val text = vm.exportChatPlainText(e.record.id)
                                if (text == null) {
                                    Toast.makeText(context, "这条会话还没有消息，无内容可导出", Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingExportText = text
                                    pendingExportTextName = e.record.name
                                    exportChatTextLauncher.launch("${e.record.name}.txt")
                                }
                            },
                            onDelete = { deleteChatTarget = e.record.id to e.record.name },
                        )
                    }
                }
            }
        }
    }

    renameChatTarget?.let { (id, oldName) ->
        AlertDialog(
            onDismissRequest = { renameChatTarget = null },
            title = { Text("重命名聊天文件") },
            text = {
                EmberTextField(
                    value = renameChatDraft,
                    onValueChange = { renameChatDraft = it },
                    singleLine = true,
                    label = { Text(oldName) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePastChat(id, renameChatDraft)
                    renameChatTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameChatTarget = null }) { Text("取消") } },
        )
    }

    deleteChatTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleteChatTarget = null },
            title = { Text("删除聊天文件") },
            text = { Text("确定删除「$name」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePastChat(id)
                        deleteChatTarget = null
                        if (id == vm.currentSessionId) onBack()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteChatTarget = null }) { Text("取消") } },
        )
    }

    if (showCfgSheet) {
        CfgScaleSheet(
            initial = vm.cfgSnapshot(),
            onDismiss = { showCfgSheet = false },
            onSave = { g, c, ch ->
                vm.saveCfgGlobal(g)
                vm.saveCfgChara(c)
                vm.saveCfgChat(ch)
                showCfgSheet = false
            },
        )
    }

    if (showLogprobsSheet) {
        LogprobsSheet(
            logprobs = vm.logprobs.collectAsState().value,
            onDismiss = { showLogprobsSheet = false },
        )
    }

    if (showPromptPreview) {
        AlertDialog(
            onDismissRequest = { showPromptPreview = false; vm.consumePromptPreview() },
            title = { Text("提示词预览（dryRun）") },
            text = {
                Column {
                    promptPreview?.let { preview ->
                        if (preview.counts.isNotEmpty()) {
                            preview.counts.entries
                                .filter { it.value > 0 }
                                .sortedBy { it.key }
                                .forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            promptSectionLabel(key),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "$value t",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(
                            "Token 合计：${preview.tokens}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.size(8.dp))
                    }
                    // 固定高度 + 外层滚动：内容超过 360dp 可滑动（对话框内嵌滚动失效的根治）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            promptPreview?.text ?: "（正在总装…）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.previewPrompt() }) { Text("刷新") }
            },
            dismissButton = {
                TextButton(onClick = { showPromptPreview = false; vm.consumePromptPreview() }) { Text("关闭") }
            },
        )
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
                    val anTokens = remember(anPrompt) { vm.tokenCount(anPrompt) }
                    val anNext = remember(anInterval) { vm.nextAnInsertion(anInterval) }
                    Text(
                        "Tokens: $anTokens · 下次插入：${anNext ?: "禁用"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
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
                    Text(
                        "角色（role）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = anRole == 0, onClick = { anRole = 0 }, label = { Text("系统") })
                        FilterChip(selected = anRole == 1, onClick = { anRole = 1 }, label = { Text("用户") })
                        FilterChip(selected = anRole == 2, onClick = { anRole = 2 }, label = { Text("助手") })
                    }
                    EmberTextField(
                        value = anInterval.toString(),
                        onValueChange = { anInterval = it.toIntOrNull() ?: 1 },
                        label = { Text("注入间隔（每 N 条用户消息，官方 note_interval 默认 1）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (vm.character != null) {
                        Text(
                            "角色备注（${vm.character?.name}）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Text("启用角色备注（useChara）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(
                                checked = charaNoteUse,
                                onCheckedChange = { charaNoteUse = it },
                            )
                        }
                        if (charaNoteUse) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                FilterChip(selected = charaNotePosition == 0, onClick = { charaNotePosition = 0 }, label = { Text("替换") })
                                FilterChip(selected = charaNotePosition == 1, onClick = { charaNotePosition = 1 }, label = { Text("前置") })
                                FilterChip(selected = charaNotePosition == 2, onClick = { charaNotePosition = 2 }, label = { Text("后置") })
                            }
                            EmberTextField(
                                value = charaNotePrompt,
                                onValueChange = { charaNotePrompt = it },
                                label = { Text("角色备注内容") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveAuthorsNote(anPrompt.trim(), anPosition, anDepth, anRole, anInterval)
                    if (vm.character != null) {
                        if (charaNoteUse && charaNotePrompt.isNotBlank()) {
                            vm.saveCharaNote(
                                com.emberinn.app.ui.settings.CharaNoteData(
                                    prompt = charaNotePrompt.trim(),
                                    useChara = true,
                                    position = charaNotePosition,
                                ),
                            )
                        } else {
                            vm.deleteCharaNote()
                        }
                    }
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
                EmberTextField(
                    value = personaQuery,
                    onValueChange = { personaQuery = it },
                    placeholder = { Text("搜索人设") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = {
                            val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                                .format(java.util.Date())
                            personaBackupLauncher.launch("personas_$stamp.json")
                        }) { Text("备份") }
                    TextButton(onClick = { personaRestoreLauncher.launch(arrayOf("application/json")) }) { Text("恢复") }
                }
                val filteredPersonas = remember(personas, personaQuery) {
                    val q = personaQuery.trim()
                    if (q.isBlank()) personas else personas.filter {
                        it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
                    }
                }
                if (filteredPersonas.isEmpty()) {
                    Text(
                        "还没有人设。新建后，人设描述会注入提示词（官方 Persona Management 语义）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                filteredPersonas.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.setPersona(p.id)
                            showPersonaPicker = false
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        if (p.avatarPath.isNotBlank() && File(p.avatarPath).exists()) {
                            AsyncImage(
                                model = File(p.avatarPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
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
                        if (defaultPersona?.id == p.id) {
                            Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(
                            onClick = { vm.setDefaultPersona(p.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                FaIcons.Star,
                                contentDescription = "设为人设默认",
                                modifier = Modifier.size(16.dp),
                                tint = if (defaultPersona?.id == p.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(
                            onClick = { vm.duplicatePersona(p.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                FaIcons.Copy,
                                contentDescription = "复制人设",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = {
                            editingPersona = p
                            personaDraftName = p.name
                            personaDraftDesc = p.description
                            personaDraftPosition = p.position
                            personaDraftDepth = p.depth
                            personaDraftRole = p.role
                            personaDraftTitle = p.title
                            personaDraftLorebook = p.lorebook
                            personaDraftAvatar = p.avatarPath
                            personaDraftConnectChar = p.connections.any { it.type == "character" && it.id == vm.characterId }
                            personaDraftConnectGroup = p.connections.any { it.type == "group" && it.id == vm.group?.id }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(FaIcons.Pencil, contentDescription = "编辑人设", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { vm.deletePersona(p.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(FaIcons.TrashCan, contentDescription = "删除人设", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
                TextButton(
                    onClick = {
                        editingPersona = Persona(id = "p-" + System.nanoTime().toString(36), name = "", description = "")
                        personaDraftName = ""
                        personaDraftDesc = ""
                        personaDraftPosition = 0
                        personaDraftDepth = 2
                        personaDraftRole = 0
                        personaDraftTitle = ""
                        personaDraftLorebook = ""
                        personaDraftAvatar = ""
                        personaDraftConnectChar = false
                        personaDraftConnectGroup = false
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("＋ 新建人设") }
                val locked = vm.lockedPersonaId()
                TextButton(
                    onClick = {
                        if (locked != null) {
                            vm.lockPersonaToChat(null)
                        } else {
                            activePersona?.let { vm.lockPersonaToChat(it.id) }
                        }
                        showPersonaPicker = false
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(if (locked != null) "解除人设聊天锁" else "锁定当前人设到本聊天")
                }
                TextButton(
                    onClick = { showSyncNameConfirm = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text("同步名称到历史消息")
                }
            }
        }
    }

    if (showSyncNameConfirm) {
        AlertDialog(
            onDismissRequest = { showSyncNameConfirm = false },
            title = { Text("确认同步？") },
            text = { Text("本聊天所有用户消息将改名为 ${vm.userName}（官方 syncUserNameToPersona 语义）。") },
            confirmButton = {
                TextButton(onClick = {
                    showSyncNameConfirm = false
                    showPersonaPicker = false
                    vm.syncUserNameToPersona()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSyncNameConfirm = false }) { Text("取消") }
            },
        )
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
                    Text(
                        "注入位置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = personaDraftPosition == 0, onClick = { personaDraftPosition = 0 }, label = { Text("提示词内") })
                        FilterChip(selected = personaDraftPosition == 2, onClick = { personaDraftPosition = 2 }, label = { Text("备注上") })
                        FilterChip(selected = personaDraftPosition == 3, onClick = { personaDraftPosition = 3 }, label = { Text("备注下") })
                        FilterChip(selected = personaDraftPosition == 4, onClick = { personaDraftPosition = 4 }, label = { Text("深度注入") })
                        FilterChip(selected = personaDraftPosition == 9, onClick = { personaDraftPosition = 9 }, label = { Text("不注入") })
                    }
                    if (personaDraftPosition == 4) {
                        EmberTextField(
                            value = personaDraftDepth.toString(),
                            onValueChange = { personaDraftDepth = it.toIntOrNull() ?: 4 },
                            label = { Text("深度（AT_DEPTH 时生效）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    Text(
                        "角色（role）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = personaDraftRole == 0, onClick = { personaDraftRole = 0 }, label = { Text("系统") })
                        FilterChip(selected = personaDraftRole == 1, onClick = { personaDraftRole = 1 }, label = { Text("用户") })
                        FilterChip(selected = personaDraftRole == 2, onClick = { personaDraftRole = 2 }, label = { Text("助手") })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        if (personaDraftAvatar.isNotBlank()) {
                            AsyncImage(
                                model = File(personaDraftAvatar),
                                contentDescription = "人设头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = { personaAvatarPicker.launch(arrayOf("image/*")) }) {
                            Text("选择头像")
                        }
                        Spacer(Modifier.width(8.dp))
                        if (personaDraftAvatar.isNotBlank()) {
                            TextButton(onClick = { personaDraftAvatar = "" }) { Text("清除") }
                        }
                    }
                    EmberTextField(
                        value = personaDraftTitle,
                        onValueChange = { personaDraftTitle = it },
                        label = { Text("标题（官方 title）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("人设世界书（官方 lorebook，参与扫描）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { personaShowLorePicker = true }) {
                                Text(if (personaDraftLorebook.isBlank()) "未选择" else personaDraftLorebook)
                            }
                            DropdownMenu(
                                expanded = personaShowLorePicker,
                                onDismissRequest = { personaShowLorePicker = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("无") },
                                    onClick = {
                                        personaDraftLorebook = ""
                                        personaShowLorePicker = false
                                    },
                                )
                                vm.externalWorlds().forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w.displayName) },
                                        onClick = {
                                            personaDraftLorebook = w.name
                                            personaShowLorePicker = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (vm.character != null || vm.group != null) {
                        Text(
                            "连接（绑定角色/群聊时自动使用）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (vm.character != null) {
                                FilterChip(
                                    selected = personaDraftConnectChar,
                                    onClick = { personaDraftConnectChar = !personaDraftConnectChar },
                                    label = { Text("当前角色") },
                                )
                            }
                            if (vm.group != null) {
                                FilterChip(
                                    selected = personaDraftConnectGroup,
                                    onClick = { personaDraftConnectGroup = !personaDraftConnectGroup },
                                    label = { Text("当前群聊") },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val connections = buildList {
                        if (personaDraftConnectChar && vm.characterId != null) {
                            add(com.emberinn.app.data.PersonaConnection(type = "character", id = vm.characterId))
                        }
                        if (personaDraftConnectGroup && vm.group?.id != null) {
                            add(com.emberinn.app.data.PersonaConnection(type = "group", id = vm.group!!.id))
                        }
                    }
                    vm.savePersona(
                        target.copy(
                            name = personaDraftName.trim(),
                            description = personaDraftDesc,
                            position = personaDraftPosition,
                            depth = personaDraftDepth,
                            role = personaDraftRole,
                            title = personaDraftTitle.trim(),
                            lorebook = personaDraftLorebook.trim(),
                            avatarPath = personaDraftAvatar,
                            connections = connections,
                        ),
                    )
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
                            Icon(FaIcons.TrashCan, contentDescription = "删除书签", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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

    val tokenStatsResolved = tokenStatsIndex?.let { it.resolve(messages) }
    if (tokenStatsIndex != null && tokenStatsResolved == null) {
        LaunchedEffect(tokenStatsIndex) { tokenStatsIndex = null }
    }
    tokenStatsResolved?.let { index ->
        // 官方 itemized-prompts.js promptItemize：按消息索引显示该次总装的分节明细。
        val entry = vm.itemizationFor(index)
        val prev = vm.itemizations().lastOrNull { it.messageIndex < index }
        EmberBottomSheet(onDismissRequest = { tokenStatsIndex = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                var showRaw by remember { mutableStateOf(false) }
                var showDiff by remember { mutableStateOf(false) }
                Text(
                    "提示词分节明细（Prompt Itemization）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (entry == null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "该消息没有分节明细：明细只在生成（发送/继续/变体）时记录，官方 itemized-prompts 同语义。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { tokenStatsIndex = null }) { Text("关闭") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(entry.rawPrompt))
                            Toast.makeText(context, "已复制提示词", Toast.LENGTH_SHORT).show()
                        }) { Text("复制") }
                        TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "收起原文" else "显示原文") }
                        TextButton(
                            onClick = { showDiff = !showDiff },
                            enabled = prev != null,
                        ) { Text(if (showDiff) "收起对比" else "与上一条对比") }
                    }
                    Text(
                        "API/模型：${entry.providerName} – ${entry.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "预设：${entry.presetName.ifBlank { "默认" }} · 分词器：${entry.tokenizer}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    // 官方 itemizationText.html：五分类 + 百分比图（角色定义/世界书/聊天历史/扩展/bias）
                    val cats = itemizationCategories(entry)
                    val catTotal = entry.totalTokens.coerceAtLeast(1)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .width(18.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            cats.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(if (c.tokens > 0) c.tokens.toFloat() / catTotal else 0f)
                                        .background(c.color),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            cats.forEach { c ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(
                                        c.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.color,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${c.tokens} t（${c.tokens * 100 / catTotal}%）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                c.subRows.forEach { (sub, tokens) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp)) {
                                        Text(
                                            "-- $sub",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "$tokens",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "总 Token：${entry.totalTokens}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Max Context（上下文-回复）：${entry.maxContext - entry.maxTokens}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Padding：0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Actual Max Context Allowed：${entry.maxContext - entry.maxTokens}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "分节消息（identifier / role / tokens，点击展开内容）",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        itemsIndexed(entry.sections) { _, sec ->
                            var expanded by remember(sec) { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                                    .padding(vertical = 5.dp),
                            ) {
                                Row {
                                    Text(
                                        sec.identifier.ifBlank { "（无标识）" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${sec.role} · ${sec.tokens}t",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (expanded) {
                                    Text(
                                        sec.content.ifBlank { "（无内容）" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (showRaw) {
                        HorizontalDivider()
                        Spacer(Modifier.size(6.dp))
                        Text("原文（raw prompt）：", style = MaterialTheme.typography.labelMedium)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(entry.rawPrompt, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (showDiff && prev != null) {
                        HorizontalDivider()
                        Spacer(Modifier.size(6.dp))
                        Text("与上一条（${prev.messageIndex}）的差异：", style = MaterialTheme.typography.labelMedium)
                        val wordDiff = wordDiffAnnotated(prev.rawPrompt, entry.rawPrompt)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (wordDiff != null) {
                                Text(wordDiff, style = MaterialTheme.typography.bodySmall)
                            } else {
                                simpleLineDiff(prev.rawPrompt, entry.rawPrompt).forEach { (tag, line) ->
                                    Text(
                                        line,
                                        color = when (tag) {
                                            '+' -> Color(0xFF7CB342)
                                            '-' -> Color(0xFFE57373)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    TextButton(onClick = { tokenStatsIndex = null }) { Text("关闭") }
                }
            }
        }
    }

    val swipePickerResolved = swipePickerIndex?.let { it.resolve(messages) }
    if (swipePickerIndex != null && swipePickerResolved == null) {
        LaunchedEffect(swipePickerIndex) { swipePickerIndex = null }
    }
    swipePickerResolved?.let { index ->
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
                                val cur = swipePickerIndex?.resolve(messages) ?: index
                                vm.swipeToVariant(cur, i)
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

    if (captionPromptAsk) {
        var promptText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.cancelCaptionFlow() },
            title = { Text("描述提示词（caption prompt_ask）") },
            text = {
                EmberTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("留空使用默认提示词") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.submitCaptionPrompt(promptText) }) { Text("生成描述") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelCaptionFlow() }) { Text("取消") }
            },
        )
    }
    if (showWorldPicker) {
        val worlds = remember { vm.externalWorlds() }
        val current = remember { vm.chatWorld() }
        AlertDialog(
            onDismissRequest = { showWorldPicker = false },
            title = { Text("本会话使用的外置世界") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FilterChip(selected = current.isNullOrEmpty(), onClick = {
                        vm.setChatWorld("")
                        showWorldPicker = false
                    }, label = { Text("（无，跟随角色/全局）") })
                    worlds.forEach { w ->
                        FilterChip(
                            selected = current == w.name,
                            onClick = {
                                vm.setChatWorld(w.name)
                                showWorldPicker = false
                            },
                            label = { Text("${w.displayName}（${w.entryCount} 条）") },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWorldPicker = false }) { Text("关闭") }
            },
        )
    }
    val captionDraftValue = captionDraft
    if (captionDraftValue != null) {
        var editText by remember(captionDraftValue) { mutableStateOf(captionDraftValue.text) }
        AlertDialog(
            onDismissRequest = { vm.cancelCaptionFlow() },
            title = { Text("确认图片描述（caption refine_mode）") },
            text = {
                EmberTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmCaptionSend(editText)
                }) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelCaptionFlow() }) { Text("取消") }
            },
        )
    }

    // 官方 stable-diffusion refine_mode：生成前弹窗确认/编辑 LLM 生成的提示词（FREE 模式不弹）
    val imageRefineValue = imageRefineDraft
    if (imageRefineValue != null) {
        var refineText by remember(imageRefineValue) { mutableStateOf(imageRefineValue.prompt) }
        AlertDialog(
            onDismissRequest = { vm.cancelImageRefine() },
            title = { Text("确认图像提示词（sd_refine_mode）") },
            text = {
                EmberTextField(
                    value = refineText,
                    onValueChange = { refineText = it },
                    label = { Text("提示词（留空则用原内容）") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmImageRefine(refineText)
                }) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelImageRefine() }) { Text("取消") }
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
    onPersona: () -> Unit = {},
    onAuthorsNote: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // EmberDS GlassBar：chrome 退后（DESIGN_SYSTEM §六.2）——半透明底 + hairline 分界，无投影
    val E = EmberTheme.colors
    Surface(
        color = E.bg.copy(alpha = 0.72f),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 底边 hairline（Compose border 无对齐重载，手绘底线）
                    drawLine(
                        color = E.line,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
                .heightIn(min = 52.dp),
        ) {
            // 返回按钮在左上角（配合边缘滑动返回），留足上下间距避免贴最高处
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(FaIcons.ArrowLeft, contentDescription = "返回", tint = E.inkSoft)
            }
            Spacer(Modifier.size(6.dp))
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 40)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = E.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAuthorsNote, modifier = Modifier.size(44.dp)) {
                Icon(FaIcons.FileLines, contentDescription = "作者注释", tint = E.inkSoft)
            }
            IconButton(onClick = onPersona, modifier = Modifier.size(44.dp)) {
                Icon(FaIcons.User, contentDescription = "人设", tint = E.inkSoft)
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp)) {
                Icon(FaIcons.Bars, contentDescription = "更多", tint = E.inkSoft)
            }
        }
    }
}

@Composable
private fun RoleAvatar(avatarPath: String?, name: String, accent: Color, size: Int) {
    val avatarFile = avatarPath?.let { File(it) }?.takeIf { it.exists() }
    // 头像形状（官方 avatar_style：ROUND 圆形50% / RECTANGULAR 大矩形 / SQUARE 方形2px / ROUNDED 圆角10px，
    // 对齐 toggle-dependent.css 的 --avatar-base-border-radius 系列半径）
    val context = LocalContext.current
    val shape = remember(context) {
        when (OfficialThemeManager.shared(context).shellSettings().avatarStyle) {
            3 -> RoundedCornerShape(10.dp)
            1, 2 -> RoundedCornerShape(2.dp)
            else -> CircleShape
        }
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
            if (name.isBlank()) {
                Icon(
                    FaIcons.User,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            } else {
                Text(
                    text = name.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
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

/** 官方 script.js systemUserName：只有这个名字的系统消息按系统消息格式化（Note 评论等按普通消息）。 */
private const val SYSTEM_USER_NAME = "SillyTavern System"

/** 内核站内源 origin（appassets.androidplatform.net，与 KernelProtocol.KERNEL_URL 同域） */
private const val KERNEL_ORIGIN = "https://appassets.androidplatform.net"

/**
 * 内核页头像 URL：仅当路径位于已暴露的站内根时可解析——
 * 角色 avatars → /avatars/<file>；persona-avatars → /pavatars/<file>。
 * 其余路径返回 null，内核模板按官方缺省头像渲染。
 */
internal fun kernelAvatarUrlOf(path: String?): String? {
    val f = path?.let { File(it) } ?: return null
    val prefix = when (f.parentFile?.name) {
        "avatars" -> "/avatars/"
        "persona-avatars" -> "/pavatars/"
        else -> return null
    }
    return "$KERNEL_ORIGIN$prefix${f.name}"
}

@Composable
private fun MessageRow(
    modifier: Modifier = Modifier,
    isUser: Boolean,
    isSystem: Boolean = false,
    kernelPool: KernelWebViewPool? = null,
    mesid: String = "",
    kernelText: String? = null,
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
    spritePath: String? = null,
    tokenCount: String? = null,
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
    onSwipePicker: () -> Unit = {},
    onEdit: () -> Unit = {},
    onMore: () -> Unit = {},
    onBookmark: () -> Unit = {},
    /** LLM 表情分类回调（官方 getExpressionLabel LLM 分支；null=不支持）。 */
    onClassifyExpression: ((String, (String?) -> Unit) -> Unit)? = null,
    classifyEnabled: Boolean = false,
    /** 官方删除模式（del_checkbox）：非 null 时行首显示勾选框，单选，勾中即 this_del_mes。 */
    deleteCheck: Boolean? = null,
    onDeleteCheck: (() -> Unit)? = null,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    if (deleteCheck != null && onDeleteCheck != null) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Checkbox(
                checked = deleteCheck,
                onCheckedChange = { onDeleteCheck() },
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                MessageRowContent(
                    modifier = Modifier,
                    isUser = isUser, isSystem = isSystem, kernelPool = kernelPool, mesid = mesid, kernelText = kernelText, text = text, media = media,
                    mediaDisplay = mediaDisplay, mediaIndex = mediaIndex, onMediaIndexChange = onMediaIndexChange,
                    reasoning = reasoning, reasoningExpanded = reasoningExpanded, onReasoningToggle = onReasoningToggle,
                    name = name, time = time, avatarPath = avatarPath, spritePath = spritePath,
                    tokenCount = tokenCount, accent = accent, dateLabel = dateLabel, showActions = false,
                    swipeCount = 0, curSwipe = curSwipe, isPrevSameSender = isPrevSameSender, aiBubble = aiBubble,
                    onImageToggle = onImageToggle, onSwipeLeft = onSwipeLeft, onSwipeRight = onSwipeRight,
                    onSwipePicker = onSwipePicker, onEdit = onEdit, onMore = onMore, onBookmark = onBookmark,
                    onClassifyExpression = onClassifyExpression, classifyEnabled = classifyEnabled,
                    onLongPress = onLongPress,
                )
            }
        }
        return
    }
    MessageRowContent(
        modifier = modifier,
        isUser = isUser, isSystem = isSystem, kernelPool = kernelPool, mesid = mesid, kernelText = kernelText, text = text, media = media,
        mediaDisplay = mediaDisplay, mediaIndex = mediaIndex, onMediaIndexChange = onMediaIndexChange,
        reasoning = reasoning, reasoningExpanded = reasoningExpanded, onReasoningToggle = onReasoningToggle,
        name = name, time = time, avatarPath = avatarPath, spritePath = spritePath,
        tokenCount = tokenCount, accent = accent, dateLabel = dateLabel, showActions = showActions,
        swipeCount = swipeCount, curSwipe = curSwipe, isPrevSameSender = isPrevSameSender, aiBubble = aiBubble,
        onImageToggle = onImageToggle, onSwipeLeft = onSwipeLeft, onSwipeRight = onSwipeRight,
        onSwipePicker = onSwipePicker, onEdit = onEdit, onMore = onMore, onBookmark = onBookmark,
        onClassifyExpression = onClassifyExpression, classifyEnabled = classifyEnabled,
        onLongPress = onLongPress,
    )
}

@Composable
private fun MessageRowContent(
    modifier: Modifier = Modifier,
    isUser: Boolean,
    isSystem: Boolean = false,
    kernelPool: KernelWebViewPool? = null,
    mesid: String = "",
    kernelText: String? = null,
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
    spritePath: String? = null,
    tokenCount: String? = null,
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
    onSwipePicker: () -> Unit = {},
    onEdit: () -> Unit = {},
    onMore: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onClassifyExpression: ((String, (String?) -> Unit) -> Unit)? = null,
    classifyEnabled: Boolean = false,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    // 官方 messageFormatting：仅 'SillyTavern System' 命名的系统消息按系统格式化（跳过引号/encode/正则）；
    // Note 评论等 is_system 消息正文按普通消息格式化（样式仍按系统灰字）
    val formatAsSystem = isSystem && name == SYSTEM_USER_NAME
    // 表情精灵：AI 消息按正文分类选立绘（官方 expressions getExpressionLabel + chooseSpriteForExpression）。
    // none API → 直接 fallback；LLM API → 先 fallback，异步分类完成后用标签重选（官方异步设置 sprite DOM 的等价）。
    val expressionPrefs = remember(text, name) { if (isUser) null else ExpressionPrefs.load(context) }
    var classified by remember(text, name) { mutableStateOf<String?>(null) }
    val classifier = onClassifyExpression
    if (expressionPrefs?.enabled == true && expressionPrefs.api == ExpressionApi.LLM &&
        classifier != null && classifyEnabled
    ) {
        LaunchedEffect(text, name) {
            if (classified == null) classifier(text) { classified = it }
        }
    }
    val spriteFile = remember(text, name, isUser, spritePath, classified) {
        if (isUser) {
            null
        } else {
            val stored = spritePath?.let { File(it).takeIf { f -> f.exists() } }
            if (stored != null) {
                stored
            } else {
                val prefs = expressionPrefs ?: ExpressionPrefs.load(context)
                if (!prefs.enabled) {
                    null
                } else {
                    val store = ExpressionStore(context)
                    val expression = when {
                        prefs.api == ExpressionApi.LLM && classified != null -> classified!!
                        else -> prefs.fallbackExpression
                    }
                    val groups = com.emberinn.engine.expression.ExpressionEngine.groupSprites(
                        store.sprites(name),
                        prefs.customLabels,
                    )
                    com.emberinn.engine.expression.ExpressionEngine.chooseSprite(
                        folderName = name,
                        expression = expression,
                        spriteCache = mapOf(name to groups),
                        settings = com.emberinn.engine.expression.ExpressionEngine.ExpressionSettings(
                            fallbackExpression = prefs.fallbackExpression.ifBlank { null },
                            allowMultiple = prefs.allowMultiple,
                            rerollIfSame = prefs.rerollIfSame,
                            customLabels = prefs.customLabels,
                        ),
                    )?.imageSrc?.let { File(it).takeIf { f -> f.exists() } }
                }
            }
        }
    }
    // 官方主题字段经 ShellTheme 推导进令牌（无本地覆盖）：气泡底/描边/弱化文字直接取 EmberDS
    val c = EmberTheme.colors
    val userBubbleColor = c.accentBg
    val botBubbleColor = c.surface
    val bubbleBorder = BorderStroke(0.5.dp, c.line)
    val emColor = c.inkMute

    // 全 DOM 行判定：内核就绪即整行交官方模板（头像/名字/时间戳/正文一体，官方移动端结构）；
    // 原生只保留宿主交互面（思考卡/操作条/媒体/手势）。用户消息按 P6 开关分流省池槽位。
    val useKernel = kernelPool != null && mesid.isNotEmpty() && kernelText != null
    val kernelAvatarUrl = remember(avatarPath) { kernelAvatarUrlOf(avatarPath) }
    // 长按菜单 + AI 消息横滑手势：全 DOM 行与原生回退路径共用
    var bubbleModifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
    if (!isUser && !isSystem && swipeCount >= 1) {
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

    Column(modifier = modifier.fillMaxWidth()) {
        // 间距层级：不同发言者之间留白更大，同一发言者连续消息收紧（纸面对话流而非堆砌）
        if (dateLabel == null && !isPrevSameSender) {
            Spacer(Modifier.size(7.dp))
        }
        if (dateLabel != null) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = emColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (isUser) 12.dp else 0.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (useKernel) {
            // 全 DOM 行：官方模板承担头像/名字/时间戳/token 计数；原生只留交互面。
            Column(modifier = Modifier.fillMaxWidth().then(bubbleModifier)) {
                // 思考块随行进内核官方 .mes_reasoning DOM（主题 CSS 接管样式，details 原生折叠展开）
                MessageKernelRow(
                    pool = kernelPool!!,
                    payload = KernelMessagePayload(
                        mesid = mesid,
                        mes = kernelText!!,
                        chName = name,
                        isUser = isUser,
                        isSystem = formatAsSystem,
                        avatarUrl = kernelAvatarUrl,
                        timestamp = time,
                        tokenCount = tokenCount,
                        reasoning = reasoning?.takeIf { it.isNotBlank() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onLongPress = onLongPress,
                )
                if (spriteFile != null) {
                    AsyncImage(
                        model = spriteFile,
                        contentDescription = "表情精灵",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(34.dp),
                    )
                }
                if (!isSystem && (swipeCount >= 1 || showActions)) {
                    Spacer(Modifier.size(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (swipeCount >= 1) {
                            MessageActionIcon(FaIcons.ChevronLeft, "上一个回复", onSwipeLeft)
                            Text(
                                text = "${curSwipe + 1}/${swipeCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onSwipePicker)
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                            )
                            MessageActionIcon(FaIcons.ChevronRight, "下一个回复", onSwipeRight)
                        }
                        if (showActions) {
                            if (swipeCount >= 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(width = 1.dp, height = 12.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                )
                            }
                            MessageActionIcon(FaIcons.EllipsisVertical, "更多操作", onMore)
                            MessageActionIcon(FaIcons.Flag, "创建书签（存档到此）", onBookmark)
                            MessageActionIcon(FaIcons.Pencil, "编辑", onEdit)
                        }
                    }
                }
                if (media.items.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    MessageMedia(media = media.items, display = mediaDisplay, index = mediaIndex, onIndexChange = onMediaIndexChange, onImageToggle = onImageToggle)
                }
            }
        } else {
        if (!isUser) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 36)
                if (spriteFile != null) {
                    AsyncImage(
                        model = spriteFile,
                        contentDescription = "表情精灵",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(34.dp),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            // 气泡自适应：内容 hug 宽度 + 上限封顶（用户 320dp / AI 气泡 340dp），
            // 纸面模式（无气泡）保持整行铺满便于长文阅读
            modifier = when {
                isUser -> Modifier.widthIn(max = 320.dp)
                aiBubble -> Modifier.widthIn(max = 340.dp)
                else -> Modifier.fillMaxWidth()
            },
        ) {
            // 官方 .ch_name 行：名字 + mes_ghost + 时间戳靠左（不 fillMaxWidth，否则会把 hug 气泡撑到上限）
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                // 官方 mes_ghost：被用户隐藏（不进提示词）的消息在名字旁标记；'SillyTavern System' 真系统消息不带
                if (isSystem && name != SYSTEM_USER_NAME) {
                    Spacer(Modifier.size(5.dp))
                    Icon(
                        FaIcons.EyeSlash,
                        contentDescription = "此消息对 AI 不可见",
                        tint = emColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = time,
                    style = (MaterialTheme.typography.labelSmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                    color = emColor,
                )
                if (tokenCount != null) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "· ${tokenCount}t",
                        style = (MaterialTheme.typography.labelSmall).let { if (textShadow != null) it.copy(shadow = textShadow) else it },
                        color = emColor,
                    )
                }
                // mes_buttons（⋯/flag/pencil）移至消息底部操作条，与变体箭头同行
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
            // 手势修饰符已上提至函数级（全 DOM 行与原生回退共用）
            if (isUser) {
                // 用户消息保留右侧胶囊：对话分隔锚点，和 AI 纯文本流形成对比
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    color = userBubbleColor,
                    border = bubbleBorder,
                    modifier = bubbleModifier,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        // 原生回退路径（内核未就绪/池满）：短文本为主的过渡显示
                        StreamingMarkdown(content = text, fillWidth = false)
                    }
                }
            } else if (aiBubble) {
                // README 气泡样式=bubble：AI 也带低对比气泡（hug 内容，上限由外层列宽封顶）
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
                    color = botBubbleColor,
                    border = bubbleBorder,
                    modifier = bubbleModifier,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        StreamingMarkdown(content = text, fillWidth = false)
                    }
                }
            } else {
                // AI 消息去气泡：纯 markdown 文本流，靠留白分隔（纸面阅读感）
                Box(modifier = bubbleModifier) {
                    StreamingMarkdown(content = text)
                }
            }
            // 底部操作条（对齐官方 swipes-counter：n/total + 左右箭头）；
            // mes_buttons（⋯ 更多 / flag 书签 / pencil 编辑）与之同行，仅最后一条 AI 显示。
            // 重做：去掉胶囊容器与逐图标底色块——裸小图标一排，安静自然不抢正文
            if (!isSystem && (swipeCount >= 1 || showActions)) {
                Spacer(Modifier.size(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (swipeCount >= 1) {
                        MessageActionIcon(FaIcons.ChevronLeft, "上一个回复", onSwipeLeft)
                        // 官方 swipes-counter 可点击：tap 打开 swipe picker（跳转任意变体）
                        Text(
                            text = "${curSwipe + 1}/${swipeCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onSwipePicker)
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                        )
                        MessageActionIcon(FaIcons.ChevronRight, "下一个回复", onSwipeRight)
                    }
                    if (showActions) {
                        if (swipeCount >= 1) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(width = 1.dp, height = 12.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            )
                        }
                        MessageActionIcon(FaIcons.EllipsisVertical, "更多操作", onMore)
                        MessageActionIcon(FaIcons.Flag, "创建书签（存档到此）", onBookmark)
                        MessageActionIcon(FaIcons.Pencil, "编辑", onEdit)
                    }
                }
            }
            if (media.items.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                MessageMedia(media = media.items, display = mediaDisplay, index = mediaIndex, onIndexChange = onMediaIndexChange, onImageToggle = onImageToggle)
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
    kernelPool: KernelWebViewPool? = null,
    mesid: String = "",
) {
    if (kernelPool != null && mesid.isNotEmpty() && !impersonating) {
        // 内核流式（§3.4）：整行交官方模板（头像/名字随行），120ms 节流轻量更新，
        // 流结束由 payload 权威重渲收尾；冒充草稿走下方原生轻量路径（临时预览不占池槽位）
        Column(modifier = modifier.fillMaxWidth()) {
            if (reasoning.isNotBlank()) {
                ReasoningCard(
                    text = reasoning,
                    expanded = reasoningExpanded,
                    onToggle = onReasoningToggle,
                    streaming = true,
                )
                Spacer(Modifier.size(6.dp))
            }
            MessageKernelRow(
                pool = kernelPool,
                payload = KernelMessagePayload(
                    mesid = mesid,
                    mes = text.ifEmpty { "…" },
                    chName = name,
                    isUser = false,
                    isSystem = false,
                    avatarUrl = kernelAvatarUrlOf(avatarPath),
                ),
                streamingText = text,
                modifier = Modifier.fillMaxWidth(),
                onLongPress = null,
            )
        }
    } else {
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
                        streaming = true,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreamingMarkdown(
                        content = text.ifEmpty { "…" },
                    )
                    // 呼吸圆点光标：AI 身份暖金点睛（DESIGN_SYSTEM §三.2）
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, end = 2.dp)
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = caretScale
                                scaleY = caretScale
                                this.alpha = caretAlpha
                            }
                            .background(EmberTheme.colors.ai, CircleShape),
                    )
                }
            }
        }
    }
}

/** 流式轻量渲染：流式中不跑完整 Markdown 管线（整段重解析空转，高概率被丢弃），
 *  只做粗粒度着色（标题→粗体、**粗**、*斜*、~~删~~、~下划线~、行内码、六种引号对、链接）。
 *  生成结束后由内核 renderMessage 一次性权威重渲染，视觉与最终一致。 */
@Composable
private fun StreamingMarkdown(content: String, fillWidth: Boolean = true) {
    // 颜色读 ShellTheme 推导令牌：正文=ink(main_text_color)、引用/下划线=accent(quote_text_color)、斜体=inkMute(italics_text_color)
    val c = EmberTheme.colors
    val styled = remember(content, c) {
        streamingStyledText(content, c.ink, c.accent, c.inkMute, c.accent)
    }
    Text(
        text = styled,
        style = chatTextStyle(),
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
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
    // 流式未闭合定界符补齐：**bold → **bold**，让下面的正则整段吞掉标记（否则流式中会露 `**` 等符号）
    val closed = closeStreamingDelimiters(raw)
    val cleaned = Regex("""(?m)^\s{0,3}(#{1,6})\s+(.+)$""").replace(closed) { m -> "**${m.groupValues[2]}**" }
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

/** 流式未闭合定界符补齐（App 增强，仅流式中间态；官方 1.18 流式是每 tick 全量 messageFormatting，
 *  未闭合时同样露符号。这里补上闭合符让轻量渲染器吞掉标记，最终态仍由内核全量渲染一致）。 */
private fun closeStreamingDelimiters(text: String): String {
    var out = text
    // ** 与 * 分开计数：** 优先（**bold 有一个 ** → 补 **；*italic 有一个 * → 补 *）
    val doubleStars = Regex("\\*\\*").findAll(text).count()
    val totalStars = text.count { it == '*' }
    val singleStars = totalStars - doubleStars * 2
    if (doubleStars % 2 == 1) out += "**"
    else if (singleStars % 2 == 1) out += "*"
    val doubleTilde = Regex("~~").findAll(text).count()
    val totalTilde = text.count { it == '~' }
    val singleTilde = totalTilde - doubleTilde * 2
    if (doubleTilde % 2 == 1) out += "~~"
    else if (singleTilde % 2 == 1) out += "~"
    if (text.count { it == '`' } % 2 == 1) out += "`"
    for ((open, close) in listOf(
        "\"" to "\"", "“" to "”", "«" to "»", "「" to "」", "『" to "』", "＂" to "＂",
    )) {
        if (text.count { it == open[0] } > text.count { it == close[0] }) out += close
    }
    // 链接：[text](url 未闭合 → 补 )
    val lastLinkOpen = text.lastIndexOf("](")
    if (lastLinkOpen >= 0 && text.indexOf(")", lastLinkOpen + 2) == -1) out += ")"
    return out
}

/**
 * 合并后的上下文胶囊：圆环进度 + token/上限 + 百分比（绿→黄→橙→红分级）｜世界书命中数，
 * 两端同一胶囊。数据实时取自引擎 `onPrepared`（wiResult.activated + result.counts/maxContextTokens），
 * 非 UI 模拟值。点击上下文区开预算分解；点击世界书命中区开命中面板。
 */
@Composable
private fun ContextCapsule(
    used: Int,
    max: Int,
    worldHits: Int,
    onOpenContext: () -> Unit,
    onOpenWorld: () -> Unit,
) {
    val ratio = if (max <= 0) 0f else used.toFloat() / max
    val grade = when {
        ratio >= 0.90f -> MaterialTheme.colorScheme.error
        ratio >= 0.75f -> Color(0xFFEF6C00)
        ratio >= 0.50f -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }
    // 世界书命中存在即高亮主色，无命中置灰（常驻胶囊，弱化但不消失）
    val worldColor = if (worldHits > 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // 上下文区：圆环 + 百分比 + token 明细
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.combinedClickable(onClick = onOpenContext, onLongClick = onOpenWorld),
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
        // 细分隔线：上下文 | 世界书命中 同囊区隔
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.size(8.dp))
        // 世界书命中区：书图标 + 命中数，点击开命中面板
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onOpenWorld),
        ) {
            Icon(
                FaIcons.BookOpen,
                contentDescription = "世界书命中",
                tint = worldColor,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                if (worldHits > 0) "×$worldHits" else "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (worldHits > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (worldHits > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
                Icon(FaIcons.XMark, contentDescription = "移除附件", modifier = Modifier.size(14.dp))
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

/** 思考过程：唯一的一个卡，正文上方；受控展开（流式/生成完共用同一状态），默认折叠。
 *  【待办 Commit 4】迁入内核官方 .mes_reasoning DOM，由主题 CSS 直接接管样式。
 *  streaming=true 时用轻量流式渲染（不跑完整管线，否则每 tick 全量解析卡死滑动），并限高滚动。 */
@Composable
private fun ReasoningCard(text: String, expanded: Boolean, onToggle: () -> Unit, streaming: Boolean = false) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "思考过程 ▾" else "思考过程 ▸",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium,
            )
        }
        if (expanded) {
            Spacer(Modifier.size(5.dp))
            if (streaming) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    StreamingMarkdown(content = text.ifEmpty { "…" })
                }
            } else {
                StreamingMarkdown(content = text.ifEmpty { "…" })
            }
        }
    }
}

/** 聊天正文样式：官方 font_scale 单一缩放（power_user.font_scale，默认 1）。
 *  层级/行高/字重/代码字号交还主题 CSS——内核是权威渲染管线；原生仅流式过渡态使用。 */
@Composable
private fun chatTextStyle(): androidx.compose.ui.text.TextStyle {
    val context = LocalContext.current
    val manager = remember { OfficialThemeManager.shared(context) }
    val name by manager.currentName.collectAsState()
    val scale = remember(name) { manager.shellSettings().fontScale.toFloat().coerceIn(0.25f, 3f) }
    val base = MaterialTheme.typography.bodyMedium
    return remember(scale, base) {
        base.copy(fontSize = (16f * scale).sp, lineHeight = (16f * scale * 1.55f).sp)
    }
}

/** 深色表面判断：App 已强制暗基底，此判断保留给玻璃边缘高光等明暗二态逻辑。 */
@Composable
private fun isDarkThemeSurface(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * 宿主能力白名单处理（V2 §5.3；动作面与 st-api-shim.js AppBridge/toastr 对齐）。
 * 桥回调线程经 mainHandler.post 进主线程；每支 runCatching 隔离——单能力失败不影响其他。
 */
private fun handleHostAction(context: Context, action: String, value: String) {
    when (action) {
        KernelHostAction.OPEN_LINK -> {
            // 只放行 http(s)——官方 window.open 语义收窄到浏览器可达 URL，
            // 拦掉 file:/intent:/content: 等卡内脚本借桥探测或拉起任意组件的路径
            val scheme = Uri.parse(value).scheme?.lowercase()
            if (scheme == "http" || scheme == "https") runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(context, "仅支持 http(s) 链接", Toast.LENGTH_SHORT).show()
        }
        KernelHostAction.COPY_TEXT -> {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("EmberInn", value))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
        KernelHostAction.SHARE -> {
            val payload = parseJsonPayload(value)
            val text = payload?.get("text")?.jsonPrimitive?.contentOrNull ?: value
            val title = payload?.get("title")?.jsonPrimitive?.contentOrNull
            runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(send, title ?: "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        KernelHostAction.TOAST -> {
            val payload = parseJsonPayload(value)
            val message = payload?.get("message")?.jsonPrimitive?.contentOrNull ?: value
            val type = payload?.get("type")?.jsonPrimitive?.contentOrNull ?: "info"
            // 官方 toastr：error/warning 停留更久 → LENGTH_LONG 近似
            val duration = if (type == "error" || type == "warning") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, message, duration).show()
        }
        KernelHostAction.SAVE_MEDIA -> runCatching {
            // 卡脚本常把 canvas.toDataURL() 结果直接传 saveMedia——data:URL 走解码落盘路径
            if (value.startsWith("data:", ignoreCase = true)) {
                saveDataUrlFile(context, """{"dataUrl":${JsonPrimitive(value)}}""")
                return@runCatching
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val name = URLUtil.guessFileName(value, null, null)
            dm.enqueue(
                DownloadManager.Request(Uri.parse(value))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setTitle(name)
                    .setDescription("EmberInn 媒体保存")
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name),
            )
            Toast.makeText(context, "已加入下载：$name", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
        KernelHostAction.SAVE_DATA_URL -> runCatching { saveDataUrlFile(context, value) }
            .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
        KernelHostAction.VIBRATE -> runCatching {
            val ms = value.toLongOrNull() ?: 20L
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vib.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 5_000), VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

/** AppBridge.saveDataUrl/share 的 JSON 参数解析（容错：坏 JSON 回 null 走纯文本路径） */
private fun parseJsonPayload(value: String): JsonObject? =
    runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull()

/** data:URL 解码落盘：API29+ 写 MediaStore Downloads；以下写应用私有下载目录（无存储授权兜底） */
private fun saveDataUrlFile(context: Context, payloadJson: String) {
    val payload = parseJsonPayload(payloadJson) ?: throw IllegalArgumentException("参数非 JSON")
    val dataUrl = payload["dataUrl"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("缺 dataUrl")
    val filename = (payload["filename"]?.jsonPrimitive?.contentOrNull ?: "emberinn-export.bin")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val comma = dataUrl.indexOf(',')
    if (!dataUrl.startsWith("data:") || comma < 0) throw IllegalArgumentException("非 data:URL")
    val mime = dataUrl.substring(5, comma).substringBefore(';').ifBlank { "application/octet-stream" }
    val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
    if (Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore 写入失败")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("输出流打开失败")
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        File(dir, filename).writeBytes(bytes)
    }
    Toast.makeText(context, "已保存：$filename", Toast.LENGTH_SHORT).show()
}



@Composable
private fun MessageActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
) {
    // 官方 mes_button 移动端等价：无底色小图标钮（28dp 触达 / 15dp 视觉），与正文拉开层级
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(15.dp))
    }
}

/** 菜单小节标题（对应官方 options 弹层的 hr 分组语义）。 */
@Composable
private fun MenuSectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

/**
 * 官方 past_chat_template：文件名 + 消息数(💬) + 预览 + 日期；当前聊天高亮；
 * 行内操作 改名(fa-pencil) / 导出JSONL(fa-file-export) / 导出txt(fa-file-lines) / 删除(fa-skull)。
 */
@Composable
private fun PastChatRow(
    entry: com.emberinn.app.ui.chat.ChatViewModel.PastChatEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onExportJsonl: () -> Unit,
    onExportText: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateText = remember(entry.lastDate) {
        if (entry.lastDate <= 0) "" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.lastDate))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                FaIcons.Comments,
                contentDescription = null,
                tint = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                entry.record.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry.isCurrent) {
                Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
            }
            // 行内操作（官方 past_chat_template 右侧按钮组）
            androidx.compose.material3.IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
                Icon(FaIcons.Pencil, contentDescription = "重命名", modifier = Modifier.size(14.dp))
            }
            androidx.compose.material3.IconButton(onClick = onExportJsonl, modifier = Modifier.size(30.dp)) {
                Icon(FaIcons.FileExport, contentDescription = "导出 JSONL", modifier = Modifier.size(14.dp))
            }
            androidx.compose.material3.IconButton(onClick = onExportText, modifier = Modifier.size(30.dp)) {
                Icon(FaIcons.FileLines, contentDescription = "导出纯文本", modifier = Modifier.size(14.dp))
            }
            androidx.compose.material3.IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(FaIcons.Skull, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
            }
        }
        entry.preview?.let { preview ->
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            Text(
                "${entry.messageCount} 💬  $dateText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    worldHitsCount: Int,
    contextUsage: Pair<Int, Int>?,
    onOpenWorldPanel: () -> Unit,
    onOpenContextDetail: () -> Unit,
    quickReplies: List<QuickReplySlot>,
    onQuickReply: (String) -> Unit,
    onQuickContinue: () -> Unit,
    onQuickImpersonate: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    slashCommands: List<Pair<String, String>> = emptyList(),
    impersonating: Boolean = false,
    impersonationDraft: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.MutableStateFlow(""),
    modifier: Modifier = Modifier,
) {
    // 官方冒充：流式正文实时写输入框（聊天区不显示）；
    // 每 tick 先过 clean(isImpersonate)+定界符补齐（官方 onProgressStreaming L3616），输入框不显示裸流
    val impersonationClean by impersonationDraft.collectAsState()
    // 斜杠补全：输入以 / 开头且第一个词未完成时，按已输入字母过滤（前缀优先，其次包含），最多 12 条
    val slashMatches = remember(input, slashCommands) {
        val show = input.startsWith("/") && input.length > 1 && !input.substring(1).contains(' ')
        if (!show) {
            emptyList()
        } else {
            val query = input.substring(1).lowercase()
            slashCommands
                .filter { (name, _) -> name.lowercase().startsWith(query) || name.lowercase().contains(query) }
                .sortedWith(compareBy({ !it.first.lowercase().startsWith(query) }, { it.first }))
                .take(12)
        }
    }
    // EmberDS 输入栏：ChatAreaTheme.inputBg 浮在舞台之上，lineStrong 上分界（§6.1 输入区独立配色）
    val barColor = EmberTheme.chat.inputBg ?: EmberTheme.colors.surface
    Surface(
        color = barColor,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column {
            // 发丝线：消息区/输入区分界
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EmberTheme.colors.lineStrong),
            )
            // README 状态可见：上下文占比 + 世界书命中合并为单个胶囊，常驻输入栏顶部（不占消息区）
            if (!isStreaming && contextUsage != null) {
                val (used, max) = contextUsage
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                ) {
                    ContextCapsule(
                        used = used,
                        max = max,
                        worldHits = worldHitsCount,
                        onOpenContext = onOpenContextDetail,
                        onOpenWorld = onOpenWorldPanel,
                    )
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
            if (slashMatches.isNotEmpty()) {
                // 补全弹层：Surface 投影 + 主题表面色，命令名匹配段用角色 accent 高亮
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(slashMatches, key = { _, pair -> pair.first }) { _, (name, desc) ->
                            val query = input.substring(1).lowercase()
                            val nameAnnotated = buildAnnotatedString {
                                append("/")
                                val full = "/$name"
                                val idx = full.lowercase().indexOf(query)
                                if (idx >= 0) {
                                    pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold))
                                    append(full.substring(idx, idx + query.length))
                                    pop()
                                    append(full.substring(idx + query.length))
                                } else {
                                    append(full.substring(1))
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onInputChange("/$name ") }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accent.copy(alpha = 0.16f)),
                                ) {
                                    Text("/", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    nameAnnotated,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
            val enabledReplies = quickReplies.filter { it.enabled }
            if (enabledReplies.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    items(enabledReplies, key = { it.label }) { slot ->
                        QuickReplyChip(slot.label.ifBlank { "（未命名）" }, onClick = { onQuickReply(slot.label) })
                    }
                }
            }
            // —— 作曲行（DESIGN_SYSTEM §6.2）：一体化输入卡。
            // 工具收进卡内左缘（幽灵纯图标）、正文无边框透明、发送/停止独立在卡右——
            // 三层嵌套容器收敛成"一张卡 + 一个动作"，输入区只保留一条视觉主线。——
            val chatC = EmberTheme.chat
            // ChatAreaTheme 允许皮肤缺省字段：逐项解析到 EmberDS 令牌兜底（颜色链：皮肤>令牌）
            val ccT = EmberTheme.colors
            val inputBgC = chatC.inputBg ?: ccT.surface
            val inputBorderC = chatC.inputBorder ?: ccT.line
            val inputAccentC = chatC.inputAccent ?: ccT.accent
            val placeholderC = chatC.inputPlaceholder ?: ccT.inkMute
            val buttonIconC = chatC.buttonIcon ?: ccT.inkSoft
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(inputBgC)
                        .border(0.5.dp, inputBorderC, RoundedCornerShape(24.dp)),
                ) {
                    // 卡内工具簇：附件 + 冒充 + 继续（官方 rightSendForm 的移动端等价）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                    ) {
                        EmberInputIcon(
                            onClick = onAttach,
                            icon = FaIcons.Plus,
                            contentDescription = "附件与工具",
                            tint = buttonIconC,
                            ghost = true,
                        )
                        if (!isStreaming) {
                            EmberInputIcon(
                                onClick = onQuickImpersonate,
                                icon = FaIcons.UserSecret,
                                contentDescription = "冒充用户发言",
                                tint = inputAccentC.copy(alpha = 0.85f),
                                ghost = true,
                            )
                            if (canQuickContinue) {
                                EmberInputIcon(
                                    onClick = onQuickContinue,
                                    icon = FaIcons.ArrowRight,
                                    contentDescription = "继续生成",
                                    tint = inputAccentC.copy(alpha = 0.85f),
                                    ghost = true,
                                )
                            }
                        }
                    }
                    EmberTextField(
                        value = if (impersonating) impersonationClean else input,
                        onValueChange = if (impersonating) { _ -> } else onInputChange,
                        placeholder = {
                            Text(
                                if (impersonating) "正在代写你的发言…" else "输入消息…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = placeholderC,
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 4,
                        colors = EmberTextFieldDefaults.colors(
                            cursorColor = inputAccentC,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        focusGlow = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp, max = 160.dp)
                            .padding(horizontal = 4.dp),
                    )
                }
                if (!isStreaming) {
                    val canSend = input.isNotBlank() || pendingMedia.isNotEmpty()
                    ChatSendButton(accent = inputAccentC, canSend = canSend, onSend = onSend)
                } else {
                    ChatStopButton(onStop = onStop)
                }
            }
        }
    }

}

/** 停止生成：danger 实心圆钮（EmberDS 语义色，与发送钮同尺寸对位）。 */
@Composable
private fun ChatStopButton(onStop: () -> Unit) {
    val c = EmberTheme.colors
    IconButton(onClick = onStop, modifier = Modifier.size(44.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.danger),
        ) {
            Icon(FaIcons.CircleStop, contentDescription = "停止生成", tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

/** 快捷回复胶囊（官方 Quick Reply 的 menu_button 移动端等价）：tonal 圆角小胶囊，横滑容器内使用。 */
@Composable
private fun QuickReplyChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                FaIcons.PaperPlane,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 附件与工具面板行：图标圆角块 + 标题/说明 + 右箭头，整行可点；禁用态自动降级。 */
@Composable
private fun AttachSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (enabled) {
            Icon(
                FaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 发送按钮：保留角色 seed 取色（accent 底 + 自适应亮/暗图标），升级为 38dp 圆钮 + accent 柔光。 */
@Composable
private fun ChatSendButton(accent: Color, canSend: Boolean, onSend: () -> Unit) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black.copy(alpha = 0.8f) else Color.White
    // 对角渐变替代纯色：深色主色亮端在上、浅色主色暗端在下，光照方向与停止钮/发丝线一致
    val sendBrush = if (accent.luminance() > 0.5f) {
        Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.18f)))
    } else {
        Brush.linearGradient(listOf(lerp(accent, Color.White, 0.22f), accent))
    }
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
                    if (canSend) sendBrush
                    else SolidColor(EmberTheme.colors.lineStrong),
                ),
        ) {
            Icon(
                FaIcons.PaperPlane,
                contentDescription = "发送",
                tint = if (canSend) onAccent else EmberTheme.colors.inkMute,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyChat(name: String) {
    EmptyState(
        title = "和 ${name.ifBlank { "TA" }} 打个招呼吧",
        body = "第一条消息会连同角色卡、世界书与示例对话一起发给模型",
        icon = FaIcons.BookOpen,
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

/** dryRun 分节计数标签（官方 TokenHandler 类型）。 */
/** 官方 itemizationText.html 的分组：Character Definitions / World Info / Chat History / Extensions / Bias。 */
private data class ItemizationCategory(
    val label: String,
    val color: Color,
    val tokens: Int,
    val subRows: List<Pair<String, Int>> = emptyList(),
)

private fun itemizationCategories(entry: com.emberinn.app.data.ItemizationEntry): List<ItemizationCategory> {
    val sec = entry.sections
    fun tokensOf(ids: Set<String>): Int = sec.filter { it.identifier in ids }.sumOf { it.tokens }
    fun countOf(ids: Set<String>): Int = sec.count { it.identifier in ids }
    val worldInfo = tokensOf(setOf("worldInfoBefore", "worldInfoAfter"))
    val chatHistory = sec.filter { it.identifier.isBlank() || it.identifier == "chatHistory" }.sumOf { it.tokens }
    val chatCount = sec.count { it.identifier.isBlank() || it.identifier == "chatHistory" }
    val extIds = setOf("1_memory", "2_floating_prompt", "chromadb", "3_vectors", "4_vectors_data_bank")
    val extensions = sec.filter { it.identifier in extIds }.sumOf { it.tokens }
    val bias = entry.counts["bias"] ?: 0
    val desc = tokensOf(setOf("charDescription"))
    val pers = tokensOf(setOf("charPersonality"))
    val scen = tokensOf(setOf("scenario"))
    val exm = tokensOf(setOf("dialogueExamples"))
    val exmCount = countOf(setOf("dialogueExamples"))
    val persona = tokensOf(setOf("personaDescription"))
    val system = tokensOf(setOf("instruction"))
    // 官方 storyStringTokens = finalPrompt - worldInfo - chatHistory - extensions - bias（余量即角色定义/主提示/示例等）
    val charDefs = (entry.totalTokens - worldInfo - chatHistory - extensions - bias).coerceAtLeast(0)
    return listOf(
        ItemizationCategory(
            "Character Definitions",
            Color(0xFFCD5C5C),
            charDefs,
            listOf(
                "Description" to desc,
                "Personality" to pers,
                "Scenario" to scen,
                "Examples${if (exmCount > 0) " ($exmCount)" else ""}" to exm,
                "User Persona" to persona,
                "System Prompt (Instruct)" to system,
            ),
        ),
        ItemizationCategory("World Info", Color(0xFFFFD700), worldInfo),
        ItemizationCategory(
            "Chat History${if (chatCount > 0) " ($chatCount)" else ""}",
            Color(0xFF98FB98),
            chatHistory,
        ),
        ItemizationCategory(
            "Extensions",
            Color(0xFF6495ED),
            extensions,
            listOf(
                "Summarize" to tokensOf(setOf("1_memory")),
                "Author's Note" to tokensOf(setOf("2_floating_prompt")),
                "Smart Context" to tokensOf(setOf("chromadb")),
                "Vector Chats" to tokensOf(setOf("3_vectors")),
                "Vector Data Bank" to tokensOf(setOf("4_vectors_data_bank")),
            ),
        ),
        ItemizationCategory("{{}} Bias", Color(0xFF9370DB), bias),
    )
}

/** 官方 DiffMatchPatch diff_main 的词级 App 等价：LCS 词对齐，删除红底、新增绿底；超大输入回退行级。 */
private fun wordDiffAnnotated(a: String, b: String): AnnotatedString? {
    val wa = Regex("""\s+|\S+""").findAll(a).map { it.value }.toList()
    val wb = Regex("""\s+|\S+""").findAll(b).map { it.value }.toList()
    if (wa.size.toLong() * wb.size.toLong() > 4_000_000L) return null
    val n = wa.size
    val m = wb.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (wa[i] == wb[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val out = AnnotatedString.Builder()
    val del = SpanStyle(color = Color(0xFFE57373), background = Color(0x33E57373))
    val ins = SpanStyle(color = Color(0xFF7CB342), background = Color(0x337CB342))
    var i = 0
    var j = 0
    while (i < n && j < m) {
        if (wa[i] == wb[j]) {
            out.append(wa[i])
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            out.withStyle(del) { append(wa[i]) }
            i++
        } else {
            out.withStyle(ins) { append(wb[j]) }
            j++
        }
    }
    while (i < n) { out.withStyle(del) { append(wa[i]) }; i++ }
    while (j < m) { out.withStyle(ins) { append(wb[j]) }; j++ }
    return out.toAnnotatedString()
}

/** 简单 LCS 行级 diff（词级超限时的回退；官方 DiffMatchPatch diff_main 的 App 等价，只标 +/-/空格）。 */
private fun simpleLineDiff(a: String, b: String): List<Pair<Char, String>> {
    val aa = a.lines()
    val bb = b.lines()
    val n = aa.size
    val m = bb.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (aa[i] == bb[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val out = mutableListOf<Pair<Char, String>>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        if (aa[i] == bb[j]) {
            out += ' ' to aa[i]
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            out += '-' to aa[i]
            i++
        } else {
            out += '+' to bb[j]
            j++
        }
    }
    while (i < n) { out += '-' to aa[i]; i++ }
    while (j < m) { out += '+' to bb[j]; j++ }
    return out
}

private fun promptSectionLabel(key: String): String = when (key) {
    "start_chat" -> "起始预留"
    "prompt" -> "主提示（系统）"
    "bias" -> "偏置 bias"
    "nudge" -> "续写引导 nudge"
    "jailbreak" -> "剧情后指令 jailbreak"
    "impersonate" -> "冒充 impersonate"
    "examples" -> "示例对话"
    "conversation" -> "聊天历史"
    else -> key
}

private fun isUser(el: JsonElement): Boolean {
    val v = el.jsonObject["is_user"] ?: return false
    return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
}

/**
 * 消息操作目标（索引漂移防护）：弹层/异步操作期间消息列表可能被结构性刷新
 * （翻译完成、变体替换、隐藏、书签等触发 refreshMessages），裸 index 会漂移到别的消息上。
 * 绑定打开时刻的消息身份（send_date），消费时重新定位：先按 index 快速校验，失配再按
 * send_date 全表扫描；都找不到（消息已删除）返回 null，调用方放弃操作而非写错对象。
 */
private class MsgTarget(val index: Int, el: JsonElement) {
    private val sendDate: String? = el.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull

    fun resolve(messages: List<JsonElement>): Int? {
        if (index in messages.indices) {
            val cur = messages[index].jsonObject
            if (cur["send_date"]?.jsonPrimitive?.contentOrNull == sendDate) return index
        }
        if (sendDate != null) {
            val byDate = messages.indexOfFirst { it.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull == sendDate }
            if (byDate >= 0) return byDate
        }
        return null
    }
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


/** CFG Scale 设置弹层（官方 scripts/cfg-scale.js 三档：会话/角色/全局 + 合并来源/深度/分隔符）。 */
@Composable
private fun CfgScaleSheet(
    initial: Triple<CfgPromptEngine.CfgGlobal, CfgPromptEngine.CfgChara?, CfgPromptEngine.CfgChat>,
    onDismiss: () -> Unit,
    onSave: (CfgPromptEngine.CfgGlobal, CfgPromptEngine.CfgChara, CfgPromptEngine.CfgChat) -> Unit,
) {
    val (global0, chara0, chat0) = initial
    var globalScale by remember { mutableStateOf(global0.guidanceScale.toFloat()) }
    var globalNeg by remember { mutableStateOf(global0.negativePrompt) }
    var globalPos by remember { mutableStateOf(global0.positivePrompt) }
    var charaScale by remember { mutableStateOf(chara0?.guidanceScale?.toFloat() ?: 1f) }
    var charaNeg by remember { mutableStateOf(chara0?.negativePrompt.orEmpty()) }
    var charaPos by remember { mutableStateOf(chara0?.positivePrompt.orEmpty()) }
    var chatScale by remember { mutableStateOf(chat0.guidanceScale?.toFloat() ?: 1f) }
    var chatNeg by remember { mutableStateOf(chat0.negativePrompt) }
    var chatPos by remember { mutableStateOf(chat0.positivePrompt) }
    var combine by remember { mutableStateOf(chat0.promptCombine.toMutableSet()) }
    var depth by remember { mutableStateOf(chat0.promptInsertionDepth.toString()) }
    var separator by remember { mutableStateOf(chat0.promptSeparator.orEmpty()) }
    var groupCharOverride by remember { mutableStateOf(chat0.groupchatIndividualChars) }

    EmberBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("CFG Scale（引导缩放）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "强度 >1 时生效：负向提示不进消息，正向提示按深度注入；openai 不发送 guidance。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))

            CfgSectionTitle("会话 CFG（本聊天）")
            CfgScaleRow("强度", chatScale, 1f..4f) { chatScale = it }
            EmberTextField(value = chatNeg, onValueChange = { chatNeg = it }, label = { Text("负向提示（不进提示词）") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            EmberTextField(value = chatPos, onValueChange = { chatPos = it }, label = { Text("正向提示（按深度注入）") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            CfgSectionTitle("角色 CFG（本角色）")
            CfgScaleRow("强度", charaScale, 1f..4f) { charaScale = it }
            EmberTextField(value = charaNeg, onValueChange = { charaNeg = it }, label = { Text("负向提示") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            EmberTextField(value = charaPos, onValueChange = { charaPos = it }, label = { Text("正向提示") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            CfgSectionTitle("全局 CFG")
            CfgScaleRow("强度", globalScale, 1f..4f) { globalScale = it }
            EmberTextField(value = globalNeg, onValueChange = { globalNeg = it }, label = { Text("负向提示") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            EmberTextField(value = globalPos, onValueChange = { globalPos = it }, label = { Text("正向提示") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Text("合并来源（cfg_prompt_combine）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                listOf(0 to "会话", 1 to "角色", 2 to "全局").forEach { (v, label) ->
                    FilterChip(
                        selected = v in combine,
                        onClick = { combine = (if (v in combine) combine - v else combine + v).toMutableSet() },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("插入深度", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                EmberTextField(value = depth, onValueChange = { depth = it.filter { c -> c.isDigit() } }, label = { Text("0=追加末条") }, singleLine = true, modifier = Modifier.width(150.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("分隔符（JSON 字符串）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                EmberTextField(value = separator, onValueChange = { separator = it }, label = { Text("例：\"\\n\"") }, singleLine = true, modifier = Modifier.width(150.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("群聊使用角色 CFG", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                EmberSwitch(checked = groupCharOverride, onCheckedChange = { groupCharOverride = it })
            }
            Spacer(Modifier.height(16.dp))
            EmberPrimaryButton(
                label = "保存并生效",
                onClick = {
                    onSave(
                        CfgPromptEngine.CfgGlobal(globalScale.toDouble(), globalNeg, globalPos),
                        CfgPromptEngine.CfgChara(
                            name = chara0?.name ?: "",
                            guidanceScale = if (charaScale > 1f) charaScale.toDouble() else null,
                            negativePrompt = charaNeg,
                            positivePrompt = charaPos,
                        ),
                        CfgPromptEngine.CfgChat(
                            guidanceScale = if (chatScale > 1f) chatScale.toDouble() else null,
                            negativePrompt = chatNeg,
                            positivePrompt = chatPos,
                            promptCombine = combine.toList().sorted(),
                            groupchatIndividualChars = groupCharOverride,
                            promptInsertionDepth = depth.toIntOrNull() ?: 1,
                            promptSeparator = separator.ifBlank { null },
                        ),
                    )
                },
                expandWidth = true,
            )
        }
    }
}

@Composable
private fun CfgSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CfgScaleRow(label: String, value: Float, range: kotlin.ranges.ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "%.2f".format(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
    EmberSlider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}


/** Token 概率查看器（官方 logprobsViewer 的移动端等价）：点击 token 显示备选；数据仅内存保留最近一条。 */
@Composable
private fun LogprobsSheet(
    logprobs: List<LogprobsEngine.TokenLogprobs>?,
    onDismiss: () -> Unit,
) {
    var selected by remember(logprobs) { mutableStateOf<Int?>(null) }
    EmberBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Token 概率（logprobs）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "点击 token 查看备选；数据来自本条 AI 回复的流式响应（内存保留最近一条）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (logprobs.isNullOrEmpty()) {
                Text(
                    "没有数据：需在 提供商与模型 → 请求 token 概率 开启后重新生成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("${logprobs.size} 个 token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    logprobs.forEachIndexed { i, lp ->
                        val sel = selected == i
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selected = if (sel) null else i },
                        ) {
                            Text(
                                lp.token.ifBlank { "␣" },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                selected?.let { idx ->
                    Spacer(Modifier.height(16.dp))
                    val lp = logprobs[idx]
                    Text(
                        "备选（${lp.token.ifBlank { "␣" }}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    lp.topLogprobs.sortedBy { it.second }.forEach { (tok, prob) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                tok.ifBlank { "␣" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "%.2f".format(prob),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
