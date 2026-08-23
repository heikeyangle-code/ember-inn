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
    /** 官方 tokenCounterDisplay 文本（原样透传，"123" 或 "1.2k"） */
    val tokenCount: String? = null,
    /** 官方 .mes_reasoning 思考文本（引擎已提取；null/blank = 无思考块，details 保持折叠隐藏） */
    val reasoning: String? = null,
    /** 官方 extra.media 附件；媒体 DOM 由内核按官方容器挂载 */
    val media: List<KernelMediaPayload> = emptyList(),
    /** 官方 data-media-display：list 或 gallery */
    @SerialName("mediaDisplay") val mediaDisplay: String? = null,
    /** 官方 mes_ghost：隐藏消息对 AI 不可见 */
    val ghost: Boolean = false,
    /** 官方 refreshSwipeButtons / swipes-counter 所需状态 */
    val swipeCount: Int = 0,
    val currentSwipe: Int = 0,
    val lastMessage: Boolean = false,
)

/** 官方消息附件的最小跨桥载荷（URL 已由 App 层解析为可访问路径/data URL） */
@Serializable
data class KernelMediaPayload(
    val url: String,
    val type: String,
    val title: String? = null,
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

/**
 * 官方消息布局模式（power-user.js chat_display：0 平铺 / 1 气泡 / 2 文档）。
 * 3..7 = Moonlit Echoes 扩展布局，映射已对上游扩展 index.js initChatDisplaySwitcher
 * 逐项核实（3=Echo/4=Whisper/5=Hush/6=Ripple/7=Tide）。类名由对应样式包 CSS 定义，
 * 包未加载时惰性——与官方 applyChatDisplay 只认 0..2 不冲突。
 */
enum class ChatDisplayMode(val bodyClass: String?) {
    FLAT("flatchat"),
    BUBBLE("bubblechat"),
    DOCUMENT("documentstyle"),
    ECHOSTYLE("echostyle"),
    WHISPERSTYLE("whisperstyle"),
    HUSHSTYLE("hushstyle"),
    RIPPLESTYLE("ripplestyle"),
    TIDESTYLE("tidestyle"),
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
    /** 官方控件动作：mes_edit/mes_copy/.../swipe_left/swipe_right/del_checkbox */
    val action: String? = null,
    // ---- st-api-shim 请求-响应（P4 扩展桥）----
    val reqId: String? = null,
    val method: String? = null,
    /** params 为 JSON 字符串（避免嵌套对象反序列化歧义），无参为 null */
    val params: String? = null,
    // ---- hostRequest：白名单宿主能力请求（openLink/copyText/saveMedia/haptic，§5.3）----
    val action: String? = null,
    /** 动作参数：URL / 文本等；haptic 无参为 null */
    val value: String? = null,
    // ---- chatScroll（整页壳 C1）：#chat 滚动容器贴底状态，驱动跳底浮标显隐 ----
    val atBottom: Boolean? = null,
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
    const val HOST_REQUEST = "hostRequest"
    const val CHAT_SCROLL = "chatScroll"
}

/**
 * hostRequest 白名单动作常量（与 st-api-shim.js AppBridge/toastr 兼容层对齐；新增能力在此登记）。
 * 能力清单来源：官方全局 toastr（st-context/script.js 全局依赖）、DESIGN_SYSTEM §5.3
 * 四能力、社区卡高频需求（dataURL 导出/系统分享/触感）；WebView 自身已覆盖的
 * （外链跳转/音频播放）不重复提供。同步对话框 alert/confirm/prompt 无法跨异步桥保持
 * 同步签名——登记不支持（HANDOFF §6.4）。
 */
object KernelHostAction {
    const val OPEN_LINK = "openLink"
    const val COPY_TEXT = "copyText"
    const val SHARE = "share"
    const val TOAST = "toast"
    const val SAVE_MEDIA = "saveMedia"
    const val SAVE_DATA_URL = "saveDataUrl"
    const val VIBRATE = "vibrate"
}
