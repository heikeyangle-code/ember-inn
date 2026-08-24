package com.emberinn.app.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 酒馆助手（TH 兼容层）独立偏好——与 AppearancePrefs 完全分离（架构约束：
 * TH 功能自成模块，不与官方外观系统互相渗透）。
 *
 * 字段与默认值对照 js-slash-runner src/type/settings.ts GlobalSettings：
 *   render.enabled=true / depth=0 / depth_ignore_hidden=false /
 *   collapse_code_block="frontend_only" / allow_streaming=false /
 *   script.enabled.global=true（macro.enabled=true 由内核层恒开承接）
 */
object TavernHelperPrefs {

    private const val NAME = "tavern_helper"

    /** 内核页渲染配置快照（桥 'th.config.get' 直接下发此 JSON 形状） */
    data class Config(
        val renderEnabled: Boolean = true,
        val depth: Int = 0,
        val depthIgnoreHidden: Boolean = false,
        val collapseCodeBlock: String = COLLAPSE_FRONTEND_ONLY,
        val allowStreaming: Boolean = false,
        val scriptEnabled: Boolean = true,
    ) {
        fun toJsonString(): String =
            """{"render":{"enabled":$renderEnabled,"depth":$depth,""" +
                """"depth_ignore_hidden":$depthIgnoreHidden,""" +
                """"collapse_code_block":"$collapseCodeBlock","allow_streaming":$allowStreaming},""" +
                """"script":{"enabled":{"global":$scriptEnabled}}}"""
    }

    @Volatile var current: Config = Config()
        private set

    fun read(context: Context): Config {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val collapse = p.getString("collapse_code_block", COLLAPSE_FRONTEND_ONLY)
            ?.takeIf { it in COLLAPSE_OPTIONS } ?: COLLAPSE_FRONTEND_ONLY
        return Config(
            renderEnabled = p.getBoolean("render_enabled", true),
            depth = p.getInt("depth", 0).coerceIn(0, 100),
            depthIgnoreHidden = p.getBoolean("depth_ignore_hidden", false),
            collapseCodeBlock = collapse,
            allowStreaming = p.getBoolean("allow_streaming", false),
            scriptEnabled = p.getBoolean("script_enabled", true),
        ).also { current = it }
    }

    private fun save(context: Context, transform: (android.content.SharedPreferences.Editor) -> Unit): Config {
        val editor = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
        transform(editor)
        editor.apply()
        val c = read(context)
        revision.value++
        return c
    }

    fun setRenderEnabled(context: Context, enabled: Boolean) {
        current = save(context) { it.putBoolean("render_enabled", enabled) }
    }

    fun setDepth(context: Context, depth: Int) {
        current = save(context) { it.putInt("depth", depth.coerceIn(0, 100)) }
    }

    fun setDepthIgnoreHidden(context: Context, enabled: Boolean) {
        current = save(context) { it.putBoolean("depth_ignore_hidden", enabled) }
    }

    fun setCollapseCodeBlock(context: Context, value: String) {
        if (value !in COLLAPSE_OPTIONS) return
        current = save(context) { it.putString("collapse_code_block", value) }
    }

    fun setAllowStreaming(context: Context, enabled: Boolean) {
        current = save(context) { it.putBoolean("allow_streaming", enabled) }
    }

    fun setScriptEnabled(context: Context, enabled: Boolean) {
        current = save(context) { it.putBoolean("script_enabled", enabled) }
    }

    /** TH collapse_code_block 三枚举（src/type/settings.ts） */
    const val COLLAPSE_NONE = "none"
    const val COLLAPSE_FRONTEND_ONLY = "frontend_only"
    const val COLLAPSE_ALL = "all"
    val COLLAPSE_OPTIONS = listOf(COLLAPSE_NONE, COLLAPSE_FRONTEND_ONLY, COLLAPSE_ALL)

    /** 配置变更版本流：聊天页据此向内核页重发 tavern_helper_config */
    val revision = MutableStateFlow(0)
}
