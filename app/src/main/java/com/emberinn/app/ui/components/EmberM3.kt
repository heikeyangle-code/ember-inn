package com.emberinn.app.ui.components

import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.Sky
import com.emberinn.app.ui.icons.FaIcons

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
    focusGlow: Color? = EmberTheme.colors.accent,
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
                    // 只保留贴合主题的聚焦描边光条；外圈 emberShadow 炫光已按用户要求移除
                    Modifier
                        .border(
                            width = if (glowAlpha > 0f) 1.5.dp else 0.dp,
                            color = focusGlow.copy(alpha = 0.9f * glowAlpha),
                            shape = ringShape,
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
        focusedContainerColor: Color = EmberTheme.colors.surface2.copy(alpha = 0.58f),
        unfocusedContainerColor: Color = EmberTheme.colors.surface2.copy(alpha = 0.34f),
        disabledContainerColor: Color = EmberTheme.colors.surface2.copy(alpha = 0.14f),
        errorContainerColor: Color = EmberTheme.colors.danger.copy(alpha = 0.45f),
        focusedIndicatorColor: Color = Color.Transparent,
        unfocusedIndicatorColor: Color = Color.Transparent,
        disabledIndicatorColor: Color = Color.Transparent,
        errorIndicatorColor: Color = Color.Transparent,
        cursorColor: Color = EmberTheme.colors.accent,
        errorCursorColor: Color = EmberTheme.colors.danger,
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
        focusedLabelColor = EmberTheme.colors.accent,
        unfocusedLabelColor = EmberTheme.colors.inkMute,
        errorLabelColor = EmberTheme.colors.danger,
        focusedPlaceholderColor = EmberTheme.colors.inkMute.copy(alpha = 0.7f),
        unfocusedPlaceholderColor = EmberTheme.colors.inkMute.copy(alpha = 0.55f),
        focusedLeadingIconColor = EmberTheme.colors.accent,
        unfocusedLeadingIconColor = EmberTheme.colors.inkMute,
    )
}

/**
 * README 浮层玻璃：玻璃悬浮按钮（首页导入卡 / 聊天列表新建会话共用）。
 * 统一走 emberGlass（毛玻璃 + 边缘高光一处定义）；关闭背景模糊时退回纯色容器。
 */
@Composable
fun EmberGlassFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    sky: Sky,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .emberShadow(
                color = EmberTheme.colors.accent.copy(alpha = 0.28f),
                radius = 12.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.14f,
            )
            .clip(RoundedCornerShape(18.dp))
            .emberGlass(sky = sky, atTop = true, tintAlpha = EmberGlassDefaults.FAB_TINT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = EmberTheme.colors.accent)
    }
}

/**
 * README UI 质感升级：高级滑块（全局替换 M3 Slider）。
 * 主色轨道 + 主色拇指，浅色主题/深色主题都取色自当前主题，禁用态自动降级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    accent: Color = EmberTheme.colors.accent,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent.copy(alpha = 0.85f),
            inactiveTrackColor = EmberTheme.colors.surfaceSink,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
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
    tint: Color = EmberTheme.colors.inkMute,
    container: Color = EmberTheme.colors.surface2.copy(alpha = 0.55f),
    ghost: Boolean = false,
) {
    if (ghost) {
        // 幽灵态：无容器纯图标（输入栏左侧工具簇降噪用，不再一排彩色圆钮抢输入框）
        IconButton(
            onClick = onClick,
            modifier = modifier.size(40.dp),
            enabled = enabled,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else EmberTheme.colors.line,
                modifier = Modifier.size(19.dp),
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(40.dp),
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = container,
                contentColor = tint,
                disabledContainerColor = EmberTheme.colors.surface2.copy(alpha = 0.28f),
                disabledContentColor = EmberTheme.colors.line,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * README UI 质感升级：高级底部栏（全局替换 ModalBottomSheet）。
 * 顶部 28dp 大圆角 + 拖拽把手 + 低对比表面，弹层更精致；交互语义不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellSheet(onDismiss = : () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = EmberTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EmberTheme.colors.line.copy(alpha = 0.65f)),
            )
        },
        content = content,
    )
}

/**
 * 高级主按钮（全局替换 M3 Button）：主色渐变 + 彩色投影 + 大圆角胶囊，
 * 比默认 M3 按钮更接近商业 App 的主行动按钮。禁用态自动降为中性容器。
 */
@Composable
fun EmberPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    expandWidth: Boolean = false,
    minHeight: Dp = 52.dp,
) {
    val primary = EmberTheme.colors.accent
    val shape = RoundedCornerShape(18.dp)
    val gradient = Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.16f)))
    Box(
        modifier = modifier
            .then(if (expandWidth) Modifier.fillMaxWidth() else Modifier)
            .height(minHeight)
            .emberShadow(
                color = primary.copy(alpha = 0.30f),
                radius = 14.dp,
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.55f,
            )
            .clip(shape)
            .then(
                if (enabled) Modifier.background(gradient)
                else Modifier.background(EmberTheme.colors.surfaceSink),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) EmberTheme.colors.ink else EmberTheme.colors.inkMute,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) EmberTheme.colors.ink else EmberTheme.colors.inkMute,
            )
        }
    }
}

/**
 * 高级次级按钮（全局替换 M3 TextButton 的主要场景）：tonal 容器 + 主色文字，
 * 与 EmberPrimaryButton 配套使用，弱化“开源默认按钮”观感。
 */
/**
 * 全局统一菜单行（底部弹层/下拉菜单共用）：图标进 tonal 圆角块 + 主文案 + 可选副文案 +
 * 可选尾部徽记/箭头，危险行整行 error 化。替换各屏裸“图标+文字”的 MenuRow/SheetRow，
 * 消除同一 App 里两套弹层菜单语言。
 */
@Composable
fun EmberMenuRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    iconTint: Color? = null,
    iconContainer: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> EmberTheme.colors.inkMute.copy(alpha = 0.38f)
        danger -> EmberTheme.colors.danger
        iconTint != null -> iconTint
        else -> EmberTheme.colors.inkMute
    }
    val container = when {
        !enabled -> EmberTheme.colors.surface2.copy(alpha = 0.20f)
        danger -> EmberTheme.colors.danger.copy(alpha = 0.12f)
        iconContainer != null -> iconContainer
        else -> EmberTheme.colors.surface2.copy(alpha = 0.72f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> EmberTheme.colors.inkMute.copy(alpha = 0.38f)
                    danger -> EmberTheme.colors.danger
                    else -> EmberTheme.colors.ink
                },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmberTheme.colors.inkMute.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
        if (showChevron) {
            Icon(
                FaIcons.ChevronRight,
                contentDescription = null,
                tint = EmberTheme.colors.inkMute.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 全局统一分区标题：accent 竖条锚点 + 标题 + 可选计数/动作，替换列表里裸 Text 标题
 * （“最近聊过/我的角色”等），给内容分区一个稳定的视觉锚。
 */
@Composable
fun EmberSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(EmberTheme.colors.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.invoke()
    }
}
