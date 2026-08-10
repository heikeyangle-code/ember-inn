package com.emberinn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.theme.LocalVibe
import com.skydoves.cloudy.Sky
import com.skydoves.cloudy.cloudy

/**
 * README UI 质感升级：高级输入框（全局替换 OutlinedTextField）。
 * 无边框 tonal 容器（低饱和表面），圆角跟随主题大圆角，聚焦时主色光标 + 半透明容器加深；
 * 比 M3 默认描边输入框更柔和、更“玻璃感”，深浅主题都可用。
 */
@Composable
fun EmberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = MaterialTheme.shapes.large,
    colors: TextFieldColors = EmberTextFieldDefaults.colors(),
    /** 聚焦光环颜色：null=不加光环；默认主题主色，聊天输入框传角色 seed 的 accent。 */
    focusGlow: Color? = MaterialTheme.colorScheme.primary,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // 光环随聚焦淡入（180ms），未聚焦时完全透明、宽度 0，不占布局
    val glowAlpha by animateFloatAsState(
        targetValue = if (focusGlow != null && focused) 1f else 0f,
        animationSpec = tween(180),
        label = "emberFieldGlow",
    )
    val ringShape = shape
    Box(
        modifier = modifier
            .then(
                if (focusGlow != null) {
                    Modifier
                        .border(
                            width = if (glowAlpha > 0f) 1.5.dp else 0.dp,
                            color = focusGlow.copy(alpha = 0.9f * glowAlpha),
                            shape = ringShape,
                        )
                        .emberShadow(
                            color = focusGlow.copy(alpha = 0.42f),
                            radius = 10.dp,
                            spread = 1.dp,
                            offset = DpOffset.Zero,
                            alpha = 0.9f * glowAlpha,
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = source,
            shape = ringShape,
            colors = colors,
        )
    }
}

/** 高级输入框配色：无边框、低饱和 tonal 容器、主色光标，错误态保留 M3 语义色。 */
object EmberTextFieldDefaults {
    @Composable
    fun colors(
        focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        unfocusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.34f),
        disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.14f),
        errorContainerColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        focusedIndicatorColor: Color = Color.Transparent,
        unfocusedIndicatorColor: Color = Color.Transparent,
        disabledIndicatorColor: Color = Color.Transparent,
        errorIndicatorColor: Color = Color.Transparent,
        cursorColor: Color = MaterialTheme.colorScheme.primary,
        errorCursorColor: Color = MaterialTheme.colorScheme.error,
    ): TextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = focusedContainerColor,
        unfocusedContainerColor = unfocusedContainerColor,
        disabledContainerColor = disabledContainerColor,
        errorContainerColor = errorContainerColor,
        cursorColor = cursorColor,
        errorCursorColor = errorCursorColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        disabledIndicatorColor = disabledIndicatorColor,
        errorIndicatorColor = errorIndicatorColor,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorLabelColor = MaterialTheme.colorScheme.error,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * README 浮层玻璃：玻璃悬浮按钮（首页导入卡 / 聊天列表新建会话共用）。
 * 静态背板模糊 + 边缘高光 + 彩色阴影；关闭背景模糊时退回纯色容器。
 */
@Composable
fun EmberGlassFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    sky: Sky,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val fabContext = LocalContext.current
    Box(
        modifier = modifier
            .size(56.dp)
            .emberShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                radius = 12.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.18f + 0.12f * LocalVibe.current.glow,
            )
            .clip(RoundedCornerShape(18.dp))
            .glassEdgeHighlight(dark = dark, atTop = true)
            .then(
                if (AppearancePrefs.backgroundBlur(fabContext)) {
                    Modifier.cloudy(sky = sky, radius = AppearancePrefs.blurStrength(fabContext).coerceAtLeast(1), tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f))
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * README UI 质感升级：输入区 tonal 圆形按钮（快捷工具/附件/语音等共用）。
 * 低饱和容器 + 主色图标，禁用态自动降级；语义与 IconButton 相同。
 */
@Composable
fun EmberInputIcon(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = tint,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.28f),
            disabledContentColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

/**
 * README UI 质感升级：高级底部栏（全局替换 ModalBottomSheet）。
 * 顶部 28dp 大圆角 + 拖拽把手 + 低对比表面，弹层更精致；交互语义不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            )
        },
        content = content,
    )
}
