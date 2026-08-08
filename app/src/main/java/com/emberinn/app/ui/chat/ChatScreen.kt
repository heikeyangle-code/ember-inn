@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private sealed interface ChatItem {
    data class Message(val index: Int, val element: JsonElement) : ChatItem
    data object Streaming : ChatItem
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

    var input by rememberSaveable { mutableStateOf("") }
    var menuMessageIndex by remember { mutableStateOf<Int?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val accent = vm.accentColor?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
    val items = remember(messages, isStreaming, streamingText) {
        buildList {
            messages.forEachIndexed { i, el -> add(ChatItem.Message(i, el)) }
            if (isStreaming) add(ChatItem.Streaming)
        }
    }
    val lastAiIndex = messages.indexOfLast { el -> !isUser(el) }

    // README 手势守则：系统返回键/侧滑返回 = 回到列表
    BackHandler(onBack = onBack)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(streamingText) {
        if (isStreaming && streamingText.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            name = name,
            avatarPath = vm.avatarPath,
            accent = accent,
            onBack = onBack,
            onMenu = { showClearConfirm = true },
        )

        if (!providerConfigured) {
            UnconfiguredBanner(onOpenSettings = onOpenSettings)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item { EmptyChat(name = name, accent = accent) }
            }
            itemsIndexed(items, key = { _, item -> when (item) {
                is ChatItem.Message -> "m-${item.index}"
                ChatItem.Streaming -> "streaming"
            } }) { _, item ->
                when (item) {
                    is ChatItem.Message -> {
                        val el = item.element
                        val isUserMsg = isUser(el)
                        val text = textOf(el)
                        val showActions = !isStreaming && item.index == lastAiIndex && !isUserMsg
                        MessageRow(
                            isUser = isUserMsg,
                            text = text,
                            name = nameOf(el, isUserMsg),
                            time = timeOf(el),
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
                        name = name,
                        avatarPath = vm.avatarPath,
                        accent = accent,
                    )
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

        ChatInputBar(
            input = input,
            onInputChange = { input = it },
            isStreaming = isStreaming,
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    vm.send(text)
                    input = ""
                }
            },
            onStop = { vm.stop() },
            onAttach = {
                Toast.makeText(context, "附件功能开发中", Toast.LENGTH_SHORT).show()
            },
            onVoice = {
                Toast.makeText(context, "语音输入开发中", Toast.LENGTH_SHORT).show()
            },
        )
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
                    MenuRow(Icons.Filled.ContentCopy, "复制") {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        menuMessageIndex = null
                    }
                    if (!isUserMsg) {
                        MenuRow(Icons.Filled.Refresh, "重新生成") {
                            vm.regenerate(); menuMessageIndex = null
                        }
                        MenuRow(Icons.Filled.FastForward, "继续生成") {
                            vm.continueGeneration(); menuMessageIndex = null
                        }
                    }
                    MenuRow(Icons.Filled.Delete, "删除这条消息", danger = true) {
                        vm.deleteMessage(index); menuMessageIndex = null
                    }
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
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 36)
            Spacer(Modifier.size(10.dp))
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
                    text = "会话中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = "还没配置模型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
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
    name: String,
    time: String,
    avatarPath: String?,
    accent: Color,
    showActions: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 28)
            Spacer(Modifier.size(8.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.size(3.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (isUser) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Markdown(
                            content = text,
                            modifier = Modifier.heightIn(max = 420.dp),
                            imageTransformer = Coil3ImageTransformerImpl,
                            components = markdownComponents(
                                codeBlock = highlightedCodeBlock,
                                codeFence = highlightedCodeFence,
                            ),
                        )
                    }
                }
            }
            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmallAction(Icons.Filled.ContentCopy, "复制", onCopy)
                    SmallAction(Icons.Filled.Refresh, "重新生成", onRegenerate)
                    SmallAction(Icons.Filled.FastForward, "继续", onContinue)
                    SmallAction(Icons.Filled.Delete, "删除", onDelete)
                }
            }
        }
    }
}

@Composable
private fun StreamingRow(text: String, name: String, avatarPath: String?, accent: Color) {
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
        RoleAvatar(avatarPath = avatarPath, name = name, accent = accent, size = 28)
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(3.dp))
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Markdown(
                        content = text.ifEmpty { "…" },
                        modifier = Modifier.heightIn(max = 420.dp),
                        imageTransformer = Coil3ImageTransformerImpl,
                        components = markdownComponents(
                            codeBlock = highlightedCodeBlock,
                            codeFence = highlightedCodeFence,
                        ),
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

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
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
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Filled.Add, contentDescription = "附件 / 语音")
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("输入消息…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 160.dp),
            )
            if (!isStreaming) {
                IconButton(onClick = onVoice) {
                    Icon(Icons.Filled.Mic, contentDescription = "语音输入")
                }
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Send, contentDescription = "发送", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "停止生成", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(name: String, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", style = MaterialTheme.typography.displayMedium, color = accent)
        Spacer(Modifier.size(12.dp))
        Text(
            "和 ${name.ifBlank { "TA" }} 打个招呼吧",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "第一条消息会连同角色卡、世界书与示例对话一起发给模型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}

private fun isUser(el: JsonElement): Boolean {
    val v = el.jsonObject["is_user"] ?: return false
    return v.jsonPrimitive.let { it.booleanOrNull ?: (it.content == "true") }
}

private fun textOf(el: JsonElement): String =
    el.jsonObject["mes"]?.jsonPrimitive?.contentOrNull ?: ""

private fun nameOf(el: JsonElement, isUser: Boolean): String =
    el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() } ?: if (isUser) "我" else "助手"

private fun timeOf(el: JsonElement): String {
    val raw = el.jsonObject["send_date"]?.jsonPrimitive?.contentOrNull ?: return ""
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}
