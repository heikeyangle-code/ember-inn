package com.emberinn.app.ui.settings

import android.content.Context

/** 全局外观偏好：壳层自有项（圆角/字体/行为开关/聊天背景）。
 *  官方主题覆盖的字段（颜色/排版/头像形状/文字阴影）一律不在此存储——
 *  由 OfficialThemeManager 主题 JSON 直供，经 ShellTheme.derive 推导进令牌。 */
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

    /** 毛玻璃模糊强度（Cloudy radius，0-40；默认 16，官方 power_user.blur_strength 默认 10 太弱看不出玻璃）。 */
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
