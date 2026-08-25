package com.emberinn.app.ui.design.components

import com.emberinn.app.ui.design.components.ShellSheet
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

// ShellSheet 统一实现在 ShellKit.kt（此前此处半成品重载与 ShellKit 版同签名冲突，
// 且引用未定义的 visible 变量——已删除，全局唯一实现避免重载歧义）

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
