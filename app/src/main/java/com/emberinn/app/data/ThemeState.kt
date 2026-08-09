package com.emberinn.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 当前会话角色主题配方（全局主题管线的输入；离开聊天清空回全局）。 */
object ThemeState {

    private val _recipe = MutableStateFlow<ThemeRecipe?>(null)
    val recipe: StateFlow<ThemeRecipe?> = _recipe

    private val _seedColor = MutableStateFlow<Long?>(null)
    val seedColor: StateFlow<Long?> = _seedColor

    fun update(recipe: ThemeRecipe?, seedColor: Long?) {
        _recipe.value = recipe
        _seedColor.value = seedColor
    }

    fun clear() {
        update(null, null)
    }
}
