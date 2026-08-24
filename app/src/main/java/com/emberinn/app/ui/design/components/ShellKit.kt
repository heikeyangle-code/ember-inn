package com.emberinn.app.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons
import java.io.File

/**
 * 壳层新组件族（docs/DESIGN_SYSTEM.md §五定稿）：
 * 一切颜色取自官方主题字段推导令牌（EmberTheme），零 MaterialTheme.colorScheme、零硬编码色。
 * 面的三档语义：Base=页底 / Content=内容面(bot tint) / Action=操作面(user tint)。
 */

enum class ShellFace { Base, Content, Action }

/** 万能面容器：主题面 + 主题圆角 + 可选发丝缘（border_color 仅在官方边框语义处使用）。 */
@Composable
fun ThemeSurface(
    face: ShellFace,
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    hairline: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    val faceColor = when (face) {
        ShellFace.Base -> c.bg
        ShellFace.Content -> c.surface
        ShellFace.Action -> c.surface2
    }
    val shape = RoundedCornerShape(corner ?: shapes.cornerCard)
    Box(
        modifier = modifier
            .clip(shape)
            .background(faceColor)
            .then(if (hairline) Modifier.border(1.dp, c.line, shape) else Modifier),
        content = content,
    )
}

/**
 * 行式列表项（§4.6）：左名称右当前值，无边框无线，行距即分组。
 * trailing 槽放 EmberSwitch 等控件；onClick 为空则纯展示。
 */
@Composable
fun RowLine(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    muted: Boolean = false,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = if (muted) c.inkMute else c.ink,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                color = c.inkMute,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 组题（§4.6）：弱墨小字加字距，组间靠留白不靠线。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    val c = EmberTheme.colors
    Text(
        text.uppercase(),
        color = c.inkMute,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 凹陷搜索场（§4.3/§4.6）：底色加深一档 + border_color 缘。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = EmberTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceSink)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Icon(FaIcons.MagnifyingGlass, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Box {
            if (value.isEmpty()) {
                Text(placeholder, color = c.inkMute, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 标签输入场：弱墨小标签在上方，凹陷面输入行。数字/文本通用。 */
@Composable
fun ShellInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val c = EmberTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(label, color = c.inkMute, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surfaceSink)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = c.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 头像圆（人圆物方口径里的"人"）：有图用图，无图落首字于凹陷面。 */
@Composable
fun AvatarCircle(path: String?, name: String, size: Dp) {
    val c = EmberTheme.colors
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(CircleShape).background(c.surfaceSink),
        ) {
            Text(
                name.take(1),
                color = c.inkMute,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 续读英雄卡（§4.1）：内容面 + 名字/最后一句/时间，未读数走强调底洗。 */
@Composable
fun HeroCard(
    title: String,
    preview: String?,
    caption: String?,
    unread: Int = 0,
    avatarPath: String?,
    onClick: () -> Unit,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        AvatarCircle(avatarPath, title, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!preview.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(preview, color = c.inkMute, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(caption, color = c.ink.copy(alpha = 0.34f), fontSize = 11.sp)
            }
        }
        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.accentBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(unread.toString(), color = c.accent, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 海报砖（§4.3）：原始纵横比 + 下缘悬浮名牌（名牌垫页底实底再半透，文字永远可读）。
 * ghost=true 时是「＋」导入幽灵位。
 */
@Composable
fun PosterTile(
    name: String,
    avatarPath: String?,
    width: Dp,
    aspect: Float = 0.70f,
    onClick: () -> Unit,
    ghost: Boolean = false,
) {
    val c = EmberTheme.colors
    Box(
        modifier = Modifier
            .width(width)
            .height(width / aspect)
            .clip(RoundedCornerShape(14.dp))
            .then(if (ghost) Modifier.border(1.dp, c.line, RoundedCornerShape(14.dp)) else Modifier.background(c.surfaceSink))
            .clickable(onClick = onClick),
    ) {
        if (ghost) {
            Icon(
                FaIcons.Plus,
                contentDescription = null,
                tint = c.inkMute,
                modifier = Modifier.align(Alignment.Center).size(20.dp),
            )
        } else {
            AsyncImage(
                model = avatarPath?.let { File(it) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(width / aspect),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(5.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.bg.copy(alpha = 0.78f)),
            ) {
                Text(
                    name,
                    color = c.ink,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** 启用状态烛火点（§4.5）：亮＝强调色，灭＝弱墨。 */
@Composable
fun CandleDot(lit: Boolean) {
    val c = EmberTheme.colors
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (lit) c.accent else c.ink.copy(alpha = 0.25f)),
    )
}

/** 自绘开关：轨道=强调/弱墨两态，滑块=页底色。无 M3 Switch 的标准安卓味。 */
@Composable
fun EmberSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = EmberTheme.colors
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(160),
        label = "emberSwitchThumb",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent else c.ink.copy(alpha = 0.16f))
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(22.dp)
                .clip(CircleShape)
                .background(c.bg)
                .align(Alignment.CenterStart),
        )
    }
}

/** 自绘选择粒：选中=操作面实底+墨字，未选=发丝缘+弱墨。无 M3 FilterChip 味。 */
@Composable
fun ShellChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = EmberTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(c.surface2) else Modifier.border(1.dp, c.line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) c.ink else c.inkMute, fontSize = 12.sp)
    }
}

/** 小型操作钮：操作面实底圆角粒（新建/导入/新增类）。 */
@Composable
fun ShellActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EmberTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = c.ink, fontSize = 13.sp)
    }
}

/** 导航栈条目（FloatHub 内部用）。 */
data class HubItem(val label: String, val icon: ImageVector)

/**
 * 悬浮主钮 + 四项竖栈（§三导航范式）：静默时只是右下角一枚圆粒，
 * 点开向上弹出玻璃小栈；当前项强调色标记。聊天页由调用方决定隐藏。
 */
@Composable
fun FloatHub(
    items: List<HubItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EmberTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(140)) + expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(180)),
            exit = fadeOut(tween(110)) + shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(140)),
        ) {
            ThemeSurface(ShellFace.Content, corner = 18.dp, hairline = true, modifier = Modifier.padding(bottom = 10.dp)) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    items.forEachIndexed { index, item ->
                        val active = selected == index
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .widthIn(min = 148.dp)
                                .clickable {
                                    onSelect(index)
                                    open = false
                                }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (active) c.accent else c.inkMute,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.label,
                                color = if (active) c.accent else c.ink,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.surface.copy(alpha = 0.96f))
                .border(1.dp, c.lineStrong, CircleShape)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (open) FaIcons.XMark else FaIcons.Bars,
                contentDescription = if (open) "收起导航" else "打开导航",
                tint = c.ink,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
