package com.emberinn.app.renderer

import android.webkit.WebView
import kotlinx.serialization.serializer

/**
 * 渲染内核门面：ChatSurface 唯一接触点。
 * 封装所有 evaluateJavascript 调用，保证与 render.js 的 API 契约集中一处。
 */
class RenderKernel(private val pooled: KernelWebViewPool.PooledWebView) {

    private val web: WebView get() = pooled.webView

    /** 渲染一条消息（引擎已完成宏/正则前处理的 mes 文本） */
    fun renderMessage(payload: KernelMessagePayload, onDone: (() -> Unit)? = null) {
        val json = KernelProtocol.json.encodeToString(KernelMessagePayload.serializer(), payload)
        eval("window.Kernel.renderMessage($json);", onDone)
    }

    /** 整页壳 C2：全量同步官方 #chat；payload 顺序即聊天顺序。 */
    fun renderChat(payloads: List<KernelMessagePayload>, onDone: (() -> Unit)? = null) {
        val json = KernelProtocol.json.encodeToString(listAdapter, payloads)
        eval("window.Kernel.renderChat($json);", onDone)
    }

    /** 官方 #chat 滚动接管（C1/C2）。 */
    fun scrollToBottom(smooth: Boolean = false) {
        eval("window.Kernel.scrollToBottom(${if (smooth) "true" else "false"});")
    }

    /** 官方 openMessageDelete 的 DOM 状态部分；确认/取消仍由宿主输入区临时承接。 */
    fun setDeleteMode(enabled: Boolean) {
        eval("window.Kernel.setDeleteMode($enabled);")
    }

    /** 官方 .mes 点击删除选择：从该条到末尾全部选中。 */
    fun selectDeleteFrom(mesid: String) {
        val escaped = jsonEsc(mesid)
        eval("window.Kernel.selectDeleteFrom($escaped);")
    }

    /** 流式更新：节流由调用方控制；流中轻量更新 .mes_text，流结束后调用 [renderMessage] 权威重渲 */
    fun updateStreamingText(mesid: String, text: String) {
        val escaped = jsonEsc(text)
        eval(
            "(function(){var el=document.querySelector('.mes[mesid=\"$mesid\"] .mes_text');" +
                "if(el){el.innerHTML=window.Kernel.formatText($escaped,{});}})();",
        )
    }

    /** 应用官方主题 JSON（34 字段 → CSS 变量 + custom_css + body 类开关）。
     *  必须传原始 JSON 字符串：内核需要全部字段（含开关型），不能经 StTheme 有损转换。 */
    fun applyThemeRaw(themeJson: String) {
        eval("window.Kernel.applyTheme($themeJson);")
    }

    /** 官方消息布局模式（bubblechat/documentstyle body 类） */
    fun setChatDisplayMode(mode: ChatDisplayMode) {
        val cls = mode.bodyClass
        val js = buildString {
            append("document.body.classList.remove('bubblechat','documentstyle');")
            if (cls != null) append("document.body.classList.add('$cls');")
        }
        eval(js)
    }

    /** body 类全量同步（池主题状态专用）：light-theme 为基底，其余类整体重设。 */
    fun setBodyClasses(classes: List<String>) {
        val joined = classes.joinToString("") { " $it" }
        eval("document.body.className='light-theme$joined';")
    }

    /** 样式包（第三方主题整包 CSS）：varsJson 为原始 JSON 对象字面量（键=CSS 变量名）。
     *  extensionHref 可选（上游扩展兼容层 extension.css）。enabled=false 或 href=null
     *  时内核侧为无操作——纯官方主题零污染。 */
    fun applyStylePack(enabled: Boolean, href: String?, varsJson: String?, extensionHref: String? = null) {
        val js = buildString {
            append("window.Kernel && window.Kernel.applyStylePack({enabled:")
            append(if (enabled) "true" else "false")
            append(",href:")
            append(if (href != null) jsonEsc(href) else "null")
            append(",extensionHref:")
            append(if (extensionHref != null) jsonEsc(extensionHref) else "null")
            append(",vars:")
            append(varsJson ?: "null")
            append("});")
        }
        eval(js)
    }

    fun clear(onDone: (() -> Unit)? = null) = eval("window.Kernel.clear();", onDone)

    /** 官方 event_types 触发（Native→Web 下发）：args 经 JSON 序列化保持类型，shim 端转 eventSource.emit */
    fun emitEvent(type: String, args: List<String> = emptyList()) {
        val js = buildString {
            append("window.__emitKernelEvent && window.__emitKernelEvent(")
            append(jsonEsc(type))
            args.forEach { append(','); append(it) } // args 已是 JSON 字面量
            append(");")
        }
        eval(js)
    }

    private fun eval(js: String, onDone: (() -> Unit)? = null) {
        web.evaluateJavascript(js) { onDone?.invoke() }
    }

    private fun jsonEsc(s: String): String {
        // 直接序列化为带引号的 JSON 字符串字面量，天然处理转义
        return KernelProtocol.json.encodeToString(
            kotlinx.serialization.serializer<String>(),
            s,
        )
    }

    private companion object {
        val listAdapter =
            kotlinx.serialization.builtins.ListSerializer(KernelMessagePayload.serializer())
    }
}
