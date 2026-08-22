package com.emberinn.app.ui.design.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme

/** 菜单行语义色。 */
enum class SheetRowTone { Neutral, Danger }

/**
 * 底部弹层（§6.1 Sheet）：底部滑入 + 背景压暗 240ms（§七）；surface2 面 + cornerSheet 圆角。
 * 自绘实现，不依赖 M3 experimental API。
 */
@Composable
fun EmberBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val ms = if (EmberTheme.reducedMotion) EmberTheme.motion.reducedMs else EmberTheme.motion.sheetMs
    BackHandler(enabled = visible) { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        // 背景压暗
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(ms)),
            exit = fadeOut(tween(ms)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.bgTint.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        // 底部面板
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(ms)) { it } + fadeIn(tween(ms)),
            exit = slideOutVertically(tween(ms)) { it } + fadeOut(tween(ms)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(topStart = s.cornerSheet, topEnd = s.cornerSheet))
                    .background(c.surface)
                    .navigationBarsPadding(),
            ) {
                // 拖拽指示条
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .height(4.dp)
                        .widthIn(min = 36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.lineStrong),
                )
                if (title != null) {
                    SectionTitle(
                        title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * 居中对话框（§6.1 Dialog）：surface 面浮层，scale 入场。
 */
@Composable
fun EmberDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = EmberTheme.colors
    val s = EmberTheme.shapes
    val ms = if (EmberTheme.reducedMotion) EmberTheme.motion.reducedMs else EmberTheme.motion.sheetMs
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(ms)),
        exit = fadeOut(tween(ms)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.bgTint.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp - 48.dp)
                    .clip(RoundedCornerShape(s.cornerCard))
                    .background(c.surface)
                    .padding(vertical = 16.dp),
            ) {
                if (title != null) {
                    SectionTitle(
                        title,
                        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp),
                    )
                }
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/** 弹层菜单行：图标 + 文字 + 可选尾部说明（长按菜单等复用）。 */
@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: String? = null,
    tone: SheetRowTone = SheetRowTone.Neutral,
) {
    val c = EmberTheme.colors
    val dangerous = tone == SheetRowTone.Danger
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = if (dangerous) c.danger else c.inkSoft,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
        InkText(label, tier = if (dangerous) InkTier.Primary else InkTier.Primary)
        Box(Modifier.weight(1f))
        if (trailing != null) InkText(trailing, tier = InkTier.Mute, sizeSp = 13f)
    }
}
