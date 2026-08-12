package com.emberinn.app.ui.settings

import android.content.Context

/** 扩展插件偏好：HTML 卡片的“可交互性”开关，默认开。
 *  渲染与交互分离：``` 内 HTML 代码块无论开关都渲染成 iframe 卡片；
 *  关闭时卡片照常显示，但脚本/表单被 sandbox 沙箱禁止（静态渲染）。
 *  头像类/宏/原代码折叠随卡片一起保留；网络/外链行为不受此开关影响（第 177/178 轮已全放开）。 */
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
