package com.emberinn.app.ui.settings

import android.content.Context

/** 全局外观偏好：圆角档位 / 字体档位（角色主题配方优先，全局兜底）。 */
object AppearancePrefs {

    private const val NAME = "ember_appearance"

    fun radius(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("radius", "default") ?: "default"

    fun font(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("font", "default") ?: "default"

    fun immersiveActions(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("immersive_actions", false)

    fun setImmersiveActions(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("immersive_actions", enabled)
            .apply()
    }

    fun bubbleStyle(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("bubble_style", "paper") ?: "paper"

    fun saveBubbleStyle(context: Context, style: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("bubble_style", style)
            .apply()
    }

    fun density(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("density", "comfortable") ?: "comfortable"

    fun saveDensity(context: Context, density: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("density", density)
            .apply()
    }

    /** README 玻璃表面：背景模糊总开关（默认开；关闭后顶栏/输入栏用纯色表面）。 */
    fun backgroundBlur(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("background_blur", true)

    fun saveBackgroundBlur(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("background_blur", enabled)
            .apply()
    }

    /** README 启动行为：启动时直接进入上次聊天（默认关）。 */
    fun openLastChat(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("open_last_chat", false)

    fun saveOpenLastChat(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("open_last_chat", enabled)
            .apply()
    }

    fun lastSessionId(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("last_session_id", "") ?: ""

    /** 官方 power_user.encode_tags（默认关）：显示时把 < > 转义为 &lt; &gt;。 */
    fun encodeTags(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("encode_tags", false)

    fun saveEncodeTags(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("encode_tags", enabled)
            .apply()
        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    /** 官方 power_user.auto_fix_generated_markdown（默认开）：显示前修复模型生成坏的 Markdown。 */
    fun fixMarkdown(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("auto_fix_generated_markdown", true)

    fun saveFixMarkdown(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_fix_generated_markdown", enabled)
            .apply()
        com.emberinn.app.data.DisplayCacheVersion.bump()
    }

    /** 全局文字阴影（对齐官方 style.css：* { text-shadow: 0 0 2px rgba(0,0,0,.5) }）。默认开、2px。 */
    fun textShadowEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("text_shadow", true)

    fun saveTextShadowEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("text_shadow", enabled)
            .apply()
    }

    /** 文字阴影模糊半径（0-4px，官方默认 2）。 */
    fun textShadowStrength(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("text_shadow_strength", 2)

    fun saveTextShadowStrength(context: Context, strength: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("text_shadow_strength", strength)
            .apply()
    }

    /** 头像形状：circle=圆形 50%（默认）、rounded=圆角 10px、square=方形 2px（官方默认）。 */
    fun avatarShape(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("avatar_shape", "circle") ?: "circle"

    fun saveAvatarShape(context: Context, shape: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("avatar_shape", shape)
            .apply()
    }

    /** 聊天文字排版：正文字号档（small/normal/official/large/xlarge；official=官方 15px）。 */
    fun textSize(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("text_size", "normal") ?: "normal"

    fun saveTextSize(context: Context, size: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("text_size", size)
            .apply()
    }

    /** 行高档（compact/normal/loose）。 */
    fun lineHeight(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("line_height", "normal") ?: "normal"

    fun saveLineHeight(context: Context, height: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("line_height", height)
            .apply()
    }

    /** 标题层级：flat=聊天风（标题缩小），real=正常层级（标题放大）。 */
    fun headingStyle(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("heading_style", "flat") ?: "flat"

    fun saveHeadingStyle(context: Context, style: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("heading_style", style)
            .apply()
    }

    /** 正文字重：normal / medium / semibold。 */
    fun bodyWeight(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("body_weight", "normal") ?: "normal"

    fun saveBodyWeight(context: Context, weight: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("body_weight", weight)
            .apply()
    }

    /** h1 额外倍率（在标题层级基础样式上再乘，默认 1.0）。 */
    fun headingH1(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("heading_h1", 1f)

    fun saveHeadingH1(context: Context, mult: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("heading_h1", mult)
            .apply()
    }

    /** h2 额外倍率。 */
    fun headingH2(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("heading_h2", 1f)

    fun saveHeadingH2(context: Context, mult: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("heading_h2", mult)
            .apply()
    }

    /** 引用块斜体（默认开）。 */
    fun quoteItalic(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("quote_italic", true)

    fun saveQuoteItalic(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("quote_italic", enabled)
            .apply()
    }

    /** 代码块字号倍率（相对正文）。 */
    fun codeSize(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("code_size", 0.9f)

    fun saveCodeSize(context: Context, mult: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("code_size", mult)
            .apply()
    }

    /** 行内代码字号倍率（相对正文）。 */
    fun inlineCodeSize(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("inline_code_size", 0.9f)

    fun saveInlineCodeSize(context: Context, mult: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("inline_code_size", mult)
            .apply()
    }

    /** 块间距：compact / normal / loose。 */
    fun blockSpacing(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("block_spacing", "normal") ?: "normal"

    fun saveBlockSpacing(context: Context, spacing: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("block_spacing", spacing)
            .apply()
    }

    /** 列表缩进（dp）：8 / 10 / 12。 */
    fun listIndent(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("list_indent", "10") ?: "10"

    fun saveListIndent(context: Context, indent: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("list_indent", indent)
            .apply()
    }

    // ---- 官方 SillyTavern 字段（public/style.css SmartTheme；空串 = 跟随主题自动生成） ----

    /** 官方字段：hex 字符串（如 #E1E1D2），空 = 跟随主题。 */
    fun stField(context: Context, key: String): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(key, "") ?: ""

    fun saveStField(context: Context, key: String, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(key, value.trim())
            .apply()
    }

    fun stBodyColor(context: Context): String = stField(context, "st_body_color")
    fun saveStBodyColor(context: Context, v: String) = saveStField(context, "st_body_color", v)

    fun stEmColor(context: Context): String = stField(context, "st_em_color")
    fun saveStEmColor(context: Context, v: String) = saveStField(context, "st_em_color", v)

    fun stUnderlineColor(context: Context): String = stField(context, "st_underline_color")
    fun saveStUnderlineColor(context: Context, v: String) = saveStField(context, "st_underline_color", v)

    fun stQuoteColor(context: Context): String = stField(context, "st_quote_color")
    fun saveStQuoteColor(context: Context, v: String) = saveStField(context, "st_quote_color", v)

    fun stUserBubble(context: Context): String = stField(context, "st_user_bubble")
    fun saveStUserBubble(context: Context, v: String) = saveStField(context, "st_user_bubble", v)

    fun stBotBubble(context: Context): String = stField(context, "st_bot_bubble")
    fun saveStBotBubble(context: Context, v: String) = saveStField(context, "st_bot_bubble", v)

    fun stBorderColor(context: Context): String = stField(context, "st_border_color")
    fun saveStBorderColor(context: Context, v: String) = saveStField(context, "st_border_color", v)

    fun stShadowColor(context: Context): String = stField(context, "st_shadow_color")
    fun saveStShadowColor(context: Context, v: String) = saveStField(context, "st_shadow_color", v)

    fun stBlurTint(context: Context): String = stField(context, "st_blur_tint")
    fun saveStBlurTint(context: Context, v: String) = saveStField(context, "st_blur_tint", v)

    /** 聊天背景：头像玻璃背景总开关（默认开；关=显式背景仍显示，头像回退到氛围渐变）。 */
    fun chatBgAvatarGlass(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("chat_bg_avatar_glass", true)

    fun saveChatBgAvatarGlass(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("chat_bg_avatar_glass", enabled)
            .apply()
    }

    /** 聊天背景图片模糊半径（px，0-48，默认 24）。 */
    fun chatBgBlur(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("chat_bg_blur", 24)

    fun saveChatBgBlur(context: Context, radius: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("chat_bg_blur", radius.coerceIn(0, 48))
            .apply()
    }

    /** 聊天背景深色遮罩强度（%，0-90，默认 65；浅色底用白色遮罩同档默认 30）。 */
    fun chatBgScrimDark(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("chat_bg_scrim_dark", 65)

    fun saveChatBgScrimDark(context: Context, percent: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("chat_bg_scrim_dark", percent.coerceIn(0, 90))
            .apply()
    }

    fun chatBgScrimLight(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("chat_bg_scrim_light", 30)

    fun saveChatBgScrimLight(context: Context, percent: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("chat_bg_scrim_light", percent.coerceIn(0, 60))
            .apply()
    }

    /** 聊天背景遮罩颜色（#RRGGBB / #AARRGGBB；最终不透明度 = 颜色 alpha × 强度%）。 */
    fun chatBgScrimDarkColor(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("chat_bg_scrim_dark_color", "#000000") ?: "#000000"

    fun saveChatBgScrimDarkColor(context: Context, v: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("chat_bg_scrim_dark_color", v.trim())
            .apply()
    }

    fun chatBgScrimLightColor(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("chat_bg_scrim_light_color", "#FFFFFF") ?: "#FFFFFF"

    fun saveChatBgScrimLightColor(context: Context, v: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("chat_bg_scrim_light_color", v.trim())
            .apply()
    }

    /** 毛玻璃模糊强度（Cloudy radius，0-40，默认 16）。 */
    fun blurStrength(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("blur_strength", 16)

    fun saveBlurStrength(context: Context, strength: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("blur_strength", strength.coerceIn(0, 40))
            .apply()
    }

    fun saveLastSessionId(context: Context, sessionId: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("last_session_id", sessionId)
            .apply()
    }

    fun save(context: Context, radius: String, font: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("radius", radius)
            .putString("font", font)
            .apply()
    }

}
