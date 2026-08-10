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
}
