package com.emberinn.app.renderer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EmberInn 2.0 渲染内核 — Kotlin↔JS 桥协议模型。
 *
 * 分工边界（见 docs/REFACTOR_V2_PLAN.md §3.2）：
 *   引擎（Kotlin，差分锁定）→ 宏/正则/reasoning 前置处理
 *   内核 render.js（官方原版逻辑）→ fixMarkdown 之后到 DOMPurify 为止的显示管线
 */
object KernelProtocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 内核资产在 AssetLoader 下的基地址 */
    const val KERNEL_URL = "https://appassets.androidplatform.net/assets/kernel/kernel.html"

    /** JS 注入名：window.AndroidKernel.postMessage(JSON) */
    const val BRIDGE_NAME = "AndroidKernel"
}

// ---------------------------------------------------------------------------
// Native → Web 载荷
// ---------------------------------------------------------------------------

/** 单条消息渲染载荷（对应 render.js renderMessage） */
@Serializable
data class KernelMessagePayload(
    val mesid: String,
    val mes: String,
    val chName: String = "",
    @SerialName("isUser") val isUser: Boolean = false,
    @SerialName("isSystem") val isSystem: Boolean = false,
    val avatarUrl: String? = null,
    val timestamp: String? = null,
    val tokenCount: Int? = null,
)

/** 官方主题 JSON（34 字段中与渲染相关的核心子集；未知字段由 ignoreUnknownKeys 吸收） */
@Serializable
data class StTheme(
    @SerialName("main_text_color") val mainTextColor: String? = null,
    @SerialName("italics_text_color") val italicsTextColor: String? = null,
    @SerialName("underline_text_color") val underlineTextColor: String? = null,
    @SerialName("quote_text_color") val quoteTextColor: String? = null,
    @SerialName("blur_tint_color") val blurTintColor: String? = null,
    @SerialName("chat_tint_color") val chatTintColor: String? = null,
    @SerialName("user_mes_blur_tint_color") val userMesBlurTintColor: String? = null,
    @SerialName("bot_mes_blur_tint_color") val botMesBlurTintColor: String? = null,
    @SerialName("shadow_color") val shadowColor: String? = null,
    @SerialName("border_color") val borderColor: String? = null,
    @SerialName("blur_strength") val blurStrength: Int? = null,
    @SerialName("shadow_width") val shadowWidth: Int? = null,
    @SerialName("font_scale") val fontScale: Double? = null,
    @SerialName("chat_width") val chatWidth: Double? = null,
    @SerialName("custom_css") val customCss: String? = null,
) {
    fun toJsonString(): String = KernelProtocol.json.encodeToString(serializer(), this)
}

/** 官方消息布局模式（power-user.js chat_display：0 平铺 / 1 气泡 / 2 文档） */
enum class ChatDisplayMode(val bodyClass: String?) {
    FLAT(null),
    BUBBLE("bubblechat"),
    DOCUMENT("documentstyle"),
}

// ---------------------------------------------------------------------------
// Web → Native 事件（render.js bridgeSend 的反序列化目标）
// ---------------------------------------------------------------------------

@Serializable
data class KernelEvent(
    val type: String,
    val mesid: String? = null,
    val height: Float? = null,
    /** click 事件的目标描述 {tag, cls} */
    val target: KernelClickTarget? = null,
    // ---- st-api-shim 请求-响应（P4 扩展桥）----
    val reqId: String? = null,
    val method: String? = null,
    /** params 为 JSON 字符串（避免嵌套对象反序列化歧义），无参为 null */
    val params: String? = null,
)

@Serializable
data class KernelClickTarget(
    val tag: String? = null,
    val cls: String? = null,
)

/** 桥事件类型常量（与 render.js bridgeSend 对齐） */
object KernelEventType {
    const val KERNEL_READY = "kernelReady"
    const val HEIGHT = "height"
    const val HEIGHT_CHANGED = "heightChanged"
    const val CLICK = "click"
    const val LONG_PRESS = "longPress"
    const val THEME_APPLIED = "themeApplied"
    const val SHIM_REQUEST = "shimRequest"
}
