package com.emberinn.app.renderer

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * TavernHelper 变量族 **global 作用域**存储（酒馆助手 variables.ts 官方语义：
 * extension_settings.variables.global + saveSettingsDebounced）。
 * App 等价物：SharedPreferences 单键 JSON——MVU 卡全局变量表体量下够用，
 * 未来迁独立文件/Room 时公开 API（read/write）不变。
 */
class GlobalVariableStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tavernhelper_global_variables", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): JsonObject =
        runCatching { json.parseToJsonElement(prefs.getString(KEY, "{}").orEmpty()).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))

    fun write(value: JsonObject) {
        prefs.edit().putString(KEY, value.toString()).apply()
    }

    companion object {
        private const val KEY = "variables_json"
    }
}
