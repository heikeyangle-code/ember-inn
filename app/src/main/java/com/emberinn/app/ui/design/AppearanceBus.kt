package com.emberinn.app.ui.design

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 外观偏好变更总线：外观页改了字体等全局项后 bump 一次，
 * MainActivity 按 revision 重读偏好（字体族即时全局生效，无需重启）。
 */
object AppearanceBus {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun notifyChanged() {
        _revision.value += 1
    }
}
