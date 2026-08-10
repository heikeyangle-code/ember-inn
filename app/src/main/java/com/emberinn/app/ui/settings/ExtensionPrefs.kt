package com.emberinn.app.ui.settings

import android.content.Context

/** 扩展插件偏好：交互 HTML 卡片（iframe 渲染器）总开关，默认开。
 *  关闭后：``` 内 HTML 代码块不再转 iframe，按普通代码块显示；
 *  头像类/宏/原代码折叠随总开关一起生效。JS/网络/外链行为不受此开关影响（第 177/178 轮已全放开）。 */
object ExtensionPrefs {

    private const val NAME = "ember_extensions"

    fun interactiveCards(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("interactive_cards", true)

    fun setInteractiveCards(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("interactive_cards", enabled)
            .apply()
    }
}
