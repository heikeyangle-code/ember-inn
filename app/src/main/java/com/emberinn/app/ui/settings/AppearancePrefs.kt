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
