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
    fun renderMessage(payload: KernelMessagePayload, onDone: ((String?) -> Unit)? = null) {
        val json = KernelProtocol.json.encodeToString(KernelMessagePayload.serializer(), payload)
        eval("window.Kernel.renderMessage($json);", onDone)
    }

    /** 整页壳 C2：全量同步官方 #chat；payload 顺序即聊天顺序。
     *  showMore=true 时顶部挂 #show_more_messages（边界5 长聊天截断，script.js printMessages）。 */
    fun renderChat(payloads: List<KernelMessagePayload>, showMore: Boolean = false, onDone: ((String?) -> Unit)? = null) {
        val json = KernelProtocol.json.encodeToString(listAdapter, payloads)
        android.util.Log.e("EmberInnKernel", "renderChat payloads=${payloads.size} showMore=$showMore")
        KernelDiagnostics.log("renderChat payloads=${payloads.size}${if (showMore) " showMore" else ""}")
        eval("window.Kernel.renderChat($json,{showMore:${if (showMore) "true" else "false"}});", onDone)
    }

    /** 边界5 show more：把上一批消息插到 #show_more_messages 之后（官方 showMoreMessages
     *  的按钮锚点插入 + 视口内高度差回滚），不动其余节点。 */
    fun prependMessages(payloads: List<KernelMessagePayload>) {
        val json = KernelProtocol.json.encodeToString(listAdapter, payloads)
        eval("window.Kernel.prependMessages($json);")
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

    /** C3：宿主 → #send_textarea（草稿下发 / 冒充流式 / 发送后清空）。
     *  内核会补发 inputChanged 镜像，宿主状态自然收敛。 */
    fun pushInputText(text: String) {
        val escaped = jsonEsc(text)
        eval("window.Kernel.setInputText($escaped);")
    }

    /** C3：生成/滑动状态 → body[data-generating]/[data-swiping] + #mes_stop 显隐 */
    fun setInputState(generating: Boolean, swiping: Boolean = false) {
        val js = "window.Kernel.setInputState({generating:$generating,swiping:$swiping});"
        eval(js)
    }

    /** C4：官方 #bg1 背景（backgrounds.js onChatChanged 同构）；url=null 清除 */
    fun setBackground(url: String?, fitting: String? = null) {
        val js = buildString {
            append("window.Kernel.setBackground(")
            append(if (url != null) jsonEsc(url) else "null")
            append(",")
            append(if (fitting != null) jsonEsc(fitting) else "null")
            append(");")
        }
        eval(js)
    }

    /** C5 官方 onProgressStreaming 同构：原地更新流式行 .mes_text（+可选 .mes_reasoning/.mes_timer），
     *  不重建其余消息节点；节流由调用方控制，流结束走 renderChat 权威同步。
     *  走内核 updateStreamingNode 一站式入口：stream_fade_in 分词渐显 + timer 直写
     *  （官方 script.js L3673-3677 每 chunk 重算 mes_timer）。 */
    fun updateStreaming(
        mesid: String,
        text: String,
        reasoning: String? = null,
        timerValue: String? = null,
        timerTitle: String? = null,
    ) {
        val js = buildString {
            append("window.Kernel.updateStreamingNode({mesid:")
            append(jsonEsc(mesid))
            append(",text:")
            append(jsonEsc(text))
            append(",reasoning:")
            append(if (reasoning != null) jsonEsc(reasoning) else "null")
            append(",timerValue:")
            append(if (timerValue != null) jsonEsc(timerValue) else "null")
            append(",timerTitle:")
            append(if (timerTitle != null) jsonEsc(timerTitle) else "null")
            append("});")
        }
        eval(js)
    }

    /** 边界3 click_to_edit 桥：宿主判定开关与选区后让内核进入行内编辑（messageEdit 同构） */
    fun beginEditMessage(mesid: String) {
        val escaped = jsonEsc(mesid)
        eval("window.Kernel.beginEditMessage($escaped);")
    }

    /** 应用官方主题 JSON（34 字段 → CSS 变量 + custom_css + body 类开关）。
     *  必须传原始 JSON 字符串：内核需要全部字段（含开关型），不能经 StTheme 有损转换。 */
    fun applyThemeRaw(themeJson: String) {
        eval("window.Kernel.applyTheme($themeJson);")
    }

    /** 运行时开关下发（KernelRuntimeConfig JSON）：streamFadeIn/gestures/sendOnEnter/
     *  quickContinue/quickImpersonate/autoSaveEdits；内核侧即时联动（含 quick 按钮显隐）。 */
    fun setRuntimeConfig(cfgJson: String) {
        eval("window.Kernel.setRuntimeConfig($cfgJson);")
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
     *  extensionHref 可选（上游扩展兼容层 extension.css）；cssBlocksJson 可选
     *  （checkbox 启用注入的内嵌 CSS，官方 cssBlock 语义）；jsUrl 可选（扩展 manifest.js，
     *  内核 <script type="module"> 注入——官方 addExtensionScript 通道）。
     *  enabled=false 或 href=null 时内核侧为无操作——纯官方主题零污染。 */
    fun applyStylePack(
        enabled: Boolean,
        href: String?,
        varsJson: String?,
        extensionHref: String? = null,
        cssBlocksJson: String? = null,
        packId: String? = null,
        jsUrl: String? = null,
    ) {
        val js = buildString {
            append("window.Kernel && window.Kernel.applyStylePack({enabled:")
            append(if (enabled) "true" else "false")
            append(",href:")
            append(if (href != null) jsonEsc(href) else "null")
            append(",extensionHref:")
            append(if (extensionHref != null) jsonEsc(extensionHref) else "null")
            append(",vars:")
            append(varsJson ?: "null")
            append(",cssBlocks:")
            append(cssBlocksJson ?: "null")
            append(",packId:")
            append(if (packId != null) jsonEsc(packId) else "null")
            append(",js:")
            append(if (jsUrl != null) jsonEsc(jsUrl) else "null")
            append("});")
        }
        eval(js)
    }

    fun clear(onDone: ((String?) -> Unit)? = null) = eval("window.Kernel.clear();", onDone)

    /** X 光诊断：回传内核页渲染全链路事实的 JSON 字符串（菜单「内核体检」入口，被动零开销）。 */
    fun diagnose(payloadCount: Int, onResult: (String) -> Unit) {
        val js = """
            (function(){
              function h(id){var e=document.getElementById(id);if(!e)return 'MISSING';
                var cs=getComputedStyle(e);return Math.round(e.getBoundingClientRect().height)+'px/'
                  +cs.display+'/'+cs.position;}
              var csEl=document.getElementById('custom-style');
              var pack=document.getElementById('style-pack-style');
              var firstMes=document.querySelector('#chat .mes .mes_text');
              var __d=JSON.stringify({
                ready:!!(window.Kernel&&window.Kernel.ready),
                generating:document.body.getAttribute('data-generating'),
                mesCount:document.querySelectorAll('#chat .mes').length,
                firstTextLen:firstMes?firstMes.textContent.length:-1,
                sheld:h('sheld'),chat:h('chat'),form:h('form_sheld'),bg1:h('bg1'),
                bgImage:(document.getElementById('bg1')||{style:{}}).style.backgroundImage||'none',
                bodyClass:document.body.className,
                chatDisplay:getComputedStyle(document.body).getPropertyValue('--sheldWidth'),
                fontScale:getComputedStyle(document.documentElement).getPropertyValue('--fontScale'),
                customCssLen:csEl?csEl.textContent.length:-1,
                stylePackHref:pack?pack.getAttribute('href'):'none',
                styleSheetCount:document.styleSheets.length,
                viewport:window.innerWidth+'x'+window.innerHeight+'@'+window.devicePixelRatio,
                href:location.href.replace('https://appassets.androidplatform.net',''),
                payloadCount:$payloadCount,
                kernelApi:window.Kernel?Object.keys(window.Kernel).length:-1,
                thApi:[typeof getVariables,typeof triggerSlash,typeof eventOn,typeof getChatMessages].join(',')
              });
              console.error(__d);
              return __d;
            })()
        """.trimIndent()
        eval(js) { onResult(it ?: "eval-no-callback") }
    }

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

    /** onDone 收到 evaluateJavascript 的返回字符串（无返回值调用传 null/空串） */
    private fun eval(js: String, onDone: ((String?) -> Unit)? = null) {
        web.evaluateJavascript(js) { onDone?.invoke(it) }
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
