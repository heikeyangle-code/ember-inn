package com.emberinn.app.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 气泡（§6.1）：AI 金描边身份 / 用户中性面。消息正文本体走 WebView 内核，
 * 这对组件用于预览、系统提示、简单文本等原生路径。
 */
@Composable
fun AiBubble(
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(s.cornerBubble)
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.surface)
            .then(if (glowing) Modifier.breathingGlow(c.aiSoft, s.cornerBubble) else Modifier.border(0.5.dp, c.aiSoft, shape))
            .padding(12.dp),
    ) { content() }
}

@Composable
fun UserBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val shape = RoundedCornerShape(s.cornerBubble)
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.accentBg)
            .border(0.5.dp, c.line, shape)
            .padding(12.dp),
    ) { content() }
}

/**
 * 思考折叠面板（ReasoningPanel）：surfaceSink 凹陷 + 弱化标题，默认收起。
 */
@Composable
fun ReasoningPanel(
    header: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    var expanded by rememberSaveable(header) { mutableStateOf(initiallyExpanded) }
    val shape = RoundedCornerShape(s.cornerCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceSink)
            .border(0.5.dp, c.line, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            AiText(header.take(64), alpha = 0.85f)
            Box(Modifier.weight(1f))
            Icon(
                com.emberinn.app.ui.icons.FaIcons.ChevronDown,
                contentDescription = null,
                tint = c.inkMute,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(14.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
    }
}
