package com.emberinn.app.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons
import java.io.File

/**
 * Editorial 组件族（docs/UI_REDESIGN_V3.md §四）：
 * Premium Editorial × AI Companion 的 Companion Space / Power Space 视觉层。
 * 全部消费 EmberTheme 令牌（主题即皮肤），零 MaterialTheme.colorScheme、零硬编码色。
 *
 * 排版律：Display 32sp Light（留白呼吸）/ Title 18sp SemiBold / Body 14·13sp / Meta 11sp+字距。
 * 空间律：三体验空间——Companion（大图/轨道/非对称）/ Chat（沉浸，本族不涉）/ Power（密度/折叠）。
 */

// ---------------------------------------------------------------- 排版基元

/** Companion 页头：Display 大标题 + 可选副文本（编辑排版，非 Dashboard）。 */
@Composable
fun EditorialHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = c.ink,
                fontSize = EmberTheme.typo.heroBig.fontSize,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.4.sp,
                lineHeight = 38.sp,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = c.inkMute, fontSize = EmberTheme.typo.bodySmall.fontSize, lineHeight = 18.sp)
            }
        }
        if (trailing != null) trailing()
    }
}

/** 轨道节头：组题 + 「查看全部」入口（横向轨道的标准头）。
 *  组间上距=壳层密度令牌（第 15 阶段：紧凑档收紧轨道呼吸）。 */
@Composable
fun RailHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = EmberTheme.spacing.sectionGap, bottom = 10.dp),
    ) {
        Text(
            title.uppercase(),
            color = c.inkMute,
            fontSize = EmberTheme.typo.meta.fontSize,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onSeeAll),
            ) {
                Text("查看全部", color = c.accent, fontSize = EmberTheme.typo.caption.fontSize)
                Icon(FaIcons.ChevronRight, contentDescription = null, tint = c.accent, modifier = Modifier.size(11.dp))
            }
        }
    }
}

/** 横向内容轨道：子项横排滚动（Companion Space 标准容器）。 */
@Composable
fun SectionRail(
    modifier: Modifier = Modifier,
    spacing: Dp = 9.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = contentPadding),
        content = content,
    )
}

// ---------------------------------------------------------------- Companion 视觉件

/**
 * 角色主页英雄区（§4.4 幕布式）：全幅头图向下渐隐入页面底，
 * 头像压缝 + 名字 + 简介折叠 + 强主操作（开始/继续对话）。
 */
@Composable
fun CharacterHeroBlock(
    name: String,
    avatarPath: String?,
    imagePath: String?,
    subtitle: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    meta: (@Composable () -> Unit)? = null,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(bottomStart = shapes.cornerSheet, bottomEnd = shapes.cornerSheet)),
        ) {
            if (imagePath != null) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize().background(c.surface))
            }
            // 渐隐幕：头图渐隐入页面底色（角色主页非对称构图核心）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to c.bg,
                        ),
                    ),
            )
            // 头像压缝：下探到内容区
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp)) {
                CharacterHeroAvatar(avatarPath, name)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(name, color = c.ink, fontSize = EmberTheme.typo.hero.fontSize, fontWeight = FontWeight.Light, letterSpacing = 0.4.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = c.inkMute, fontSize = EmberTheme.typo.bodySmall.fontSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (meta != null) { Spacer(Modifier.height(10.dp)); meta() }
            Spacer(Modifier.height(16.dp))
            PrimaryAction(label = primaryLabel, onClick = onPrimary)
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 英雄头像：大尺寸人圆（有图用图，无图首字沉面）。 */
@Composable
private fun CharacterHeroAvatar(avatarPath: String?, name: String) {
    val c = EmberTheme.colors
    if (avatarPath != null) {
        AsyncImage(
            model = File(avatarPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(c.bg)
                .padding(2.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(c.surface2),
        ) {
            Text(name.take(1), color = c.ink, fontSize = EmberTheme.typo.hero.fontSize, fontWeight = FontWeight.Light)
        }
    }
}

/** 唯一强主操作（§4.4）：操作面整宽按钮——一页只允许一个。 */
@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) readableOnLabel(c.accent) else c.inkMute,
            fontSize = EmberTheme.typo.subhead.fontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
    }
}

/**
 * 故事卡（Conversation=Story）：角色+最后一句+相对时间。
 * 与 HeroCard 区分：更叙事（时间行/多行预览），用于「我和这个角色经历过的故事」。
 */
@Composable
fun StoryCard(
    title: String,
    preview: String?,
    caption: String?,
    avatarPath: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(14.dp),
    ) {
        AvatarCircle(avatarPath, title, 44.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(badge, color = c.accent, fontSize = EmberTheme.typo.micro.fontSize, letterSpacing = 0.8.sp)
                }
            }
            if (!preview.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(preview, color = c.inkMute, fontSize = EmberTheme.typo.bodySmall.fontSize, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(caption, color = c.ink.copy(alpha = 0.34f), fontSize = EmberTheme.typo.meta.fontSize)
            }
        }
    }
}

// ---------------------------------------------------------------- Power Space 件

/** 设置分区磁贴（§4.6 2×2 常用磁贴）：图标 + 分区名 + 实时值摘要。 */
@Composable
fun SettingsTile(
    title: String,
    icon: ImageVector,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EmberTheme.colors
    val shapes = EmberTheme.shapes
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(shapes.cornerCard))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .heightIn(min = 76.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(16.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = c.ink, fontSize = EmberTheme.typo.body.fontSize, fontWeight = FontWeight.Medium)
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(value, color = c.inkMute, fontSize = EmberTheme.typo.meta.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 折叠组（Progressive Disclosure 载体，§六）：
 * 头部=组题+当前值摘要+旋转箭头；内容按 reducedMotion 降级。
 * 降低视觉复杂度而不是功能复杂度——复杂设置全部保留，默认收起。
 */
@Composable
fun AccordionGroup(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = EmberTheme.colors
    val motion = EmberTheme.motion
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val reduced = EmberTheme.reducedMotion
    // 折叠组动效（§七 归档）：展开/箭头走 controlMs；减动画=瞬时切换 + 80ms fade
    val revealMs = if (reduced) motion.reducedMs else motion.controlMs
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(motion.controlMs),
        label = "accordionArrow",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 4.dp, top = 16.dp, bottom = if (expanded) 4.dp else 12.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    color = c.inkMute,
                    fontSize = EmberTheme.typo.meta.fontSize,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (!summary.isNullOrBlank() && !expanded) {
                    Spacer(Modifier.height(3.dp))
                    Text(summary, color = c.ink.copy(alpha = 0.34f), fontSize = EmberTheme.typo.meta.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                FaIcons.ChevronDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = c.ink.copy(alpha = 0.34f),
                modifier = Modifier.size(13.dp).rotate(arrow),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(revealMs)) + expandVertically(tween(revealMs)),
            exit = fadeOut(tween(revealMs)) + shrinkVertically(tween(revealMs)),
        ) {
            Column { content() }
        }
    }
}

/** 元信息行（角色主页/Power 页通用）：标签 + 值横排。 */
@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    val c = EmberTheme.colors
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = c.inkMute, fontSize = EmberTheme.typo.caption.fontSize)
        Spacer(Modifier.width(12.dp))
        Text(value, color = c.ink, fontSize = EmberTheme.typo.caption.fontSize, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** 统计徽章组：角色条目数/会话数等元信息胶囊。 */
@Composable
fun StatBadges(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = EmberTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        stats.forEach { (k, v) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(v, color = c.ink, fontSize = EmberTheme.typo.caption.fontSize, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(k, color = c.inkMute, fontSize = EmberTheme.typo.meta.fontSize)
            }
        }
    }
}
