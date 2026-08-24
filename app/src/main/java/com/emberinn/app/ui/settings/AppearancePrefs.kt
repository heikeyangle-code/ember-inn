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

    /** 官方背景系统：全局背景图路径（filesDir/backgrounds/ 下的文件）。空 = 无全局背景。 */
    fun globalBackground(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("global_background", "") ?: ""

    fun saveGlobalBackground(context: Context, path: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("global_background", path)
            .apply()
    }
}
