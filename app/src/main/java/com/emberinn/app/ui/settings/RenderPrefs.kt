package com.emberinn.app.ui.settings

import android.content.Context

/** 消息渲染偏好：HTML 消息开关（官方 Showdown HTML；本 App 用 WebView 兜底渲染）。 */
object RenderPrefs {

    private const val NAME = "ember_render"

    fun htmlEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("html_enabled", true)

    fun setHtmlEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("html_enabled", enabled)
            .apply()
    }

    /** 官方 power_user.collapse_newlines：字段/示例/回复清理时折叠连续换行。 */
    fun collapseNewlines(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("collapse_newlines", false)

    fun setCollapseNewlines(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("collapse_newlines", enabled)
            .apply()
    }

    /** 官方 power_user.context.example_separator（默认 ***）。 */
    fun exampleSeparator(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("example_separator", "***") ?: "***"

    fun setExampleSeparator(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("example_separator", value)
            .apply()
    }
}
