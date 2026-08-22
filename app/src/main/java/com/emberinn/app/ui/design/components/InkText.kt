package com.emberinn.app.ui.design.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme

/** 墨阶四档（DESIGN_SYSTEM §三 4）。 */
enum class InkTier { Primary, Soft, Mute, Faint }

/**
 * 文字四档封装：全部业务文字统一入口，禁止散落 colorScheme 引用。
 * 字号克制（font_scale ≈ 1.0）、行距宽松。
 */
@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    tier: InkTier = InkTier.Primary,
    sizeSp: Float = 15f,
    lineHeightSp: Float = 22f,
    italic: Boolean = false,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = EmberTheme.colors
    Text(
        text = text,
        modifier = modifier,
        color = when (tier) {
            InkTier.Primary -> c.ink
            InkTier.Soft -> c.inkSoft
            InkTier.Mute -> c.inkMute
            InkTier.Faint -> c.inkSoft2
        },
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = fontWeight,
        maxLines = maxLines,
    )
}

/** 区块标题：17sp SemiBold 主墨。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, tier: InkTier = InkTier.Primary) {
    InkText(text, modifier, tier = tier, sizeSp = 17f, fontWeight = FontWeight.SemiBold)
}

/** 强调色文字（链接/选中态等）。 */
@Composable
fun AccentText(text: String, modifier: Modifier = Modifier, sizeSp: Float = 15f, fontWeight: FontWeight? = null) {
    Text(
        text = text,
        modifier = modifier,
        color = EmberTheme.colors.accent,
        fontSize = sizeSp.sp,
        fontWeight = fontWeight,
    )
}

/** AI 身份色文字（模型 chip、生成中等）。 */
@Composable
fun AiText(text: String, modifier: Modifier = Modifier, sizeSp: Float = 13f, alpha: Float = 1f) {
    Text(
        text = text,
        modifier = modifier,
        color = EmberTheme.colors.ai.copy(alpha = alpha),
        fontSize = sizeSp.sp,
    )
}
