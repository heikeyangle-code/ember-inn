package com.emberinn.app.ui.settings

import android.content.Context

/** 表情精灵偏好（对齐官方 extensions/expressions settings.html 核心字段）。 */
data class ExpressionSettingsApp(
    val enabled: Boolean = false,
    val fallbackExpression: String = "",
    val allowMultiple: Boolean = false,
    val rerollIfSame: Boolean = false,
    val customLabels: Set<String> = emptySet(),
)

object ExpressionPrefs {

    private const val NAME = "ember_expression"

    fun load(context: Context): ExpressionSettingsApp {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return ExpressionSettingsApp(
            enabled = p.getBoolean("enabled", false),
            fallbackExpression = p.getString("fallback", "") ?: "",
            allowMultiple = p.getBoolean("allowMultiple", false),
            rerollIfSame = p.getBoolean("rerollIfSame", false),
            customLabels = (p.getStringSet("customLabels", emptySet()) ?: emptySet()).toSet(),
        )
    }

    fun save(context: Context, s: ExpressionSettingsApp) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putString("fallback", s.fallbackExpression)
            .putBoolean("allowMultiple", s.allowMultiple)
            .putBoolean("rerollIfSame", s.rerollIfSame)
            .putStringSet("customLabels", s.customLabels)
            .apply()
    }
}
