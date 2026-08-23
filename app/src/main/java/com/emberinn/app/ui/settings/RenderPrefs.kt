package com.emberinn.app.ui.settings

import android.content.Context

/** 消息渲染偏好。HTML 渲染只由官方唯一开关 encode_tags 决定（存在 AppearancePrefs.encodeTags，默认 false=渲染）：
 *  encode_tags=false → 消息 HTML 走 WebView 渲染；
 *  encode_tags=true → 显示管线把 < > 转义为纯文本，不进 WebView。
 *  htmlEnabled() 是渲染层的便捷取反；旧“HTML 消息（WebView 渲染）”键 html_enabled 已折算迁移。 */
object RenderPrefs {

    private const val NAME = "ember_render"

    fun htmlEnabled(context: Context): Boolean {
        // 旧键 html_enabled（true=渲染 HTML）→ 官方 encode_tags（false=渲染）：
        // 旧值 false（用户关过 HTML 渲染）折算为 encode_tags=true；迁移后删旧键，只发生一次。
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (sp.contains("html_enabled")) {
            val legacyRender = sp.getBoolean("html_enabled", true)
            sp.edit().remove("html_enabled").apply()
            if (!legacyRender) {
                AppearancePrefs.saveEncodeTags(context, true)
            }
        }
        return !AppearancePrefs.encodeTags(context)
    }

    /** 官方 power_user.collapse_newlines：字段/示例/回复清理时折叠连续换行。 */
    fun collapseNewlines(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("collapse_newlines", false)

    fun setCollapseNewlines(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("collapse_newlines", enabled)
            .apply()
    }

    /** V2 内核渲染：消息正文走 WebView 官方管线（池化，embed-shell 模式）。
     *  默认开；关闭回退旧原生渲染路线（P5 删除前保留双轨）。 */
    fun kernelRender(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("kernel_render", true)

    fun setKernelRender(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("kernel_render", enabled)
            .apply()
    }

    /** 严格模式（V2 §5.3 安全模型）：默认关=全开零打扰；开=内核 WebView 禁执行 JS
     *  （卡片脚本/围栏代码不跑，正文纯显示）。改动后需重建池实例（重进聊天页生效）。 */
    fun strictMode(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("strict_mode", false)

    fun setStrictMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("strict_mode", enabled)
            .apply()
    }

    /** P6 用户消息内核渲染评估结论（HANDOFF §5.2）：官方用户消息同样走 messageFormatting，
     *  1:1 上应进内核；代价是池槽位（用户消息短、收益小，群聊长列表内存压力）。
     *  默认关=原生胶囊省槽位；开=用户气泡正文同走内核管线。需 kernelRender 总开关同时为开。 */
    fun userKernelRender(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("user_kernel_render", false)

    fun setUserKernelRender(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("user_kernel_render", enabled)
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
