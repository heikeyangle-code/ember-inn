package com.emberinn.app.ui.theme

/**
 * 视觉氛围（vibe）：控制“算法取色后的性格”，全部可调、默认中性无品牌滤镜。
 * - desaturateLight/Dark：降饱和强度（0 = 算法原色，1 = 完全中性灰）
 * - warmth：冷暖偏移（>0 暖黄，<0 冷蓝）
 * - glow：装饰光效强度（阴影/空状态装饰用，0 = 关闭）
 */
data class VibePreset(
    val id: String,
    val name: String,
    val desc: String,
    val desaturateLight: Float,
    val desaturateDark: Float,
    val warmth: Float,
    val glow: Float,
)

val VibePresets: List<VibePreset> = listOf(
    VibePreset("standard", "标准", "取色原样输出，无滤镜（默认）", 0f, 0f, 0f, 0.5f),
    VibePreset("soft", "柔和", "轻微降饱和，观感更柔和", 0.15f, 0.13f, 0f, 0.6f),
    VibePreset("cool", "清冷", "偏冷灰蓝的低饱和", 0.28f, 0.22f, -0.10f, 0.5f),
    VibePreset("vivid", "明快", "高饱和、更鲜艳", 0.05f, 0.05f, 0.02f, 0.8f),
    VibePreset("airy", "轻盈", "高亮度、低饱和的通透感", 0.2f, 0.15f, 0.05f, 0.6f),
    VibePreset("calm", "沉静", "低对比度、冷色调的平静感", 0.3f, 0.25f, -0.06f, 0.4f),
    VibePreset("custom", "自定义", "手动调节三项参数", 0.15f, 0.15f, 0f, 0.6f),
)

fun List<VibePreset>.vibeById(id: String): VibePreset = firstOrNull { it.id == id } ?: VibePresets.first()
