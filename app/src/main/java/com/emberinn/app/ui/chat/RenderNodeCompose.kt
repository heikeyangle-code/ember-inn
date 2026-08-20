package com.emberinn.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.emberinn.engine.render.CssLength
import com.emberinn.engine.render.HtmlSanitizerEngine
import com.emberinn.engine.render.InteractiveKind
import com.emberinn.engine.render.RenderNode
import com.emberinn.engine.render.RenderStyleResolver
import com.emberinn.engine.render.ResolvedStyle
import com.emberinn.engine.render.RgbaColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashMap

// ============================================================
// 路线 A：RenderNode → Compose UI 映射
// 应用层职责：将引擎层产出的 RenderNode 树递归映射为 Compose 组件。
// 引擎层（消毒/样式解析/树构建）与 UI 层单向依赖，本文件不反向依赖引擎外的 UI 逻辑。
// ============================================================

/** 入口：渲染整棵 RenderNode 树（根为消毒引擎合成的 body，直接渲染其子节点）。 */
@Composable
fun RenderNodeTree(
    root: RenderNode,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val children = when (root) {
        is RenderNode.Element -> if (root.tag == "body") root.children else listOf(root)
        else -> listOf(root)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        children.forEach { node ->
            renderNode(node, textColor, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun renderNode(
    node: RenderNode,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is RenderNode.Text -> Text(node.text, color = textColor, modifier = modifier)
        is RenderNode.Element -> renderElement(node, textColor, modifier)
    }
}

@Composable
private fun renderElement(
    node: RenderNode.Element,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (node.interactive) {
        InteractiveKind.Details -> DetailsExpandable(node, textColor, modifier)
        InteractiveKind.Summary -> renderInline(node, textColor, modifier)
        InteractiveKind.Link -> RenderLink(node, textColor, modifier)
        InteractiveKind.Image -> RenderImage(node, modifier)
        InteractiveKind.Video -> RenderMedia(node, isAudio = false, modifier)
        InteractiveKind.Audio -> RenderMedia(node, isAudio = true, modifier)
        InteractiveKind.InputCheckbox -> RenderCheckbox(node, modifier)
        InteractiveKind.None -> {
            if (isBlockElement(node)) renderBlock(node, textColor, modifier)
            else renderInline(node, textColor, modifier)
        }
    }
}

private fun isBlockElement(node: RenderNode.Element): Boolean =
    node.style.isBlock || node.tag in BLOCK_TAGS

private val BLOCK_TAGS = setOf(
    "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre",
    "table", "caption", "tr", "td", "th", "tbody", "thead", "tfoot", "hr", "details",
    "summary", "section", "article", "aside", "header", "footer", "nav", "main", "address",
    "figure", "figcaption", "fieldset", "form", "center", "dl", "dt", "dd", "menu", "dir",
    "dialog",
)

// ============================================================
// 块级元素渲染
// ============================================================

@Composable
private fun renderBlock(
    node: RenderNode.Element,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val style = node.style
    val bgColor = style.backgroundColor?.toComposeColor()
    val borderStroke = style.toBorderStroke()
    val radius = style.borderRadius?.toDp(0.dp)
    val margin = style.margin.toPaddingValues()
    val padding = style.padding.toPaddingValues()
    val radiusShape = if (radius != null && radius > 0.dp) RoundedCornerShape(radius) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(margin)
            .then(bgColor?.let { Modifier.background(it) } ?: Modifier)
            .then(borderStroke?.let { Modifier.border(it) } ?: Modifier)
            .then(if (radiusShape != null) Modifier.clip(radiusShape) else Modifier)
            .padding(padding),
    ) {
        renderChildren(node.children, textColor, style)
    }
}

// ============================================================
// 内联元素渲染（Text + AnnotatedString）
// ============================================================

@Composable
private fun renderInline(
    node: RenderNode.Element,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val textStyle = node.style.toTextStyle(textColor)
    val annotated = remember(node) {
        buildAnnotatedString { appendInlineTree(node, textColor) }
    }
    if (annotated.isNotEmpty()) {
        Text(
            text = annotated,
            style = textStyle,
            modifier = modifier,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 递归把节点树（含自身样式）追加为带样式的 AnnotatedString。 */
private fun AnnotatedString.Builder.appendInlineTree(
    node: RenderNode,
    textColor: Color,
) {
    when (node) {
        is RenderNode.Text -> append(node.text)
        is RenderNode.Element -> {
            val span = node.style.toSpanStyle(textColor)
            if (node.children.isEmpty()) return  // 自闭合（br 等）无文本
            withStyle(span) {
                node.children.forEach { appendInlineTree(it, textColor) }
            }
        }
    }
}

// ============================================================
// Details / Summary 可折叠组件（路线 A 原生交互）
// ============================================================

@Composable
private fun DetailsExpandable(
    node: RenderNode.Element,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(node) { mutableStateOf(node.isOpen) }
    val summary = node.children.firstOrNull {
        it is RenderNode.Element && it.interactive == InteractiveKind.Summary
    }
    val content = node.children.filter { it !== summary }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▼" else "▶",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.width(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            if (summary is RenderNode.Element) {
                renderInline(summary, textColor, Modifier.weight(1f))
            } else {
                Text(
                    text = "详细信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
            ) {
                content.forEach { child ->
                    renderNode(child, textColor, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ============================================================
// 链接组件
// ============================================================

@Composable
private fun RenderLink(
    node: RenderNode.Element,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val href = node.href
    val context = LocalContext.current
    val textStyle = node.style.toTextStyle(textColor)
        .copy(textDecoration = TextDecoration.Underline)
    val annotated = remember(node) {
        buildAnnotatedString { appendInlineTree(node, textColor) }
    }
    if (annotated.isNotEmpty()) {
        Text(
            text = annotated,
            style = textStyle,
            modifier = modifier.clickable(enabled = href != null) {
                if (href != null && (href.startsWith("https://") || href.startsWith("http://"))) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(href))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

// ============================================================
// 图片组件
// ============================================================

@Composable
private fun RenderImage(
    node: RenderNode.Element,
    modifier: Modifier = Modifier,
) {
    val src = node.src
    if (src.isNullOrEmpty()) return
    val alt = node.alt
    val maxHeight = node.style.height?.toDp(320.dp) ?: 320.dp
    val model = remember(src) {
        when {
            src.startsWith("data:") -> src
            src.startsWith("file://") -> File(src.removePrefix("file://"))
            src.startsWith("/") -> File(src)
            else -> src
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .build(),
        contentDescription = alt ?: "图片",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(8.dp)),
    )
}

// ============================================================
// 音视频组件
// ============================================================

@Composable
private fun RenderMedia(
    node: RenderNode.Element,
    isAudio: Boolean,
    modifier: Modifier = Modifier,
) {
    val src = node.src
    if (src.isNullOrEmpty()) return
    val context = LocalContext.current
    val player = remember(src) {
        ExoPlayer.Builder(context).build().apply {
            val uri = when {
                src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://") ->
                    Uri.parse(src)
                else -> Uri.fromFile(File(src))
            }
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isAudio) 56.dp else 120.dp, max = 320.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

// ============================================================
// 复选框组件
// ============================================================

@Composable
private fun RenderCheckbox(
    node: RenderNode.Element,
    modifier: Modifier = Modifier,
) {
    val checked = node.isChecked
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

// ============================================================
// 子节点集合渲染（块级容器内）
// ============================================================

@Composable
private fun renderChildren(
    children: List<RenderNode>,
    textColor: Color,
    parentStyle: ResolvedStyle,
) {
    if (children.isEmpty()) return
    val allInline = children.all { isInlineNode(it) }
    if (allInline) {
        flushInlineRun(children, textColor, parentStyle)
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            val pending = mutableListOf<RenderNode>()
            for (child in children) {
                if (isInlineNode(child)) {
                    pending += child
                } else {
                    if (pending.isNotEmpty()) {
                        flushInlineRun(pending.toList(), textColor, parentStyle)
                        pending.clear()
                    }
                    renderNode(child, textColor, Modifier.fillMaxWidth())
                }
            }
            if (pending.isNotEmpty()) flushInlineRun(pending.toList(), textColor, parentStyle)
        }
    }
}

private fun isInlineNode(node: RenderNode): Boolean = when (node) {
    is RenderNode.Text -> true
    is RenderNode.Element -> !isBlockElement(node) && node.interactive == InteractiveKind.None
}

/** 把一串相邻内联节点合并成一段带样式的 Text。 */
@Composable
private fun flushInlineRun(
    nodes: List<RenderNode>,
    textColor: Color,
    parentStyle: ResolvedStyle,
) {
    val annotated = remember(nodes) {
        buildAnnotatedString {
            nodes.forEach { appendInlineTree(it, textColor) }
        }
    }
    if (annotated.isNotEmpty()) {
        Text(
            text = annotated,
            style = parentStyle.toTextStyle(textColor),
            modifier = Modifier.fillMaxWidth(),
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

// ============================================================
// 样式 → Compose 转换
// ============================================================

private fun RgbaColor.toComposeColor(): Color = Color(r, g, b, a)

private fun CssLength.toDp(default: Dp): Dp = when (this) {
    is CssLength.Px -> value.dp
    is CssLength.Percent -> default * value / 100f
    CssLength.Auto -> 0.dp
}

private fun com.emberinn.engine.render.CssBox.toPaddingValues(): PaddingValues =
    PaddingValues(
        start = left?.toDp(0.dp) ?: 0.dp,
        top = top?.toDp(0.dp) ?: 0.dp,
        end = right?.toDp(0.dp) ?: 0.dp,
        bottom = bottom?.toDp(0.dp) ?: 0.dp,
    )

private fun ResolvedStyle.toBorderStroke(): BorderStroke? {
    val w = border.width?.toDp(0.dp) ?: return null
    if (w <= 0.dp) return null
    val c = border.color?.toComposeColor() ?: Color.Gray
    return BorderStroke(w, SolidColor(c))
}

private fun ResolvedStyle.toSpanStyle(textColor: Color): SpanStyle {
    val mono = fontFamily?.any { "monospace" in it.lowercase() } == true
    return SpanStyle(
        color = color?.toComposeColor() ?: textColor,
        fontSize = fontSizePx?.let { it.sp } ?: TextUnit.Unspecified,
        fontWeight = fontWeight?.let { FontWeight(it) },
        fontStyle = if (fontStyle == "italic") FontStyle.Italic else null,
        textDecoration = textDecoration?.toTextDecoration(),
        fontFamily = if (mono) FontFamily.Monospace else null,
        background = backgroundColor?.toComposeColor() ?: Color.Unspecified,
    )
}

/** Compose 1.11 TextStyle 主构造（spanStyle/paragraphStyle 版）为 internal，这里用公开的 color 版构造。 */
private fun ResolvedStyle.toTextStyle(textColor: Color): TextStyle = TextStyle(
    color = color?.toComposeColor() ?: textColor,
    fontSize = fontSizePx?.let { it.sp } ?: TextUnit.Unspecified,
    fontWeight = fontWeight?.let { FontWeight(it) },
    fontStyle = if (fontStyle == "italic") FontStyle.Italic else null,
    textDecoration = textDecoration?.toTextDecoration(),
    textAlign = textAlign?.toTextAlign() ?: TextAlign.Unspecified,
    lineHeight = lineHeight?.let { (fontSizePx ?: 16f) * it }?.sp ?: TextUnit.Unspecified,
)

private fun String?.toTextDecoration(): TextDecoration? {
    if (this == null) return null
    val parts = split(" ").toSet()
    val list = mutableListOf<TextDecoration>()
    if ("underline" in parts) list += TextDecoration.Underline
    if ("line-through" in parts) list += TextDecoration.LineThrough
    return if (list.isEmpty()) null else TextDecoration.combine(list)
}

private fun String?.toTextAlign(): TextAlign? = when (this) {
    "left" -> TextAlign.Start
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    "justify" -> TextAlign.Justify
    else -> null
}

// ============================================================
// 静态 HTML 检测（内容分流：路线 A / 路线 B）
// ============================================================

/** 判断 HTML 是否含脚本依赖。false = 动态（走路线 B WebView）；true = 静态（走路线 A 原生）。 */
fun isStaticHtml(html: String): Boolean {
    if (Regex("<script\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)) return false
    if (Regex("\\son\\w+\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(html)) return false
    if (html.lowercase().contains("javascript:")) return false
    return true
}

// ============================================================
// 路线 A 入口：静态 HTML → 原生渲染（解析 + 缓存 + 组合）
// ============================================================

/** 路线 A 的 HTML → RenderNode 解析结果 LRU 缓存（纯 JVM，按内容键，滚回直出）。 */
object HtmlRenderCache {
    private const val MAX_ENTRIES = 48
    private val cache = object : LinkedHashMap<String, RenderNode>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RenderNode>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): RenderNode? = cache[key]

    @Synchronized
    fun put(key: String, node: RenderNode) {
        cache[key] = node
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

/** 静态 HTML → RenderNode 树（消毒 + 样式块 + 树构建，全部无 UI 依赖，可后台线程执行）。
 *  外部媒体默认放行（用户要求放开网络）；显式传 false 时才按官方 forbid_external_media 删除外部媒体。 */
fun parseStaticHtml(html: String, externalMediaAllowed: Boolean = true): RenderNode {
    val config = HtmlSanitizerEngine.Config(externalMediaAllowed = externalMediaAllowed)
    val result = HtmlSanitizerEngine.sanitize(html, config)
    return RenderStyleResolver.resolve(result.root, result.styleRules)
}

/**
 * 路线 A 入口：把静态 HTML 渲染成原生 UI 树。
 * 解析（消毒/样式块/树构建）在 Dispatchers.Default 执行并缓存；首帧先给非零占位避免滚回闪空。
 * 外部媒体默认放行（用户要求）；显式传 false 时才按官方 forbid_external_media 语义删除外部媒体节点。
 */
@Composable
fun StaticHtmlContent(
    html: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    externalMediaAllowed: Boolean = true,
) {
    var state by remember(html) { mutableStateOf<RenderNode?>(HtmlRenderCache.get(html)) }
    LaunchedEffect(html, externalMediaAllowed) {
        if (state == null) {
            val node = withContext(Dispatchers.Default) { parseStaticHtml(html, externalMediaAllowed) }
            HtmlRenderCache.put(html, node)
            state = node
        }
    }
    val node = state
    if (node != null) {
        RenderNodeTree(root = node, textColor = textColor, modifier = modifier)
    } else {
        // 解析占位：保持非零高度，避免“内容已解析但外层给零空间”的假空白
        Text(" ", color = Color.Transparent, modifier = modifier.height(24.dp))
    }
}