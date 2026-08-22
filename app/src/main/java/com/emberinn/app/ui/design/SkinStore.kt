package com.emberinn.app.ui.design

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 活动皮肤持久化 + 进程级状态流。皮肤选择是壳层自己的偏好，与官方内容主题（OfficialThemeManager）分轨。
 * 外观页切换皮肤 → StateFlow 更新 → MainActivity 重组，全局即时生效。
 */
object SkinStore {
    private const val PREFS = "ember_skin"
    private const val KEY_ID = "skin_id"

    private val _skin = MutableStateFlow(EmberSkins.DEFAULT)
    val skin: StateFlow<EmberSkin> = _skin

    /** App 冷启动调用一次：从磁盘恢复上次选择。 */
    fun init(context: Context) {
        _skin.value = active(context)
    }

    fun id(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, EmberSkins.DEFAULT.id)
            ?: EmberSkins.DEFAULT.id

    fun active(context: Context): EmberSkin = EmberSkins.byId(id(context))

    /** 切换皮肤：落盘 + 推送状态流（外观页一键换装入口）。 */
    fun select(context: Context, skin: EmberSkin) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ID, skin.id).apply()
        _skin.value = skin
    }
}
